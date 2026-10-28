package com.termux.app.statusbar;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.ProxyInfo;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Collects the values that are shown by the segments of the status bar with the system apis of the
 * app.
 *
 * The values are collected lazily, i.e. only when the segment that needs them is refreshed. The cpu
 * usage and the network rates need two samples to be computed, so their readers keep the previous
 * sample and report `-1` (unknown) until a second sample can be taken.
 */
public final class StatusBarMetrics {

    private static final String LOG_TAG = "StatusBarMetrics";

    /** The value that is returned when a metric is not available, like the cpu usage on devices that restrict /proc/stat. */
    public static final float UNKNOWN_PERCENT = -1f;
    public static final long UNKNOWN_LONG = -1L;

    private final Context mContext;

    /** The previous /proc/stat sample, `{idle, total}` jiffies, or null if not available. */
    @Nullable private long[] mPreviousProcStat;
    /** Whether /proc/stat was readable on the last attempt, so that it is not read on every refresh. */
    private boolean mProcStatAvailable = true;

    /** The previous network sample, `{rxBytes, txBytes, elapsedRealtime}`, or null. */
    @Nullable private long[] mPreviousNetworkSample;

    /** The time of the last ip lookup, since reading the interfaces is more expensive than the other metrics. */
    private long mLastIpLookupTime;
    @Nullable private Ip mCachedIp;

    public StatusBarMetrics(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Take the first sample of the metrics that need two samples to compute a value, so that the cpu
     * usage and the network rates are not unknown when the status bar is first shown.
     */
    public void prime() {
        try {
            getCpu();
            getNetworkTraffic();
        } catch (Exception e) {
            Logger.logVerbose(LOG_TAG, "Failed to prime the status bar metrics: " + e.getMessage());
        }
    }

    /** @return The current battery state, or {@code null} if the battery broadcast could not be read. */
    @Nullable
    public Battery getBattery() {
        Intent intent;
        try {
            intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception e) {
            // Android 14 may throw for apps that are not in the foreground, and some roms restrict it.
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the battery state", e);
            return null;
        }
        if (intent == null) return null;

        Battery battery = new Battery();

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) battery.percent = Math.round(level * 100f / scale);

        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (temperature != Integer.MIN_VALUE) battery.temperatureCelsius = temperature / 10f;

        battery.plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        battery.status = getBatteryStatusName(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1));

