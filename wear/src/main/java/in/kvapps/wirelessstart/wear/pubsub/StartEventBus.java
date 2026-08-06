package in.kvapps.wirelessstart.wear.pubsub;

public class StartEventBus {
    public interface StartSuccessListener {
        void onSuccess();
    }

    private static StartSuccessListener listener;

    public static void setListener(StartSuccessListener l) {
        listener = l;
    }

    public static void notifySuccess() {
        if (listener != null) {
            listener.onSuccess();
        }
    }
}
