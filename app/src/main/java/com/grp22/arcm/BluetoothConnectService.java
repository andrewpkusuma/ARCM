package com.grp22.arcm;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.IOException;
import java.util.StringTokenizer;
import java.util.UUID;

import static com.grp22.arcm.R.id.connect;

/**
 * Created by Andrew on 28/8/17.
 */

public class BluetoothConnectService extends IntentService {
    public static final String PARAM_ADDRESS = "a";
    public static final String PARAM_OUT_MSG = "omsg";

    private final BluetoothServerSocket mmServerSocket;

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
        BluetoothSocket socket = null;
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
                broadcastIntent.setAction(BluetoothConnectActivity.ResponseReceiver.ACTION_RESP);
                broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
                broadcastIntent.putExtra(PARAM_OUT_MSG, "hoopla");
                sendBroadcast(broadcastIntent);
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mmServerSocket.close();
        } catch (IOException e) {
            Log.e("Error", "Could not close the connect socket", e);
        }
    }
}
