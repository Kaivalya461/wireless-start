package in.kvapps.wirelessstart.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;

import in.kvapps.wirelessstart.R;

public class GlowCardView extends MaterialCardView {

    private final Paint glowPaint = new Paint();
    private final RectF rectF = new RectF();
    private ValueAnimator pulseAnimator;
    private boolean isGlowActive = false;

    private int coreColorBase;
    private int glowColorBase;
    private float currentShadowRadius = 20f;
    private float currentAlphaFactor = 0f; // Ranges from 0.0f (invisible) to 1.0f (fully visible)

    public GlowCardView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public GlowCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GlowCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        coreColorBase = ContextCompat.getColor(context, R.color.default_core_color);
        glowColorBase = ContextCompat.getColor(context, R.color.default_glow_color);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(8f);
        glowPaint.setAntiAlias(true);

        // Initialize with 0 alpha (hidden)
        updateColors(0f);
    }

    private void updateColors(float alphaFactor) {
        currentAlphaFactor = alphaFactor;

        int alphaCore = (int) (Color.alpha(coreColorBase) * alphaFactor);
        int alphaGlow = (int) (Color.alpha(glowColorBase) * alphaFactor);

        int currentCore = ColorUtils.setAlphaComponent(coreColorBase, alphaCore);
        int currentGlow = ColorUtils.setAlphaComponent(glowColorBase, alphaGlow);

        glowPaint.setColor(currentCore);
        glowPaint.setShadowLayer(currentShadowRadius, 0f, 0f, currentGlow);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = glowPaint.getStrokeWidth() / 2f;
        rectF.set(padding, padding, w - padding, h - padding);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (isGlowActive || currentAlphaFactor > 0f) {
            float cornerRadius = getRadius();
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, glowPaint);
        }
    }

    public void setGlowActive(boolean active) {
        if (isGlowActive == active) return;
        isGlowActive = active;

        stopPulseAnimation();

        if (active) {
            startFadeInAndPulseAnimation();
        } else {
            startFadeOutAnimation();
        }
    }

    private void startFadeInAndPulseAnimation() {
        // 1. Fade-in animator (0.0 -> 1.0 alpha over 2200ms)
        ValueAnimator fadeInAnimator = ValueAnimator.ofFloat(currentAlphaFactor, 1f);
        fadeInAnimator.setDuration(2200);
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

        // 2. Continuous pulse animator (radius oscillations between 12f and 28f)
        ValueAnimator radiusAnimator = ValueAnimator.ofFloat(12f, 28f);
        radiusAnimator.setDuration(2600);
        radiusAnimator.setRepeatMode(ValueAnimator.REVERSE);
        radiusAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radiusAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        radiusAnimator.addUpdateListener(animation -> {
            currentShadowRadius = (float) animation.getAnimatedValue();
            updateColors(1.0f); // Maintain full opacity during pulse
            invalidate();
        });

        pulseAnimator = radiusAnimator;
        radiusAnimator.start();
    }

    private void startFadeOutAnimation() {
        // Fade-out animator (currentAlphaFactor -> 0.0 alpha over 350ms)
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