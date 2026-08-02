package in.kvapps.wirelessstart.wear;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import in.kvapps.wirelessstart.shared.Constants;

// Listener to consume messages sent by Phone App
public class PhoneMessageListenerService extends WearableListenerService {
    private static final String TAG = Constants.PHONE_DATA_LAYER_TAG;
    private static final String VIBRATE_PATH = Constants.VIBRATE_PATH;

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (VIBRATE_PATH.equals(messageEvent.getPath())) {
            Log.d(TAG, "Vibrate message received from phone. Triggering watch vibrate.");
            triggerDoubleVibrate(this);
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
}