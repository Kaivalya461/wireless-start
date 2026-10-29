package in.kvapps.wirelessstart.adapter;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.model.VehicleWithSettings;
import in.kvapps.wirelessstart.util.UiUtils;

public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder> {

    private final List<VehicleWithSettings> vehicleList;
    private final VehicleActionListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    protected static List<ConnectionState> lastSeenEligibleStatusList = List.of(ConnectionState.DISCONNECTED, ConnectionState.SCANNING);

    public interface VehicleActionListener {
        void onStartClick(Vehicle vehicle, int position);
        void onStopClick(Vehicle vehicle, int position);
        void onReconnectClick(Vehicle vehicle, int position);
        void onVoltageClick(Vehicle vehicle, int position);
    }

    public VehicleAdapter(List<VehicleWithSettings> vehicleList, VehicleActionListener listener) {
        this.vehicleList = vehicleList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vehicle_control, parent, false);
        return new VehicleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        VehicleWithSettings item = vehicleList.get(position);
        holder.bind(item.getVehicle(), item.getVehicleSettings(), listener, position);
    }

    @Override
    public int getItemCount() {
        return vehicleList.size();
    }

    public void triggerCooldown(Vehicle vehicle, long cooldownMillis) {
        if (vehicle == null) return;

        int position = -1;
        for (int i = 0; i < vehicleList.size(); i++) {
            if (vehicleList.get(i).getVehicle().getId().equals(vehicle.getId())) {
                position = i;
                break;
            }
        }
        if (position == -1) return;

        vehicle.setBusy(true);
        notifyItemChanged(position);

        final String targetVehicleId = vehicle.getId();
        handler.postDelayed(() -> {
            int currentPosition = -1;
            for (int i = 0; i < vehicleList.size(); i++) {
                if (vehicleList.get(i).getVehicle().getId().equals(targetVehicleId)) {
                    currentPosition = i;
                    break;
                }
            }
            if (currentPosition != -1) {
                vehicleList.get(currentPosition).getVehicle().setBusy(false);
                notifyItemChanged(currentPosition);
            }
        }, cooldownMillis);
    }

    public void updateVehicleConnection(String vehicleId, boolean isConnected, String statusText) {
        for (int i = 0; i < vehicleList.size(); i++) {
            Vehicle vehicle = vehicleList.get(i).getVehicle();
            if (vehicle.getId().equals(vehicleId)) {
                vehicle.setConnected(isConnected);
                vehicle.setStatusText(statusText);
                notifyItemChanged(i);
                break;
            }
        }
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtVehicleName, txtStatus, txtVoltageValue, txtActiveBadge;
        private final View statusIndicator, viewStatusBar;
        private final ViewGroup statusBarParent;
        private final ImageButton btnReconnect;
        private final Button btnStart, btnStop;
        private final View panelVoltage;

        private ValueAnimator activeAnimator;

        public VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            txtVehicleName = itemView.findViewById(R.id.txt_vehicle_name);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            txtStatus = itemView.findViewById(R.id.txt_status);
            btnReconnect = itemView.findViewById(R.id.btn_reconnect);
            btnStart = itemView.findViewById(R.id.btn_start);
            btnStop = itemView.findViewById(R.id.btn_stop);
            panelVoltage = itemView.findViewById(R.id.panel_voltage);
            txtVoltageValue = itemView.findViewById(R.id.txt_voltage_value);
            txtActiveBadge = itemView.findViewById(R.id.txt_active_badge);
            viewStatusBar = itemView.findViewById(R.id.view_status_bar);
            statusBarParent = (ViewGroup) viewStatusBar.getParent();
        }

        public void bind(Vehicle vehicle, VehicleSettings settings, VehicleActionListener listener, int position) {
            txtVehicleName.setText(vehicle.getName() != null ? vehicle.getName() : "Unit #" + vehicle.getId());

            MyApplication app = (MyApplication) itemView.getContext().getApplicationContext();
            PreferenceManager preferenceManager = app.getPreferenceManager();

            ConnectionStateRepository.DeviceConnectionState connectionState =
                    ConnectionStateRepository.getInstance().getState(vehicle.getMacAddress());
            boolean isConnected = vehicle.isConnected();
            boolean isBusy = vehicle.isBusy();

            long lastSeenTime = preferenceManager.getLastSeenTimestamp(vehicle.getMacAddress());
            txtStatus.setText(getStatusText(vehicle.getStatusText(), connectionState, lastSeenTime));
            statusIndicator.setBackgroundResource(isConnected ? R.drawable.indicator_online : R.drawable.indicator_offline);
            btnReconnect.setVisibility(isConnected ? View.GONE : View.VISIBLE);

            // Determine Connection State & RSSI
            ConnectionState state = (connectionState != null && connectionState.getState() != null)
                    ? connectionState.getState()
                    : ConnectionState.DISCONNECTED;

            updateConnectionState(state, -61); // Using -61 as sample RSSI for now

            // Active Badge & Preferences
            String selectedId = preferenceManager.getSelectedVehicleId();
            txtActiveBadge.setVisibility(selectedId != null && selectedId.equals(vehicle.getId()) ? View.VISIBLE : View.GONE);

            // Extract flags from settings (fallback safely if settings is null)
            boolean isStartEnabledSetting = settings != null && settings.isStartEngineEnabled();
            boolean isStopEnabledSetting = settings != null && settings.isStopEngineEnabled();

            boolean canClick = isConnected && !isBusy;

//            // Start button visibility & state
            btnStart.setVisibility(isStartEnabledSetting ? View.VISIBLE : View.GONE);
            if (isStartEnabledSetting) {
                UiUtils.setButtonState(btnStart, canClick, canClick ? 1.0f : 0.5f);
            }
//            // Stop button visibility & state
            btnStop.setVisibility(isStopEnabledSetting ? View.VISIBLE : View.GONE);
            if (isStopEnabledSetting) {
                UiUtils.setButtonState(btnStop, canClick, canClick ? 1.0f : 0.5f);
            }

            txtVoltageValue.setText(vehicle.getVoltage());

            // Click Listeners
            btnStart.setOnClickListener(v -> { if (listener != null) listener.onStartClick(vehicle, position); });
            btnStop.setOnClickListener(v -> { if (listener != null) listener.onStopClick(vehicle, position); });
            btnReconnect.setOnClickListener(v -> { if (listener != null) listener.onReconnectClick(vehicle, position); });
            panelVoltage.setOnClickListener(v -> { if (listener != null) listener.onVoltageClick(vehicle, position); });
        }

        private CharSequence getStatusText(String statusText, ConnectionStateRepository.DeviceConnectionState state,
                                           long lastSeenTime) {
            String result;
            if (lastSeenTime > 0
                    && state != null &&
                    lastSeenEligibleStatusList.contains(state.getState())) {
                result = statusText + " • last seen " + UiUtils.formatRelativeTime(lastSeenTime);
            } else {
                result = statusText;
            }

            return result;
        }

        public void updateConnectionState(ConnectionState state, int rssiPercent) {
            viewStatusBar.animate().cancel();
            if (activeAnimator != null) {
                activeAnimator.cancel();
                activeAnimator = null;
            }

            int parentWidth = statusBarParent.getWidth();
            if (parentWidth == 0) {
                statusBarParent.post(() -> updateConnectionState(state, rssiPercent));
                return;
            }

            if (state != ConnectionState.SCANNING) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) viewStatusBar.getLayoutParams();
                marginParams.leftMargin = 0;
                viewStatusBar.setLayoutParams(marginParams);
            }

            int startWidth = viewStatusBar.getWidth();
            int colorRes;
            int targetWidth = 0;
            long duration = 300;

            switch (state) {
                case CONNECTED:
                    colorRes = R.color.status_connected;
                    targetWidth = parentWidth;
                    duration = 300;
                    animateBarWidth(startWidth, targetWidth, duration, colorRes, false);
                    break;

                case DISCONNECTED:
                    colorRes = R.color.status_disconnected;
                    targetWidth = 0;
                    duration = 800;
                    animateBarWidth(startWidth, targetWidth, duration, colorRes, false);
                    break;

                case NEARBY_FAR_AWAY: // TODO- Yet to implement this
                    colorRes = R.color.status_nearby;
                    targetWidth = (int) (parentWidth * (Math.max(0, Math.min(rssiPercent, 100)) / 100f));
                    duration = 400;
                    animateBarWidth(startWidth, targetWidth, duration, colorRes, false);
                    break;

                case SCANNING:
                    colorRes = R.color.status_scanning;
                    viewStatusBar.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), colorRes));
                    startScanningBounceAnimation(parentWidth);
                    break;
            }
        }

        private void animateBarWidth(int startWidth, int endWidth, long duration, int colorRes, boolean isScanning) {
            viewStatusBar.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), colorRes));

            ValueAnimator anim = ValueAnimator.ofInt(startWidth, endWidth);
            anim.setDuration(duration);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.addUpdateListener(animator -> {
                ViewGroup.LayoutParams params = viewStatusBar.getLayoutParams();
                params.width = (int) animator.getAnimatedValue();
                viewStatusBar.setLayoutParams(params);
            });
            anim.start();
            activeAnimator = anim;
        }

        private void startScanningBounceAnimation(int parentWidth) {
            int sweepWidth = (int) (parentWidth * 0.22f);

            ValueAnimator anim = ValueAnimator.ofInt(0, parentWidth - sweepWidth);
            anim.setDuration(1500);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());

            anim.addUpdateListener(animator -> {
                int leftMargin = (int) animator.getAnimatedValue();
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) viewStatusBar.getLayoutParams();
                params.width = sweepWidth;
                params.leftMargin = leftMargin;
                viewStatusBar.setLayoutParams(params);
            });
            anim.start();
            activeAnimator = anim;
        }
    }
}