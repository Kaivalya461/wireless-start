package in.kvapps.wirelessstart.shared;

public class Constants {
    // Data Layer Paths
    public static final String START_PATH = "/dio/engine_start";
    public static final String STOP_PATH = "/dio/engine_stop";
    public static final String VIBRATE_PATH = "/dio/trigger_vibrate"; // Phone and ESP32 connection completion
    public static final String START_COMMAND_RESULT_PATH = "/dio/start_command_result";
    public static final String TARGET_DEVICE_CONNECTION_STATUS = "/dio/target_device_connection_status";

    // Common Tags or Shared Extras if needed
    public static final String WEAR_DATA_LAYER_TAG = "DioWearDataLayer";
    public static final String PHONE_DATA_LAYER_TAG = "DioPhoneDataLayer";
    public static final String PHONE_MAIN_ACTIVITY_TAG = "PhoneMainActivity";

    // Haptic Types
    public static final String HAPTIC_CONNECT = "CONNECT";
    public static final String HAPTIC_DISCONNECT = "DISCONNECT";

    // Start Command Result Types
    public static final String COMMAND_SUCCESS = "Command_Success";
    public static final String COMMAND_FAILURE = "Command_Failure";

    // Target Device CONNECTION STATUS Types
    public static final String TARGET_DEVICE_CONNECTED = "TARGET_DEVICE_CONNECTED";
    public static final String TARGET_DEVICE_DISCONNECTED = "TARGET_DEVICE_DISCONNECTED";
}