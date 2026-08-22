package in.kvapps.wirelessstart.wear.complication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;

import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.wear.R;

public class WirelessStartComplicationService extends ComplicationDataSourceService {
    private static final String TAG = "WirelessStartComplication";

    @Override
    public void onComplicationRequest(
            @NonNull ComplicationRequest request,
            @NonNull ComplicationRequestListener listener
    ) {
        // Read connection state saved by PhoneMessageListenerService
        SharedPreferences prefs = getSharedPreferences("wear_prefs", Context.MODE_PRIVATE);
        boolean isConnected = prefs.getBoolean("is_connected", false);

        // Pick icon resource based on connection status
        int iconResId = isConnected ? R.drawable.ic_device_connected_ambient : R.drawable.ic_device_disconnected_ambient;

        MonochromaticImage monochromaticImage = new MonochromaticImage.Builder(
                android.graphics.drawable.Icon.createWithResource(this, iconResId)
        ).build();

        PlainComplicationText contentDescription = new PlainComplicationText.Builder(
                isConnected ? Constants.TARGET_DEVICE_CONNECTED : Constants.TARGET_DEVICE_DISCONNECTED
        ).build();

        ComplicationData complicationData = new MonochromaticImageComplicationData.Builder(
                monochromaticImage,
                contentDescription
        ).build();

        try {
            listener.onComplicationData(complicationData);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send complication data", e);
        }
    }

    @Nullable
    @Override
    public ComplicationData getPreviewData(@NonNull ComplicationType type) {
        MonochromaticImage monochromaticImage = new MonochromaticImage.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_device_disconnected_ambient)
        ).build();

        PlainComplicationText contentDescription = new PlainComplicationText.Builder("Connection Status Preview").build();

        return new MonochromaticImageComplicationData.Builder(
                monochromaticImage,
                contentDescription
        ).build();
    }
}