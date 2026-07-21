package in.kvapps.wirelessstart.wear;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String START_PATH = "/dio/engine_start";
    private static final String STOP_PATH = "/dio/engine_stop";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btn_wear_start);
        Button btnStop = findViewById(R.id.btn_wear_stop);

        btnStart.setOnClickListener(v -> transmitActionToPhone(START_PATH, "Cranking Engine..."));
        btnStop.setOnClickListener(v -> transmitActionToPhone(STOP_PATH, "Killing Engine..."));
    }

    private void transmitActionToPhone(String targetPath, String promptText) {
        Task<CapabilityInfo> lookupTask = Wearable.getCapabilityClient(this)
                .getCapability("dio_phone_app", CapabilityClient.FILTER_REACHABLE);

        lookupTask.addOnSuccessListener(capabilityInfo -> {
            Set<Node> availableNodes = capabilityInfo.getNodes();

            if (availableNodes.isEmpty()) {
                Toast.makeText(MainActivity.this, "Error: Phone Gateway Offline", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Node communicationNode : availableNodes) {
                Wearable.getMessageClient(MainActivity.this)
                        .sendMessage(communicationNode.getId(), targetPath, new byte[0])
                        .addOnSuccessListener(aVoid ->
                                Toast.makeText(MainActivity.this, promptText, Toast.LENGTH_SHORT).show());
            }
        });

        lookupTask.addOnFailureListener(e ->
                Toast.makeText(MainActivity.this, "Pipeline Connection Failure", Toast.LENGTH_SHORT).show());
    }
}
