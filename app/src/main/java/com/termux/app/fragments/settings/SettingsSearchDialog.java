package com.termux.app.fragments.settings;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialog of the search of the settings, which filters the entries of all pages by their title, their
 * summary and the title of their page, and opens the page of the entry that the user picks.
 *
 * <p>The dialog is a Material 3 surface like the command palette: the search field is the header, the rows show
 * the title of the entry and the page and summary that tell two entries with the same title apart, and an empty
 * query lists every entry so that the settings can also be browsed from here.</p>
 */
public final class SettingsSearchDialog {

    /** The height of a row in dp, which is the height of the row layout with its padding. */
    private static final int ROW_HEIGHT_DP = 60;
    /** The number of rows that the list shows before it scrolls. */
    private static final int MAX_VISIBLE_ROWS = 6;

    private SettingsSearchDialog() {
    }

    /** Show the search dialog, which opens the page of the entry that the user picks from {@code fragment}. */
    public static void show(@NonNull PreferenceFragmentCompat fragment) {
        final Context context = fragment.getContext();
        if (context == null) return;

        final List<SettingsSearchIndex.Entry> entries = SettingsSearchIndex.build(context);

        View content = LayoutInflater.from(context).inflate(R.layout.dialog_settings_search, null, false);
        final TextInputEditText input = content.findViewById(R.id.settings_search_input);
        final ListView listView = content.findViewById(R.id.settings_search_list);
        final TextView empty = content.findViewById(R.id.settings_search_empty);
        if (input == null || listView == null || empty == null) return;

        final ArrayAdapter<SettingsSearchIndex.Entry> adapter = new ArrayAdapter<SettingsSearchIndex.Entry>(
            context, R.layout.item_settings_search, R.id.settings_search_title, new ArrayList<>()) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                SettingsSearchIndex.Entry entry = getItem(position);
                if (entry == null) return view;

                TextView titleView = view.findViewById(R.id.settings_search_title);
                titleView.setText(entry.title);

                // The second line shows which page the entry is on, so that two entries with the same
                // title, like the header of a page and a setting, can be told apart.
                TextView summaryView = view.findViewById(R.id.settings_search_summary);
                StringBuilder detail = new StringBuilder(entry.pageTitle);
                if (entry.summary != null && entry.summary.length() > 0)
                    detail.append(" · ").append(entry.summary);
                summaryView.setText(detail);
                return view;
            }
        };
        listView.setAdapter(adapter);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setView(content)
            .create();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                List<SettingsSearchIndex.Entry> matches = SettingsSearchIndex.search(entries, s.toString());
                adapter.clear();
                adapter.addAll(matches);
                adapter.notifyDataSetChanged();
                empty.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
                updateListHeight(context, listView, matches.size());
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            SettingsSearchIndex.Entry entry = adapter.getItem(position);
            if (entry == null) return;

            dialog.dismiss();
            SettingsSearchNavigation.open(fragment, entry);
        });

        dialog.show();

        // Show the keyboard right away, since the dialog exists to type a query, and show every entry until
        // the user types something, so that the settings can also be browsed from here.
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        List<SettingsSearchIndex.Entry> all = SettingsSearchIndex.search(entries, null);
        adapter.addAll(all);
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        updateListHeight(context, listView, all.size());
        input.requestFocus();
    }

    /**
     * Set the height of the list to the number of rows it shows, so that the dialog is not mostly empty when a
     * query matches one setting, but never taller than the screen allows on a small device.
     */
    private static void updateListHeight(@NonNull Context context, @NonNull ListView listView, int rowCount) {
        float density = context.getResources().getDisplayMetrics().density;
        int rowHeight = Math.round(ROW_HEIGHT_DP * density);
        int desired = rowCount > 0 ? rowHeight * Math.min(MAX_VISIBLE_ROWS, rowCount) : rowHeight;
        int available = context.getResources().getDisplayMetrics().heightPixels
            - Math.round(280 * density);
        int height = Math.max(Math.round(160 * density),
            Math.min(desired, Math.min(Math.round(360 * density), available)));

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        if (params.height == height) return;
        params.height = height;
        listView.setLayoutParams(params);
    }
}
