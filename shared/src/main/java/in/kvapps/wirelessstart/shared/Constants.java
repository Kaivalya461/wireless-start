package in.kvapps.wirelessstart.shared;

public class Constants {
    // Data Layer Paths
    public static final String START_PATH = "/dio/engine_start";
    public static final String STOP_PATH = "/dio/engine_stop";
    public static final String VIBRATE_PATH = "/dio/trigger_vibrate"; // Phone and ESP32 connection completion
    public static final String START_SUCCESS_PATH = "/dio/start_success";

    // Common Tags or Shared Extras if needed
    public static final String WEAR_DATA_LAYER_TAG = "DioWearDataLayer";
    public static final String PHONE_DATA_LAYER_TAG = "DioPhoneDataLayer";

    // Haptic Tpyes
    public static final String HAPTIC_CONNECT = "CONNECT";
    public static final String HAPTIC_DISCONNECT = "DISCONNECT";
}