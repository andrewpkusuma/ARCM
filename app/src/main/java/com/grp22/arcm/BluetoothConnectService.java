package com.grp22.arcm;

import android.app.IntentService;
import android.bluetooth.BluetoothDevice;
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

    private BluetoothSocket socket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;

    private boolean isStopped = false;

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
        BluetoothDevice device = intent.getParcelableExtra("device");
        BluetoothSocket tmp = null;
        try {
            // MY_UUID is the app's UUID string, also used by the client code.
            tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        } catch (IOException e) {
            Log.e("Error: ", "Socket's listen() method failed", e);
        }
        socket = tmp;
        if (socket != null) {
            while (true) {
                try {
                    socket.connect();
                } catch (Exception e) {
                    Log.e("Error: ", "Socket's accept() method failed", e);
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(BluetoothConnectActivity.ResponseReceiver.CONNECT_FAIL);
                    broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    sendBroadcast(broadcastIntent);
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            socket.close();
            if (mmInStream != null)
                mmInStream.close();
            if (mmOutStream != null)
                mmOutStream.close();
        } catch (IOException e) {
            Log.e("Error", "Could not close the connect socket", e);
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
        byte[] mmBuffer = new byte[1024];
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
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(MainActivity.ResponseReceiver.DISCONNECT_SUCCESS);
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                sendBroadcast(broadcastIntent);
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
