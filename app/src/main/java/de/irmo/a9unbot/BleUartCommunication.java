package de.irmo.a9unbot;

import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.bytesToHex;

import android.util.Log;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;
public class BleUartCommunication {

    private ExecutorService executorService;
    private RxBleConnection rxBleConnection;
    private BlockingQueue<byte[]> notificationQueue = new LinkedBlockingQueue<>();

    private Disposable connectionDisposable;

    private RxBleDevice rxBleDevice;
    private RxBleClient rxBleClient;
    private String targetMacAddress;

    public static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public static final UUID UART_SERVICE_UUID =      UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");


    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02X", b);
            hexString.append(hex).append(" ");
        }
        return hexString.toString();
    }

    public BleUartCommunication(RxBleClient rxBleClient, String macAddress) {
        this.rxBleClient = rxBleClient;
        this.targetMacAddress = macAddress;
        this.executorService = Executors.newSingleThreadExecutor();
        scanAndConnect();
    }


    private void scanAndConnect() {
        RxBleClient rxBleClient = this.rxBleClient;

        Disposable scanSubscription = rxBleClient.scanBleDevices(
                        new ScanSettings.Builder().build(),
                        new ScanFilter.Builder().setDeviceAddress(this.targetMacAddress).build() // Replace with the actual MAC address
                )
                .timeout(5, TimeUnit.SECONDS) // Set the timeout for 5 seconds
                .firstElement() // We are only interested in the first occurrence
                .subscribe(
                        scanResult -> {
                            // Device with the specified MAC address found, establish connection
                            this.rxBleDevice  = scanResult.getBleDevice();
                            establishConnection();
                            // Proceed to connect to the device
                        },
                        throwable -> {
                            Log.e("nb_ble", "Bluetooth scanning failed: " + throwable.getMessage(), throwable);
                        }
                );
    }

    private void establishConnection() {


        connectionDisposable = rxBleDevice.establishConnection(false)
                .timeout(50000, TimeUnit.SECONDS) // Set timeout for 5 seconds
                .flatMap(rxBleConnection -> {
                    // Store the connection
                    this.rxBleConnection = rxBleConnection;
                    // Start service discovery and convert Single to Observable
                    return rxBleConnection.discoverServices().toObservable();
                })
                .flatMap(rxBleDeviceServices -> {
                    // Check if the UART service is available on the device
                    if (rxBleDeviceServices.getService(UART_SERVICE_UUID) != null) {
                        // UART service is available, continue with setting up notification
                        return Observable.just(this.rxBleConnection);
                    } else {
                        // UART service is not available, complete the observable with an error
                        return Observable.error(new IllegalStateException("UART service not found"));
                    }
                })
                .subscribe(
                        connection -> {
                            // At this point, we know that the UART service is available
                            setupNotification();
                        },
                        throwable -> {
                            // Handle errors
                            if (throwable instanceof TimeoutException) {
                                Log.e("nb_ble", "Failed to connect to BLE device within 5 seconds.");
                            } else {
                                Log.e("nb_ble", "Error establishing BLE connection: " + throwable.getMessage());
                            }
                        }
                );

        // Optionally store connectionDisposable for later disposal if needed
    }



    private void setupNotification() {
        if (rxBleConnection != null) {
            Log.i("nb_ble", "Setup notification receiver... ");
            rxBleConnection.setupNotification(RX_CHARACTERISTIC_UUID)
                    .flatMap(notificationObservable -> notificationObservable)
                    .subscribe(
                            bytes -> {
                                Log.i("nb_ble", "Received notification: " + bytesToHex(bytes));
                                notificationQueue.offer(bytes);
                            },
                            throwable -> {
                                Log.e("nb_ble", "Error in setupNotification: " + throwable.getMessage(), throwable);
                            }
                    );
        }
    }

    public Future<List<byte[]>> collectAllMessagesForDuration(int timeoutInSeconds, byte[] dataToSend,  int desiredQueueSize) {
        return executorService.submit(() -> {
            if (rxBleDevice.getConnectionState() != RxBleConnection.RxBleConnectionState.CONNECTED) {
                throw new IllegalStateException("BLE device is not connected.");
            }

            // Clear the notificationQueue before sending new data
            notificationQueue.clear();

            Log.i("nb_ble", "Data so send: " + bytesToHex(dataToSend));

            // Chunk and write data to the characteristic
            final int chunkSize = 20;

            for (int start = 0; start < dataToSend.length; start += chunkSize) {
                int end = Math.min(dataToSend.length, start + chunkSize);
                byte[] chunk = Arrays.copyOfRange(dataToSend, start, end);

                Log.i("nb_ble", "Sending chunk: " + bytesToHex(chunk));


                try {
                    rxBleConnection.writeCharacteristic(TX_CHARACTERISTIC_UUID, chunk).blockingGet();
                    Thread.sleep(50);
                } catch (Exception e) {
                    throw new IllegalStateException("Error writing to BLE device: " + e.getMessage());
                }
            }

            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutInSeconds * 1000) {
                if (notificationQueue.size() >= desiredQueueSize) {
                    break;
                }
                try {
                    Thread.sleep(100); // Sleep a bit to reduce CPU usage
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Thread was interrupted", e);
                }
            }

            List<byte[]> messages = new ArrayList<>();
            notificationQueue.drainTo(messages);

            return messages;
        });
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
            connectionDisposable.dispose();
            Log.i("nb_ble", "Disconnected from BLE device");
        }
    }
}
