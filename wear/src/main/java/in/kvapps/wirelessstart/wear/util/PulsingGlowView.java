package in.kvapps.wirelessstart.wear.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
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

    private ValueAnimator pulseAnimator;
    private boolean isGlowActive = false;

    private int coreColorBase;
    private int glowColorBase;

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
        // Outer layer: wide atmospheric haze
        outerGlowPaint.setColor(Color.TRANSPARENT); // Invisible stroke, only shadow renders the bloom
        outerGlowPaint.setShadowLayer(currentShadowRadius * 1.4f, 0f, 0f, currentGlow);

        // Core layer: sharp line with an intense inner halo
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
            // Pass 1: Draw the wide, velvety atmospheric halo underneath
            canvas.drawArc(rectF, -90, 360f, false, outerGlowPaint);

            // Pass 2: Draw the razor-sharp core ring with its tight inner bloom on top
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
        if (!isGlowActive && currentAlphaFactor <= 0f) return;
        isGlowActive = false;

        stopPulseAnimation();
        startFadeOutAnimation();
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

        // Smooth, organic breathing range (16f to 32f)
        ValueAnimator radiusAnimator = ValueAnimator.ofFloat(16f, 32f);
        radiusAnimator.setDuration(2200);
        radiusAnimator.setRepeatMode(ValueAnimator.REVERSE);
        radiusAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radiusAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        radiusAnimator.addUpdateListener(animation -> {
            currentShadowRadius = (float) animation.getAnimatedValue();
            updateColors(1.0f);
            invalidate();
        });

        pulseAnimator = radiusAnimator;
        radiusAnimator.start();
    }

    private void startFadeOutAnimation() {
        ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(currentAlphaFactor, 0f);
        fadeOutAnimator.setDuration(350);
        fadeOutAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        fadeOutAnimator.addUpdateListener(animation -> {
            updateColors((float) animation.getAnimatedValue());
            invalidate();
        });

        fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isGlowActive = false;
                updateColors(0f);
                invalidate();
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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulseAnimation();
    }
}