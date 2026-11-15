package in.kvapps.wirelessstart;

import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import in.kvapps.wirelessstart.adapter.CommandLogAdapter;
import in.kvapps.wirelessstart.viewmodel.VehicleCommandLogViewModel;
import in.kvapps.wirelessstart.viewmodel.VehicleCommandLogViewModelFactory;

public class VehicleCommandLogActivity extends AppCompatActivity {
    public static final String EXTRA_MAC_ADDRESS = "extra_mac_address";
    private String macAddress;
    private VehicleCommandLogViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_command_log);

        macAddress = getIntent().getStringExtra(EXTRA_MAC_ADDRESS);
        if (macAddress == null || macAddress.isEmpty()) {
            finish();
            return;
        }

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Command History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Setup RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recycler_logs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        CommandLogAdapter adapter = new CommandLogAdapter();
        recyclerView.setAdapter(adapter);

        // Setup ViewModel with Factory
        VehicleCommandLogViewModelFactory factory = new VehicleCommandLogViewModelFactory(getApplication(), macAddress);
        viewModel = new ViewModelProvider(this, factory).get(VehicleCommandLogViewModel.class);

        viewModel.getLogList().observe(this, adapter::setLogs);

        // Clear Logs Button
        Button btnClear = findViewById(R.id.btn_clear_logs);
        btnClear.setOnClickListener(v -> viewModel.clearLogs());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}