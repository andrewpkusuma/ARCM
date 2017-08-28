package com.grp22.arcm;

/**
 * Created by Andrew on 28/8/17.
 */

public class DeviceItem {

    private String deviceName;
    private String address;
    private int status;

    public String getDeviceName() {
        return deviceName;
    }

    public int getStatus() {
        return status;
    }

    public String getAddress() {
        return address;
    }

    public DeviceItem(String name, String address, int status){
        this.deviceName = name;
        this.address = address;
        this.status= status;
    }
}
