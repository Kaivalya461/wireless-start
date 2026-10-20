package in.kvapps.wirelessstart.adapter;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.model.Vehicle;
import in.kvapps.wirelessstart.util.UiUtils;

public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder> {

    private final List<Vehicle> vehicleList;
    private final VehicleActionListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface VehicleActionListener {
        void onStartClick(Vehicle vehicle, int position);
        void onStopClick(Vehicle vehicle, int position);
        void onReconnectClick(Vehicle vehicle, int position);
        void onVoltageClick(Vehicle vehicle, int position);
    }

    public VehicleAdapter(List<Vehicle> vehicleList, VehicleActionListener listener) {
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
        Vehicle vehicle = vehicleList.get(position);
        holder.bind(vehicle, listener, position);
    }

    @Override
    public int getItemCount() {
        return vehicleList.size();
    }

    /**
     * Helper to set a vehicle into a busy/cooldown state using the Vehicle object
     * and automatically restore it after delay.
     */
    public void triggerCooldown(Vehicle vehicle, long cooldownMillis) {
        if (vehicle == null) return;

        // Find the current position of the vehicle in the list
        int position = vehicleList.indexOf(vehicle);
        if (position == -1) return; // Vehicle is no longer in the list

        vehicle.setBusy(true);
        notifyItemChanged(position);

        handler.postDelayed(() -> {
            // Re-check the position in case the list changed while waiting
            int currentPosition = vehicleList.indexOf(vehicle);
            if (currentPosition != -1) {
                vehicle.setBusy(false);
                notifyItemChanged(currentPosition);
            }
        }, cooldownMillis);
    }

    public void updateVehicleConnection(String vehicleId, boolean isConnected, String statusText) {
        if (vehicleId == null) return;

        for (int i = 0; i < vehicleList.size(); i++) {
            Vehicle vehicle = vehicleList.get(i);
            if (vehicle.getId().equals(vehicleId)) {
                // Update the properties on the exact object inside the adapter
                vehicle.setConnected(isConnected);
                vehicle.setStatusText(statusText);

                // Redraw only this specific row
                notifyItemChanged(i);
                break;
            }
        }
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtVehicleName;
        private final View statusIndicator;
        private final TextView txtStatus;
        private final ImageButton btnReconnect;
        private final Button btnStart;
        private final Button btnStop;
        private final View panelVoltage;
        private final TextView txtVoltageValue;

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
        }

        public void bind(Vehicle vehicle, VehicleActionListener listener, int position) {
            txtVehicleName.setText(vehicle.getName() != null ? vehicle.getName() : "Unit #" + vehicle.getId());

            boolean isConnected = vehicle.isConnected();
            boolean isBusy = vehicle.isBusy();

            txtStatus.setText(vehicle.getStatusText().toUpperCase());

            int indicatorRes = isConnected ? R.drawable.indicator_online : R.drawable.indicator_offline;
            statusIndicator.setBackgroundResource(indicatorRes);

            btnReconnect.setVisibility(isConnected ? View.GONE : View.VISIBLE);

            // Buttons are enabled ONLY if connected AND not currently busy
            boolean canClick = isConnected && !isBusy;
            UiUtils.setButtonState(btnStart, canClick, canClick ? 1.0f : 0.5f);
            UiUtils.setButtonState(btnStop, canClick, canClick ? 1.0f : 0.5f);

            txtVoltageValue.setText(vehicle.getVoltage());

            // Click Listeners
            btnStart.setOnClickListener(v -> {
                if (listener != null) listener.onStartClick(vehicle, position);
            });

            btnStop.setOnClickListener(v -> {
                if (listener != null) listener.onStopClick(vehicle, position);
            });

            btnReconnect.setOnClickListener(v -> {
                if (listener != null) listener.onReconnectClick(vehicle, position);
            });

            panelVoltage.setOnClickListener(v -> {
                if (listener != null) listener.onVoltageClick(vehicle, position);
            });
        }
    }
}