        return battery;
    }

    @NonNull
    private static String getBatteryStatusName(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "not-charging";
            default: return "";
        }
    }

    /**
     * @return The used and total memory of the device, read from `/proc/meminfo` if it is readable,
     *         since it reports the memory that is actually available for new processes, otherwise
     *         from the {@link ActivityManager}.
     */
    @NonNull
    public Memory getMemory() {
        Memory memory = new Memory();

        long totalKb = -1, availableKb = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/meminfo")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) totalKb = parseMeminfoValueKb(line);
                else if (line.startsWith("MemAvailable:")) availableKb = parseMeminfoValueKb(line);
                if (totalKb > 0 && availableKb >= 0) break;
            }
        } catch (Exception e) {
            // Fall back to the ActivityManager below.
        }

        if (totalKb > 0 && availableKb >= 0) {
            memory.totalBytes = totalKb * 1024;
            memory.availableBytes = availableKb * 1024;
            memory.usedBytes = memory.totalBytes - memory.availableBytes;
            return memory;
        }

        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            memory.totalBytes = memoryInfo.totalMem;
            memory.availableBytes = memoryInfo.availMem;
            memory.usedBytes = memory.totalBytes - memory.availableBytes;
        }

        return memory;
    }

    private static long parseMeminfoValueKb(@NonNull String line) {
        int start = line.indexOf(':');
        if (start < 0) return -1;

        StringBuilder digits = new StringBuilder();
        for (int i = start + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else if (digits.length() > 0) break;
        }

        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return The cpu usage in percent, or {@link #UNKNOWN_PERCENT} if it cannot be read.
     *
     * The usage is read from the `/proc/stat` kernel counters when the device allows reading them.
     * Android 9+ restricts `/proc/stat` for apps, so if it is not readable, the current frequency of
     * the cpu cores relative to their maximum frequency is used as an approximation, since a core is
     * only scaled up when there is work to do. If neither is available, the usage is unknown.
     */
    @NonNull
    public Cpu getCpu() {
        Cpu cpu = new Cpu();
        cpu.cores = Runtime.getRuntime().availableProcessors();

        readCpuFrequencies(cpu);

        if (mProcStatAvailable) {
            long[] procStat = readProcStat();
            if (procStat == null) {
                mProcStatAvailable = false;
            } else if (mPreviousProcStat != null) {
                long totalDelta = procStat[1] - mPreviousProcStat[1];
                long idleDelta = procStat[0] - mPreviousProcStat[0];
                if (totalDelta > 0)
                    cpu.percent = Math.max(0f, Math.min(100f, 100f * (totalDelta - idleDelta) / totalDelta));
            }
            mPreviousProcStat = procStat;
        }

        if (cpu.percent == UNKNOWN_PERCENT && cpu.averageFrequencyKHz > 0 && cpu.maxFrequencyKHz > 0)
            cpu.percent = Math.max(0f, Math.min(100f, 100f * cpu.averageFrequencyKHz / cpu.maxFrequencyKHz));

        return cpu;
    }

    /**
     * Read the current and maximum frequency of the cpu clusters from sysfs, which are readable for
     * apps, unlike `/proc/stat`, and average them to get a single value for all cores.
     */
    private void readCpuFrequencies(@NonNull Cpu cpu) {
        long totalCurrentKHz = 0, totalMaxKHz = 0;
        int clusters = 0;

        File[] policies = new File("/sys/devices/system/cpu/cpufreq").listFiles();
        if (policies == null) return;

        for (File policy : policies) {
            if (!policy.getName().startsWith("policy")) continue;

            long current = readLongFromFile(new File(policy, "scaling_cur_freq"));
            long max = readLongFromFile(new File(policy, "cpuinfo_max_freq"));
            if (current <= 0 || max <= 0) continue;

            totalCurrentKHz += current;
            totalMaxKHz += max;
            clusters++;
        }

        if (clusters > 0) {
            cpu.averageFrequencyKHz = totalCurrentKHz / clusters;
            cpu.maxFrequencyKHz = totalMaxKHz / clusters;
        }
    }

    /** @return `{idle, total}` jiffies from the first line of `/proc/stat`, or {@code null}. */
    @Nullable
    private long[] readProcStat() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/stat")))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;

            String[] fields = line.trim().split("\\s+");
            long total = 0;
            long idle = 0;
            // fields[0] is "cpu", fields[1..] are user, nice, system, idle, iowait, irq, softirq, steal.
            for (int i = 1; i < fields.length; i++) {
                long value = Long.parseLong(fields[i]);
                total += value;
                if (i == 4 || i == 5) idle += value;
            }

            return new long[]{idle, total};
        } catch (Exception e) {
            // Not readable on Android 9+ in the app domain.
            Logger.logVerbose(LOG_TAG, "Failed to read /proc/stat, falling back to the cpu frequencies: " + e.getMessage());
            return null;
        }
    }

    /**
     * @return The network rates since the previous call and the total bytes since boot, or unknown
     *         values if the {@link TrafficStats} are not supported by the device.
     */
    @NonNull
    public Traffic getNetworkTraffic() {
        Traffic network = new Traffic();

        long rxTotal = TrafficStats.getTotalRxBytes();
        long txTotal = TrafficStats.getTotalTxBytes();
        if (rxTotal == TrafficStats.UNSUPPORTED || txTotal == TrafficStats.UNSUPPORTED) return network;

        network.rxTotalBytes = rxTotal;
        network.txTotalBytes = txTotal;

        long now = SystemClock.elapsedRealtime();
        if (mPreviousNetworkSample != null) {
            long elapsed = now - mPreviousNetworkSample[2];
            if (elapsed > 0) {
                network.rxRateBytesPerSecond = Math.max(0, (rxTotal - mPreviousNetworkSample[0]) * 1000 / elapsed);
                network.txRateBytesPerSecond = Math.max(0, (txTotal - mPreviousNetworkSample[1]) * 1000 / elapsed);
            }
        }
        mPreviousNetworkSample = new long[]{rxTotal, txTotal, now};

        return network;
    }

    /**
     * @return The addresses of the active network, or empty values if there is no active network.
     *         The result is cached for a second, since reading the interfaces is relatively expensive.
     */
    @NonNull
    public Ip getIp() {
        long now = SystemClock.elapsedRealtime();
        if (mCachedIp != null && now - mLastIpLookupTime < 1000) return mCachedIp;

        Ip ip = new Ip();

        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network activeNetwork = getActiveNetwork(connectivityManager);
            if (activeNetwork != null) {
                try {
                    LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
                    if (linkProperties != null) {
                        ip.interfaceName = linkProperties.getInterfaceName();
                        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                            InetAddress address = linkAddress.getAddress();
                            if (address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                            if (address instanceof Inet4Address && ip.ipv4 == null) ip.ipv4 = address.getHostAddress();
                            else if (address instanceof Inet6Address && ip.ipv6 == null) ip.ipv6 = address.getHostAddress();
                        }
                    }
                } catch (Exception e) {
                    Logger.logVerbose(LOG_TAG, "Failed to read the link properties of the active network: " + e.getMessage());
                }
            }
        }

        if (ip.ipv4 == null && ip.ipv6 == null) fillIpFromNetworkInterfaces(ip);

        mCachedIp = ip;
        mLastIpLookupTime = now;
        return ip;
    }

    @Nullable
    private static Network getActiveNetwork(@NonNull ConnectivityManager connectivityManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return connectivityManager.getActiveNetwork();
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected()) return null;
            // getActiveNetwork() is required to get the LinkProperties, so fall back to enumerating
            // the interfaces on older versions, which is done by the caller when no ip was found.
            return null;
        }
    }

    private static void fillIpFromNetworkInterfaces(@NonNull Ip ip) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;

                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                    if (ip.interfaceName == null) ip.interfaceName = networkInterface.getName();
                    if (address instanceof Inet4Address && ip.ipv4 == null) ip.ipv4 = address.getHostAddress();
                    else if (address instanceof Inet6Address && ip.ipv6 == null) ip.ipv6 = address.getHostAddress();
                }
            }
        } catch (Exception e) {
            Logger.logVerbose(LOG_TAG, "Failed to read the network interfaces: " + e.getMessage());
        }
    }

    /**
     * @return The proxy that the system is configured to use for the active network, or a proxy with
     *         no host if the connections are made directly.
     */
    @NonNull
    public Proxy getProxy() {
        Proxy proxy = new Proxy();
        proxy.type = "none";

        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                ProxyInfo proxyInfo = connectivityManager.getDefaultProxy();
                if (proxyInfo != null && proxyInfo.getHost() != null && !proxyInfo.getHost().isEmpty()) {
                    proxy.host = proxyInfo.getHost();
                    proxy.port = proxyInfo.getPort();
                    proxy.type = proxyInfo.getPacFileUrl() != null ? "pac" : "http";
                    return proxy;
                }
            } catch (Exception e) {
                Logger.logVerbose(LOG_TAG, "Failed to read the default proxy: " + e.getMessage());
            }
        }

        // Fall back to the java system properties, which are set by some devices for the global proxy.
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        if (host != null && !host.isEmpty()) {
            proxy.host = host;
            proxy.type = "http";
            if (port != null) {
                try {
                    proxy.port = Integer.parseInt(port.trim());
                } catch (NumberFormatException e) {
                    // Ignore, the port is optional.
                }
            }
        }

        return proxy;
    }

    private static long readLongFromFile(@NonNull File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line = reader.readLine();
            if (line == null) return -1;
            return Long.parseLong(line.trim());
        } catch (Exception e) {
            return -1;
        }
    }



    /** The battery state of the device. */
    public static final class Battery {
        /** The charge level in percent, or {@code -1} if unknown. */
        public int percent = -1;
        /** The temperature in celsius, or {@link Float#NaN} if unknown. */
        public float temperatureCelsius = Float.NaN;
        /** The status, `charging`, `discharging`, `full`, `not-charging` or empty. */
        @NonNull public String status = "";
        /** The source the device is plugged into, as defined by the {@link BatteryManager} extras. */
        public int plugged;
    }

    /** The memory state of the device. */
    public static final class Memory {
        public long usedBytes = UNKNOWN_LONG;
        public long totalBytes = UNKNOWN_LONG;
        public long availableBytes = UNKNOWN_LONG;
    }

    /** The cpu state of the device. */
    public static final class Cpu {
        /** The usage in percent, or {@link #UNKNOWN_PERCENT} if it cannot be determined. */
        public float percent = UNKNOWN_PERCENT;
        /** The average current frequency of the cores in kHz, or {@code -1} if unknown. */
        public long averageFrequencyKHz = UNKNOWN_LONG;
        /** The average maximum frequency of the cores in kHz, or {@code -1} if unknown. */
        public long maxFrequencyKHz = UNKNOWN_LONG;
        public int cores;
    }

    /** The network traffic of the device. */
    public static final class Traffic {
        public long rxRateBytesPerSecond = UNKNOWN_LONG;
        public long txRateBytesPerSecond = UNKNOWN_LONG;
        public long rxTotalBytes = UNKNOWN_LONG;
        public long txTotalBytes = UNKNOWN_LONG;
    }

    /** The addresses of the active network of the device. */
    public static final class Ip {
        @Nullable public String ipv4;
        @Nullable public String ipv6;
        @Nullable public String interfaceName;
    }

    /** The proxy that the device is configured to use. */
    public static final class Proxy {
        /** The proxy type, `none`, `http` or `pac`. */
        @NonNull public String type = "none";
        @Nullable public String host;
        public int port = -1;
    }



    /** @return The {@code bytes} as a short human readable string, like `1.2G` or `350M`. */
    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "-";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0fK", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.0fM", bytes / (1024f * 1024f));
        return String.format(Locale.ROOT, "%.1fG", bytes / (1024f * 1024f * 1024f));
    }

    /** @return The {@code bytesPerSecond} rate as a short human readable string, like `1.2M/s`. */
    @NonNull
    public static String formatRate(long bytesPerSecond) {
        if (bytesPerSecond < 0) return "-";
        if (bytesPerSecond < 1024) return bytesPerSecond + "B/s";
        if (bytesPerSecond < 1024L * 1024) return String.format(Locale.ROOT, "%.0fK/s", bytesPerSecond / 1024f);
        if (bytesPerSecond < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1fM/s", bytesPerSecond / (1024f * 1024f));
        return String.format(Locale.ROOT, "%.1fG/s", bytesPerSecond / (1024f * 1024f * 1024f));
    }

    /** @return The {@code percent} as a string, like `42%`, or `-` if it is unknown. */
    @NonNull
    public static String formatPercent(float percent) {
        if (percent < 0) return "-";
        return Math.round(percent) + "%";
    }

    /** @return The frequency in kHz as a string in MHz, like `1800M`, or `-` if it is unknown. */
    @NonNull
    public static String formatFrequency(long kilohertz) {
        if (kilohertz < 0) return "-";
        return Math.round(kilohertz / 1000f) + "M";
    }

}
