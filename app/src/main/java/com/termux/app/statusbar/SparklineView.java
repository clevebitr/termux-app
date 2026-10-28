package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A minimal sparkline chart that draws the history of a value between {@link #setMinValue(float)}
 * and {@link #setMaxValue(float)} as a line with a subtle area fill below it.
 *
 * The view keeps a fixed number of samples in a ring buffer, so that the chart scrolls to the left
 * as new samples are added, without allocating while it is being updated.
 */
public class SparklineView extends View {

    /** The values of the samples, oldest first from {@link #mHead}. */
    private float[] mValues;
    private int mSampleCount;
    private int mHead;
    private int mSize;

    private float mMinValue = 0;
    private float mMaxValue = 100;
    private boolean mAutoScale;

    private final Paint mLinePaint = new Paint();
    private final Paint mFillPaint = new Paint();
    private final Paint mBaselinePaint = new Paint();
    private final Path mPath = new Path();
    private final Path mFillPath = new Path();

    private int mColor = 0xffffffff;

    public SparklineView(@NonNull Context context) {
        this(context, null);
    }

    public SparklineView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        float density = getResources().getDisplayMetrics().density;

        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(Math.max(1f, 1.2f * density));
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setAntiAlias(true);

        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAntiAlias(true);

        mBaselinePaint.setStyle(Paint.Style.STROKE);
        mBaselinePaint.setStrokeWidth(Math.max(1f, 0.5f * density));

        setSampleCount(32);
        setColor(mColor);
    }

    /** Set the number of samples that are kept and drawn, at least 2. */
    public void setSampleCount(int sampleCount) {
        sampleCount = Math.max(2, sampleCount);
        if (sampleCount == mSize) return;

        mValues = new float[sampleCount];
        mSize = sampleCount;
        mSampleCount = 0;
        mHead = 0;
        invalidate();
    }

    /** Set the color of the line and of the area below it. */
    public void setColor(int color) {
        mColor = color;
        mLinePaint.setColor(color);
        mFillPaint.setColor(StatusBarColors.withAlpha(color, 0.22f));
        mBaselinePaint.setColor(StatusBarColors.withAlpha(color, 0.25f));
        invalidate();
    }

    /** Set the range of the values that are drawn. If {@code maxValue} is not greater than
     * {@code minValue}, then the range is computed from the samples. */
    public void setRange(float minValue, float maxValue) {
        mMinValue = minValue;
        mAutoScale = maxValue <= minValue;
        mMaxValue = Math.max(minValue + 1, maxValue);
        invalidate();
    }

    /** Add a sample, which pushes out the oldest sample if the buffer is full. */
    public void addSample(float value) {
        if (mValues == null) return;

        if (mSampleCount < mSize) {
            mValues[(mHead + mSampleCount) % mSize] = value;
            mSampleCount++;
        } else {
            mValues[mHead] = value;
            mHead = (mHead + 1) % mSize;
        }

        invalidate();
    }

    /** Remove all samples. */
    public void clearSamples() {
        mSampleCount = 0;
        mHead = 0;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float baselineY = height - 1;
        if (mSampleCount > 1) canvas.drawLine(0, baselineY, width, baselineY, mBaselinePaint);
        if (mSampleCount < 2) return;

        float padding = Math.max(1f, height * 0.08f);
        float drawableHeight = height - 2 * padding;
        float stepX = width / (float) (mSize - 1);

        float maxValue = mMaxValue;
        if (mAutoScale) {
            float highest = 0;
            for (int i = 0; i < mSampleCount; i++) {
                float value = mValues[(mHead + i) % mSize];
                if (!Float.isNaN(value) && value > highest) highest = value;
            }
            maxValue = highest > 0 ? highest : 1f;
        }
        float range = maxValue - mMinValue;

        mPath.rewind();
        mFillPath.rewind();

        boolean started = false;
        for (int i = 0; i < mSampleCount; i++) {
            float value = mValues[(mHead + i) % mSize];
            if (Float.isNaN(value)) continue;

            float normalized = (value - mMinValue) / range;
            normalized = Math.max(0f, Math.min(1f, normalized));

            // Older samples are drawn on the right if the buffer is not full yet, so that a value
            // that is added to the chart does not jump horizontally when the buffer becomes full.
            int slot = mSize - mSampleCount + i;
            float x = slot * stepX;
            float y = padding + (1f - normalized) * drawableHeight;

            if (!started) {
                mPath.moveTo(x, y);
                mFillPath.moveTo(x, baselineY);
                mFillPath.lineTo(x, y);
                started = true;
            } else {
                mPath.lineTo(x, y);
                mFillPath.lineTo(x, y);
            }
        }

        if (!started) return;

        mFillPath.lineTo(width, baselineY);
        mFillPath.close();

        canvas.drawPath(mFillPath, mFillPaint);
        canvas.drawPath(mPath, mLinePaint);
    }

}
