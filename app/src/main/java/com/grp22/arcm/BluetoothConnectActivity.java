package com.grp22.arcm;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;

public class BluetoothConnectActivity extends AppCompatActivity implements DeviceListFragment.OnFragmentInteractionListener {

    private DeviceListFragment mDeviceListFragment;
    private BluetoothAdapter BTAdapter;
    public static Intent connectIntent;
    private ResponseReceiver receiver;

    public static final int REQUEST_BLUETOOTH = 420;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_connect);

        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!BTAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        mDeviceListFragment = DeviceListFragment.newInstance(BTAdapter);
        fragmentManager.beginTransaction().replace(R.id.container, mDeviceListFragment).commit();

        IntentFilter filter = new IntentFilter(ResponseReceiver.ACTION_RESP);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new ResponseReceiver();
        registerReceiver(receiver, filter);
    }

    @Override
    public void startPairing(String address) {
        if (BTAdapter.isDiscovering())
            BTAdapter.cancelDiscovery();
        BluetoothDevice BTDevice = BTAdapter.getRemoteDevice(address);
        if (BTDevice.getBondState() == BluetoothDevice.BOND_NONE)
            BTDevice.createBond();
    }

    @Override
    public void startConnection(String address) {
        if (BTAdapter.isDiscovering())
            BTAdapter.cancelDiscovery();
        connectIntent = new Intent(this, BluetoothConnectService.class);
        connectIntent.putExtra(BluetoothConnectService.PARAM_ADDRESS, address);
        startService(connectIntent);
    }

    @Override
    public void stopConnection() {
        stopService(connectIntent);
    }

    public class ResponseReceiver extends BroadcastReceiver {
        public static final String ACTION_RESP =
                "com.grp22.arcm.CONNECTION_SUCCESSFUL";

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(), "Connection Successful", Toast.LENGTH_SHORT).show();
            Intent begin = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(begin);
        }
    }
}
