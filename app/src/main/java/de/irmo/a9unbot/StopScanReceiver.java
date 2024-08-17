package de.irmo.a9unbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StopScanReceiver extends BroadcastReceiver {
    private static final String TAG = "StopScanReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm triggered to stop BLE scan.");
        Intent serviceIntent = new Intent(context, BLEScannerService.class);
        context.stopService(serviceIntent);
    }
}
