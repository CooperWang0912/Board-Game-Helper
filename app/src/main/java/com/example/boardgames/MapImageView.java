package com.example.boardgames;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.appcompat.widget.AppCompatImageView;

public class MapImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 10f;

    private final Matrix imageMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private final PointF lastTouch = new PointF();
    private boolean isDragging = false;
    private boolean isScaling = false;

    private int viewWidth;
    private int viewHeight;

    public MapImageView(Context context) {
        super(context);
        init(context);
    }

    public MapImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MapImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float currentScale = getCurrentScale();
                float newScale = currentScale * scaleFactor;

                if (newScale < MIN_SCALE) {
                    scaleFactor = MIN_SCALE / currentScale;
                } else if (newScale > MAX_SCALE) {
                    scaleFactor = MAX_SCALE / currentScale;
                }

                imageMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(imageMatrix);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                isScaling = false;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                fitImageToView();
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        fitImageToView();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null && viewWidth > 0 && viewHeight > 0) {
            fitImageToView();
        }
    }

    public void fitImageToView() {
        Drawable drawable = getDrawable();
        if (drawable == null || viewWidth == 0 || viewHeight == 0) return;

        int imgWidth = drawable.getIntrinsicWidth();
        int imgHeight = drawable.getIntrinsicHeight();
        if (imgWidth <= 0 || imgHeight <= 0) return;

        float scaleX = (float) viewWidth / imgWidth;
        float scaleY = (float) viewHeight / imgHeight;
        float scale = Math.min(scaleX, scaleY);

        float dx = (viewWidth - imgWidth * scale) / 2f;
        float dy = (viewHeight - imgHeight * scale) / 2f;

        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        setImageMatrix(imageMatrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                isDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && !isScaling && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    imageMatrix.postTranslate(dx, dy);
                    setImageMatrix(imageMatrix);
                    lastTouch.set(event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                isDragging = false;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() - 1 == 1) {
                    int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                    lastTouch.set(event.getX(remainingIndex), event.getY(remainingIndex));
                    isDragging = true;
                }
                break;
        }

        return true;
    }

    private float getCurrentScale() {
        imageMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }
}
