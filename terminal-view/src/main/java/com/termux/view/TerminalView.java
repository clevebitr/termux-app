package com.termux.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Scroller;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.textselection.TextSelectionCursorController;

/** View displaying and interacting with a {@link TerminalSession}. */
public final class TerminalView extends View {

    /** Log terminal view key and IME events. */
    private static boolean TERMINAL_VIEW_KEY_LOGGING_ENABLED = false;

    /** The currently displayed terminal session, whose emulator is {@link #mEmulator}. */
    public TerminalSession mTermSession;
    /** Our terminal emulator whose session is {@link #mTermSession}. */
    public TerminalEmulator mEmulator;

    public TerminalRenderer mRenderer;

    public TerminalViewClient mClient;

    private TextSelectionCursorController mTextSelectionCursorController;

    private Handler mTerminalCursorBlinkerHandler;
    private TerminalCursorBlinkerRunnable mTerminalCursorBlinkerRunnable;
    private int mTerminalCursorBlinkerRate;
    private boolean mCursorInvisibleIgnoreOnce;
    public static final int TERMINAL_CURSOR_BLINK_RATE_MIN = 100;
    public static final int TERMINAL_CURSOR_BLINK_RATE_MAX = 2000;

    /** The top row of text to display. Ranges from -activeTranscriptRows to 0. */
    int mTopRow;
    int[] mDefaultSelectors = new int[]{-1,-1,-1,-1};

    /** Whether a redraw requested by {@link #onScreenOutputThrottled(long)} is already scheduled. */
    private boolean mOutputRedrawPending;
    /** The runnable that performs a redraw requested by {@link #onScreenOutputThrottled(long)}. */
    private final Runnable mOutputRedrawRunnable = new Runnable() {
        @Override
        public void run() {
            mOutputRedrawPending = false;
            onScreenOutput();
        }
    };

    float mScaleFactor = 1.f;
    final GestureAndScaleRecognizer mGestureRecognizer;

    /** Keep track of where mouse touch event started which we report as mouse scroll. */
    private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;
    /** Keep track of the time when a touch event leading to sending mouse scroll events started. */
    private long mMouseStartDownTime = -1;

    final Scroller mScroller;

    /** What was left in from scrolling movement. */
    float mScrollRemainder;

    /**
     * Whether a drag with a finger is being reported as a drag of the mouse pointer with the left
     * button pressed, in which case the button must be released when the gesture ends. See
     * {@link TerminalViewClient#shouldTouchDragSendMouseDrag()}.
     */
    private boolean mIsMouseDragFromTouchInProgress;

    /** If non-zero, this is the last unicode code point received if that was a combining character. */
    int mCombiningAccent;

    /** The query of the current search, or {@code null} if no search is active. */
    private String mSearchQuery;
    /** Whether the search matches the case of the text. */
    private boolean mSearchCaseSensitive;
    /** Whether the query of the search is a regular expression. */
    private boolean mSearchRegex;
    /** Whether the last query was an invalid regular expression, which matches nothing. */
    private boolean mSearchRegexInvalid;
    /** The matches of {@link #mSearchQuery} in the screen and transcript, sorted by row and column. */
    private TerminalBuffer.SearchMatch[] mSearchMatches = new TerminalBuffer.SearchMatch[0];
    /** The index in {@link #mSearchMatches} of the match that is currently selected, or -1 if none is. */
    private int mSearchMatchIndex = -1;
    /** Flattened copies of {@link #mSearchMatches} that are passed to {@link TerminalRenderer}. */
    private int[] mSearchMatchRows = new int[0];
    private int[] mSearchMatchStartColumns = new int[0];
    private int[] mSearchMatchEndColumns = new int[0];

    /** The width in dp of the strip on the right edge where the command marks can be tapped. */
    private static final float COMMAND_MARK_TOUCH_STRIP_WIDTH_DP = 16f;
    /** Whether a touch on the command mark strip on the right edge is in progress. */
    private boolean mIsCommandMarkStripTouchInProgress;
    private float mCommandMarkStripTouchDownY;
    /** The command row that will be jumped to when the strip touch is released. */
    private int mCommandMarkStripTouchRow;

    /**
     * The current AutoFill type returned for {@link View#getAutofillType()} by {@link #getAutofillType()}.
     *
     * The default is {@link #AUTOFILL_TYPE_NONE} so that AutoFill UI, like toolbar above keyboard
     * is not shown automatically, like on Activity starts/View create. This value should be updated
     * to required value, like {@link #AUTOFILL_TYPE_TEXT} before calling
     * {@link AutofillManager#requestAutofill(View)} so that AutoFill UI shows. The updated value
     * set will automatically be restored to {@link #AUTOFILL_TYPE_NONE} in
     * {@link #autofill(AutofillValue)} so that AutoFill UI isn't shown anymore by calling
     * {@link #resetAutoFill()}.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private int mAutoFillType = AUTOFILL_TYPE_NONE;

    /**
     * The current AutoFill type returned for {@link View#getImportantForAutofill()} by
     * {@link #getImportantForAutofill()}.
     *
     * The default is {@link #IMPORTANT_FOR_AUTOFILL_NO} so that view is not considered important
     * for AutoFill. This value should be updated to required value, like
     * {@link #IMPORTANT_FOR_AUTOFILL_YES} before calling {@link AutofillManager#requestAutofill(View)}
     * so that Android and apps consider the view as important for AutoFill to process the request.
     * The updated value set will automatically be restored to {@link #IMPORTANT_FOR_AUTOFILL_NO} in
     * {@link #autofill(AutofillValue)} by calling {@link #resetAutoFill()}.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private int mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;

    /**
     * The current AutoFill hints returned for {@link View#getAutofillHints()} ()} by {@link #getAutofillHints()} ()}.
     *
     * The default is an empty `string[]`. This value should be updated to required value. The
     * updated value set will automatically be restored an empty `string[]` in
     * {@link #autofill(AutofillValue)} by calling {@link #resetAutoFill()}.
     */
    private String[] mAutoFillHints = new String[0];

    private final boolean mAccessibilityEnabled;

    /** The {@link KeyEvent} is generated from a virtual keyboard, like manually with the {@link KeyEvent#KeyEvent(int, int)} constructor. */
    public final static int KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD; // -1

    /** The {@link KeyEvent} is generated from a non-physical device, like if 0 value is returned by {@link KeyEvent#getDeviceId()}. */
    public final static int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;

    private static final String LOG_TAG = "TerminalView";

    /**
     * The language tag of the currently selected IME subtype, like `en` or `zh-Hans-CN`. It is set
     * by {@link #updateImeLanguageTag()} and is {@code null} if it cannot be determined.
     * Currently only used by the {@link #mClient gboard CJK workaround}.
     */
    private String mImeLanguageTag;

    /**
     * If the currently selected IME subtype is for a CJK language. It is set by
     * {@link #updateImeLanguageTag()}.
     */
    private boolean mImeIsCjkLanguage;

