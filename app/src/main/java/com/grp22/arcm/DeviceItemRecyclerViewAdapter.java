package com.grp22.arcm;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Andrew on 28/8/17.
 */

public class DeviceItemRecyclerViewAdapter extends RecyclerView.Adapter<DeviceItemRecyclerViewAdapter.DeviceItemViewHolder> {

    private List<DeviceItem> deviceItems;
    private Context context;
    private int selectedPosition = -1;
    private String selectedAddress = null;
    private boolean isSelectionEnabled = true;
    private DeviceListFragment fragment;

    public DeviceItemRecyclerViewAdapter(List<DeviceItem> deviceItems, Context context, DeviceListFragment fragment) {
        this.deviceItems = deviceItems;
        this.context = context;
        this.fragment = fragment;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerView.invalidate();
    }

    @Override
    public DeviceItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        DeviceItemViewHolder viewHolder = new DeviceItemViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(final DeviceItemViewHolder holder, final int position) {
        if (position == selectedPosition) {
            Log.d("Aku biru", Integer.toString(position));
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_light));
        } else {
            Log.d("Aku putih", Integer.toString(position));
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cardview_light_background));
            holder.card.setRadius(context.getResources().getDimension(R.dimen.cardview_default_radius));
        }
        holder.card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSelectionEnabled) {
                    if (selectedPosition != holder.getAdapterPosition()) {
                        int previousPosition = selectedPosition;
                        selectedPosition = holder.getAdapterPosition();
                        selectedAddress = deviceItems.get(selectedPosition).getAddress();
                        Log.d("Selected position", Integer.toString(selectedPosition));
                        notifyItemChanged(previousPosition);
                        notifyItemChanged(selectedPosition);
                        fragment.toggleConnect(true);//
                    }
                }
            }
        });

        holder.deviceDetails.setText(deviceItems.get(position).getDeviceName() + "\n" + deviceItems.get(position).getAddress());
        holder.deviceImage.setImageResource(R.drawable.ic_devices_black_48px);

        if (deviceItems.get(position).getStatus() == BluetoothDevice.BOND_NONE) {
            holder.connect.setText("Pair");
            holder.connect.setEnabled(true);
        } else {
            holder.connect.setText("Paired");
            holder.connect.setEnabled(false);
        }
        holder.connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DeviceListFragment.mListener.startPairing(deviceItems.get(position).getAddress());
                notifyItemChanged(position);
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceItems.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void add(DeviceItem deviceItem) {
        for (DeviceItem d : deviceItems)
            if (d.getAddress().equals(deviceItem.getAddress()))
                return;
        deviceItems.add(deviceItem);
        notifyDataSetChanged();
    }

    public void clear() {
        selectedPosition = -1;
        Log.d("Selected position", Integer.toString(selectedPosition));
        selectedAddress = null;
        deviceItems.clear();
        fragment.toggleConnect(false);
        notifyDataSetChanged();
    }

    public String getSelectedAddress() {
        return selectedAddress;
    }

    public void toggleSelection(boolean toggle) {
        isSelectionEnabled = toggle;
    }

    public class DeviceItemViewHolder extends RecyclerView.ViewHolder {

        public CardView card;
        public TextView deviceDetails;
        public ImageView deviceImage;
        public Button connect;

        DeviceItemViewHolder(View v) {
            super(v);
            card = (CardView) v.findViewById(R.id.card);
            deviceDetails = (TextView) v.findViewById(R.id.device_details);
            deviceImage = (ImageView) v.findViewById(R.id.device_image);
            connect = (Button) v.findViewById(R.id.connect);
        }
    }
}
