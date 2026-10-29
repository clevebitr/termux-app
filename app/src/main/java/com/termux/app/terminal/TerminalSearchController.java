package com.termux.app.terminal;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.view.KeyboardUtils;
import com.termux.view.TerminalView;

import java.util.List;

/**
 * The search bar of the terminal, which finds the matches of a query in the screen and the transcript of
 * the active session, highlights them and lets the user jump between them.
 *
 * The bar is overlayed on top of the terminal instead of being part of its layout, so that the terminal
 * grid is not resized when it is shown, which would change the number of rows and columns.
 *
 * The bar can search case sensitively and with a regular expression, and it remembers the queries that were
 * used recently, so that a query does not have to be typed again. The options and the history are stored by
 * {@link TerminalSearchPreferences}.
 */
public final class TerminalSearchController {

    @NonNull
    private final TermuxActivity mActivity;
    @NonNull
    private final TerminalSearchPreferences mPreferences;

    @Nullable
    private View mSearchBar;
    @Nullable
    private EditText mTextInput;
    @Nullable
    private TextView mResults;
    @Nullable
    private Button mCaseSensitiveButton;
    @Nullable
    private Button mRegexButton;
    @Nullable
    private ImageButton mHistoryButton;

    private boolean mCaseSensitive;
    private boolean mRegex;

