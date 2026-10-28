package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.app.terminal.io.KeyboardShortcut;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.data.TermuxUrlUtils;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import androidx.drawerlayout.widget.DrawerLayout;

public class TermuxTerminalViewClient extends TermuxTerminalViewClientBase {

    final TermuxActivity mActivity;

    /**
     * The {@link TerminalView} of the pane that this client controls. A {@link TermuxActivity} may
     * show two panes when split screen is enabled, and each pane has its own client, since the
     * callbacks of a view must act on the view that received the event instead of the active pane.
     */
    final TerminalView mTerminalView;

    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /** Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys. */
    boolean mVirtualControlKeyDown, mVirtualFnKeyDown;

    /** Whether the long press action of the volume key that is currently held down has already been
     * performed, so that the single press action is not also performed when the key is released. */
    private boolean mVolumeKeyLongPressTriggered;

    private Runnable mShowSoftKeyboardRunnable;

    private boolean mShowSoftKeyboardIgnoreOnce;
    private boolean mShowSoftKeyboardWithDelayOnce;

    private boolean mTerminalCursorBlinkerStateAlreadySet;

    private List<KeyboardShortcut> mSessionShortcuts;

    private static final String LOG_TAG = "TermuxTerminalViewClient";

    public TermuxTerminalViewClient(TermuxActivity activity, @NonNull TerminalView terminalView,
                                    TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mActivity = activity;
        this.mTerminalView = terminalView;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    public TermuxActivity getActivity() {
        return mActivity;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public void onCreate() {
        onReloadProperties();

        mTerminalView.setTextSize(mActivity.getPreferences().getFontSize());
        mTerminalView.setKeepScreenOn(mActivity.getPreferences().shouldKeepScreenOn());

        setTerminalViewFocusChangeListener();
    }

    /**
     * Should be called when mActivity.onStart() is called
     */
    public void onStart() {
        // Set {@link TerminalView#TERMINAL_VIEW_KEY_LOGGING_ENABLED} value
        // Also required if user changed the preference from {@link TermuxSettings} activity and returns
        boolean isTerminalViewKeyLoggingEnabled = mActivity.getPreferences().isTerminalViewKeyLoggingEnabled();
        mTerminalView.setIsTerminalViewKeyLoggingEnabled(isTerminalViewKeyLoggingEnabled);

        // Piggyback on the terminal view key logging toggle for now, should add a separate toggle in future
        mActivity.getTermuxActivityRootView().setIsRootViewLoggingEnabled(isTerminalViewKeyLoggingEnabled);
        ViewUtils.setIsViewUtilsLoggingEnabled(isTerminalViewKeyLoggingEnabled);
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        // Show the soft keyboard if required
        setSoftKeyboardState(true, mActivity.isActivityRecreated());

        mTerminalCursorBlinkerStateAlreadySet = false;

        if (mTerminalView.mEmulator != null) {
            // Start terminal cursor blinking if enabled
            // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
            // event to start it. This is needed since onEmulatorSet() may not be called after
            // TermuxActivity is started after device display timeout with double tap and not power button.
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    public void onStop() {
        // Stop terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(false);
    }

    /**
     * Should be called when mActivity.reloadProperties() is called
     */
    public void onReloadProperties() {
        setSessionShortcuts();

        // Restart the input connection so that the IME reloads the input attributes returned by
        // TerminalView.onCreateInputConnection(), since properties like gboard-cjk-workaround may
        // have changed.
        TerminalView terminalView = mTerminalView;
        if (terminalView != null) terminalView.restartInputConnection();
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    public void onReloadActivityStyling() {
        // Show the soft keyboard if required
        setSoftKeyboardState(false, true);

        // Start terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(true);
    }

    /**
     * Should be called when {@link com.termux.view.TerminalView#mEmulator} is set
     */
    @Override
    public void onEmulatorSet() {
        if (!mTerminalCursorBlinkerStateAlreadySet) {
            // Start terminal cursor blinking if enabled
            // We need to wait for the first session to be attached that's set in
            // TermuxActivity.onServiceConnected() and then the multiple calls to TerminalView.updateSize()
            // where the final one eventually sets the mEmulator when width/height is not 0. Otherwise
            // blinker will not start again if TermuxActivity is started again after exiting it with
            // double back press. Check TerminalView.setTerminalCursorBlinkerState().
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }



    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }



    @Override
    public void onSingleTapUp(MotionEvent e) {
        TerminalEmulator term = mTerminalView.getCurrentSession().getEmulator();

        if (mActivity.getProperties().shouldOpenTerminalTranscriptURLOnClick()) {
            int[] columnAndRow = mTerminalView.getColumnAndRow(e, true);

            // Prefer the OSC 8 hyperlink that the tapped cell is explicitly part of over a URL found in
            // the text of the cell, since the text may be a shortened or different form of the link.
            String hyperlink = term.getScreen().getHyperlinkAtLocation(columnAndRow[0], columnAndRow[1]);
            if (hyperlink != null) {
                ShareUtils.openUrl(mActivity, hyperlink);
                return;
            }

            String wordAtTap = term.getScreen().getWordAtLocation(columnAndRow[0], columnAndRow[1]);
            LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(wordAtTap);

            if (!urlSet.isEmpty()) {
                String url = (String) urlSet.iterator().next();
                ShareUtils.openUrl(mActivity, url);
                return;
            }
        }

        // A tap with a mouse or while mouse tracking is active is normally forwarded to the terminal
        // instead of being treated as a keyboard show request, unless the user has explicitly enabled
        // the `mouse-tap-shows-keyboard` property.
        boolean isMouseTap = e.isFromSource(InputDevice.SOURCE_MOUSE) || term.isMouseTrackingActive();

        if (!isMouseTap || mActivity.getProperties().shouldShowSoftKeyboardOnMouseTap()) {
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                KeyboardUtils.showSoftKeyboard(mActivity, mTerminalView);
            else
                Logger.logVerbose(LOG_TAG, "Not showing soft keyboard onSingleTapUp since its disabled");
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return mActivity.getProperties().isBackKeyTheEscapeKey();
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return mActivity.getProperties().isEnforcingCharBasedInput();
    }

    @Override
    public boolean isGboardCjkWorkaroundEnabled() {
        return mActivity.getProperties().isGboardCjkWorkaroundEnabled();
    }

    @Override
    public boolean shouldTouchDragSendMouseDrag() {
        return mActivity.getProperties().shouldTouchDragSendMouseDrag();
    }

    @Override
    public boolean shouldSwitchSessionOnSwipe() {
        return mActivity.getProperties().shouldSwitchSessionOnSwipe();
    }

    @Override
    public void onSwitchSessionSwipe(boolean forward) {
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.switchToSession(forward);
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return mActivity.getProperties().isUsingCtrlSpaceWorkaround();
    }

    @Override
    public boolean isTerminalViewSelected() {
        return mActivity.getTerminalToolbarViewPager() == null || mActivity.isTerminalViewSelected() || mTerminalView.hasFocus();
    }



    @Override
    public void copyModeChanged(boolean copyMode) {
        // Disable drawer while copying.
        mActivity.getDrawer().setDrawerLockMode(copyMode ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }



    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        if (handleVirtualKeys(keyCode, e, true)) return true;

        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning()) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession);
            return true;
        } else if (!mActivity.getProperties().areHardwareKeyboardShortcutsDisabled() &&
            e.isCtrlPressed() && e.isAltPressed()) {
            // Get the unmodified code point:
            int unicodeChar = e.getUnicodeChar(0);

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || unicodeChar == 'n'/* next */) {
                mTermuxTerminalSessionActivityClient.switchToSession(true);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || unicodeChar == 'p' /* previous */) {
                mTermuxTerminalSessionActivityClient.switchToSession(false);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mActivity.getDrawer().openDrawer(Gravity.LEFT);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mActivity.getDrawer().closeDrawers();
            } else if (unicodeChar == 'k'/* keyboard */) {
                onToggleSoftKeyboardRequest();
            } else if (unicodeChar == 'f' /* find */) {
                mActivity.showTerminalSearch();
            } else if (unicodeChar == 'm'/* menu */) {
                mActivity.showTerminalMenu();
            } else if (unicodeChar == 'r'/* rename */) {
                mTermuxTerminalSessionActivityClient.renameSession(currentSession);
            } else if (unicodeChar == 'c'/* create */) {
                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
            } else if (unicodeChar == 'u' /* urls */) {
                showUrlSelection();
            } else if (unicodeChar == 'v') {
                doPaste();
            } else if (unicodeChar == '+' || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+') {
                // We also check for the shifted char here since shift may be required to produce '+',
                // see https://github.com/termux/termux-api/issues/2
                changeFontSize(true);
            } else if (unicodeChar == '-') {
                changeFontSize(false);
            } else if (unicodeChar >= '1' && unicodeChar <= '9') {
                int index = unicodeChar - '1';
                mTermuxTerminalSessionActivityClient.switchToSession(index);
            }
            return true;
        }

        return false;

    }



    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (keyCode == KeyEvent.KEYCODE_BACK && mTerminalView.mEmulator == null) {
            mActivity.finishActivityIfNotFinishing();
            return true;
        }

        return handleVirtualKeys(keyCode, e, false);
    }

    /** Handle dedicated volume buttons as virtual keys if applicable. */
    private boolean handleVirtualKeys(int keyCode, KeyEvent event, boolean down) {
        InputDevice inputDevice = event.getDevice();
        if (mActivity.getProperties().areVirtualVolumeKeysDisabled()) {
            return false;
        } else if (inputDevice != null && inputDevice.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            // Do not steal dedicated buttons from a full external keyboard.
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            handleVolumeKey(false, event, down);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            handleVolumeKey(true, event, down);
            return true;
        }
        return false;
    }

    /**
     * Handle the volume up or down key based on the `volume-up-action`, `volume-up-long-action`,
     * `volume-down-action` and `volume-down-long-action` properties. If the action is the
     * {@link TermuxPropertyConstants#IVALUE_VOLUME_KEY_ACTION_DEFAULT} one, then the key behaves as the
     * virtual CTRL key (volume down) or virtual FN key (volume up) for as long as it is held down, which
     * is the upstream termux behaviour. Otherwise the action is performed once, either when the key is
     * released, or when it is held down long enough for the key to start repeating, in which case the
     * long press action is performed instead, unless it is the
     * {@link TermuxPropertyConstants#IVALUE_VOLUME_KEY_ACTION_NONE} one, in which case the single press
     * action is also performed on release.
     */
    private void handleVolumeKey(boolean isVolumeUp, KeyEvent event, boolean down) {
        String action = mActivity.getProperties().getVolumeKeyAction(isVolumeUp, false);
        boolean isDefaultAction = TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_DEFAULT.equals(action);

        if (down) {
            if (event.getRepeatCount() == 0) {
                mVolumeKeyLongPressTriggered = false;

                // The virtual modifier must be applied as soon as the key is pressed, since it may be
                // combined with other keys that are pressed before the volume key is released. It is
                // also cleared here, in case the action was changed while the key was still held down.
                setVirtualVolumeKeyDown(isVolumeUp, isDefaultAction);
            } else if (!mVolumeKeyLongPressTriggered) {
                // The key started repeating, so it is a long press. The single press action must not be
                // performed on release if the long press action was performed instead.
                String longAction = mActivity.getProperties().getVolumeKeyAction(isVolumeUp, true);
                if (!TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NONE.equals(longAction)) {
                    mVolumeKeyLongPressTriggered = true;
                    performVolumeKeyAction(longAction, isVolumeUp);
                }
            }
        } else {
            // Always clear the virtual modifier, since the action may have been changed while the key
            // was held down, in which case the single press action was not applied on release.
            setVirtualVolumeKeyDown(isVolumeUp, false);
            if (!mVolumeKeyLongPressTriggered) performVolumeKeyAction(action, isVolumeUp);
            mVolumeKeyLongPressTriggered = false;
        }
    }

    /** Set whether the volume key is currently applying the virtual CTRL key (volume down) or the
     * virtual FN key (volume up). */
    private void setVirtualVolumeKeyDown(boolean isVolumeUp, boolean down) {
        if (isVolumeUp)
            mVirtualFnKeyDown = down;
        else
            mVirtualControlKeyDown = down;
    }

    /** Perform the action of a volume key defined by the properties. The
     * {@link TermuxPropertyConstants#IVALUE_VOLUME_KEY_ACTION_DEFAULT} action does not have to do
     * anything, since the virtual modifier is applied when the key is pressed and released. */
    private void performVolumeKeyAction(String action, boolean isVolumeUp) {
        if (action == null) return;

        switch (action) {
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_DEFAULT:
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NONE:
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_VOLUME:
                AudioManager audio = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
                if (audio != null)
                    audio.adjustSuggestedStreamVolume(isVolumeUp ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_KEYBOARD:
                onToggleSoftKeyboardRequest();
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_TOGGLE_EXTRA_KEYS:
                mActivity.toggleTerminalToolbar();
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PAGE_UP:
                sendVolumeKeyHardwareKey(KeyEvent.KEYCODE_PAGE_UP);
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PAGE_DOWN:
                sendVolumeKeyHardwareKey(KeyEvent.KEYCODE_PAGE_DOWN);
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PREV_COMMAND:
                mTerminalView.scrollToPreviousCommand();
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NEXT_COMMAND:
                mTerminalView.scrollToNextCommand();
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PREV_SESSION:
                mTermuxTerminalSessionActivityClient.switchToSession(false);
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NEXT_SESSION:
                mTermuxTerminalSessionActivityClient.switchToSession(true);
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NEW_SESSION:
                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PASTE:
                doPaste();
                break;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_SEND_KEYS:
                sendVolumeKeySequence(mActivity.getProperties().getVolumeKeySendKeys(isVolumeUp));
                break;
            default:
                Logger.logError(LOG_TAG, "Ignoring unknown volume key action: \"" + action + "\"");
                break;
        }
    }

    /** Send a hardware key to the terminal of the current session, like the virtual FN key combos do. */
    private void sendVolumeKeyHardwareKey(int keyCode) {
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session == null) return;

        TerminalEmulator term = session.getEmulator();
        if (term == null) return;

        session.write(KeyHandler.getCode(keyCode, 0, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode()));
    }

    /**
     * Send a key sequence to the terminal of the current session, using the same syntax as the
     * `extra-keys` macros, like `ESC`, `CTRL c`, `UP UP` or `FN UP`. The sequence is split on space and
     * each key is either a key name defined in
     * {@link com.termux.shared.termux.extrakeys.ExtraKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS}, or
     * some text, optionally prefixed with `CTRL`, `ALT`, `SHIFT` or `FN` to apply it as a modifier.
     */
    private void sendVolumeKeySequence(String sequence) {
        if (DataUtils.isNullOrEmpty(sequence)) return;

        TerminalView terminalView = mTerminalView;
        if (terminalView == null) return;

        new TerminalExtraKeys(terminalView).onExtraKeyButtonClick(null, new ExtraKeyButton(sequence, true, sequence), null);
    }



    @Override
    public boolean readControlKey() {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || mVirtualControlKeyDown;
    }

    @Override
    public boolean readAltKey() {
        return readExtraKeysSpecialButton(SpecialButton.ALT);
    }

    @Override
    public boolean readShiftKey() {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT);
    }

