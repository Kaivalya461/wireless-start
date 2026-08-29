package in.kvapps.wirelessstart.wear.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.util.Set;

import in.kvapps.wirelessstart.shared.Constants;

public class ActionUtil {
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

    public static void transmitScheduleToPhone(Context context, long epochSeconds) {
        // 1. Create the request builder instance
        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(Constants.ENGINE_SCHEDULE_PATH);

        // 2. Insert your payload data into the data map
        dataMapRequest.getDataMap().putLong(Constants.KEY_SCHEDULE_EPOCH, epochSeconds);

        // 3. Generate the actual PutDataRequest from the builder instance
        PutDataRequest request = dataMapRequest.asPutDataRequest();

        // Optional: Mark as urgent if you need immediate delivery sync
        request.setUrgent();

        Log.i("ActionUtil", "transmitScheduleToPhone triggered");
        // 4. Submit it to the Wearable DataClient API
        Wearable.getDataClient(context).putDataItem(request)
                .addOnSuccessListener(dataItem ->
                        Log.d("ActionUtil", "Schedule successfully synced to Data Layer ->  epochSeconds: " + epochSeconds)
                )
                .addOnFailureListener(e ->
                        Log.e("ActionUtil", "Failed to sync schedule to Data Layer", e)
                );
    }
}
