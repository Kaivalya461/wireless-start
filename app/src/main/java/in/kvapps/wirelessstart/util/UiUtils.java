package in.kvapps.wirelessstart.util;

import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UiUtils {
    public static void setButtonState(View button, boolean isEnabled, float alpha) {
        button.setEnabled(isEnabled);
        button.setAlpha(alpha);
    }

    public static String formatRelativeTimeLastSeen(long timestamp) {
        if (timestamp == 0) return "Last seen: Never";
        long diffMillis = System.currentTimeMillis() - timestamp;
        long minutes = diffMillis / (1000 * 60);
        long hours = minutes / 60;

        if (minutes < 1) return "Last seen: Just now";
        if (minutes < 60) return "Last seen: " + minutes + "m ago";
        if (hours < 24) return "Last seen: " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestamp));
        return "Last seen: " + new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatRelativeTime(long timestamp) {
        if (timestamp == 0) return "Never";
        long diffMillis = System.currentTimeMillis() - timestamp;
        long minutes = diffMillis / (1000 * 60);
        long hours = minutes / 60;

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestamp));
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(timestamp));
    }
}