    @Override
    public boolean readFnKey() {
        return readExtraKeysSpecialButton(SpecialButton.FN);
    }

    public boolean readExtraKeysSpecialButton(SpecialButton specialButton) {
        // The special button state is stored in each extra keys panel separately, so the button is
        // considered active if it is active in any of the panels. Reading the state also disables the
        // active state of the panel, unless it is locked.
        boolean state = false;
        for (ExtraKeysView extraKeysView : mActivity.getExtraKeysViews()) {
            Boolean viewState = extraKeysView.readSpecialButton(specialButton, true);
            if (viewState == null) {
                Logger.logError(LOG_TAG, "Failed to read an unregistered " + specialButton + " special button value from extra keys.");
            } else if (viewState) {
                state = true;
            }
        }
        return state;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    /**
     * Show the styled terminal context menu of the app instead of the system context menu, so that its
     * items and their order can be configured with the `terminal-menu-items` property and it can be
     * drawn with the colors of the termux color scheme.
     */
    @Override
    public boolean onShowContextMenu(@NonNull TerminalView view) {
        mActivity.showTerminalMenu();
        return true;
    }

    /** The actions of the floating text selection toolbar, from the `selection-toolbar-items` property. */
    @NonNull
    @Override
    public List<Integer> getTextSelectionToolbarActions() {
        return mActivity.getTerminalMenuConfig().selectionToolbarActions;
    }



    @Override
    public boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        if (mVirtualFnKeyDown) {
            int resultingKeyCode = -1;
            int resultingCodePoint = -1;
            boolean altDown = false;
            int lowerCase = Character.toLowerCase(codePoint);
            switch (lowerCase) {
                // Arrow keys.
                case 'w':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP;
                    break;
                case 'a':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                    break;
                case 's':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                    break;
                case 'd':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                    break;

                // Page up and down.
                case 'p':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP;
                    break;
                case 'n':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN;
                    break;

                // Some special keys:
                case 't':
                    resultingKeyCode = KeyEvent.KEYCODE_TAB;
                    break;
                case 'i':
                    resultingKeyCode = KeyEvent.KEYCODE_INSERT;
                    break;
                case 'h':
                    resultingCodePoint = '~';
                    break;

                // Special characters to input.
                case 'u':
                    resultingCodePoint = '_';
                    break;
                case 'l':
                    resultingCodePoint = '|';
                    break;

                // Function keys.
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    resultingKeyCode = (codePoint - '1') + KeyEvent.KEYCODE_F1;
                    break;
                case '0':
                    resultingKeyCode = KeyEvent.KEYCODE_F10;
                    break;

                // Other special keys.
                case 'e':
                    resultingCodePoint = /*Escape*/ 27;
                    break;
                case '.':
                    resultingCodePoint = /*^.*/ 28;
                    break;

                case 'b': // alt+b, jumping backward in readline.
                case 'f': // alf+f, jumping forward in readline.
                case 'x': // alt+x, common in emacs.
                    resultingCodePoint = lowerCase;
                    altDown = true;
                    break;

                // Volume control.
                case 'v':
                    resultingCodePoint = -1;
                    AudioManager audio = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
                    audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    break;

                // Writing mode:
                case 'q':
                case 'k':
                    mActivity.toggleTerminalToolbar();
                    mVirtualFnKeyDown=false; // force disable fn key down to restore keyboard input into terminal view, fixes termux/termux-app#1420
                    break;
            }

            if (resultingKeyCode != -1) {
                TerminalEmulator term = session.getEmulator();
                session.write(KeyHandler.getCode(resultingKeyCode, 0, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode()));
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint);
            }
            return true;
        } else if (ctrlDown) {
            if (codePoint == 106 /* Ctrl+j or \n */ && !session.isRunning()) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                return true;
            }

