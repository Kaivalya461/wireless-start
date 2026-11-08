package in.kvapps.wirelessstart.wear.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

public class PixelFireView extends View {
    private final Paint paint = new Paint();
    private final Random random = new Random();

    private int[][] firePixels;
    private int rows = 0;
    private int cols = 0;
    private final int cellSize = 12; // Size of each pixel block

    // Classic fire color palette
    private final int[] colorPalette = {
            0xFF000000, // 0: Black / Transparent background
            0xFF1F0700, // 1
            0xFF470B00, // 2
            0xFF750D00, // 3
            0xFFA01100, // 4
            0xFFC02000, // 5
            0xFFD83800, // 6
            0xFFF05000, // 7
            0xFFF87000, // 8
            0xFFFF8800, // 9
            0xFFFF9E14, // 10
            0xFFFFB532, // 11
            0xFFFFCC55, // 12
            0xFFFFE088, // 13
            0xFFFFFFCC, // 14
            0xFFFFFFFF  // 15: Pure White hot
    };

    private boolean isRunning = false;
    private boolean isDyingOut = false;

    private final Runnable fireLoop = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                updateFire();
                invalidate();
                if (isRunning) {
                    postDelayed(this, 50); // ~20 fps for retro pixel feel
                }
            }
        }
    };

    public PixelFireView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false); // Crisp pixel look
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            cols = w / cellSize;
            rows = h / cellSize;
            firePixels = new int[rows][cols];
            initFireSource();
        }
    }

    private void initFireSource() {
        if (firePixels == null) return;

        int centerX = cols / 2;
        int fireWidth = Math.max(6, cols / 3);
        int startX = Math.max(0, centerX - (fireWidth / 2));
        int endX = Math.min(cols, centerX + (fireWidth / 2));

        for (int x = startX; x < endX; x++) {
            firePixels[rows - 1][x] = colorPalette.length - 1;
        }
    }

    private void updateFire() {
        if (firePixels == null || rows == 0 || cols == 0) return;

        boolean allExtinguished = true;

        // Propagate fire upwards and handle cooling
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (y == rows - 1 && isDyingOut) {
                    // When dying out, bottom row also cools down and extinguishes
                    int val = firePixels[y][x];
                    firePixels[y][x] = Math.max(0, val - (1 + random.nextInt(2)));
                } else if (y < rows - 1) {
                    int randomIdx = random.nextInt(3);
                    int srcX = Math.min(Math.max(x + randomIdx - 1, 0), cols - 1);
                    int srcY = Math.min(y + 1, rows - 1);

                    int pixelValue = firePixels[srcY][srcX];
                    int decay = random.nextInt(2);
                    int cooledValue = Math.max(0, pixelValue - decay);

                    firePixels[y][x] = cooledValue;
                }

                if (firePixels[y][x] > 0) {
                    allExtinguished = false;
                }
            }
        }

        // If not dying out, keep bottom center burning hot
        if (!isDyingOut) {
            int centerX = cols / 2;
            int fireWidth = Math.max(6, cols / 3);
            int startX = Math.max(0, centerX - (fireWidth / 2));
            int endX = Math.min(cols, centerX + (fireWidth / 2));

            for (int x = startX; x < endX; x++) {
                firePixels[rows - 1][x] = colorPalette.length - 1 - random.nextInt(3);
            }
        } else if (allExtinguished) {
            // Once all pixels have cooled to black, fully stop the loop
            isRunning = false;
            removeCallbacks(fireLoop);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (firePixels == null) return;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int colorIndex = firePixels[y][x];
                if (colorIndex > 0) {
                    paint.setColor(colorPalette[colorIndex]);
                    paint.setAlpha(178); // ~70% transparency

                    canvas.drawRect(
                            x * cellSize,
                            y * cellSize,
                            (x * cellSize) + cellSize,
                            (y * cellSize) + cellSize,
                            paint
                    );
                }
            }
        }
    }

    public void startFire() {
        isDyingOut = false;
        if (isRunning) return;
        isRunning = true;
        post(fireLoop);
    }

    public void stopFire() {
        // Triggers the burnout sequence instead of cutting instantly
        isDyingOut = true;
    }
}