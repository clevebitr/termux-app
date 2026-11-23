package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 目的 — 在 `keyboard-compat-log=true` 时把按键事件同时写入 logcat 与 `~/.termux/keyboard-compat.log`，
 * 使硬件键盘问题可以在设备上直接查看日志，而不必依赖 adb。
 *
 * <p>行为 — 每行带时间戳与来源标签，追加写入；文件超过 128KB 时重建，避免无限增长。写入失败只记录一次警告，
 * 不影响输入。</p>
 *
 * <p>约束 — 只在诊断开关打开时调用；写入发生在按键路径上，因此保持同步小写入并吞掉 IOException。</p>
 */
public final class KeyboardCompatLog {

    private static final String LOG_TAG = "KeyboardCompatLog";
    /** The file that `cat ~/.termux/keyboard-compat.log` shows, which is inside the app's own home directory. */
    private static final String FILE_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.termux/keyboard-compat.log";
    private static final long MAX_BYTES = 128 * 1024;

    private static boolean mWriteFailedLogged;

    private KeyboardCompatLog() {
    }

    /** Append {@code message} to the logcat and the log file, prefixed with a timestamp and {@code source}. */
    public static synchronized void log(@NonNull String source, @NonNull String message) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date())
            + " [" + source + "] " + message;

        Logger.logInfo(LOG_TAG, line);

        File file = new File(FILE_PATH);
        try {
            if (file.length() > MAX_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (IOException e) {
            if (mWriteFailedLogged) return;
            mWriteFailedLogged = true;
            Logger.logWarn(LOG_TAG, "Failed to write the keyboard log to \"" + FILE_PATH + "\": " + e.getMessage());
        }
    }

    /** @return The path of the log file, for the settings summary and the documentation. */
    @NonNull
    public static String filePath() {
        return FILE_PATH;
    }
}
