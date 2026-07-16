package com.yourapp.wifimanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.yourapp.wifimanager.models.WifiNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private WifiManager wifiManager;
    private Context context;

    public NetworkManager(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean enableWifi() {
        if (!wifiManager.isWifiEnabled()) {
            return wifiManager.setWifiEnabled(true);
        }
        return true;
    }

    public boolean startScan() {
        if (!enableWifi()) return false;
        return wifiManager.startScan();
    }

    public List<WifiNetwork> getAvailableNetworks() {
        List<WifiNetwork> networks = new ArrayList<>();
        if (!enableWifi()) return networks;

        List<ScanResult> scanResults = wifiManager.getScanResults();
        if (scanResults == null || scanResults.isEmpty()) {
            Log.w(TAG, "Scan results empty, triggering new scan...");
            startScan();
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            scanResults = wifiManager.getScanResults();
        }

        if (scanResults == null || scanResults.isEmpty()) {
            Log.e(TAG, "Still no scan results. Check location permission & GPS.");
            return networks;
        }

        WifiInfo connectedWifi = wifiManager.getConnectionInfo();
        String connectedSSID = connectedWifi != null ? cleanSSID(connectedWifi.getSSID()) : null;

        List<WifiConfiguration> savedNetworks = wifiManager.getConfiguredNetworks();
        List<String> savedSSIDs = new ArrayList<>();
        if (savedNetworks != null) {
            for (WifiConfiguration config : savedNetworks) {
                savedSSIDs.add(cleanSSID(config.SSID));
            }
        }

        for (ScanResult result : scanResults) {
            String ssid = result.SSID;
            if (ssid == null || ssid.isEmpty()) continue;

            WifiNetwork network = new WifiNetwork(
                    ssid,
                    result.BSSID,
                    result.level,
                    result.capabilities
            );

            if (connectedSSID != null && connectedSSID.equals(ssid)) {
                network.setConnected(true);
            }

            if (savedSSIDs.contains(ssid)) {
                network.setSaved(true);
            }

            networks.add(network);
        }

        return networks;
    }

    public List<WifiNetwork> scanWithBroadcastReceiver(final Runnable onComplete) {
        final List<WifiNetwork> result = new ArrayList<>();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                result.addAll(parseScanResults());
                if (onComplete != null) onComplete.run();
            }
        };

        context.registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        startScan();

        return result;
    }

    private List<WifiNetwork> parseScanResults() {
        List<WifiNetwork> networks = new ArrayList<>();
        List<ScanResult> scanResults = wifiManager.getScanResults();
        if (scanResults == null) return networks;

        WifiInfo connectedWifi = wifiManager.getConnectionInfo();
        String connectedSSID = connectedWifi != null ? cleanSSID(connectedWifi.getSSID()) : null;

        List<WifiConfiguration> savedNetworks = wifiManager.getConfiguredNetworks();
        List<String> savedSSIDs = new ArrayList<>();
        if (savedNetworks != null) {
            for (WifiConfiguration config : savedNetworks) {
                savedSSIDs.add(cleanSSID(config.SSID));
            }
        }

        for (ScanResult result : scanResults) {
            String ssid = result.SSID;
            if (ssid == null || ssid.isEmpty()) continue;
            WifiNetwork network = new WifiNetwork(ssid, result.BSSID, result.level, result.capabilities);
            if (connectedSSID != null && connectedSSID.equals(ssid)) network.setConnected(true);
            if (savedSSIDs.contains(ssid)) network.setSaved(true);
            networks.add(network);
        }
        return networks;
    }

    public boolean connectToNetwork(String ssid, String password) {
        if (ssid == null || ssid.isEmpty()) return false;

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";

        if (password != null && !password.isEmpty()) {
            config.preSharedKey = "\"" + password + "\"";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        int networkId = wifiManager.addNetwork(config);
        if (networkId == -1) {
            List<WifiConfiguration> saved = wifiManager.getConfiguredNetworks();
            if (saved != null) {
                for (WifiConfiguration savedConfig : saved) {
                    if (cleanSSID(savedConfig.SSID).equals(ssid)) {
                        networkId = savedConfig.networkId;
                        break;
                    }
                }
            }
        }

        if (networkId != -1) {
            wifiManager.disconnect();
            wifiManager.enableNetwork(networkId, true);
            wifiManager.reconnect();
            return true;
        }
        return false;
    }

    public boolean forgetNetwork(String ssid) {
        List<WifiConfiguration> savedNetworks = wifiManager.getConfiguredNetworks();
        if (savedNetworks == null) return false;

        for (WifiConfiguration config : savedNetworks) {
            if (cleanSSID(config.SSID).equals(ssid)) {
                boolean removed = wifiManager.removeNetwork(config.networkId);
                if (removed) {
                    wifiManager.saveConfiguration();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean forgetCurrentNetwork() {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) return false;

        int networkId = wifiInfo.getNetworkId();
        if (networkId == -1) return false;

        boolean removed = wifiManager.removeNetwork(networkId);
        if (removed) {
            wifiManager.saveConfiguration();
            wifiManager.disconnect();
            return true;
        }
        return false;
    }

    public WifiInfo getCurrentNetwork() {
        return wifiManager.getConnectionInfo();
    }

    public boolean isWifiEnabled() {
        return wifiManager.isWifiEnabled();
    }

    private String cleanSSID(String ssid) {
        if (ssid == null) return null;
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }
}
