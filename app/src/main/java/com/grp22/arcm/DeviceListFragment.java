package com.grp22.arcm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;

public class DeviceListFragment extends Fragment {

    private static BluetoothAdapter BTAdapter;
    private ArrayList<DeviceItem> deviceItemList;
    private DeviceItemRecyclerViewAdapter mAdapter;
    private FrameLayout placeholder;
    private RecyclerView deviceItemListView;
    private ToggleButton scan, socket;

    private final BroadcastReceiver bcReciever = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Create a new device item
                DeviceItem newDevice = new DeviceItem(device.getName(), device.getAddress(), device.getBondState());
                // Add it to our adapter
                mAdapter.add(newDevice);
            }
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action) && scan.isChecked()) {
                scan.setChecked(false);
            }
        }
    };

    public static OnFragmentInteractionListener mListener;

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

        deviceItemList = new ArrayList<>();

        mAdapter = new DeviceItemRecyclerViewAdapter(deviceItemList, getContext());
        mAdapter.setHasStableIds(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_device_list, container, false);

        placeholder = (FrameLayout) view.findViewById(R.id.placeholder);
        deviceItemListView = (RecyclerView) view.findViewById(R.id.device_list);
        deviceItemListView.setLayoutManager(new LinearLayoutManager(getContext()) {
        });
        deviceItemListView.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.border));
        deviceItemListView.setAdapter(mAdapter);

        scan = (ToggleButton) view.findViewById(R.id.scan);
        scan.setText("scan");
        scan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                if (isChecked) {
                    if (socket.isChecked()) {
                        socket.setChecked(false);
                        mListener.stopConnection();
                    }
                    mAdapter.clear();
                    getActivity().registerReceiver(bcReciever, filter);
                    BTAdapter.startDiscovery();
                    placeholder.removeView(view.findViewById(R.id.start_screen));
                } else {
                    getActivity().unregisterReceiver(bcReciever);
                    BTAdapter.cancelDiscovery();
                }
            }
        });

        socket = (ToggleButton) view.findViewById(R.id.toggle_socket);
        socket.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (scan.isChecked()) {
                        scan.setChecked(false);
                    }
                    int selectedPosition = mAdapter.getSelectedPosition();
                    if (selectedPosition < 0) {
                        Toast.makeText(getContext(), "Please select a device first", Toast.LENGTH_SHORT).show();
                        socket.setChecked(false);
                    }
                    else {
                        mListener.startConnection(mAdapter.getSelectedAddress());
                    }
                } else {
                    mListener.stopConnection();
                }
            }
        });

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

    public interface OnFragmentInteractionListener {
        void startPairing(String address);
        void startConnection(String address);
        void stopConnection();
    }
}