            List<KeyboardShortcut> shortcuts = mSessionShortcuts;
            if (shortcuts != null && !shortcuts.isEmpty()) {
                int codePointLowerCase = Character.toLowerCase(codePoint);
                for (int i = shortcuts.size() - 1; i >= 0; i--) {
                    KeyboardShortcut shortcut = shortcuts.get(i);
                    if (codePointLowerCase == shortcut.codePoint) {
                        switch (shortcut.shortcutAction) {
                            case TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION:
                                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION:
                                mTermuxTerminalSessionActivityClient.switchToSession(true);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION:
                                mTermuxTerminalSessionActivityClient.switchToSession(false);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION:
                                mTermuxTerminalSessionActivityClient.renameSession(mTerminalView.getCurrentSession());
                                return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Set the terminal sessions shortcuts.
     */
    private void setSessionShortcuts() {
        mSessionShortcuts = new ArrayList<>();

        // The {@link TermuxPropertyConstants#MAP_SESSION_SHORTCUTS} stores the session shortcut key and action pair
        for (Map.Entry<String, Integer> entry : TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.entrySet()) {
            // The mMap stores the code points for the session shortcuts while loading properties
            Integer codePoint = (Integer) mActivity.getProperties().getInternalPropertyValue(entry.getKey(), true);
            // If codePoint is null, then session shortcut did not exist in properties or was invalid
            // as parsed by {@link #getCodePointForSessionShortcuts(String,String)}
            // If codePoint is not null, then get the action for the MAP_SESSION_SHORTCUTS key and
            // add the code point to sessionShortcuts
            if (codePoint != null)
                mSessionShortcuts.add(new KeyboardShortcut(codePoint, entry.getValue()));
        }
    }





    public void changeFontSize(boolean increase) {
        mActivity.getPreferences().changeFontSize(increase);
        // The font size is per view, so it must be applied to the view of every pane.
        for (TerminalView terminalView : mActivity.getTerminalViews())
            terminalView.setTextSize(mActivity.getPreferences().getFontSize());
    }



    /**
     * Called when user requests the soft keyboard to be toggled via "KEYBOARD" toggle button in
     * drawer or extra keys, or with ctrl+alt+k hardware keyboard shortcut.
     */
    public void onToggleSoftKeyboardRequest() {
        // If soft keyboard toggle behaviour is enable/disabled
        if (mActivity.getProperties().shouldEnableDisableSoftKeyboardOnToggle()) {
            // If soft keyboard is visible
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity)) {
                Logger.logVerbose(LOG_TAG, "Disabling soft keyboard on toggle");
                mActivity.getPreferences().setSoftKeyboardEnabled(false);
                KeyboardUtils.disableSoftKeyboard(mActivity, mTerminalView);
            } else {
                // Show with a delay, otherwise pressing keyboard toggle won't show the keyboard after
                // switching back from another app if keyboard was previously disabled by user.
                // Also request focus, since it wouldn't have been requested at startup by
                // setSoftKeyboardState if keyboard was disabled. #2112
                Logger.logVerbose(LOG_TAG, "Enabling soft keyboard on toggle");
                mActivity.getPreferences().setSoftKeyboardEnabled(true);
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
                if(mShowSoftKeyboardWithDelayOnce) {
                    mShowSoftKeyboardWithDelayOnce = false;
                    mTerminalView.postDelayed(getShowSoftKeyboardRunnable(), 500);
                    mTerminalView.requestFocus();
                } else
                    KeyboardUtils.showSoftKeyboard(mActivity, mTerminalView);
            }
        }
        // If soft keyboard toggle behaviour is show/hide
        else {
            // If soft keyboard is disabled by user for Termux
            if (!mActivity.getPreferences().isSoftKeyboardEnabled()) {
                Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard on toggle");
                KeyboardUtils.disableSoftKeyboard(mActivity, mTerminalView);
            } else {
                Logger.logVerbose(LOG_TAG, "Showing/Hiding soft keyboard on toggle");
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
                KeyboardUtils.toggleSoftKeyboard(mActivity);
            }
        }
    }

    public void setSoftKeyboardState(boolean isStartup, boolean isReloadTermuxProperties) {
        // Only the terminal view of the active pane may request focus and show the soft keyboard,
        // otherwise the view of the other pane would steal the focus when split screen is enabled.
        if (mTerminalView != mActivity.getTerminalView()) return;

        boolean noShowKeyboard = false;

        // Requesting terminal view focus is necessary regardless of if soft keyboard is to be
        // disabled or hidden at startup, otherwise if hardware keyboard is attached and user
        // starts typing on hardware keyboard without tapping on the terminal first, then a colour
        // tint will be added to the terminal as highlight for the focussed view. Test with a light
        // theme. For android 8.+, the "defaultFocusHighlightEnabled" attribute is also set to false
        // in TerminalView layout to fix the issue.

        // If soft keyboard is disabled by user for Termux (check function docs for Termux behaviour info)
        if (KeyboardUtils.shouldSoftKeyboardBeDisabled(mActivity,
            mActivity.getPreferences().isSoftKeyboardEnabled(),
            mActivity.getPreferences().isSoftKeyboardEnabledOnlyIfNoHardware())) {
            Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard");
            KeyboardUtils.disableSoftKeyboard(mActivity, mTerminalView);
            mTerminalView.requestFocus();
            noShowKeyboard = true;
            // Delay is only required if onCreate() is called like when Termux app is exited with
            // double back press, not when Termux app is switched back from another app and keyboard
            // toggle is pressed to enable keyboard
            if (isStartup && mActivity.isOnResumeAfterOnCreate())
                mShowSoftKeyboardWithDelayOnce = true;
        } else {
            // Set flag to automatically push up TerminalView when keyboard is opened instead of showing over it
            KeyboardUtils.setSoftInputModeAdjustResize(mActivity);

            // Clear any previous flags to disable soft keyboard in case setting updated
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);

            // If soft keyboard is to be hidden on startup
            if (isStartup && mActivity.getProperties().shouldSoftKeyboardBeHiddenOnStartup()) {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on startup");
                // Required to keep keyboard hidden when Termux app is switched back from another app
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity);

                KeyboardUtils.hideSoftKeyboard(mActivity, mTerminalView);
                mTerminalView.requestFocus();
                noShowKeyboard = true;
                // Required to keep keyboard hidden on app startup
                mShowSoftKeyboardIgnoreOnce = true;
            }
        }

        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
        if (!isReloadTermuxProperties && !noShowKeyboard) {
            // Request focus for TerminalView
            // Also show the keyboard, since onFocusChange will not be called if TerminalView already
            // had focus on startup to show the keyboard, like when opening url with context menu
            // "Select URL" long press and returning to Termux app with back button. This
            // will also show keyboard even if it was closed before opening url. #2111
            Logger.logVerbose(LOG_TAG, "Requesting TerminalView focus and showing soft keyboard");
            mTerminalView.requestFocus();
            mTerminalView.postDelayed(getShowSoftKeyboardRunnable(), 300);
        }
    }

