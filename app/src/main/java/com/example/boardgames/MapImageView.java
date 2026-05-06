package com.example.boardgames;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MapImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 10f;
    private static final float TAP_THRESHOLD = 15f;
    private static final float POINT_HIT_RADIUS_DP = 30f;
    private static final float POINT_RADIUS_DP = 8f;
    private static final float CHAR_RADIUS_DP = 10f;
    private static final long LONG_PRESS_DELAY_MS = 500;
    private static final float DEFAULT_STROKE_WIDTH_DP = 3f;
    private static final float ERASER_HIT_RADIUS_DP = 20f;
    private static final float MIN_DRAW_DISTANCE_SQ = 25f; // 5px² in image space — coarser for perf

    private final Matrix imageMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    // Reusable arrays — avoids allocation in hot paths (onDraw, touch, hit-test)
    private final float[] touchCoords = new float[2];
    private final float[] drawSrcPt = new float[2];
    private final float[] drawDstPt = new float[2];
    // Reusable visible-rect corners (8 floats = 4 corners x,y)
    private final float[] visCorners = new float[8];
    private final RectF visibleRect = new RectF();

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
    private boolean characterPlacementMode = false;
    private boolean hasUnsavedChanges = false;

    private Paint pointFillPaint;
    private Paint pointStrokePaint;
    private Paint labelPaint;
    private Paint labelOutlinePaint;

    private Paint charLabelPaint;
    private Paint charLabelOutlinePaint;
    private Bitmap avatarBitmap;
    private int avatarBitmapSize;
    private final RectF avatarDestRect = new RectF();
    private final Paint avatarPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private OnPointPlacedListener onPointPlacedListener;
    private OnCharacterPlacedListener onCharacterPlacedListener;
    private OnPointTappedListener onPointTappedListener;

    private float pointRadius;
    private float charRadius;
    private float pointHitRadiusSq;
    private float density;

    // Long-press drag state
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private MapPoint draggingPoint = null;
    private boolean wasLongPress = false;

    // Drawing
    public enum InteractionMode { NAVIGATE, DRAW, ERASE }

    public static class DrawStroke {
        public final List<float[]> points;
        public int color;
        public float strokeWidth;
        Path cachedPath;
        // Bounding box in image-space for frustum culling
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        public DrawStroke(int color, float strokeWidth) {
            this.points = new ArrayList<>();
            this.color = color;
            this.strokeWidth = strokeWidth;
            this.cachedPath = new Path();
        }

        void addPoint(float x, float y) {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (points.isEmpty()) {
                cachedPath.moveTo(x, y);
            } else {
                cachedPath.lineTo(x, y);
            }
            points.add(new float[]{x, y});
        }

        void rebuildPath() {
            cachedPath.reset();
            minX = Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;
            if (points.isEmpty()) return;
            float[] first = points.get(0);
            cachedPath.moveTo(first[0], first[1]);
            minX = first[0]; maxX = first[0];
            minY = first[1]; maxY = first[1];
            for (int i = 1; i < points.size(); i++) {
                float[] pt = points.get(i);
                cachedPath.lineTo(pt[0], pt[1]);
                if (pt[0] < minX) minX = pt[0];
                if (pt[0] > maxX) maxX = pt[0];
                if (pt[1] < minY) minY = pt[1];
                if (pt[1] > maxY) maxY = pt[1];
            }
        }
    }

    private InteractionMode interactionMode = InteractionMode.NAVIGATE;
    private final List<DrawStroke> strokes = new ArrayList<>();
    private DrawStroke currentStroke = null;
    private int drawColor = Color.RED;
    private float drawStrokeWidth;
    private float eraserHitRadiusSq;
    private boolean isDrawing = false;
    private boolean inverseMatrixValid = false;

    private final Paint strokePaint = new Paint();

    public static class MapPoint {
        public float imgX;
        public float imgY;
        public final String label;
        public final boolean isCharacter;

        public MapPoint(float imgX, float imgY, String label) {
            this(imgX, imgY, label, false);
        }

        public MapPoint(float imgX, float imgY, String label, boolean isCharacter) {
            this.imgX = imgX;
            this.imgY = imgY;
            this.label = label;
            this.isCharacter = isCharacter;
        }
    }

    public interface OnPointPlacedListener {
        void onPointPlaced(float imgX, float imgY);
    }

    public interface OnCharacterPlacedListener {
        void onCharacterPlaced(float imgX, float imgY);
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

        density = context.getResources().getDisplayMetrics().density;
        pointRadius = POINT_RADIUS_DP * density;
        charRadius = CHAR_RADIUS_DP * density;
        float pointHitRadius = POINT_HIT_RADIUS_DP * density;
        pointHitRadiusSq = pointHitRadius * pointHitRadius;
        drawStrokeWidth = DEFAULT_STROKE_WIDTH_DP;
        float eraserHitRadius = ERASER_HIT_RADIUS_DP;
        eraserHitRadiusSq = eraserHitRadius * eraserHitRadius;

        // Strokes: no anti-alias for performance — user requested max perf
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        pointFillPaint = new Paint();
        pointFillPaint.setColor(Color.RED);
        pointFillPaint.setStyle(Paint.Style.FILL);

        pointStrokePaint = new Paint();
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

        // Avatar drawn at same fixed screen size as pin markers (pointRadius * 2 diameter).
        avatarBitmapSize = Math.round(pointRadius * 2);
        Drawable avatarDrawable = ContextCompat.getDrawable(context, R.drawable.ic_default_avatar);
        if (avatarDrawable != null) {
            avatarBitmap = Bitmap.createBitmap(avatarBitmapSize, avatarBitmapSize, Bitmap.Config.ARGB_8888);
            Canvas bmpCanvas = new Canvas(avatarBitmap);
            avatarDrawable.setBounds(0, 0, avatarBitmapSize, avatarBitmapSize);
            avatarDrawable.draw(bmpCanvas);
        }

        charLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        charLabelPaint.setColor(0xFFFFD700);
        charLabelPaint.setTextSize(14f * density);
        charLabelPaint.setTextAlign(Paint.Align.CENTER);
        charLabelPaint.setFakeBoldText(true);

        charLabelOutlinePaint = new Paint(charLabelPaint);
        charLabelOutlinePaint.setColor(0xFF0D47A1);
        charLabelOutlinePaint.setStyle(Paint.Style.STROKE);
        charLabelOutlinePaint.setStrokeWidth(3f * density);

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
                if (currentScale <= 0) return true;
                float newScale = currentScale * scaleFactor;

                if (newScale < MIN_SCALE) {
                    scaleFactor = MIN_SCALE / currentScale;
                } else if (newScale > MAX_SCALE) {
                    scaleFactor = MAX_SCALE / currentScale;
                }

                imageMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                invalidateInverseMatrix();
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
                if (interactionMode == InteractionMode.NAVIGATE) {
                    fitImageToView();
                }
                return true;
            }
        });
    }

    private void invalidateInverseMatrix() {
        inverseMatrixValid = false;
    }

    private boolean ensureInverseMatrix() {
        if (!inverseMatrixValid) {
            inverseMatrixValid = imageMatrix.invert(inverseMatrix);
        }
        return inverseMatrixValid;
    }

    /** Compute the visible rectangle in image-space coordinates. */
    private void computeVisibleRect() {
        if (!ensureInverseMatrix()) return;
        visCorners[0] = 0;          visCorners[1] = 0;
        visCorners[2] = viewWidth;   visCorners[3] = 0;
        visCorners[4] = viewWidth;   visCorners[5] = viewHeight;
        visCorners[6] = 0;          visCorners[7] = viewHeight;
        inverseMatrix.mapPoints(visCorners);
        float x0 = visCorners[0], x1 = visCorners[2], x2 = visCorners[4], x3 = visCorners[6];
        float y0 = visCorners[1], y1 = visCorners[3], y2 = visCorners[5], y3 = visCorners[7];
        visibleRect.set(
                Math.min(Math.min(x0, x1), Math.min(x2, x3)),
                Math.min(Math.min(y0, y1), Math.min(y2, y3)),
                Math.max(Math.max(x0, x1), Math.max(x2, x3)),
                Math.max(Math.max(y0, y1), Math.max(y2, y3))
        );
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
        invalidateInverseMatrix();
        setImageMatrix(imageMatrix);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
            drawStrokes(canvas);
            drawPoints(canvas);
        } catch (Exception e) {
            // Prevent rendering crashes from killing the app
        }
    }

    private void drawStrokes(Canvas canvas) {
        if (strokes.isEmpty() && currentStroke == null) return;

        // Compute the visible region in image space for frustum culling
        computeVisibleRect();

        canvas.save();
        canvas.concat(imageMatrix);

        for (int i = 0, n = strokes.size(); i < n; i++) {
            DrawStroke stroke = strokes.get(i);
            if (stroke.cachedPath.isEmpty()) continue;
            // Frustum cull: skip strokes whose bounding box doesn't overlap the visible area
            if (stroke.maxX < visibleRect.left || stroke.minX > visibleRect.right
                    || stroke.maxY < visibleRect.top || stroke.minY > visibleRect.bottom) {
                continue;
            }
            strokePaint.setColor(stroke.color);
            strokePaint.setStrokeWidth(stroke.strokeWidth);
            canvas.drawPath(stroke.cachedPath, strokePaint);
        }

        if (currentStroke != null && !currentStroke.cachedPath.isEmpty()) {
            strokePaint.setColor(currentStroke.color);
            strokePaint.setStrokeWidth(currentStroke.strokeWidth);
            canvas.drawPath(currentStroke.cachedPath, strokePaint);
        }

        canvas.restore();
    }

    private void drawPoints(Canvas canvas) {
        if (points.isEmpty()) return;

        float charIconSize = pointRadius * 2;
        float cullMargin = charIconSize / 2f + 20f * density;

        for (int i = 0, n = points.size(); i < n; i++) {
            MapPoint point = points.get(i);
            drawSrcPt[0] = point.imgX;
            drawSrcPt[1] = point.imgY;
            imageMatrix.mapPoints(drawDstPt, drawSrcPt);

            float screenX = drawDstPt[0];
            float screenY = drawDstPt[1];

            if (screenX < -cullMargin || screenX > viewWidth + cullMargin
                    || screenY < -cullMargin || screenY > viewHeight + cullMargin) {
                continue;
            }

            if (point.isCharacter) {
                drawCharacterMarker(canvas, screenX, screenY, point.label, charIconSize);
            } else {
                drawPinMarker(canvas, screenX, screenY, point.label);
            }
        }
    }

    private void drawPinMarker(Canvas canvas, float screenX, float screenY, String label) {
        canvas.drawCircle(screenX, screenY, pointRadius, pointFillPaint);
        canvas.drawCircle(screenX, screenY, pointRadius, pointStrokePaint);

        if (label != null && !label.isEmpty()) {
            float labelY = screenY - pointRadius - 8f;
            canvas.drawText(label, screenX, labelY, labelOutlinePaint);
            canvas.drawText(label, screenX, labelY, labelPaint);
        }
    }

    private void drawCharacterMarker(Canvas canvas, float screenX, float screenY,
                                     String label, float drawSize) {
        if (avatarBitmap != null) {
            float half = drawSize / 2f;
            avatarDestRect.set(screenX - half, screenY - half,
                    screenX + half, screenY + half);
            canvas.drawBitmap(avatarBitmap, null, avatarDestRect, avatarPaint);
        }

        if (label != null && !label.isEmpty()) {
            float labelY = screenY - (drawSize / 2f) - 8f;
            canvas.drawText(label, screenX, labelY, charLabelOutlinePaint);
            canvas.drawText(label, screenX, labelY, charLabelPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) return false;
        if (interactionMode == InteractionMode.DRAW) {
            return handleDrawTouch(event);
        } else if (interactionMode == InteractionMode.ERASE) {
            return handleEraseTouch(event);
        }
        return handleNavigateTouch(event);
    }

    private boolean handleDrawTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        if (isScaling) {
            if (currentStroke != null) {
                currentStroke = null;
                isDrawing = false;
                invalidate();
            }
            return true;
        }

        if (event.getPointerCount() > 1) {
            if (currentStroke != null) {
                currentStroke = null;
                isDrawing = false;
                invalidate();
            }
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                requestDisallowIntercept(true);
                if (ensureInverseMatrix()) {
                    touchCoords[0] = event.getX();
                    touchCoords[1] = event.getY();
                    inverseMatrix.mapPoints(touchCoords);
                    currentStroke = new DrawStroke(drawColor, drawStrokeWidth);
                    currentStroke.addPoint(touchCoords[0], touchCoords[1]);
                    isDrawing = true;
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDrawing && currentStroke != null && ensureInverseMatrix()) {
                    int histSize = event.getHistorySize();
                    for (int h = 0; h < histSize; h++) {
                        addPointToCurrentStroke(
                                event.getHistoricalX(h),
                                event.getHistoricalY(h));
                    }
                    addPointToCurrentStroke(event.getX(), event.getY());
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isDrawing && currentStroke != null && !currentStroke.points.isEmpty()) {
                    strokes.add(currentStroke);
                    hasUnsavedChanges = true;
                }
                currentStroke = null;
                isDrawing = false;
                requestDisallowIntercept(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                currentStroke = null;
                isDrawing = false;
                requestDisallowIntercept(false);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (currentStroke != null) {
                    currentStroke = null;
                    isDrawing = false;
                    invalidate();
                }
                break;
        }

        return true;
    }

    private void addPointToCurrentStroke(float screenX, float screenY) {
        touchCoords[0] = screenX;
        touchCoords[1] = screenY;
        inverseMatrix.mapPoints(touchCoords);

        List<float[]> pts = currentStroke.points;
        if (!pts.isEmpty()) {
            float[] last = pts.get(pts.size() - 1);
            float dx = touchCoords[0] - last[0];
            float dy = touchCoords[1] - last[1];
            if (dx * dx + dy * dy < MIN_DRAW_DISTANCE_SQ) {
                return;
            }
        }

        currentStroke.addPoint(touchCoords[0], touchCoords[1]);
    }

    private boolean handleEraseTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        if (isScaling || event.getPointerCount() > 1) {
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                requestDisallowIntercept(true);
                if (ensureInverseMatrix()) {
                    eraseStrokesAt(event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (ensureInverseMatrix()) {
                    int histSize = event.getHistorySize();
                    for (int h = 0; h < histSize; h++) {
                        eraseStrokesAt(
                                event.getHistoricalX(h),
                                event.getHistoricalY(h));
                    }
                    eraseStrokesAt(event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestDisallowIntercept(false);
                break;
        }

        return true;
    }

    private void eraseStrokesAt(float screenX, float screenY) {
        touchCoords[0] = screenX;
        touchCoords[1] = screenY;
        inverseMatrix.mapPoints(touchCoords);
        float imgTouchX = touchCoords[0];
        float imgTouchY = touchCoords[1];

        boolean removed = false;
        Iterator<DrawStroke> it = strokes.iterator();
        while (it.hasNext()) {
            DrawStroke stroke = it.next();
            List<float[]> pts = stroke.points;
            for (int i = 0, n = pts.size(); i < n; i++) {
                float[] pt = pts.get(i);
                float dx = imgTouchX - pt[0];
                float dy = imgTouchY - pt[1];
                if (dx * dx + dy * dy <= eraserHitRadiusSq) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
        }

        if (removed) {
            hasUnsavedChanges = true;
            invalidate();
        }
    }

    private boolean handleNavigateTouch(MotionEvent event) {
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
                requestDisallowIntercept(true);

                if (!placementMode && !characterPlacementMode) {
                    MapPoint hitPoint = findPointAt(event.getX(), event.getY());
                    if (hitPoint != null) {
                        longPressRunnable = () -> {
                            draggingPoint = hitPoint;
                            wasLongPress = true;
                            isDragging = false;
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            invalidate();
                        };
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (draggingPoint != null && event.getPointerCount() == 1) {
                    if (ensureInverseMatrix()) {
                        touchCoords[0] = event.getX();
                        touchCoords[1] = event.getY();
                        inverseMatrix.mapPoints(touchCoords);
                        draggingPoint.imgX = touchCoords[0];
                        draggingPoint.imgY = touchCoords[1];
                        hasUnsavedChanges = true;
                        invalidate();
                    }
                } else if (isDragging && !isScaling && event.getPointerCount() == 1) {
                    float moveDistX = event.getX() - downTouch.x;
                    float moveDistY = event.getY() - downTouch.y;
                    if (moveDistX * moveDistX + moveDistY * moveDistY > TAP_THRESHOLD * TAP_THRESHOLD) {
                        cancelPendingLongPress();
                    }

                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    imageMatrix.postTranslate(dx, dy);
                    invalidateInverseMatrix();
                    setImageMatrix(imageMatrix);
                    lastTouch.set(event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_UP:
                cancelPendingLongPress();

                if (draggingPoint != null) {
                    separateFromOthers(draggingPoint);
                    hasUnsavedChanges = true;
                    invalidate();
                    draggingPoint = null;
                } else if (!wasLongPress) {
                    float distX = event.getX() - downTouch.x;
                    float distY = event.getY() - downTouch.y;
                    if (distX * distX + distY * distY < TAP_THRESHOLD * TAP_THRESHOLD
                            && !isScaling) {
                        handleTap(event.getX(), event.getY());
                    }
                }

                isDragging = false;
                wasLongPress = false;
                requestDisallowIntercept(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPress();
                draggingPoint = null;
                isDragging = false;
                wasLongPress = false;
                requestDisallowIntercept(false);
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

    private void requestDisallowIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void handleTap(float screenX, float screenY) {
        if (placementMode || characterPlacementMode) {
            if (ensureInverseMatrix()) {
                touchCoords[0] = screenX;
                touchCoords[1] = screenY;
                inverseMatrix.mapPoints(touchCoords);
                if (characterPlacementMode && onCharacterPlacedListener != null) {
                    onCharacterPlacedListener.onCharacterPlaced(touchCoords[0], touchCoords[1]);
                } else if (placementMode && onPointPlacedListener != null) {
                    onPointPlacedListener.onPointPlaced(touchCoords[0], touchCoords[1]);
                }
            }
        } else {
            MapPoint tapped = findPointAt(screenX, screenY);
            if (tapped != null && onPointTappedListener != null) {
                onPointTappedListener.onPointTapped(tapped);
            }
        }
    }

    private MapPoint findPointAt(float screenX, float screenY) {
        for (int i = 0, n = points.size(); i < n; i++) {
            MapPoint point = points.get(i);
            drawSrcPt[0] = point.imgX;
            drawSrcPt[1] = point.imgY;
            imageMatrix.mapPoints(drawDstPt, drawSrcPt);

            float dx = screenX - drawDstPt[0];
            float dy = screenY - drawDstPt[1];
            if (dx * dx + dy * dy <= pointHitRadiusSq) {
                return point;
            }
        }
        return null;
    }

    // Public API — Points

    public void setPlacementMode(boolean enabled) {
        this.placementMode = enabled;
        if (enabled) this.characterPlacementMode = false;
    }

    public boolean isPlacementMode() {
        return placementMode;
    }

    public void setCharacterPlacementMode(boolean enabled) {
        this.characterPlacementMode = enabled;
        if (enabled) this.placementMode = false;
    }

    public boolean isCharacterPlacementMode() {
        return characterPlacementMode;
    }

    public void addPoint(float imgX, float imgY, String label) {
        MapPoint point = new MapPoint(imgX, imgY, label);
        points.add(point);
        separateFromOthers(point);
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

    // Public API — Drawing

    public void setInteractionMode(InteractionMode mode) {
        this.interactionMode = mode;
        if (currentStroke != null) {
            currentStroke = null;
            isDrawing = false;
            invalidate();
        }
    }

    public InteractionMode getInteractionMode() {
        return interactionMode;
    }

    public void setDrawColor(int color) {
        this.drawColor = color;
    }

    public int getDrawColor() {
        return drawColor;
    }

    public void setDrawStrokeWidth(float width) {
        this.drawStrokeWidth = width;
    }

    public List<DrawStroke> getStrokes() {
        return new ArrayList<>(strokes);
    }

    public void setStrokes(List<DrawStroke> newStrokes) {
        strokes.clear();
        strokes.addAll(newStrokes);
        for (DrawStroke stroke : strokes) {
            stroke.rebuildPath();
        }
        invalidate();
    }

    public void clearStrokes() {
        strokes.clear();
        hasUnsavedChanges = true;
        invalidate();
    }

    public void clearAll() {
        points.clear();
        strokes.clear();
        hasUnsavedChanges = true;
        invalidate();
    }

    public void undoLastStroke() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            hasUnsavedChanges = true;
            invalidate();
        }
    }

    private void separateFromOthers(MapPoint target) {
        Drawable d = getDrawable();
        if (d == null) return;
        int imgW = d.getIntrinsicWidth();
        int imgH = d.getIntrinsicHeight();
        if (imgW <= 0 || imgH <= 0) return;

        float minDist = 0.07f * Math.min(imgW, imgH);
        float minX = 0.02f * imgW;
        float maxX = 0.98f * imgW;
        float minY = 0.02f * imgH;
        float maxY = 0.98f * imgH;

        for (int iter = 0; iter < 10; iter++) {
            boolean moved = false;
            for (int i = 0, n = points.size(); i < n; i++) {
                MapPoint other = points.get(i);
                if (other == target) continue;
                float dx = target.imgX - other.imgX;
                float dy = target.imgY - other.imgY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < minDist) {
                    moved = true;
                    if (dist < 0.001f) {
                        dx = 1f;
                        dy = 1f;
                        dist = (float) Math.sqrt(2f);
                    }
                    float push = minDist - dist;
                    target.imgX += (dx / dist) * push;
                    target.imgY += (dy / dist) * push;
                    target.imgX = Math.max(minX, Math.min(maxX, target.imgX));
                    target.imgY = Math.max(minY, Math.min(maxY, target.imgY));
                }
            }
            if (!moved) break;
        }
    }

    // Public API — General

    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    public void markSaved() {
        hasUnsavedChanges = false;
    }

    public void addCharacter(float imgX, float imgY, String label) {
        MapPoint point = new MapPoint(imgX, imgY, label, true);
        points.add(point);
        separateFromOthers(point);
        hasUnsavedChanges = true;
        invalidate();
    }

    public void setOnPointPlacedListener(OnPointPlacedListener listener) {
        this.onPointPlacedListener = listener;
    }

    public void setOnCharacterPlacedListener(OnCharacterPlacedListener listener) {
        this.onCharacterPlacedListener = listener;
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
        if (currentScale <= 0) return;
        float newScale = currentScale * factor;

        if (newScale < MIN_SCALE) {
            factor = MIN_SCALE / currentScale;
        } else if (newScale > MAX_SCALE) {
            factor = MAX_SCALE / currentScale;
        }

        float cx = viewWidth / 2f;
        float cy = viewHeight / 2f;
        imageMatrix.postScale(factor, factor, cx, cy);
        invalidateInverseMatrix();
        setImageMatrix(imageMatrix);
    }

    private float getCurrentScale() {
        imageMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }
}
