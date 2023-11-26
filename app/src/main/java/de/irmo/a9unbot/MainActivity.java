package de.irmo.a9unbot;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import android.Manifest;
import android.provider.Settings;
import android.util.Log;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import io.reactivex.disposables.Disposable;





public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_LOCATION_PERMISSION = 101;
    private static final int REQUEST_CODE_PERMISSIONS = 1; // Arbitrary integer for the request code
    private static final int REQUEST_CODE_BLUETOOTH_CONNECT_PERMISSION = 102;
    private static final String MAC_ADDRESS = "F6:34:CD:56:E6:1B"; // replace with your device's MAC address
    private Disposable disposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check for location and Bluetooth connect permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CODE_PERMISSIONS);
        }
 else {
            // Permissions already granted, initialize BLE client
            initBleClient();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // All permissions were granted, initialize BLE client
                initBleClient();
            } else {
                // At least one permission was denied, show an alert dialog
                new AlertDialog.Builder(this)
                        .setTitle("Permissions Required")
                        .setMessage("Location and Bluetooth permissions are required for BLE scanning. Please grant them in the app settings.")
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .setNegativeButton("Settings", (dialog, which) -> {
                            // Intent to open app settings
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        })
                        .create()
                        .show();
            }
        }
    }


    private void initBleClient() {



        Intent serviceIntent = new Intent(this, BluetoothBackgroundService.class);
        serviceIntent.putExtra("MAC_ADDRESS", "F6:34:CD:56:E6:1B"); // Replace with actual MAC address
        startService(serviceIntent);




    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}