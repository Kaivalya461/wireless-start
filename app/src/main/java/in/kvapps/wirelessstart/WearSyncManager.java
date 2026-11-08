package in.kvapps.wirelessstart;

import static in.kvapps.wirelessstart.enums.ConnectionState.CONNECTED;
import static in.kvapps.wirelessstart.enums.ConnectionState.DISCONNECTED;

import android.content.Context;
import android.util.Log;

import in.kvapps.wirelessstart.ble.VehicleEventRepository;
import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.VehicleEventType;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.repository.VehicleRepository;
import in.kvapps.wirelessstart.shared.Constants;
import in.kvapps.wirelessstart.shared.model.VehicleNearbyPayload;
import in.kvapps.wirelessstart.util.FeedbackUtils;
import in.kvapps.wirelessstart.util.WearSyncUtils;

public class WearSyncManager implements VehicleEventRepository.VehicleEventListener {
    private static final String TAG = "WearSyncManager";
    private static WearSyncManager instance;
    private final Context context;
    private final VehicleRepository vehicleRepository;

    private WearSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.vehicleRepository = ((MyApplication) context.getApplicationContext()).getVehicleRepository();
        VehicleEventRepository.getInstance().addListener(this);
    }

    // Thread-safe singleton getter
    public static synchronized WearSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new WearSyncManager(context);
        }
        return instance;
    }

    @Override
    public void onVehicleEvent(String macAddress, VehicleEventType vehicleEventType, VehicleEventData vehicleEventData) {
        Log.i(TAG, "Received vehicle event for MAC: " + macAddress + " | Event: " + vehicleEventType);

        // React based on the event type for Wear OS syncing
        switch (vehicleEventType) {
            case CONNECTION_SUCCESS:
                WearSyncUtils.syncBleStatusToWatch(context, true, CONNECTED.getDisplayName());
                FeedbackUtils.sendHapticToWatch(context, Constants.HAPTIC_CONNECT);
                break;
            case LINK_LOSS_DISCONNECT:
                WearSyncUtils.syncBleStatusToWatch(context, false, DISCONNECTED.getDisplayName());
                FeedbackUtils.sendHapticToWatch(context, Constants.HAPTIC_DISCONNECT);
                break;
            case ENTERED_NEARBY:
                Vehicle nearbyVehicle = vehicleRepository.getVehicleByMac(macAddress);
                if (vehicleEventData != null) {
                    VehicleNearbyPayload payload = new VehicleNearbyPayload(
                            nearbyVehicle.getId(),
                            nearbyVehicle.getName(),
                            vehicleEventData.getRssi(),
                            vehicleEventData.isConnected(),
                            true
                    );
                    FeedbackUtils.sendVehicleNearbyToWatch(context, payload);
                }
                break;
            case EXITED_NEARBY:
                Vehicle exitedNearbyVehicle = vehicleRepository.getVehicleByMac(macAddress);
                if (vehicleEventData != null) {
                    VehicleNearbyPayload payload = new VehicleNearbyPayload(
                            exitedNearbyVehicle.getId(),
                            exitedNearbyVehicle.getName(),
                            vehicleEventData.getRssi(),
                            vehicleEventData.isConnected(),
                            false
                    );
                    FeedbackUtils.sendVehicleNearbyToWatch(context, payload);
                }
                break;
        }
    }
}