package in.kvapps.wirelessstart.wear.pubsub;

public class StartEventBus {

    public interface StartCallbackListener {
        void onSuccess();
        void onFailure();
    }

    private static StartCallbackListener listener;

    public static void setListener(StartCallbackListener l) {
        listener = l;
    }

    public static void notifySuccess() {
        if (listener != null) {
            listener.onSuccess();
        }
    }

    public static void notifyFailure() {
        if (listener != null) {
            listener.onFailure();
        }
    }
}