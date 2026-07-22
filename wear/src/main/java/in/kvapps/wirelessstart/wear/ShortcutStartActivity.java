package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import in.kvapps.wirelessstart.wear.util.ActionUtil;

public class ShortcutStartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fire the start request path to phone
        ActionUtil.transmitActionToPhone(this, ActionUtil.START_PATH, "Cranking Engine...");

        // Close instantly after handshake transmission
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1200);
    }
}