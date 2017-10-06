package com.grp22.arcm;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
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

    public static final String CONNECT_SUCCESS = "com.grp22.arcm.CONNECTION_SUCCESSFUL";
    public static final String CONNECT_FAIL = "com.grp22.arcm.CONNECTION_FAIL";
    public static final String CONNECTION_INTERRUPTED = "com.grp22.arcm.CONNECTION_INTERRUPTED";
    public static final String CONNECTION_RECOVERED = "com.grp22.arcm.CONNECTION_RECOVERED";
    public static final String DISCONNECTED = "com.grp22.arcm.DISCONNECTED";
    public static final String STRING_RECEIVED = "com.grp22.arcm.STRING_RECEIVED";
    private final Object lock = new Object();
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private boolean connectAsServer;
    private BluetoothDevice device;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;
    private BluetoothSocket clientSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private Intent broadcastIntent;
    private boolean tryReconnecting = true;
    private Runnable reconnectRunnable;
    private Thread reconnectThread;
    private int timeout;
    private boolean isStopped = false;

    public BluetoothConnectService() {
        super("BluetoothConnectService");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        device = intent.getParcelableExtra("device");
        timeout = intent.getIntExtra("timeout", 10);
        connectAsServer = intent.getBooleanExtra("connectionMode", false);
        Log.d("Pilihan:", Boolean.toString(connectAsServer));
        if (connectAsServer) {
            BluetoothServerSocket tmpServer = null;
            try {
                tmpServer = BluetoothAdapter.getDefaultAdapter().listenUsingInsecureRfcommWithServiceRecord("NAME", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) {
                Log.e("Error: ", "Socket's listen() method failed", e);
            }
            serverSocket = tmpServer;
            Log.d("Awalnya", "dari sini");
            while (true) {
                if (serverSocket != null) {
                    try {
                        Log.d("Masuk", "ke sini");
                        socket = serverSocket.accept(timeout * 1000);
                    } catch (Exception e) {
                        Log.e("Error: ", "Socket's accept() method failed", e);
                        try {
                            serverSocket.close();
                        } catch (IOException error) {
                            error.printStackTrace();
                        }
                        broadcastIntent = new Intent();
                        broadcastIntent.setAction(CONNECT_FAIL);
                        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                        sendBroadcast(broadcastIntent);
                        break;
                    }
                }
                Log.d("Setelah itu", "ke sini");
                if (socket != null) {
                    if (socket.getRemoteDevice().getAddress().equals(device.getAddress())) {
                        Intent broadcastIntent = new Intent();
                        broadcastIntent.setAction(CONNECT_SUCCESS);
                        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                        sendBroadcast(broadcastIntent);
                        tryReconnecting = true;
                        isStopped = false;
                        setupStream();
                        receiveFromInputStream();
                        break;
                    } else {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    broadcastIntent = new Intent();
                    broadcastIntent.setAction(CONNECT_FAIL);
                    broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    sendBroadcast(broadcastIntent);
                    break;
                }
            }
        } else {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) {
                Log.e("Error: ", "Socket's listen() method failed", e);
            }
            clientSocket = tmp;
            Log.d("Awalnya", "dari sini");
            while (true) {
                if (clientSocket != null) {
                    try {
                        Log.d("Masuk", "ke sini");
                        clientSocket.connect();
                    } catch (Exception e) {
                        Log.e("Error: ", "Socket's connect() method failed", e);
                        broadcastIntent = new Intent();
                        broadcastIntent.setAction(CONNECT_FAIL);
                        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                        sendBroadcast(broadcastIntent);
                        break;
                    }
                }
                Log.d("Setelah itu", "ke sini");
                if (clientSocket != null) {
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(CONNECT_SUCCESS);
                    broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    sendBroadcast(broadcastIntent);
                    tryReconnecting = true;
                    isStopped = false;
                    setupStream();
                    receiveFromInputStream();
                    break;
                }
            }
        }
    }

    public void setupStream() {
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        if (reconnectThread != null) {
            reconnectThread.interrupt();
            reconnectThread = null;
        }

        try {
            if (connectAsServer)
                tmpIn = socket.getInputStream();
            else
                tmpIn = clientSocket.getInputStream();
        } catch (IOException e) {
            Log.e("error", "Error occurred when creating input stream", e);
        }
        try {
            if (connectAsServer)
                tmpOut = socket.getOutputStream();
            else
                tmpOut = clientSocket.getOutputStream();
        } catch (IOException e) {
            Log.e("error", "Error occurred when creating output stream", e);
        }

        Log.d("Lalu", "ke sini");
        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void receiveFromInputStream() {
        byte[] mmBuffer = new byte[1024];
        int numBytes; // bytes returned from read()

        Log.d("Macet", "di sini");

        if (connectAsServer) {
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
                    }
                    try {
                        mmInStream.close();
                        mmOutStream.close();
                        if (socket != null)
                            socket.close();
                        socket = null;
                        if (serverSocket != null)
                            serverSocket.close();
                        serverSocket = null;
                    } catch (IOException error) {
                        // *shrugs*
                    }
                    if (tryReconnecting) {
                        try {
                            serverSocket = BluetoothAdapter.getDefaultAdapter().listenUsingInsecureRfcommWithServiceRecord("TEST", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                            socket = serverSocket.accept(timeout * 1000);
                        } catch (IOException exception) {
                            // Nothing can be done
                        }
                        if (socket != null && socket.isConnected() && socket.getRemoteDevice().getAddress().equals(device.getAddress())) {
                            broadcastIntent = new Intent();
                            broadcastIntent.setAction(CONNECTION_RECOVERED);
                            broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                            sendBroadcast(broadcastIntent);
                            tryReconnecting = true;
                            isStopped = false;
                            setupStream();
                            receiveFromInputStream();
                            break;
                        } else {
                            stop();
                            break;
                        }
                    }
                    break;
                }
            }
        } else {
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
                    }
                    reconnectRunnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mmInStream.close();
                                mmOutStream.close();
                                clientSocket = null;
                                clientSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                                clientSocket.connect();
                            } catch (IOException exception) {
                                // Nothing can be done
                            }
                            if (clientSocket.isConnected()) {
                                broadcastIntent = new Intent();
                                broadcastIntent.setAction(CONNECTION_RECOVERED);
                                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                                sendBroadcast(broadcastIntent);
                                isStopped = false;
                                setupStream();
                                receiveFromInputStream();
                            }
                        }
                    };
                    reconnectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            long startTime = System.currentTimeMillis();
                            while (true) {
                                SystemClock.sleep(100);
                                reconnectRunnable.run();
                                long timeElapsed = System.currentTimeMillis() - startTime;
                                if (clientSocket.isConnected() || !tryReconnecting || timeElapsed >= timeout * 1000)
                                    break;
                            }
                            if (!clientSocket.isConnected()) {
                                synchronized (lock) {
                                    stop();
                                }
                                reconnectThread.interrupt();
                            }
                        }
                    });
                    if (tryReconnecting)
                        reconnectThread.start();
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

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void stop() {
        tryReconnecting = false;
        try {
            if (mmInStream != null)
                mmInStream.close();
            if (mmOutStream != null)
                mmOutStream.close();
            if (socket != null)
                socket.close();
            if (clientSocket != null)
                clientSocket.close();
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            Log.e("Error", "Could not close the connect socket", e);
        } finally {
            if (!isStopped) {
                Log.d("Akhirnya", "ke sini");
                broadcastIntent = new Intent();
                broadcastIntent.setAction(DISCONNECTED);
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                sendBroadcast(broadcastIntent);
                isStopped = true;
            }
        }
    }

    public class LocalBinder extends Binder {
        BluetoothConnectService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BluetoothConnectService.this;
        }
    }
}