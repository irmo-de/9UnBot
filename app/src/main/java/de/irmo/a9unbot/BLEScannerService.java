package de.irmo.a9unbot;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Collections;

public class BLEScannerService extends Service {

    private static final String TAG = "BLEScannerService";
    private static final String TARGET_MAC_ADDRESS = "F6:34:CD:56:E6:1B";
    private static final String CHANNEL_ID = "BLEScannerChannel";
    private static final long SCAN_DURATION = 15 * 60 * 1000; // 15 minutes
    private static final long DELAY_BEFORE_NEXT_SERVICE = 5000; // 5 seconds

    private BluetoothLeScanner bluetoothLeScanner;
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;

    // Flag to ensure the scan result actions are performed only once
    private boolean isDeviceFound = false;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service created, instance: " + this.hashCode());

        createNotificationChannel();
        startForeground(1, getNotification());

        // Acquire a partial wake lock to keep CPU running in Doze mode
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BLEScannerService::WakeLock");
        wakeLock.acquire();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            handler = new Handler();
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            startBLEScan();
        } else {
            Log.e(TAG, "Bluetooth is disabled or not supported on this device.");
            stopSelf();
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_BLE_SCAN".equals(intent.getAction())) {
            Log.i(TAG, "Received STOP_BLE_SCAN action, stopping BLE scan.");
            stopBLEScan(); // Ensure the scan is stopped
            stopSelf(); // Stop the service
        } else {
            Log.i(TAG, "Service started, startId: " + startId + ", instance: " + this.hashCode());
            // Your existing onStartCommand logic here
        }
        return START_STICKY;
    }




    private void startBLEScan() {
        // Configure a ScanFilter to target the specific device MAC address
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceAddress(TARGET_MAC_ADDRESS)
                .build();

        // Configure ScanSettings for less aggressive scanning
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // Less aggressive scan mode
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY) // Less aggressive matching
                .build();

        // Start scanning with the configured filter and settings
        bluetoothLeScanner.startScan(Collections.singletonList(filter), settings, bleScanCallback);

        // Schedule stopping the scan after the specified duration (15 minutes)
        Intent intent = new Intent(this, StopScanReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + SCAN_DURATION, pendingIntent);

        Log.i(TAG, "BLE scan started. Will stop after 15 minutes if the target device is not found. Instance: " + this.hashCode());
    }

    private void stopBLEScan() {
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(bleScanCallback);
            Log.i(TAG, "BLE scan stopped. Instance: " + this.hashCode());
        }

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            Log.i(TAG, "AlarmManager canceled. Instance: " + this.hashCode());
        }

        stopSelf(); // Stop the service after the scan is complete
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!isDeviceFound) { // Ensure this block is executed only once
                BluetoothDevice device = result.getDevice();
                if (TARGET_MAC_ADDRESS.equals(device.getAddress())) {
                    isDeviceFound = true;
                    Log.d(TAG, "Target device found: " + device.getAddress() + ", instance: " + this.hashCode());
                    triggerVibration();

                    // Stop the alarm since the target device is found
                    if (pendingIntent != null) {
                        alarmManager.cancel(pendingIntent);
                        Log.i(TAG, "AlarmManager canceled because the target device was found. Instance: " + this.hashCode());
                    }

                    // Keep the device awake during the delay
                    wakeLock.acquire(DELAY_BEFORE_NEXT_SERVICE + 1000);

                    // Schedule the next service to start after a 5-second delay
                    handler.postDelayed(() -> {
                        Intent serviceIntent = new Intent(BLEScannerService.this, BluetoothBackgroundService.class);
                        serviceIntent.putExtra("MAC_ADDRESS", TARGET_MAC_ADDRESS);
                        startService(serviceIntent);

                        Log.i(TAG, "BluetoothBackgroundService started after 5 seconds delay. Instance: " + this.hashCode());
                        stopSelf();
                    }, DELAY_BEFORE_NEXT_SERVICE);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan failed with error code: " + errorCode + ", instance: " + this.hashCode());
            stopBLEScan();
        }
    };

    private void triggerVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500); // Deprecated in API 26 but still works for lower APIs
            }
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BLE Scanner Service")
                .setContentText("Scanning for target BLE device...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BLE Scanner Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed, instance: " + this.hashCode());
        stopBLEScan(); // Stop BLE scan and cancel any pending alarms
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(); // Release the wake lock
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null); // Remove any pending handler callbacks
        }
        super.onDestroy(); // Call the superclass's onDestroy method
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
