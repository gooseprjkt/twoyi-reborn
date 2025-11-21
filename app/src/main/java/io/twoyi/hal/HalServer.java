package io.twoyi.hal;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.twoyi.utils.IOUtils;

/**
 * Hardware Abstraction Layer (HAL) Server for Twoyi
 * Handles system services like battery, connectivity, RIL, etc.
 */
public class HalServer {

    private static final String TAG = "TwoyiHal";
    private static final String HAL_SOCKET_NAME = "twoyi_hal";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static HalServer INSTANCE;
    private boolean mStarted = false;
    private Thread mListenerThread;
    private final Context mContext;

    private HalServer(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized HalServer getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new HalServer(context);
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
            try {
                if (localServerSocket != null) localServerSocket.close();
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) {}
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
                case 11: // BroadcastHalService - handles battery, time, etc.
                    BroadcastHalService.getInstance().initialize(mContext, socket);
                    break;
                case 12: // WiFi services
                    // TODO: Add WiFi service functionality
                    break;
                case 13: // RIL services
                    // TODO: Add RIL service functionality
                    break;
                case 14: // Connectivity services
                    // TODO: Add connectivity service functionality
                    break;
                default:
                    Log.w(TAG, "Unknown HAL message type: " + messageType);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling HAL socket", e);
        }
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

    public void stop() {
        mStarted = false;
        if (mListenerThread != null) {
            mListenerThread.interrupt();
        }
    }
}