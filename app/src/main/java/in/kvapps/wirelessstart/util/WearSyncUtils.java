package in.kvapps.wirelessstart.util;

import android.content.Context;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import in.kvapps.wirelessstart.shared.Constants;

public class WearSyncUtils {
    public static void syncBleStatusToWatch(Context context, boolean isConnected, String statusText) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(Constants.TARGET_DEVICE_CONNECTION_STATUS);

        dataMap.getDataMap().putBoolean("is_connected", isConnected);
        dataMap.getDataMap().putString("status_text", statusText);
        dataMap.getDataMap().putLong("timestamp", System.currentTimeMillis());

        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();

        Wearable.getDataClient(context).putDataItem(request);
    }
}