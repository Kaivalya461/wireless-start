package in.kvapps.wirelessstart.chart;

import android.content.Context;
import android.widget.TextView;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.R;

public class CustomMarkerView extends MarkerView {
    private final TextView txtContent;
    private long baseCutoffTimeMs;
    private String currentActiveFilter = "1H"; // Default configuration matching starting view state

    // 1. Separate formatters for short versus long timeline representations
    private final SimpleDateFormat shortTimeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat longTimeFormat = new SimpleDateFormat("h:mm a d MMM yyyy", Locale.getDefault());

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        txtContent = findViewById(R.id.txt_marker_content);
    }

    public void setBaseCutoffTime(long baseCutoffTimeMs) {
        this.baseCutoffTimeMs = baseCutoffTimeMs;
    }

    // 2. Public modifier configuration setter method to intercept active filter string updates
    public void setActiveFilter(String filter) {
        this.currentActiveFilter = filter;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (Float.isNaN(e.getY())) {
            super.refreshContent(e, highlight);
            return;
        }

        // Rebuild absolute milliseconds context location out from relative chart minutes floats
        long actualTimestampMs = baseCutoffTimeMs + ((long) e.getX() * 60 * 1000);
        Date targetDate = new Date(actualTimestampMs);
        String formattedDateTime;

        // 3. Select appropriate date pattern configuration path based on user time intervals
        if ("1H".equals(currentActiveFilter)) {
            formattedDateTime = shortTimeFormat.format(targetDate);
        } else {
            // For 7D, 1Y, All views: Upper-case the text to output clean formal asset lines like "7 JUL 2026"
            formattedDateTime = longTimeFormat.format(targetDate).toUpperCase(Locale.getDefault());
        }

        // Format layout text string output configuration matrix parameters
        txtContent.setText(String.format(Locale.US, "%.2fV | %s", e.getY(), formattedDateTime));

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF((-(float) getWidth() / 2), -getHeight() - 15);
    }

    // To fix the Market getting cut off when at extreme right side of the chart.
    @Override
    public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
        MPPointF offset = super.getOffsetForDrawingAtPoint(posX, posY);

        // Default behavior: center horizontally, place above the point vertically
        float xOffset = -(getWidth() / 2f);
        float yOffset = -getHeight() - 15f;

        // Check if chartView context is available
        if (getChartView() != null) {
            float chartWidth = getChartView().getWidth();

            // If marker goes off the right edge, shift it entirely to the left of the touch point
            if (posX + xOffset + getWidth() > chartWidth) {
                xOffset = -getWidth();
            }
            // If marker goes off the left edge, pin it to the left side
            else if (posX + xOffset < 0f) {
                xOffset = -posX;
            }
        }

        offset.x = xOffset;
        offset.y = yOffset;
        return offset;
    }
}