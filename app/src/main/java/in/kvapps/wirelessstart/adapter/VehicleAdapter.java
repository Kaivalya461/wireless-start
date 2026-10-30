package in.kvapps.wirelessstart.adapter;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
import java.util.List;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.enums.SignalState;
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
        private final List<Integer> rssiHistory = new ArrayList<>();
        private static final int MAX_HISTORY_SIZE = 7; // Increased size for better smoothing

        private float currentAnimatedWidth = 0f;
        private int lastAnimatedWidth = -1;

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

            // Determine Connection State & Live RSSI
            ConnectionState state = (connectionState != null && connectionState.getState() != null)
                    ? connectionState.getState()
                    : ConnectionState.DISCONNECTED;

            int liveRssi = (connectionState != null) ? connectionState.getRssi() : -999;

            // Reset history if the vehicle changes or disconnects to prevent stale averages
            if (state == ConnectionState.DISCONNECTED || state == ConnectionState.SCANNING) {
                rssiHistory.clear();
                lastAnimatedWidth = -1;
                currentAnimatedWidth = 0f;
            }

            updateConnectionState(state, liveRssi);

            // Active Badge & Preferences
            txtActiveBadge.setVisibility(vehicle.isDefault() ? View.VISIBLE : View.GONE);

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

        public void updateConnectionState(ConnectionState state, int rssiValue) {
            viewStatusBar.animate().cancel();
            if (activeAnimator != null) {
                activeAnimator.cancel();
                activeAnimator = null;
            }

            int parentWidth = statusBarParent.getWidth();
            if (parentWidth == 0) {
                statusBarParent.post(() -> updateConnectionState(state, rssiValue));
                return;
            }

            if (state != ConnectionState.SCANNING) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) viewStatusBar.getLayoutParams();
                marginParams.leftMargin = 0;
                viewStatusBar.setLayoutParams(marginParams);
            }

            int startWidth = viewStatusBar.getWidth();
            int colorRes;

            switch (state) {
                case CONNECTED:
                    colorRes = R.color.status_connected;
                    animateBarWidth(startWidth, parentWidth, 300, colorRes, false);
                    break;

                case DISCONNECTED:
                    colorRes = R.color.status_disconnected;
                    animateBarWidth(startWidth, 0, 800, colorRes, false);
                    break;

                case NEARBY_FAR_AWAY:
                    colorRes = R.color.status_nearby;
                    viewStatusBar.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), colorRes));

                    // Get smoothed width using median filtering + EMA smoothing
                    int smoothWidth = getSmoothTargetWidth(rssiValue, parentWidth);

                    // Deadband check: Ignore tiny width variations (< 4 pixels) to avoid layout jiggling
                    if (lastAnimatedWidth != -1 && Math.abs(smoothWidth - lastAnimatedWidth) < 4) {
                        return;
                    }
                    lastAnimatedWidth = smoothWidth;

                    if (currentAnimatedWidth == 0f && startWidth > 0) {
                        currentAnimatedWidth = startWidth;
                    }

                    ViewGroup.LayoutParams params = viewStatusBar.getLayoutParams();
                    params.width = smoothWidth;
                    viewStatusBar.setLayoutParams(params);
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

        private int calculateTargetWidth(int rssiValue, int parentWidth) {
            // Filter out invalid/empty RSSI packets
            if (rssiValue >= 0 || rssiValue == -999) {
                return lastAnimatedWidth != -1 ? lastAnimatedWidth : 0;
            }

            // 1. Maintain sliding window history (Median Filter for outlier rejection)
            rssiHistory.add(rssiValue);
            if (rssiHistory.size() > MAX_HISTORY_SIZE) {
                rssiHistory.remove(0);
            }

            List<Integer> sortedList = new ArrayList<>(rssiHistory);
            java.util.Collections.sort(sortedList);

            int medianRssi;
            int size = sortedList.size();
            if (size % 2 == 0) {
                medianRssi = (sortedList.get((size / 2) - 1) + sortedList.get(size / 2)) / 2;
            } else {
                medianRssi = sortedList.get(size / 2);
            }

            // 2. Clamping bounds (~max range cutoff vs closest range)
            int minRssi = -100;
            int maxRssi = SignalState.SUPER_STRONG.getRssiLimit();

            if (maxRssi <= minRssi) {
                return 0;
            }

            int clampedRssi = Math.max(minRssi, Math.min(medianRssi, maxRssi));
            float normalizedPercent = (float) (clampedRssi - minRssi) / (maxRssi - minRssi);

            // 3. Perceptual curve scaling (makes medium distance scaling feel more natural)
            normalizedPercent = (float) Math.pow(normalizedPercent, 1.5);

            return (int) (parentWidth * normalizedPercent);
        }

        private int getSmoothTargetWidth(int rssiValue, int parentWidth) {
            int targetWidth = calculateTargetWidth(rssiValue, parentWidth);

            // Exponential Moving Average (EMA) factor (0.15f handles fast packet arrivals smoothly)
            float smoothingFactor = 0.15f;
            currentAnimatedWidth = currentAnimatedWidth + smoothingFactor * (targetWidth - currentAnimatedWidth);

            return (int) currentAnimatedWidth;
        }
    }
}