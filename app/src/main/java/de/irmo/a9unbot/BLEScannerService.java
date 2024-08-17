package de.irmo.a9unbot;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Collections;

public class BLEScannerService extends Service {

    private static final String TAG = "BLEScannerService";
    private static final String TARGET_MAC_ADDRESS = "F6:34:CD:56:E6:1B";
    private static final String CHANNEL_ID = "BLEScannerChannel";

    private BluetoothLeScanner bluetoothLeScanner;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForeground(1, getNotification());

        // Acquire a partial wake lock to keep CPU running in Doze mode
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BLEScannerService::WakeLock");
        wakeLock.acquire();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            startBLEScan();
        } else {
            Log.e(TAG, "Bluetooth is disabled or not supported on this device.");
            stopSelf();
        }
    }

    private void startBLEScan() {
        // Configure a ScanFilter to target the specific device MAC address
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceAddress(TARGET_MAC_ADDRESS)
                .build();

        // Configure ScanSettings for aggressive scanning
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // High power, high duty cycle scanning
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // Aggressive matching to improve detection
                .build();

        // Start scanning with the configured filter and settings
        bluetoothLeScanner.startScan(Collections.singletonList(filter), settings, bleScanCallback);
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (TARGET_MAC_ADDRESS.equals(device.getAddress())) {
                Log.d(TAG, "Target device found: " + device.getAddress());
                triggerVibration();
                // Optionally stop the scan if you only need to find the device once
                bluetoothLeScanner.stopScan(this);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan failed with error code: " + errorCode);
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(bleScanCallback);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
