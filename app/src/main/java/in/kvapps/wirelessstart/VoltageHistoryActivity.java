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
import java.util.ArrayList;
import java.util.List;

import in.kvapps.wirelessstart.chart.CustomMarkerView;
import in.kvapps.wirelessstart.db.VoltageDbHelper;
import in.kvapps.wirelessstart.model.VoltageEntry;

public class VoltageHistoryActivity extends Activity {

    private LineChart voltageChart;
    private CustomMarkerView markerView;
    private VoltageDbHelper dbHelper;
    private Button btn1D, btn7D, btn1M, btn1Y;
    private String currentFilter = "1H";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voltage_history);

        dbHelper = new VoltageDbHelper(this);

        // Bind Views
        voltageChart = findViewById(R.id.voltage_trend_chart);
        btn1D = findViewById(R.id.btn_filter_1d);
        btn7D = findViewById(R.id.btn_filter_7d);
        btn1M = findViewById(R.id.btn_filter_1m);
        btn1Y = findViewById(R.id.btn_filter_1y);

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

        // Y-Axis Grid Rules Styling
        voltageChart.getAxisLeft().setTextColor(Color.parseColor("#CCCCCC"));
        voltageChart.getAxisLeft().setGridColor(Color.parseColor("#333333"));
        voltageChart.getAxisRight().setEnabled(false);
        voltageChart.getAxisLeft().setDrawAxisLine(true);

        // X-Axis Timeline Constraints
        voltageChart.getXAxis().setTextColor(Color.parseColor("#888888"));
        voltageChart.getXAxis().setGridColor(Color.parseColor("#333333"));
        voltageChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        voltageChart.getXAxis().setDrawLabels(false);

        // Inject padding space inside the view window container bounds
        voltageChart.setViewPortOffsets(60f, 40f, 40f, 60f);

        markerView = new CustomMarkerView(this, R.layout.chart_marker_view);
        voltageChart.setMarker(markerView);
    }

    private void setupChartFilterListeners() {
        View.OnClickListener filterListener = v -> {
            ColorStateList unselectedBg = ColorStateList.valueOf(Color.parseColor("#121212"));

            // 1. Reset backgrounds using universal ViewCompat tool
            ViewCompat.setBackgroundTintList(btn1D, unselectedBg); btn1D.setTextColor(Color.parseColor("#666666"));
            ViewCompat.setBackgroundTintList(btn7D, unselectedBg); btn7D.setTextColor(Color.parseColor("#666666"));
            ViewCompat.setBackgroundTintList(btn1M, unselectedBg); btn1M.setTextColor(Color.parseColor("#666666"));
            ViewCompat.setBackgroundTintList(btn1Y, unselectedBg); btn1Y.setTextColor(Color.parseColor("#666666"));

            // 2. Highlight the clicked item button element
            Button clickedButton = (Button) v;
            ViewCompat.setBackgroundTintList(clickedButton, ColorStateList.valueOf(Color.parseColor("#222222")));
            clickedButton.setTextColor(Color.parseColor("#FFFFFF"));

            currentFilter = clickedButton.getText().toString();
            updateChartData();
        };

        btn1D.setOnClickListener(filterListener);
        btn7D.setOnClickListener(filterListener);
        btn1M.setOnClickListener(filterListener);
        btn1Y.setOnClickListener(filterListener);
    }


    private void updateChartData() {
        long now = System.currentTimeMillis();
        final long filterCutoffTime;

        switch (currentFilter) {
            case "1H":  filterCutoffTime = now - (60L * 60 * 1000); break;
            case "7D":  filterCutoffTime = now - (7L * 24 * 60 * 60 * 1000); break;
            case "1Y":  filterCutoffTime = now - (365L * 24 * 60 * 60 * 1000); break;
            case "All": filterCutoffTime = 0; break;
            default:    filterCutoffTime = now - (60L * 60 * 1000); break;
        }

        new Thread(() -> {
            List<Entry> chartEntries = new ArrayList<>();

            // 1. CONDITIONAL DATA DOWNSAMPLING SWITCH
            List<VoltageEntry> filteredHistory;
            if ("1Y".equals(currentFilter) || "All".equals(currentFilter)) {
                // Fetch downsampled hourly trends for long windows
                filteredHistory = dbHelper.getHourlyAveragesSince(filterCutoffTime);
            } else {
                // Fetch raw, granular per-second points for 1H and 7D windows
                filteredHistory = dbHelper.getReadingsSince(filterCutoffTime);
            }

            // 2. ADAPTIVE GAP CLOSURE COEFFICIENT
            long gapThresholdMs;
            if (currentFilter.equals("1H")) {
                gapThresholdMs = 1L * 60 * 1000;
            } else if (currentFilter.equals("7D")) {
                gapThresholdMs = 15L * 60 * 1000;
            } else {
                // Since data is grouped hourly, a gap exists if no logs are found for > 3 Hours
                gapThresholdMs = 3L * 60 * 60 * 1000;
            }

            long lastTimestamp = -1;

            for (VoltageEntry data : filteredHistory) {
                long currentTimestamp = data.getTimestamp();
                float relativeTime = (float) (currentTimestamp - filterCutoffTime) / (60 * 1000);

                if (lastTimestamp != -1 && (currentTimestamp - lastTimestamp) > gapThresholdMs) {
                    float breakTimeStart = (float) ((lastTimestamp + 1000) - filterCutoffTime) / (60 * 1000);
                    chartEntries.add(new Entry(breakTimeStart, Float.NaN));
                }

                chartEntries.add(new Entry(relativeTime, data.getVoltage()));
                lastTimestamp = currentTimestamp;
            }

            runOnUiThread(() -> {
                if (!chartEntries.isEmpty()) {
                    LineDataSet dataSet = new LineDataSet(chartEntries, "Battery Voltage Status");
                    dataSet.setColor(Color.parseColor("#00E5FF"));
                    dataSet.setLineWidth(2f);
                    dataSet.setDrawCircles(true);
                    dataSet.setCircleRadius(0f);
                    dataSet.setDrawCircleHole(false);
                    dataSet.setDrawValues(false);
                    dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                    dataSet.setDrawFilled(true);
                    dataSet.setFillColor(Color.parseColor("#00E5FF"));
                    dataSet.setFillAlpha(30);

                    LineData lineData = new LineData(dataSet);
                    voltageChart.setData(lineData);

                    if (markerView != null) {
                        markerView.setBaseCutoffTime(filterCutoffTime);
                        markerView.setActiveFilter(currentFilter);
                    }

                    voltageChart.invalidate();
                } else {
                    voltageChart.clear();
                }
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close(); // Clean data disconnect closing event
    }
}
