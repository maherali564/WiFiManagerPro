package com.yourapp.wifimanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecification;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.util.Log;

import com.yourapp.wifimanager.models.WifiNetwork;

import java.util.ArrayList;
import java.util.Collections;
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

        // API 31+: use WifiNetworkSpecification + ConnectivityManager.requestNetwork (forces immediate connect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return connectToNetworkApi31(ssid, password);
        }

        // API 29-30: add suggestion (system may auto-connect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return connectToNetworkSuggestion(ssid, password);
        }

        // Legacy (API < 29): addNetwork + enableNetwork + reconnect
        return connectToNetworkLegacy(ssid, password);
    }

    // API 31+: forces immediate connection via WifiNetworkSpecification + ConnectivityManager
    private boolean connectToNetworkApi31(String ssid, String password) {
        try {
            // Also add a suggestion so the system remembers the network
            addSuggestion(ssid, password);

            WifiNetworkSpecification.Builder specBuilder = new WifiNetworkSpecification.Builder()
                    .setSsid(ssid);

            if (password != null && !password.isEmpty()) {
                specBuilder.setWpa2Passphrase(password);
            }

            WifiNetworkSpecification spec = specBuilder.build();
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(spec)
                    .build();

            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return false;

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] connected = {false};

            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.i(TAG, "Connected to " + ssid + " via requestNetwork");
                    connected[0] = true;
                    latch.countDown();
                }

                @Override
                public void onUnavailable() {
                    Log.w(TAG, "Network " + ssid + " unavailable");
                    latch.countDown();
                }
            };

            connectivityManager.requestNetwork(request, callback);
            boolean awaitResult = latch.await(15, TimeUnit.SECONDS);
            connectivityManager.unregisterNetworkCallback(callback);

            if (connected[0]) {
                Log.i(TAG, "Successfully connected to: " + ssid);
                return true;
            }

            // Fallback: try suggestion-only (might still work)
            Log.w(TAG, "requestNetwork did not connect, trying suggestion fallback");
            return true; // suggestion was already added
        } catch (Exception e) {
            Log.e(TAG, "connectToNetworkApi31 error: " + e.getMessage());
            // Last resort fallback
            addSuggestion(ssid, password);
            return true;
        }
    }

    // API 29-30: use WifiNetworkSuggestion (passive, system decides when to connect)
    private boolean connectToNetworkSuggestion(String ssid, String password) {
        Log.i(TAG, "Adding network suggestion for: " + ssid);

        WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder()
                .setSsid(ssid);

        if (password != null && !password.isEmpty()) {
            builder.setWpa2Passphrase(password);
        }

        WifiNetworkSuggestion suggestion = builder.build();
        List<WifiNetworkSuggestion> suggestionsList = Collections.singletonList(suggestion);

        int status = wifiManager.addNetworkSuggestions(suggestionsList);
        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Log.i(TAG, "Suggestion added for: " + ssid);
            wifiManager.disconnect();
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            return true;
        } else {
            Log.w(TAG, "addNetworkSuggestions failed with status: " + status);
            return connectToNetworkLegacy(ssid, password);
        }
    }

    // API 29+: add WifiNetworkSuggestion so the system remembers the network
    private void addSuggestion(String ssid, String password) {
        try {
            WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder()
                    .setSsid(ssid);
            if (password != null && !password.isEmpty()) {
                builder.setWpa2Passphrase(password);
            }
            wifiManager.addNetworkSuggestions(Collections.singletonList(builder.build()));
        } catch (Exception e) {
            Log.w(TAG, "addSuggestion error: " + e.getMessage());
        }
    }

    private boolean connectToNetworkLegacy(String ssid, String password) {
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
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
        // API 33+: remove from suggestions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<WifiNetworkSuggestion> suggestions = Collections.singletonList(
                    new WifiNetworkSuggestion.Builder().setSsid(ssid).build()
            );
            int status = wifiManager.removeNetworkSuggestions(suggestions);
            Log.i(TAG, "removeNetworkSuggestions for " + ssid + " status=" + status);
        }

        // Try legacy removeNetwork (may work on some devices)
        wifiManager.disconnect();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        List<WifiConfiguration> savedNetworks = wifiManager.getConfiguredNetworks();
        if (savedNetworks != null) {
            for (WifiConfiguration config : savedNetworks) {
                if (cleanSSID(config.SSID).equals(ssid)) {
                    wifiManager.removeNetwork(config.networkId);
                }
            }
        }

        wifiManager.disconnect();
        return true;
    }

    public boolean forgetCurrentNetwork() {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String currentSSID = wifiInfo != null ? cleanSSID(wifiInfo.getSSID()) : null;

        if (currentSSID != null) {
            forgetNetwork(currentSSID);
        } else {
            wifiManager.disconnect();
        }
        return true;
    }

    public WifiInfo getCurrentNetwork() {
        return wifiManager.getConnectionInfo();
    }

    public boolean isWifiEnabled() {
        return wifiManager.isWifiEnabled();
    }

    public String getCurrentSSID() {
        WifiInfo info = wifiManager.getConnectionInfo();
        if (info == null) return null;
        return cleanSSID(info.getSSID());
    }

    public Network getWifiNetwork() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;

        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network;
            }
        }
        return null;
    }

    private String cleanSSID(String ssid) {
        if (ssid == null) return null;
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }
}
