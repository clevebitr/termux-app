package com.termux.app.commands;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.widget.ColorSchemeColors;
import com.termux.app.widget.TerminalDialogBuilder;
import com.termux.app.widget.TerminalDialogStyles;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The command palette: a searchable list of the actions of the app.
 *
 * <p>The palette is the single entry point that the fork adds for actions that used to be reachable only
 * from a menu, a drawer button or a shortcut. It renders the commands of
 * {@link ShellCommandCatalog} and filters them by a case insensitive substring of the title, the category
 * or the shortcut, so that the same list can be searched by command name or by the keys the user knows.</p>
 *
 * <p>The dialog follows the colors of the current session's termux color scheme, like the sessions drawer and
 * the terminal context menu, instead of the colors of the app theme: the search field is the header, the rows
 * show the title, the category and the shortcut of a command, and a disabled command is dimmed instead of
 * hidden, so that the user can tell why nothing happens when it is picked. While the dialog is open it also
 * takes the shortcut of a command and runs it, which makes the shortcuts that are shown in the list usable
 * without closing the palette.</p>
 */
public final class ShellCommandPalette {

    /** The height of a row in dp, which is the height of the row with its padding. */
    private static final int ROW_HEIGHT_DP = 60;
    /** The number of rows that the list shows before it scrolls. */
    private static final int MAX_VISIBLE_ROWS = 8;
    /** The height of the list in dp, which is the maximum on a normal screen. */
    private static final int MAX_LIST_HEIGHT_DP = 360;

    private static final float ROW_PADDING_HORIZONTAL_DP = 12;
    private static final float ROW_PADDING_VERTICAL_DP = 10;
    private static final float TITLE_FONT_SIZE_SP = 14;
    private static final float CATEGORY_FONT_SIZE_SP = 11;
    private static final float CHIP_CORNER_RADIUS_DP = 8;

    private ShellCommandPalette() {
    }

