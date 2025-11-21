package io.twoyi.hal;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.twoyi.utils.IOUtils;

/**
 * Main HAL (Hardware Abstraction Layer) Server for Twoyi 0.7.5
 * Handles all system services including battery status, time, WiFi, RIL, etc.
 */
public class TwoyiHalServer {

    private static final String TAG = "TwoyiHal";
    private static final String HAL_SOCKET_NAME = "twoyi_hal";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static TwoyiHalServer INSTANCE;
    private boolean mStarted = false;
    private Thread mListenerThread;
    private final Context mContext;

    private TwoyiHalServer(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized TwoyiHalServer getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new TwoyiHalServer(context);
        }
        return INSTANCE;
    }

    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        
        mListenerThread = new Thread(this::startServer, "twoyi-hal-server");
        mListenerThread.start();
    }

    private void startServer() {
        LocalSocket serverSocket = null;
        LocalServerSocket localServerSocket = null;
        
        try {
            Log.d(TAG, "Starting HAL server: " + HAL_SOCKET_NAME);
            serverSocket = new LocalSocket();
            serverSocket.bind(new LocalSocketAddress(HAL_SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            localServerSocket = new LocalServerSocket(serverSocket.getFileDescriptor());

            while (mStarted && !Thread.currentThread().isInterrupted()) {
                LocalSocket clientSocket = localServerSocket.accept();
                handleSocket(clientSocket);
            }
        } catch (Exception e) {
            Log.e(TAG, "HAL server error", e);
        } finally {
            IOUtils.closeSilently(localServerSocket);
            IOUtils.closeSilently(serverSocket);
        }
    }

    private void handleSocket(LocalSocket socket) {
        EXECUTOR.execute(() -> handleSocketInternal(socket));
    }

    private void handleSocketInternal(LocalSocket socket) {
        try {
            InputStream inputStream = socket.getInputStream();
            
            // Read message type from the stream
            int messageType = readInt(inputStream);
            Log.d(TAG, "HAL message type: " + messageType);
            
            switch (messageType) {
                case 0: // Legacy or initialization
                    handleLegacy(socket, inputStream);
                    break;
                case 1: // App manager initialization
                    handleAppManagerInit(socket, inputStream);
                    break;
                case 3: // Vibrator service
                    handleVibratorService(socket, inputStream);
                    break;
                case 4: // Brightness control
                    handleBrightnessControl(socket, inputStream);
                    break;
                case 5: // App query service
                    handleAppQueryService(socket, inputStream);
                    break;
                case 6: // App manager service
                    handleAppManager(socket, inputStream);
                    break;
                case 7: // Bootlog service
                    handleBootLogService(socket, inputStream);
                    break;
                case 8: // Location services
                    handleLocationService(socket, inputStream);
                    break;
                case 9: // GPS service
                    handleGPSService(socket, inputStream);
                    break;
                case 10: // App manager operations
                    handleAppOperations(socket, inputStream);
                    break;
                case 11: // Broadcast receiver (BATTERY_CHANGED, TIME_SET, etc.)
                    handleBroadcastReceiver(socket, inputStream);
                    break;
                case 12: // WiFi services
                    handleWiFiService(socket, inputStream);
                    break;
                case 13: // RIL (Radio Interface Layer) services
                    handleRilService(socket, inputStream);
                    break;
                case 14: // Connectivity services
                    handleConnectivityService(socket, inputStream);
                    break;
                default:
                    Log.w(TAG, "Unknown HAL message type: " + messageType);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling HAL socket", e);
        }
    }

    private void handleBroadcastReceiver(LocalSocket socket, InputStream inputStream) {
        // Initialize the broadcast receiver for system events like battery status
        try {
            BroadcastHalService.getInstance().initialize(mContext, socket);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing broadcast receiver", e);
        }
    }

    private void handleVibratorService(LocalSocket socket, InputStream inputStream) {
        try {
            PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            int durationMs = readInt(inputStream);
            if (powerManager != null) {
                if (durationMs == 0) {
                    powerManager.cancelVibrate();
                } else {
                    powerManager.vibrate(durationMs);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling vibrator service", e);
        }
    }

    private void handleBrightnessControl(LocalSocket socket, InputStream inputStream) {
        try {
            float brightness = readInt(inputStream) / 255.0f;
            // Send brightness change intent to system
            android.content.Intent intent = new android.content.Intent("io.twoyi.light.CHANGE");
            intent.putExtra("key_brightness", brightness);
            mContext.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error handling brightness control", e);
        }
    }

    private void handleWiFiService(LocalSocket socket, InputStream inputStream) {
        // WiFi service implementation
        Log.d(TAG, "Handling WiFi service");
    }

    private void handleRilService(LocalSocket socket, InputStream inputStream) {
        // RIL service implementation
        Log.d(TAG, "Handling RIL service");
    }

    private void handleConnectivityService(LocalSocket socket, InputStream inputStream) {
        // Connectivity service implementation
        Log.d(TAG, "Handling connectivity service");
    }

    // Placeholder handlers for other services
    private void handleLegacy(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling legacy message");
    }

    private void handleAppManagerInit(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling app manager initialization");
    }

    private void handleAppQueryService(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling app query service");
    }

    private void handleAppManager(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling app manager");
    }

    private void handleBootLogService(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling boot log service");
    }

    private void handleLocationService(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling location service");
    }

    private void handleGPSService(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling GPS service");
    }

    private void handleAppOperations(LocalSocket socket, InputStream inputStream) {
        Log.d(TAG, "Handling app operations");
    }

    private int readInt(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4];
        int totalRead = 0;
        while (totalRead < 4) {
            int bytesRead = inputStream.read(buffer, totalRead, 4 - totalRead);
            if (bytesRead == -1) {
                throw new IOException("Unexpected end of stream");
            }
            totalRead += bytesRead;
        }
        
        // Convert from little-endian byte order (Intel format)
        return (buffer[3] & 0xFF) << 24 |
               (buffer[2] & 0xFF) << 16 |
               (buffer[1] & 0xFF) << 8  |
               (buffer[0] & 0xFF);
    }

    private void writeInt(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xFF);
        outputStream.write((value >> 8) & 0xFF);
        outputStream.write((value >> 16) & 0xFF);
        outputStream.write((value >> 24) & 0xFF);
    }

    public void stop() {
        mStarted = false;
        if (mListenerThread != null) {
            mListenerThread.interrupt();
        }
    }
}