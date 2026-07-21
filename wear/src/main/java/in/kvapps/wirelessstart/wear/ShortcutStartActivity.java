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

        // 1. Fire the engine start handshake to the phone
        ActionUtil.transmitActionToPhone(this, ActionUtil.START_PATH, "Cranking Engine...");

        // 2. Kill this activity instantly so the user never sees an interface
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
    }
}