    /**
     * Set the listener that shows or hides the soft keyboard when the focus of the pane's terminal
     * view changes. The listener is set for every pane, but the keyboard is only hidden if no other
     * terminal view of the activity has focus, since tapping one pane moves the focus to it and must
     * not hide the keyboard that the other pane's view is about to show.
     */
    private void setTerminalViewFocusChangeListener() {
        mTerminalView.setOnFocusChangeListener((view, hasFocus) -> {
            // Force show soft keyboard if TerminalView or toolbar text input view has
            // focus and close it if they don't
            boolean textInputViewHasFocus = mActivity.isTerminalSearchBarVisible();
            final EditText textInputView = mActivity.findViewById(R.id.terminal_toolbar_text_input);
            if (textInputView != null) textInputViewHasFocus = textInputViewHasFocus || textInputView.hasFocus();

            boolean showKeyboard = hasFocus || textInputViewHasFocus || mActivity.isAnyTerminalViewFocused();

            if (showKeyboard) {
                if (mShowSoftKeyboardIgnoreOnce) {
                    mShowSoftKeyboardIgnoreOnce = false; return;
                }
                Logger.logVerbose(LOG_TAG, "Showing soft keyboard on focus change");
            } else {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on focus change");
            }

            KeyboardUtils.setSoftKeyboardVisibility(getShowSoftKeyboardRunnable(), mActivity, mTerminalView, showKeyboard);
        });
    }

