package com.yourapp.wifimanager.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.yourapp.wifimanager.CaptivePortalDetector;
import com.yourapp.wifimanager.MainActivity;
import com.yourapp.wifimanager.NetworkManager;
import com.yourapp.wifimanager.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoReconnectWorker extends Service {
    private static final String TAG = "AutoReconnectWorker";
    private static final String CHANNEL_ID = "WifiManagerChannel";
    private static final int NOTIFICATION_ID = 1001;

    private NetworkManager networkManager;
    private CaptivePortalDetector portalDetector;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private Handler mainHandler;

    private String targetSSID;
    private String targetPassword;
    private int intervalSeconds;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service Created");
        networkManager = new NetworkManager(this);
        portalDetector = new CaptivePortalDetector(this);
        executor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("جارٍ الإدارة التلقائية"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetSSID = intent.getStringExtra("SSID");
            targetPassword = intent.getStringExtra("PASSWORD");
            intervalSeconds = intent.getIntExtra("INTERVAL_SECONDS", 30);
        }

        if (targetSSID == null || targetSSID.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startAutoManage();
        return START_STICKY;
    }

    private void startAutoManage() {
        updateNotification("⚡ " + targetSSID + " كل " + intervalSeconds + "ث");
        scheduler.schedule(this::executeCycle, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::executeCycle, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private void executeCycle() {
        executor.submit(() -> {
            try {
                Log.i(TAG, "Cycle started for: " + targetSSID);
                boolean forgotten = networkManager.forgetCurrentNetwork();
                Log.i(TAG, "Forget current network: " + forgotten);
                Thread.sleep(500);

                boolean connected = networkManager.connectToNetwork(targetSSID, targetPassword);
                Log.i(TAG, "Reconnect to target: " + connected);

                if (connected) {
                    Thread.sleep(1000);
                    boolean hasInternet = portalDetector.hasInternetAccess();
                    if (!hasInternet) {
                        Log.i(TAG, "No internet, trying to bypass portal...");
                        boolean bypassed = portalDetector.bypassCaptivePortal();
                        Log.i(TAG, "Bypass result: " + bypassed);
                    }
                    updateNotification("✅ متصل بـ " + targetSSID);
                } else {
                    updateNotification("⚠️ فشل الاتصال بـ " + targetSSID);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cycle error: " + e.getMessage());
                updateNotification("❌ خطأ: " + e.getMessage());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi Manager",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("إدارة الشبكات التلقائية");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📶 Wi-Fi Manager Pro")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(message));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) scheduler.shutdownNow();
        if (executor != null) executor.shutdownNow();
        Log.i(TAG, "Service Destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
