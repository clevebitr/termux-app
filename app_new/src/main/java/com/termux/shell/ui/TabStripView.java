package com.termux.shell.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.R;
import com.termux.shell.tabs.TabWorkspace;
import com.termux.shell.tabs.WorkspaceTab;

import java.util.ArrayList;
import java.util.List;

/**
 * 窗口级标签栏。
 *
 * <p>目的：以自绘方式呈现与 Windows Terminal 相近的标签条：标签相邻排列、当前标签使用强调背景、
 * 末尾固定「新建标签」按钮。自绘而非组合控件的原因是需要按可用宽度在「平均分配」与「按标题收缩」
 * 两种模式间切换，并在超出宽度时交由外层横向滚动容器处理。
 *
 * <p>行为：
 * <ul>
 *     <li>{@code equal} 模式下标签平均分配可用宽度，单标签不低于最小宽度；</li>
 *     <li>{@code compact} 模式下标签宽度按标题测量值收缩，上层容器未给出确定宽度时按此模式布局；</li>
 *     <li>点击标签切换，点击关闭按钮关闭，长按标签交给宿主弹出菜单；</li>
 *     <li>按下时显示背景反馈，横向移动超过触摸阈值后取消按压，避免与滚动冲突。</li>
 * </ul>
 *
 * <p>约束：
 * <ul>
 *     <li>本视图不修改标签集合，全部变更请求通过 {@link Listener} 交给宿主执行；</li>
 *     <li>仅支持主线程访问；标签数量与宽度均受工作区上限约束；</li>
 *     <li>可访问性（无障碍朗读与焦点遍历）尚未实现，记录于 {@code docs/DELIVERY_PLAN.md} 的后续任务。</li>
 * </ul>
 */
public final class TabStripView extends View {

    private static final int STRIP_HEIGHT_DP = 44;
    private static final int TAB_MIN_WIDTH_DP = 96;
    private static final int TAB_MAX_WIDTH_DP = 224;
    private static final int TAB_HORIZONTAL_PADDING_DP = 12;
    private static final int TAB_GAP_DP = 4;
    private static final int ADD_BUTTON_WIDTH_DP = 40;
    private static final int CLOSE_ICON_SIZE_DP = 16;
    private static final int CLOSE_ICON_GAP_DP = 8;
    private static final float TAB_CORNER_RADIUS_DP = 12f;
    private static final float TEXT_SIZE_SP = 13f;
    private static final float PRESSED_ALPHA = 0.12f;

    /**
     * 标签栏交互回调。回调只描述用户意图，状态变更由宿主负责。
     */
    public interface Listener {

        void onTabSelected(int index);

        void onTabClosed(int index);

        void onTabLongPressed(int index, @NonNull View anchor);

        void onNewTabRequested();
    }

    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> mTabRects = new ArrayList<>();
    private final List<RectF> mCloseRects = new ArrayList<>();
    private final RectF mAddRect = new RectF();

    private final float mMinTabWidth;
    private final float mMaxTabWidth;
    private final float mTabPadding;
    private final float mTabGap;
    private final float mAddWidth;
    private final float mCloseIconSize;
    private final float mCloseIconGap;
    private final float mCornerRadius;
    private final float mPressedAlpha;
    private final float mTouchSlop;
    private final long mLongPressTimeout;

    @Nullable
    private Drawable mCloseIcon;
    @Nullable
    private Drawable mAddIcon;

    private int mColorActiveBackground;
    private int mColorInactiveBackground;
    private int mColorTextActive;
    private int mColorTextInactive;

    @Nullable
    private TabWorkspace mWorkspace;
    @Nullable
    private Listener mListener;
    private boolean mShowCloseButton = true;
    private boolean mEqualWidth = true;

    private int mPressedTabIndex = -1;
    private boolean mPressedAddButton;
    private boolean mPressedCloseButton;
    private boolean mLongPressFired;
    private float mDownX;
    private float mDownY;