    private Runnable getShowSoftKeyboardRunnable() {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable = () -> {
                KeyboardUtils.showSoftKeyboard(mActivity, mTerminalView);
            };
        }
        return mShowSoftKeyboardRunnable;
    }



    public void setTerminalCursorBlinkerState(boolean start) {
        if (start) {
            // If set/update the cursor blinking rate is successful, then enable cursor blinker
            if (mTerminalView.setTerminalCursorBlinkerRate(mActivity.getProperties().getTerminalCursorBlinkRate()))
                mTerminalView.setTerminalCursorBlinkerState(true, true);
            else
                Logger.logError(LOG_TAG,"Failed to start cursor blinker");
        } else {
            // Disable cursor blinker
            mTerminalView.setTerminalCursorBlinkerState(false, true);
        }
    }



    public void shareSessionTranscript() {
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session == null) return;

        String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null) return;

        // See https://github.com/termux/termux-app/issues/1166.
        transcriptText = DataUtils.getTruncatedCommandOutput(transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false).trim();
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_transcript),
            transcriptText, mActivity.getString(R.string.title_share_transcript_with));
    }

    public void shareSelectedText() {
        String selectedText = mTerminalView.getStoredSelectedText();
        if (DataUtils.isNullOrEmpty(selectedText)) return;
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_selected_text),
            selectedText, mActivity.getString(R.string.title_share_selected_text_with));
    }

    public void showUrlSelection() {
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session == null) return;

        String text = ShellUtils.getTerminalSessionTranscriptText(session, true, true);

        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(text);
        if (urlSet.isEmpty()) {
            new AlertDialog.Builder(mActivity).setMessage(R.string.title_select_url_none_found).show();
            return;
        }

        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        Collections.reverse(Arrays.asList(urls)); // Latest first.

        // Click to copy url to clipboard:
        final AlertDialog dialog = new AlertDialog.Builder(mActivity).setItems(urls, (di, which) -> {
            String url = (String) urls[which];
            ShareUtils.copyTextToClipboard(mActivity, url, mActivity.getString(R.string.msg_select_url_copied_to_clipboard));
        }).setTitle(R.string.title_select_url_dialog).create();

        // Long press to open URL:
        dialog.setOnShowListener(di -> {
            ListView lv = dialog.getListView(); // this is a ListView with your "buds" in it
            lv.setOnItemLongClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                String url = (String) urls[position];
                ShareUtils.openUrl(mActivity, url);
                return true;
            });
        });

        dialog.show();
    }

    public void reportIssueFromTranscript() {
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session == null) return;

        final String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null) return;

        MessageDialogUtils.showMessage(mActivity, TermuxConstants.TERMUX_APP_NAME + " Report Issue",
            mActivity.getString(R.string.msg_add_termux_debug_info),
            mActivity.getString(com.termux.shared.R.string.action_yes), (dialog, which) -> reportIssueFromTranscript(transcriptText, true),
            mActivity.getString(com.termux.shared.R.string.action_no), (dialog, which) -> reportIssueFromTranscript(transcriptText, false),
            null);
    }

    private void reportIssueFromTranscript(String transcriptText, boolean addTermuxDebugInfo) {
        Logger.showToast(mActivity, mActivity.getString(R.string.msg_generating_report), true);

        new Thread() {
            @Override
            public void run() {
                StringBuilder reportString = new StringBuilder();

                String title = TermuxConstants.TERMUX_APP_NAME + " Report Issue";

                reportString.append("## Transcript\n");
                reportString.append("\n").append(MarkdownUtils.getMarkdownCodeForString(transcriptText, true));
                reportString.append("\n##\n");

                if (addTermuxDebugInfo) {
                    reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                } else {
                    reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_PACKAGE));
                }

                reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(mActivity, true));

                if (TermuxBootstrap.isAppPackageManagerAPT()) {
                    String termuxAptInfo = TermuxUtils.geAPTInfoMarkdownString(mActivity);
                    if (termuxAptInfo != null)
                        reportString.append("\n\n").append(termuxAptInfo);
                }

                if (addTermuxDebugInfo) {
                    String termuxDebugInfo = TermuxUtils.getTermuxDebugMarkdownString(mActivity);
                    if (termuxDebugInfo != null)
                        reportString.append("\n\n").append(termuxDebugInfo);
                }

                String userActionName = UserAction.REPORT_ISSUE_FROM_TRANSCRIPT.getName();

                ReportInfo reportInfo = new ReportInfo(userActionName,
                    TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME, title);
                reportInfo.setReportString(reportString.toString());
                reportInfo.setReportStringSuffix("\n\n" + TermuxUtils.getReportIssueMarkdownString(mActivity));
                reportInfo.setReportSaveFileLabelAndPath(userActionName,
                    Environment.getExternalStorageDirectory() + "/" +
                        FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                ReportActivity.startReportActivity(mActivity, reportInfo);
            }
        }.start();
    }

    public void doPaste() {
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session == null) return;
        if (!session.isRunning()) return;

        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null)
            session.getEmulator().paste(text);
    }

}
