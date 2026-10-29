package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the details of the process of a terminal session from {@code /proc}, like the name of the process
 * that is in the foreground and the working directory of the shell.
 *
 * The details are read by the list of the sessions drawer and by the summary of its header, so they live in
 * one place. Everything is best effort: a session that exited or a process that the app is not allowed to
 * read simply has no details.
 */
public final class SessionDetails {

    /** The maximum number of bytes to read from the files of a process. */
    private static final int PROCESS_FILE_MAX_READ_SIZE = 4096;

    private SessionDetails() {
    }

    /**
     * @return The name of the process that is in the foreground of the terminal of {@code pid}, which is the
     *         process that the terminal device is attached to, or {@code null} if it cannot be read.
     */
    @Nullable
    public static String getForegroundProcessName(int pid) {
        if (pid <= 0) return null;

        try {
            // The foreground process group of the terminal is the `tpgid` field of the stat file, which is
            // the 8th field, but the name in the 2nd field may contain spaces and is surrounded by
            // parentheses, so the fields after it are parsed from the end of the name instead.
            String stat = readProcessFile(pid, "stat");
            if (stat == null) return null;

            int nameEndIndex = stat.lastIndexOf(')');
            if (nameEndIndex < 0 || nameEndIndex + 2 >= stat.length()) return null;

            // The fields after the name start with the state field, so `tpgid` is the 6th field.
            String[] fieldsAfterName = stat.substring(nameEndIndex + 2).split(" ");
            if (fieldsAfterName.length < 6) return null;

            int foregroundProcessGroup = Integer.parseInt(fieldsAfterName[5]);
            // The value is -1 if the process is not attached to a terminal, in which case the shell is used.
            int foregroundPid = foregroundProcessGroup > 0 ? foregroundProcessGroup : pid;

            String processName = getProcessName(foregroundPid);
            return processName != null ? processName : getProcessName(pid);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return The name of the process, or {@code null} if it cannot be read. */
    @Nullable
    public static String getProcessName(int pid) {
        if (pid <= 0) return null;

        // The command line contains the executable and its arguments separated by NUL characters, but it is
        // empty for kernel threads, in which case the name is read from the comm file instead.
        String cmdline = readProcessFile(pid, "cmdline");
        if (cmdline != null && !cmdline.isEmpty()) {
            int argumentEndIndex = cmdline.indexOf('\0');
            String executable = argumentEndIndex >= 0 ? cmdline.substring(0, argumentEndIndex) : cmdline;
            if (!executable.isEmpty()) {
                int pathSeparatorIndex = executable.lastIndexOf('/');
                return pathSeparatorIndex >= 0 ? executable.substring(pathSeparatorIndex + 1) : executable;
            }
        }

        String comm = readProcessFile(pid, "comm");
        return comm != null && !comm.isEmpty() ? comm.trim() : null;
    }

    /**
     * @return The working directory of the process, with the home directory of the app shortened to
     *         {@code ~}, or {@code null} if it cannot be read.
     */
    @Nullable
    public static String getWorkingDirectory(int pid) {
        if (pid <= 0) return null;

        try {
            String workingDirectory = new File(processFilePath(pid, "cwd")).getCanonicalPath();
            return shortenHomeDirectory(workingDirectory);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return {@code path} with a leading {@code homeDirectory} replaced by {@code ~}, so that a long path
     *         fits in the list of the sessions, or {@code path} unchanged when it is not in the home
     *         directory.
     */
    @NonNull
    static String shortenHomeDirectory(@NonNull String path, @NonNull String homeDirectory) {
        if (homeDirectory.isEmpty()) return path;
        if (path.equals(homeDirectory)) return "~";
        if (path.startsWith(homeDirectory + "/")) return "~" + path.substring(homeDirectory.length());
        return path;
    }

    /** @return {@code path} with the home directory of the app shortened like {@link #shortenHomeDirectory}. */
    @NonNull
    static String shortenHomeDirectory(@NonNull String path) {
        return shortenHomeDirectory(path, TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    /**
     * @return The details of the session that are shown in one line, like {@code vim · ~/projects/foo}, with
     *         the parts that cannot be read left out, or {@code null} if there is nothing to show.
     */
    @Nullable
    public static String getShortDetails(int pid) {
        List<String> details = new ArrayList<>();
        String processName = getForegroundProcessName(pid);
        if (processName != null) details.add(processName);
        String workingDirectory = getWorkingDirectory(pid);
        if (workingDirectory != null) details.add(workingDirectory);

        return details.isEmpty() ? null : join(details, " · ");
    }

    /**
     * @return The non empty parts of {@code parts} joined with {@code separator}, so that a line never
     *         starts or ends with a separator when one of the details cannot be read.
     */
    @NonNull
    public static String join(@NonNull List<String> parts, @NonNull String separator) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(separator);
            builder.append(part);
        }
        return builder.toString();
    }

    private static String processFilePath(int pid, @NonNull String fileName) {
        return "/proc/" + pid + "/" + fileName;
    }

    /**
     * Read the contents of the {@code fileName} of the process, or {@code null} if it cannot be read, like
     * if the process does not exist anymore or access to it is not granted.
     */
    @Nullable
    private static String readProcessFile(int pid, @NonNull String fileName) {
        File file = new File(processFilePath(pid, fileName));
        if (!file.isFile() && !file.exists()) return null;

        try (InputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[PROCESS_FILE_MAX_READ_SIZE];
            int length = inputStream.read(buffer);
            if (length <= 0) return null;
            return new String(buffer, 0, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
