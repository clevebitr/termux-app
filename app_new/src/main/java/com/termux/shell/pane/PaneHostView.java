package com.termux.shell.pane;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 窗格树绘制与分割条交互视图。
 *
 * <p>目的：在骨架阶段以几何方式呈现窗格树结构，并提供分割条拖拽能力，用于验证树结构与视图布局的一致性。
 *
 * <p>行为：
 * <ul>
 *     <li>按 {@link PaneSplit#getRatio()} 递归切分可用区域并绘制叶子边框；</li>
 *     <li>叶子无会话时显示空窗格提示文本；当前焦点叶子使用强调色边框；</li>
 *     <li>触摸落在分割条命中范围内时进入拖拽状态，触摸移动按比例更新对应节点。</li>
 * </ul>
 *
 * <p>约束：
 * <ul>
 *     <li>骨架阶段不承载终端视图；终端视图挂载在里程碑 M13 实现；</li>
 *     <li>本视图只记录分割条的几何信息用于命中测试，不持有任何会话状态。</li>
 * </ul>
 */
public final class PaneHostView extends View {

    private static final float DIVIDER_WIDTH_DP = 2f;
    private static final float BORDER_WIDTH_DP = 1f;
    private static final float FOCUSED_BORDER_WIDTH_DP = 2f;
    private static final float HIT_SLOP_DP = 12f;
    private static final float LABEL_TEXT_SIZE_SP = 12f;

    private final Paint mLeafFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActiveDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Map<PaneSplit, SplitGeometry> mGeometry = new HashMap<>();
    private final float mHitSlopPx;

    @Nullable
    private PaneLayoutController mController;
    @Nullable
    private PaneSplit mDraggingSplit;
    private float mDragStartRatio;
    private float mDragStartCoordinate;

    public PaneHostView(@NonNull Context context) {
        this(context, null);
    }

    public PaneHostView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PaneHostView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        mHitSlopPx = HIT_SLOP_DP * density;

        mLeafFillPaint.setStyle(Paint.Style.FILL);
        mLeafFillPaint.setColor(resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceVariant, 0xFF202124));

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(BORDER_WIDTH_DP * density);
        mBorderPaint.setColor(resolveThemeColor(
            com.google.android.material.R.attr.colorOutline, 0xFF5F6368));

        mDividerPaint.setStyle(Paint.Style.FILL);
        mDividerPaint.setColor(resolveThemeColor(
            com.google.android.material.R.attr.colorOutlineVariant, 0xFF3C4043));

        mActiveDividerPaint.setStyle(Paint.Style.FILL);
        mActiveDividerPaint.setColor(resolveThemeColor(
            com.google.android.material.R.attr.colorPrimary, 0xFF8AB4F8));

        mLabelPaint.setAntiAlias(true);
        mLabelPaint.setTextSize(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SIZE_SP, getResources().getDisplayMetrics()));
        mLabelPaint.setColor(resolveThemeColor(
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFE8EAED));
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置窗格树控制器并触发重绘。
     */
    public void setController(@NonNull PaneLayoutController controller) {
        mController = controller;
        mDraggingSplit = null;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        PaneLayoutController controller = mController;
        mGeometry.clear();
        if (controller == null) {
            return;
        }
        drawNode(canvas, controller.getRoot(), controller.getFocusedLeaf(), 0f, 0f, getWidth(), getHeight());
    }

    private void drawNode(@NonNull Canvas canvas, @NonNull PaneNode node, @NonNull PaneLeaf focusedLeaf,
                          float left, float top, float right, float bottom) {
        if (right - left <= 0f || bottom - top <= 0f) {
            return;
        }
        if (node instanceof PaneLeaf) {
            drawLeaf(canvas, (PaneLeaf) node, node == focusedLeaf, left, top, right, bottom);
            return;
        }

        PaneSplit split = (PaneSplit) node;
        RectF extent = new RectF(left, top, right, bottom);
        RectF first;
        RectF second;
        RectF divider;

        float halfDivider = DIVIDER_WIDTH_DP * getResources().getDisplayMetrics().density / 2f;
        if (split.getOrientation() == PaneSplit.ORIENTATION_HORIZONTAL) {
            float x = left + (right - left) * split.getRatio();
            first = new RectF(left, top, x, bottom);
            second = new RectF(x, top, right, bottom);
            divider = new RectF(x - halfDivider, top, x + halfDivider, bottom);
        } else {
            float y = top + (bottom - top) * split.getRatio();
            first = new RectF(left, top, right, y);
            second = new RectF(left, y, right, bottom);
            divider = new RectF(left, y - halfDivider, right, y + halfDivider);
        }

        mGeometry.put(split, new SplitGeometry(extent, divider));

        drawNode(canvas, split.getFirst(), focusedLeaf, first.left, first.top, first.right, first.bottom);
        drawNode(canvas, split.getSecond(), focusedLeaf, second.left, second.top, second.right, second.bottom);

        canvas.drawRect(divider, split == mDraggingSplit ? mActiveDividerPaint : mDividerPaint);
    }

    private void drawLeaf(@NonNull Canvas canvas, @NonNull PaneLeaf leaf, boolean focused,
                          float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, mLeafFillPaint);

        mBorderPaint.setStrokeWidth((focused ? FOCUSED_BORDER_WIDTH_DP : BORDER_WIDTH_DP)
            * getResources().getDisplayMetrics().density);
        canvas.drawRect(left + 0.5f, top + 0.5f, right - 0.5f, bottom - 0.5f, mBorderPaint);

        String label = leaf.hasSessions()
            ? getContext().getString(R.string.shell_header, leaf.getId(), leaf.getTabs().size())
            : getContext().getString(R.string.shell_empty_pane);
        float centerX = (left + right) / 2f;
        float centerY = (top + bottom) / 2f - (mLabelPaint.ascent() + mLabelPaint.descent()) / 2f;
        canvas.drawText(label, centerX, centerY, mLabelPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        PaneLayoutController controller = mController;
        if (controller == null) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                PaneSplit hit = findSplitAt(event.getX(), event.getY());
                if (hit == null) {
                    return false;
                }
                mDraggingSplit = hit;
                mDragStartRatio = hit.getRatio();
                mDragStartCoordinate = hit.getOrientation() == PaneSplit.ORIENTATION_HORIZONTAL
                    ? event.getX() : event.getY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mDraggingSplit == null) {
                    return false;
                }
                SplitGeometry geometry = mGeometry.get(mDraggingSplit);
                if (geometry == null) {
                    return false;
                }
                boolean horizontal = mDraggingSplit.getOrientation() == PaneSplit.ORIENTATION_HORIZONTAL;
                float extentStart = horizontal ? geometry.mExtent.left : geometry.mExtent.top;
                float extentSize = horizontal ? geometry.mExtent.width() : geometry.mExtent.height();
                if (extentSize <= 0f) {
                    return false;
                }
                float coordinate = horizontal ? event.getX() : event.getY();
                float ratio = (coordinate - extentStart) / extentSize;
                if (controller.setRatio(mDraggingSplit, ratio)) {
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mDraggingSplit == null) {
                    return false;
                }
                mDraggingSplit = null;
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Nullable
    private PaneSplit findSplitAt(float x, float y) {
        for (Map.Entry<PaneSplit, SplitGeometry> entry : mGeometry.entrySet()) {
            RectF divider = entry.getValue().mDivider;
            if (x >= divider.left - mHitSlopPx && x <= divider.right + mHitSlopPx
                && y >= divider.top - mHitSlopPx && y <= divider.bottom + mHitSlopPx) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int resolveThemeColor(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return getContext().getColor(value.resourceId);
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return fallback;
    }

    /**
     * 分割节点的几何信息。{@code mExtent} 用于把触摸坐标换算为比例，{@code mDivider} 用于命中测试。
     */
    private static final class SplitGeometry {

        private final RectF mExtent;
        private final RectF mDivider;

        private SplitGeometry(@NonNull RectF extent, @NonNull RectF divider) {
            mExtent = extent;
            mDivider = divider;
        }
    }
}
