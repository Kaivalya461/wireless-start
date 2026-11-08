package in.kvapps.wirelessstart.wear.pubsub;

import android.os.Handler;
import android.os.Looper;

public class NearbyVehicleEventBus {

    public interface NearbyVehicleCallbackListener {
        void onStopPulseEffect();
    }

    private static NearbyVehicleCallbackListener listener;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void setListener(NearbyVehicleCallbackListener l) {
        listener = l;
    }

    public static void notifyStopPulseEffect() {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onStopPulseEffect();
            }
        });
    }
}