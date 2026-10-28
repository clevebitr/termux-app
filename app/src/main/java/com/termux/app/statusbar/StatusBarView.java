package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.view.ViewUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The view of the status bar, which shows a row of segments like the time, the battery level, the
 * memory and cpu usage, the network traffic and the ip and proxy of the device.
 *
 * The views of the segments are created when the configuration is set and are only updated
 * afterwards, so that a refresh does not have to create new views.
 */
public class StatusBarView extends LinearLayout {

    /** The listener that is called when a segment is clicked and it has an action defined. */
    public interface OnSegmentClickListener {
        /**
         * @param segment The segment that was clicked.
         */
        void onStatusBarSegmentClicked(@NonNull StatusBarConfig.Segment segment);
    }

    /** The icon of each segment, or 0 if the segment does not have an icon. */
    @DrawableRes
    private static int getSegmentIcon(@NonNull String segmentId) {
        switch (segmentId) {
            case StatusBarConfig.SEGMENT_CLOCK: return R.drawable.ic_status_clock;
            case StatusBarConfig.SEGMENT_BATTERY: return R.drawable.ic_status_battery;
            case StatusBarConfig.SEGMENT_MEMORY: return R.drawable.ic_status_memory;
            case StatusBarConfig.SEGMENT_CPU: return R.drawable.ic_status_cpu;
            case StatusBarConfig.SEGMENT_NET: return R.drawable.ic_status_network;
            case StatusBarConfig.SEGMENT_IP: return R.drawable.ic_status_ip;
            case StatusBarConfig.SEGMENT_PROXY: return R.drawable.ic_status_proxy;
            default: return 0;
        }
    }

    /** The views of a single segment. */
    static final class SegmentViews {
        @NonNull final LinearLayout container;
        @Nullable final ImageView icon;
        @Nullable final TextView text;
        @Nullable final SparklineView chart;

        SegmentViews(@NonNull LinearLayout container, @Nullable ImageView icon, @Nullable TextView text,
                     @Nullable SparklineView chart) {
            this.container = container;
            this.icon = icon;
            this.text = text;
            this.chart = chart;
        }
    }

    @NonNull private final Map<String, SegmentViews> mSegmentViews = new LinkedHashMap<>();

    @Nullable private StatusBarConfig mConfig;
    private int mForegroundColor = 0xffffffff;

