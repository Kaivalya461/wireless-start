package in.kvapps.wirelessstart.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import in.kvapps.wirelessstart.model.CommandLogEntity;
import in.kvapps.wirelessstart.repository.CommandLogsRepository;

public class VehicleCommandLogViewModel extends AndroidViewModel {
    private final LiveData<List<CommandLogEntity>> logList;
    private final CommandLogsRepository repository;
    private final String macAddress;

    public VehicleCommandLogViewModel(@NonNull Application application, String macAddress) {
        super(application);
        this.macAddress = macAddress;
        this.repository = CommandLogsRepository.getInstance(application);
        this.logList = repository.getLogsForVehicle(macAddress);
    }

    public LiveData<List<CommandLogEntity>> getLogList() {
        return logList;
    }

    public void clearLogs() {
        repository.clearLogsForVehicle(macAddress);
    }
}