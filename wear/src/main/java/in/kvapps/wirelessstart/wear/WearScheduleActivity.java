package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.wear.util.ActionUtil;

public class WearScheduleActivity extends Activity implements DataClient.OnDataChangedListener {

    private static final String TAG = "WearScheduleActivity";
    private TextView txtScheduledTime;
    private Button btnSetTime, btnCancelSchedule;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_schedule);

        txtScheduledTime = findViewById(R.id.txt_wear_scheduled_time);
        btnSetTime = findViewById(R.id.btn_wear_pick_time);
        btnCancelSchedule = findViewById(R.id.btn_wear_cancel_schedule);

        prefs = getSharedPreferences("wear_prefs", MODE_PRIVATE);

        btnSetTime.setOnClickListener(v -> showDateTimePicker());
        btnCancelSchedule.setOnClickListener(v -> cancelSchedule());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDisplay();

        // Directly register live listener for data changes while this activity is open
        Wearable.getDataClient(this).addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remove listener to prevent memory leaks
        Wearable.getDataClient(this).removeListener(this);
    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (Constants.SYNC_ENGINE_SCHEDULE_PATH.equals(path)) {
                    Log.i(TAG, "Live Data Layer update received for schedule path!");

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    long engineScheduleEpoch = dataMapItem.getDataMap().getLong(Constants.KEY_SAVED_SCHEDULE_EPOCH);

                    // Save locally and refresh UI immediately on the Main Thread
                    prefs.edit()
                            .putLong(Constants.KEY_SAVED_SCHEDULE_EPOCH, engineScheduleEpoch)
                            .apply();

                    runOnUiThread(this::updateDisplay);
                }
            }
        }
        dataEvents.release();
    }

    private void updateDisplay() {
        long savedEpoch = prefs.getLong(Constants.KEY_SAVED_SCHEDULE_EPOCH, 0);
        if (savedEpoch > 0 && savedEpoch > (System.currentTimeMillis() / 1000L)) {
            String timeFormatted = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(savedEpoch * 1000L));
            txtScheduledTime.setText(timeFormatted);
        } else {
            txtScheduledTime.setText("None Set");
        }
    }

    private void showDateTimePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
                    int currentMinute = calendar.get(Calendar.MINUTE);

                    TimePickerDialog timePickerDialog = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, selectedMinute) -> {
                                Calendar targetCalendar = Calendar.getInstance();
                                targetCalendar.set(selectedYear, selectedMonth, selectedDay, hourOfDay, selectedMinute, 0);
                                targetCalendar.set(Calendar.MILLISECOND, 0);

                                long targetEpochSeconds = targetCalendar.getTimeInMillis() / 1000L;

                                if (targetEpochSeconds <= (System.currentTimeMillis() / 1000L)) {
                                    Toast.makeText(this, "Selected time must be in the future!", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                ActionUtil.transmitScheduleToPhone(this, targetEpochSeconds);
                                Toast.makeText(this, "Syncing with hardware...", Toast.LENGTH_SHORT).show();
                            },
                            currentHour, currentMinute, true
                    );
                    timePickerDialog.setTitle("Select Time");
                    timePickerDialog.show();
                },
                year, month, day
        );

        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.setTitle("Select Date");
        datePickerDialog.show();
    }

    private void cancelSchedule() {
        ActionUtil.transmitScheduleToPhone(this, 0);
        Toast.makeText(this, "Cancelling on hardware...", Toast.LENGTH_SHORT).show();
    }
}