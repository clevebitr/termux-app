package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    /** The maximum number of bytes to read from the files of a process for the session details. */
    private static final int PROCESS_FILE_MAX_READ_SIZE = 4096;

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);
        TextView sessionDetailsView = sessionRowView.findViewById(R.id.session_details);

        TerminalSession sessionAtRow = getItem(position).getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            sessionDetailsView.setText(null);
            sessionDetailsView.setVisibility(View.GONE);
            return sessionRowView;
        }

        boolean shouldEnableDarkTheme = ThemeUtils.shouldEnableDarkTheme(mActivity, NightMode.getAppNightMode().getName());

        if (shouldEnableDarkTheme) {
            sessionRowView.setBackground(
                ContextCompat.getDrawable(mActivity, R.drawable.session_background_black_selected)
            );
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + (position + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = shouldEnableDarkTheme ? Color.WHITE : Color.BLACK;
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
        sessionTitleView.setTextColor(color);

        // Show the info about the session below the title, like the pid and the name of the foreground
        // process and the current working directory of the shell.
        String sessionDetails = getSessionDetails(sessionAtRow);
        sessionDetailsView.setText(sessionDetails);
        sessionDetailsView.setVisibility(sessionDetails == null ? View.GONE : View.VISIBLE);
        sessionDetailsView.setTextColor((color & 0x00FFFFFF) | 0xB0000000);
        return sessionRowView;
    }

    /**
     * Get the info about the {@code session}, like the pid and the name of the foreground process and the
     * current working directory of the shell, or {@code null} if the info cannot be read, like if the
     * session is not running or access to the files of the process is not granted.
     */
    @Nullable
    private String getSessionDetails(@NonNull TerminalSession session) {
        int pid = session.getPid();
        if (pid <= 0) return null;

        List<String> details = new ArrayList<>();
        details.add(mActivity.getString(R.string.termux_session_details_pid, pid));

        String foregroundProcessName = getForegroundProcessName(pid);
        if (foregroundProcessName != null) details.add(foregroundProcessName);

        String currentWorkingDirectory = getCurrentWorkingDirectory(pid);
        if (currentWorkingDirectory != null) details.add(currentWorkingDirectory);

        return TextUtils.join(" · ", details);
    }

    /**
     * Get the name of the foreground process of the terminal of the session, which is the process that
     * the terminal device of the session is attached to, or {@code null} if it cannot be read.
     */
    @Nullable
    private static String getForegroundProcessName(int pid) {
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

    /** Get the name of the process, or {@code null} if it cannot be read. */
    @Nullable
    private static String getProcessName(int pid) {
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
     * Get the current working directory of the process, with the home directory of the app shortened to
     * {@code ~}, or {@code null} if it cannot be read.
     */
    @Nullable
    private static String getCurrentWorkingDirectory(int pid) {
        try {
            String currentWorkingDirectory = new File(getProcessFilePath(pid, "cwd")).getCanonicalPath();

            String homeDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;
            if (currentWorkingDirectory.equals(homeDirectory)) return "~";
            if (currentWorkingDirectory.startsWith(homeDirectory + "/"))
                return "~" + currentWorkingDirectory.substring(homeDirectory.length());

            return currentWorkingDirectory;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getProcessFilePath(int pid, @NonNull String fileName) {
        return "/proc/" + pid + "/" + fileName;
    }

    /**
     * Read the contents of the {@code fileName} of the process, or {@code null} if it cannot be read, like
     * if the process does not exist anymore or access to it is not granted.
     */
    @Nullable
    private static String readProcessFile(int pid, @NonNull String fileName) {
        File processFile = new File(getProcessFilePath(pid, fileName));
        if (!processFile.isFile() || !processFile.canRead()) return null;

        try (InputStream in = new FileInputStream(processFile)) {
            byte[] buffer = new byte[PROCESS_FILE_MAX_READ_SIZE];
            int length = in.read(buffer);
            if (length <= 0) return null;

            return new String(buffer, 0, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }

}
