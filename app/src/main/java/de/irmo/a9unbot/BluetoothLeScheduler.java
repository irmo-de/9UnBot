package de.irmo.a9unbot;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.OutOfQuotaPolicy;

public class BluetoothLeScheduler {

    private static final String UNIQUE_WORK_NAME = "BluetoothLeScanWork";

    public static void scheduleBluetoothScan(Context context) {
        // Create a OneTimeWorkRequest with expedited execution
        OneTimeWorkRequest bluetoothLeWorkRequest = new OneTimeWorkRequest.Builder(BluetoothLeScanWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) // Expedite work if possible
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,  // This ensures that if work is already running, it won't be replaced
                bluetoothLeWorkRequest
        );
    }
}