    private final Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPressedTabIndex < 0 || mPressedCloseButton) {
                return;
            }
            mLongPressFired = true;
            Listener listener = mListener;
            if (listener != null) {
                listener.onTabLongPressed(mPressedTabIndex, TabStripView.this);
            }
            invalidate();
        }
    };

    public TabStripView(@NonNull Context context) {
        this(context, null);
    }

    public TabStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabStripView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        mMinTabWidth = TAB_MIN_WIDTH_DP * density;
        mMaxTabWidth = TAB_MAX_WIDTH_DP * density;
        mTabPadding = TAB_HORIZONTAL_PADDING_DP * density;
        mTabGap = TAB_GAP_DP * density;
        mAddWidth = ADD_BUTTON_WIDTH_DP * density;
        mCloseIconSize = CLOSE_ICON_SIZE_DP * density;
        mCloseIconGap = CLOSE_ICON_GAP_DP * density;
        mCornerRadius = TAB_CORNER_RADIUS_DP * density;
        mPressedAlpha = PRESSED_ALPHA;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();

        mTextPaint.setTextSize(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, getResources().getDisplayMetrics()));

        Drawable closeIcon = ContextCompat.getDrawable(context, com.termux.shell.R.drawable.ic_tab_close);
        mCloseIcon = closeIcon == null ? null : DrawableCompat.wrap(closeIcon).mutate();
        Drawable addIcon = ContextCompat.getDrawable(context, com.termux.shell.R.drawable.ic_tab_add);
        mAddIcon = addIcon == null ? null : DrawableCompat.wrap(addIcon).mutate();

        refreshThemeColors();
    }

    /**
     * 重新解析主题颜色。日夜模式切换后由宿主调用，避免自绘颜色停留在旧主题。
     */
    public void refreshThemeColors() {
        Context context = getContext();
        mColorActiveBackground = ThemeColors.resolve(context, R.attr.colorSecondaryContainer, 0xFFE8DEF8);
        mColorInactiveBackground = ThemeColors.resolve(context, R.attr.colorSurfaceVariant, 0xFFE7E0EC);
        mColorTextActive = ThemeColors.resolve(context, R.attr.colorOnSecondaryContainer, 0xFF1D192B);
        mColorTextInactive = ThemeColors.resolve(context, R.attr.colorOnSurfaceVariant, 0xFF49454F);
        invalidate();
    }

    /**
     * 设置标签工作区并重新布局。
     */
    public void setWorkspace(@Nullable TabWorkspace workspace) {
        mWorkspace = workspace;
        removeCallbacks(mLongPressRunnable);
        mPressedTabIndex = -1;
        mPressedAddButton = false;
        mPressedCloseButton = false;
        requestLayout();
        invalidate();
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * 设置是否绘制关闭按钮。关闭后点击标签仅切换，不提供关闭入口。
     */
    public void setShowCloseButton(boolean showCloseButton) {
        mShowCloseButton = showCloseButton;
        requestLayout();
        invalidate();
    }

    /**
     * 设置标签宽度模式。{@code true} 为平均分配，{@code false} 为按标题收缩。
     */
    public void setEqualWidth(boolean equalWidth) {
        mEqualWidth = equalWidth;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (STRIP_HEIGHT_DP * getResources().getDisplayMetrics().density);
        int resolvedHeight = resolveSize(height, heightMeasureSpec);

        int count = mWorkspace == null ? 0 : mWorkspace.size();
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int contentWidth;
        if (count == 0) {
            contentWidth = (int) mAddWidth;
        } else if (widthMode == MeasureSpec.EXACTLY && mEqualWidth) {
            contentWidth = widthSize;
        } else {
            contentWidth = (int) (measureNaturalTabWidth() * count
                + mTabGap * count + mAddWidth);
        }
        setMeasuredDimension(resolveSize(contentWidth, widthMeasureSpec), resolvedHeight);
        layoutTabRects(count, getMeasuredWidth());
    }

    /**
     * 按标题测量单个标签的期望宽度，并按最小与最大宽度截断。
     */
    private float measureNaturalTabWidth() {
        TabWorkspace workspace = mWorkspace;
        if (workspace == null || workspace.size() == 0) {
            return mMinTabWidth;
        }
        float widest = 0f;
        for (WorkspaceTab tab : workspace.getTabs()) {
            float textWidth = mTextPaint.measureText(tab.getTitle());
            float width = textWidth + mTabPadding * 2;
            if (mShowCloseButton) {
                width += mCloseIconSize + mCloseIconGap;
            }
            widest = Math.max(widest, width);
        }
        return Math.min(mMaxTabWidth, Math.max(mMinTabWidth, widest));
    }

    private void layoutTabRects(int count, int measuredWidth) {
        mTabRects.clear();
        mCloseRects.clear();
        if (count <= 0) {
            mAddRect.set(0f, 0f, Math.min(mAddWidth, measuredWidth), getMeasuredHeight());
            return;
        }

        float available = measuredWidth - mAddWidth - mTabGap;
        float tabWidth;
        if (mEqualWidth && getMeasuredWidth() > 0) {
            // 采用平均分配时，可用宽度已由首轮测量的自然宽度保证不小于最小宽度之和，
            // 此处只需防止除零与负值，不再单独施加最小宽度，否则内容会超出宿主宽度而被裁剪。
            tabWidth = Math.max(1f, available / count);
            tabWidth = Math.min(mMaxTabWidth, tabWidth);
        } else {
            tabWidth = measureNaturalTabWidth();
        }
        tabWidth = Math.max(1f, Math.min(mMaxTabWidth, tabWidth));

        float left = 0f;
        float top = 0f;
        float bottom = getMeasuredHeight();
        for (int i = 0; i < count; i++) {
            float right = left + tabWidth;
            RectF tabRect = new RectF(left, top, right, bottom);
            mTabRects.add(tabRect);

            RectF closeRect = new RectF();
            if (mShowCloseButton) {
                float closeLeft = right - mTabPadding - mCloseIconSize;
                float centerY = (top + bottom) / 2f;
                closeRect.set(closeLeft, centerY - mCloseIconSize / 2f,
                    closeLeft + mCloseIconSize, centerY + mCloseIconSize / 2f);
            }
            mCloseRects.add(closeRect);
            left = right + mTabGap;
        }

        float addLeft = Math.min(left, measuredWidth - mAddWidth);
        mAddRect.set(addLeft, top, addLeft + mAddWidth, bottom);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        TabWorkspace workspace = mWorkspace;
        if (workspace == null || workspace.size() == 0) {
            drawAddButton(canvas, false);
            return;
        }

        int activeIndex = workspace.getActiveIndex();
        List<WorkspaceTab> tabs = workspace.getTabs();
        for (int i = 0; i < tabs.size() && i < mTabRects.size(); i++) {
            drawTab(canvas, tabs.get(i), i, i == activeIndex);
        }
        drawAddButton(canvas, mPressedAddButton);
    }

    private void drawTab(@NonNull Canvas canvas, @NonNull WorkspaceTab tab, int index, boolean active) {
        RectF rect = mTabRects.get(index);
        boolean pressed = index == mPressedTabIndex && !mLongPressFired;

        if (active) {
            mTabPaint.setColor(mColorActiveBackground);
            canvas.drawRoundRect(rect, mCornerRadius, mCornerRadius, mTabPaint);
        } else if (pressed) {
            mTabPaint.setColor(mColorInactiveBackground);
            mTabPaint.setAlpha((int) (255 * mPressedAlpha));
            canvas.drawRoundRect(rect, mCornerRadius, mCornerRadius, mTabPaint);
            mTabPaint.setAlpha(255);
        }

        int textColor = active ? mColorTextActive : mColorTextInactive;
        mTextPaint.setColor(textColor);

        RectF closeRect = index < mCloseRects.size() ? mCloseRects.get(index) : null;
        float textRight = rect.right - mTabPadding;
        if (mShowCloseButton && closeRect != null) {
            textRight = closeRect.left - mCloseIconGap;
        }
        float textWidth = Math.max(0f, textRight - (rect.left + mTabPadding));
        CharSequence title = TextUtils.ellipsize(tab.getTitle(), mTextPaint, textWidth, TextUtils.TruncateAt.END);
        float baseline = rect.centerY() - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.bottom);
        canvas.drawText(title, 0, title.length(), rect.left + mTabPadding, baseline, mTextPaint);
        canvas.restore();

        Drawable closeIcon = mCloseIcon;
        if (mShowCloseButton && closeIcon != null && closeRect != null) {
            DrawableCompat.setTint(closeIcon, textColor);
            closeIcon.setBounds(Math.round(closeRect.left), Math.round(closeRect.top),
                Math.round(closeRect.right), Math.round(closeRect.bottom));
            closeIcon.setAlpha(pressed && mPressedCloseButton ? 255 : 190);
            closeIcon.draw(canvas);
        }
    }

    private void drawAddButton(@NonNull Canvas canvas, boolean pressed) {
        if (pressed) {
            mTabPaint.setColor(mColorInactiveBackground);
            mTabPaint.setAlpha((int) (255 * mPressedAlpha));
            canvas.drawRoundRect(mAddRect, mCornerRadius, mCornerRadius, mTabPaint);
            mTabPaint.setAlpha(255);
        }
        Drawable addIcon = mAddIcon;
        if (addIcon == null) {
            return;
        }
        int iconSize = Math.round(mCloseIconSize);
        float centerX = mAddRect.centerX();
        float centerY = mAddRect.centerY();
        addIcon.setBounds(Math.round(centerX - iconSize / 2f), Math.round(centerY - iconSize / 2f),
            Math.round(centerX + iconSize / 2f), Math.round(centerY + iconSize / 2f));
        DrawableCompat.setTint(addIcon, mColorTextInactive);
        addIcon.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownX = event.getX();
                mDownY = event.getY();
                mLongPressFired = false;
                mPressedTabIndex = findTabAt(event.getX(), event.getY());
                mPressedAddButton = mPressedTabIndex < 0 && mAddRect.contains(event.getX(), event.getY());
                mPressedCloseButton = mPressedTabIndex >= 0 && isInCloseButton(mPressedTabIndex, event.getX());
                if (mPressedTabIndex < 0 && !mPressedAddButton) {
                    return false;
                }
                if (mPressedTabIndex >= 0 && !mPressedCloseButton) {
                    postDelayed(mLongPressRunnable, mLongPressTimeout);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mPressedTabIndex < 0 && !mPressedAddButton) {
                    return false;
                }
                if (Math.abs(event.getX() - mDownX) > mTouchSlop
                    || Math.abs(event.getY() - mDownY) > mTouchSlop) {
                    cancelPress();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int pressedTab = mPressedTabIndex;
                boolean pressedAdd = mPressedAddButton;
                boolean pressedClose = mPressedCloseButton;
                boolean longPressFired = mLongPressFired;
                cancelPress();
                if (longPressFired) {
                    return true;
                }
                Listener listener = mListener;
                if (pressedTab >= 0) {
                    if (pressedClose) {
                        if (listener != null) {
                            listener.onTabClosed(pressedTab);
                        }
                    } else if (listener != null) {
                        listener.onTabSelected(pressedTab);
                    }
                    return true;
                }
                if (pressedAdd) {
                    if (listener != null) {
                        listener.onNewTabRequested();
                    }
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancelPress();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void cancelPress() {
        removeCallbacks(mLongPressRunnable);
        mPressedTabIndex = -1;
        mPressedAddButton = false;
        mPressedCloseButton = false;
        mLongPressFired = false;
        invalidate();
    }

    private int findTabAt(float x, float y) {
        for (int i = 0; i < mTabRects.size(); i++) {
            if (mTabRects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInCloseButton(int index, float x) {
        if (!mShowCloseButton || index < 0 || index >= mCloseRects.size()) {
            return false;
        }
        RectF closeRect = mCloseRects.get(index);
        // 关闭按钮的点击区域向两侧扩展，避免图标过小导致误点到切换。
        float slop = mCloseIconGap;
        return x >= closeRect.left - slop && x <= closeRect.right + slop;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mLongPressRunnable);
        super.onDetachedFromWindow();
    }
}
