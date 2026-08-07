package in.kvapps.wirelessstart;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.core.view.ViewCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

import in.kvapps.wirelessstart.chart.CustomMarkerView;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.model.VoltageEntry;

public class VoltageHistoryActivity extends Activity {
    private static final String DEFAULT_4H_VIEW = "4H";
    private static final long INTERVAL_1_MIN = 60 * 1000;
    private static final long INTERVAL_10_MIN = 10L * 60 * 1000;
    private static final long INTERVAL_1_HOUR = 60L * 60 * 1000;
    private LineChart voltageChart;
    private CustomMarkerView markerView;
    private VoltageDbHelper dbHelper;
    private Button btn4H, btn2D, btn1M, btn1Y; // Note: XML button IDs can remain or change depending on your layout, using btn4H, btn2D, btn1M, btnAll equivalents
    private String currentFilter = DEFAULT_4H_VIEW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voltage_history);

        dbHelper = new VoltageDbHelper(this);

        // Bind Views
        voltageChart = findViewById(R.id.voltage_trend_chart);
        btn4H = findViewById(R.id.btn_filter_4h);
//        btn2D = findViewById(R.id.btn_filter_2d);
//        btn1M = findViewById(R.id.btn_filter_1m);
//        btn1Y = findViewById(R.id.btn_filter_all); // Mapping the last filter button to "All"

        findViewById(R.id.btn_back).setOnClickListener(v -> finish()); // Back Button

        configureChartStyling();
        setupChartFilterListeners();
        updateChartData(); // Initialize loading data
    }

    private void configureChartStyling() {
        voltageChart.getDescription().setEnabled(false);
        voltageChart.setDrawGridBackground(false);
        voltageChart.getLegend().setEnabled(false);

        // FIXED: Added 's' to setDrawBorders
        voltageChart.setDrawBorders(true);
        voltageChart.setBorderColor(Color.parseColor("#333333"));
        voltageChart.setBorderWidth(1f);

        // DISABLE ALL ZOOMING
        voltageChart.setScaleXEnabled(false);
        voltageChart.setScaleYEnabled(false);
        voltageChart.setPinchZoom(false);
        voltageChart.setDoubleTapToZoomEnabled(false);

        // Y-Axis Grid Rules Styling
        voltageChart.getAxisLeft().setTextColor(Color.parseColor("#CCCCCC"));
        voltageChart.getAxisLeft().setGridColor(Color.parseColor("#333333"));
        voltageChart.getAxisRight().setEnabled(false);
        voltageChart.getAxisLeft().setDrawAxisLine(true);
        // APPEND "V" TO Y-AXIS LABELS
        voltageChart.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(java.util.Locale.US, "%.1fV", value);
            }
        });

        // X-Axis Timeline Constraints
        voltageChart.getXAxis().setTextColor(Color.parseColor("#888888"));
        voltageChart.getXAxis().setGridColor(Color.parseColor("#333333"));
        voltageChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        voltageChart.getXAxis().setDrawLabels(false);

        // Inject padding space inside the view window container bounds
        voltageChart.setViewPortOffsets(85f, 40f, 10f, 60f);

        markerView = new CustomMarkerView(this, R.layout.chart_marker_view);
        markerView.setChartView(voltageChart);
        voltageChart.setMarker(markerView);
    }

    private void setupChartFilterListeners() {
        View.OnClickListener filterListener = v -> {
            ColorStateList unselectedBg = ColorStateList.valueOf(Color.parseColor("#121212"));

            // 1. Reset backgrounds using universal ViewCompat tool
            ViewCompat.setBackgroundTintList(btn4H, unselectedBg); btn4H.setTextColor(Color.parseColor("#666666"));
//            ViewCompat.setBackgroundTintList(btn2D, unselectedBg); btn2D.setTextColor(Color.parseColor("#666666"));
//            ViewCompat.setBackgroundTintList(btn1M, unselectedBg); btn1M.setTextColor(Color.parseColor("#666666"));
//            ViewCompat.setBackgroundTintList(btn1Y, unselectedBg); btn1Y.setTextColor(Color.parseColor("#666666"));

            // 2. Highlight the clicked item button element
            Button clickedButton = (Button) v;
            ViewCompat.setBackgroundTintList(clickedButton, ColorStateList.valueOf(Color.parseColor("#222222")));
            clickedButton.setTextColor(Color.parseColor("#FFFFFF"));

            currentFilter = clickedButton.getText().toString();
            updateChartData();
        };

        btn4H.setOnClickListener(filterListener);
//        btn2D.setOnClickListener(filterListener);
//        btn1M.setOnClickListener(filterListener);
//        btn1Y.setOnClickListener(filterListener);
    }


    private void updateChartData() {
        long now = System.currentTimeMillis();
        final long filterCutoffTime;

        switch (currentFilter) {
            case DEFAULT_4H_VIEW: filterCutoffTime = now - (4L * 60 * 60 * 1000); break;
            case "2D":            filterCutoffTime = now - (2L * 24 * 60 * 60 * 1000); break;
            case "1M":            filterCutoffTime = now - (30L * 24 * 60 * 60 * 1000); break;
            case "All":           filterCutoffTime = 0; break;
            default:              filterCutoffTime = now - (4L * 60 * 60 * 1000); break;
        }

        new Thread(() -> {
            List<Entry> chartEntries = new ArrayList<>();

            // 1. CONDITIONAL DATA DOWNSAMPLING SWITCH
            List<VoltageEntry> filteredHistory;
            if (DEFAULT_4H_VIEW.equals(currentFilter)) {
                // lightweight 1-minute averages for 4H
                filteredHistory = dbHelper.getAveragesSince(filterCutoffTime, INTERVAL_1_MIN);
            } else if ("2D".equals(currentFilter)) {
                // lightweight 1-minute aggregated buckets for the 2-day view
                filteredHistory = dbHelper.getAveragesSince(filterCutoffTime, INTERVAL_1_MIN);
            } else {
                // Fetch 10-min averages for 1M and All windows
                filteredHistory = dbHelper.getAveragesSince(filterCutoffTime, INTERVAL_10_MIN);
            }

            // 2. ADAPTIVE GAP CLOSURE COEFFICIENT
            long gapThresholdMs;
            if (currentFilter.equals(DEFAULT_4H_VIEW)) {
                gapThresholdMs = 5L * 60 * 1000; // 5 mins gap for 4H
            } else if (currentFilter.equals("2D")) {
                gapThresholdMs = 45L * 60 * 1000; // 45 mins gap for 2D and 1M
            } else {
                gapThresholdMs = 3L * 60 * 60 * 1000; // 3 hours gap for All
            }

            List<ILineDataSet> dataSets = new ArrayList<>();
            List<Entry> currentSegmentEntries = new ArrayList<>();

            long lastTimestamp = -1;

            for (VoltageEntry data : filteredHistory) {
                long currentTimestamp = data.getTimestamp();
                float relativeTime = (float) (currentTimestamp - filterCutoffTime) / (60 * 1000);

                // Check if the gap exceeds our threshold
                if (lastTimestamp != -1 && (currentTimestamp - lastTimestamp) > gapThresholdMs) {
                    // 1. If we have accumulated valid points, push them as a completed segment dataset
                    if (!currentSegmentEntries.isEmpty()) {
                        dataSets.add(createStyledDataSet(currentSegmentEntries, currentFilter));
                        currentSegmentEntries = new ArrayList<>(); // Start a new segment
                    }
                }

                currentSegmentEntries.add(new Entry(relativeTime, data.getVoltage()));
                lastTimestamp = currentTimestamp;
            }

            // Add any remaining entries as the final segment
            if (!currentSegmentEntries.isEmpty()) {
                dataSets.add(createStyledDataSet(currentSegmentEntries, currentFilter));
            }

            runOnUiThread(() -> {
                // 1. CRITICAL: Clear existing highlights/markers before loading new data
                // to prevent looking up old indices against a new dataset structure.
                voltageChart.highlightValues(null);

                if (!dataSets.isEmpty()) {
                    LineData lineData = new LineData(dataSets);
                    voltageChart.setData(lineData);

                    if (markerView != null) {
                        markerView.setBaseCutoffTime(filterCutoffTime);
                        markerView.setActiveFilter(currentFilter);
                    }

                    voltageChart.notifyDataSetChanged();
                    voltageChart.invalidate();
                } else {
                    voltageChart.clear();
                    voltageChart.invalidate();
                }
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close(); // Clean data disconnect closing event
    }

    private LineDataSet createStyledDataSet(List<Entry> entries, String filter) {
        LineDataSet dataSet = new LineDataSet(entries, "Battery Voltage Status");
        dataSet.setColor(Color.parseColor("#00E5FF"));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);

        if (DEFAULT_4H_VIEW.equals(filter)) {
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        } else {
            dataSet.setMode(LineDataSet.Mode.LINEAR);
        }

        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#00E5FF"));
        dataSet.setFillAlpha(30);

        return dataSet;
    }
}
