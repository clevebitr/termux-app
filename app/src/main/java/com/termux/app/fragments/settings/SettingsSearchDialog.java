package com.termux.app.fragments.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialog of the search of the settings, which filters the entries of all pages by their title, their
 * summary and the title of their page, and opens the page of the entry that the user picks.
 */
public final class SettingsSearchDialog {

    private SettingsSearchDialog() {
    }

    /** Show the search dialog, which opens the page of the entry that the user picks from {@code fragment}. */
    public static void show(@NonNull PreferenceFragmentCompat fragment) {
        final Context context = fragment.getContext();
        if (context == null) return;

        final List<SettingsSearchIndex.Entry> entries = SettingsSearchIndex.build(context);

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);

        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.settings_search_hint);
        container.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ListView listView = new ListView(context);
        container.addView(listView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ArrayAdapter<SettingsSearchIndex.Entry> adapter =
            new ArrayAdapter<SettingsSearchIndex.Entry>(context, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>()) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    SettingsSearchIndex.Entry entry = getItem(position);
                    if (entry == null) return view;

                    TextView titleView = view.findViewById(android.R.id.text1);
                    titleView.setText(entry.title);

                    // The second line shows which page the entry is on, so that two entries with the same
                    // title, like the header of a page and a setting, can be told apart.
                    TextView summaryView = view.findViewById(android.R.id.text2);
                    StringBuilder detail = new StringBuilder(entry.pageTitle);
                    if (entry.summary != null && entry.summary.length() > 0)
                        detail.append(" · ").append(entry.summary);
                    summaryView.setText(detail);
                    return view;
                }
            };
        listView.setAdapter(adapter);

        final AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(R.string.action_search_settings)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
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

        adapter.addAll(SettingsSearchIndex.search(entries, null));
        input.requestFocus();
    }
}
