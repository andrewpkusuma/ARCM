package com.grp22.arcm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

public class BluetoothConnectActivity extends AppCompatActivity implements DeviceListFragment.OnFragmentInteractionListener {

    public static final int REQUEST_BLUETOOTH = 420;
    private SharedPreferences sharedPreferences;
    private DeviceListFragment mDeviceListFragment;
    private BluetoothAdapter BTAdapter;
    private ResponseReceiver receiver;
    private boolean isRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_connect);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!BTAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        mDeviceListFragment = DeviceListFragment.newInstance(BTAdapter);
        fragmentManager.beginTransaction().replace(R.id.container, mDeviceListFragment).commit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothConnectService.CONNECT_SUCCESS);
        filter.addAction(BluetoothConnectService.CONNECT_FAIL);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        if (!isRegistered) {
            receiver = new ResponseReceiver();
            registerReceiver(receiver, filter);
            isRegistered = true;
            Log.d("Registered", "hi");
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRegistered) {
            unregisterReceiver(receiver);
            Log.d("Unregistered", "bye");
            isRegistered = false;
        }
        mDeviceListFragment.resetView();
    }

    @Override
    public void startPairing(String address) {
        if (BTAdapter.isDiscovering())
            BTAdapter.cancelDiscovery();
        BluetoothDevice BTDevice = BTAdapter.getRemoteDevice(address);
        Log.d("Bond status", Integer.toString(BTDevice.getBondState()));
        if (BTDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            Log.d("I accept", "your nomination");
            final BluetoothDevice btDevice = BTDevice;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SystemClock.sleep(100);
                    btDevice.createBond();
                }
            }).start();
        }
    }

    @Override
    public void startConnection(String address, boolean connectAsServer) {
        if (BTAdapter.isDiscovering())
            BTAdapter.cancelDiscovery();
        Intent connectIntent = new Intent(this, BluetoothConnectService.class);
        connectIntent.putExtra("device", BTAdapter.getRemoteDevice(address));
        connectIntent.putExtra("timeout", sharedPreferences.getInt("timeout", 10));
        connectIntent.putExtra("connectionMode", connectAsServer);
        startService(connectIntent);
    }

    public class ResponseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothConnectService.CONNECT_SUCCESS)) {
                Toast.makeText(getApplicationContext(), "Successfully connected", Toast.LENGTH_SHORT).show();
                Intent begin = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(begin);
            }
            if (action.equals(BluetoothConnectService.CONNECT_FAIL)) {
                Toast.makeText(getApplicationContext(), "Fail to connect", Toast.LENGTH_SHORT).show();
                mDeviceListFragment.toggleConnect(true);
                mDeviceListFragment.toggleConnectionMode(true);
                mDeviceListFragment.toggleSelection(true);
            }
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED) && intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) == BluetoothDevice.BOND_BONDED) {
                Toast.makeText(getApplicationContext(), "Device paired", Toast.LENGTH_SHORT).show();
                mDeviceListFragment.rescan();
            }
        }
    }
}