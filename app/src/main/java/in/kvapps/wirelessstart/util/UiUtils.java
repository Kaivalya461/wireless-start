package in.kvapps.wirelessstart.util;

import android.view.View;

public class UiUtils {
    public static void setButtonState(View button, boolean isEnabled, float alpha) {
        button.setEnabled(isEnabled);
        button.setAlpha(alpha);
    }
}