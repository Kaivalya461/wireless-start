package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

import in.kvapps.wirelessstart.wear.util.ActionUtil;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btn_wear_start);
        Button btnStop = findViewById(R.id.btn_wear_stop);

        btnStart.setOnClickListener(v -> ActionUtil.transmitActionToPhone(
                this, ActionUtil.START_PATH, "Cranking Engine..."));
        btnStop.setOnClickListener(v -> ActionUtil.transmitActionToPhone(
                this, ActionUtil.STOP_PATH, "Killing Engine..."));
    }
}
