package in.kvapps.wirelessstart.wear.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class EdgeAnimationView extends View {
    private final Paint paint = new Paint();
    private final RectF rectF = new RectF();
    private float sweepAngle = 0f;
    private boolean isAnimating = true;

    private boolean isClosing = false;
    private int alphaVal = 255;

    // Base colors stored to handle synchronized alpha scaling
    private static final int CORE_COLOR = 0xFFE0F7FF;
    private static final int GLOW_COLOR = 0xFF00B0FF;

    public EdgeAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Force software layer for the view so the neon shadow/glow renders smoothly
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setAntiAlias(true);

        // Initial setup
        paint.setColor(CORE_COLOR);
        paint.setShadowLayer(24f, 0f, 0f, GLOW_COLOR);
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
            alphaVal -= 35; // Slightly faster, clean fade step
            if (alphaVal < 0) alphaVal = 0;

            // Synchronize alpha for both the inner stroke and the outer shadow layer
            // by recalculating the color components with the current alphaVal.
            int currentCoreAlpha = (int) (((alphaVal / 255f) * ((CORE_COLOR >> 24) & 0xFF)));
            int currentGlowAlpha = (int) (((alphaVal / 255f) * ((GLOW_COLOR >> 24) & 0xFF)));

            int syncedCoreColor = (currentCoreAlpha << 24) | (CORE_COLOR & 0x00FFFFFF);
            int syncedGlowColor = (currentGlowAlpha << 24) | (GLOW_COLOR & 0x00FFFFFF);

            paint.setColor(syncedCoreColor);
            // Re-applying shadow layer with the matched alpha forces the Android canvas
            // to render the blur gradient accurately relative to the fading core.
            paint.setShadowLayer(24f, 0f, 0f, syncedGlowColor);

            invalidate();
        } else {
            paint.setAlpha(alphaVal);
        }

        // Draw expanding neon edge circle segment
        canvas.drawArc(rectF, -90, sweepAngle, false, paint);

        if (isClosing) {
            if (alphaVal <= 0) {
                // Stop invalidating once fully transparent
                isClosing = false;
            }
        } else if (isAnimating) {
            sweepAngle += 10f; // Speed of animation progression
            if (sweepAngle > 360f) {
                sweepAngle = 360f;
            }
            invalidate(); // Keep refreshing until full circle
        }
    }

    public void startFadeOut() {
        isAnimating = false;
        isClosing = true;
        invalidate();
    }
}