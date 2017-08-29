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

    public DeviceItemRecyclerViewAdapter(List<DeviceItem> deviceItems, Context context) {
        this.deviceItems = deviceItems;
        this.context = context;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    @Override
    public DeviceItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        DeviceItemViewHolder viewHolder = new DeviceItemViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(final DeviceItemViewHolder holder, final int position) {
        Log.d("Ini", "kepanggil");
        final int currentPosition = holder.getAdapterPosition();
        if (currentPosition == selectedPosition) {
            holder.card.setBackgroundResource(android.R.color.holo_blue_light);
        } else {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cardview_light_background));
            holder.card.setRadius(context.getResources().getDimension(R.dimen.cardview_default_radius));
        }
        holder.card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("Adapter position", Integer.toString(holder.getAdapterPosition()));
                if (isSelectionEnabled) {
                    if (currentPosition != selectedPosition) {
                        selectedPosition = currentPosition;
                        selectedAddress = deviceItems.get(selectedPosition).getAddress();
                        Log.d("Selected position", Integer.toString(selectedPosition));
                        notifyItemChanged(currentPosition);
                    } else {
                        clearSelection();
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
        deviceItems.add(deviceItem);
        notifyDataSetChanged();
    }

    public void clear() {
        int size = deviceItems.size();
        for (int i = 0; i < size; i++) {
            deviceItems.remove(i);
            notifyDataSetChanged();
        }
    }

    public void clearSelection() {
        int previousSelection = selectedPosition;
        selectedPosition = -1;
        selectedAddress = null;
        Log.d("Selected position", Integer.toString(selectedPosition));
        notifyItemChanged(previousSelection);
        notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return this.selectedPosition;
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
