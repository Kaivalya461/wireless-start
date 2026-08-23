package in.kvapps.wirelessstart.wear;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.wear.complication.WirelessStartComplicationService;
import in.kvapps.wirelessstart.wear.pubsub.StartEventBus;

// Listener to consume messages sent by Phone App
public class PhoneMessageListenerService extends WearableListenerService {
    private static final String TAG = Constants.PHONE_DATA_LAYER_TAG;

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (Constants.VIBRATE_PATH.equals(messageEvent.getPath())) {
            Log.d(TAG, "Vibrate message received from phone. Triggering watch vibrate.");
            String command = new String(messageEvent.getData());

            if (Constants.HAPTIC_DISCONNECT.equals(command)) {
                triggerDisconnectVibrate(this);
            } else if (Constants.HAPTIC_CONNECT.equals(command)) {
                triggerDoubleVibrate(this);
            }
        }

        if (Constants.START_COMMAND_RESULT_PATH.equals(messageEvent.getPath())) {
            Log.d(TAG, "Start_Success message received from phone. Triggering command completion watch feedback.");
            String command = new String(messageEvent.getData());

            if (Constants.START_SUCCESS.equals(command)) {
                triggerDoubleVibrate(this);

                // Notify UI Screen
                StartEventBus.notifySuccess();
            } else if (Constants.START_FAILURE.equals(command)) {
                StartEventBus.notifyFailure();
            }
        }
    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.i(TAG, "CONN Status update received ........ ");

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (Constants.TARGET_DEVICE_CONNECTION_STATUS.equals(path)) {

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    boolean isConnected = dataMapItem.getDataMap().getBoolean("is_connected");
                    String statusText = dataMapItem.getDataMap().getString("status_text");

                    // Save state locally on the watch
                    SharedPreferences prefs = getSharedPreferences("wear_prefs", Context.MODE_PRIVATE);
                    prefs.edit()
                            .putBoolean("is_connected", isConnected)
                            .putString("status_text", statusText)
                            .apply();

                    // Force update the watch complication using requestUpdateAll()
                    ComplicationDataSourceUpdateRequester.create(
                            this,
                            new ComponentName(this, WirelessStartComplicationService.class)
                    ).requestUpdateAll();
                }
            }
        }
    }

    private void triggerDoubleVibrate(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            long[] timings = {0, 80, 80, 80};
            int[] amplitudes = {0, 255, 0, 255};
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        }
    }

    /**
     * Triggers a distinct disconnect vibration pattern optimized for standard haptic motors.
     * Pattern: Strong snap, brief pause, lighter click.
     */
    public static void triggerDisconnectVibrate(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            // Snap-Thud pattern:
            // Wait 0ms | Vibrate 70ms (Amp: 255) | Pause 50ms | Vibrate 40ms (Amp: 120)
            long[] timings = {0, 70, 50, 40};
            int[] amplitudes = {0, 255, 0, 120};
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1));
        }
    }
}