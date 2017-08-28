package com.grp22.arcm;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.CardView;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Andrew on 28/8/17.
 */

public class DeviceItemRecyclerViewAdapter extends RecyclerView.Adapter<DeviceItemRecyclerViewAdapter.DeviceItemViewHolder>{

    private List<DeviceItem> deviceItems;
    private Context context;
    private int selectedPosition = -1;
    private String selectedAddress = null;

    public DeviceItemRecyclerViewAdapter(List<DeviceItem> deviceItems, Context context) {
        this.deviceItems = deviceItems;
        this.context = context;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    public class DeviceItemViewHolder extends RecyclerView.ViewHolder {

        public CardView card;
        public TextView deviceDetails;
        public ImageView deviceImage;
        public Button connect;
        public Button select;

        DeviceItemViewHolder(View v) {
            super(v);
            card = (CardView) v.findViewById(R.id.card);
            deviceDetails = (TextView) v.findViewById(R.id.device_details);
            deviceImage = (ImageView) v.findViewById(R.id.device_image);
            connect = (Button) v.findViewById(R.id.connect);
            select = (Button) v.findViewById(R.id.select);
        }
    }

    @Override
    public DeviceItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        DeviceItemViewHolder viewHolder = new DeviceItemViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(final DeviceItemViewHolder holder, final int position) {
        holder.deviceDetails.setText(deviceItems.get(position).getDeviceName() + "\n" + deviceItems.get(position).getAddress());
        holder.deviceImage.setImageResource(R.drawable.ic_devices_black_48px);
        if (position != selectedPosition) {
            holder.select.setText("Select");
            holder.select.setEnabled(true);
        }
        else {
            holder.select.setText("Selected");
            holder.select.setEnabled(false);
        }
        holder.select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedPosition = position;
                selectedAddress = deviceItems.get(selectedPosition).getAddress();
                holder.select.setText("Selected");
                holder.select.setEnabled(false);
                notifyDataSetChanged();
            }
        });
        if (deviceItems.get(position).getStatus() == BluetoothDevice.BOND_NONE) {
            holder.connect.setText("Pair");
            holder.connect.setEnabled(true);
        }
        else {
            holder.connect.setText("Paired");
            holder.connect.setEnabled(false);
        }
        holder.connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DeviceListFragment.mListener.startPairing(deviceItems.get(position).getAddress());
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceItems.size();
    }

    public void add(DeviceItem deviceItem) {
        deviceItems.add(deviceItem);
        notifyDataSetChanged();
    }

    public void clear() {
        deviceItems.clear();
        notifyDataSetChanged();
    }

    public void clearSelected() {
        this.selectedPosition = -1;
        this.selectedAddress = null;
    }

    public int getSelectedPosition() {
        return this.selectedPosition;
    }

    public String getSelectedAddress() {
        return selectedAddress;
    }
}
