package com.termux.app.ext.statusbar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.app.properties.ForkProperties;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import com.termux.app.widget.ColorSchemeColors;

/**
 * Drives the status bar: it parses the configuration from the `termux.properties` file, creates the
 * [
 * {@link StatusBarView} segments, and refreshes the values of each segment at its own interval while
 * the activity is visible.
 *
 * Only the values of the segments that are shown are collected, and each segment has its own refresh
 * interval, so that the clock can update every second without reading the battery or the network
 * interfaces that often.
 */
public class StatusBarController {

    private static final String LOG_TAG = "StatusBarController";

    /** The receiver of the click of a segment, which runs the action that the properties configure. */
    public interface SegmentListener {
        void onStatusBarSegmentClicked(@NonNull StatusBarConfig.Segment segment);
    }

    /** The minimum interval of the handler that refreshes the segments that are due. */
    private static final long MIN_TICK_INTERVAL_MS = 100;
    /** The maximum interval of the handler, so that a change of the system clock is picked up. */
    private static final long MAX_TICK_INTERVAL_MS = 5000;

    @NonNull private final StatusBarView mView;
    @NonNull private final SegmentListener mListener;
    @NonNull private final StatusBarMetrics mMetrics;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Nullable private StatusBarConfig mConfig;
    /** The time at which each segment should be refreshed next, by segment id. */
    @NonNull private final Map<String, Long> mNextRefreshTimes = new HashMap<>();

    private boolean mStarted;
    private int mBackgroundColor;
    private int mForegroundColor;

