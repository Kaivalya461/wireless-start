package in.kvapps.wirelessstart.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.model.CommandLogEntity;
import in.kvapps.wirelessstart.shared.Constants;

public class CommandLogAdapter extends RecyclerView.Adapter<CommandLogAdapter.ViewHolder> {
    private List<CommandLogEntity> logs = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public void setLogs(List<CommandLogEntity> logs) {
        this.logs = logs != null ? logs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_command_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommandLogEntity log = logs.get(position);
        String command = log.getCommand();
        Context context = holder.itemView.getContext(); // Safe context retrieval

        holder.txtCommand.setText(command);
        holder.txtTimestamp.setText(dateFormat.format(new Date(log.getTimestamp())));
        holder.txtTimeTaken.setText(log.getTimeTaken() + " ms");

        // Color-code Command text using colors.xml
        if (command != null) {
            String upperCmd = command.toUpperCase();
            if (upperCmd.startsWith("START") || upperCmd.startsWith("UNLOCK")) {
                holder.txtCommand.setTextColor(ContextCompat.getColor(context, R.color.primary_action_color));
            } else if (upperCmd.startsWith("STOP") || upperCmd.startsWith("LOCK")) {
                holder.txtCommand.setTextColor(ContextCompat.getColor(context, R.color.secondary_action_color));
            } else {
                // Fallback white
                holder.txtCommand.setTextColor(ContextCompat.getColor(context, android.R.color.white));
            }
        }

        // Color-code Result text
        holder.txtResult.setText(log.getResult());
        if (Constants.COMMAND_SUCCESS.equalsIgnoreCase(log.getResult())) {
            // Uses colors.xml -> @color/command_success (Green)
            holder.txtResult.setTextColor(ContextCompat.getColor(context, R.color.soft_gold));
        } else {
            // Uses colors.xml -> @color/command_error (Red)
            holder.txtResult.setTextColor(ContextCompat.getColor(context, R.color.soft_red));
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtCommand, txtTimestamp, txtResult, txtTimeTaken;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCommand = itemView.findViewById(R.id.txt_command);
            txtTimestamp = itemView.findViewById(R.id.txt_timestamp);
            txtResult = itemView.findViewById(R.id.txt_result);
            txtTimeTaken = itemView.findViewById(R.id.txt_time_taken);
        }
    }
}