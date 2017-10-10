package com.grp22.arcm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Set;

public class DeviceListFragment extends Fragment {

    public static OnFragmentInteractionListener mListener;
    private static BluetoothAdapter BTAdapter;
    public ArrayList<DeviceItem> deviceItemList;
    private DeviceItemRecyclerViewAdapter mAdapter;
    private LinearLayout startScreen;
    private RecyclerView deviceItemListView;
    private RadioGroup connectMethod;
    private boolean connectAsServer;
    private ToggleButton scan;
    private final BroadcastReceiver bcReciever = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Create a new device item
                DeviceItem newDevice = new DeviceItem(device.getName(), device.getAddress(), device.getBondState());
                // Add it to our adapter
                mAdapter.toggleSelection(false);
                mAdapter.add(newDevice);
                mAdapter.toggleSelection(true);
            }
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action) && scan.isChecked()) {
                scan.setChecked(false);
            }
        }
    };
    private Button connect;

    public DeviceListFragment() {
        // Required empty public constructor
    }

    public static DeviceListFragment newInstance(BluetoothAdapter adapter) {
        DeviceListFragment fragment = new DeviceListFragment();
        BTAdapter = adapter;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_device_list, container, false);

        deviceItemList = new ArrayList<>();
        mAdapter = new DeviceItemRecyclerViewAdapter(deviceItemList, getContext(), this);
        mAdapter.setHasStableIds(true);

        deviceItemListView = (RecyclerView) view.findViewById(R.id.device_list);
        deviceItemListView.setLayoutManager(new LinearLayoutManager(getContext()) {
        });
        deviceItemListView.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border));
        deviceItemListView.setAdapter(mAdapter);

        connectMethod = (RadioGroup) view.findViewById(R.id.connection_mode);
        connectMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
                switch (i) {
                    case R.id.client_mode:
                        Log.d("Ini", "kepanggil");
                        connectAsServer = false;
                        break;
                    case R.id.server_mode:
                        Log.d("Ini", "kepanggil");
                        connectAsServer = true;
                        break;
                }
            }
        });

        startScreen = (LinearLayout) view.findViewById(R.id.start_screen);

        scan = (ToggleButton) view.findViewById(R.id.scan);
        scan.setText("scan");
        scan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                if (isChecked) {
                    mAdapter.clear();
                    Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        for (BluetoothDevice device : pairedDevices) {
                            DeviceItem newDevice = new DeviceItem(device.getName(), device.getAddress(), device.getBondState());
                            deviceItemList.add(newDevice);
                        }
                    }
                    deviceItemListView.setAdapter(mAdapter);
                    getActivity().registerReceiver(bcReciever, filter);
                    BTAdapter.startDiscovery();
                    startScreen.setVisibility(View.GONE);
                } else {
                    getActivity().unregisterReceiver(bcReciever);
                    BTAdapter.cancelDiscovery();
                }
            }
        });

        connect = (Button) view.findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (scan.isChecked()) {
                    scan.setChecked(false);
                }
                mAdapter.toggleSelection(false);
                connect.setEnabled(false);
                toggleConnectionMode(false);
                mListener.startConnection(mAdapter.getSelectedAddress(), connectAsServer);
                if (connectAsServer)
                    Toast.makeText(getContext(), "Waiting for device to connect...", Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(getContext(), "Connecting...", Toast.LENGTH_SHORT).show();
            }
        });
        connect.setEnabled(false);

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) getActivity();
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public void toggleConnect(boolean isEnabled) {
        connect.setEnabled(isEnabled);
    }

    public void toggleConnectionMode(boolean isEnabled) {
        for (int i = 0; i < connectMethod.getChildCount(); i++)
            connectMethod.getChildAt(i).setEnabled(isEnabled);
    }

    public void toggleSelection(boolean toggle) {
        mAdapter.toggleSelection(toggle);
    }

    public void resetView() {
        mAdapter.clear();
        deviceItemListView.setAdapter(mAdapter);
        startScreen.setVisibility(View.VISIBLE);
        scan.setText("SCAN");
        toggleConnectionMode(true);
    }

    public void rescan() {
        resetView();
        scan.setChecked(true);
    }

    public interface OnFragmentInteractionListener {
        void startPairing(String address);

        void startConnection(String address, boolean connectAsServer);
    }
}
