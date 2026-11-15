package in.kvapps.wirelessstart.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class VehicleCommandLogViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final String macAddress;

    public VehicleCommandLogViewModelFactory(Application application, String macAddress) {
        this.application = application;
        this.macAddress = macAddress;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(VehicleCommandLogViewModel.class)) {
            return (T) new VehicleCommandLogViewModel(application, macAddress);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}