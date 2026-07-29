package in.kvapps.wirelessstart.chart;

import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.Calendar;
import java.util.Locale;

public class TimeAxisValueFormatter extends ValueFormatter implements IAxisValueFormatter {
    private final long baseCutoffTimeMs;

    public TimeAxisValueFormatter(long baseCutoffTimeMs) {
        this.baseCutoffTimeMs = baseCutoffTimeMs;
    }

    @Override
    public String getFormattedValue(float value, AxisBase axis) {
        // 1. Convert the relative minutes float back into actual timestamp milliseconds
        long actualTimestampMs = baseCutoffTimeMs + ((long) value * 60 * 1000);

        // 2. Format the timestamp into a readable time string
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(actualTimestampMs);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }
}