    /** Show the palette with the given commands, colored with the color scheme of {@code session}. */
    public static void show(@NonNull Activity activity, @NonNull List<ShellCommand> commands,
                            @NonNull TerminalSession session) {
        if (activity.isFinishing() || commands.isEmpty()) return;

        // The palette follows the terminal color scheme, like the sessions drawer and the context menu, so the
        // colors are resolved once and handed to the views instead of coming from the app theme.
        int foreground = foregroundOf(session);
        int background = TerminalDialogStyles.getBackgroundColor(null, foreground);
        int accent = TerminalDialogStyles.getAccentColor(null, foreground);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText input = createInput(activity, foreground);
        content.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(createDivider(activity, foreground));

        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        int listPadding = TerminalDialogStyles.dp(activity, 8);
        list.setPadding(listPadding, TerminalDialogStyles.dp(activity, 2),
            listPadding, TerminalDialogStyles.dp(activity, 2));
        content.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, listHeight(activity, commands.size())));

        TextView empty = new TextView(activity);
        empty.setText(R.string.command_palette_empty);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, CATEGORY_FONT_SIZE_SP);
        empty.setTextColor(TerminalDialogStyles.withAlpha(foreground, 0.6f));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, TerminalDialogStyles.dp(activity, 24), 0, TerminalDialogStyles.dp(activity, 24));
        empty.setVisibility(View.GONE);
        content.addView(empty, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final List<ShellCommand> filtered = new ArrayList<>(commands);
        final CommandAdapter adapter = new CommandAdapter(activity, filtered, foreground, accent);
        list.setAdapter(adapter);

        TerminalDialogBuilder.Style style = new TerminalDialogBuilder.Style(foreground, background, accent,
            TerminalDialogStyles.DIALOG_CORNER_DP, 1, TITLE_FONT_SIZE_SP, false);
        final Dialog dialog = TerminalDialogBuilder.create(activity, style)
            .setContent(content)
            .setScrollable(false)
            .show();

        Runnable executeFirst = () -> {
            for (ShellCommand command : filtered) {
                if (command.enabled) {
                    dialog.dismiss();
                    command.execute();
                    return;
                }
            }
        };

        list.setOnItemClickListener((parent, view, position, id) -> {
            ShellCommand command = filtered.get(position);
            if (command == null || !command.enabled) return;
            dialog.dismiss();
            command.execute();
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(commands, filtered, s == null ? "" : s.toString());
                adapter.notifyDataSetChanged();
                empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                setListHeight(activity, list, filtered.size());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        input.setOnEditorActionListener((view, actionId, event) -> {
            executeFirst.run();
            return true;
        });

        // The shortcut of a command can be used while the palette is open, which is the only place where taking
        // the key from the terminal is harmless. The search field holds the focus, so the listener is installed on
        // it and on the list instead of on the dialog.
        View.OnKeyListener shortcutListener = (view, keyCode, event) -> {
            for (ShellCommand command : filtered) {
                if (!command.enabled) continue;
                if (!ShellCommandShortcut.matches(command.shortcut, event)) continue;
                dialog.dismiss();
                command.execute();
                return true;
            }
            return false;
        };
        input.setOnKeyListener(shortcutListener);
        list.setOnKeyListener(shortcutListener);

        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    /** @return The text color of the palette, which is the foreground color of the color scheme. */
    private static int foregroundOf(@NonNull TerminalSession session) {
        int[] colors = session.getEmulator() != null ? session.getEmulator().mColors.mCurrentColors : null;
        if (colors != null) return colors[TextStyle.COLOR_INDEX_FOREGROUND];
        return ColorSchemeColors.getThemeColor(ColorSchemeColors.THEME_FOREGROUND);
    }

    /** @return The search field of the palette, which is the header of the window. */
    @NonNull
    private static EditText createInput(@NonNull Activity activity, int foregroundColor) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setHint(R.string.command_palette_hint);
        input.setTextColor(foregroundColor);
        input.setHintTextColor(TerminalDialogStyles.withAlpha(foregroundColor, 0.55f));
        input.setHighlightColor(TerminalDialogStyles.withAlpha(foregroundColor, 0.20f));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setPadding(TerminalDialogStyles.dp(activity, 16), TerminalDialogStyles.dp(activity, 12),
            TerminalDialogStyles.dp(activity, 16), TerminalDialogStyles.dp(activity, 8));
        return input;
    }

    /** @return A 1dp line below the search field, which separates the query from the results. */
    @NonNull
    private static View createDivider(@NonNull Activity activity, int foregroundColor) {
        View divider = new View(activity);
        divider.setBackgroundColor(TerminalDialogStyles.withAlpha(foregroundColor, 0.13f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, TerminalDialogStyles.dp(activity, 1));
        int margin = TerminalDialogStyles.dp(activity, 16);
        params.setMargins(margin, 0, margin, 0);
        divider.setLayoutParams(params);
        return divider;
    }

    /** @return The height of the list for {@code rowCount} rows, capped to the space the screen allows. */
    private static int listHeight(@NonNull Activity activity, int rowCount) {
        return computeListHeight(activity, rowCount);
    }

    /** Set the height of the list, so that a query with one result does not leave the window mostly empty. */
    private static void setListHeight(@NonNull Activity activity, @NonNull ListView list, int rowCount) {
        int height = computeListHeight(activity, rowCount);
        ViewGroup.LayoutParams params = list.getLayoutParams();
        if (params.height == height) return;
        params.height = height;
        list.setLayoutParams(params);
    }

    private static int computeListHeight(@NonNull Activity activity, int rowCount) {
        float density = activity.getResources().getDisplayMetrics().density;
        int rowHeight = Math.round(ROW_HEIGHT_DP * density);
        int desired = rowCount > 0 ? rowHeight * Math.min(MAX_VISIBLE_ROWS, rowCount) : 0;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        // The search field and the soft keyboard take the rest of the screen.
        int available = screenHeight - Math.round(240 * density);
        int max = Math.min(Math.round(MAX_LIST_HEIGHT_DP * density), available);
        return Math.min(desired, Math.max(rowHeight * 2, max));
    }

    /** Filter a candidate list into {@code out}. Pure, so that the matching rule can be reused and tested. */
    public static void filter(@NonNull List<ShellCommand> commands, @NonNull List<ShellCommand> out,
                              @NonNull String query) {
        out.clear();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            out.addAll(commands);
            return;
        }

        // A command whose shortcut is exactly what was typed is the most likely one, so it is listed first.
        String normalizedNeedle = ShellCommandShortcut.normalize(needle);
        for (ShellCommand command : commands) {
            if (command.shortcut != null
                && ShellCommandShortcut.normalize(command.shortcut).equals(normalizedNeedle)) {
                out.add(command);
            }
        }
        for (ShellCommand command : commands) {
            if (out.contains(command)) continue;
            if (command.toString().toLowerCase(Locale.ROOT).contains(needle)) out.add(command);
        }
    }

    /** Renders one command of the palette as a row of the terminal colored window. */
    private static final class CommandAdapter extends BaseAdapter {

        @NonNull
        private final Activity mActivity;
        @NonNull
        private final List<ShellCommand> mCommands;
        private final int mForegroundColor;
        private final int mAccentColor;

        CommandAdapter(@NonNull Activity activity, @NonNull List<ShellCommand> commands,
                       int foregroundColor, int accentColor) {
            mActivity = activity;
            mCommands = commands;
            mForegroundColor = foregroundColor;
            mAccentColor = accentColor;
        }

        @Override
        public int getCount() {
            return mCommands.size();
        }

        @Override
        public ShellCommand getItem(int position) {
            return mCommands.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            ShellCommand command = getItem(position);
            return command != null && command.enabled;
        }

        @SuppressLint("SetTextI18n")
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                holder = createRow();
                holder.row.setTag(holder);
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            ShellCommand command = getItem(position);
            if (command == null) return holder.row;

            holder.title.setText(command.title);
            holder.category.setText(command.category);

            if (command.shortcut == null || command.shortcut.isEmpty()) {
                holder.shortcut.setVisibility(View.GONE);
            } else {
                holder.shortcut.setVisibility(View.VISIBLE);
                holder.shortcut.setText(command.shortcut);
            }

            // A command that cannot run is dimmed instead of hidden, so that the list does not move under the
            // finger of the user while the state of the terminal changes.
            holder.row.setAlpha(command.enabled ? 1f : 0.45f);
            return holder.row;
        }

        /** @return A new row with the title, the category and the chip of the shortcut. */
        @NonNull
        private RowHolder createRow() {
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(TerminalDialogStyles.dp(mActivity, ROW_PADDING_HORIZONTAL_DP),
                TerminalDialogStyles.dp(mActivity, ROW_PADDING_VERTICAL_DP),
                TerminalDialogStyles.dp(mActivity, ROW_PADDING_HORIZONTAL_DP),
                TerminalDialogStyles.dp(mActivity, ROW_PADDING_VERTICAL_DP));
            row.setBackground(TerminalDialogStyles.createRowBackground(mActivity, mAccentColor));

            LinearLayout texts = new LinearLayout(mActivity);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(texts);

            TextView title = TerminalDialogStyles.createTextView(mActivity, mForegroundColor,
                TITLE_FONT_SIZE_SP, true, false);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView category = TerminalDialogStyles.createTextView(mActivity,
                TerminalDialogStyles.withAlpha(mForegroundColor, 0.6f), CATEGORY_FONT_SIZE_SP, true, false);
            category.setSingleLine(true);
            category.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(category, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView shortcut = TerminalDialogStyles.createTextView(mActivity, mAccentColor,
                CATEGORY_FONT_SIZE_SP, true, false);
            shortcut.setGravity(Gravity.CENTER);
            shortcut.setPadding(TerminalDialogStyles.dp(mActivity, 6), TerminalDialogStyles.dp(mActivity, 2),
                TerminalDialogStyles.dp(mActivity, 6), TerminalDialogStyles.dp(mActivity, 2));

            GradientDrawable chip = new GradientDrawable();
            chip.setShape(GradientDrawable.RECTANGLE);
            chip.setCornerRadius(TerminalDialogStyles.dp(mActivity, CHIP_CORNER_RADIUS_DP));
            chip.setColor(TerminalDialogStyles.withAlpha(mAccentColor, 0.16f));
            shortcut.setBackground(chip);

            LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            shortcutParams.setMarginStart(TerminalDialogStyles.dp(mActivity, 8));
            row.addView(shortcut, shortcutParams);

            return new RowHolder(row, title, category, shortcut);
        }
    }

    /** The views of a row of the palette, so that a recycled row does not have to look them up by id. */
    private static final class RowHolder {
        @NonNull final LinearLayout row;
        @NonNull final TextView title;
        @NonNull final TextView category;
        @NonNull final TextView shortcut;

        RowHolder(@NonNull LinearLayout row, @NonNull TextView title, @NonNull TextView category,
                  @NonNull TextView shortcut) {
            this.row = row;
            this.title = title;
            this.category = category;
            this.shortcut = shortcut;
        }
    }
}
