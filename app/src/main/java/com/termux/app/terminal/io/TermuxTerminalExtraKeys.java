package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    /**
     * The extra keys panels, where each panel is a set of extra keys that the user can swipe between in
     * the terminal toolbar. The list will contain at least one panel, and more than one if the
     * `extra-keys-panels` property is set.
     */
    private List<ExtraKeysInfo> mExtraKeysPanels;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    /**
     * The values of the `extra-keys-panels`, `extra-keys` and `extra-keys-style` properties that the
     * current extra keys panels were created from, used to detect whether the panels must be recreated
     * when the properties are reloaded with {@link #reloadExtraKeys()}.
     */
    private String mExtraKeysProperties;

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }

    /**
     * Get the values of the extra keys properties that the panels are created from, in a single string
     * that can be compared to detect whether the panels must be reloaded.
     */
    private String getExtraKeysProperties() {
        StringBuilder properties = new StringBuilder();
        properties.append(mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_PANELS, true));
        properties.append('\0');
        properties.append(mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true));
        properties.append('\0');
        properties.append(mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true));
        return properties.toString();
    }

    /**
     * Reload the extra keys panels and style from the properties, if they have changed.
     *
     * @return Whether the panels changed, in which case the views of the extra keys must be recreated,
     *         since the views are created from the panels.
     */
    public boolean reloadExtraKeys() {
        String extraKeysProperties = getExtraKeysProperties();
        if (extraKeysProperties.equals(mExtraKeysProperties)) return false;

        setExtraKeys();
        return true;
    }


    /**
     * Set the terminal extra keys panels and style from the `extra-keys-panels` property, or from the
     * `extra-keys` property as a single panel if the former is not set.
     */
    private void setExtraKeys() {
        mExtraKeysPanels = new ArrayList<>();
        mExtraKeysProperties = getExtraKeysProperties();

        // The mMap stores the extra key and style string values while loading properties
        // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
        // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
        String extraKeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
        String extraKeysPanels = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_PANELS, true);
        String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

        ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
        if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
            Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
            extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
        }

        if (extraKeysPanels != null && !extraKeysPanels.isEmpty()) {
            try {
                // Each panel is a matrix of rows of keys, so the property is a JSON array of matrices.
                JSONArray panelsArray = new JSONArray(extraKeysPanels);
                for (int i = 0; i < panelsArray.length(); i++) {
                    JSONArray panelArray = panelsArray.optJSONArray(i);
                    if (panelArray == null) {
                        throw new JSONException("The panel at index " + i + " is not an array of rows of keys");
                    }
                    mExtraKeysPanels.add(new ExtraKeysInfo(panelArray.toString(), extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES));
                }

                if (mExtraKeysPanels.isEmpty()) {
                    throw new JSONException("The property does not define any panel");
                }
            } catch (JSONException e) {
                Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_PANELS + "\" property from the properties file: " + e.toString(), true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_PANELS + "\" property from the properties file: ", e);

                // Fall back to the `extra-keys` property as a single panel.
                mExtraKeysPanels.clear();
            }
        }

        if (mExtraKeysPanels.isEmpty()) {
            try {
                mExtraKeysPanels.add(new ExtraKeysInfo(extraKeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES));
            } catch (JSONException e) {
                Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

                mExtraKeysPanels.clear();
                try {
                    mExtraKeysPanels.add(new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES));
                } catch (JSONException e2) {
                    Logger.showToast(mActivity, "Can't create default extra keys",true);
                    Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e2);
                    mExtraKeysPanels.clear();
                }
            }
        }
    }

    /** Get the extra keys panels that the user can swipe between in the terminal toolbar. */
    public List<ExtraKeysInfo> getExtraKeysPanels() {
        return mExtraKeysPanels;
    }

    /** Get the number of extra keys panels. */
    public int getExtraKeysPanelCount() {
        return mExtraKeysPanels.size();
    }

    /**
     * Get the maximum number of rows in the extra keys panels, which is the number of rows that the
     * terminal toolbar must be able to display.
     */
    public int getExtraKeysPanelsMaxRows() {
        int maxRows = 0;
        for (ExtraKeysInfo extraKeysPanel : mExtraKeysPanels)
            maxRows = Math.max(maxRows, extraKeysPanel.getMatrix().length);
        return maxRows;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        // Send the keys to the terminal view of the active pane, which may have changed since the
        // extra keys were created, like when split screen is enabled.
        setTerminalView(mActivity.getTerminalView());

        if ("KEYBOARD".equals(key)) {
            TermuxTerminalViewClient viewClient = mActivity.getTermuxTerminalViewClient();
            if (viewClient != null)
                viewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mActivity.getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        } else if ("SEARCH".equals(key)) {
            mActivity.showTerminalSearch();
        } else if ("SPLIT_V".equals(key)) {
            // A second press changes the orientation of the panes instead of closing the split screen,
            // so that the key can be used to switch between the two layouts.
            mActivity.setSplitScreen(true, LinearLayout.VERTICAL);
        } else if ("SPLIT_H".equals(key)) {
            mActivity.setSplitScreen(true, LinearLayout.HORIZONTAL);
        } else if ("SPLIT_CLOSE".equals(key)) {
            mActivity.setSplitScreen(false, mActivity.getSplitScreenOrientation());
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mActivity.getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
