package de.irmo.a9unbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BluetoothLeScanWorker extends Worker {

    private static final String TAG = "BluetoothLeScanWorker";
    private static final String TARGET_MAC_ADDRESS = "F6:34:CD:56:E6:1B";
    private static final String CHANNEL_ID = "BluetoothWorkerChannel";
    private static final int NOTIFICATION_ID = 456;
    private BluetoothAdapter bluetoothAdapter;
    private static volatile boolean deviceFound = false;

    public BluetoothLeScanWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    @NonNull
    @Override
    public Result doWork() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            return Result.failure();
        }

        // Set the worker as a foreground task
        setForegroundAsync(createForegroundInfo());

        startScanning();

        return Result.success(); // Or return Result.failure() if you need to retry on failure
    }

    private void startScanning() {
        Log.i(TAG, "Starting Bluetooth scan");
        bluetoothAdapter.startLeScan(leScanCallback);

        // If scan doesn't stop due to device being found, stop it after a delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!deviceFound) {
                Log.i(TAG, "Stopping Bluetooth scan after timeout");
                bluetoothAdapter.stopLeScan(leScanCallback);
            }
        }, 15 * 60 * 1000); // Stop scan after 15 minutes if not found
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (deviceFound) {
                Log.i(TAG, "Device already found, ignoring subsequent callbacks.");
                return;
            }

            if (TARGET_MAC_ADDRESS.equals(device.getAddress())) {
                deviceFound = true;
                Log.i(TAG, "Target device found: " + device.getAddress());

                // Stop scanning immediately
                bluetoothAdapter.stopLeScan(this);

                // Start your Bluetooth background service or whatever you need to do
                startBluetoothBackgroundService();
            }
        }
    };

    private void startBluetoothBackgroundService() {
        Log.i(TAG, "Starting BluetoothBackgroundService");
        Context context = getApplicationContext();
        Intent serviceIntent = new Intent(context, BluetoothBackgroundService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Context context = getApplicationContext();
        Notification notification = getNotification();

        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    private Notification getNotification() {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Bluetooth Worker Channel", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Scanning for Bluetooth Devices")
                .setContentText("Running background task")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build();
    }
}