    public TerminalSearchController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mPreferences = new TerminalSearchPreferences(activity);
        mCaseSensitive = mPreferences.isCaseSensitive();
        mRegex = mPreferences.isRegex();
    }

    /**
     * Find the views of the search bar and set up their listeners, which must be called when the activity
     * created its content view.
     */
    public void setUp() {
        mSearchBar = mActivity.findViewById(R.id.terminal_search_bar);
        if (mSearchBar == null) return;

        mTextInput = mActivity.findViewById(R.id.terminal_search_text_input);
        mResults = mActivity.findViewById(R.id.terminal_search_results);
        if (mTextInput == null) return;

        mTextInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                TerminalView terminalView = mActivity.getTerminalView();
                if (terminalView == null) return;

                terminalView.setSearchQuery(s.toString());
                updateResults();
            }
        });

        mTextInput.setOnEditorActionListener((v, actionId, event) -> {
            // Pressing the enter/search key on the soft or hardware keyboard selects the next match.
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                findNext(true);
                return true;
            }
            return false;
        });

        mActivity.findViewById(R.id.terminal_search_previous_button).setOnClickListener(v -> findNext(false));
        mActivity.findViewById(R.id.terminal_search_next_button).setOnClickListener(v -> findNext(true));
        mActivity.findViewById(R.id.terminal_search_close_button).setOnClickListener(v -> hide());

        mCaseSensitiveButton = mActivity.findViewById(R.id.terminal_search_case_button);
        mRegexButton = mActivity.findViewById(R.id.terminal_search_regex_button);
        mHistoryButton = mActivity.findViewById(R.id.terminal_search_history_button);

        if (mCaseSensitiveButton != null)
            mCaseSensitiveButton.setOnClickListener(v -> setSearchOptions(!mCaseSensitive, mRegex));
        if (mRegexButton != null)
            mRegexButton.setOnClickListener(v -> setSearchOptions(mCaseSensitive, !mRegex));
        if (mHistoryButton != null)
            mHistoryButton.setOnClickListener(v -> showHistory());

        updateOptionButtons();
        updateHistoryButton();
    }

    /** Show the search bar and focus its text input, so that the user can type a query. */
    public void show() {
        if (mSearchBar == null || mTextInput == null || mActivity.getTerminalView() == null) return;
        if (isVisible()) {
            mTextInput.requestFocus();
            return;
        }

        applySearchOptions();
        mSearchBar.setVisibility(View.VISIBLE);
        mTextInput.setText(null);
        mTextInput.requestFocus();
        KeyboardUtils.showSoftKeyboard(mActivity, mTextInput);
        updateResults();
    }

    /** Hide the search bar and clear the search highlights. */
    public void hide() {
        if (mSearchBar == null || mTextInput == null || !isVisible()) return;

        // The query that was looked for is the one that the user wants to find again later, so it is
        // remembered when the bar is closed instead of on every keystroke.
        addCurrentQueryToHistory();

        mSearchBar.setVisibility(View.GONE);
        mTextInput.setText(null);

        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null) return;

        terminalView.clearSearch();
        terminalView.requestFocus();
    }

    /** @return Whether the search bar is shown. */
    public boolean isVisible() {
        return mSearchBar != null && mSearchBar.getVisibility() == View.VISIBLE;
    }

    /**
     * Apply the query of the search bar to the current session again, which is required after a session
     * switch since the search matches of the previous session's buffer are then invalid.
     */
    public void refresh() {
        if (!isVisible() || mTextInput == null || mActivity.getTerminalView() == null) return;

        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null) return;

        // The other pane has its own view, which may not have seen the options yet.
        applySearchOptions();
        terminalView.setSearchQuery(mTextInput.getText().toString());
        updateResults();
    }

    /** Set whether the search is case sensitive and whether its query is a regular expression. */
    private void setSearchOptions(boolean caseSensitive, boolean regex) {
        if (mCaseSensitive == caseSensitive && mRegex == regex) return;

        mCaseSensitive = caseSensitive;
        mRegex = regex;
        mPreferences.setCaseSensitive(caseSensitive);
        mPreferences.setRegex(regex);

        updateOptionButtons();
        applySearchOptions();
        updateResults();
    }

    /** Apply the current options to the terminal view and re-run its search query with them. */
    private void applySearchOptions() {
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView != null) terminalView.setSearchOptions(mCaseSensitive, mRegex);
    }

    /** Show which options are enabled, since a toggle button has no visible state of its own. */
    private void updateOptionButtons() {
        updateOptionButton(mCaseSensitiveButton, mCaseSensitive);
        updateOptionButton(mRegexButton, mRegex);
    }

    private void updateOptionButton(@Nullable Button button, boolean enabled) {
        if (button == null) return;

        button.setSelected(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
        button.setTypeface(button.getTypeface(), enabled ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /** Show the recent queries, so that one of them can be used again without typing it. */
    private void showHistory() {
        if (mTextInput == null) return;

        List<String> history = mPreferences.getHistory();
        if (history.isEmpty()) {
            mActivity.showToast(mActivity.getString(R.string.action_search_history_empty), false);
            return;
        }

        new AlertDialog.Builder(mActivity)
            .setTitle(R.string.action_search_history_title)
            .setItems(history.toArray(new String[0]), (dialog, which) -> {
                if (which < 0 || which >= history.size()) return;
                String query = history.get(which);
                mPreferences.addToHistory(query);
                mTextInput.setText(query);
                mTextInput.setSelection(mTextInput.getText().length());
                updateHistoryButton();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Record the query of the bar, so that it can be picked from the history later. */
    private void addCurrentQueryToHistory() {
        if (mTextInput == null) return;

        String query = mTextInput.getText().toString().trim();
        if (query.isEmpty()) return;

        mPreferences.addToHistory(query);
        updateHistoryButton();
    }

    private void updateHistoryButton() {
        if (mHistoryButton == null) return;

        boolean hasHistory = !mPreferences.getHistory().isEmpty();
        mHistoryButton.setEnabled(hasHistory);
        mHistoryButton.setAlpha(hasHistory ? 1f : 0.4f);
    }

    /** Select the next or the previous match of the query. */
    private void findNext(boolean forward) {
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null) return;

        terminalView.findNextSearchMatch(forward);
        updateResults();
        addCurrentQueryToHistory();
    }

    /** Show which of the matches of the query is selected, like `2/7`. */
    private void updateResults() {
        TerminalView terminalView = mActivity.getTerminalView();
        if (mResults == null || mTextInput == null || terminalView == null) return;

        if (terminalView.isSearchRegexInvalid()) {
            mResults.setText(R.string.action_search_invalid_regex);
            return;
        }

        int count = terminalView.getSearchMatchCount();
        if (count == 0) {
            // Do not show "0/0" for an empty query, only if the query has no matches.
            boolean hasQuery = mTextInput.getText().length() > 0;
            mResults.setText(hasQuery ? mActivity.getString(R.string.action_search_results, 0, 0) : "");
        } else {
            mResults.setText(mActivity.getString(R.string.action_search_results,
                terminalView.getSearchMatchIndex() + 1, count));
        }
    }
}
