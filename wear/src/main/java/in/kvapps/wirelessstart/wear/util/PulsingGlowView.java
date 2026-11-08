package in.kvapps.wirelessstart.wear.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import in.kvapps.wirelessstart.wear.R;

public class PulsingGlowView extends View {

    private final Paint outerGlowPaint = new Paint();
    private final Paint corePaint = new Paint();
    private final RectF rectF = new RectF();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ValueAnimator pulseAnimator;
    private boolean isGlowActive = false;

    private int coreColorBase;
    private int glowColorBase;
    private int coreColorSuccess;
    private int glowColorSuccess;

    // Rich breathing parameters
    private float currentShadowRadius = 24f;
    private float currentAlphaFactor = 0f;

    public PulsingGlowView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PulsingGlowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PulsingGlowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Essential for smooth hardware/software shadow layers
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        coreColorBase = ContextCompat.getColor(context, R.color.pulse_core_color);
        glowColorBase = ContextCompat.getColor(context, R.color.pulse_glow_color);
        coreColorSuccess = ContextCompat.getColor(context, R.color.success_core_color);
        glowColorSuccess = ContextCompat.getColor(context, R.color.success_glow_color);

        // 1. Outer ambient bloom paint (wide, ultra-soft diffusion)
        outerGlowPaint.setStyle(Paint.Style.STROKE);
        outerGlowPaint.setStrokeWidth(10f);
        outerGlowPaint.setAntiAlias(true);

        // 2. Razor-sharp core paint (sits directly on top for crisp definition)
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(6f);
        corePaint.setAntiAlias(true);

        updateColors(0f);
    }

    private void updateColors(float alphaFactor) {
        currentAlphaFactor = alphaFactor;

        int alphaCore = (int) (Color.alpha(coreColorBase) * alphaFactor);
        int alphaGlow = (int) (Color.alpha(glowColorBase) * alphaFactor * 0.75f); // Richer, balanced outer diffusion

        int currentCore = ColorUtils.setAlphaComponent(coreColorBase, alphaCore);
        int currentGlow = ColorUtils.setAlphaComponent(glowColorBase, alphaGlow);

        // Set up the rich dual-shadow volumetric stack
        outerGlowPaint.setColor(Color.TRANSPARENT);
        outerGlowPaint.setShadowLayer(currentShadowRadius * 1.4f, 0f, 0f, currentGlow);

        corePaint.setColor(currentCore);
        corePaint.setShadowLayer(currentShadowRadius * 0.5f, 0f, 0f, currentGlow);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = corePaint.getStrokeWidth() / 2f;
        rectF.set(padding, padding, w - padding, h - padding);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isGlowActive || currentAlphaFactor > 0f) {
            canvas.drawArc(rectF, -90, 360f, false, outerGlowPaint);
            canvas.drawArc(rectF, -90, 360f, false, corePaint);
        }
    }

    public void startPulsing() {
        if (isGlowActive) return;
        isGlowActive = true;

        stopPulseAnimation();
        startFadeInAndPulseAnimation();
    }

    public void startFadeOut() {
        startFadeOut(false, null);
    }

    public void startFadeOut(boolean isSuccess) {
        startFadeOut(isSuccess, null);
    }

    public void startFadeOut(boolean isSuccess, @Nullable Runnable onFinished) {
        if (!isGlowActive && currentAlphaFactor <= 0f) {
            if (onFinished != null) onFinished.run();
            return;
        }
        isGlowActive = false;

        stopPulseAnimation();

        if (isSuccess) {
            // 1. Smoothly transition colors to success green over 300ms
            animateToSuccessColors(() -> {
                // 2. Hold the green success state for 4 seconds
                mainHandler.postDelayed(() -> {
                    // 3. Fade out after the 4-second hold
                    startFadeOutAnimation(onFinished);
                }, 4000);
            });
        } else {
            // Immediate fade out for failure/timeout
            startFadeOutAnimation(onFinished);
        }
    }

    private void animateToSuccessColors(Runnable onComplete) {
        final int startCore = coreColorBase;
        final int startGlow = glowColorBase;

        ValueAnimator colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(300);
        colorAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        ArgbEvaluator argbEvaluator = new ArgbEvaluator();

        colorAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            coreColorBase = (Integer) argbEvaluator.evaluate(fraction, startCore, coreColorSuccess);
            glowColorBase = (Integer) argbEvaluator.evaluate(fraction, startGlow, glowColorSuccess);
            updateColors(1.0f);
            invalidate();
        });

        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });

        pulseAnimator = colorAnimator;
        colorAnimator.start();
    }

    private void startFadeInAndPulseAnimation() {
        ValueAnimator fadeInAnimator = ValueAnimator.ofFloat(currentAlphaFactor, 1f);
        fadeInAnimator.setDuration(1200);
        fadeInAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        fadeInAnimator.addUpdateListener(animation -> {
            updateColors((float) animation.getAnimatedValue());
            invalidate();
        });

        fadeInAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isGlowActive) {
                    startInfinitePulseAnimation();
                }
            }
        });

        pulseAnimator = fadeInAnimator;
        fadeInAnimator.start();
    }

    private void startInfinitePulseAnimation() {
        if (!isGlowActive) return;

        // By starting from 1f down to 0f (or min alpha) via REVERSE,
        // the first frame seamlessly continues right from the 1.0f peak of the fade-in.
        ValueAnimator pulseAnimatorSet = ValueAnimator.ofFloat(1f, 0.3f);
        pulseAnimatorSet.setDuration(1100); // Half cycle duration (total breath period = 2200ms)
        pulseAnimatorSet.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimatorSet.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());

        pulseAnimatorSet.addUpdateListener(animation -> {
            float alphaFactor = (float) animation.getAnimatedValue();

            // Map alpha factor proportionally to shadow radius so size and opacity match
            // When alpha is 1.0f, radius is max (32f). When alpha is 0.3f, radius is min (16f).
            float fraction = (alphaFactor - 0.3f) / 0.7f;
            currentShadowRadius = 16f + (fraction * 16f);

            updateColors(alphaFactor);
            invalidate();
        });

        pulseAnimator = pulseAnimatorSet;
        pulseAnimatorSet.start();
    }

    private void startFadeOutAnimation(@Nullable Runnable onFinished) {
        Log.i("PulsingGlowView", "Starting fade out animation");

        ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(currentAlphaFactor, 0f);
        fadeOutAnimator.setDuration(350);
        fadeOutAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        fadeOutAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            updateColors(alpha);
            invalidate();
        });

        fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isGlowActive = false;
                updateColors(0f);
                invalidate();
                if (onFinished != null) {
                    onFinished.run();
                }
            }
        });

        pulseAnimator = fadeOutAnimator;
        fadeOutAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulseAnimation();
    }
}