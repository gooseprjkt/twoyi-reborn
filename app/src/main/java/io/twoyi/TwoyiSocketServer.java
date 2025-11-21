/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.twoyi;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.twoyi.hal.BroadcastHalService;
import io.twoyi.utils.IOUtils;

/**
 * @author weishu
 * @date 2021/10/27.
 */

public class TwoyiSocketServer {

    private static final String TAG = "TwoyiSocketServer";

    private static TwoyiSocketServer INSTANCE;

    private static final String SOCK_NAME = "TWOYI_SOCK";

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private boolean mStarted = false;
    private final Context mContext;
    private Thread mListenerThread;

    private TwoyiSocketServer(Context context) {
        mContext = context.getApplicationContext();
    }

    public static TwoyiSocketServer getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new TwoyiSocketServer(context);
        }
        return INSTANCE;
    }

    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;

        mListenerThread = new Thread(this::startServer, "twoyi-socket-server");
        mListenerThread.start();
    }

    private void startServer() {
        LocalSocket serverSocket = null;
        LocalServerSocket localServerSocket = null;

        try {
            serverSocket = new LocalSocket();
            serverSocket.bind(new LocalSocketAddress(SOCK_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            localServerSocket = new LocalServerSocket(serverSocket.getFileDescriptor());

            Log.d(TAG, "HAL server started: " + SOCK_NAME);

            while (mStarted && !Thread.currentThread().isInterrupted()) {
                LocalSocket clientSocket = localServerSocket.accept();
                handleSocket(clientSocket);
            }
        } catch (IOException e) {
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

            // Read message type from the first 4 bytes
            int messageType = readInt(inputStream);
            Log.d(TAG, "HAL message type: " + messageType);

            switch (messageType) {
                case 0:
                    // Legacy socket handling - could be for older compatibility
                    handleLegacySocket(socket);
                    break;
                case 11: // BroadcastHalService
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
            Log.e(TAG, "Error handling socket", e);
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

    private void handleLegacySocket(LocalSocket socket) {
        // Handle legacy message format for backward compatibility
        // This could be used for basic commands like switch host, boot completed
        try {
            byte[] data = new byte[1024];
            int read = socket.getInputStream().read(data);
            String msg = new String(data, 0, read);

            if (msg.startsWith("SWITCH_HOST")) {
                TwoyiStatusManager.getInstance().switchOs(mContext);
            } else if (msg.startsWith("BOOT_COMPLETED")) {
                TwoyiStatusManager.getInstance().markStarted();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error handling legacy socket", e);
        }
    }

    public void stop() {
        mStarted = false;
        if (mListenerThread != null) {
            mListenerThread.interrupt();
        }
    }
}
