package com.yourapp.wifimanager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.yourapp.wifimanager.adapters.NetworkAdapter;
import com.yourapp.wifimanager.models.WifiNetwork;
import com.yourapp.wifimanager.services.AutoReconnectWorker;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private ListView listViewNetworks;
    private TextView tvCurrentNetwork, tvStatus;
    private EditText etInterval, etPassword;
    private Spinner spinnerUnit;
    private Button btnStartService, btnStopService, btnRefresh;
    private Button btnForgetCurrent, btnBypassPortal;

    private NetworkManager networkManager;
    private CaptivePortalDetector portalDetector;
    private List<WifiNetwork> networks = new ArrayList<>();
    private NetworkAdapter adapter;
    private String selectedSSID = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        networkManager = new NetworkManager(this);
        portalDetector = new CaptivePortalDetector(this);

        requestPermissions();
        setupListeners();
        refreshNetworks();
    }

    private void initViews() {
        listViewNetworks = findViewById(R.id.listViewNetworks);
        tvCurrentNetwork = findViewById(R.id.tvCurrentNetwork);
        tvStatus = findViewById(R.id.tvStatus);
        etInterval = findViewById(R.id.etInterval);
        etPassword = findViewById(R.id.etPassword);
        spinnerUnit = findViewById(R.id.spinnerUnit);
        btnStartService = findViewById(R.id.btnStartService);
        btnStopService = findViewById(R.id.btnStopService);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnForgetCurrent = findViewById(R.id.btnForgetCurrent);
        btnBypassPortal = findViewById(R.id.btnBypassPortal);

        btnStopService.setEnabled(false);
        etInterval.setText("30");

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.time_units, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnit.setAdapter(spinnerAdapter);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> refreshNetworks());
        btnStartService.setOnClickListener(v -> startService());
        btnStopService.setOnClickListener(v -> stopService());
        btnForgetCurrent.setOnClickListener(v -> forgetCurrentNetwork());
        btnBypassPortal.setOnClickListener(v -> testConnection());

        listViewNetworks.setOnItemClickListener((parent, view, position, id) -> {
            if (position < networks.size()) {
                selectedSSID = networks.get(position).getSsid();
                showNetworkOptions(selectedSSID);
            }
        });
    }

    private void refreshNetworks() {
        tvStatus.setText(getString(R.string.status_loading));

        new Handler().postDelayed(() -> {
            networks = networkManager.getAvailableNetworks();
            adapter = new NetworkAdapter(this, networks);
            listViewNetworks.setAdapter(adapter);

            String currentSSID = networkManager.getCurrentNetwork() != null ?
                    networkManager.getCurrentNetwork().getSSID() : null;
            if (currentSSID != null && currentSSID.startsWith("\"") && currentSSID.endsWith("\"")) {
                currentSSID = currentSSID.substring(1, currentSSID.length() - 1);
            }
            tvCurrentNetwork.setText(getString(R.string.current_network) + ": " +
                    (currentSSID != null ? currentSSID : getString(R.string.not_connected)));
            tvStatus.setText(getString(R.string.status_refreshed));
        }, 500);
    }

    private void showNetworkOptions(String ssid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.network_options) + ": " + ssid);

        String[] options = {
            getString(R.string.option_connect),
            getString(R.string.option_forget),
            getString(R.string.option_auto)
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    showPasswordDialog(ssid);
                    break;
                case 1:
                    confirmForget(ssid);
                    break;
                case 2:
                    selectedSSID = ssid;
                    startService();
                    break;
            }
        });
        builder.show();
    }

    private void showPasswordDialog(String ssid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_password_title) + " " + ssid);

        final EditText input = new EditText(this);
        input.setHint(getString(R.string.dialog_password_hint));
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.dialog_connect), (dialog, which) -> {
            String password = input.getText().toString().trim();
            boolean connected = networkManager.connectToNetwork(ssid, password);
            if (connected) {
                Toast.makeText(this, String.format(getString(R.string.msg_connected), ssid), Toast.LENGTH_SHORT).show();
                etPassword.setText(password);
                refreshNetworks();
            } else {
                Toast.makeText(this, getString(R.string.msg_connection_failed), Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), null);
        builder.show();
    }

    private void confirmForget(String ssid) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_forget_title))
                .setMessage(String.format(getString(R.string.dialog_forget_message), ssid))
                .setPositiveButton(getString(R.string.dialog_forget_confirm), (dialog, which) -> {
                    boolean forgotten = networkManager.forgetNetwork(ssid);
                    if (forgotten) {
                        Toast.makeText(this, getString(R.string.msg_forgotten), Toast.LENGTH_SHORT).show();
                        refreshNetworks();
                    } else {
                        Toast.makeText(this, getString(R.string.msg_forget_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void forgetCurrentNetwork() {
        if (networkManager.getCurrentNetwork() == null) {
            Toast.makeText(this, "لا توجد شبكة متصلة", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean forgotten = networkManager.forgetCurrentNetwork();
        if (forgotten) {
            Toast.makeText(this, getString(R.string.msg_forgotten), Toast.LENGTH_SHORT).show();
            refreshNetworks();
        } else {
            Toast.makeText(this, getString(R.string.msg_forget_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void testConnection() {
        boolean hasInternet = portalDetector.hasInternetAccess();
        if (hasInternet) {
            Toast.makeText(this, "✅ الإنترنت متصل", Toast.LENGTH_SHORT).show();
            tvStatus.setText("✅ الإنترنت متصل");
            return;
        }
        boolean bypassed = portalDetector.bypassCaptivePortal();
        if (bypassed) {
            Toast.makeText(this, getString(R.string.msg_bypass_success), Toast.LENGTH_SHORT).show();
            tvStatus.setText(getString(R.string.msg_bypass_success));
        } else {
            Toast.makeText(this, getString(R.string.msg_bypass_failed), Toast.LENGTH_SHORT).show();
            tvStatus.setText(getString(R.string.msg_bypass_failed));
        }
    }

    private void startService() {
        if (selectedSSID == null) {
            Toast.makeText(this, getString(R.string.msg_select_network_first), Toast.LENGTH_LONG).show();
            return;
        }

        String intervalStr = etInterval.getText().toString().trim();
        if (intervalStr.isEmpty()) {
            etInterval.setError(getString(R.string.msg_enter_interval));
            return;
        }

        int intervalValue = Integer.parseInt(intervalStr);
        if (intervalValue < 1) {
            etInterval.setError(getString(R.string.msg_min_interval));
            return;
        }

        String unit = spinnerUnit.getSelectedItem().toString();
        int intervalSeconds;
        if (unit.equals(getString(R.string.unit_minutes))) {
            intervalSeconds = intervalValue * 60;
        } else if (unit.equals(getString(R.string.unit_hours))) {
            intervalSeconds = intervalValue * 3600;
        } else {
            intervalSeconds = intervalValue;
        }

        if (intervalSeconds < 5) {
            Toast.makeText(this, getString(R.string.msg_min_interval), Toast.LENGTH_SHORT).show();
            return;
        }

        String password = etPassword.getText().toString().trim();

        Intent serviceIntent = new Intent(this, AutoReconnectWorker.class);
        serviceIntent.putExtra("SSID", selectedSSID);
        serviceIntent.putExtra("PASSWORD", password);
        serviceIntent.putExtra("INTERVAL_SECONDS", intervalSeconds);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        btnStartService.setEnabled(false);
        btnStopService.setEnabled(true);

        String unitDisplay = unit;
        tvStatus.setText(getString(R.string.status_running) + ": " + selectedSSID + " | كل " + intervalValue + " " + unitDisplay);
        Toast.makeText(this, String.format(getString(R.string.msg_service_started), selectedSSID, intervalValue, unitDisplay), Toast.LENGTH_LONG).show();
    }

    private void stopService() {
        Intent serviceIntent = new Intent(this, AutoReconnectWorker.class);
        stopService(serviceIntent);

        btnStartService.setEnabled(true);
        btnStopService.setEnabled(false);
        tvStatus.setText(getString(R.string.status_stopped));
        Toast.makeText(this, getString(R.string.msg_service_stopped), Toast.LENGTH_SHORT).show();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        needed.add(Manifest.permission.ACCESS_WIFI_STATE);
        needed.add(Manifest.permission.CHANGE_WIFI_STATE);
        needed.add(Manifest.permission.INTERNET);
        needed.add(Manifest.permission.ACCESS_NETWORK_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        List<String> missing = new ArrayList<>();
        for (String permission : needed) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Toast.makeText(this, "⚠️ الرجاء منح الصلاحية: " + permissions[i], Toast.LENGTH_LONG).show();
                }
            }
            if (allGranted) {
                refreshNetworks();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNetworks();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!btnStartService.isEnabled()) {
            stopService();
        }
    }
}
