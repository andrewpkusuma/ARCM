package com.grp22.arcm;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Andrew on 28/8/17.
 */

public class BluetoothConnectService extends IntentService {
    public static final String PARAM_ADDRESS = "";

    private final BluetoothServerSocket mmServerSocket;
    private BluetoothSocket socket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private byte[] mmBuffer;

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
        // Use a temporary object that is later assigned to mmServerSocket
        // because mmServerSocket is final.
        BluetoothServerSocket tmp = null;
        try {
            // MY_UUID is the app's UUID string, also used by the client code.
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            tmp = adapter.listenUsingInsecureRfcommWithServiceRecord("com.grp22.arcm", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        } catch (IOException e) {
            Log.e("Error: ", "Socket's listen() method failed", e);
        }
        mmServerSocket = tmp;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String deviceAddress = intent.getStringExtra(PARAM_ADDRESS);
        socket = null;
        while (true) {
            try {
                socket = mmServerSocket.accept();
                if (!socket.getRemoteDevice().getAddress().equals(deviceAddress)) {
                    socket.close();
                    continue;
                }
            } catch (Exception e) {
                Log.e("Error: ", "Socket's accept() method failed", e);
                break;
            }
            if (socket != null) {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(BluetoothConnectActivity.ResponseReceiver.CONNECT_SUCCESS);
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                sendBroadcast(broadcastIntent);
                setupStream();
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mmServerSocket.close();
            if (mmInStream != null)
                mmInStream.close();
            if (mmOutStream != null)
                mmOutStream.close();
        } catch (IOException e) {
            Log.e("Error", "Could not close the connect socket", e);
        } finally {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(MainActivity.ResponseReceiver.DISCONNECT_SUCCESS);
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

        mmInStream = tmpIn;
        mmOutStream = tmpOut;

        receiveFromInputStream();
    }

    public void receiveFromInputStream() {
        mmBuffer = new byte[1024];
        int numBytes; // bytes returned from read()

        // Keep listening to the InputStream until an exception occurs.
        while (true) {
            try {
                // Read from the InputStream.
                numBytes = mmInStream.read(mmBuffer);
                String message = new String(mmBuffer, 0, numBytes);
                Log.d("Message -> ", message);
                // Send the obtained bytes to the UI activity.
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(MainActivity.ResponseReceiver.STRING_RECEIVED);
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                broadcastIntent.putExtra("message", message);
                sendBroadcast(broadcastIntent);
            } catch (IOException e) {
                Log.d("error", "Input stream was disconnected", e);
                break;
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
        onDestroy();
    }
}