    public StatusBarView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBaselineAligned(false);
    }

    /**
     * This constructor is required by the {@link android.view.LayoutInflater}, which creates the
     * view when the `activity_termux` layout is inflated.
     */
    public StatusBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBaselineAligned(false);
    }

    public StatusBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setBaselineAligned(false);
    }

    /** @return The id of the segment that is visible, used for logging and testing. */
    @NonNull
    Map<String, SegmentViews> getSegmentViews() {
        return mSegmentViews;
    }

    /**
     * Create the views of the segments from the {@code config} and apply the style of the status
     * bar, like its height, font size, colors, alignment and separators.
     *
     * @param config The configuration of the status bar.
     * @param foregroundColor The color of the text and icons of the segments.
     * @param backgroundColor The background color of the status bar, which may be translucent.
     * @param listener The listener for the clicks of the segments that have an action defined.
     */
    public void setConfig(@NonNull StatusBarConfig config, int foregroundColor, int backgroundColor,
                          @Nullable OnSegmentClickListener listener) {
        mConfig = config;
        mForegroundColor = foregroundColor;

        removeAllViews();
        mSegmentViews.clear();

        setBackgroundColor(backgroundColor);
        setPadding(dpToPx(config.paddingHorizontalDp), 0, dpToPx(config.paddingHorizontalDp), 0);

        // The status bar is hosted in a FrameLayout, so its height and the edge it is aligned to are
        // set from the properties. The edge determines whether it takes the space above or below the
        // terminal, which is done by the activity by insetting the panes container.
        setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(config.heightDp),
            config.positionBottom ? Gravity.BOTTOM : Gravity.TOP));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(getRowGravity(config));
        row.setBaselineAligned(false);
        addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean first = true;
        for (StatusBarConfig.Segment segment : config.segments) {
            if (!config.separator.isEmpty() && !first) {
                row.addView(createSeparatorView(config));
            }
            first = false;

            SegmentViews segmentViews = createSegmentViews(config, segment);
            mSegmentViews.put(segment.id, segmentViews);
            row.addView(segmentViews.container);

            if (segmentViews.container != null && listener != null &&
                !StatusBarConfig.ACTION_NONE.equals(segment.action)) {
                segmentViews.container.setOnClickListener(v -> listener.onStatusBarSegmentClicked(segment));
                segmentViews.container.setClickable(true);
                segmentViews.container.setFocusable(false);
            }
        }

        setVisibility(config.enabled && !config.segments.isEmpty() ? VISIBLE : GONE);
    }

    /** Set the text of the segment {@code segmentId} and the color it is drawn with. */
    public void setSegmentText(@NonNull String segmentId, @NonNull CharSequence text, int color) {
        SegmentViews segmentViews = mSegmentViews.get(segmentId);
        if (segmentViews == null || segmentViews.text == null) return;

        segmentViews.text.setText(text);
        segmentViews.text.setTextColor(color);
    }

    /** Add a sample to the chart of the segment {@code segmentId}. */
    public void addSegmentSample(@NonNull String segmentId, float sample) {
        SegmentViews segmentViews = mSegmentViews.get(segmentId);
        if (segmentViews == null || segmentViews.chart == null) return;

        segmentViews.chart.addSample(sample);
    }

    /** Set the range of the chart of the segment {@code segmentId}, use a max lower than the min for auto scaling. */
    public void setSegmentChartRange(@NonNull String segmentId, float minValue, float maxValue) {
        SegmentViews segmentViews = mSegmentViews.get(segmentId);
        if (segmentViews == null || segmentViews.chart == null) return;

        segmentViews.chart.setRange(minValue, maxValue);
    }

    /** Set the color of the segment {@code segmentId} and of its chart. */
    public void setSegmentColor(@NonNull String segmentId, int color) {
        SegmentViews segmentViews = mSegmentViews.get(segmentId);
        if (segmentViews == null) return;

        if (segmentViews.text != null) segmentViews.text.setTextColor(color);
        if (segmentViews.chart != null) segmentViews.chart.setColor(color);
        if (segmentViews.icon != null) segmentViews.icon.setColorFilter(color);
    }

    /** Set the background color of the status bar, so that a change of the color scheme is applied. */
    public void setStatusBarBackgroundColor(int color) {
        setBackgroundColor(color);
    }

    /** Set the color of the text, icons and charts of all segments. */
    public void setStatusBarForegroundColor(int color, @NonNull StatusBarConfig config) {
        mForegroundColor = color;

        for (StatusBarConfig.Segment segment : config.segments) {
            // The color of the segment may be set to a specific color, in which case it is kept.
            if (segment.color.isEmpty()) setSegmentColor(segment.id, getSegmentColor(segment, color, false));
        }
    }

    /**
     * @return The color that the segment is drawn with, which is the color of the segment if it is
     *         set, otherwise the {@code defaultColor} of the status bar. If {@code isWarning} is
     *         true, then the warning color is returned if it is set.
     */
    public int getSegmentColor(@NonNull StatusBarConfig.Segment segment, int defaultColor, boolean isWarning) {
        if (isWarning && !segment.warnColor.isEmpty())
            return StatusBarColors.resolve(segment.warnColor, StatusBarColors.THEME_FOREGROUND);
        if (!segment.color.isEmpty())
            return StatusBarColors.resolve(segment.color, StatusBarColors.THEME_FOREGROUND);
        return defaultColor;
    }

    /** @return The default color of the text, icons and charts of the segments. */
    public int getStatusBarForegroundColor() {
        return mForegroundColor;
    }



    @NonNull
    private static int getRowGravity(@NonNull StatusBarConfig config) {
        switch (config.align) {
            case StatusBarConfig.ALIGN_CENTER: return Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
            case StatusBarConfig.ALIGN_END: return Gravity.END | Gravity.CENTER_VERTICAL;
            default: return Gravity.START | Gravity.CENTER_VERTICAL;
        }
    }

    @NonNull
    private TextView createSeparatorView(@NonNull StatusBarConfig config) {
        TextView separator = new TextView(getContext());
        separator.setText(config.separator);
        separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp);
        separator.setTextColor(StatusBarColors.withAlpha(mForegroundColor, 0.55f));
        separator.setGravity(Gravity.CENTER_VERTICAL);
        separator.setIncludeFontPadding(false);
        separator.setSingleLine(true);
        separator.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return separator;
    }

    @NonNull
    private SegmentViews createSegmentViews(@NonNull StatusBarConfig config, @NonNull StatusBarConfig.Segment segment) {
        Context context = getContext();
        int color = getSegmentColor(segment, mForegroundColor, false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setBaselineAligned(false);
        // A segment must not shrink below its content, the row can scroll instead if it is too wide.
        container.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int iconSize = dpToPx(Math.max(8, config.heightDp - 8));
        int spacing = dpToPx(3);

        ImageView icon = null;
        int iconRes = config.showIcons ? getSegmentIcon(segment.id) : 0;
        if (iconRes != 0) {
            icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(color);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

            LinearLayout.LayoutParams iconLayoutParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            icon.setLayoutParams(iconLayoutParams);

            if (!config.iconsAtEnd) {
                iconLayoutParams.rightMargin = spacing;
                container.addView(icon);
            }
        }

        TextView text = null;
        if (segment.shouldShowText()) {
            text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp);
            text.setTextColor(color);
            text.setTypeface(Typeface.MONOSPACE);
            text.setIncludeFontPadding(false);
            text.setSingleLine(true);
            text.setGravity(Gravity.CENTER_VERTICAL);
            text.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (config.iconsAtEnd && icon != null) {
                ((LinearLayout.LayoutParams) text.getLayoutParams()).rightMargin = spacing;
            }
            container.addView(text);
        }

        if (config.iconsAtEnd && icon != null) container.addView(icon);

        SparklineView chart = null;
        if (segment.shouldShowChart()) {
            chart = new SparklineView(context);
            chart.setSampleCount(segment.getChartSampleCount());
            chart.setColor(color);
            chart.setRange(0, 100);

            LinearLayout.LayoutParams chartLayoutParams = new LinearLayout.LayoutParams(
                dpToPx(Math.max(16, config.heightDp)), ViewGroup.LayoutParams.MATCH_PARENT);
            if (text != null || icon != null) {
                if (config.iconsAtEnd && icon != null) {
                    // The chart is the last view, so the icon does not need a margin.
                } else {
                    chartLayoutParams.leftMargin = spacing;
                }
            }
            chart.setLayoutParams(chartLayoutParams);
            container.addView(chart);
        }

        container.setPadding(0, 0, 0, 0);
        return new SegmentViews(container, icon, text, chart);
    }

    private int dpToPx(int dp) {
        return Math.round(ViewUtils.dpToPx(getContext(), dp));
    }

}
