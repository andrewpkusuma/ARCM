package com.grp22.arcm;

import android.app.IntentService;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Andrew on 28/8/17.
 */

public class BluetoothConnectService extends IntentService {

    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private Intent broadcastIntent;
    private boolean tryReconnecting = true;
    private Thread reconnectThread;
    private Thread stopThread;

    public static final String CONNECT_SUCCESS = "com.grp22.arcm.CONNECTION_SUCCESSFUL";
    public static final String CONNECT_FAIL = "com.grp22.arcm.CONNECTION_FAIL";
    public static final String CONNECTION_INTERRUPTED = "com.grp22.arcm.CONNECTION_INTERRUPTED";
    public static final String CONNECTION_RECOVERED = "com.grp22.arcm.CONNECTION_RECOVERED";
    public static final String DISCONNECTED = "com.grp22.arcm.DISCONNECTED";
    public static final String STRING_RECEIVED = "com.grp22.arcm.STRING_RECEIVED";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        BluetoothConnectService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BluetoothConnectService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public BluetoothConnectService() {
        super("BluetoothConnectService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Use a temporary object that is later assigned to mmServerSocket
        // because mmServerSocket is final.
        device = intent.getParcelableExtra("device");
        BluetoothSocket tmp = null;
        try {
            // MY_UUID is the app's UUID string, also used by the client code.
            tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        } catch (IOException e) {
            Log.e("Error: ", "Socket's listen() method failed", e);
        }
        Log.d("Awalnya", "dari sini");
        socket = tmp;
        if (socket != null) {
            while (true) {
                try {
                    socket.connect();
                } catch (Exception e) {
                    Log.e("Error: ", "Socket's accept() method failed", e);
                    broadcastIntent = new Intent();
                    broadcastIntent.setAction(CONNECT_FAIL);
                    broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    sendBroadcast(broadcastIntent);
                    break;
                }
                if (socket != null) {
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(CONNECT_SUCCESS);
                    broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    sendBroadcast(broadcastIntent);
                    setupStream();
                    break;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mmInStream != null)
                mmInStream.close();
            if (mmOutStream != null)
                mmOutStream.close();
            socket.close();
        } catch (IOException e) {
            Log.e("Error", "Could not close the connect socket", e);
        } finally {
            Log.d("Akhirnya", "ke sini");
            broadcastIntent = new Intent();
            broadcastIntent.setAction(DISCONNECTED);
            broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
            sendBroadcast(broadcastIntent);
        }
    }

    public void setupStream() {
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        try {
            tmpIn = socket.getInputStream();
        } catch (IOException e) {
            Log.e("error", "Error occurred when creating input stream", e);
        }
        try {
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            Log.e("error", "Error occurred when creating output stream", e);
        }

        Log.d("Lalu", "ke sini");
        mmInStream = tmpIn;
        mmOutStream = tmpOut;

        receiveFromInputStream();
    }

    public void receiveFromInputStream() {
        byte[] mmBuffer = new byte[1024];
        int numBytes; // bytes returned from read()

        Log.d("Macet", "di sini");

        // Keep listening to the InputStream until an exception occurs.
        while (true) {
            try {
                // Read from the InputStream.
                numBytes = mmInStream.read(mmBuffer);
                String message = new String(mmBuffer, 0, numBytes);
                Log.d("Message -> ", message);
                // Send the obtained bytes to the UI activity.
                broadcastIntent = new Intent();
                broadcastIntent.setAction(STRING_RECEIVED);
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                broadcastIntent.putExtra("message", message);
                sendBroadcast(broadcastIntent);
            } catch (IOException e) {
                Log.d("Bisa", "ke sini");
                e.printStackTrace();
                if (tryReconnecting) {
                    broadcastIntent = new Intent();
                    broadcastIntent.setAction(CONNECTION_INTERRUPTED);
                    broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    sendBroadcast(broadcastIntent);
                    stopThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SystemClock.sleep(10000);
                            if (!socket.isConnected()) {
                                Log.d("Aku", "hidup");
                                onDestroy();
                            }
                        }
                    });
                    stopThread.start();
                    reconnectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("Aku", "jalan");
                            SystemClock.sleep(1000);
                            try {
                                mmInStream.close();
                                mmOutStream.close();
                                socket = null;
                                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                                socket.connect();
                            } catch (IOException exception) {
                                try {
                                    socket.close();
                                } catch (IOException error) {
                                    error.printStackTrace();
                                }
                            }
                            if (socket.isConnected()) {
                                Log.d("Balik", "ke atas deh");
                                broadcastIntent = new Intent();
                                broadcastIntent.setAction(CONNECTION_RECOVERED);
                                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                                sendBroadcast(broadcastIntent);
                                stopThread.interrupt();
                                setupStream();
                            }
                        }
                    });
                    reconnectThread.start();
                    break;
                } else {
                    break;
                }
            }
        }
    }

    public void sendToOutputStream(String message) {
        try {
            mmOutStream.write(message.getBytes());
        } catch (IOException e) {
            Log.e("error", "Error occurred when sending data", e);
        }
    }

    public void stop() {
        tryReconnecting = false;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
