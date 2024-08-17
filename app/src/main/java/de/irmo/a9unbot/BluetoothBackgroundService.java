package de.irmo.a9unbot;

import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.bytesToHex;
import static de.irmo.a9unbot.BleUartCommunication.hexStringToByteArray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.polidea.rxandroidble2.RxBleClient;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BluetoothBackgroundService extends Service {
    private static final int NOTIFICATION_ID = 123;
    private static final String CHANNEL_ID = "BluetoothServiceChannel";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 3000; // 2 seconds delay

    private ExecutorService backgroundExecutor;

    private volatile boolean isOperationInProgress = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());
        backgroundExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isOperationInProgress) {
            Log.i("NB_BLE", "Operation already in progress. Ignoring this start request.");
            return START_NOT_STICKY; // Ignore subsequent calls if already running
        }

        isOperationInProgress = true; // Mark operation as in progress

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "nineunbot:BluetoothWakeLock");

        final String defaultMacAddress = "F6:34:CD:56:E6:1B";
        final String macAddress;

        if (intent != null) {
            String retrievedMacAddress = intent.getStringExtra("MAC_ADDRESS");
            if (retrievedMacAddress != null) {
                macAddress = retrievedMacAddress;
            } else {
                macAddress = defaultMacAddress;
            }
        } else {
            macAddress = defaultMacAddress;
        }

        backgroundExecutor.execute(() -> {
            try {
                wakeLock.acquire();
                boolean success = false;
                int retryCount = 0;

                while (!success && retryCount < MAX_RETRIES) {
                    BleUartCommunication uartCommunication = null; // Declare inside the loop
                    try {
                        RxBleClient rxBleClient = RxBleClient.create(this);
                        uartCommunication = new BleUartCommunication(rxBleClient, macAddress);

                        performBluetoothOperations(uartCommunication);
                        success = true; // Mark success if no exceptions occur
                    } catch (Exception e) {
                        if (e.getMessage() != null && (e.getMessage().contains("status 0 (GATT_SUCCESS)") || e.getMessage().contains("-6"))) {
                            // Ignore these specific errors and break out of the retry loop
                            Log.i("NB_BLE", "Ignoring specific error: " + e.getMessage());
                            success = true;
                            break;
                        } else {
                            Log.e("NB_BLE", "Error during Bluetooth operations: " + e.getMessage());
                            retryCount++;
                            if (retryCount < MAX_RETRIES) {
                                Log.i("NB_BLE", "Retrying connection... (" + retryCount + "/" + MAX_RETRIES + ")");
                                try {
                                    Thread.sleep(RETRY_DELAY_MS); // Wait for 2 seconds before retrying
                                } catch (InterruptedException interruptedException) {
                                    Log.e("NB_BLE", "Retry sleep interrupted: " + interruptedException.getMessage());
                                }
                            } else {
                                Log.e("NB_BLE", "Max retries reached. Aborting connection.");
                            }
                        }
                    } finally {
                        if (uartCommunication != null) {
                            uartCommunication.shutdown(); // Ensure shutdown is called within the loop
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                isOperationInProgress = false; // Mark operation as complete
            }
        });

        return START_NOT_STICKY;
    }




    private void performBluetoothOperations(BleUartCommunication uartCommunication) throws Exception {
        ProtocolNinebot NbProtocol = new ProtocolNinebot("NBScooter1777");

        byte[] InitMessage = hexStringToByteArray("5AA5003E215B00");
        byte[] pingMessage = hexStringToByteArray("5AA5103E215C00");
        byte[] pairMessage = hexStringToByteArray("5AA50E3E215D00");
        byte[] unlockfirmware = hexStringToByteArray("5AA5023D20037A0000");

        byte[] pingAck1 = hexStringToByteArray("5AA500213E5C00");
        byte[] pingAck2 = hexStringToByteArray("5AA500213E5C01");
        byte[] pairAck = hexStringToByteArray("5AA500213e5d01");

        Log.i("NB_BLE", "Ping message " + bytesToHex(pingMessage));

        byte[] random_ping = addRandomPayload(pingMessage);

        Log.i("NB_BLE", "Ping message + random bytes: " + bytesToHex(random_ping));

        byte[] msg = NbProtocol.encrypt(InitMessage);

        Log.i("NB_BLE", "The message: " + bytesToHex(msg));

        Thread.sleep(1000);

        Future<List<byte[]>> futureMessages = uartCommunication.collectAllMessagesForDuration(1, msg, 3);
        List<byte[]> response1 = futureMessages.get();

        byte[] combinedArray = concatenateByteArrayList(response1);

        byte[] decyrpt = NbProtocol.decrypt(combinedArray, false);
        Log.i("NB_BLE", "Response1 in hex: " + bytesToHex(decyrpt));

        int lengthToExtract = 14;
        byte[] serial_number = extractLastNBytes(decyrpt, lengthToExtract);

        Log.i("NB_BLE", "Serial of scooter: " + bytesToHex(serial_number));

        for (int i = 0; i < 100; i++) {
            byte[] ping_msg = NbProtocol.encrypt(random_ping);

            futureMessages = uartCommunication.collectAllMessagesForDuration(1, ping_msg, 1);
            response1 = futureMessages.get();

            combinedArray = concatenateByteArrayList(response1);

            Log.i("NB_BLE", "Ping cmd raw in  hex: " + bytesToHex(combinedArray));
            if (combinedArray.length > 6) {
                decyrpt = NbProtocol.decrypt(combinedArray, false);
                Log.i("NB_BLE", "Ping response in hex: " + bytesToHex(decyrpt));

                if (Arrays.equals(decyrpt, pingAck1) || Arrays.equals(decyrpt, pingAck2)) {
                    Log.i("NB_BLE", "Received pingAck1 or pingAck2, breaking the loop");
                    break;
                }
            }
        }

        for (int i = 0; i < 100; i++) {
            byte[] pair_message = NbProtocol.encrypt(concatenateArrays(pairMessage, serial_number));

            futureMessages = uartCommunication.collectAllMessagesForDuration(1, pair_message, 1);
            response1 = futureMessages.get();

            combinedArray = concatenateByteArrayList(response1);

            if (combinedArray.length > 6) {
                decyrpt = NbProtocol.decrypt(combinedArray, false);
                Log.i("NB_BLE", "Pair response in hex: " + bytesToHex(decyrpt));

                if (Arrays.equals(decyrpt, pairAck)) {
                    Log.i("NB_BLE", "Received pair Ack, breaking the loop - we can now send messages to scooter");
                    break;
                }
            }
        }

        Thread.sleep(50);

        byte[] unlock = NbProtocol.encrypt(unlockfirmware);

        futureMessages = uartCommunication.collectAllMessagesForDuration(1, unlock, 1);
        response1 = futureMessages.get();

        combinedArray = concatenateByteArrayList(response1);

        decyrpt = NbProtocol.decrypt(combinedArray, false);
        Log.i("NB_BLE", "Response to unlock: " + bytesToHex(decyrpt));

        unlock = NbProtocol.encrypt(unlockfirmware);

        futureMessages = uartCommunication.collectAllMessagesForDuration(1, unlock, 1);
        response1 = futureMessages.get();

        combinedArray = concatenateByteArrayList(response1);

        decyrpt = NbProtocol.decrypt(combinedArray, false);
        Log.i("NB_BLE", "Response to unlock: " + bytesToHex(decyrpt));
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Service")
                .setContentText("Running Bluetooth operations")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // System icon for Bluetooth
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02X", b);
            hexString.append(hex).append(" ");
        }
        return hexString.toString();
    }

    public byte[] concatenateByteArrayList(List<byte[]> byteArrayList) {
        int totalLength = 0;
        for (byte[] array : byteArrayList) {
            totalLength += array.length;
        }

        byte[] result = new byte[totalLength];
        int currentPosition = 0;
        for (byte[] array : byteArrayList) {
            System.arraycopy(array, 0, result, currentPosition, array.length);
            currentPosition += array.length;
        }
        return result;
    }

    private static byte[] addRandomPayload(byte[] originalArray) {
        byte[] newArray = new byte[originalArray.length + 16];
        System.arraycopy(originalArray, 0, newArray, 0, originalArray.length);

        Random random = new Random();
        byte[] randomPayload = new byte[16];
        random.nextBytes(randomPayload);

        System.arraycopy(randomPayload, 0, newArray, originalArray.length, 16);

        return newArray;
    }

    public static byte[] extractLastNBytes(byte[] array, int n) {
        if (n > array.length) {
            throw new IllegalArgumentException("Length to extract is greater than array length.");
        }

        byte[] result = new byte[n];
        System.arraycopy(array, array.length - n, result, 0, n);
        return result;
    }

    private byte[] concatenateArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }
}
