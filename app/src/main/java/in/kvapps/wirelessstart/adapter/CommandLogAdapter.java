package in.kvapps.wirelessstart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import in.kvapps.wirelessstart.R;
import in.kvapps.wirelessstart.model.CommandLogEntity;
import in.kvapps.wirelessstart.shared.Constants;

public class CommandLogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final List<Object> displayItems = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dayHeaderFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

    public void setLogs(List<CommandLogEntity> logs) {
        displayItems.clear();
        if (logs == null || logs.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        long now = System.currentTimeMillis();
        long twoHoursAgo = now - (2 * 60 * 60 * 1000L);

        List<CommandLogEntity> last2HoursList = new ArrayList<>();
        List<CommandLogEntity> earlierTodayList = new ArrayList<>();
        Map<String, List<CommandLogEntity>> olderDaysMap = new LinkedHashMap<>();

        Calendar todayCalendar = Calendar.getInstance();
        todayCalendar.setTimeInMillis(now);
        int todayDayOfYear = todayCalendar.get(Calendar.DAY_OF_YEAR);
        int todayYear = todayCalendar.get(Calendar.YEAR);

        Calendar yesterdayCalendar = Calendar.getInstance();
        yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayKey = dateKeyFormat.format(yesterdayCalendar.getTime());

        Calendar logCalendar = Calendar.getInstance();

        for (CommandLogEntity log : logs) {
            long timestamp = log.getTimestamp();
            logCalendar.setTimeInMillis(timestamp);

            if (timestamp >= twoHoursAgo) {
                last2HoursList.add(log);
            } else if (logCalendar.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && logCalendar.get(Calendar.YEAR) == todayYear) {
                earlierTodayList.add(log);
            } else {
                String logDayKey = dateKeyFormat.format(logCalendar.getTime());
                String headerKey;
                if (logDayKey.equals(yesterdayKey)) {
                    headerKey = "Yesterday";
                } else {
                    headerKey = dayHeaderFormat.format(new Date(timestamp));
                }

                if (!olderDaysMap.containsKey(headerKey)) {
                    olderDaysMap.put(headerKey, new ArrayList<>());
                }
                olderDaysMap.get(headerKey).add(log);
            }
        }

        // Build flattened display items list with headers
        if (!last2HoursList.isEmpty()) {
            displayItems.add("Last 2 Hours");
            displayItems.addAll(last2HoursList);
        }
        if (!earlierTodayList.isEmpty()) {
            displayItems.add("Earlier Today");
            displayItems.addAll(earlierTodayList);
        }
        for (Map.Entry<String, List<CommandLogEntity>> entry : olderDaysMap.entrySet()) {
            displayItems.add(entry.getKey());
            displayItems.addAll(entry.getValue());
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (displayItems.get(position) instanceof String) {
            return VIEW_TYPE_HEADER;
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_command_log_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_command_log, parent, false);
            return new LogViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_HEADER) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            String title = (String) displayItems.get(position);
            headerHolder.txtHeaderTitle.setText(title);
        } else {
            LogViewHolder logHolder = (LogViewHolder) holder;
            CommandLogEntity log = (CommandLogEntity) displayItems.get(position);
            String command = log.getCommand();
            Context context = logHolder.itemView.getContext();

            logHolder.txtCommand.setText(command);
            logHolder.txtTimestamp.setText(dateFormat.format(new Date(log.getTimestamp())));
            logHolder.txtTimeTaken.setText(log.getTimeTaken() + " ms");

            // Color-code Command text
            if (command != null) {
                String upperCmd = command.toUpperCase();
                if (upperCmd.startsWith("START") || upperCmd.startsWith("UNLOCK")) {
                    logHolder.txtCommand.setTextColor(ContextCompat.getColor(context, R.color.primary_action_color));
                } else if (upperCmd.startsWith("STOP") || upperCmd.startsWith("LOCK")) {
                    logHolder.txtCommand.setTextColor(ContextCompat.getColor(context, R.color.secondary_action_color));
                } else {
                    logHolder.txtCommand.setTextColor(ContextCompat.getColor(context, android.R.color.white));
                }
            }

            // Color-code Result text
            logHolder.txtResult.setText(log.getResult());
            if (Constants.COMMAND_SUCCESS.equalsIgnoreCase(log.getResult())) {
                logHolder.txtResult.setTextColor(ContextCompat.getColor(context, R.color.soft_gold));
            } else {
                logHolder.txtResult.setTextColor(ContextCompat.getColor(context, R.color.soft_red));
            }
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView txtHeaderTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtHeaderTitle = (TextView) itemView;
        }
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView txtCommand, txtTimestamp, txtResult, txtTimeTaken;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCommand = itemView.findViewById(R.id.txt_command);
            txtTimestamp = itemView.findViewById(R.id.txt_timestamp);
            txtResult = itemView.findViewById(R.id.txt_result);
            txtTimeTaken = itemView.findViewById(R.id.txt_time_taken);
        }
    }
}