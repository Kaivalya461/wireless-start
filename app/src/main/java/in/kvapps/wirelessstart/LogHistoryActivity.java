package in.kvapps.wirelessstart;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.db.VoltageDbHelper;

public class LogHistoryActivity extends AppCompatActivity {

    private TextView txtHistoryLog;
    private Button btnSelectDate;
    private ScrollView scrollHistoryLog;
    private VoltageDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_history);

        txtHistoryLog = findViewById(R.id.txt_history_log);
        btnSelectDate = findViewById(R.id.btn_select_date);
        scrollHistoryLog = findViewById(R.id.scroll_history_log);

        // Load today's logs by default
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        loadLogsForDate(currentDate);

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());

        dbHelper = new VoltageDbHelper(this);
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Format the selected date to match your DB date format (e.g., "yyyy-MM-dd")
                    String selectedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month + 1, dayOfMonth);
                    btnSelectDate.setText(selectedDate);
                    loadLogsForDate(selectedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void loadLogsForDate(String date) {
        if (dbHelper == null) return;

        // Clear existing text
        txtHistoryLog.setText("");

        // Fetch logs from SQLite database for the specified date
        // Note: Make sure to implement getLogsForDate(date) in your dbHelper
        // that queries your database using the provided date string.
        java.util.List<String> dateLogs = dbHelper.getLogsForDate(date);

        if (dateLogs != null && !dateLogs.isEmpty()) {
            StringBuilder logBuilder = new StringBuilder();
            for (String logLine : dateLogs) {
                logBuilder.append(logLine).append("\n");
            }
            txtHistoryLog.setText(logBuilder.toString());

            // Scroll down to the latest log entry automatically
            if (scrollHistoryLog != null) {
                scrollHistoryLog.post(() -> scrollHistoryLog.fullScroll(View.FOCUS_DOWN));
            }
        } else {
            // Fallback message if no logs exist for the selected date
            txtHistoryLog.setText("No activity logs found for " + date + ".\n");
        }
    }
}