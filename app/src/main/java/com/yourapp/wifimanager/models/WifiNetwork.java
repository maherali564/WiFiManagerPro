package com.yourapp.wifimanager.models;

public class WifiNetwork {
    private String ssid;
    private String bssid;
    private int signalStrength;
    private String capabilities;
    private boolean isConnected;
    private boolean isSaved;

    public WifiNetwork(String ssid, String bssid, int signalStrength, String capabilities) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.signalStrength = signalStrength;
        this.capabilities = capabilities;
        this.isConnected = false;
        this.isSaved = false;
    }

    public String getSsid() { return ssid; }
    public String getBssid() { return bssid; }
    public int getSignalStrength() { return signalStrength; }
    public String getCapabilities() { return capabilities; }
    public boolean isConnected() { return isConnected; }
    public boolean isSaved() { return isSaved; }

    public void setConnected(boolean connected) { isConnected = connected; }
    public void setSaved(boolean saved) { isSaved = saved; }

    public boolean isSecured() {
        return capabilities != null &&
               (capabilities.contains("WPA") || capabilities.contains("WEP"));
    }

    public int getSignalLevel(int maxLevel) {
        return android.net.wifi.WifiManager.calculateSignalLevel(signalStrength, maxLevel);
    }
}
