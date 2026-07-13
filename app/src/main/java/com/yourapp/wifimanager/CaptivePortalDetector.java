package com.yourapp.wifimanager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class CaptivePortalDetector {
    private static final String TAG = "CaptivePortalDetector";
    private Context context;

    public CaptivePortalDetector(Context context) {
        this.context = context;
    }

    public boolean isCaptivePortalPresent() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) return false;

            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        return !hasInternetAccess();
    }

    public boolean hasInternetAccess() {
        String[] urls = {
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://www.google.com/generate_204",
            "http://clients3.google.com/generate_204"
        };

        for (String urlString : urls) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode == 204 || responseCode == 200) {
                    return true;
                }
            } catch (IOException ignored) {}
        }
        return false;
    }

    public boolean bypassCaptivePortal() {
        String[] urls = {
            "http://www.google.com",
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://clients3.google.com/generate_204"
        };

        for (String urlString : urls) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(true);

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode == 204 || responseCode == 200) {
                    return true;
                }
            } catch (IOException ignored) {}
        }
        return false;
    }
}
