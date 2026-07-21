package in.kvapps.wirelessstart;

import android.content.Intent;
import android.util.Log;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearMessageListenerService extends WearableListenerService {
    private static final String START_PATH = "/dio/engine_start";
    private static final String STOP_PATH = "/dio/engine_stop";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d("DioWearListener", "Received data packet route path: " + path);

        // Broadcaster to wake up and pass the event directly into your active MainActivity UI loop
        Intent broadCastIntent = new Intent("DIO_HARDWARE_TRIGGER");

        if (START_PATH.equals(path)) {
            broadCastIntent.putExtra("COMMAND", "START");
            sendBroadcast(broadCastIntent);
        } else if (STOP_PATH.equals(path)) {
            broadCastIntent.putExtra("COMMAND", "STOP");
            sendBroadcast(broadCastIntent);
        }
    }
}