    public TerminalView(Context context, AttributeSet attributes) { // NO_UCD (unused code)
        super(context, attributes);
        mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {

            boolean scrolledWithFinger;

            @Override
            public boolean onUp(MotionEvent event) {
                mScrollRemainder = 0.0f;
                if (mIsMouseDragFromTouchInProgress) {
                    mIsMouseDragFromTouchInProgress = false;
                    scrolledWithFinger = false;
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                    return true;
                }
                if (mEmulator != null && mEmulator.isMouseTrackingActive() && !event.isFromSource(InputDevice.SOURCE_MOUSE) && !isSelectingText() && !scrolledWithFinger) {
                    // Quick event processing when mouse tracking is active - do not wait for check of double tapping
                    // for zooming.
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                    sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                    return true;
                }
                scrolledWithFinger = false;
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (mEmulator == null) return true;

                if (isSelectingText()) {
                    stopTextSelectionMode();
                    return true;
                }
                requestFocus();
                mClient.onSingleTapUp(event);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (mEmulator == null) return true;

                boolean fromMouse = e2.isFromSource(InputDevice.SOURCE_MOUSE);
                // A drag with a finger is reported as a drag of the mouse pointer instead of scroll wheel
                // events, if enabled, since the emulator cannot report a drag of the mouse pointer without a
                // button press event and a terminal program that supports dragging needs one. This is not
                // done when selecting text or after a long press, since the gesture then belongs to the view
                // and the release of the button press would not be reported, which would leave the mouse
                // pointer in a pressed state in the terminal program.
                if (mEmulator.isMouseTrackingActive() &&
                    (fromMouse || (mClient.shouldTouchDragSendMouseDrag() && !isSelectingText() && !mGestureRecognizer.isAfterLongPress))) {
                    if (!fromMouse) {
                        // The press must be reported before the first move, so that the terminal program knows
                        // that a drag is being made and from which position, since a touch drag has no press
                        // event of its own, unlike a drag with the mouse pointer in onTouchEvent().
                        if (!mIsMouseDragFromTouchInProgress) {
                            mIsMouseDragFromTouchInProgress = true;
                            sendMouseEventCode(e1, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
                        }
                    }
                    sendMouseEventCode(e2, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                } else {
                    scrolledWithFinger = true;
                    distanceY += mScrollRemainder;
                    int deltaRows = (int) (distanceY / mRenderer.mFontLineSpacing);
                    mScrollRemainder = distanceY - deltaRows * mRenderer.mFontLineSpacing;
                    doScroll(e2, deltaRows);
                }
                return true;
            }

            @Override
            public boolean onScale(float focusX, float focusY, float scale) {
                if (mEmulator == null || isSelectingText()) return true;
                mScaleFactor *= scale;
                mScaleFactor = mClient.onScale(mScaleFactor);
                return true;
            }

            @Override
            public boolean onFling(final MotionEvent e1, final MotionEvent e2, float velocityX, float velocityY) {
                if (mEmulator == null) return true;

                // A horizontal swipe switches to the previous or next session, unless a terminal program is
                // using mouse tracking or text is being selected, in which case the gesture belongs to them.
                if (mClient.shouldSwitchSessionOnSwipe() && isSessionSwitchSwipe(e1, e2, velocityX, velocityY)) {
                    // Swiping towards the left moves the view towards the left, which is like moving to the next
                    // page of a pager, so the next session is switched to.
                    mClient.onSwitchSessionSwipe(velocityX < 0);
                    return true;
                }

                // Do not start scrolling until last fling has been taken care of:
                if (!mScroller.isFinished()) return true;

                final boolean mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive();
                // Do not send scroll wheel events for the inertial scroll of a drag with a finger, since the
                // drag was already reported as a drag of the mouse pointer, if that is enabled.
                if (mouseTrackingAtStartOfFling && !e2.isFromSource(InputDevice.SOURCE_MOUSE) && mClient.shouldTouchDragSendMouseDrag())
                    return true;

                float SCALE = 0.25f;
                if (mouseTrackingAtStartOfFling) {
                    mScroller.fling(0, 0, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.mRows / 2, mEmulator.mRows / 2);
                } else {
                    mScroller.fling(0, mTopRow, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.getScreen().getActiveTranscriptRows(), 0);
                }

                post(new Runnable() {
                    private int mLastY = 0;

                    @Override
                    public void run() {
                        if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive()) {
                            mScroller.abortAnimation();
                            return;
                        }
                        if (mScroller.isFinished()) return;
                        boolean more = mScroller.computeScrollOffset();
                        int newY = mScroller.getCurrY();
                        int diff = mouseTrackingAtStartOfFling ? (newY - mLastY) : (newY - mTopRow);
                        doScroll(e2, diff);
                        mLastY = newY;
                        if (more) post(this);
                    }
                });

                return true;
            }

            @Override
            public boolean onDown(float x, float y) {
                // Why is true not returned here?
                // https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures
                // Although setting this to true still does not solve the following errors when long pressing in terminal view text area
                // ViewDragHelper: Ignoring pointerId=0 because ACTION_DOWN was not received for this pointer before ACTION_MOVE
                // Commenting out the call to mGestureDetector.onTouchEvent(event) in GestureAndScaleRecognizer#onTouchEvent() removes
                // the error logging, so issue is related to GestureDetector
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                // Do not treat is as a single confirmed tap - it may be followed by zoom.
                return false;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                if (mGestureRecognizer.isInProgress()) return;
                if (mClient.onLongPress(event)) return;
                if (!isSelectingText()) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    startTextSelectionMode(event);
                }
            }
        });
        mScroller = new Scroller(context);
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAccessibilityEnabled = am.isEnabled();
    }



    /**
     * Whether a fling is a horizontal swipe that should switch to the next or previous session, which
     * requires it to be clearly horizontal, so that a diagonal swipe meant for scrolling does not switch
     * the session, and to cover a minimum distance, so that a small flick does not switch it either.
     */
    private boolean isSessionSwitchSwipe(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null) return false;
        if (mEmulator == null || mEmulator.isMouseTrackingActive() || isSelectingText()) return false;

        if (Math.abs(velocityX) < 2 * Math.abs(velocityY)) return false;

        return Math.abs(e2.getX() - e1.getX()) > getWidth() / 10.f;
    }

    /**
     * @param client The {@link TerminalViewClient} interface implementation to allow
     *                           for communication between {@link TerminalView} and its client.
     */
    public void setTerminalViewClient(TerminalViewClient client) {
        this.mClient = client;
    }

    /**
     * Sets whether terminal view key logging is enabled or not.
     *
     * @param value The boolean value that defines the state.
     */
    public void setIsTerminalViewKeyLoggingEnabled(boolean value) {
        TERMINAL_VIEW_KEY_LOGGING_ENABLED = value;
    }



    /**
     * Attach a {@link TerminalSession} to this view.
     *
     * @param session The {@link TerminalSession} this view will be displaying.
     */
    public boolean attachSession(TerminalSession session) {
        if (session == mTermSession) return false;

        mTermSession = session;
        mEmulator = null;
        mCombiningAccent = 0;
        // A redraw that was scheduled by the previous session's output is not wanted anymore.
        cancelPendingOutputRedraw();
        // The search matches refer to rows of the previous session's buffer.
        clearSearch();

        // The emulator's cached value will be read in `updateSize()` when emulator is set.
        setTopRow(0, false);

        updateSize();

        // Wait with enabling the scrollbar until we have a terminal to get scroll position from.
        setVerticalScrollBarEnabled(true);

        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // Ensure that inputType is only set if TerminalView is selected view with the keyboard and
        // an alternate view is not selected, like an EditText. This is necessary if an activity is
        // initially started with the alternate view or if activity is returned to from another app
        // and the alternate view was the one selected the last time.
        if (mClient.isTerminalViewSelected()) {
            if (mClient.shouldEnforceCharBasedInput()) {
                // Some keyboards seems do not reset the internal state on TYPE_NULL.
                // Affects mostly Samsung stock keyboards.
                // https://github.com/termux/termux-app/issues/686
                // However, this is not a valid value as per AOSP since `InputType.TYPE_CLASS_*` is
                // not set and it logs a warning:
                // W/InputAttributes: Unexpected input class: inputType=0x00080090 imeOptions=0x02000000
                // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/inputmethods/LatinIME/java/src/com/android/inputmethod/latin/InputAttributes.java;l=79
                outAttrs.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            } else {
                // Using InputType.NULL is the most correct input type and avoids issues with other hacks.
                //
                // Previous keyboard issues:
                // https://github.com/termux/termux-packages/issues/25
                // https://github.com/termux/termux-app/issues/87.
                // https://github.com/termux/termux-app/issues/126.
                // https://github.com/termux/termux-app/issues/137 (japanese chars and TYPE_NULL).
                outAttrs.inputType = InputType.TYPE_NULL;
            }
        } else {
            // Corresponds to android:inputType="text"
            outAttrs.inputType =  InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        }

        // Workaround to allow IMEs like Gboard to use CJK keyboards, like Chinese Pinyin, while the
        // terminal view is selected. Gboard forces itself to an ASCII QWERTY keyboard and hides the
        // language switch key if the input type is `InputType.TYPE_NULL` or
        // `InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, since it does not consider the view as a
        // normal text editor. A text class input type is used instead, and the input is restarted
        // whenever the IME language is changed, like when user switches to a CJK language, since
        // the input type must be updated for the new language.
        // See https://github.com/termux/termux-app/issues/1539 and
        // https://github.com/termux/termux-x11/pull/620.
        if (mClient.isGboardCjkWorkaroundEnabled() && mClient.isTerminalViewSelected()) {
            updateImeLanguageTag();
            if (mImeLanguageTag != null) {
                // `InputType.TYPE_TEXT_VARIATION_NORMAL` is required for CJK keyboards for Gboard to
                // show the composing/candidate view, otherwise the input may not work correctly.
                outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                    (mImeIsCjkLanguage ? InputType.TYPE_TEXT_VARIATION_NORMAL : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }
        }

        // Note that IME_ACTION_NONE cannot be used as that makes it impossible to input newlines using the on-screen
        // keyboard on Android TV (see https://github.com/termux/termux-app/issues/221).
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;

        return new BaseInputConnection(this, true) {

            @Override
            public boolean finishComposingText() {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "IME: finishComposingText()");
                super.finishComposingText();

                sendTextToTerminal(getEditable());
                getEditable().clear();
                return true;
            }

            @Override
            public boolean requestCursorUpdates(int cursorUpdateMode) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "IME: requestCursorUpdates(" + cursorUpdateMode + ")");

                // Gboard calls this function whenever the IME language is changed, like when user
                // switches to a CJK language. The terminal view must be restarted with the new input
                // type for the correct keyboard to be shown for the new language.
                if (mClient.isGboardCjkWorkaroundEnabled() && mClient.isTerminalViewSelected())
                    checkImeLanguageTagAndRestartInput(true);

                return super.requestCursorUpdates(cursorUpdateMode);
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
                }
                super.commitText(text, newCursorPosition);

                if (mEmulator == null) return true;

                Editable content = getEditable();
                sendTextToTerminal(content);
                content.clear();

                // Workaround to hide the suggestion strip that Gboard shows for CJK languages,
                // since it may cover the extra keys row or the terminal, and the suggestions are
                // not usable anyway since committed text is sent directly to the terminal.
                if (mImeIsCjkLanguage) invalidateImeInput();
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
                    mClient.logInfo(LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
                }
                // The stock Samsung keyboard with 'Auto check spelling' enabled sends leftLength > 1.
                KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < leftLength; i++) sendKeyEvent(deleteKey);
                return super.deleteSurroundingText(leftLength, rightLength);
            }

            void sendTextToTerminal(CharSequence text) {
                stopTextSelectionMode();
                final int textLengthInChars = text.length();
                for (int i = 0; i < textLengthInChars; i++) {
                    char firstChar = text.charAt(i);
                    int codePoint;
                    if (Character.isHighSurrogate(firstChar)) {
                        if (++i < textLengthInChars) {
                            codePoint = Character.toCodePoint(firstChar, text.charAt(i));
                        } else {
                            // At end of string, with no low surrogate following the high:
                            codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
                        }
                    } else {
                        codePoint = firstChar;
                    }

                    // Check onKeyDown() for details.
                    if (mClient.readShiftKey())
                        codePoint = Character.toUpperCase(codePoint);

                    boolean ctrlHeld = false;
                    if (codePoint <= 31 && codePoint != 27) {
                        if (codePoint == '\n') {
                            // The AOSP keyboard and descendants seems to send \n as text when the enter key is pressed,
                            // instead of a key event like most other keyboard apps. A terminal expects \r for the enter
                            // key (although when icrnl is enabled this doesn't make a difference - run 'stty -icrnl' to
                            // check the behaviour).
                            codePoint = '\r';
                        }

                        // E.g. penti keyboard for ctrl input.
                        ctrlHeld = true;
                        switch (codePoint) {
                            case 31:
                                codePoint = '_';
                                break;
                            case 30:
                                codePoint = '^';
                                break;
                            case 29:
                                codePoint = ']';
                                break;
                            case 28:
                                codePoint = '\\';
                                break;
                            default:
                                codePoint += 96;
                                break;
                        }
                    }

                    inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
                }
            }

        };
    }

    /**
     * Get the {@link InputMethodManager} for the {@link Context} of the terminal view.
     *
     * @return Returns the {@link InputMethodManager} or {@code null} if it is not available.
     */
    @Nullable
    private InputMethodManager getInputMethodManager() {
        Context context = getContext();
        if (context == null) return null;

        return (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /**
     * Set the {@link #mImeLanguageTag} and {@link #mImeIsCjkLanguage} values based on the language of
     * the currently selected IME subtype. The values are set to {@code null} and {@code false}
     * respectively if the language cannot be determined, like on android versions before
     * {@link Build.VERSION_CODES#N} or if the IME does not define a language for the subtype.
     */
    private void updateImeLanguageTag() {
        mImeLanguageTag = null;
        mImeIsCjkLanguage = false;

        // `InputMethodManager.getCurrentInputMethodSubtype()` was added in android 7.0.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        InputMethodManager inputMethodManager = getInputMethodManager();
        if (inputMethodManager == null) return;

        try {
            InputMethodSubtype subtype = inputMethodManager.getCurrentInputMethodSubtype();
            if (subtype == null) return;

            String languageTag = subtype.getLanguageTag();
            if (TextUtils.isEmpty(languageTag)) return;

            mImeLanguageTag = languageTag;
            mImeIsCjkLanguage = languageTag.regionMatches(true, 0, "zh", 0, 2) ||
                languageTag.regionMatches(true, 0, "ja", 0, 2) ||
                languageTag.regionMatches(true, 0, "ko", 0, 2);
        } catch (Exception e) {
            mClient.logWarn(LOG_TAG, "Failed to get language tag of current IME subtype: " + e.getMessage());
        }
    }

    /**
     * Restart the input connection of the terminal view if the language of the currently selected
     * IME subtype has changed, since the input type returned by
     * {@link #onCreateInputConnection(EditorInfo)} may need to be updated for the new language.
     *
     * @param recheck If the language should be rechecked after a small delay, since Gboard may call
     *                {@link InputConnection#requestCursorUpdates(int)} before the
     *                {@link InputMethodManager} has detected the change in the IME subtype.
     */
    private void checkImeLanguageTagAndRestartInput(boolean recheck) {
        String previousImeLanguageTag = mImeLanguageTag;
        updateImeLanguageTag();

        if (mImeLanguageTag != null && !mImeLanguageTag.equals(previousImeLanguageTag)) {
            restartInputConnection();
        } else if (recheck) {
            postDelayed(() -> checkImeLanguageTagAndRestartInput(false), 40);
        }
    }

    /**
     * Restart the input connection of the terminal view so that the IME reloads the input attributes
     * returned by {@link #onCreateInputConnection(EditorInfo)}, like after the IME language or the
     * termux properties have changed.
     */
    public void restartInputConnection() {
        InputMethodManager inputMethodManager = getInputMethodManager();
        if (inputMethodManager == null) return;

        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "Restarting IME input");
        inputMethodManager.restartInput(this);
    }

    /**
     * Invalidate the input connection of the terminal view, like to hide the suggestion strip that
     * Gboard shows for CJK languages. The IME is restarted on android versions before android 13.0
     * since {@link InputMethodManager#invalidateInput(View)} was added in android 13.0.
     */
    private void invalidateImeInput() {
        if (!mClient.isGboardCjkWorkaroundEnabled()) return;

        InputMethodManager inputMethodManager = getInputMethodManager();
        if (inputMethodManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            inputMethodManager.invalidateInput(this);
        else
            inputMethodManager.restartInput(this);
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mEmulator == null ? 1 : mEmulator.mRows;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
    }

    public void onScreenUpdated() {
        onScreenUpdated(false);
    }

    public void onScreenUpdated(boolean skipScrolling) {
        if (mEmulator == null) return;

        int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
        if (mTopRow < -rowsInHistory) setTopRow(-rowsInHistory);

        if (isSelectingText() || mEmulator.isAutoScrollDisabled()) {
            // Do not scroll when selecting text.
            int rowShift = mEmulator.getScrollCounter();
            if (-mTopRow + rowShift > rowsInHistory) {
                // .. unless we're hitting the end of history transcript, in which
                // case we abort text selection and scroll to end.
                if (isSelectingText())
                    stopTextSelectionMode();

                if (mEmulator.isAutoScrollDisabled()) {
                    setTopRow(-rowsInHistory);
                    skipScrolling = true;
                }
            } else {
                skipScrolling = true;
                setTopRow(mTopRow - rowShift);
                decrementYTextSelectionCursors(rowShift);
            }
        }

        if (!skipScrolling && mTopRow != 0) {
            // Scroll down if not already there.
            if (mTopRow < -3) {
                // Awaken scroll bars only if scrolling a noticeable amount
                // - we do not want visible scroll bars during normal typing
                // of one row at a time.
                awakenScrollBars();
            }
            setTopRow(0);
        }

        mEmulator.clearScrollCounter();

        invalidate();
        if (mAccessibilityEnabled) setContentDescription(getText());
    }

    /**
     * Redraw the view for new output of a session that is shown in a pane that is not the active
     * pane, so that both panes of a split screen update in real time like two separate terminal
     * windows on a desktop.
     *
     * Unlike {@link #onScreenUpdated()}, this keeps the scroll position of the view instead of
     * snapping to the bottom, so that a view that is scrolled back into the transcript keeps
     * showing the same rows while new rows are added below it. The accessibility content
     * description is not updated either, since computing it is expensive and a pane that is not the
     * active pane is not being interacted with.
     *
     * This relies on the invariant that a session is attached to at most one view, since the scroll
     * counter of the emulator is consumed here.
     */
    public void onScreenOutput() {
        if (mEmulator == null) return;

        cancelPendingOutputRedraw();

        int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
        int rowShift = mEmulator.getScrollCounter();
        mEmulator.clearScrollCounter();

        if (mTopRow < -rowsInHistory) {
            // The rows that were scrolled past have been trimmed from the transcript.
            setTopRow(-rowsInHistory);
        } else if (mTopRow != 0) {
            // Keep the rows that are visible in the viewport stable while rows are added below them,
            // the same way as onScreenUpdated() does when text is being selected.
            setTopRow(Math.min(0, mTopRow - rowShift));
            if (isSelectingText() && rowShift > 0) decrementYTextSelectionCursors(rowShift);
        }

        invalidate();
    }

    /**
     * Like {@link #onScreenOutput()}, but the redraw is delayed by up to {@code minIntervalMs}
     * milliseconds, so that a pane that is not the active pane is not redrawn on every frame when
     * it is flooded with output, which would take main thread time away from the active pane. A
     * {@code minIntervalMs} of 0 redraws immediately. Redraws are coalesced, so at most one redraw
     * is scheduled at a time, and at most one frame is drawn per {@code minIntervalMs}.
     */
    public void onScreenOutputThrottled(long minIntervalMs) {
        if (mEmulator == null) return;

        if (minIntervalMs <= 0) {
            onScreenOutput();
            return;
        }

        if (mOutputRedrawPending) return;
        mOutputRedrawPending = true;
        postDelayed(mOutputRedrawRunnable, minIntervalMs);
    }

    /** Cancel a redraw that was scheduled by {@link #onScreenOutputThrottled(long)}. */
    private void cancelPendingOutputRedraw() {
        if (!mOutputRedrawPending) return;
        mOutputRedrawPending = false;
        removeCallbacks(mOutputRedrawRunnable);
    }

    /**
     * Show the context menu of the terminal.
     *
     * The client is given the chance to show its own menu with
     * {@link TerminalViewClient#onShowContextMenu(TerminalView)}, like a styled dialog that the user can
     * configure, and only the system context menu of the view is shown if it does not.
     *
     * @return Always {@code true}, since either the client or the view shows a menu.
     */
    public boolean showTerminalContextMenu() {
        if (mClient != null && mClient.onShowContextMenu(this)) return true;
        showContextMenu();
        return true;
    }

    /** This must be called by the hosting activity in {@link Activity#onContextMenuClosed(Menu)}
     * when context menu for the {@link TerminalView} is started by
     * {@link TextSelectionCursorController#ACTION_MORE} is closed. */
    public void onContextMenuClosed(Menu menu) {
        // Unset the stored text since it shouldn't be used anymore and should be cleared from memory
        unsetStoredSelectedText();
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param textSize the new font size, in density-independent pixels.
     */
    public void setTextSize(int textSize) {
        mRenderer = new TerminalRenderer(textSize, mRenderer == null ? Typeface.MONOSPACE : mRenderer.mTypeface,
            mRenderer == null ? null : mRenderer.mItalicTypeface);
        updateSize();
    }

    public void setTypeface(Typeface newTypeface) {
        setTypeface(newTypeface, null);
    }

    /**
     * Set the typeface used to render text and optionally the one used to render italic text.
     *
     * @param newTypeface The typeface to use for the terminal grid.
     * @param newItalicTypeface The typeface to use for italic text, or {@code null} to render italic
     *                          text by skewing the {@code newTypeface} glyphs.
     */
    public void setTypeface(Typeface newTypeface, @Nullable Typeface newItalicTypeface) {
        mRenderer = new TerminalRenderer(mRenderer.mTextSize, newTypeface, newItalicTypeface);
        updateSize();
        invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    /**
     * Get the zero indexed column and row of the terminal view for the
     * position of the event.
     *
     * @param event The event with the position to get the column and row for.
     * @param relativeToScroll If true the column number will take the scroll
     * position into account. E.g. if scrolled 3 lines up and the event
     * position is in the top left, column will be -3 if relativeToScroll is
     * true and 0 if relativeToScroll is false.
     * @return Array with the column and row.
     */
    public int[] getColumnAndRow(MotionEvent event, boolean relativeToScroll) {
        int column = (int) (event.getX() / mRenderer.mFontWidth);
        int row = (int) ((event.getY() - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);
        if (relativeToScroll) {
            row += mTopRow;
        }
        return new int[] { column, row };
    }

    /** Send a single mouse event code to the terminal. */
    void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
        int[] columnAndRow = getColumnAndRow(e, false);
        int x = columnAndRow[0] + 1;
        int y = columnAndRow[1] + 1;
        if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
            if (mMouseStartDownTime == e.getDownTime()) {
                x = mMouseScrollStartX;
                y = mMouseScrollStartY;
            } else {
                mMouseStartDownTime = e.getDownTime();
                mMouseScrollStartX = x;
                mMouseScrollStartY = y;
            }
        }
        mEmulator.sendMouseEvent(button, x, y, pressed);
    }

    /** Perform a scroll, either from dragging the screen or by scrolling a mouse wheel. */
    void doScroll(MotionEvent event, int rowsDown) {
        boolean up = rowsDown < 0;
        int amount = Math.abs(rowsDown);
        for (int i = 0; i < amount; i++) {
            if (mEmulator.isMouseTrackingActive()) {
                sendMouseEventCode(event, up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
            } else if (mEmulator.isAlternateBufferActive()) {
                // Send up and down key events for scrolling, which is what some terminals do to make scroll work in
                // e.g. less, which shifts to the alt screen without mouse handling.
                handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
            } else {
                setTopRow(Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1))));
                if (!awakenScrollBars()) invalidate();
            }
        }
    }

    /** Overriding {@link View#onGenericMotionEvent(MotionEvent)}. */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mEmulator != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getAction() == MotionEvent.ACTION_SCROLL) {
            // Handle mouse wheel scrolling.
            boolean up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f;
            doScroll(event, up ? -3 : 3);
            return true;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @TargetApi(23)
    public boolean onTouchEvent(MotionEvent event) {
        if (mEmulator == null) return true;
        final int action = event.getAction();

        if (handleCommandMarkStripTouchEvent(event)) return true;

        if (action == MotionEvent.ACTION_CANCEL && mIsMouseDragFromTouchInProgress) {
            // Release the button press that was reported for a drag with a finger, since the gesture was
            // cancelled and the release event would otherwise not be reported, which would leave the
            // mouse pointer in a pressed state in the terminal program.
            mIsMouseDragFromTouchInProgress = false;
            sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
        }

        if (isSelectingText()) {
            updateFloatingToolbarVisibility(event);
            mGestureRecognizer.onTouchEvent(event);
            return true;
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                if (action == MotionEvent.ACTION_DOWN) showTerminalContextMenu();
                return true;
            } else if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (clipData != null) {
                    ClipData.Item clipItem = clipData.getItemAt(0);
                    if (clipItem != null) {
                        CharSequence text = clipItem.coerceToText(getContext());
                        if (!TextUtils.isEmpty(text)) mEmulator.paste(text.toString());
                    }
                }
            } else if (mEmulator.isMouseTrackingActive()) { // BUTTON_PRIMARY.
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON, event.getAction() == MotionEvent.ACTION_DOWN);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        sendMouseEventCode(event, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
                        break;
                }
            }
        }

        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "onKeyPreIme(keyCode=" + keyCode + ", event=" + event + ")");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelRequestAutoFill();
            if (isSelectingText()) {
                stopTextSelectionMode();
                return true;
            } else if (mClient.shouldBackButtonBeMappedToEscape()) {
                // Intercept back button to treat it as escape:
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        return onKeyDown(keyCode, event);
                    case KeyEvent.ACTION_UP:
                        return onKeyUp(keyCode, event);
                }
            }
        } else if (mClient.shouldUseCtrlSpaceWorkaround() &&
                   keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed()) {
            /* ctrl+space does not work on some ROMs without this workaround.
               However, this breaks it on devices where it works out of the box. */
            return onKeyDown(keyCode, event);
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Key presses in software keyboards will generally NOT trigger this listener, although some
     * may elect to do so in some situations. Do not rely on this to catch software key presses.
     * Gboard calls this when shouldEnforceCharBasedInput() is disabled (InputType.TYPE_NULL) instead
     * of calling commitText(), with deviceId=-1. However, Hacker's Keyboard, OpenBoard, LG Keyboard
     * call commitText().
     *
     * This function may also be called directly without android calling it, like by
     * `TerminalExtraKeys` which generates a KeyEvent manually which uses {@link KeyCharacterMap#VIRTUAL_KEYBOARD}
     * as the device (deviceId=-1), as does Gboard. That would normally use mappings defined in
     * `/system/usr/keychars/Virtual.kcm`. You can run `dumpsys input` to find the `KeyCharacterMapFile`
     * used by virtual keyboard or hardware keyboard. Note that virtual keyboard device is not the
     * same as software keyboard, like Gboard, etc. Its a fake device used for generating events and
     * for testing.
     *
     * We handle shift key in `commitText()` to convert codepoint to uppercase case there with a
     * call to {@link Character#toUpperCase(int)}, but here we instead rely on getUnicodeChar() for
     * conversion of keyCode, for both hardware keyboard shift key (via effectiveMetaState) and
     * `mClient.readShiftKey()`, based on value in kcm files.
     * This may result in different behaviour depending on keyboard and android kcm files set for the
     * InputDevice for the event passed to this function. This will likely be an issue for non-english
     * languages since `Virtual.kcm` in english only by default or at least in AOSP. For both hardware
     * shift key (via effectiveMetaState) and `mClient.readShiftKey()`, `getUnicodeChar()` is used
     * for shift specific behaviour which usually is to uppercase.
     *
     * For fn key on hardware keyboard, android checks kcm files for hardware keyboards, which is
     * `Generic.kcm` by default, unless a vendor specific one is defined. The event passed will have
     * {@link KeyEvent#META_FUNCTION_ON} set. If the kcm file only defines a single character or unicode
     * code point `\\uxxxx`, then only one event is passed with that value. However, if kcm defines
     * a `fallback` key for fn or others, like `key DPAD_UP { ... fn: fallback PAGE_UP }`, then
     * android will first pass an event with original key `DPAD_UP` and {@link KeyEvent#META_FUNCTION_ON}
     * set. But this function will not consume it and android will pass another event with `PAGE_UP`
     * and {@link KeyEvent#META_FUNCTION_ON} not set, which will be consumed.
     *
     * Now there are some other issues as well, firstly ctrl and alt flags are not passed to
     * `getUnicodeChar()`, so modified key values in kcm are not used. Secondly, if the kcm file
     * for other modifiers like shift or fn define a non-alphabet, like { fn: '\u0015' } to act as
     * DPAD_LEFT, the `getUnicodeChar()` will correctly return `21` as the code point but action will
     * not happen because the `handleKeyCode()` function that transforms DPAD_LEFT to `\033[D`
     * escape sequence for the terminal to perform the left action would not be called since its
     * called before `getUnicodeChar()` and terminal will instead get `21 0x15 Negative Acknowledgement`.
     * The solution to such issues is calling `getUnicodeChar()` before the call to `handleKeyCode()`
     * if user has defined a custom kcm file, like done in POC mentioned in #2237. Note that
     * Hacker's Keyboard calls `commitText()` so don't test fn/shift with it for this function.
     * https://github.com/termux/termux-app/pull/2237
     * https://github.com/agnostic-apollo/termux-app/blob/terminal-code-point-custom-mapping/terminal-view/src/main/java/com/termux/view/TerminalView.java
     *
     * Key Character Map (kcm) and Key Layout (kl) files info:
     * https://source.android.com/devices/input/key-character-map-files
     * https://source.android.com/devices/input/key-layout-files
     * https://source.android.com/devices/input/keyboard-devices
     * AOSP kcm and kl files:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/data/keyboards
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/packages/InputDevices/res/raw
     *
     * KeyCodes:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/view/KeyEvent.java
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/native/include/android/keycodes.h
     *
     * `dumpsys input`:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinger/reader/EventHub.cpp;l=1917
     *
     * Loading of keymap:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/services/inputflinger/reader/EventHub.cpp;l=1644
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/Keyboard.cpp;l=41
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/InputDevice.cpp
     * OVERLAY keymaps for hardware keyboards may be combined as well:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=165
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=831
     *
     * Parse kcm file:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=727
     * Parse key value:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=981
     *
     * `KeyEvent.getUnicodeChar()`
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/view/KeyEvent.java;l=2716
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/KeyCharacterMap.java;l=368
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/jni/android_view_KeyCharacterMap.cpp;l=117
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/native/libs/input/KeyCharacterMap.cpp;l=231
     *
     * Keyboard layouts advertised by applications, like for hardware keyboards via #ACTION_QUERY_KEYBOARD_LAYOUTS
     * Config is stored in `/data/system/input-manager-state.xml`
     * https://github.com/ris58h/custom-keyboard-layout
     * Loading from apps:
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=1221
     * Set:
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/hardware/input/InputManager.java;l=89
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/core/java/android/hardware/input/InputManager.java;l=543
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:packages/apps/Settings/src/com/android/settings/inputmethod/KeyboardLayoutDialogFragment.java;l=167
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=1385
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/PersistentDataStore.java
     * Get overlay keyboard layout
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/input/InputManagerService.java;l=2158
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r40:frameworks/base/services/core/jni/com_android_server_input_InputManagerService.cpp;l=616
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "onKeyDown(keyCode=" + keyCode + ", isSystem()=" + event.isSystem() + ", event=" + event + ")");
        if (mEmulator == null) return true;
        if (isSelectingText()) {
            stopTextSelectionMode();
        }

        if (mClient.onKeyDown(keyCode, event, mTermSession)) {
            invalidate();
            return true;
        } else if (event.isSystem() && (!mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
            return super.onKeyDown(keyCode, event);
        } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            mTermSession.write(event.getCharacters());
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            return super.onKeyDown(keyCode, event);
        }

        final int metaState = event.getMetaState();
        final boolean controlDown = event.isCtrlPressed() || mClient.readControlKey();
        final boolean leftAltDown = (metaState & KeyEvent.META_ALT_LEFT_ON) != 0 || mClient.readAltKey();
        final boolean shiftDown = event.isShiftPressed() || mClient.readShiftKey();
        final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;

        int keyMod = 0;
        if (controlDown) keyMod |= KeyHandler.KEYMOD_CTRL;
        if (event.isAltPressed() || leftAltDown) keyMod |= KeyHandler.KEYMOD_ALT;
        if (shiftDown) keyMod |= KeyHandler.KEYMOD_SHIFT;
        if (event.isNumLockOn()) keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
        // https://github.com/termux/termux-app/issues/731
        if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) mClient.logInfo(LOG_TAG, "handleKeyCode() took key event");
            return true;
        }

        // Clear Ctrl since we handle that ourselves:
        int bitsToClear = KeyEvent.META_CTRL_MASK;
        if (rightAltDownFromEvent) {
            // Let right Alt/Alt Gr be used to compose characters.
        } else {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }
        int effectiveMetaState = event.getMetaState() & ~bitsToClear;

        if (shiftDown) effectiveMetaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (mClient.readFnKey()) effectiveMetaState |= KeyEvent.META_FUNCTION_ON;

        int result = event.getUnicodeChar(effectiveMetaState);
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "KeyEvent#getUnicodeChar(" + effectiveMetaState + ") returned: " + result);
        if (result == 0) {
            return false;
        }

        int oldCombiningAccent = mCombiningAccent;
        if ((result & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // If entered combining accent previously, write it out:
            if (mCombiningAccent != 0)
                inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
            mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
        } else {
            if (mCombiningAccent != 0) {
                int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
                if (combinedChar > 0) result = combinedChar;
                mCombiningAccent = 0;
            }
            inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
        }

        if (mCombiningAccent != oldCombiningAccent) invalidate();

        return true;
    }

    public void inputCodePoint(int eventSource, int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED) {
            mClient.logInfo(LOG_TAG, "inputCodePoint(eventSource=" + eventSource + ", codePoint=" + codePoint + ", controlDownFromEvent=" + controlDownFromEvent + ", leftAltDownFromEvent="
                + leftAltDownFromEvent + ")");
        }

        if (mTermSession == null) return;

        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        if (mEmulator != null)
            mEmulator.setCursorBlinkState(true);

        final boolean controlDown = controlDownFromEvent || mClient.readControlKey();
        final boolean altDown = leftAltDownFromEvent || mClient.readAltKey();

        if (mClient.onCodePoint(codePoint, controlDown, mTermSession)) return;

        if (controlDown) {
            if (codePoint >= 'a' && codePoint <= 'z') {
                codePoint = codePoint - 'a' + 1;
            } else if (codePoint >= 'A' && codePoint <= 'Z') {
                codePoint = codePoint - 'A' + 1;
            } else if (codePoint == ' ' || codePoint == '2') {
                codePoint = 0;
            } else if (codePoint == '[' || codePoint == '3') {
                codePoint = 27; // ^[ (Esc)
            } else if (codePoint == '\\' || codePoint == '4') {
                codePoint = 28;
            } else if (codePoint == ']' || codePoint == '5') {
                codePoint = 29;
            } else if (codePoint == '^' || codePoint == '6') {
                codePoint = 30; // control-^
            } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31;
            } else if (codePoint == '8') {
                codePoint = 127; // DEL
            }
        }

        if (codePoint > -1) {
            // If not virtual or soft keyboard.
            if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
                // Work around bluetooth keyboards sending funny unicode characters instead
                // of the more normal ones from ASCII that terminal programs expect - the
                // desire to input the original characters should be low.
                switch (codePoint) {
                    case 0x02DC: // SMALL TILDE.
                        codePoint = 0x007E; // TILDE (~).
                        break;
                    case 0x02CB: // MODIFIER LETTER GRAVE ACCENT.
                        codePoint = 0x0060; // GRAVE ACCENT (`).
                        break;
                    case 0x02C6: // MODIFIER LETTER CIRCUMFLEX ACCENT.
                        codePoint = 0x005E; // CIRCUMFLEX ACCENT (^).
                        break;
                }
            }

            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            mTermSession.writeCodePoint(altDown, codePoint);
        }
    }

    /** Input the specified keyCode if applicable and return if the input was consumed. */
    public boolean handleKeyCode(int keyCode, int keyMod) {
        // Ensure cursor is shown when a key is pressed down like long hold on (arrow) keys
        if (mEmulator != null)
            mEmulator.setCursorBlinkState(true);

        if (handleKeyCodeAction(keyCode, keyMod))
            return true;

        TerminalEmulator term = mTermSession.getEmulator();
        String code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode());
        if (code == null) return false;
        mTermSession.write(code);
        return true;
    }

    public boolean handleKeyCodeAction(int keyCode, int keyMod) {
        boolean shiftDown = (keyMod & KeyHandler.KEYMOD_SHIFT) != 0;

        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                // shift+page_up and shift+page_down should scroll scrollback history instead of
                // scrolling command history or changing pages
                if (shiftDown) {
                    long time = SystemClock.uptimeMillis();
                    MotionEvent motionEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
                    doScroll(motionEvent, keyCode == KeyEvent.KEYCODE_PAGE_UP ? -mEmulator.mRows : mEmulator.mRows);
                    motionEvent.recycle();
                    return true;
                }
        }

       return false;
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event   A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
            mClient.logInfo(LOG_TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");

        // Do not return for KEYCODE_BACK and send it to the client since user may be trying
        // to exit the activity.
        if (mEmulator == null && keyCode != KeyEvent.KEYCODE_BACK) return true;

        if (mClient.onKeyUp(keyCode, event)) {
            invalidate();
            return true;
        } else if (event.isSystem()) {
            // Let system key events through.
            return super.onKeyUp(keyCode, event);
        }

        return true;
    }

    /**
     * This is called during layout when the size of this view has changed. If you were just added to the view
     * hierarchy, you're called with the old values of 0.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateSize();
    }

    /** Check if the terminal size in rows and columns should be updated. */
    public void updateSize() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth == 0 || viewHeight == 0 || mTermSession == null) return;

        // Set to 80 and 24 if you want to enable vttest.
        int newColumns = Math.max(4, (int) (viewWidth / mRenderer.mFontWidth));
        int newRows = Math.max(4, (viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);

        if (mEmulator == null || (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows)) {
            mTermSession.updateSize(newColumns, newRows, (int) mRenderer.getFontWidth(), mRenderer.getFontLineSpacing());
            mEmulator = mTermSession.getEmulator();
            mClient.onEmulatorSet();

            // Update mTerminalCursorBlinkerRunnable inner class mEmulator on session change
            if (mTerminalCursorBlinkerRunnable != null)
                mTerminalCursorBlinkerRunnable.setEmulator(mEmulator);

            // Restore cached top row value if session/emulator was switched back from a
            // different session or after activity restart. The top row value also needs to be
            // maintained after opening/closing soft keyboard.
            int topRow = 0;
            if (mEmulator != null) {
                int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
                int cachedTopRow = mEmulator.getTopRow();
                if (cachedTopRow >= -rowsInHistory) {
                    topRow = cachedTopRow;
                }
            }
            setTopRow(topRow);

            scrollTo(0, 0);
            invalidate();

            // The search matches refer to rows of the previous buffer layout, since the reflow of
            // the resize changes the rows, so recompute them and keep the selected match as close
            // to its previous location as possible.
            if (mSearchQuery != null) {
                int previousRow = -1, previousColumn = 0;
                if (mSearchMatchIndex >= 0 && mSearchMatchIndex < mSearchMatches.length) {
                    TerminalBuffer.SearchMatch previousMatch = mSearchMatches[mSearchMatchIndex];
                    previousRow = previousMatch.row;
                    previousColumn = previousMatch.startColumn;
                }
                setSearchQuery(mSearchQuery);
                if (previousRow != -1) selectSearchMatchAtOrAfter(previousRow, previousColumn);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mEmulator == null) {
            canvas.drawColor(0XFF000000);
        } else {
            // render the terminal view and highlight any selected text
            int[] sel = mDefaultSelectors;
            if (mTextSelectionCursorController != null) {
                mTextSelectionCursorController.getSelectors(sel);
            }

            mRenderer.render(mEmulator, canvas, mTopRow, sel[0], sel[1], sel[2], sel[3]);

            // render the text selection handles
            renderTextSelection();
        }
    }

    public TerminalSession getCurrentSession() {
        return mTermSession;
    }

    private CharSequence getText() {
        return mEmulator.getScreen().getSelectedText(0, mTopRow, mEmulator.mColumns, mTopRow + mEmulator.mRows);
    }

    public int getCursorX(float x) {
        return (int) (x / mRenderer.mFontWidth);
    }

    public int getCursorY(float y) {
        return (int) (((y - 40) / mRenderer.mFontLineSpacing) + mTopRow);
    }

    public int getPointX(int cx) {
        if (cx > mEmulator.mColumns) {
            cx = mEmulator.mColumns;
        }
        return Math.round(cx * mRenderer.mFontWidth);
    }

    public int getPointY(int cy) {
        return Math.round((cy - mTopRow) * mRenderer.mFontLineSpacing);
    }

    public int getTopRow() {
        return mTopRow;
    }

    public void setTopRow(int topRow) {
        setTopRow(topRow, true);
    }

    public void setTopRow(int topRow, boolean updateEmulator) {
        mTopRow = topRow;
        if (updateEmulator && mEmulator != null) {
            mEmulator.setTopRow(mTopRow);
        }
    }



    /**
     * Set the search query and highlight all of its matches in the screen and the transcript, with the
     * options that were set with {@link #setSearchOptions(boolean, boolean)}. The most recent match is
     * selected and scrolled into view, since that is usually the one being looked for. Passing an empty
     * query clears the search.
     *
     * @param query The text to search for.
     * @return The number of matches found.
     */
    public int setSearchQuery(@Nullable String query) {
        mSearchQuery = (query == null || query.isEmpty()) ? null : query;
        mSearchRegexInvalid = false;
        if (mSearchQuery == null || mEmulator == null) {
            clearSearch();
            return 0;
        }

        if (mSearchRegex) {
            try {
                Pattern.compile(mSearchQuery);
            } catch (PatternSyntaxException e) {
                // The query is typed by the user, so an unfinished expression must not throw, it just
                // matches nothing until it is valid.
                mSearchRegexInvalid = true;
            }
        }

        List<TerminalBuffer.SearchMatch> matches = mEmulator.getScreen().findAllMatches(
            mSearchQuery, mSearchCaseSensitive, mSearchRegex);
        mSearchMatches = matches.toArray(new TerminalBuffer.SearchMatch[0]);

        int count = mSearchMatches.length;
        mSearchMatchRows = new int[count];
        mSearchMatchStartColumns = new int[count];
        mSearchMatchEndColumns = new int[count];
        for (int i = 0; i < count; i++) {
            TerminalBuffer.SearchMatch match = mSearchMatches[i];
            mSearchMatchRows[i] = match.row;
            mSearchMatchStartColumns[i] = match.startColumn;
            mSearchMatchEndColumns[i] = match.endColumn;
        }

        mSearchMatchIndex = count > 0 ? count - 1 : -1;
        updateSearchHighlights();
        if (mSearchMatchIndex >= 0) scrollToSearchMatch(mSearchMatchIndex);
        invalidate();
        return count;
    }

    /** @return The number of matches of the current search query, or 0 if no search is active. */
    public int getSearchMatchCount() {
        return mSearchMatches.length;
    }

    /**
     * Set whether the search is case sensitive and whether its query is a regular expression, and apply
     * the options to the current query again.
     */
    public void setSearchOptions(boolean caseSensitive, boolean regex) {
        if (mSearchCaseSensitive == caseSensitive && mSearchRegex == regex) return;

        mSearchCaseSensitive = caseSensitive;
        mSearchRegex = regex;
        if (mSearchQuery != null) setSearchQuery(mSearchQuery);
    }

    /** @return Whether the search matches the case of the text. */
    public boolean isSearchCaseSensitive() {
        return mSearchCaseSensitive;
    }

    /** @return Whether the query of the search is a regular expression. */
    public boolean isSearchRegex() {
        return mSearchRegex;
    }

    /**
     * @return Whether the current query is an invalid regular expression, which the search bar shows to
     *         the user instead of pretending that there are no matches.
     */
    public boolean isSearchRegexInvalid() {
        return mSearchRegexInvalid;
    }

    /** @return The index of the currently selected search match, or -1 if none is selected. */
    public int getSearchMatchIndex() {
        return mSearchMatchIndex;
    }

    /**
     * Select the next or the previous search match, wrapping around at the end, and scroll it into
     * view.
     *
     * @param forward Whether to select the next match instead of the previous one.
     * @return The index of the selected match, or -1 if there are no matches.
     */
    public int findNextSearchMatch(boolean forward) {
        int count = mSearchMatches.length;
        if (count == 0) return -1;

        if (mSearchMatchIndex < 0) {
            mSearchMatchIndex = forward ? 0 : count - 1;
        } else {
            mSearchMatchIndex = (mSearchMatchIndex + (forward ? 1 : -1) + count) % count;
        }

        updateSearchHighlights();
        scrollToSearchMatch(mSearchMatchIndex);
        invalidate();
        return mSearchMatchIndex;
    }

    /** Clear the current search and its highlights. */
    public void clearSearch() {
        mSearchQuery = null;
        mSearchRegexInvalid = false;
        mSearchMatches = new TerminalBuffer.SearchMatch[0];
        mSearchMatchIndex = -1;
        mSearchMatchRows = new int[0];
        mSearchMatchStartColumns = new int[0];
        mSearchMatchEndColumns = new int[0];
        updateSearchHighlights();
        invalidate();
    }

    private void updateSearchHighlights() {
        mRenderer.setSearchHighlights(mSearchMatchRows, mSearchMatchStartColumns, mSearchMatchEndColumns, mSearchMatchIndex);
    }

    /** Select the match at or after {@code row} and {@code column}, and scroll it into view. */
    private void selectSearchMatchAtOrAfter(int row, int column) {
        int count = mSearchMatches.length;
        if (count == 0) return;

        int index = count - 1;
        for (int i = 0; i < count; i++) {
            TerminalBuffer.SearchMatch match = mSearchMatches[i];
            if (match.row > row || (match.row == row && match.startColumn >= column)) {
                index = i;
                break;
            }
        }

        mSearchMatchIndex = index;
        updateSearchHighlights();
        scrollToSearchMatch(index);
        invalidate();
    }

    /** @return Whether the external row is the start of a command, which is what command jumps scroll to. */
    private boolean isCommandRow(int row) {
        if (mEmulator == null) return false;
        int mark = mEmulator.getScreen().getCommandMark(row);
        return (mark & (TerminalBuffer.COMMAND_MARK_PROMPT_START | TerminalBuffer.COMMAND_MARK_COMMAND_START)) != 0;
    }

    /**
     * Scroll to the start of the previous command in the transcript, as reported by the shell
     * integration with `OSC 133`.
     *
     * @return Whether a previous command was found.
     */
    public boolean scrollToPreviousCommand() {
        if (mEmulator == null) return false;
        int minRow = -mEmulator.getScreen().getActiveTranscriptRows();
        for (int row = mTopRow - 1; row >= minRow; row--) {
            if (isCommandRow(row)) {
                scrollToCommandRow(row);
                return true;
            }
        }
        return false;
    }

    /**
     * Scroll to the start of the next command below the visible rows, or to the bottom of the
     * transcript if there is none, so that the user can get back to the current prompt.
     *
     * @return Whether the view was scrolled.
     */
    public boolean scrollToNextCommand() {
        if (mEmulator == null) return false;
        for (int row = mTopRow + mEmulator.mRows; row < mEmulator.mRows; row++) {
            if (isCommandRow(row)) {
                scrollToCommandRow(row);
                return true;
            }
        }
        if (mTopRow != 0) {
            scrollToCommandRow(0);
            return true;
        }
        return false;
    }

    /**
     * Scroll to the command with the sequence number {@code commandSequence}, which is used to jump
     * to the command that a notification card is about.
     *
     * Unlike {@link #scrollToPreviousCommand()}, the row is looked up by the sequence number of the
     * command that is stored on its row, so that the command can be jumped to even when other commands
     * have finished in the meantime.
     *
     * @return Whether the row of the command is still in the buffer and was scrolled to.
     */
    public boolean scrollToCommandSequence(int commandSequence) {
        if (mEmulator == null || commandSequence == 0) return false;

        int minRow = -mEmulator.getScreen().getActiveTranscriptRows();
        for (int row = minRow; row < mEmulator.mRows; row++) {
            if (mEmulator.getScreen().getCommandMarkSequence(row) != commandSequence) continue;
            scrollToCommandRow(row);
            return true;
        }

        return false;
    }

    /**
     * Handle a touch on the strip on the right edge where the command marks are drawn. A tap on the
     * strip scrolls to the command mark nearest to the tapped row.
     *
     * @return Whether the event was handled.
     */
    private boolean handleCommandMarkStripTouchEvent(MotionEvent event) {
        if (mEmulator == null) return false;
        // Do not interfere with the selection handles or with clearing a selection.
        if (isSelectingText()) return false;

        final float stripWidth = COMMAND_MARK_TOUCH_STRIP_WIDTH_DP * getResources().getDisplayMetrics().density;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (event.getX() < getWidth() - stripWidth) return false;
                int row = findCommandRowNear(mTopRow + (int) (event.getY() / mRenderer.mFontLineSpacing));
                if (row == Integer.MIN_VALUE) return false;
                mIsCommandMarkStripTouchInProgress = true;
                mCommandMarkStripTouchRow = row;
                mCommandMarkStripTouchDownY = event.getY();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                return mIsCommandMarkStripTouchInProgress;
            case MotionEvent.ACTION_UP:
                if (!mIsCommandMarkStripTouchInProgress) return false;
                mIsCommandMarkStripTouchInProgress = false;
                if (Math.abs(event.getY() - mCommandMarkStripTouchDownY) <= ViewConfiguration.get(getContext()).getScaledTouchSlop())
                    scrollToCommandRow(mCommandMarkStripTouchRow);
                return true;
            case MotionEvent.ACTION_CANCEL:
                boolean wasInProgress = mIsCommandMarkStripTouchInProgress;
                mIsCommandMarkStripTouchInProgress = false;
                return wasInProgress;
            default:
                return false;
        }
    }

    /** Find a row with a command mark within a few rows of {@code row}, or {@code Integer.MIN_VALUE}. */
    private int findCommandRowNear(int row) {
        if (mEmulator == null) return Integer.MIN_VALUE;
        int minRow = -mEmulator.getScreen().getActiveTranscriptRows();
        int maxRow = mEmulator.mRows - 1;
        if (row < minRow || row > maxRow) return Integer.MIN_VALUE;
        if (isCommandRow(row)) return row;
        for (int offset = 1; offset <= 4; offset++) {
            if (row - offset >= minRow && isCommandRow(row - offset)) return row - offset;
            if (row + offset <= maxRow && isCommandRow(row + offset)) return row + offset;
        }
        return Integer.MIN_VALUE;
    }

    /** Scroll the terminal so that the command row is at the top of the view and clamp it to the buffer. */
    private void scrollToCommandRow(int row) {
        if (mEmulator == null) return;
        int minRow = -mEmulator.getScreen().getActiveTranscriptRows();
        if (row < minRow) row = minRow;
        if (row > 0) row = 0;
        if (row != mTopRow) setTopRow(row);
        invalidate();
    }

    /** Scroll the terminal so that the search match at {@code index} is visible. */
    private void scrollToSearchMatch(int index) {
        if (mEmulator == null || index < 0 || index >= mSearchMatches.length) return;

        int row = mSearchMatches[index].row;
        int rows = mEmulator.mRows;
        int topRow = mTopRow;
        if (row < topRow) {
            topRow = row;
        } else if (row >= topRow + rows) {
            topRow = row - rows + 1;
        }

        int minTopRow = -mEmulator.getScreen().getActiveTranscriptRows();
        if (topRow < minTopRow) topRow = minTopRow;
        if (topRow > 0) topRow = 0;

        if (topRow != mTopRow) setTopRow(topRow);
    }



    /**
     * Define functions required for AutoFill API
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void autofill(AutofillValue value) {
        if (value.isText()) {
            mTermSession.write(value.getTextValue().toString());
        }

        resetAutoFill();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return mAutoFillType;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public String[] getAutofillHints() {
        return mAutoFillHints;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public AutofillValue getAutofillValue() {
        return AutofillValue.forText("");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getImportantForAutofill() {
        return mAutoFillImportance;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private synchronized void resetAutoFill() {
        // Restore none type so that AutoFill UI isn't shown anymore.
        mAutoFillType = AUTOFILL_TYPE_NONE;
        mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_NO;
        mAutoFillHints = new String[0];
    }

    public AutofillManager getAutoFillManagerService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;

        try {
            Context context = getContext();
            if (context == null) return null;
            return context.getSystemService(AutofillManager.class);
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to get AutofillManager service", e);
            return null;
        }
    }

    public boolean isAutoFillEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            return autofillManager != null && autofillManager.isEnabled();
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to check if Autofill is enabled", e);
            return false;
        }
    }

    public synchronized void requestAutoFillUsername() {
        requestAutoFill(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new String[]{View.AUTOFILL_HINT_USERNAME} :
                null);
    }

    public synchronized void requestAutoFillPassword() {
        requestAutoFill(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new String[]{View.AUTOFILL_HINT_PASSWORD} :
            null);
    }

    public synchronized void requestAutoFill(String[] autoFillHints) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (autoFillHints == null || autoFillHints.length < 1) return;

        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            if (autofillManager != null && autofillManager.isEnabled()) {
                // Update type that will be returned by `getAutofillType()` so that AutoFill UI is shown.
                mAutoFillType = AUTOFILL_TYPE_TEXT;
                // Update importance that will be returned by `getImportantForAutofill()` so that
                // AutoFill considers the view as important.
                mAutoFillImportance = IMPORTANT_FOR_AUTOFILL_YES;
                // Update hints that will be returned by `getAutofillHints()` for which to show AutoFill UI.
                mAutoFillHints = autoFillHints;
                autofillManager.requestAutofill(this);
            }
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to request Autofill", e);
        }
    }

    public synchronized void cancelRequestAutoFill() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (mAutoFillType == AUTOFILL_TYPE_NONE) return;

        try {
            AutofillManager autofillManager = getAutoFillManagerService();
            if (autofillManager != null && autofillManager.isEnabled()) {
                resetAutoFill();
                autofillManager.cancel();
            }
        } catch (Exception e) {
            mClient.logStackTraceWithMessage(LOG_TAG, "Failed to cancel Autofill request", e);
        }
    }





    /**
     * Set terminal cursor blinker rate. It must be between {@link #TERMINAL_CURSOR_BLINK_RATE_MIN}
     * and {@link #TERMINAL_CURSOR_BLINK_RATE_MAX}, otherwise it will be disabled.
     *
     * The {@link #setTerminalCursorBlinkerState(boolean, boolean)} must be called after this
     * for changes to take effect if not disabling.
     *
     * @param blinkRate The value to set.
     * @return Returns {@code true} if setting blinker rate was successfully set, otherwise [@code false}.
     */
    public synchronized boolean setTerminalCursorBlinkerRate(int blinkRate) {
        boolean result;

        // If cursor blinking rate is not valid
        if (blinkRate != 0 && (blinkRate < TERMINAL_CURSOR_BLINK_RATE_MIN || blinkRate > TERMINAL_CURSOR_BLINK_RATE_MAX)) {
            mClient.logError(LOG_TAG, "The cursor blink rate must be in between " + TERMINAL_CURSOR_BLINK_RATE_MIN + "-" + TERMINAL_CURSOR_BLINK_RATE_MAX + ": " + blinkRate);
            mTerminalCursorBlinkerRate = 0;
            result = false;
        } else {
            mClient.logVerbose(LOG_TAG, "Setting cursor blinker rate to " + blinkRate);
            mTerminalCursorBlinkerRate = blinkRate;
            result = true;
        }

        if (mTerminalCursorBlinkerRate == 0) {
            mClient.logVerbose(LOG_TAG, "Cursor blinker disabled");
            stopTerminalCursorBlinker();
        }

        return result;
    }

    /**
     * Sets whether cursor blinker should be started or stopped. Cursor blinker will only be
     * started if {@link #mTerminalCursorBlinkerRate} does not equal 0 and is between
     * {@link #TERMINAL_CURSOR_BLINK_RATE_MIN} and {@link #TERMINAL_CURSOR_BLINK_RATE_MAX}.
     *
     * This should be called when the view holding this activity is resumed or stopped so that
     * cursor blinker does not run when activity is not visible. If you call this on onResume()
     * to start cursor blinking, then ensure that {@link #mEmulator} is set, otherwise wait for the
     * {@link TerminalViewClient#onEmulatorSet()} event after calling {@link #attachSession(TerminalSession)}
     * for the first session added in the activity since blinking will not start if {@link #mEmulator}
     * is not set, like if activity is started again after exiting it with double back press. Do not
     * call this directly after {@link #attachSession(TerminalSession)} since {@link #updateSize()}
     * may return without setting {@link #mEmulator} since width/height may be 0. Its called again in
     * {@link #onSizeChanged(int, int, int, int)}. Calling on onResume() if emulator is already set
     * is necessary, since onEmulatorSet() may not be called after activity is started after device
     * display timeout with double tap and not power button.
     *
     * It should also be called on the
     * {@link com.termux.terminal.TerminalSessionClient#onTerminalCursorStateChange(boolean)}
     * callback when cursor is enabled or disabled so that blinker is disabled if cursor is not
     * to be shown. It should also be checked if activity is visible if blinker is to be started
     * before calling this.
     *
     * It should also be called after terminal is reset with {@link TerminalSession#reset()} in case
     * cursor blinker was disabled before reset due to call to
     * {@link com.termux.terminal.TerminalSessionClient#onTerminalCursorStateChange(boolean)}.
     *
     * How cursor blinker starting works is by registering a {@link Runnable} with the looper of
     * the main thread of the app which when run, toggles the cursor blinking state and re-registers
     * itself to be called with the delay set by {@link #mTerminalCursorBlinkerRate}. When cursor
     * blinking needs to be disabled, we just cancel any callbacks registered. We don't run our own
     * "thread" and let the thread for the main looper do the work for us, whose usage is also
     * required to update the UI, since it also handles other calls to update the UI as well based
     * on a queue.
     *
     * Note that when moving cursor in text editors like nano, the cursor state is quickly
     * toggled `-> off -> on`, which would call this very quickly sequentially. So that if cursor
     * is moved 2 or more times quickly, like long hold on arrow keys, it would trigger
     * `-> off -> on -> off -> on -> ...`, and the "on" callback at index 2 is automatically
     * cancelled by next "off" callback at index 3 before getting a chance to be run. For this case
     * we log only if {@link #TERMINAL_VIEW_KEY_LOGGING_ENABLED} is enabled, otherwise would clutter
     * the log. We don't start the blinking with a delay to immediately show cursor in case it was
     * previously not visible.
     *
     * @param start If cursor blinker should be started or stopped.
     * @param startOnlyIfCursorEnabled If set to {@code true}, then it will also be checked if the
     *                                 cursor is even enabled by {@link TerminalEmulator} before
     *                                 starting the cursor blinker.
     */
    public synchronized void setTerminalCursorBlinkerState(boolean start, boolean startOnlyIfCursorEnabled) {
        // Stop any existing cursor blinker callbacks
        stopTerminalCursorBlinker();

        if (mEmulator == null) return;

        mEmulator.setCursorBlinkingEnabled(false);

        if (start) {
            // If cursor blinker is not enabled or is not valid
            if (mTerminalCursorBlinkerRate < TERMINAL_CURSOR_BLINK_RATE_MIN || mTerminalCursorBlinkerRate > TERMINAL_CURSOR_BLINK_RATE_MAX)
                return;
            // If cursor blinder is to be started only if cursor is enabled
            else if (startOnlyIfCursorEnabled && ! mEmulator.isCursorEnabled()) {
                if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                    mClient.logVerbose(LOG_TAG, "Ignoring call to start cursor blinker since cursor is not enabled");
                return;
            }

            // Start cursor blinker runnable
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient.logVerbose(LOG_TAG, "Starting cursor blinker with the blink rate " + mTerminalCursorBlinkerRate);
            if (mTerminalCursorBlinkerHandler == null)
                mTerminalCursorBlinkerHandler = new Handler(Looper.getMainLooper());
            mTerminalCursorBlinkerRunnable = new TerminalCursorBlinkerRunnable(mEmulator, mTerminalCursorBlinkerRate);
            mEmulator.setCursorBlinkingEnabled(true);
            mTerminalCursorBlinkerRunnable.run();
        }
    }

    /**
     * Cancel the terminal cursor blinker callbacks
     */
    private void stopTerminalCursorBlinker() {
        if (mTerminalCursorBlinkerHandler != null && mTerminalCursorBlinkerRunnable != null) {
            if (TERMINAL_VIEW_KEY_LOGGING_ENABLED)
                mClient.logVerbose(LOG_TAG, "Stopping cursor blinker");
            mTerminalCursorBlinkerHandler.removeCallbacks(mTerminalCursorBlinkerRunnable);
        }
    }

    private class TerminalCursorBlinkerRunnable implements Runnable {

        private TerminalEmulator mEmulator;
        private final int mBlinkRate;

        // Initialize with false so that initial blink state is visible after toggling
        boolean mCursorVisible = false;

        public TerminalCursorBlinkerRunnable(TerminalEmulator emulator, int blinkRate) {
            mEmulator = emulator;
            mBlinkRate = blinkRate;
        }

        public void setEmulator(TerminalEmulator emulator) {
            mEmulator = emulator;
        }

        public void run() {
            try {
                if (mEmulator != null) {
                    // Toggle the blink state and then invalidate() the view so
                    // that onDraw() is called, which then calls TerminalRenderer.render()
                    // which checks with TerminalEmulator.shouldCursorBeVisible() to decide whether
                    // to draw the cursor or not
                    mCursorVisible = !mCursorVisible;
                    //mClient.logVerbose(LOG_TAG, "Toggling cursor blink state to " + mCursorVisible);
                    mEmulator.setCursorBlinkState(mCursorVisible);
                    invalidate();
                }
            } finally {
                // Recall the Runnable after mBlinkRate milliseconds to toggle the blink state
                mTerminalCursorBlinkerHandler.postDelayed(this, mBlinkRate);
            }
        }
    }



    /**
     * Define functions required for text selection and its handles.
     */
    TextSelectionCursorController getTextSelectionCursorController() {
        if (mTextSelectionCursorController == null) {
            mTextSelectionCursorController = new TextSelectionCursorController(this);

            final ViewTreeObserver observer = getViewTreeObserver();
            if (observer != null) {
                observer.addOnTouchModeChangeListener(mTextSelectionCursorController);
            }
        }

        return mTextSelectionCursorController;
    }

    private void showTextSelectionCursors(MotionEvent event) {
        getTextSelectionCursorController().show(event);
    }

    private boolean hideTextSelectionCursors() {
        return getTextSelectionCursorController().hide();
    }

    private void renderTextSelection() {
        if (mTextSelectionCursorController != null)
            mTextSelectionCursorController.render();
    }

    public boolean isSelectingText() {
        if (mTextSelectionCursorController != null) {
            return mTextSelectionCursorController.isActive();
        } else {
            return false;
        }
    }

    /** Get the currently selected text if selecting. */
    public String getSelectedText() {
        if (isSelectingText() && mTextSelectionCursorController != null)
            return mTextSelectionCursorController.getSelectedText();
        else
            return null;
    }

    /** Get the selected text stored before "MORE" button was pressed on the context menu. */
    @Nullable
    public String getStoredSelectedText() {
        return mTextSelectionCursorController != null ? mTextSelectionCursorController.getStoredSelectedText() : null;
    }

    /** Unset the selected text stored before "MORE" button was pressed on the context menu. */
    public void unsetStoredSelectedText() {
        if (mTextSelectionCursorController != null) mTextSelectionCursorController.unsetStoredSelectedText();
    }

    private ActionMode getTextSelectionActionMode() {
        if (mTextSelectionCursorController != null) {
            return mTextSelectionCursorController.getActionMode();
        } else {
            return null;
        }
    }

    public void startTextSelectionMode(MotionEvent event) {
        if (!requestFocus()) {
            return;
        }

        showTextSelectionCursors(event);
        mClient.copyModeChanged(isSelectingText());

        invalidate();
    }

    public void stopTextSelectionMode() {
        if (hideTextSelectionCursors()) {
            mClient.copyModeChanged(isSelectingText());
            invalidate();
        }
    }

    private void decrementYTextSelectionCursors(int decrement) {
        if (mTextSelectionCursorController != null) {
            mTextSelectionCursorController.decrementYTextSelectionCursors(decrement);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mTextSelectionCursorController != null) {
            getViewTreeObserver().addOnTouchModeChangeListener(mTextSelectionCursorController);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        cancelPendingOutputRedraw();

        if (mTextSelectionCursorController != null) {
            // Might solve the following exception
            // android.view.WindowLeaked: Activity com.termux.app.TermuxActivity has leaked window android.widget.PopupWindow
            stopTextSelectionMode();

            getViewTreeObserver().removeOnTouchModeChangeListener(mTextSelectionCursorController);
            mTextSelectionCursorController.onDetached();
        }
    }



    /**
     * Define functions required for long hold toolbar.
     */
    private final Runnable mShowFloatingToolbar = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void run() {
            if (getTextSelectionActionMode() != null) {
                getTextSelectionActionMode().hide(0);  // hide off.
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            int delay = ViewConfiguration.getDoubleTapTimeout();
            postDelayed(mShowFloatingToolbar, delay);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void hideFloatingToolbar() {
        if (getTextSelectionActionMode() != null) {
            removeCallbacks(mShowFloatingToolbar);
            getTextSelectionActionMode().hide(-1);
        }
    }

    public void updateFloatingToolbarVisibility(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getTextSelectionActionMode() != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    hideFloatingToolbar();
                    break;
                case MotionEvent.ACTION_UP:  // fall through
                case MotionEvent.ACTION_CANCEL:
                    showFloatingToolbar();
            }
        }
    }

}
