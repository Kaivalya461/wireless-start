package in.kvapps.wirelessstart.wear.util;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import in.kvapps.wirelessstart.wear.R;

public class EdgeAnimationView extends View {
    private final Paint paint = new Paint();
    private final RectF rectF = new RectF();
    private float sweepAngle = 0f;

    private boolean isClosing = false;
    private int alphaVal = 255;

    // Color definitions (loaded dynamically from resources)
    private final int defaultCoreColor;
    private final int defaultGlowColor;
    private final int successCoreColor;
    private final int successGlowColor;
    private final int timeoutCoreColor;
    private final int timeoutGlowColor;

    // Mutable current colors used during rendering
    private int currentCoreColor;
    private int currentGlowColor;

    private ValueAnimator creepAnimator;
    private boolean isCreeping = false;

    public EdgeAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Load colors safely using Context
        defaultCoreColor = context.getResources().getColor(R.color.default_core_color, context.getTheme());
        defaultGlowColor = context.getResources().getColor(R.color.default_glow_color, context.getTheme());
        successCoreColor = context.getResources().getColor(R.color.success_core_color, context.getTheme());
        successGlowColor = context.getResources().getColor(R.color.success_glow_color, context.getTheme());
        timeoutCoreColor = context.getResources().getColor(R.color.timeout_core_color, context.getTheme());
        timeoutGlowColor = context.getResources().getColor(R.color.timeout_glow_color, context.getTheme());

        // Initialize current colors to default
        currentCoreColor = defaultCoreColor;
        currentGlowColor = defaultGlowColor;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setAntiAlias(true);

        paint.setColor(currentCoreColor);
        paint.setShadowLayer(24f, 0f, 0f, currentGlowColor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int padding = 6;
        rectF.set(padding, padding, w - padding, h - padding);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isClosing) {
            alphaVal -= 35;
            if (alphaVal < 0) alphaVal = 0;

            int coreAlpha = (int) (((alphaVal / 255f) * ((currentCoreColor >> 24) & 0xFF)));
            int glowAlpha = (int) (((alphaVal / 255f) * ((currentGlowColor >> 24) & 0xFF)));

            int syncedCoreColor = (coreAlpha << 24) | (currentCoreColor & 0x00FFFFFF);
            int syncedGlowColor = (glowAlpha << 24) | (currentGlowColor & 0x00FFFFFF);

            paint.setColor(syncedCoreColor);
            paint.setShadowLayer(24f, 0f, 0f, syncedGlowColor);

            if (alphaVal > 0) {
                invalidate();
            } else {
                isClosing = false;
            }
        } else {
            paint.setAlpha(alphaVal);
            paint.setColor(currentCoreColor);
            paint.setShadowLayer(24f, 0f, 0f, currentGlowColor);
        }

        canvas.drawArc(rectF, -90, sweepAngle, false, paint);
    }

    public void startCreepingProgress() {
        isCreeping = true;
        final float targetAngle = 288f; // 80%

        creepAnimator = ValueAnimator.ofFloat(0f, targetAngle);
        creepAnimator.setDuration(4000);
        creepAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(2.0f));

        creepAnimator.addUpdateListener(animation -> {
            if (isCreeping) {
                sweepAngle = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        creepAnimator.start();
    }

    public void completeAndFadeOut() {
        isCreeping = false;
        if (creepAnimator != null) {
            creepAnimator.cancel();
        }

        ValueAnimator completionAnimator = ValueAnimator.ofFloat(sweepAngle, 360f);
        completionAnimator.setDuration(250);
        completionAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        ArgbEvaluator argbEvaluator = new ArgbEvaluator();
        int startCore = currentCoreColor;
        int startGlow = currentGlowColor;

        completionAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            sweepAngle = (float) animation.getAnimatedValue();

            currentCoreColor = (int) argbEvaluator.evaluate(fraction, startCore, successCoreColor);
            currentGlowColor = (int) argbEvaluator.evaluate(fraction, startGlow, successGlowColor);

            float currentShadowRadius = 24f + (8f * fraction);
            paint.setShadowLayer(currentShadowRadius, 0f, 0f, currentGlowColor);

            invalidate();
        });

        completionAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                postDelayed(() -> startFadeOut(), 1000); // 1s GREEN color hold
            }
        });

        completionAnimator.start();
    }

    /**
     * Shifts colors to warning red, holds position, and triggers fade out.
     */
    public void timeoutAndFadeOut() {
        isCreeping = false;
        if (creepAnimator != null) {
            creepAnimator.cancel();
        }

        ValueAnimator timeoutAnimator = ValueAnimator.ofFloat(0f, 1f);
        timeoutAnimator.setDuration(300);

        ArgbEvaluator argbEvaluator = new ArgbEvaluator();
        int startCore = currentCoreColor;
        int startGlow = currentGlowColor;

        timeoutAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            currentCoreColor = (int) argbEvaluator.evaluate(fraction, startCore, timeoutCoreColor);
            currentGlowColor = (int) argbEvaluator.evaluate(fraction, startGlow, timeoutGlowColor);
            invalidate();
        });

        timeoutAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                postDelayed(() -> startFadeOut(), 2000); // 2s RED color hold
            }
        });

        timeoutAnimator.start();
    }

    public void startFadeOut() {
        isClosing = true;
        invalidate();
    }
}