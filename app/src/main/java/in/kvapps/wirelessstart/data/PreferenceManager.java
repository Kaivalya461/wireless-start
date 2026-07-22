package in.kvapps.wirelessstart.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class PreferenceManager {
    private static final String PREFS_NAME = "WirelessStartPrefs";
    private static final String KEY_START_SPINNER_POS = "start_spinner_pos";
    private static final String KEY_STOP_SPINNER_POS = "stop_spinner_pos";
    private static final String KEY_START_CUSTOM_MS = "start_custom_ms";
    private static final String KEY_STOP_CUSTOM_MS = "stop_custom_ms";

    private final SharedPreferences prefs;
    private final Context context;

    public PreferenceManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveStartSpinnerPosition(int pos) {
        prefs.edit().putInt(KEY_START_SPINNER_POS, pos).apply();
        syncToWearables();
    }

    public int getStartSpinnerPosition() {
        return prefs.getInt(KEY_START_SPINNER_POS, 0);
    }

    public void saveStopSpinnerPosition(int pos) {
        prefs.edit().putInt(KEY_STOP_SPINNER_POS, pos).apply();
        syncToWearables();
    }

    public int getStopSpinnerPosition() {
        return prefs.getInt(KEY_STOP_SPINNER_POS, 0);
    }

    public void saveStartCustomMs(String customMs) {
        prefs.edit().putString(KEY_START_CUSTOM_MS, customMs).apply();
        syncToWearables();
    }

    public String getStartCustomMs() {
        return prefs.getString(KEY_START_CUSTOM_MS, "");
    }

    public void saveStopCustomMs(String customMs) {
        prefs.edit().putString(KEY_STOP_CUSTOM_MS, customMs).apply();
        syncToWearables();
    }

    public String getStopCustomMs() {
        return prefs.getString(KEY_STOP_CUSTOM_MS, "");
    }

    // Resolves active pulse duration string for a given action
    public String getFormattedCommand(String action) {
        boolean isStart = "START".equals(action);
        int pos = isStart ? getStartSpinnerPosition() : getStopSpinnerPosition();
        String customVal = isStart ? getStartCustomMs() : getStopCustomMs();

        switch (pos) {
            case 1: return action + ":2000";
            case 2: return action + ":3000";
            case 3:
                if (!customVal.trim().isEmpty()) return action + ":" + customVal.trim();
                return action;
            default: return action;
        }
    }

    // Sync configuration map to all connected Wear OS devices
    public void syncToWearables() {
        PutDataMapRequest dataMap = PutDataMapRequest.create("/config_durations");
        dataMap.getDataMap().putString("START_CMD", getFormattedCommand("START"));
        dataMap.getDataMap().putString("STOP_CMD", getFormattedCommand("STOP"));

        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Wearable.getDataClient(context).putDataItem(request);
    }
}