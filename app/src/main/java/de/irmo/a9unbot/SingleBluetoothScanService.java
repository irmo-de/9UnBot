package de.irmo.a9unbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class SingleBluetoothScanService extends Service {

    private static final String TAG = "SingleBluetoothScanService";
    private static final String TARGET_MAC_ADDRESS = "F6:34:CD:56:E6:1B";
    private static final String CHANNEL_ID = "SingleBluetoothScanServiceChannel";
    private static final String WAKE_LOCK_TAG = "de.irmo.a9unbot:WakeLock";
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private boolean deviceFound = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            stopSelf();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Bluetooth LE Scanner not available");
            stopSelf();
            return;
        }

        handler = new Handler();

        createNotificationChannel();
        acquireWakeLock(); // Acquire the WakeLock to keep the device awake

        startScanning();
    }

    private void startScanning() {
        Log.d(TAG, "Starting Bluetooth scan");
        bluetoothLeScanner.startScan(leScanCallback);
        handler.postDelayed(() -> stopScanning(), 15 * 60 * 1000); // Stop scanning after 15 minutes
    }

    private void stopScanning() {
        Log.d(TAG, "Stopping Bluetooth scan");
        bluetoothLeScanner.stopScan(leScanCallback);
        releaseWakeLock(); // Release the WakeLock when scanning is done
        if (!deviceFound) {
            Log.d(TAG, "Target device not found, stopping service");
        }
        stopSelf(); // Stop the service after scanning
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            Log.d(TAG, "Device found: " + deviceAddress);

            if (TARGET_MAC_ADDRESS.equals(deviceAddress)) {
                Log.d(TAG, "Target device found: " + deviceAddress);
                deviceFound = true;
                bluetoothLeScanner.stopScan(this);
                startYourService(); // Trigger the action when the device is found
                stopSelf(); // Stop the service immediately after the device is found
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Bluetooth scan failed with error code: " + errorCode);
            stopSelf();
        }
    };

    private void startYourService() {
        Log.i(TAG, "Target device found. Waiting 5 seconds before starting the background service...");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Starting BluetoothBackgroundService");
                Context context = getApplicationContext();
                Intent serviceIntent = new Intent(context, BluetoothBackgroundService.class);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }, 5000); // 5000 milliseconds = 5 seconds
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Scanning Service")
                .setContentText("Scanning for target Bluetooth device...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build();

        startForeground(1, notification);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        if (!deviceFound) {
            Log.d(TAG, "Stopping Bluetooth scan in onDestroy");
            bluetoothLeScanner.stopScan(leScanCallback);
        }
        releaseWakeLock(); // Ensure the WakeLock is released when the service is destroyed
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel");
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth Scanning Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create notification channel");
            }
        }
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                wakeLock.acquire();
                Log.d(TAG, "WakeLock acquired");
            } else {
                Log.e(TAG, "PowerManager is null, cannot acquire WakeLock");
            }
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
            wakeLock = null;
        }
    }
}
