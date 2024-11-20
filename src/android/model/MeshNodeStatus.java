package com.megster.cordova.ble.central.model;

import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import io.objectbox.relation.ToMany;

public class MeshNodeStatus {
    private int meshAddress;
    private boolean onOff;
    private int lum;
    private int temp;
    boolean isOnline;
    private ArrayList<NodeSensorState> sensorStates;

    private EventsCallback mEventsCallback;

    public static final String MESH_NODE_STATUS_EVENT = "meshNodeStatusEvent";

    public MeshNodeStatus(int meshAddress) {
        this.meshAddress = meshAddress;
        this.sensorStates = new ArrayList<>();
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
        // this.mEventsCallback.onChange(this);
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

    public void setSensorState(SparseArray<byte[]> sensorData) {
        try {
            NodeSensorState st;
            this.sensorStates.clear();

            for (int i = 0; i < sensorData.size(); i++) {
                st = new NodeSensorState();
                st.propertyID = sensorData.keyAt(i);
                st.state = sensorData.valueAt(i);
                sensorStates.add(st);
            }
            this.mEventsCallback.onChange(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<NodeSensorState> getSensorStateList() {
        return sensorStates;
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
