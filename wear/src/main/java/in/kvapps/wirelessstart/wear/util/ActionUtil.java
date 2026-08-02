package in.kvapps.wirelessstart.wear.util;

import android.content.Context;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import java.util.Set;

import in.kvapps.wirelessstart.shared.Constants;

public class ActionUtil {
    public static final String START_PATH = Constants.START_PATH;
    public static final String STOP_PATH = Constants.STOP_PATH;

    public static void transmitActionToPhone(Context context, String targetPath, String promptText) {
        Task<CapabilityInfo> lookupTask = Wearable.getCapabilityClient(context)
                .getCapability("dio_phone_app", CapabilityClient.FILTER_REACHABLE);

        lookupTask.addOnSuccessListener(capabilityInfo -> {
            Set<Node> availableNodes = capabilityInfo.getNodes();

            if (availableNodes.isEmpty()) {
                Toast.makeText(context, "Error: Phone Gateway Offline", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Node communicationNode : availableNodes) {
                Wearable.getMessageClient(context)
                        .sendMessage(communicationNode.getId(), targetPath, new byte[0])
                        .addOnSuccessListener(aVoid ->
                                Toast.makeText(context, promptText, Toast.LENGTH_SHORT).show());
            }
        });

        lookupTask.addOnFailureListener(e ->
                Toast.makeText(context, "Pipeline Connection Failure", Toast.LENGTH_SHORT).show());
    }
}
