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
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        txtContent = findViewById(R.id.txt_marker_content);
    }

    // Dynamic method to update the window offset based on active time buttons (1D, 7D, etc.)
    public void setBaseCutoffTime(long baseCutoffTimeMs) {
        this.baseCutoffTimeMs = baseCutoffTimeMs;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // Skip handling for gap injection markers
        if (Float.isNaN(e.getY())) {
            super.refreshContent(e, highlight);
            return;
        }

        // 1. Rebuild the exact human timestamp from the relative minutes X value
        long actualTimestampMs = baseCutoffTimeMs + ((long) e.getX() * 60 * 1000);
        String formattedTime = timeFormat.format(new Date(actualTimestampMs));

        // 2. Format and show the layout string (e.g., "12.60V | 2:29 PM")
        txtContent.setText(String.format(Locale.US, "%.1fV | %s", e.getY(), formattedTime));

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Center the popup box directly horizontally over the finger point node, lifted upward slightly
        return new MPPointF((-(float) getWidth() / 2), -getHeight() - 15);
    }
}
