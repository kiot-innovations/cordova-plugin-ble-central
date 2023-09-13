package com.megster.cordova.ble.central.model;

public class MeshNodeStatus {
    private int meshAddress;
    private boolean onOff;
    private int lum;
    private int temp;
    boolean isOnline;

    private EventsCallback mEventsCallback;

    public static final String MESH_NODE_STATUS_EVENT = "meshNodeStatusEvent";

    public MeshNodeStatus(int meshAddress) {
        this.meshAddress = meshAddress;
    }

    public void setOnOff(boolean onOff) {
        this.onOff = onOff;
        this.mEventsCallback.onChange(this);
    }

    public boolean getOnOff() {
        return onOff;
    }

    public void setOnlineStatus(boolean isOnline) {
        this.isOnline = isOnline;
//        this.mEventsCallback.onChange(this);
    }

    public boolean getOnlineStatus() {
        return isOnline;
    }

    public void setLum(int lum) {
        this.lum = lum;
        this.mEventsCallback.onChange(this);
    }

    public int getLum() {
        return lum;
    }

    public void setTemp(int temp) {
        this.temp = temp;
        this.mEventsCallback.onChange(this);
    }

    public int getTemp() {
        return temp;
    }

    public int getMeshAddress() {
        return meshAddress;
    }

    public void setEventsCallback(EventsCallback mEventsCallback) {
        this.mEventsCallback = mEventsCallback;
    }

    public interface EventsCallback {
        void onChange(MeshNodeStatus nodeStatus);
    }
}
