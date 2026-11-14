package in.kvapps.wirelessstart.ble;

import static in.kvapps.wirelessstart.enums.VehicleEventType.*;

import android.content.Context;
import android.util.Log;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.dto.VehicleEventData;
import in.kvapps.wirelessstart.enums.VehicleEventType;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.repository.VehicleRepository;

public class BackgroundVehicleEventManager implements VehicleEventRepository.VehicleEventListener {
    private static final String TAG = "VehicleEventRouter";
    private static BackgroundVehicleEventManager instance;

    private final Context context;
    private BleManager bleManager;
    private final VehicleRepository vehicleRepository;
    private PreferenceManager preferenceManager;

    private BackgroundVehicleEventManager(Context context) {
        this.context = context.getApplicationContext();
        MyApplication app = (MyApplication) this.context;
        this.bleManager = app.getBleManager();
        this.vehicleRepository = app.getVehicleRepository();
        preferenceManager = app.getPreferenceManager();
        // Register permanently at the application level
        VehicleEventRepository.getInstance().addListener(this);
    }

    public static synchronized BackgroundVehicleEventManager getInstance(Context context) {
        if (instance == null) {
            instance = new BackgroundVehicleEventManager(context);
        }
        return instance;
    }

    @Override
    public void onVehicleEvent(String macAddress, VehicleEventType vehicleEventType, VehicleEventData vehicleEventData) {
        Vehicle vehicle = vehicleRepository.getVehicleByMac(macAddress);
        if (vehicle == null) {
            Log.w(TAG, "Missing Vehicle object for event: " + vehicleEventType + ", MAC: " + macAddress);
            return;
        }

        switch (vehicleEventType) {
            case CLOSEST_VEHICLE_CHANGED:
                // This now executes reliably even when the phone is in your pocket!
                vehicleRepository.setDefaultVehicle(vehicle.getId());
                Log.i(TAG, "Default vehicle automatically updated to: " + vehicle.getName());
                break;

            case LINK_LOSS_DISCONNECT:
                // Do Nothing
                break;

            case CONNECTION_SUCCESS:
                startOnConnectInitSequence(macAddress);
                break;

            case DISCONNECT_THRESHOLD_EXCEEDED:
                String vehicleName = (vehicle != null) ? vehicle.getName() : macAddress;
                Log.w(TAG, "Force disconnecting [" + vehicleName + "] due to persistent low RSSI threshold breach.");

                // Command BleManager to drop the gatt connection cleanly
                bleManager.disconnect(macAddress);
                break;

            default:
                break;
        }
    }

    private void startOnConnectInitSequence(String macAddress) {
        BleDeviceSession session = bleManager.getSession(macAddress);

        if (session != null) {
            boolean savedTelemetryState = preferenceManager.isTelemetryEnabled();
            boolean savedFailSafeState = preferenceManager.isFailSafeEnabled();

            session.syncInitializationSequence(
                    session::sendAutoTimeSync,
                    () -> session.syncTelemetryState(savedTelemetryState),
                    () -> session.syncFailSafeState(savedFailSafeState),
                    () -> session.requestScheduleFromEsp32(),
                    () -> VehicleEventRepository.getInstance().emitEvent(macAddress, READY_FOR_CONTROL)
            );
        }
    }
}