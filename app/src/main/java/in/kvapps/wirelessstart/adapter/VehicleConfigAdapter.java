package in.kvapps.wirelessstart.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.model.Vehicle;

public class VehicleConfigAdapter extends RecyclerView.Adapter<VehicleConfigAdapter.VehicleViewHolder> {

    private List<Vehicle> vehicleList;
    private String activeVehicleId;
    private final OnVehicleActionListener listener;

    public interface OnVehicleActionListener {
        void onSelect(Vehicle vehicle);
        void onDelete(Vehicle vehicle);
    }

    public VehicleConfigAdapter(List<Vehicle> vehicleList, String activeVehicleId, OnVehicleActionListener listener) {
        this.vehicleList = vehicleList;
        this.activeVehicleId = activeVehicleId;
        this.listener = listener;
    }

    public void updateData(List<Vehicle> newVehicles, String activeId) {
        this.vehicleList = newVehicles;
        this.activeVehicleId = activeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vehicle_config, parent, false);
        return new VehicleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        Vehicle vehicle = vehicleList.get(position);
        holder.tvName.setText(vehicle.getName());
        holder.tvMac.setText(vehicle.getMacAddress());

        boolean isActive = vehicle.getId().equals(activeVehicleId);
        if (isActive) {
            holder.btnSelect.setText("Active");
            holder.btnSelect.setEnabled(false);
            holder.btnSelect.setTextColor(0xFF4CAF50); // Green color for active state
            holder.btnSelect.setStrokeColor(ColorStateList.valueOf(0xFF4CAF50));
        } else {
            holder.btnSelect.setText("Select");
            holder.btnSelect.setEnabled(true);
            holder.btnSelect.setTextColor(0xFF4A90E2); // Accent blue color
            holder.btnSelect.setStrokeColor(ColorStateList.valueOf(0xFF4A90E2));
        }

        holder.btnSelect.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect(vehicle);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(vehicle);
            }
        });
    }

    @Override
    public int getItemCount() {
        return vehicleList != null ? vehicleList.size() : 0;
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvMac;
        MaterialButton btnSelect;
        ImageButton btnDelete;

        public VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_vehicle_name);
            tvMac = itemView.findViewById(R.id.tv_vehicle_mac);
            btnSelect = itemView.findViewById(R.id.btn_select_vehicle);
            btnDelete = itemView.findViewById(R.id.btn_delete_vehicle);
        }
    }
}
