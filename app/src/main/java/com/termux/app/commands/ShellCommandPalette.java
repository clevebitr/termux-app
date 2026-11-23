package com.termux.app.commands;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
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
 * <p>The dialog is a Material 3 surface instead of the platform dialog frame: the search field is the header,
 * the rows show the title, the category and the shortcut of a command, and a disabled command is dimmed instead
 * of hidden, so that the user can tell why nothing happens when it is picked. While the dialog is open it also
 * takes the shortcut of a command and runs it, which makes the shortcuts that are shown in the list usable
 * without closing the palette.</p>
 */
public final class ShellCommandPalette {

    private ShellCommandPalette() {
    }

    /** Show the palette with the given commands. */
    public static void show(@NonNull Activity activity, @NonNull List<ShellCommand> commands) {
        if (activity.isFinishing() || commands.isEmpty()) return;

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_command_palette, null, false);
        TextInputEditText input = content.findViewById(R.id.command_palette_input);
        ListView list = content.findViewById(R.id.command_palette_list);
        TextView empty = content.findViewById(R.id.command_palette_empty);
        if (input == null || list == null || empty == null) return;

        final List<ShellCommand> filtered = new ArrayList<>(commands);
        final CommandAdapter adapter = new CommandAdapter(activity, filtered);
        list.setAdapter(adapter);

        // The list has a fixed height of 360dp in the layout, which is taller than the space that is left on a
        // small screen once the soft keyboard is shown, so the height is capped to the space that is actually
        // available instead of pushing the search field off the screen.
        ViewGroup.LayoutParams listParams = list.getLayoutParams();
        listParams.height = Math.min(listParams.height, maxListHeight(activity));
        list.setLayoutParams(listParams);

        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
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

        dialog.setOnShowListener(it -> {
            input.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) activity.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_command_palette);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        dialog.show();
    }

    /** @return The height that the list of the palette may take on the screen of {@code context}. */
    private static int maxListHeight(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        // The search field, the buttons of the dialog and the soft keyboard take the rest of the screen.
        int reserved = Math.round(240 * density);
        return Math.max(Math.round(160 * density), screenHeight - reserved);
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

    /** Renders one command of the palette with the row layout of the dialog. */
    private static final class CommandAdapter extends BaseAdapter {

        @NonNull
        private final LayoutInflater mInflater;
        @NonNull
        private final List<ShellCommand> mCommands;

        CommandAdapter(@NonNull Context context, @NonNull List<ShellCommand> commands) {
            mInflater = LayoutInflater.from(context);
            mCommands = commands;
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

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) row = mInflater.inflate(R.layout.item_command_palette, parent, false);

            ShellCommand command = getItem(position);
            if (command == null) return row;

            TextView titleView = row.findViewById(R.id.command_title);
            TextView categoryView = row.findViewById(R.id.command_category);
            TextView shortcutView = row.findViewById(R.id.command_shortcut);

            titleView.setText(command.title);
            categoryView.setText(command.category);

            if (command.shortcut == null || command.shortcut.isEmpty()) {
                shortcutView.setVisibility(View.GONE);
            } else {
                shortcutView.setVisibility(View.VISIBLE);
                shortcutView.setText(command.shortcut);
            }

            // A command that cannot run is dimmed instead of hidden, so that the list does not move under the
            // finger of the user while the state of the terminal changes.
            row.setAlpha(command.enabled ? 1f : 0.45f);
            return row;
        }
    }
}
