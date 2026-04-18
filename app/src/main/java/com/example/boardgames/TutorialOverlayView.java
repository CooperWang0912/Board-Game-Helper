package com.example.boardgames;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class TutorialOverlayView extends FrameLayout {

    private static final int SCRIM_COLOR = 0xCC000000;
    private static final int SPOTLIGHT_PADDING = 12; // dp
    private static final int SPOTLIGHT_RADIUS = 16; // dp
    private static final long FADE_DURATION = 250;

    private final Paint scrimPaint = new Paint();
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF spotlightRect = new RectF();
    private Bitmap scrimBitmap;
    private Canvas scrimCanvas;

    private MaterialCardView tooltipCard;
    private TextView textMessage;
    private TextView textStepCounter;

    private final String activityName;
    private final TutorialManager manager;
    private View currentTarget;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;

    public TutorialOverlayView(Context context, String activityName) {
        super(context);
        this.activityName = activityName;
        this.manager = TutorialManager.getInstance(context);

        setWillNotDraw(false);

        scrimPaint.setColor(SCRIM_COLOR);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        buildTooltip();
    }

    private void buildTooltip() {
        Context ctx = getContext();
        int dp8 = dpToPx(8);
        int dp12 = dpToPx(12);
        int dp16 = dpToPx(16);
        int dp20 = dpToPx(20);

        tooltipCard = new MaterialCardView(ctx);
        tooltipCard.setRadius(dpToPx(16));
        tooltipCard.setCardElevation(dpToPx(8));
        tooltipCard.setCardBackgroundColor(0xFFFFFFFF);

        LinearLayout cardContent = new LinearLayout(ctx);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(dp20, dp16, dp20, dp16);

        textMessage = new TextView(ctx);
        textMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textMessage.setTextColor(0xFF212121);
        textMessage.setLineSpacing(0, 1.3f);
        cardContent.addView(textMessage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        textStepCounter = new TextView(ctx);
        textStepCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textStepCounter.setTextColor(0xFF757575);
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        counterParams.topMargin = dp8;
        textStepCounter.setLayoutParams(counterParams);
        cardContent.addView(textStepCounter);

        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowParams.topMargin = dp12;
        buttonRow.setLayoutParams(btnRowParams);

        MaterialButton btnSkip = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.borderlessButtonStyle);
        btnSkip.setText(R.string.tutorial_skip);
        btnSkip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnSkip.setOnClickListener(v -> skipTutorial());

        MaterialButton btnNext = new MaterialButton(ctx);
        btnNext.setText(R.string.tutorial_next);
        btnNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nextParams.setMarginStart(dp8);
        btnNext.setLayoutParams(nextParams);
        btnNext.setOnClickListener(v -> advanceStep());

        buttonRow.addView(btnSkip);
        buttonRow.addView(btnNext);
        cardContent.addView(buttonRow);

        tooltipCard.addView(cardContent);

        FrameLayout.LayoutParams tooltipParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        int margin = dpToPx(24);
        tooltipParams.setMargins(margin, margin, margin, margin);
        tooltipCard.setLayoutParams(tooltipParams);
        addView(tooltipCard);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Let the tooltip card handle its own touches (Next / Skip buttons)
        Rect tooltipHit = new Rect();
        tooltipCard.getHitRect(tooltipHit);
        if (tooltipHit.contains((int) ev.getX(), (int) ev.getY())) {
            return super.dispatchTouchEvent(ev);
        }

        // If touch is inside the spotlight, pass it through to the view below
        if (!spotlightRect.isEmpty() && spotlightRect.contains(ev.getX(), ev.getY())) {
            return false;
        }

        // Touch is on the scrim — consume it so dimmed areas can't be tapped
        return true;
    }

    public void showCurrentStep() {
        if (!manager.isActive()) {
            removeSelf();
            return;
        }

        TutorialManager.TutorialStep step = manager.getCurrentStep(activityName);
        if (step == null) {
            removeSelf();
            return;
        }

        List<TutorialManager.TutorialStep> steps = manager.getSteps(activityName);
        int currentIndex = manager.getCurrentStepIndex(activityName);

        textMessage.setText(step.messageResId);
        textStepCounter.setText(getContext().getString(R.string.tutorial_step_counter,
                currentIndex + 1, steps.size()));

        // Run autofill action if defined
        if (step.autofillAction != null) {
            step.autofillAction.run();
        }

        // Find the target view and position the spotlight
        Activity activity = (Activity) getContext();
        View target = activity.findViewById(step.targetViewId);
        if (target != null && target.getWidth() > 0) {
            currentTarget = target;
            // Scroll the target into view if inside a ScrollView
            scrollTargetIntoView(target);

            // Post to allow scroll to settle before measuring positions
            post(() -> positionSpotlightOn(target));
        } else {
            currentTarget = null;
            spotlightRect.setEmpty();
            // Position tooltip in center
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tooltipCard.getLayoutParams();
            params.gravity = Gravity.CENTER;
            params.topMargin = dpToPx(24);
            tooltipCard.setLayoutParams(params);
            invalidate();
        }
    }

    private void scrollTargetIntoView(View target) {
        View current = target;
        while (current.getParent() instanceof View) {
            ViewGroup parent = (ViewGroup) current.getParent();
            if (parent instanceof ScrollView) {
                // Calculate target position relative to the ScrollView
                int targetTop = 0;
                View child = target;
                while (child != parent) {
                    targetTop += child.getTop();
                    child = (View) child.getParent();
                }
                int scrollY = ((ScrollView) parent).getScrollY();
                int parentHeight = parent.getHeight();
                // Scroll if the target is not fully visible
                if (targetTop < scrollY || targetTop + target.getHeight() > scrollY + parentHeight) {
                    int scrollTo = Math.max(0, targetTop - parentHeight / 4);
                    ((ScrollView) parent).smoothScrollTo(0, scrollTo);
                }
                return;
            }
            current = parent;
        }
    }

    private void positionSpotlightOn(View target) {
        Rect targetRect = new Rect();
        target.getGlobalVisibleRect(targetRect);

        // Adjust for status bar offset
        Rect myRect = new Rect();
        getGlobalVisibleRect(myRect);
        int offsetY = myRect.top;
        targetRect.offset(0, -offsetY);

        int padding = dpToPx(SPOTLIGHT_PADDING);
        spotlightRect.set(
                targetRect.left - padding,
                targetRect.top - padding,
                targetRect.right + padding,
                targetRect.bottom + padding);

        positionTooltip(targetRect);
        invalidate();
    }

    private void positionTooltip(Rect targetRect) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int margin = dpToPx(24);
        int gap = dpToPx(8);

        // Measure actual tooltip height instead of using a fixed estimate
        int availableWidth = getWidth() - 2 * margin;
        if (availableWidth <= 0) {
            availableWidth = getResources().getDisplayMetrics().widthPixels - 2 * margin;
        }
        tooltipCard.measure(
                View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int tooltipHeight = tooltipCard.getMeasuredHeight();

        int spotBottom = targetRect.bottom + dpToPx(SPOTLIGHT_PADDING);
        int spotTop = targetRect.top - dpToPx(SPOTLIGHT_PADDING);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tooltipCard.getLayoutParams();
        params.setMargins(margin, 0, margin, 0);
        params.bottomMargin = 0;

        if (spotBottom + gap + tooltipHeight < screenHeight - margin) {
            // Place below the spotlight — preferred when there's room
            params.gravity = Gravity.TOP;
            params.topMargin = spotBottom + gap;
        } else if (spotTop - gap - tooltipHeight > margin) {
            // Place above the spotlight
            params.gravity = Gravity.TOP;
            params.topMargin = spotTop - gap - tooltipHeight;
        } else {
            // Neither fits cleanly — anchor to bottom of screen
            params.gravity = Gravity.BOTTOM;
            params.topMargin = 0;
            params.bottomMargin = margin;
        }

        tooltipCard.setLayoutParams(params);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        if (scrimBitmap == null || scrimBitmap.getWidth() != w || scrimBitmap.getHeight() != h) {
            if (scrimBitmap != null) scrimBitmap.recycle();
            scrimBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            scrimCanvas = new Canvas(scrimBitmap);
        }

        // Draw scrim
        scrimBitmap.eraseColor(Color.TRANSPARENT);
        scrimCanvas.drawRect(0, 0, w, h, scrimPaint);

        // Punch spotlight hole
        if (!spotlightRect.isEmpty()) {
            float radius = dpToPx(SPOTLIGHT_RADIUS);
            scrimCanvas.drawRoundRect(spotlightRect, radius, radius, clearPaint);
        }

        canvas.drawBitmap(scrimBitmap, 0, 0, null);
    }

    private void advanceStep() {
        boolean hasMore = manager.advanceStep(activityName);
        if (hasMore) {
            fadeTransition(this::showCurrentStep);
        } else if ("MainActivity".equals(activityName)) {
            // All home screen steps done — tutorial is fully complete
            manager.stop();
            fadeOut(() -> {
                removeSelf();
                Toast.makeText(getContext(), R.string.tutorial_complete, Toast.LENGTH_SHORT).show();
            });
        } else {
            // Sub-activity steps done — prompt user to go back and continue
            fadeOut(() -> {
                removeSelf();
                Toast.makeText(getContext(), R.string.tutorial_go_back, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void skipTutorial() {
        manager.stop();
        fadeOut(() -> {
            removeSelf();
            Toast.makeText(getContext(), R.string.tutorial_skipped, Toast.LENGTH_SHORT).show();
        });
    }

    private void fadeTransition(Runnable after) {
        animate().alpha(0.5f).setDuration(FADE_DURATION / 2)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        after.run();
                        animate().alpha(1f).setDuration(FADE_DURATION / 2)
                                .setListener(null).start();
                    }
                }).start();
    }

    private void fadeOut(Runnable after) {
        animate().alpha(0f).setDuration(FADE_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        after.run();
                    }
                }).start();
    }

    public void fadeIn() {
        setAlpha(0f);
        animate().alpha(1f).setDuration(FADE_DURATION).setListener(null).start();
    }

    private void removeSelf() {
        detachLayoutListener();
        currentTarget = null;
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    private void attachLayoutListener(ViewGroup root) {
        layoutListener = () -> {
            if (currentTarget != null && currentTarget.getWidth() > 0 && currentTarget.isShown()) {
                Rect targetRect = new Rect();
                currentTarget.getGlobalVisibleRect(targetRect);
                Rect myRect = new Rect();
                getGlobalVisibleRect(myRect);
                targetRect.offset(0, -myRect.top);

                int padding = dpToPx(SPOTLIGHT_PADDING);
                RectF newSpotlight = new RectF(
                        targetRect.left - padding,
                        targetRect.top - padding,
                        targetRect.right + padding,
                        targetRect.bottom + padding);

                // Only reposition if the target actually moved
                if (!newSpotlight.equals(spotlightRect)) {
                    positionSpotlightOn(currentTarget);
                }
            }
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    private void detachLayoutListener() {
        if (layoutListener != null) {
            getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
            layoutListener = null;
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    /**
     * Attaches a TutorialOverlayView to the activity's content view, shows the current step,
     * and fades in. Returns the overlay (or null if the tutorial is not active or has no steps).
     */
    public static TutorialOverlayView attach(Activity activity, String activityName) {
        TutorialManager mgr = TutorialManager.getInstance(activity);
        if (!mgr.isActive()) return null;
        if (mgr.getCurrentStep(activityName) == null) return null;

        ViewGroup content = activity.findViewById(android.R.id.content);
        // Remove any existing overlay first
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            if (content.getChildAt(i) instanceof TutorialOverlayView) {
                content.removeViewAt(i);
            }
        }

        TutorialOverlayView overlay = new TutorialOverlayView(activity, activityName);
        content.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Listen for layout changes so spotlight repositions when views move
        overlay.attachLayoutListener(content);

        // Post to ensure views are laid out before measuring spotlight positions
        overlay.post(() -> {
            overlay.showCurrentStep();
            overlay.fadeIn();
        });
        return overlay;
    }
}
