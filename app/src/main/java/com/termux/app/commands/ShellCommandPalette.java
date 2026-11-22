package com.termux.app.commands;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;

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
 * <p>The dialog is built in code instead of a layout so that it follows the theme of the app without a new
 * resource, and it is dismissed before a command runs, so that a command that opens another screen does not
 * race with the dismissal.</p>
 */
public final class ShellCommandPalette {

    private ShellCommandPalette() {
    }

    /** Show the palette with the given commands. */
    public static void show(@NonNull Activity activity, @NonNull List<ShellCommand> commands) {
        if (activity.isFinishing() || commands.isEmpty()) return;

        Context context = activity;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 8);
        content.setPadding(padding, padding, padding, 0);

        EditText input = new EditText(context);
        input.setHint(R.string.command_palette_hint);
        input.setSingleLine(true);
        content.addView(input);

        TextView empty = new TextView(context);
        empty.setText(R.string.command_palette_empty);
        empty.setVisibility(View.GONE);
        content.addView(empty);

        ListView list = new ListView(context);
        content.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 360)));

        List<ShellCommand> filtered = new ArrayList<>(commands);
        ArrayAdapter<ShellCommand> adapter = new ArrayAdapter<ShellCommand>(context,
            android.R.layout.simple_list_item_1, filtered) {
            @Override
            public boolean isEnabled(int position) {
                return getItem(position) != null && getItem(position).enabled;
            }
        };
        list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setView(content)
            .create();

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
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        input.setOnEditorActionListener((view, actionId, event) -> {
            executeFirst.run();
            return true;
        });

        dialog.setOnShowListener(it -> {
            input.requestFocus();
            InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
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
        for (ShellCommand command : commands) {
            if (command.toString().toLowerCase(Locale.ROOT).contains(needle)) out.add(command);
        }
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
