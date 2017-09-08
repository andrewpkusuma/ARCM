package com.grp22.arcm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class BluetoothConnectActivity extends AppCompatActivity implements DeviceListFragment.OnFragmentInteractionListener {

    private DeviceListFragment mDeviceListFragment;
    private BluetoothAdapter BTAdapter;
    private ResponseReceiver receiver;

    private boolean isRegistered = false;

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
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothConnectService.CONNECT_SUCCESS);
        filter.addAction(BluetoothConnectService.CONNECT_FAIL);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new ResponseReceiver();
        registerReceiver(receiver, filter);
        isRegistered = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRegistered)
            unregisterReceiver(receiver);
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
        Intent connectIntent = new Intent(this, BluetoothConnectService.class);
        connectIntent.putExtra("device", BTAdapter.getRemoteDevice(address));
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
                mDeviceListFragment.setSelection(true);
            }
        }
    }
}
