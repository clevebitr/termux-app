package com.termux.app.terminal;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.view.KeyboardUtils;
import com.termux.view.TerminalView;

/**
 * The search bar of the terminal, which finds the matches of a query in the screen and the transcript of
 * the active session, highlights them and lets the user jump between them.
 *
 * The bar is overlayed on top of the terminal instead of being part of its layout, so that the terminal
 * grid is not resized when it is shown, which would change the number of rows and columns.
 */
public final class TerminalSearchController {

    @NonNull
    private final TermuxActivity mActivity;

    @Nullable
    private View mSearchBar;
    @Nullable
    private EditText mTextInput;
    @Nullable
    private TextView mResults;

    public TerminalSearchController(@NonNull TermuxActivity activity) {
        mActivity = activity;
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
    }

    /** Show the search bar and focus its text input, so that the user can type a query. */
    public void show() {
        if (mSearchBar == null || mTextInput == null || mActivity.getTerminalView() == null) return;
        if (isVisible()) {
            mTextInput.requestFocus();
            return;
        }

        mSearchBar.setVisibility(View.VISIBLE);
        mTextInput.setText(null);
        mTextInput.requestFocus();
        KeyboardUtils.showSoftKeyboard(mActivity, mTextInput);
        updateResults();
    }

    /** Hide the search bar and clear the search highlights. */
    public void hide() {
        if (mSearchBar == null || mTextInput == null || !isVisible()) return;

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

        terminalView.setSearchQuery(mTextInput.getText().toString());
        updateResults();
    }

    /** Select the next or the previous match of the query. */
    private void findNext(boolean forward) {
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null) return;

        terminalView.findNextSearchMatch(forward);
        updateResults();
    }

    /** Show which of the matches of the query is selected, like `2/7`. */
    private void updateResults() {
        TerminalView terminalView = mActivity.getTerminalView();
        if (mResults == null || mTextInput == null || terminalView == null) return;

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
