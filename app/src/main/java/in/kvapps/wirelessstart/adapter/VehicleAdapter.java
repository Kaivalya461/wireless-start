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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import in.kvapps.wirelessstart.MyApplication;
import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.ble.ConnectionStateRepository;
import in.kvapps.wirelessstart.ble.VehicleDistanceManager;
import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.ConnectionState;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.model.VehicleSettings;
import in.kvapps.wirelessstart.model.VehicleWithSettings;
import in.kvapps.wirelessstart.util.UiUtils;
import in.kvapps.wirelessstart.view.GlowCardView;

public class VehicleAdapter extends ListAdapter<VehicleWithSettings, VehicleAdapter.VehicleViewHolder> {

    private final VehicleActionListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    protected static List<ConnectionState> lastSeenEligibleStatusList = List.of(ConnectionState.DISCONNECTED, ConnectionState.SCANNING);
    public static final String PAYLOAD_VOLTAGE = "PAYLOAD_VOLTAGE";

    public interface VehicleActionListener {
        void onStartClick(Vehicle vehicle, int position);
        void onStopClick(Vehicle vehicle, int position);
        void onReconnectClick(Vehicle vehicle, int position);
        void onVoltageClick(Vehicle vehicle, int position);
    }

    public VehicleAdapter(VehicleActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<VehicleWithSettings> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull VehicleWithSettings oldItem, @NonNull VehicleWithSettings newItem) {
            Vehicle v1 = oldItem.getVehicle();
            Vehicle v2 = newItem.getVehicle();
            if (v1 == null || v2 == null) return false;
            return v1.getId().equals(v2.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull VehicleWithSettings oldItem, @NonNull VehicleWithSettings newItem) {
            Vehicle v1 = oldItem.getVehicle();
            Vehicle v2 = newItem.getVehicle();
            VehicleSettings s1 = oldItem.getVehicleSettings();
            VehicleSettings s2 = newItem.getVehicleSettings();

            if (v1 == null || v2 == null) return false;

            // Check if settings contents match (handling potential nulls safely)
            boolean areSettingsSame;
            if (s1 == null && s2 == null) {
                areSettingsSame = true;
            } else if (s1 == null || s2 == null) {
                areSettingsSame = false;
            } else {
                areSettingsSame = s1.isStartEngineEnabled() == s2.isStartEngineEnabled() &&
                        s1.isStopEngineEnabled() == s2.isStopEngineEnabled();
            }

            return v1.isConnected() == v2.isConnected() &&
                    v1.isBusy() == v2.isBusy() &&
                    v1.isDefault() == v2.isDefault() &&
                    Objects.equals(v1.getName(), v2.getName()) &&
                    Objects.equals(v1.getVoltage(), v2.getVoltage()) &&
                    Objects.equals(v1.getStatusText(), v2.getStatusText()) &&
                    areSettingsSame;
        }
    };

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vehicle_control, parent, false);
        return new VehicleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            VehicleWithSettings item = getItem(position);
            if (item.getVehicle() != null) {
                for (Object payload : payloads) {
                    if (PAYLOAD_VOLTAGE.equals(payload)) {
                        holder.updateVoltage(item.getVehicle().getVoltage());
                    }
                }
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        VehicleWithSettings item = getItem(position);
        holder.bind(item.getVehicle(), item.getVehicleSettings(), listener, position);
    }

    public void triggerCooldown(Vehicle vehicle, long cooldownMillis) {
        if (vehicle == null) return;

        List<VehicleWithSettings> currentList = getCurrentList();
        int position = -1;
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).getVehicle() != null &&
                    currentList.get(i).getVehicle().getId().equals(vehicle.getId())) {
                position = i;
                break;
            }
        }
        if (position == -1) return;

        vehicle.setBusy(true);
        notifyItemChanged(position);

        final String targetVehicleId = vehicle.getId();
        handler.postDelayed(() -> {
            List<VehicleWithSettings> latestList = getCurrentList();
            int currentPosition = -1;
            for (int i = 0; i < latestList.size(); i++) {
                if (latestList.get(i).getVehicle() != null &&
                        latestList.get(i).getVehicle().getId().equals(targetVehicleId)) {
                    currentPosition = i;
                    break;
                }
            }
            if (currentPosition != -1) {
                latestList.get(currentPosition).getVehicle().setBusy(false);
                notifyItemChanged(currentPosition);
            }
        }, cooldownMillis);
    }

    public void updateVehicleConnection(String vehicleId, boolean isConnected, String statusText) {
        List<VehicleWithSettings> currentList = getCurrentList();
        for (int i = 0; i < currentList.size(); i++) {
            Vehicle vehicle = currentList.get(i).getVehicle();
            if (vehicle != null && vehicle.getId().equals(vehicleId)) {
                vehicle.setConnected(isConnected);
                vehicle.setStatusText(statusText);
                notifyItemChanged(i);
                break;
            }
        }
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        private final GlowCardView cardVehicleItem;
        private final TextView txtVehicleName, txtStatus, txtVoltageValue, txtActiveBadge;
        private final View statusIndicator, viewStatusBar;
        private final ViewGroup statusBarParent;
        private final ImageButton btnReconnect;
        private final Button btnStart, btnStop;
        private final View panelVoltage;

        private ValueAnimator activeAnimator;
        private final List<Integer> rssiHistory = new ArrayList<>();
        private float currentAnimatedWidth = 0f;

        public VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            cardVehicleItem = (GlowCardView) itemView;
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

            ConnectionState state = (connectionState != null && connectionState.getState() != null)
                    ? connectionState.getState()
                    : ConnectionState.DISCONNECTED;

            int liveRssi = (connectionState != null) ? connectionState.getRssi() : -999;

            if (state == ConnectionState.DISCONNECTED || state == ConnectionState.SCANNING) {
                rssiHistory.clear();
                currentAnimatedWidth = 0f;
            }

            updateConnectionState(vehicle, state, liveRssi);

            boolean isActive = vehicle.isDefault();
            txtActiveBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
            cardVehicleItem.setGlowActive(isActive && isConnected);

            boolean isStartEnabledSetting = settings != null && settings.isStartEngineEnabled();
            boolean isStopEnabledSetting = settings != null && settings.isStopEngineEnabled();
            boolean canClick = isConnected && !isBusy;

            btnStart.setVisibility(isStartEnabledSetting ? View.VISIBLE : View.GONE);
            if (isStartEnabledSetting) {
                UiUtils.setButtonState(btnStart, canClick, canClick ? 1.0f : 0.5f);
            }

            btnStop.setVisibility(isStopEnabledSetting ? View.VISIBLE : View.GONE);
            if (isStopEnabledSetting) {
                UiUtils.setButtonState(btnStop, canClick, canClick ? 1.0f : 0.5f);
            }

            txtVoltageValue.setText(vehicle.getVoltage());

            btnStart.setOnClickListener(v -> { if (listener != null) listener.onStartClick(vehicle, position); });
            btnStop.setOnClickListener(v -> { if (listener != null) listener.onStopClick(vehicle, position); });
            btnReconnect.setOnClickListener(v -> { if (listener != null) listener.onReconnectClick(vehicle, position); });
            panelVoltage.setOnClickListener(v -> { if (listener != null) listener.onVoltageClick(vehicle, position); });
        }

        private CharSequence getStatusText(String statusText, ConnectionStateRepository.DeviceConnectionState state,
                                           long lastSeenTime) {
            if (lastSeenTime > 0 && state != null && lastSeenEligibleStatusList.contains(state.getState())) {
                return statusText + " • last seen " + UiUtils.formatRelativeTime(lastSeenTime);
            }
            return statusText;
        }

        public void updateConnectionState(Vehicle vehicle, ConnectionState state, int rssiValue) {
            viewStatusBar.animate().cancel();
            if (activeAnimator != null) {
                activeAnimator.cancel();
                activeAnimator = null;
            }

            int parentWidth = statusBarParent.getWidth();
            if (parentWidth == 0) {
                statusBarParent.post(() -> updateConnectionState(vehicle, state, rssiValue));
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
                    animateBarWidth(startWidth, parentWidth, 300, colorRes);
                    break;

                case DISCONNECTED:
                    colorRes = R.color.status_disconnected;
                    animateBarWidth(startWidth, 0, 800, colorRes);
                    break;

                case NEARBY_FAR_AWAY:
                    colorRes = R.color.status_nearby;
                    viewStatusBar.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), colorRes));

                    int smoothWidth = VehicleDistanceManager.getInstance()
                            .getSmoothedWidth(vehicle.getMacAddress(), rssiValue, parentWidth);

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

        private void animateBarWidth(int startWidth, int endWidth, long duration, int colorRes) {
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

        public void updateVoltage(String voltage) {
            txtVoltageValue.setText(voltage);
        }
    }
}