package com.termux.app.terminal.io;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter {

        final TermuxActivity mActivity;
        String mSavedTextInput;

        public PageAdapter(TermuxActivity activity, String savedTextInput) {
            this.mActivity = activity;
            this.mSavedTextInput = savedTextInput;
        }

        /** The number of the extra keys panels, which are the first pages of the pager. */
        private int getExtraKeysPanelsCount() {
            return mActivity.getTermuxTerminalExtraKeys().getExtraKeysPanelCount();
        }

        @Override
        public int getCount() {
            // The extra keys panels are followed by the text input view, which can be reached by swiping
            // to the right, like between the panels themselves.
            return getExtraKeysPanelsCount() + 1;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            // The pages have to be recreated when the number of extra keys panels changes, since they are
            // created from the panels of the `extra-keys-panels` or `extra-keys` property.
            return POSITION_NONE;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            View layout;
            List<ExtraKeysInfo> extraKeysPanels = mActivity.getTermuxTerminalExtraKeys().getExtraKeysPanels();
            if (position < extraKeysPanels.size()) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys());
                extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
                extraKeysView.setShowPopupHints(mActivity.getProperties().shouldExtraKeysPopupHintsBeShown());
                extraKeysView.setShowTileBackground(mActivity.getProperties().shouldExtraKeysTileBackgroundBeShown());
                mActivity.addExtraKeysView(extraKeysView);
                extraKeysView.reload(extraKeysPanels.get(position), mActivity.getTerminalToolbarDefaultHeight());

                // Apply the extra keys fix if enabled in prefs, but only for the first panel, since it
                // registers a layout listener and would otherwise be applied once for each panel.
                if (position == 0 && mActivity.getProperties().isUsingFullScreen() && mActivity.getProperties().isUsingFullScreenWorkAround()) {
                    FullScreenWorkAround.apply(mActivity);
                }

            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false);
                final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);

                // The text color is hardcoded to white in the layout, so it must be set to the color
                // being used by the extra keys view, since the toolbar background may be light.
                mActivity.setTerminalToolbarTextInputViewColors(editText);

                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }

                editText.setOnEditorActionListener((v, actionId, event) -> {
                    TerminalSession session = mActivity.getCurrentSession();
                    if (session != null) {
                        if (session.isRunning()) {
                            String textToSend = editText.getText().toString();
                            if (textToSend.length() == 0) textToSend = "\r";
                            session.write(textToSend);
                        } else {
                            mActivity.getTermuxTerminalSessionClient().removeFinishedSession(session);
                        }
                        editText.setText("");
                    }
                    return true;
                });
            }
            collection.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            if (view instanceof ExtraKeysView) {
                mActivity.removeExtraKeysView((ExtraKeysView) view);
            } else if (view instanceof View) {
                // Preserve the text typed in the text input view, since the pages are recreated when the
                // extra keys panels change, like when a property is set from the settings.
                EditText editText = ((View) view).findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) mSavedTextInput = editText.getText().toString();
            }

            collection.removeView((View) view);
        }

    }



    public static class OnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        final TermuxActivity mActivity;
        final ViewPager mTerminalToolbarViewPager;

        public OnPageChangeListener(TermuxActivity activity, ViewPager viewPager) {
            this.mActivity = activity;
            this.mTerminalToolbarViewPager = viewPager;
        }

        @Override
        public void onPageSelected(int position) {
            if (position < mActivity.getTermuxTerminalExtraKeys().getExtraKeysPanelCount()) {
                mActivity.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) editText.requestFocus();
            }
        }

    }

}
