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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import android.os.PowerManager;


import de.irmo.a9unbot.ProtocolNinebot;import de.irmo.a9unbot.ProtocolNinebot;

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

    private ExecutorService backgroundExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());
        backgroundExecutor = Executors.newSingleThreadExecutor();
        // Initialize your Bluetooth communication setup here
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
        // Determine the total length of all byte arrays combined
        int totalLength = 0;
        for (byte[] array : byteArrayList) {
            totalLength += array.length;
        }

        // Create a new array to hold all the bytes
        byte[] result = new byte[totalLength];

        // Copy each array into the result array
        int currentPosition = 0;
        for (byte[] array : byteArrayList) {
            System.arraycopy(array, 0, result, currentPosition, array.length);
            currentPosition += array.length;
        }

        return result;
    }


    private static byte[] addRandomPayload(byte[] originalArray) {
        // Create a new array with space for the original array plus 16 bytes for the random payload
        byte[] newArray = new byte[originalArray.length + 16];

        // Copy the original array into the new array
        System.arraycopy(originalArray, 0, newArray, 0, originalArray.length);

        // Generate 16 random bytes
        Random random = new Random();
        byte[] randomPayload = new byte[16];
        random.nextBytes(randomPayload); // Fill the byte array with random bytes

        // Append the random bytes to the new array
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "nineunbot:BluetoothWakeLock");

        String macAddress = "F6:34:CD:56:E6:1B";

        if (intent != null) {
            macAddress = intent.getStringExtra("MAC_ADDRESS");
            // Handle the MAC address
        }

        Log.i("NB_BLE", "Intent mac: " + macAddress);

        RxBleClient rxBleClient = RxBleClient.create(this);
        BleUartCommunication uartCommunication = new BleUartCommunication(rxBleClient, macAddress);

        // rest of your code...

        //ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

        backgroundExecutor.execute(() -> {
            try {
                wakeLock.acquire();
                //BasicTest test = new BasicTest();


                ProtocolNinebot NbProtocol = new ProtocolNinebot("NBScooter1777");


                byte[] InitMessage      = hexStringToByteArray("5AA5003E215B00");
                byte[] pingMessage      = hexStringToByteArray("5AA5103E215C00");
                byte[] pairMessage      = hexStringToByteArray("5AA50E3E215D00");
                byte[] unlockfirmware   = hexStringToByteArray("5AA5023D20037A0000");
                byte[] lockfirmware     = hexStringToByteArray("5AA5023D20037A0100");

                byte[] pingAck1 = hexStringToByteArray("5AA500213E5C00");
                byte[] pingAck2 = hexStringToByteArray("5AA500213E5C01");
                byte[] pairAck =  hexStringToByteArray("5AA500213e5d01");

                Log.i("NB_BLE", "Ping message " + bytesToHex(pingMessage));

                byte[] random_ping = addRandomPayload(pingMessage);

                Log.i("NB_BLE", "Ping message + random bytes: " + bytesToHex(random_ping));


                //byte[] InitMessage = hexStringToByteArray("5aa50033a94345000046ff0000");

                //byte[] InitMessage = hexStringToByteArray("3e215b00");


                byte[] msg = NbProtocol.encrypt(InitMessage);

                Log.i("NB_BLE", "The message: " + bytesToHex(msg));

                Thread.sleep(1000);

                // Pre init to receive enryption key
                Future<List<byte[]>> futureMessages = uartCommunication.collectAllMessagesForDuration(1,msg, 3); // for a 5-second timeout
                //Future<byte[]> futureResponse1 = uartCommunication.sendDataAndWaitForResponse(msg);
                List<byte[]> response1 = futureMessages.get(); // This blocks until the response is available

                //String hex = bytesToHex(response1);

                byte[] combinedArray = concatenateByteArrayList(response1);

                byte[] decyrpt = NbProtocol.decrypt(combinedArray, false);
                Log.i("NB_BLE", "Response1 in hex: " + bytesToHex(decyrpt));

                int lengthToExtract = 14; // Length of the sequence to extract
                byte[] serial_number = extractLastNBytes(decyrpt, lengthToExtract);

                Log.i("NB_BLE", "Serial of scooter: " + bytesToHex(serial_number));

                // Ping scooter


                for (int i = 0; i < 100; i++) {

                    byte[] ping_msg = NbProtocol.encrypt(random_ping);



                    futureMessages = uartCommunication.collectAllMessagesForDuration(1, ping_msg, 1); // for a 5-second timeout
                    //Future<byte[]> futureResponse1 = uartCommunication.sendDataAndWaitForResponse(msg);
                    response1 = futureMessages.get(); // This blocks until the response is available

                    //String hex = bytesToHex(response1);

                    combinedArray = concatenateByteArrayList(response1);


                    Log.i("NB_BLE", "Ping cmd raw in  hex: " + bytesToHex(combinedArray));
                    if (combinedArray.length > 6) {
                        decyrpt = NbProtocol.decrypt(combinedArray, false);
                        Log.i("NB_BLE", "Ping response in hex: " + bytesToHex(decyrpt));

                        // Checking received notification.

                        // Check if the response is pingAck1 or pingAck2
                        if (Arrays.equals(decyrpt, pingAck1) || Arrays.equals(decyrpt, pingAck2)) {
                            Log.i("NB_BLE", "Received pingAck1 or pingAck2, breaking the loop");
                            break; // Exit the loop if the condition is met
                        }


                    }



                }

                // Authenticating


                for (int i = 0; i < 100; i++) {

                    byte[] pair_message = NbProtocol.encrypt(concatenateArrays(pairMessage, serial_number));

                    futureMessages = uartCommunication.collectAllMessagesForDuration(1, pair_message, 1); // for a 5-second timeout
                    //Future<byte[]> futureResponse1 = uartCommunication.sendDataAndWaitForResponse(msg);
                    response1 = futureMessages.get(); // This blocks until the response is available

                    //String hex = bytesToHex(response1);
                    combinedArray = concatenateByteArrayList(response1);

                    if (combinedArray.length > 6) {
                        decyrpt = NbProtocol.decrypt(combinedArray, false);
                        Log.i("NB_BLE", "Pair response in hex: " + bytesToHex(decyrpt));

                        // Checking received notification.

                        // Check if the response is pingAck1 or pingAck2
                        if (Arrays.equals(decyrpt, pairAck) ) {
                            Log.i("NB_BLE", "Received pair Ack, breaking the loop - we can now send messages to scooter");
                            break; // Exit the loop if the condition is met
                        }


                    }

                }

                // Final send the unlock full power command to scooter - the moment we all waited for

                Thread.sleep(50);


                byte[] unlock = NbProtocol.encrypt(unlockfirmware);

                futureMessages = uartCommunication.collectAllMessagesForDuration(1, unlock, 1); // for a 5-second timeout
                //Future<byte[]> futureResponse1 = uartCommunication.sendDataAndWaitForResponse(msg);
                response1 = futureMessages.get(); // This blocks until the response is available

                //String hex = bytesToHex(response1);
                combinedArray = concatenateByteArrayList(response1);


                decyrpt = NbProtocol.decrypt(combinedArray, false);
                Log.i("NB_BLE", "Response to unlock: " + bytesToHex(decyrpt));

                unlock = NbProtocol.encrypt(unlockfirmware);

                futureMessages = uartCommunication.collectAllMessagesForDuration(1, unlock, 1); // for a 5-second timeout
                //Future<byte[]> futureResponse1 = uartCommunication.sendDataAndWaitForResponse(msg);
                response1 = futureMessages.get(); // This blocks until the response is available


                //String hex = bytesToHex(response1);
                combinedArray = concatenateByteArrayList(response1);


                decyrpt = NbProtocol.decrypt(combinedArray, false);
                Log.i("NB_BLE", "Response to unlock: " + bytesToHex(decyrpt));


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                uartCommunication.shutdown();
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        });

        return START_NOT_STICKY;
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
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Shutdown backgroundExecutor here
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }
}