    /** The formatter of the clock segment, recreated when the format changes. */
    @Nullable private SimpleDateFormat mClockFormat;
    @Nullable private String mClockFormatPattern;

    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mStarted) return;
            tick();
            scheduleNextTick();
        }
    };

    /**
     * The runnable that refreshes every segment once after the activity became visible.
     *
     * The refresh is deferred instead of being run in {@link #start()}, since it reads the battery
     * state, {@code /proc} and the network interfaces on the main thread, which would delay the frame
     * that shows the activity when it is first started.
     */
    private final Runnable mInitialRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mStarted) return;
            // The cpu usage and the network rates need two samples, so the first one is taken here and not
            // while the activity is being started.
            mMetrics.prime();
            refreshAllSegments();
            scheduleNextTick();
        }
    };

    public StatusBarController(@NonNull Context context, @NonNull StatusBarView view,
                               @NonNull SegmentListener listener) {
        mView = view;
        mListener = listener;
        mMetrics = new StatusBarMetrics(context);
    }

    /** @return The current configuration of the status bar, or {@code null} if it was not reloaded yet. */
    @Nullable
    public StatusBarConfig getConfig() {
        return mConfig;
    }

    /**
     * Reload the configuration from the `termux.properties` file and rebuild the views of the
     * segments. This is called when the activity is created and whenever the properties may have
     * changed, like when the user returns from the settings.
     */
    public void reload() {
        // The status bar properties are not part of the `TERMUX_APP_PROPERTIES_LIST` that the shared
        // library converts to internal values, and `getProperties(true)` only returns the keys of
        // that list, since `SharedProperties.loadPropertiesFromDisk()` drops every other key. So the
        // properties must be read from disk directly, otherwise the status bar always gets the
        // default values and never shows up. Reloads are rare (activity start and styling changes),
        // so reading the file here is fine.
        Properties properties = ForkProperties.readTermuxProperties();

        mConfig = StatusBarConfig.parse(properties);

        mBackgroundColor = ColorSchemeColors.resolve(mConfig.backgroundColor, ColorSchemeColors.THEME_BACKGROUND);
        mForegroundColor = ColorSchemeColors.resolve(mConfig.foregroundColor, ColorSchemeColors.THEME_FOREGROUND);

        mClockFormat = null;
        mClockFormatPattern = null;

        mView.setConfig(mConfig, mForegroundColor, mBackgroundColor, segment -> {
            mListener.onStatusBarSegmentClicked(segment);
        });

        mNextRefreshTimes.clear();
        if (mConfig.enabled && !mConfig.segments.isEmpty()) {
            // The cpu usage and the network rates need two samples. They are taken when the activity
            // becomes visible instead of here, since this is called while the activity is created.
            if (mStarted) {
                // Show the values immediately instead of after the first interval.
                refreshAllSegments();
                scheduleNextTick();
            }
            Logger.logDebug(LOG_TAG, "Reloaded status bar, segments: " + mConfig.segments.size());
        } else {
            mHandler.removeCallbacks(mInitialRefreshRunnable);
            mHandler.removeCallbacks(mTickRunnable);
            mView.setVisibility(android.view.View.GONE);
        }
    }

    /** Re-resolve the colors from the color scheme, like after the `colors.properties` file changed. */
    public void reloadColors() {
        if (mConfig == null) return;

        mBackgroundColor = ColorSchemeColors.resolve(mConfig.backgroundColor, ColorSchemeColors.THEME_BACKGROUND);
        mForegroundColor = ColorSchemeColors.resolve(mConfig.foregroundColor, ColorSchemeColors.THEME_FOREGROUND);

        mView.setStatusBarBackgroundColor(mBackgroundColor);
        mView.setStatusBarForegroundColor(mForegroundColor, mConfig);
        refreshAllSegments();
    }

    /** Start refreshing the segments, called when the activity becomes visible. */
    public void start() {
        if (mStarted) return;
        mStarted = true;

        if (mConfig == null) reload();
        if (mConfig == null || !mConfig.enabled || mConfig.segments.isEmpty()) return;

        // The values may be stale after the activity was not visible, and the cpu usage and the network
        // rates need two samples, so they are refreshed after the first frame instead of now.
        mHandler.removeCallbacks(mInitialRefreshRunnable);
        mHandler.removeCallbacks(mTickRunnable);
        mHandler.post(mInitialRefreshRunnable);
    }

    /** Stop refreshing the segments, called when the activity is not visible anymore. */
    public void stop() {
        mStarted = false;
        mHandler.removeCallbacks(mInitialRefreshRunnable);
        mHandler.removeCallbacks(mTickRunnable);
    }

    /** @return Whether the status bar is enabled and has segments. */
    public boolean isVisible() {
        return mConfig != null && mConfig.enabled && !mConfig.segments.isEmpty();
    }

    /** Refresh the value of every segment now. */
    private void refreshAllSegments() {
        if (mConfig == null) return;

        long now = SystemClock.elapsedRealtime();
        for (StatusBarConfig.Segment segment : mConfig.segments) {
            refreshSegment(segment);
            mNextRefreshTimes.put(segment.id, now + segment.intervalMs);
        }
    }

    private void tick() {
        if (mConfig == null || !mConfig.enabled || mConfig.segments.isEmpty()) return;

        long now = SystemClock.elapsedRealtime();
        for (StatusBarConfig.Segment segment : mConfig.segments) {
            Long nextRefreshTime = mNextRefreshTimes.get(segment.id);
            if (nextRefreshTime != null && now < nextRefreshTime) continue;

            refreshSegment(segment);
            mNextRefreshTimes.put(segment.id, now + segment.intervalMs);
        }
    }

    /**
     * Schedule the next tick at the time that the next segment is due, instead of polling at a fixed
     * interval, so that the main thread is woken up once per second for the clock of a status bar with
     * a one second clock interval and not five times per second.
     */
    private void scheduleNextTick() {
        mHandler.removeCallbacks(mTickRunnable);
        if (!mStarted || mConfig == null) return;

        long now = SystemClock.elapsedRealtime();
        long next = Long.MAX_VALUE;
        for (StatusBarConfig.Segment segment : mConfig.segments) {
            Long nextRefreshTime = mNextRefreshTimes.get(segment.id);
            long refreshTime = nextRefreshTime != null ? nextRefreshTime : now;
            if (refreshTime < next) next = refreshTime;
        }

        long delay = next == Long.MAX_VALUE ? MAX_TICK_INTERVAL_MS : next - now;
        delay = Math.max(MIN_TICK_INTERVAL_MS, Math.min(MAX_TICK_INTERVAL_MS, delay));
        mHandler.postDelayed(mTickRunnable, delay);
    }

    /**
     * Collect the value of the {@code segment} and update its view.
     */
    private void refreshSegment(@NonNull StatusBarConfig.Segment segment) {
        if (mConfig == null) return;

        String text;
        Float chartSample = null;
        Float chartMax = 100f;
        Double primaryValue = null;

        try {
            switch (segment.id) {
                case StatusBarConfig.SEGMENT_CLOCK: {
                    text = formatClock(segment.format);
                    break;
                }
                case StatusBarConfig.SEGMENT_BATTERY: {
                    StatusBarMetrics.Battery battery = mMetrics.getBattery();
                    if (battery == null) {
                        text = "-";
                        break;
                    }

                    Map<String, String> tokens = new LinkedHashMap<>();
                    tokens.put("p", StatusBarMetrics.formatPercent(battery.percent));
                    tokens.put("t", Float.isNaN(battery.temperatureCelsius) ? "-"
                        : String.format(Locale.ROOT, "%.1f\u00b0C", battery.temperatureCelsius));
                    tokens.put("s", battery.status);
                    text = applyTokens(segment.format, tokens);
                    primaryValue = battery.percent >= 0 ? (double) battery.percent : null;
                    chartSample = battery.percent >= 0 ? (float) battery.percent : null;
                    break;
                }
                case StatusBarConfig.SEGMENT_MEMORY: {
                    StatusBarMetrics.Memory memory = mMetrics.getMemory();
                    Map<String, String> tokens = new LinkedHashMap<>();
                    tokens.put("p", memory.totalBytes > 0
                        ? StatusBarMetrics.formatPercent(100f * memory.usedBytes / memory.totalBytes) : "-");
                    tokens.put("u", StatusBarMetrics.formatBytes(memory.usedBytes));
                    tokens.put("a", StatusBarMetrics.formatBytes(memory.availableBytes));
                    tokens.put("t", StatusBarMetrics.formatBytes(memory.totalBytes));
                    text = applyTokens(segment.format, tokens);
                    if (memory.totalBytes > 0) {
                        float percent = 100f * memory.usedBytes / memory.totalBytes;
                        chartSample = percent;
                        primaryValue = (double) percent;
                    }
                    break;
                }
                case StatusBarConfig.SEGMENT_CPU: {
                    StatusBarMetrics.Cpu cpu = mMetrics.getCpu();
                    Map<String, String> tokens = new LinkedHashMap<>();
                    tokens.put("p", StatusBarMetrics.formatPercent(cpu.percent));
                    tokens.put("f", StatusBarMetrics.formatFrequency(cpu.averageFrequencyKHz));
                    tokens.put("m", StatusBarMetrics.formatFrequency(cpu.maxFrequencyKHz));
                    tokens.put("c", String.valueOf(cpu.cores));
                    text = applyTokens(segment.format, tokens);
                    if (cpu.percent >= 0) {
                        chartSample = cpu.percent;
                        primaryValue = (double) cpu.percent;
                    }
                    break;
                }
                case StatusBarConfig.SEGMENT_NET: {
                    StatusBarMetrics.Traffic network = mMetrics.getNetworkTraffic();
                    Map<String, String> tokens = new LinkedHashMap<>();
                    tokens.put("rx", StatusBarMetrics.formatRate(network.rxRateBytesPerSecond));
                    tokens.put("tx", StatusBarMetrics.formatRate(network.txRateBytesPerSecond));
                    tokens.put("rxTotal", StatusBarMetrics.formatBytes(network.rxTotalBytes));
                    tokens.put("txTotal", StatusBarMetrics.formatBytes(network.txTotalBytes));
                    text = applyTokens(segment.format, tokens);
                    if (network.rxRateBytesPerSecond >= 0 && network.txRateBytesPerSecond >= 0) {
                        float rate = network.rxRateBytesPerSecond + network.txRateBytesPerSecond;
                        chartSample = rate;
                        // The rates are auto scaled, since they are not bounded like a percentage.
                        chartMax = null;
                        primaryValue = (double) rate;
                    }
                    break;
                }
                case StatusBarConfig.SEGMENT_IP: {
                    StatusBarMetrics.Ip ip = mMetrics.getIp();
                    Map<String, String> tokens = new LinkedHashMap<>();
                    tokens.put("4", ip.ipv4 != null ? ip.ipv4 : "-");
                    tokens.put("6", ip.ipv6 != null ? ip.ipv6 : "-");
                    tokens.put("i", ip.ipv4 != null ? ip.ipv4 : (ip.ipv6 != null ? ip.ipv6 : "-"));
                    tokens.put("if", ip.interfaceName != null ? ip.interfaceName : "-");
                    text = applyTokens(segment.format, tokens);
                    break;
                }
                case StatusBarConfig.SEGMENT_PROXY: {
                    StatusBarMetrics.Proxy proxy = mMetrics.getProxy();
                    Map<String, String> tokens = new LinkedHashMap<>();
                    String hostAndPort = proxy.host != null
                        ? (proxy.port > 0 ? proxy.host + ":" + proxy.port : proxy.host) : "none";
                    tokens.put("p", hostAndPort);
                    tokens.put("h", proxy.host != null ? proxy.host : "-");
                    tokens.put("o", proxy.port > 0 ? String.valueOf(proxy.port) : "-");
                    tokens.put("t", proxy.type);
                    text = applyTokens(segment.format, tokens);
                    break;
                }
                default: {
                    text = "-";
                    break;
                }
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to refresh the \"" + segment.id + "\" status bar segment", e);
            text = "-";
        }

        if (!segment.label.isEmpty()) text = segment.label + " " + text;

        boolean isWarning = isWarning(segment, primaryValue);
        mView.setSegmentText(segment.id, text, mView.getSegmentColor(segment, mForegroundColor, isWarning));

        if (chartSample != null) {
            if (chartMax == null) {
                mView.setSegmentChartRange(segment.id, 0, 0);
            } else {
                mView.setSegmentChartRange(segment.id, 0, chartMax);
            }
            mView.addSegmentSample(segment.id, chartSample);
        }
    }

    /** @return Whether the {@code primaryValue} of the segment reached one of its warning thresholds. */
    private static boolean isWarning(@NonNull StatusBarConfig.Segment segment, @Nullable Double primaryValue) {
        if (primaryValue == null) return false;
        if (segment.warnAbove != null && primaryValue > segment.warnAbove) return true;
        return segment.warnBelow != null && primaryValue < segment.warnBelow;
    }

    @NonNull
    private String formatClock(@NonNull String pattern) {
        if (mClockFormat == null || !pattern.equals(mClockFormatPattern)) {
            try {
                mClockFormat = new SimpleDateFormat(pattern, Locale.getDefault());
            } catch (IllegalArgumentException e) {
                Logger.logError(LOG_TAG, "Invalid clock format \"" + pattern + "\", using \"HH:mm\" instead");
                mClockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            }
            mClockFormatPattern = pattern;
        }

        return mClockFormat.format(new Date());
    }

    /**
     * Replace the `%<token>` placeholders in the {@code template} with their values from {@code
     * tokens}. The longest matching token wins, so that `%rxTotal` is not replaced as `%rx` followed
     * by `Total`, and `%%` produces a literal `%`. Unknown tokens are left as they are.
     */
    @NonNull
    public static String applyTokens(@NonNull String template, @NonNull Map<String, String> tokens) {
        StringBuilder result = new StringBuilder(template.length() + 16);

        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c != '%' || i + 1 >= template.length()) {
                result.append(c);
                continue;
            }

            if (template.charAt(i + 1) == '%') {
                result.append('%');
                i++;
                continue;
            }

            String bestMatch = null;
            for (String token : tokens.keySet()) {
                if (template.startsWith(token, i + 1) && (bestMatch == null || token.length() > bestMatch.length()))
                    bestMatch = token;
            }

            if (bestMatch == null) {
                result.append(c);
                continue;
            }

            result.append(tokens.get(bestMatch));
            i += bestMatch.length();
        }

        return result.toString();
    }

}
