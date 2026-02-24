package com.example.boardgames;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.List;

public class MapImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 10f;
    private static final float TAP_THRESHOLD = 15f;
    private static final float POINT_HIT_RADIUS_DP = 30f;
    private static final float POINT_RADIUS_DP = 8f;
    private static final long LONG_PRESS_DELAY_MS = 500;

    private final Matrix imageMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private final PointF lastTouch = new PointF();
    private final PointF downTouch = new PointF();
    private boolean isDragging = false;
    private boolean isScaling = false;

    private int viewWidth;
    private int viewHeight;

    // Points
    private final List<MapPoint> points = new ArrayList<>();
    private boolean placementMode = false;
    private boolean hasUnsavedChanges = false;

    private Paint pointFillPaint;
    private Paint pointStrokePaint;
    private Paint labelPaint;
    private Paint labelOutlinePaint;

    private OnPointPlacedListener onPointPlacedListener;
    private OnPointTappedListener onPointTappedListener;

    private float pointRadius;
    private float pointHitRadius;

    // Long-press drag state
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private MapPoint draggingPoint = null;
    private boolean wasLongPress = false;

    public static class MapPoint {
        public float imgX;
        public float imgY;
        public final String label;

        public MapPoint(float imgX, float imgY, String label) {
            this.imgX = imgX;
            this.imgY = imgY;
            this.label = label;
        }
    }

    public interface OnPointPlacedListener {
        void onPointPlaced(float imgX, float imgY);
    }

    public interface OnPointTappedListener {
        void onPointTapped(MapPoint point);
    }

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

        float density = context.getResources().getDisplayMetrics().density;
        pointRadius = POINT_RADIUS_DP * density;
        pointHitRadius = POINT_HIT_RADIUS_DP * density;

        pointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointFillPaint.setColor(Color.RED);
        pointFillPaint.setStyle(Paint.Style.FILL);

        pointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointStrokePaint.setColor(Color.WHITE);
        pointStrokePaint.setStyle(Paint.Style.STROKE);
        pointStrokePaint.setStrokeWidth(2f * density);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(14f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        labelOutlinePaint = new Paint(labelPaint);
        labelOutlinePaint.setColor(Color.BLACK);
        labelOutlinePaint.setStyle(Paint.Style.STROKE);
        labelOutlinePaint.setStrokeWidth(3f * density);

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
                invalidate();
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
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawPoints(canvas);
    }

    private void drawPoints(Canvas canvas) {
        if (points.isEmpty()) return;

        float[] srcPoint = new float[2];
        float[] dstPoint = new float[2];

        for (MapPoint point : points) {
            srcPoint[0] = point.imgX;
            srcPoint[1] = point.imgY;
            imageMatrix.mapPoints(dstPoint, srcPoint);

            float screenX = dstPoint[0];
            float screenY = dstPoint[1];

            // Draw circle
            canvas.drawCircle(screenX, screenY, pointRadius, pointFillPaint);
            canvas.drawCircle(screenX, screenY, pointRadius, pointStrokePaint);

            // Draw label above the point
            if (point.label != null && !point.label.isEmpty()) {
                float labelY = screenY - pointRadius - 8f;
                canvas.drawText(point.label, screenX, labelY, labelOutlinePaint);
                canvas.drawText(point.label, screenX, labelY, labelPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Don't let scale/gesture detectors interfere while dragging a point
        if (draggingPoint == null) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                downTouch.set(event.getX(), event.getY());
                isDragging = true;
                wasLongPress = false;
                getParent().requestDisallowInterceptTouchEvent(true);

                // Start long-press detection if over a point and not in placement mode
                if (!placementMode) {
                    MapPoint hitPoint = findPointAt(event.getX(), event.getY());
                    if (hitPoint != null) {
                        longPressRunnable = () -> {
                            draggingPoint = hitPoint;
                            wasLongPress = true;
                            isDragging = false; // stop map panning
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            invalidate();
                        };
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (draggingPoint != null && event.getPointerCount() == 1) {
                    // Dragging a point: update its image-space coordinates
                    if (imageMatrix.invert(inverseMatrix)) {
                        float[] pts = {event.getX(), event.getY()};
                        inverseMatrix.mapPoints(pts);
                        draggingPoint.imgX = pts[0];
                        draggingPoint.imgY = pts[1];
                        hasUnsavedChanges = true;
                        invalidate();
                    }
                } else if (isDragging && !isScaling && event.getPointerCount() == 1) {
                    // Cancel long-press if finger moved too far before it fired
                    float moveDistX = event.getX() - downTouch.x;
                    float moveDistY = event.getY() - downTouch.y;
                    if (moveDistX * moveDistX + moveDistY * moveDistY > TAP_THRESHOLD * TAP_THRESHOLD) {
                        cancelPendingLongPress();
                    }

                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    imageMatrix.postTranslate(dx, dy);
                    setImageMatrix(imageMatrix);
                    invalidate();
                    lastTouch.set(event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_UP:
                cancelPendingLongPress();

                if (draggingPoint != null) {
                    draggingPoint = null;
                } else {
                    isDragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);

                    // Check if this was a tap (not a drag or long-press)
                    if (!wasLongPress) {
                        float distX = event.getX() - downTouch.x;
                        float distY = event.getY() - downTouch.y;
                        float dist = (float) Math.sqrt(distX * distX + distY * distY);

                        if (dist < TAP_THRESHOLD && !isScaling) {
                            handleTap(event.getX(), event.getY());
                        }
                    }
                }

                isDragging = false;
                wasLongPress = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPress();
                draggingPoint = null;
                isDragging = false;
                wasLongPress = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                cancelPendingLongPress();
                draggingPoint = null;
                isDragging = false;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (draggingPoint == null && event.getPointerCount() - 1 == 1) {
                    int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                    lastTouch.set(event.getX(remainingIndex), event.getY(remainingIndex));
                    isDragging = true;
                }
                break;
        }

        return true;
    }

    private void cancelPendingLongPress() {
        if (longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private void handleTap(float screenX, float screenY) {
        if (placementMode) {
            // Convert screen coordinates to image coordinates
            if (imageMatrix.invert(inverseMatrix)) {
                float[] pts = {screenX, screenY};
                inverseMatrix.mapPoints(pts);
                if (onPointPlacedListener != null) {
                    onPointPlacedListener.onPointPlaced(pts[0], pts[1]);
                }
            }
        } else {
            // Check if a point was tapped
            MapPoint tapped = findPointAt(screenX, screenY);
            if (tapped != null && onPointTappedListener != null) {
                onPointTappedListener.onPointTapped(tapped);
            }
        }
    }

    private MapPoint findPointAt(float screenX, float screenY) {
        float[] srcPoint = new float[2];
        float[] dstPoint = new float[2];

        for (MapPoint point : points) {
            srcPoint[0] = point.imgX;
            srcPoint[1] = point.imgY;
            imageMatrix.mapPoints(dstPoint, srcPoint);

            float dx = screenX - dstPoint[0];
            float dy = screenY - dstPoint[1];
            if (dx * dx + dy * dy <= pointHitRadius * pointHitRadius) {
                return point;
            }
        }
        return null;
    }

    // Public API

    public void setPlacementMode(boolean enabled) {
        this.placementMode = enabled;
    }

    public boolean isPlacementMode() {
        return placementMode;
    }

    public void addPoint(float imgX, float imgY, String label) {
        points.add(new MapPoint(imgX, imgY, label));
        hasUnsavedChanges = true;
        invalidate();
    }

    public void removePoint(MapPoint point) {
        points.remove(point);
        hasUnsavedChanges = true;
        invalidate();
    }

    public List<MapPoint> getPoints() {
        return new ArrayList<>(points);
    }

    public void setPoints(List<MapPoint> newPoints) {
        points.clear();
        points.addAll(newPoints);
        hasUnsavedChanges = false;
        invalidate();
    }

    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    public void markSaved() {
        hasUnsavedChanges = false;
    }

    public void setOnPointPlacedListener(OnPointPlacedListener listener) {
        this.onPointPlacedListener = listener;
    }

    public void setOnPointTappedListener(OnPointTappedListener listener) {
        this.onPointTappedListener = listener;
    }

    public void zoomIn() {
        zoomBy(1.25f);
    }

    public void zoomOut() {
        zoomBy(0.8f);
    }

    private void zoomBy(float factor) {
        float currentScale = getCurrentScale();
        float newScale = currentScale * factor;

        if (newScale < MIN_SCALE) {
            factor = MIN_SCALE / currentScale;
        } else if (newScale > MAX_SCALE) {
            factor = MAX_SCALE / currentScale;
        }

        float cx = viewWidth / 2f;
        float cy = viewHeight / 2f;
        imageMatrix.postScale(factor, factor, cx, cy);
        setImageMatrix(imageMatrix);
        invalidate();
    }

    private float getCurrentScale() {
        imageMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }
}
