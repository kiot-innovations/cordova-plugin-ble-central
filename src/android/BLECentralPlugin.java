// (c) 2014-2016 Don Coleman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.megster.cordova.ble.central;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Build;

import android.provider.Settings;
import android.telecom.Call;
import android.util.Log;

//import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megster.cordova.ble.central.model.AppSettings;
import com.megster.cordova.ble.central.model.GroupInfo;
import com.megster.cordova.ble.central.model.MeshInfo;
import com.megster.cordova.ble.central.model.MeshNetKey;
import com.megster.cordova.ble.central.model.NetworkingDevice;
import com.megster.cordova.ble.central.model.NetworkingState;
import com.megster.cordova.ble.central.model.NodeInfo;
import com.megster.cordova.ble.central.model.NodeStatusChangedEvent;
import com.megster.cordova.ble.central.model.OnlineState;
import com.megster.cordova.ble.central.model.PrivateDevice;
import com.megster.cordova.ble.central.model.UnitConvert;
import com.megster.cordova.ble.central.model.MeshNodeStatus;
import com.megster.cordova.ble.central.model.json.MeshStorage;
import com.megster.cordova.ble.central.model.json.MeshStorageService;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.access.BindingBearer;
import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.core.message.MeshSigModel;
import com.telink.ble.mesh.core.message.NotificationMessage;
import com.telink.ble.mesh.core.message.config.ConfigStatus;
import com.telink.ble.mesh.core.message.config.ModelPublicationSetMessage;
import com.telink.ble.mesh.core.message.config.ModelPublicationStatusMessage;
import com.telink.ble.mesh.core.message.config.ModelSubscriptionSetMessage;
import com.telink.ble.mesh.core.message.config.ModelSubscriptionStatusMessage;
import com.telink.ble.mesh.core.message.config.NodeResetMessage;
import com.telink.ble.mesh.core.message.config.NodeResetStatusMessage;
import com.telink.ble.mesh.core.message.generic.LevelGetMessage;
import com.telink.ble.mesh.core.message.generic.OnOffGetMessage;
import com.telink.ble.mesh.core.message.generic.OnOffSetMessage;
import com.telink.ble.mesh.core.message.lighting.CtlTemperatureSetMessage;
import com.telink.ble.mesh.core.message.lighting.LightnessSetMessage;
import com.telink.ble.mesh.core.message.time.TimeSetMessage;
import com.telink.ble.mesh.entity.BindingDevice;
import com.telink.ble.mesh.entity.CompositionData;
import com.telink.ble.mesh.entity.ConnectionFilter;
import com.telink.ble.mesh.entity.ModelPublication;
import com.telink.ble.mesh.entity.ProvisioningDevice;
import com.telink.ble.mesh.foundation.Event;
import com.telink.ble.mesh.foundation.EventListener;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.foundation.MeshController;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.event.AutoConnectEvent;
import com.telink.ble.mesh.foundation.event.BindingEvent;
import com.telink.ble.mesh.foundation.event.GattOtaEvent;
import com.telink.ble.mesh.foundation.event.MeshEvent;
import com.telink.ble.mesh.foundation.event.NetworkInfoUpdateEvent;
import com.telink.ble.mesh.foundation.event.OnlineStatusEvent;
import com.telink.ble.mesh.foundation.event.ProvisioningEvent;
import com.telink.ble.mesh.foundation.event.StatusNotificationEvent;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.foundation.parameter.BindingParameters;
import com.telink.ble.mesh.foundation.parameter.GattOtaParameters;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.FileSystem;
import com.telink.ble.mesh.util.LogInfo;
import com.telink.ble.mesh.util.MeshLogger;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteOrder;
import java.util.*;

import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class BLECentralPlugin extends CordovaPlugin implements EventListener<String> {
  // permissions
  private static final String ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"; // API 29
  private static final String BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"; // API 31
  private static final String BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"; // API 31

  // actions
  private static final String SCAN = "scan";
  private static final String START_SCAN = "startScan";
  private static final String STOP_SCAN = "stopScan";
  private static final String START_SCAN_WITH_OPTIONS = "startScanWithOptions";
  private static final String BONDED_DEVICES = "bondedDevices";
  private static final String LIST = "list";

  private static final String CONNECT = "connect";
  private static final String AUTOCONNECT = "autoConnect";
  private static final String DISCONNECT = "disconnect";

  private static final String QUEUE_CLEANUP = "queueCleanup";
  private static final String SET_PIN = "setPin";

  private static final String REQUEST_MTU = "requestMtu";
  private static final String REQUEST_CONNECTION_PRIORITY = "requestConnectionPriority";
  private final String CONNECTION_PRIORITY_HIGH = "high";
  private final String CONNECTION_PRIORITY_LOW = "low";
  private final String CONNECTION_PRIORITY_BALANCED = "balanced";
  private static final String REFRESH_DEVICE_CACHE = "refreshDeviceCache";

  private static final String READ = "read";
  private static final String WRITE = "write";
  private static final String WRITE_WITHOUT_RESPONSE = "writeWithoutResponse";

  private static final String READ_RSSI = "readRSSI";

  private static final String START_NOTIFICATION = "startNotification"; // register for characteristic notification
  private static final String STOP_NOTIFICATION = "stopNotification"; // remove characteristic notification

  private static final String IS_ENABLED = "isEnabled";
  private static final String IS_LOCATION_ENABLED = "isLocationEnabled";
  private static final String IS_CONNECTED = "isConnected";

  private static final String SETTINGS = "showBluetoothSettings";
  private static final String ENABLE = "enable";

  private static final String START_STATE_NOTIFICATIONS = "startStateNotifications";
  private static final String STOP_STATE_NOTIFICATIONS = "stopStateNotifications";

  private static final String MESH_PREFIX = "mesh_";
  private static final String OPEN_L2CAP = "openL2Cap";
  private static final String CLOSE_L2CAP = "closeL2Cap";
  private static final String RECEIVE_L2CAP = "receiveDataL2Cap";
  private static final String WRITE_L2CAP = "writeL2Cap";

  private static final String START_LOCATION_STATE_NOTIFICATIONS = "startLocationStateNotifications";
  private static final String STOP_LOCATION_STATE_NOTIFICATIONS = "stopLocationStateNotifications";

  // callbacks
  CallbackContext discoverCallback;
  private CallbackContext enableBluetoothCallback;

  private static final String TAG = "BLEPlugin";
  private static final int REQUEST_ENABLE_BLUETOOTH = 1;

  BluetoothAdapter bluetoothAdapter;
  BluetoothLeScanner bluetoothLeScanner;

  // key is the MAC Address
  Map<String, Peripheral> peripherals = new LinkedHashMap<String, Peripheral>();

  // scan options
  boolean reportDuplicates = false;
  private boolean isPubSetting = false;

  // Android 23 requires new permissions for BluetoothLeScanner.startScan()
  private static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
  private static final String ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;

  private static final int REQUEST_ACCESS_COARSE_LOCATION = 2;
  private static final int REQUEST_ACCESS_FINE_LOCATION = 3;
  private static final int REQUEST_BLUETOOTH_SCAN = 2;
  private static final int REQUEST_BLUETOOTH_CONNECT = 3;
  private static final int REQUEST_BLUETOOTH_CONNECT_AUTO = 4;
  private static final int REQUEST_GET_BONDED_DEVICES = 5;
  private static final int REQUEST_LIST_KNOWN_DEVICES = 6;
  private static int COMPILE_SDK_VERSION = -1;
  private CallbackContext permissionCallback;
  private String deviceMacAddress;
  private UUID[] serviceUUIDs;
  private byte[] mFirmware;
  private int scanSeconds;
  private int binPid;
  private ScanSettings scanSettings;

  // Bluetooth state notification
  CallbackContext stateCallback;
  BroadcastReceiver stateReceiver;
  Map<Integer, String> bluetoothStates = new Hashtable<Integer, String>() {
    {
      put(BluetoothAdapter.STATE_OFF, "off");
      put(BluetoothAdapter.STATE_TURNING_OFF, "turningOff");
      put(BluetoothAdapter.STATE_ON, "on");
      put(BluetoothAdapter.STATE_TURNING_ON, "turningOn");
    }
  };

  private boolean meshSdkInitialized = false;
  private TelinkMeshApplication meshHandler;
  DeviceProvisioning dp;
  private Gson mGson;

  private Handler mHandler = new Handler();

  CallbackContext locationStateCallback;
  BroadcastReceiver locationStateReceiver;

  @Override
  protected void pluginInitialize() {
    if (COMPILE_SDK_VERSION == -1) {
      Context context = cordova.getContext();
      COMPILE_SDK_VERSION = context.getApplicationContext().getApplicationInfo().targetSdkVersion;
    }
  }

  @Override
  public void onDestroy() {
    removeStateListener();
    removeLocationStateListener();
    for (Peripheral peripheral : peripherals.values()) {
      peripheral.disconnect();
    }
    super.onDestroy();
    TelinkMeshApplication.getInstance().removeEventListener(this);
    MeshService.getInstance().clear();
  }

  @Override
  public void onReset() {
    removeStateListener();
    removeLocationStateListener();
    for (Peripheral peripheral : peripherals.values()) {
      peripheral.disconnect();
    }
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    // if (TelinkMeshApplication.getInstance() != null) {
    // TelinkMeshApplication.getInstance().autoConnect();
    // }
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  @Override
  public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    LOG.d(TAG, "action = %s", action);

    if (bluetoothAdapter == null) {
      Activity activity = cordova.getActivity();
      boolean hardwareSupportsBLE = activity.getApplicationContext()
          .getPackageManager()
          .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
          Build.VERSION.SDK_INT >= 18;
      if (!hardwareSupportsBLE) {
        LOG.w(TAG, "This hardware does not support Bluetooth Low Energy.");
        callbackContext.error("This hardware does not support Bluetooth Low Energy.");
        return false;
      }
      BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
      bluetoothAdapter = bluetoothManager.getAdapter();
      bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    boolean validAction = true;

    if (action.equals(SCAN)) {

      UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
      int scanSeconds = args.getInt(1);
      resetScanOptions();
      findLowEnergyDevices(callbackContext, serviceUUIDs, scanSeconds);

    } else if (action.equals(START_SCAN)) {

      UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
      resetScanOptions();
      findLowEnergyDevices(callbackContext, serviceUUIDs, -1);

    } else if (action.equals(STOP_SCAN)) {

      bluetoothLeScanner.stopScan(leScanCallback);
      callbackContext.success();

    } else if (action.equals(LIST)) {

      listKnownDevices(callbackContext);

    } else if (action.equals(CONNECT)) {

      String macAddress = args.getString(0);
      connect(callbackContext, macAddress);

    } else if (action.equals(AUTOCONNECT)) {

      String macAddress = args.getString(0);
      autoConnect(callbackContext, macAddress);

    } else if (action.equals(DISCONNECT)) {

      String macAddress = args.getString(0);
      disconnect(callbackContext, macAddress);

    } else if (action.equals(QUEUE_CLEANUP)) {

      String macAddress = args.getString(0);
      queueCleanup(callbackContext, macAddress);

    } else if (action.equals(SET_PIN)) {

      String pin = args.getString(0);
      setPin(callbackContext, pin);

    } else if (action.equals(REQUEST_MTU)) {

      String macAddress = args.getString(0);
      int mtuValue = args.getInt(1);
      requestMtu(callbackContext, macAddress, mtuValue);

    } else if (action.equals(REQUEST_CONNECTION_PRIORITY)) {

      String macAddress = args.getString(0);
      String priority = args.getString(1);

      requestConnectionPriority(callbackContext, macAddress, priority);

    } else if (action.equals(REFRESH_DEVICE_CACHE)) {

      String macAddress = args.getString(0);
      long timeoutMillis = args.getLong(1);

      refreshDeviceCache(callbackContext, macAddress, timeoutMillis);

    } else if (action.equals(READ)) {

      String macAddress = args.getString(0);
      UUID serviceUUID = uuidFromString(args.getString(1));
      UUID characteristicUUID = uuidFromString(args.getString(2));
      read(callbackContext, macAddress, serviceUUID, characteristicUUID);

    } else if (action.equals(READ_RSSI)) {

      String macAddress = args.getString(0);
      readRSSI(callbackContext, macAddress);

    } else if (action.equals(WRITE)) {

      String macAddress = args.getString(0);
      UUID serviceUUID = uuidFromString(args.getString(1));
      UUID characteristicUUID = uuidFromString(args.getString(2));
      byte[] data = args.getArrayBuffer(3);
      int type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
      write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type);

    } else if (action.equals(WRITE_WITHOUT_RESPONSE)) {

      String macAddress = args.getString(0);
      UUID serviceUUID = uuidFromString(args.getString(1));
      UUID characteristicUUID = uuidFromString(args.getString(2));
      byte[] data = args.getArrayBuffer(3);
      int type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
      write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type);

    } else if (action.equals(START_NOTIFICATION)) {

      String macAddress = args.getString(0);
      UUID serviceUUID = uuidFromString(args.getString(1));
      UUID characteristicUUID = uuidFromString(args.getString(2));
      registerNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID);

    } else if (action.equals(STOP_NOTIFICATION)) {

      String macAddress = args.getString(0);
      UUID serviceUUID = uuidFromString(args.getString(1));
      UUID characteristicUUID = uuidFromString(args.getString(2));
      removeNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID);

    } else if (action.equals(IS_ENABLED)) {

      if (bluetoothAdapter.isEnabled()) {
        callbackContext.success();
      } else {
        callbackContext.error("Bluetooth is disabled.");
      }

    } else if (action.equals(IS_LOCATION_ENABLED)) {

      if (locationServicesEnabled()) {
        callbackContext.success();
      } else {
        callbackContext.error("Location services disabled.");
      }

    } else if (action.equals(IS_CONNECTED)) {

      String macAddress = args.getString(0);

      if (peripherals.containsKey(macAddress) && peripherals.get(macAddress).isConnected()) {
        callbackContext.success();
      } else {
        callbackContext.error("Not connected");
      }

    } else if (action.equals(SETTINGS)) {

      Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
      cordova.getActivity().startActivity(intent);
      callbackContext.success();

    } else if (action.equals(ENABLE)) {

      enableBluetoothCallback = callbackContext;
      Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);

    } else if (action.equals(START_STATE_NOTIFICATIONS)) {

      if (this.stateCallback != null) {
        callbackContext.error("State callback already registered.");
      } else {
        this.stateCallback = callbackContext;
        addStateListener();
        sendBluetoothStateChange(bluetoothAdapter.getState());
      }

    } else if (action.equals(STOP_STATE_NOTIFICATIONS)) {

      if (this.stateCallback != null) {
        // Clear callback in JavaScript without actually calling it
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(false);
        this.stateCallback.sendPluginResult(result);
        this.stateCallback = null;
      }
      removeStateListener();
      callbackContext.success();

    } else if (action.equals(START_LOCATION_STATE_NOTIFICATIONS)) {

      if (this.locationStateCallback != null) {
        callbackContext.error("Location state callback already registered.");
      } else {
        this.locationStateCallback = callbackContext;
        addLocationStateListener();
        sendLocationStateChange();
      }

    } else if (action.equals(STOP_LOCATION_STATE_NOTIFICATIONS)) {

      if (this.locationStateCallback != null) {
        // Clear callback in JavaScript without actually calling it
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(false);
        this.locationStateCallback.sendPluginResult(result);
        this.locationStateCallback = null;
      }
      removeLocationStateListener();
      callbackContext.success();

    } else if (action.equals(START_SCAN_WITH_OPTIONS)) {
      UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
      JSONObject options = args.getJSONObject(1);

      resetScanOptions();
      this.reportDuplicates = options.optBoolean("reportDuplicates", false);
      ScanSettings.Builder scanSettings = new ScanSettings.Builder();

      switch (options.optString("scanMode", "")) {
        case "":
          break;
        case "lowPower":
          scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
          break;
        case "balanced":
          scanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
          break;
        case "lowLatency":
          scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
          break;
        case "opportunistic":
          scanSettings.setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC);
          break;
        default:
          callbackContext.error("scanMode must be one of: lowPower | balanced | lowLatency | opportunistic");
          validAction = false;
          break;
      }

      switch (options.optString("callbackType", "")) {
        case "":
          break;
        case "all":
          scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
          break;
        case "first":
          scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH);
          break;
        case "lost":
          scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST);
          break;
        default:
          callbackContext.error("callbackType must be one of: all | first | lost");
          validAction = false;
          break;
      }

      switch (options.optString("matchMode", "")) {
        case "":
          break;
        case "aggressive":
          scanSettings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
          break;
        case "sticky":
          scanSettings.setMatchMode(ScanSettings.MATCH_MODE_STICKY);
          break;
        default:
          callbackContext.error("matchMode must be one of: aggressive | sticky");
          validAction = false;
          break;
      }

      switch (options.optString("numOfMatches", "")) {
        case "":
          break;
        case "one":
          scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
          break;
        case "few":
          scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT);
          break;
        case "max":
          scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
          break;
        default:
          callbackContext.error("numOfMatches must be one of: one | few | max");
          validAction = false;
          break;
      }

      switch (options.optString("phy", "")) {
        case "":
          break;
        case "1m":
          scanSettings.setPhy(BluetoothDevice.PHY_LE_1M);
          break;
        case "coded":
          scanSettings.setPhy(BluetoothDevice.PHY_LE_CODED);
          break;
        case "all":
          scanSettings.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
          break;
        default:
          callbackContext.error("phy must be one of: 1m | coded | all");
          validAction = false;
          break;
      }

      if (validAction) {
        String LEGACY = "legacy";
        if (!options.isNull(LEGACY))
          scanSettings.setLegacy(options.getBoolean(LEGACY));

        long reportDelay = options.optLong("reportDelay", -1);
        if (reportDelay >= 0L)
          scanSettings.setReportDelay(reportDelay);

        findLowEnergyDevices(callbackContext, serviceUUIDs, -1, scanSettings.build());
      }

    } else if (action.equals(BONDED_DEVICES)) {

      getBondedDevices(callbackContext);

    } else if (action.startsWith(MESH_PREFIX)) {
      java.lang.reflect.Method method;
      try {
        // method = this.getClass().getMethod(action);
        method = BLECentralPlugin.class.getMethod(action, CordovaArgs.class, CallbackContext.class);
      } catch (java.lang.SecurityException e) {
        LOG.d(TAG, "getMethod SecurityException = %s", e.toString());
        return false;

      } catch (java.lang.NoSuchMethodException e) {
        LOG.d(TAG, "getMethod NoSuchMethodException = %s", e.toString());
        return false;
      }

      try {
        method.invoke(this, args, callbackContext);
      } catch (java.lang.IllegalArgumentException e) {
        callbackContext.error(e.toString());
      } catch (java.lang.IllegalAccessException e) {
        callbackContext.error(e.toString());
      } catch (java.lang.reflect.InvocationTargetException e) {
        callbackContext.error(e.toString());
      }

    } else if (action.equals(OPEN_L2CAP)) {

      String macAddress = args.getString(0);
      int psm = args.getInt(1);
      JSONObject options = args.optJSONObject(2);
      boolean secureChannel = options != null && options.optBoolean("secureChannel", false);
      connectL2cap(callbackContext, macAddress, psm, secureChannel);

    } else if (action.equals(CLOSE_L2CAP)) {

      String macAddress = args.getString(0);
      int psm = args.getInt(1);
      disconnectL2cap(callbackContext, macAddress, psm);

    } else if (action.equals(WRITE_L2CAP)) {

      String macAddress = args.getString(0);
      int psm = args.getInt(1);
      byte[] data = args.getArrayBuffer(2);
      writeL2cap(callbackContext, macAddress, psm, data);

    } else if (action.equals(RECEIVE_L2CAP)) {

      String macAddress = args.getString(0);
      int psm = args.getInt(1);
      registerL2CapReceiver(callbackContext, macAddress, psm);

    } else {

      validAction = false;

    }

    return validAction;
  }

  private void getBondedDevices(CallbackContext callbackContext) {
    if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
      if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
        permissionCallback = callbackContext;
        PermissionHelper.requestPermission(this, REQUEST_GET_BONDED_DEVICES, BLUETOOTH_CONNECT);
        return;
      }
    }

    JSONArray bonded = new JSONArray();
    Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

    for (BluetoothDevice device : bondedDevices) {
      device.getBondState();
      int type = device.getType();

      // just low energy devices (filters out classic and unknown devices)
      if (type == DEVICE_TYPE_LE || type == DEVICE_TYPE_DUAL) {
        Peripheral p = new Peripheral(device);
        bonded.put(p.asJSONObject());
      }
    }

    callbackContext.success(bonded);
  }

  private UUID[] parseServiceUUIDList(JSONArray jsonArray) throws JSONException {
    List<UUID> serviceUUIDs = new ArrayList<UUID>();

    for (int i = 0; i < jsonArray.length(); i++) {
      String uuidString = jsonArray.getString(i);
      serviceUUIDs.add(uuidFromString(uuidString));
    }

    return serviceUUIDs.toArray(new UUID[jsonArray.length()]);
  }

  private void onBluetoothStateChange(Intent intent) {
    final String action = intent.getAction();

    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
      final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
      sendBluetoothStateChange(state);
      if (state == BluetoothAdapter.STATE_OFF) {
        // #894 When Bluetooth is physically turned off the whole process might die, so
        // the normal
        // onConnectionStateChange callbacks won't be invoked

        BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity()
            .getSystemService(Context.BLUETOOTH_SERVICE);
        for (Peripheral peripheral : peripherals.values()) {
          if (!peripheral.isConnected())
            continue;

          int connectedState = bluetoothManager.getConnectionState(peripheral.getDevice(),
              BluetoothProfile.GATT);
          if (connectedState == BluetoothProfile.STATE_DISCONNECTED) {
            peripheral.peripheralDisconnected("Bluetooth Disabled");
          }
        }
      }
    }
  }

  private void sendBluetoothStateChange(int state) {
    if (this.stateCallback != null) {
      PluginResult result = new PluginResult(PluginResult.Status.OK, this.bluetoothStates.get(state));
      result.setKeepCallback(true);
      this.stateCallback.sendPluginResult(result);
    }
  }

  private void addStateListener() {
    if (this.stateReceiver == null) {
      this.stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          onBluetoothStateChange(intent);
        }
      };
    }

    try {
      IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      webView.getContext().registerReceiver(this.stateReceiver, intentFilter);
    } catch (Exception e) {
      LOG.e(TAG, "Error registering state receiver: " + e.getMessage(), e);
    }
  }

  private void removeStateListener() {
    if (this.stateReceiver != null) {
      try {
        webView.getContext().unregisterReceiver(this.stateReceiver);
      } catch (Exception e) {
        LOG.e(TAG, "Error unregistering state receiver: " + e.getMessage(), e);
      }
    }
    this.stateCallback = null;
    this.stateReceiver = null;
  }

  private void onLocationStateChange(Intent intent) {
    final String action = intent.getAction();

    if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(action)) {
      sendLocationStateChange();
    }
  }

  private void sendLocationStateChange() {
    if (this.locationStateCallback != null) {
      PluginResult result = new PluginResult(PluginResult.Status.OK, locationServicesEnabled());
      result.setKeepCallback(true);
      this.locationStateCallback.sendPluginResult(result);
    }
  }

  private void addLocationStateListener() {
    if (this.locationStateReceiver == null) {
      this.locationStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          onLocationStateChange(intent);
        }
      };
    }

    try {
      IntentFilter intentFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
      intentFilter.addAction(Intent.ACTION_PROVIDER_CHANGED);
      webView.getContext().registerReceiver(this.locationStateReceiver, intentFilter);
    } catch (Exception e) {
      LOG.e(TAG, "Error registering location state receiver: " + e.getMessage(), e);
    }
  }

  private void removeLocationStateListener() {
    if (this.locationStateReceiver != null) {
      try {
        webView.getContext().unregisterReceiver(this.locationStateReceiver);
      } catch (Exception e) {
        LOG.e(TAG, "Error unregistering location state receiver: " + e.getMessage(), e);
      }
    }
    this.locationStateCallback = null;
    this.locationStateReceiver = null;
  }

  private void connect(CallbackContext callbackContext, String macAddress) {
    if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
      if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
        permissionCallback = callbackContext;
        deviceMacAddress = macAddress;
        PermissionHelper.requestPermission(this, REQUEST_BLUETOOTH_CONNECT, BLUETOOTH_CONNECT);
        return;
      }
    }

    if (!peripherals.containsKey(macAddress)
        && BLECentralPlugin.this.bluetoothAdapter.checkBluetoothAddress(macAddress)) {
      BluetoothDevice device = BLECentralPlugin.this.bluetoothAdapter.getRemoteDevice(macAddress);
      Peripheral peripheral = new Peripheral(device);
      peripherals.put(macAddress, peripheral);
    }

    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {
      // #894: BLE adapter state listener required so disconnect can be fired on BLE
      // disabled
      addStateListener();
      peripheral.connect(callbackContext, cordova.getActivity(), false);
    } else {
      callbackContext.error("Peripheral " + macAddress + " not found.");
    }

  }

  private void autoConnect(CallbackContext callbackContext, String macAddress) {

    if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
      if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
        permissionCallback = callbackContext;
        deviceMacAddress = macAddress;
        PermissionHelper.requestPermission(this, REQUEST_BLUETOOTH_CONNECT_AUTO, BLUETOOTH_CONNECT);
        return;
      }
    }

    Peripheral peripheral = peripherals.get(macAddress);

    // allow auto-connect to connect to devices without scanning
    if (peripheral == null) {
      if (BluetoothAdapter.checkBluetoothAddress(macAddress)) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        peripheral = new Peripheral(device);
        peripherals.put(device.getAddress(), peripheral);
      } else {
        callbackContext.error(macAddress + " is not a valid MAC address.");
        return;
      }
    }

    // #894: BLE adapter state listener required so disconnect can be fired on BLE
    // disabled
    addStateListener();
    peripheral.connect(callbackContext, cordova.getActivity(), true);

  }

  private void disconnect(CallbackContext callbackContext, String macAddress) {

    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {
      peripheral.disconnect();
      callbackContext.success();
    } else {
      String message = "Peripheral " + macAddress + " not found.";
      LOG.w(TAG, message);
      callbackContext.error(message);
    }

  }

  private void queueCleanup(CallbackContext callbackContext, String macAddress) {
    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {
      peripheral.queueCleanup();
    }
    callbackContext.success();
  }

  BroadcastReceiver broadCastReceiver;

  private void setPin(CallbackContext callbackContext, final String pin) {

    try {
      if (broadCastReceiver != null) {
        webView.getContext().unregisterReceiver(broadCastReceiver);
      }

      broadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();

          if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
            BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

            if (type == BluetoothDevice.PAIRING_VARIANT_PIN) {
              bluetoothDevice.setPin(pin.getBytes());
              abortBroadcast();
            }
          }
        }
      };

      IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
      intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
      webView.getContext().registerReceiver(broadCastReceiver, intentFilter);

      callbackContext.success("OK");
    } catch (Exception e) {
      callbackContext.error("Error: " + e.getMessage());
      return;
    }
  }

  private void requestMtu(CallbackContext callbackContext, String macAddress, int mtuValue) {
    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {
      peripheral.requestMtu(callbackContext, mtuValue);
    } else {
      String message = "Peripheral " + macAddress + " not found.";
      LOG.w(TAG, message);
      callbackContext.error(message);
    }
  }

  private void requestConnectionPriority(CallbackContext callbackContext, String macAddress, String priority) {
    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    if (!peripheral.isConnected()) {
      callbackContext.error("Peripheral " + macAddress + " is not connected.");
      return;
    }

    int androidPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
    if (priority.equals(CONNECTION_PRIORITY_LOW)) {
      androidPriority = BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
    } else if (priority.equals(CONNECTION_PRIORITY_BALANCED)) {
      androidPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
    } else if (priority.equals(CONNECTION_PRIORITY_HIGH)) {
      androidPriority = BluetoothGatt.CONNECTION_PRIORITY_HIGH;
    }
    peripheral.requestConnectionPriority(androidPriority);
    callbackContext.success();
  }

  private void refreshDeviceCache(CallbackContext callbackContext, String macAddress, long timeoutMillis) {

    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral != null) {
      peripheral.refreshDeviceCache(callbackContext, timeoutMillis);
    } else {
      String message = "Peripheral " + macAddress + " not found.";
      LOG.w(TAG, message);
      callbackContext.error(message);
    }
  }

  private void read(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    if (!peripheral.isConnected()) {
      callbackContext.error("Peripheral " + macAddress + " is not connected.");
      return;
    }

    // peripheral.readCharacteristic(callbackContext, serviceUUID,
    // characteristicUUID);
    peripheral.queueRead(callbackContext, serviceUUID, characteristicUUID);

  }

  private void readRSSI(CallbackContext callbackContext, String macAddress) {

    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    if (!peripheral.isConnected()) {
      callbackContext.error("Peripheral " + macAddress + " is not connected.");
      return;
    }
    peripheral.queueReadRSSI(callbackContext);
  }

  private void write(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID,
      byte[] data, int writeType) {

    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    if (!peripheral.isConnected()) {
      callbackContext.error("Peripheral " + macAddress + " is not connected.");
      return;
    }

    // peripheral.writeCharacteristic(callbackContext, serviceUUID,
    // characteristicUUID, data, writeType);
    peripheral.queueWrite(callbackContext, serviceUUID, characteristicUUID, data, writeType);

  }

  private void connectL2cap(CallbackContext callbackContext, String macAddress, int psm, boolean secureChannel) {
    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    if (!peripheral.isConnected()) {
      callbackContext.error("Peripheral " + macAddress + " is not connected.");
      return;
    }

    peripheral.connectL2cap(callbackContext, psm, secureChannel);
  }

  private void disconnectL2cap(CallbackContext callbackContext, String macAddress, int psm) {

    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {
      peripheral.disconnectL2Cap(callbackContext, psm);
    }

    callbackContext.success();

  }

  private void writeL2cap(CallbackContext callbackContext, String macAddress, int psm, byte[] data) {

    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    if (!peripheral.isL2capConnected(psm)) {
      callbackContext.error("Peripheral " + macAddress + " L2Cap is not connected.");
      return;
    }

    cordova.getThreadPool().execute(() -> peripheral.writeL2CapChannel(callbackContext, psm, data));

  }

  private void registerL2CapReceiver(CallbackContext callbackContext, String macAddress, int psm) {

    Peripheral peripheral = peripherals.get(macAddress);

    if (peripheral == null) {
      callbackContext.error("Peripheral " + macAddress + " not found.");
      return;
    }

    peripheral.registerL2CapReceiver(callbackContext, psm);

  }

  private void registerNotifyCallback(CallbackContext callbackContext, String macAddress, UUID serviceUUID,
      UUID characteristicUUID) {

    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {

      if (!peripheral.isConnected()) {
        callbackContext.error("Peripheral " + macAddress + " is not connected.");
        return;
      }

      // peripheral.setOnDataCallback(serviceUUID, characteristicUUID,
      // callbackContext);
      peripheral.queueRegisterNotifyCallback(callbackContext, serviceUUID, characteristicUUID);

    } else {

      callbackContext.error("Peripheral " + macAddress + " not found");

    }

  }

  private void removeNotifyCallback(CallbackContext callbackContext, String macAddress, UUID serviceUUID,
      UUID characteristicUUID) {

    Peripheral peripheral = peripherals.get(macAddress);
    if (peripheral != null) {

      if (!peripheral.isConnected()) {
        callbackContext.error("Peripheral " + macAddress + " is not connected.");
        return;
      }

      peripheral.queueRemoveNotifyCallback(callbackContext, serviceUUID, characteristicUUID);

    } else {

      callbackContext.error("Peripheral " + macAddress + " not found");

    }

  }

  private ScanCallback leScanCallback = new ScanCallback() {
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      LOG.w(TAG, "Scan Result");
      super.onScanResult(callbackType, result);
      BluetoothDevice device = result.getDevice();
      String address = device.getAddress();
      boolean alreadyReported = peripherals.containsKey(address) && !peripherals.get(address).isUnscanned();

      if (!alreadyReported) {

        Peripheral peripheral = new Peripheral(device, result.getRssi(), result.getScanRecord().getBytes());
        peripherals.put(device.getAddress(), peripheral);

        if (discoverCallback != null) {
          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, peripheral.asJSONObject());
          pluginResult.setKeepCallback(true);
          discoverCallback.sendPluginResult(pluginResult);
        }

      } else {
        Peripheral peripheral = peripherals.get(address);
        if (peripheral != null) {
          peripheral.update(result.getRssi(), result.getScanRecord().getBytes());
          if (reportDuplicates && discoverCallback != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, peripheral.asJSONObject());
            pluginResult.setKeepCallback(true);
            discoverCallback.sendPluginResult(pluginResult);
          }
        }
      }
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
      super.onBatchScanResults(results);
    }

    @Override
    public void onScanFailed(int errorCode) {
      super.onScanFailed(errorCode);
    }
  };

  private void findLowEnergyDevices(CallbackContext callbackContext, UUID[] serviceUUIDs, int scanSeconds) {
    findLowEnergyDevices(callbackContext, serviceUUIDs, scanSeconds, new ScanSettings.Builder().build());
  }

  private void findLowEnergyDevices(CallbackContext callbackContext, UUID[] serviceUUIDs, int scanSeconds,
      ScanSettings scanSettings) {

    if (!locationServicesEnabled() && Build.VERSION.SDK_INT < 31) {
      LOG.w(TAG, "Location Services are disabled");
    }

    List<String> missingPermissions = new ArrayList<String>();
    if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
      if (!PermissionHelper.hasPermission(this, BLUETOOTH_SCAN)) {
        missingPermissions.add(BLUETOOTH_SCAN);
      }
      if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
        missingPermissions.add(BLUETOOTH_CONNECT);
      }
    } else if (COMPILE_SDK_VERSION >= 29 && Build.VERSION.SDK_INT >= 29) { // (API 29) Build.VERSION_CODES.Q
      if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
        missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
      }

      String accessBackgroundLocation = this.preferences.getString("accessBackgroundLocation", "false");
      if (accessBackgroundLocation == "true"
          && !PermissionHelper.hasPermission(this, ACCESS_BACKGROUND_LOCATION)) {
        LOG.w(TAG, "ACCESS_BACKGROUND_LOCATION is being requested");
        missingPermissions.add(ACCESS_BACKGROUND_LOCATION);
      }
    } else {
      if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
        missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
      }
    }

    if (missingPermissions.size() > 0) {
      // save info so we can call this method again after permissions are granted
      permissionCallback = callbackContext;
      this.serviceUUIDs = serviceUUIDs;
      this.scanSeconds = scanSeconds;
      this.scanSettings = scanSettings;
      PermissionHelper.requestPermissions(this, REQUEST_BLUETOOTH_SCAN,
          missingPermissions.toArray(new String[0]));
      return;
    }
    if (!PermissionHelper.hasPermission(this, ACCESS_FINE_LOCATION)) {
      // save info so we can call this method again after permissions are granted
      permissionCallback = callbackContext;
      this.serviceUUIDs = serviceUUIDs;
      this.scanSeconds = scanSeconds;
      PermissionHelper.requestPermission(this, REQUEST_ACCESS_FINE_LOCATION, ACCESS_FINE_LOCATION);
      return;
    }

    // return error if already scanning
    if (bluetoothAdapter.isDiscovering()) {
      LOG.w(TAG, "Tried to start scan while already running.");
      callbackContext.error("Tried to start scan while already running.");
      return;
    }

    // clear non-connected cached peripherals
    for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator
        .hasNext();) {
      Map.Entry<String, Peripheral> entry = iterator.next();
      Peripheral device = entry.getValue();
      boolean connecting = device.isConnecting();
      if (connecting) {
        LOG.d(TAG, "Not removing connecting device: " + device.getDevice().getAddress());
      }
      if (!entry.getValue().isConnected() && !connecting) {
        iterator.remove();
      }
    }

    discoverCallback = callbackContext;
    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    List<ScanFilter> filters = new ArrayList<ScanFilter>();
    if (serviceUUIDs != null && serviceUUIDs.length > 0) {
      for (UUID uuid : serviceUUIDs) {
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(
            new ParcelUuid(uuid)).build();
        filters.add(filter);
      }
    }
    bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback);

    if (scanSeconds > 0) {
      Handler handler = new Handler();
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          LOG.d(TAG, "Stopping Scan");
          bluetoothLeScanner.stopScan(leScanCallback);
        }
      }, scanSeconds * 1000);
    }

    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
    result.setKeepCallback(true);
    callbackContext.sendPluginResult(result);
  }

  private boolean locationServicesEnabled() {
    int locationMode = 0;
    try {
      locationMode = Settings.Secure.getInt(cordova.getActivity().getContentResolver(),
          Settings.Secure.LOCATION_MODE);
    } catch (Settings.SettingNotFoundException e) {
      LOG.e(TAG, "Location Mode Setting Not Found", e);
    }
    return (locationMode > 0);
  }

  private void listKnownDevices(CallbackContext callbackContext) {
    if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
      if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
        permissionCallback = callbackContext;
        PermissionHelper.requestPermission(this, REQUEST_LIST_KNOWN_DEVICES, BLUETOOTH_CONNECT);
        return;
      }
    }

    JSONArray json = new JSONArray();

    // do we care about consistent order? will peripherals.values() be in order?
    for (Map.Entry<String, Peripheral> entry : peripherals.entrySet()) {
      Peripheral peripheral = entry.getValue();
      if (!peripheral.isUnscanned()) {
        json.put(peripheral.asJSONObject());
      }
    }

    PluginResult result = new PluginResult(PluginResult.Status.OK, json);
    callbackContext.sendPluginResult(result);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {

    if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

      if (resultCode == Activity.RESULT_OK) {
        LOG.d(TAG, "User enabled Bluetooth");
        if (enableBluetoothCallback != null) {
          enableBluetoothCallback.success();
        }
      } else {
        LOG.d(TAG, "User did *NOT* enable Bluetooth");
        if (enableBluetoothCallback != null) {
          enableBluetoothCallback.error("User did not enable Bluetooth");
        }
      }

      enableBluetoothCallback = null;
    }
  }

  /* @Override */
  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
    // Android 12 (API 31) and higher
    // Users MUST accept BLUETOOTH_SCAN and BLUETOOTH_CONNECT
    // Android 10 (API 29) up to Android 11 (API 30)
    // Users MUST accept ACCESS_FINE_LOCATION
    // Users may accept or reject ACCESS_BACKGROUND_LOCATION
    // Android 9 (API 28) and lower
    // Users MUST accept ACCESS_COARSE_LOCATION
    for (int i = 0; i < permissions.length; i++) {

      if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)
          && grantResults[i] == PackageManager.PERMISSION_DENIED) {
        LOG.d(TAG, "User *rejected* Fine Location Access");
        this.permissionCallback.error("Location permission not granted.");
        return;
      } else if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)
          && grantResults[i] == PackageManager.PERMISSION_DENIED) {
        LOG.d(TAG, "User *rejected* Coarse Location Access");
        this.permissionCallback.error("Location permission not granted.");
        return;
      } else if (permissions[i].equals(BLUETOOTH_SCAN) && grantResults[i] == PackageManager.PERMISSION_DENIED) {
        LOG.d(TAG, "User *rejected* Bluetooth_Scan Access");
        this.permissionCallback.error("Bluetooth scan permission not granted.");
        return;
      } else if (permissions[i].equals(BLUETOOTH_CONNECT)
          && grantResults[i] == PackageManager.PERMISSION_DENIED) {
        LOG.d(TAG, "User *rejected* Bluetooth_Connect Access");
        this.permissionCallback.error("Bluetooth Connect permission not granted.");
        return;
      }
    }

    switch (requestCode) {
      case REQUEST_BLUETOOTH_SCAN:
        LOG.d(TAG, "User granted Bluetooth Scan Access");
        findLowEnergyDevices(permissionCallback, serviceUUIDs, scanSeconds, scanSettings);
        this.permissionCallback = null;
        this.serviceUUIDs = null;
        this.scanSeconds = -1;
        this.scanSettings = null;
        break;

      case REQUEST_BLUETOOTH_CONNECT:
        LOG.d(TAG, "User granted Bluetooth Connect Access");
        connect(permissionCallback, deviceMacAddress);
        this.permissionCallback = null;
        this.deviceMacAddress = null;
        break;

      case REQUEST_BLUETOOTH_CONNECT_AUTO:
        LOG.d(TAG, "User granted Bluetooth Auto Connect Access");
        autoConnect(permissionCallback, deviceMacAddress);
        this.permissionCallback = null;
        this.deviceMacAddress = null;
        break;

      case REQUEST_GET_BONDED_DEVICES:
        LOG.d(TAG, "User granted permissions for bonded devices");
        getBondedDevices(permissionCallback);
        this.permissionCallback = null;
        break;

      case REQUEST_LIST_KNOWN_DEVICES:
        LOG.d(TAG, "User granted permissions for list known devices");
        listKnownDevices(permissionCallback);
        this.permissionCallback = null;
        break;
    }
  }

  private UUID uuidFromString(String uuid) {
    return UUIDHelper.uuidFromString(uuid);
  }

  /**
   * Reset the BLE scanning options
   */
  private void resetScanOptions() {
    this.reportDuplicates = false;
  }

  // =========================================================
  // ========================= Mesh Interface =================
  // =========================================================
  public void mesh_stopScan(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    Log.d(TAG, "mesh_stopScan");
    if (dp == null) {
      dp = new DeviceProvisioning();
      dp.initialize(cordova.getActivity().getApplication(), cordova.getActivity(), null);
      // TODO: we also have to destroy dp events we subscribed to.
    }
    dp.stop();
  }

  /**
   * s
   */
  public void mesh_provScanDevices(CordovaArgs args, CallbackContext callbackContext) {
    Log.d(TAG, "mesh_provScanDevices");
    if (!PermissionHelper.hasPermission(this, ACCESS_FINE_LOCATION)) {
      // save info so we can call this method again after permissions are granted
      permissionCallback = callbackContext;
      this.serviceUUIDs = serviceUUIDs;
      this.scanSeconds = scanSeconds;
      PermissionHelper.requestPermission(this, REQUEST_ACCESS_FINE_LOCATION, ACCESS_FINE_LOCATION);
      return;
    }
    if (dp == null) {
      dp = new DeviceProvisioning();
      dp.initialize(cordova.getActivity().getApplication(), cordova.getActivity(), callbackContext);
    }
    // dp.stop();
    dp.setCallbackContext(callbackContext);
    dp.startScan();
  }

  public void mesh_initialize(CordovaArgs args, CallbackContext callbackContext) {
    try {
      Log.d(TAG, "mesh_initialize: ");
      Boolean force = args.getBoolean(0);
      String meshName = args.getString(1);
      if (!meshSdkInitialized) {
        mGson = new GsonBuilder().setPrettyPrinting().create();
        meshSdkInitialized = true;
        meshHandler = new TelinkMeshApplication();
        meshHandler.initialize(this.cordova.getActivity().getApplicationContext());
        meshHandler.addEventListener(AutoConnectEvent.EVENT_TYPE_AUTO_CONNECT_LOGIN, this);
        meshHandler.addEventListener(MeshEvent.EVENT_TYPE_DISCONNECTED, this);
        meshHandler.addEventListener(NetworkInfoUpdateEvent.EVENT_TYPE_NETWORKD_INFO_UPDATE, this);

        // meshHandler.addEventListener(BindingEvent.EVENT_TYPE_BIND_SUCCESS, this);
        // meshHandler.addEventListener(BindingEvent.EVENT_TYPE_BIND_FAIL, this);
        // meshHandler.addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS,
        // this);
        // meshHandler.addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL,
        // this);
        // meshHandler.addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN,
        // this);

        meshHandler.addEventListener(NodeStatusChangedEvent.EVENT_TYPE_NODE_STATUS_CHANGED, this);
        meshHandler.addEventListener(GattOtaEvent.EVENT_TYPE_OTA_SUCCESS, this);
        meshHandler.addEventListener(GattOtaEvent.EVENT_TYPE_OTA_PROGRESS, this);
        meshHandler.addEventListener(GattOtaEvent.EVENT_TYPE_OTA_FAIL, this);

        meshHandler.addEventListener(ModelSubscriptionStatusMessage.class.getName(), this);
        // MeshService.getInstance().init(cordova.getContext(),
        // meshHandler.getInstance());
        // MeshConfiguration meshConfiguration =
        // meshHandler.getMeshInfo().convertToConfiguration();
        // MeshService.getInstance().setupMeshNetwork(meshConfiguration);
        //

      }
      Util.sendPluginResult(callbackContext, true);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext meshStartProvisionCallbackContext;
  NetworkingDevice pvDevice;

  public void mesh_provAddDevice(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      pvDevice = null;
      Log.d(TAG, "mesh_provAddDevice: ");
      meshStartProvisionCallbackContext = null;
      String deviceId = args.getString(0);
      if (deviceId == null) {
        callbackContext.error(Util.makeError("1", "Deviceid not preent"));
        return;
      }

      if (dp == null) {
        dp = new DeviceProvisioning();
        dp.initialize(cordova.getActivity().getApplication(), cordova.getActivity(), callbackContext);
        // TODO: we also have to destroy dp events we subscribed to.
      }
      dp.setCallbackContext(callbackContext);

      pvDevice = dp.getDevicebyUUID(deviceId);
      if (pvDevice == null) {
        callbackContext.error(Util.makeError("1", "Device with given uuid not found"));
        return;
      }
      int address = meshHandler.getMeshInfo().getProvisionIndex();
      MeshInfo meshInfo = meshHandler.getMeshInfo();
      for (NodeInfo info : meshInfo.nodes) {
        int ele = info.elementCnt;
        int nodeAddress = info.meshAddress;
        if (address >= nodeAddress && address < (nodeAddress + ele)) {
          meshHandler.getMeshInfo().increaseProvisionIndex(ele);
          address = meshHandler.getMeshInfo().getProvisionIndex();
        }
      }
      dp.stop();
      dp.startProvision(pvDevice, address);
      meshStartProvisionCallbackContext = callbackContext;
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  public void mesh_getMeshInfo(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    MeshInfo meshInfo = meshHandler.getMeshInfo();
    List<MeshNetKey> selectedNetKeys = new ArrayList<MeshNetKey>(meshInfo.meshNetKeyList);
    String meshInfoStr = MeshStorageService.getInstance().meshToJsonString(meshInfo, selectedNetKeys);
    Log.d("MESHINFOSTR", meshInfoStr);
    Util.sendPluginResult(callbackContext, meshInfoStr);
  }

  public void mesh_blinkDevice(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    // int address = Integer.parseInt(args.getString(0));
    // int appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
    // OnOffSetMessage onOffSetMessage = OnOffSetMessage.getSimple(address,
    // appKeyIndex, 1, !AppSettings.ONLINE_STATUS_ENABLE,
    // !AppSettings.ONLINE_STATUS_ENABLE ? 1 : 0);
    // MeshService.getInstance().sendMeshMessage(onOffSetMessage);

  }

  public void mesh_importMeshInfo(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      // String inMeshInfo = args.getString(0);
      // Gson mGson = new GsonBuilder().setPrettyPrinting().create();
      // MeshStorage serverMesh = mGson.fromJson(inMeshInfo, MeshStorage.class);
      // MeshInfo tmpMesh =
      // MeshInfo.createNewMesh(cordova.getActivity().getApplicationContext());
      //// MeshInfo tmpMesh = createNewMesh();
      // tmpMesh.provisionerUUID = "64bac88a-35d1-11ee-be56-0242ac120002";
      // MeshStorageService.getInstance().updateLocalMesh(serverMesh, tmpMesh);
      // if (FileSystem.writeAsObject(cordova.getActivity().getApplicationContext(),
      // MeshInfo.FILE_NAME, tmpMesh)) {
      // Util.sendPluginResult(callbackContext, true);
      // }
      // else {
      // Util.sendPluginResult(callbackContext, "failed");
      // }
      // String inMeshInfo =
      // "¨Ì\u0000\u0005sr\u0000.com.megster.cordova.ble.central.model.MeshInfov\u001Aœ‰Õ˜}ì\u0002\u0000\n"
      // +
      // "I\u0000\u000FaddressTopLimitI\u0000\u0007ivIndexI\u0000\flocalAddressI\u0000\u000EprovisionIndexI\u0000\u000EsequenceNumberL\u0000\n"
      // +
      // "appKeyListt\u0000\u0010Ljava/util/List;L\u0000\u0006groupsq\u0000~\u0000\u0001L\u0000\u000EmeshNetKeyListq\u0000~\u0000\u0001L\u0000\u0005nodesq\u0000~\u0000\u0001L\u0000\boobPairsq\u0000~\u0000\u0001L\u0000\u000FprovisionerUUIDt\u0000\u0012Ljava/lang/String;L\u0000\u0006scenesq\u0000~\u0000\u0001L\u0000\funicastRangeq\u0000~\u0000\u0001xp\u0000\u0000\b\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0004\u0001\u0000\u0000\u0004\u0004\u0000\u0000\u0006\u0000sr\u0000\u0013java.util.ArrayListxÅ“\u001Dô«aù\u0003\u0000\u0001I\u0000\u0004sizexp\u0000\u0000\u0000\u0003w\u0004\u0000\u0000\u0000\u0003sr\u00000com.megster.cordova.ble.central.model.MeshAppKeyH≠<«Ñﬂ–\u007F\u0002\u0000\u0004I\u0000\u0010boundNetKeyIndexI\u0000\u0005index[\u0000\u0003keyt\u0000\u0002[BL\u0000\u0004nameq\u0000~\u0000\u0002xp\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000ur\u0000\u0002[B¨Û\u0017¯\u0006\bT‡\u0002\u0000\u0000xp\u0000\u0000\u0000\u0010*E@=Òá\u000Fm[Õ˚\u0015≈Ô5Bt\u0000\u000FDefault
      // App
      // Keysq\u0000~\u0000\u0006\u0000\u0000\u0000\u0001\u0000\u0000\u0000\u0001uq\u0000~\u0000\t\u0000\u0000\u0000\u0010*E@=Òá\u000Fm[Õ˚\u0015≈Ô5Bt\u0000\n"
      // +
      // "Sub App Key
      // 1sq\u0000~\u0000\u0006\u0000\u0000\u0000\u0002\u0000\u0000\u0000\u0002uq\u0000~\u0000\t\u0000\u0000\u0000\u0010*E@=Òá\u000Fm[Õ˚\u0015≈Ô5Bt\u0000\n"
      // +
      // "Sub App Key
      // 2xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0002w\u0004\u0000\u0000\u0000\u0002sr\u0000/com.megster.cordova.ble.central.model.GroupInfo$€Ïo—D#\u0001\u0002\u0000\u0003I\u0000\u0007addressZ\u0000\bselectedL\u0000\u0004nameq\u0000~\u0000\u0002xp\u0000\u0000¿\u0000\u0000t\u0000\u0007Kitchensq\u0000~\u0000\u0013\u0000\u0000¿\u0001\u0000t\u0000\u0007Balconyxsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0003w\u0004\u0000\u0000\u0000\u0003sr\u00000com.megster.cordova.ble.central.model.MeshNetKeyM?Ùﬂû\n"
      // +
      // "∫“\u0002\u0000\u0003I\u0000\u0005index[\u0000\u0003keyq\u0000~\u0000\u0007L\u0000\u0004nameq\u0000~\u0000\u0002xp\u0000\u0000\u0000\u0000uq\u0000~\u0000\t\u0000\u0000\u0000\u0010˘2*£ﬁıÂg’÷K49wT`t\u0000\u000FDefault
      // Net
      // Keysq\u0000~\u0000\u0019\u0000\u0000\u0000\u0001uq\u0000~\u0000\t\u0000\u0000\u0000\u0010C—Æ∫Ì\u0002YÚ0S/Eıπˆ.t\u0000\n"
      // +
      // "Sub Net Key
      // 1sq\u0000~\u0000\u0019\u0000\u0000\u0000\u0002uq\u0000~\u0000\t\u0000\u0000\u0000\u0010˛^–\u000F(>®¥Àn\u001C¬(\u00152\n"
      // +
      // "t\u0000\n" +
      // "Sub Net Key
      // 2xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0002w\u0004\u0000\u0000\u0000\u0002sr\u0000.com.megster.cordova.ble.central.model.NodeInfoqOÓ⁄\b\u001A´€\u0002\u0000\u001AZ\u0000\fbeaconOpenedZ\u0000\u0005boundZ\u0000\u000BdefaultBindB\u0000\n"
      // +
      // "defaultTTLI\u0000\n" +
      // "elementCntZ\u0000\ffriendEnableZ\u0000\u000FgattProxyEnableI\u0000\u0003lumI\u0000\u000BmeshAddressB\u0000\u0011networkRetransmitZ\u0000\u000BrelayEnableB\u0000\u000FrelayRetransmitZ\u0000\bselectedZ\u0000\u0013subnetBridgeEnabledI\u0000\u0004tempL\u0000\u0011bridgingTableListq\u0000~\u0000\u0001L\u0000\u000FcompositionDatat\u0000,Lcom/telink/ble/mesh/entity/CompositionData;[\u0000\tdeviceKeyq\u0000~\u0000\u0007[\u0000\n"
      // +
      // "deviceUUIDq\u0000~\u0000\u0007L\u0000\n" +
      // "macAddressq\u0000~\u0000\u0002L\u0000\n" +
      // "netKeyIndexesq\u0000~\u0000\u0001L\u0000\u0010offlineCheckTaskt\u00008Lcom/megster/cordova/ble/central/model/OfflineCheckTask;L\u0000\u000BonlineStatet\u00003Lcom/megster/cordova/ble/central/model/OnlineState;L\u0000\fpublishModelt\u00004Lcom/megster/cordova/ble/central/model/PublishModel;L\u0000\n"
      // +
      // "schedulersq\u0000~\u0000\u0001L\u0000\u0007subListq\u0000~\u0000\u0001xp\u0001\u0001\u0000\n"
      // +
      // "\u0000\u0000\u0000\u0002\u0001\u0001\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0002\u0015\u0001\u0015\u0000\u0000\u0000\u0000\u0000\u0000sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsr\u0000*com.telink.ble.mesh.entity.CompositionDataÂ]Ôà÷\u0019\u0010∆\u0002\u0000\u0006I\u0000\u0003cidI\u0000\u0004crplI\u0000\bfeaturesI\u0000\u0003pidI\u0000\u0003vidL\u0000\belementsq\u0000~\u0000\u0001xp\u0000\u0000\u0002\u0011\u0000\u0000\u0000i\u0000\u0000\u0000\u0007\u0000\u0000\u0000\n"
      // +
      // "\u0000\u000063sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0002w\u0004\u0000\u0000\u0000\u0002sr\u00002com.telink.ble.mesh.entity.CompositionData$Elementˇ1≈ÙµÒ ç\u0002\u0000\u0005I\u0000\blocationI\u0000\u0006sigNumI\u0000\tvendorNumL\u0000\tsigModelsq\u0000~\u0000\u0001L\u0000\fvendorModelsq\u0000~\u0000\u0001xp\u0000\u0000\u0000\u0000\u0000\u0000\u0000\n"
      // +
      // "\u0000\u0000\u0000\u0002sq\u0000~\u0000\u0004\u0000\u0000\u0000\n" +
      // "w\u0004\u0000\u0000\u0000\n" +
      // "sr\u0000\u0011java.lang.Integer\u0012‚†§˜Åá8\u0002\u0000\u0001I\u0000\u0005valuexr\u0000\u0010java.lang.NumberÜ¨ï\u001D\u000Bî‡ã\u0002\u0000\u0000xp\u0000\u0000\u0000\u0000sq\u0000~\u00001\u0000\u0000\u0000\u0002sq\u0000~\u00001\u0000\u0000\u0000\u0003sq\u0000~\u00001\u0000\u0000\u0010\u0000sq\u0000~\u00001\u0000\u0000\u0010\u0002sq\u0000~\u00001\u0000\u0000\u0010\u0004sq\u0000~\u00001\u0000\u0000\u0010\u0006sq\u0000~\u00001\u0000\u0000\u0010\u0007sq\u0000~\u00001\u0000\u0000\u0013\u0000sq\u0000~\u00001\u0000\u0000\u0013\u0001xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0002w\u0004\u0000\u0000\u0000\u0002sq\u0000~\u00001\u0000\u0000\u0002\u0011sq\u0000~\u00001\u0000\u0001\u0002\u0011xsq\u0000~\u0000.\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0007\u0000\u0000\u0000\u0001sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0007w\u0004\u0000\u0000\u0000\u0007sq\u0000~\u00001\u0000\u0000\u0010\u0000sq\u0000~\u00001\u0000\u0000\u0010\u0002sq\u0000~\u00001\u0000\u0000\u0010\u0004sq\u0000~\u00001\u0000\u0000\u0010\u0006sq\u0000~\u00001\u0000\u0000\u0010\u0007sq\u0000~\u00001\u0000\u0000\u0013\u0000sq\u0000~\u00001\u0000\u0000\u0013\u0001xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0001w\u0004\u0000\u0000\u0000\u0001sq\u0000~\u00001\u0000\u0000\u0002\u0011xxuq\u0000~\u0000\t\u0000\u0000\u0000\u0010auQtÑ⁄ˆ+•î4ƒËÆ\u0005<uq\u0000~\u0000\t\u0000\u0000\u0000\u0010f\u0014zı≤•S;Ç§Dß\u001B@€*psq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsr\u0000Hcom.megster.cordova.ble.central.model.NodeInfo$$ExternalSyntheticLambda0\u0016\u0007◊\u0015-o\uF8FF
      // \u0002\u0000\u0001L\u0000\u0003f$0t\u00000Lcom/megster/cordova/ble/central/model/NodeInfo;xpq\u0000~\u0000)~r\u00001com.megster.cordova.ble.central.model.OnlineState\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0012\u0000\u0000xr\u0000\u000Ejava.lang.Enum\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0012\u0000\u0000xpt\u0000\u0007OFFLINEpsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsq\u0000~\u0000$\u0001\u0001\u0000\n"
      // +
      // "\u0000\u0000\u0000\u0002\u0001\u0001\u0000\u0000\u0000d\u0000\u0000\u0004\u0002\u0015\u0001\u0015\u0000\u0000\u0000\u0000\u0000\u0000sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsq\u0000~\u0000+\u0000\u0000\u0002\u0011\u0000\u0000\u0000i\u0000\u0000\u0000\u0007\u0000\u0000\u0000\n"
      // +
      // "\u0000\u000063sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0002w\u0004\u0000\u0000\u0000\u0002sq\u0000~\u0000.\u0000\u0000\u0000\u0000\u0000\u0000\u0000\n"
      // +
      // "\u0000\u0000\u0000\u0002sq\u0000~\u0000\u0004\u0000\u0000\u0000\n" +
      // "w\u0004\u0000\u0000\u0000\n" +
      // "sq\u0000~\u00001\u0000\u0000\u0000\u0000sq\u0000~\u00001\u0000\u0000\u0000\u0002sq\u0000~\u00001\u0000\u0000\u0000\u0003sq\u0000~\u00001\u0000\u0000\u0010\u0000sq\u0000~\u00001\u0000\u0000\u0010\u0002sq\u0000~\u00001\u0000\u0000\u0010\u0004sq\u0000~\u00001\u0000\u0000\u0010\u0006sq\u0000~\u00001\u0000\u0000\u0010\u0007sq\u0000~\u00001\u0000\u0000\u0013\u0000sq\u0000~\u00001\u0000\u0000\u0013\u0001xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0002w\u0004\u0000\u0000\u0000\u0002sq\u0000~\u00001\u0000\u0000\u0002\u0011sq\u0000~\u00001\u0000\u0001\u0002\u0011xsq\u0000~\u0000.\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0007\u0000\u0000\u0000\u0001sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0007w\u0004\u0000\u0000\u0000\u0007sq\u0000~\u00001\u0000\u0000\u0010\u0000sq\u0000~\u00001\u0000\u0000\u0010\u0002sq\u0000~\u00001\u0000\u0000\u0010\u0004sq\u0000~\u00001\u0000\u0000\u0010\u0006sq\u0000~\u00001\u0000\u0000\u0010\u0007sq\u0000~\u00001\u0000\u0000\u0013\u0000sq\u0000~\u00001\u0000\u0000\u0013\u0001xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0001w\u0004\u0000\u0000\u0000\u0001sq\u0000~\u00001\u0000\u0000\u0002\u0011xxuq\u0000~\u0000\t\u0000\u0000\u0000\u0010ÇåÔ%;Ø\n"
      // +
      // "ÇÚﬁ¬7Ä5*6uq\u0000~\u0000\t\u0000\u0000\u0000\u0010Œ¸=\u000F±\u0006\f>ÑÔëq•ß∑4t\u0000\u0011A4:C1:38:D0:F4:D3sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0001w\u0004\u0000\u0000\u0000\u0001q\u0000~\u0000]xsq\u0000~\u0000Nq\u0000~\u0000Wq\u0000~\u0000Spsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xxsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xt\u0000$64bac88a-35d1-11ee-be56-0242ac120002sq\u0000~\u0000\u0004\u0000\u0000\u0000\u0000w\u0004\u0000\u0000\u0000\u0000xsq\u0000~\u0000\u0004\u0000\u0000\u0000\u0001w\u0004\u0000\u0000\u0000\u0001sr\u00007com.megster.cordova.ble.central.model.json.AddressRange§\u0004(Õ™•Õ/\u0002\u0000\u0002I\u0000\u0004highI\u0000\u0003lowxp\u0000\u0000\b\u0000\u0000\u0000\u0004\u0001x";
      // if
      // (FileSystem.writeString(cordova.getActivity().getApplicationContext().getFilesDir(),
      // MeshInfo.FILE_NAME, inMeshInfo) != null) {
      // Util.sendPluginResult(callbackContext, true);
      // }
      // else {
      // Util.sendPluginResult(callbackContext, "failed");
      // }

      // Util.sendPluginResult(callbackContext, true);

      // MeshService meshService = MeshService.getInstance();
      // meshService.idle(true);
      //
      String inMeshInfo = args.getString(0);
      String meshName = args.getString(1);
      MeshInfo newMesh = MeshStorageService.getInstance().importExternal(inMeshInfo,
          MeshInfo.createNewMesh(cordova.getActivity().getApplicationContext(), meshName));
      if (newMesh == null) {
        Util.sendPluginResult(callbackContext, "mesh init failed");
        return;
      }
      // newMesh.ivIndex = 0;
      // newMesh.sequenceNumber = 1536;
      TelinkMeshApplication.getInstance().getMeshInfo().saveOrUpdate();

      // ToVerify:
      // Replace file write with saveOrUpdate, which handles db to save
      try {
        newMesh.saveOrUpdate();
      } catch (Exception e) {
        e.printStackTrace();
        // MeshLogger.e("Failed to save mesh: " + e.getMessage());
      }
      // FileSystem.writeAsObject(cordova.getActivity().getApplicationContext(),
      // MeshInfo.FILE_NAME,
      // newMesh.clone());

      Util.sendPluginResult(callbackContext, true);
      // newMesh.saveOrUpdate(cordova.getActivity().getApplicationContext());
      // MeshService.getInstance().idle(true);
      //
      // TelinkMeshApplication.getInstance().setupMesh(newMesh);
      //
      // // meshHandler.setMeshInfo(newMesh);
      // MeshService.getInstance().setupMeshNetwork(newMesh.convertToConfiguration());
      // MeshService.getInstance().checkBluetoothState();
      // Util.sendPluginResult(callbackContext, true);
      // MeshService.getInstance().resetExtendBearerMode(SharedPreferenceHelper.getExtendBearerMode(cordova.getContext()));
      // TelinkMeshApplication.getInstance().resetNodeState();
      // TelinkMeshApplication.getInstance().autoConnect();

    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext meshBindDeviceCallbackContext;

  public void mesh_bindDevice(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      int meshAddress = args.getInt(0);
      meshBindDeviceCallbackContext = null;
      targetDevice = meshHandler.getMeshInfo().getDeviceByMeshAddress(meshAddress);
      BindingDevice bindingDevice = new BindingDevice(targetDevice.meshAddress, targetDevice.deviceUUID,
          meshHandler.getMeshInfo().getDefaultAppKeyIndex());
      MeshService.getInstance().startBinding(new BindingParameters(bindingDevice));
      meshBindDeviceCallbackContext = callbackContext;
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  boolean kickDirect;
  NodeInfo targetDevice;
  CallbackContext nodeKickCallback;

  public void mesh_kickOutDevice(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      nodeKickCallback = null;
      int meshAddress = args.getInt(0);
      targetDevice = meshHandler.getMeshInfo().getDeviceByMeshAddress(meshAddress);
      Handler handler = new Handler();
      // send reset message
      boolean cmdSent = MeshService.getInstance().sendMeshMessage(new NodeResetMessage(targetDevice.meshAddress));
      kickDirect = meshAddress == (MeshService.getInstance().getDirectConnectedNodeAddress());
      nodeKickCallback = callbackContext;
      if (!cmdSent || !kickDirect) {
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            handler.removeCallbacksAndMessages(null);
            onKickOutFinish();
            // finish();
          }
        }, 3 * 1000);
      }
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext sendOnOffcallback = null;

  public void mesh_sendOnOffCommand(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      int meshAddress = args.getInt(0);
      int appKeyIndex = args.getInt(1);
      int OnOff = args.getInt(2);
      appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
      OnOffSetMessage offSetMessage = OnOffSetMessage.getSimple(meshAddress, appKeyIndex,
          OnOff == 1 ? OnOffSetMessage.ON : OnOffSetMessage.OFF, true, 0);
      if (!MeshService.getInstance().isProxyLogin()) {
        Util.sendPluginResult(callbackContext, "{\"proxy\": 0}");
        return;
      }
      if (MeshService.getInstance().sendMeshMessage(offSetMessage)) {
        Util.sendPluginResult(callbackContext, "{\"success\": 1}");
      } else {
        mHandler.postDelayed(new Runnable() {
          @Override
          public void run() {
            if (MeshService.getInstance().sendMeshMessage(offSetMessage)) {
              Util.sendPluginResult(callbackContext, "{\"success\": 1}");
            } else {
              Util.sendPluginResult(callbackContext, "{\"success\": 0}");
            }
          }
        }, 1 * 1000);
      }
      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          OnOffGetMessage onOffGetMessage = new OnOffGetMessage(meshAddress, 0);
          MeshService.getInstance().sendMeshMessage(onOffGetMessage);
        }
      }, 1 * 1000);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext sendLightnesscallback = null;

  public void mesh_sendLightnessCommand(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      sendLightnesscallback = callbackContext;
      int meshAddress = args.getInt(0);
      int appKeyIndex = args.getInt(1);
      int lightness = args.getInt(2);
      lightness = (int) (lightness * (65535.0 / 255.0));
      appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
      if (!MeshService.getInstance().isProxyLogin()) {
        Util.sendPluginResult(callbackContext, "{\"proxy\": 0}");
        return;
      }
      LightnessSetMessage lightnessSetMessage = LightnessSetMessage.getSimple(meshAddress, appKeyIndex, lightness,
          true, 0);

      if (MeshService.getInstance().sendMeshMessage(lightnessSetMessage)) {
        Util.sendPluginResult(callbackContext, "{\"success\": 1}");
      } else {
        Util.sendPluginResult(callbackContext, "{\"success\": 0}");
      }
      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          LevelGetMessage levelGetMessage = new LevelGetMessage(meshAddress, 0);
          MeshService.getInstance().sendMeshMessage(levelGetMessage);
        }
      }, 1 * 1000);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext sendCTLcallback = null;

  public void mesh_sendCTLCommand(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      sendCTLcallback = callbackContext;
      int meshAddress = args.getInt(0);
      int appKeyIndex = args.getInt(1);
      int ctl = args.getInt(2);
      appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
      CtlTemperatureSetMessage ctlTemperatureSetMessage = CtlTemperatureSetMessage.getSimple(meshAddress,
          appKeyIndex, ctl, 0, true, 0);
      // OnOffSetMessage offSetMessage = OnOffSetMessage.getSimple(meshAddress,
      // appKeyIndex, OnOff == 1 ? OnOffSetMessage.ON : OnOffSetMessage.OFF, true, 0);
      // offSetMessage.setComplete(true);
      MeshService.getInstance().sendMeshMessage(ctlTemperatureSetMessage);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext addDeviceToGroupCallback;
  List<Integer> addDeviceToGroupMeta;

  public void mesh_addDeviceToGroup(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      int meshAddress = args.getInt(0);
      int opGroupAdr = args.getInt(1);
      int addOrRemove = args.getInt(2);
      int modelIndex = args.getInt(3);
      int targetElementAddress = args.getInt(4);

      addDeviceToGroupCallback = null;
      MeshSigModel[] models = MeshSigModel.getDefaultSubList();
      NodeInfo deviceInfo = meshHandler.getMeshInfo().getDeviceByMeshAddress(meshAddress);

      if (deviceInfo == null) {
        callbackContext.error("device not found");
        return;
      }

      if (deviceInfo != null && deviceInfo.compositionData != null
          && deviceInfo.compositionData.pid == AppSettings.PID_REMOTE) {
        // if direct connected device is remote-control, disconnect
        MeshService.getInstance().idle(true);
      }

      // final int eleAdr = deviceInfo.getTargetEleAdr(models[modelIndex].modelId);
      MeshMessage groupingMessage = ModelSubscriptionSetMessage.getSimple(meshAddress, addOrRemove,
          meshAddress + targetElementAddress,
          opGroupAdr, models[modelIndex].modelId, true);

      if (!MeshService.getInstance().sendMeshMessage(groupingMessage)) {
        Util.sendPluginResult(callbackContext, "failed");
      }
      addDeviceToGroupCallback = callbackContext;
      if (addDeviceToGroupMeta != null) {
        addDeviceToGroupMeta.clear();
      } else {
        addDeviceToGroupMeta = new ArrayList<Integer>();
      }
      addDeviceToGroupMeta.add(1); // means active
      addDeviceToGroupMeta.add(meshAddress);
      addDeviceToGroupMeta.add(opGroupAdr);
      addDeviceToGroupMeta.add(addOrRemove);
      addDeviceToGroupMeta.add(modelIndex);
      // Util.sendPluginResult(callbackContext, true);

    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  public void mesh_groupControl(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      int position = args.getInt(0);
      int onOff = args.getInt(1);

      boolean ack;
      int rspMax;
      List<GroupInfo> mGroups = meshHandler.getMeshInfo().groups;
      MeshInfo meshInfo = meshHandler.getMeshInfo();
      if (AppSettings.ONLINE_STATUS_ENABLE) {
        ack = false;
        rspMax = 0;
      } else {
        ack = true;
        rspMax = meshInfo.getOnlineCountInGroup(mGroups.get(position).address);
      }
      OnOffSetMessage message = OnOffSetMessage.getSimple(mGroups.get(position).address,
          meshInfo.getDefaultAppKeyIndex(),
          (byte) onOff,
          ack,
          rspMax);
      MeshService.getInstance().sendMeshMessage(message);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  public void mesh_autoConnect(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      TelinkMeshApplication.getInstance().autoConnect();
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext deviceotacallback = null;
  CallbackContext deviceotaProgress = null;

  public void mesh_deviceOTA(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      deviceotacallback = callbackContext;
      // deviceotaProgress = callbackContext;
      String fileName = args.getString(0);
      int meshAddress = args.getInt(1);
      NodeInfo mNodeInfo = meshHandler.getMeshInfo().getDeviceByMeshAddress(meshAddress);
      File mCurrentDir = Environment.getExternalStorageDirectory();
      String mFilePath = mCurrentDir.getAbsolutePath();
      ActivityCompat.requestPermissions(cordova.getActivity(),
          new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
      InputStream stream = new FileInputStream(fileName);
      int length = stream.available();
      mFirmware = new byte[length];
      stream.read(mFirmware);
      stream.close();

      byte[] pid = new byte[2];
      byte[] vid = new byte[2];
      System.arraycopy(mFirmware, 2, pid, 0, 2);
      this.binPid = MeshUtils.bytes2Integer(pid, ByteOrder.LITTLE_ENDIAN);

      System.arraycopy(mFirmware, 4, vid, 0, 2);
      ConnectionFilter connectionFilter = new ConnectionFilter(ConnectionFilter.TYPE_MESH_ADDRESS,
          mNodeInfo.meshAddress);
      GattOtaParameters parameters = new GattOtaParameters(connectionFilter, mFirmware);
      MeshService.getInstance().startGattOta(parameters);
    } catch (Exception e) {
      e.printStackTrace();
      mFirmware = null;
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  // private void readFirmware(String fileName) {
  // try {
  // InputStream stream = new FileInputStream(fileName);
  // int length = stream.available();
  // mFirmware = new byte[length];
  // stream.read(mFirmware);
  // stream.close();
  //
  // byte[] pid = new byte[2];
  // byte[] vid = new byte[2];
  // System.arraycopy(mFirmware, 2, pid, 0, 2);
  // this.binPid = MeshUtils.bytes2Integer(pid, ByteOrder.LITTLE_ENDIAN);
  //
  // System.arraycopy(mFirmware, 4, vid, 0, 2);
  //
  //// String pidInfo = com.telink.ble.mesh.util.Arrays.bytesToHexString(pid,
  // ":");
  //// String vidInfo = Arrays.bytesToHexString(vid, ":");
  //// String firmVersion = " pid-" + pidInfo + " vid-" + vidInfo;
  // } catch (IOException e) {
  // e.printStackTrace();
  // mFirmware = null;
  // }
  // }

  CallbackContext onoffstatuscallback = null;

  public void mesh_onoffstatus(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      AppSettings.ONLINE_STATUS_ENABLE = MeshService.getInstance().getOnlineStatus();
      if (!AppSettings.ONLINE_STATUS_ENABLE) {
        MeshService.getInstance().getOnlineStatus();
      }
      int rspMax = meshHandler.getMeshInfo().getOnlineCountInAll();
      MeshInfo meshInfo = meshHandler.getMeshInfo();
      int appKeyIndex = meshInfo.getDefaultAppKeyIndex();
      OnOffGetMessage message = OnOffGetMessage.getSimple(0xFFFF, appKeyIndex, rspMax);
      LevelGetMessage message2 = new LevelGetMessage(0xFFFF, appKeyIndex);
      MeshService.getInstance().sendMeshMessage(message2);
      if (!MeshService.getInstance().sendMeshMessage(message)) {
        Util.sendPluginResult(callbackContext, (String) null);
        return;
      }
      // String json = new Gson().toJson(meshInfo.nodes);
      String json = new Gson().toJson(TelinkMeshApplication.getInstance().getAllMeshNodeStatusObjects());
      Util.sendPluginResult(callbackContext, json);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  CallbackContext meshEventCallback = null;

  public void mesh_subscribeToMeshEvents(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      meshEventCallback = callbackContext;
      TelinkMeshApplication.getInstance().setMeshEventCallback(meshEventCallback);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  public void mesh_unsubscribeFromMeshEvents(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      meshEventCallback = null;
      TelinkMeshApplication.getInstance().setMeshEventCallback(null);
      Util.sendPluginResult(callbackContext, "success");
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  public void mesh_downloadMeshBlobFromServer(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      String url = args.getString(0);
      String authToken = args.getString(1);

      // DownloadManager manager = (DownloadManager)
      // cordova.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
      // Uri uri = Uri.parse(url);
      // DownloadManager.Request request = new DownloadManager.Request(uri);
      // request.addRequestHeader("Authorization", token);
      //// request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
      // request.setDestinationInExternalFilesDir(cordova.getActivity().getApplicationContext(),
      // cordova.getActivity().getApplicationContext().getFilesDir().getAbsolutePath(),
      // FILE_NAME);
      // long reference = manager.enqueue(request);
      // downloadFile(url, authToken,
      // cordova.getActivity().getApplicationContext().getFilesDir().getAbsolutePath()
      // + "/" + FILE_NAME,
      // callbackContext);
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  private void downloadFile(String fileURL, String authToken, String destinationPath,
      CallbackContext callbackContext) {
    cordova.getThreadPool().execute(() -> {
      try {
        URL url = new URL(fileURL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", authToken);
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
          callbackContext.error("Server returned HTTP " + connection.getResponseCode()
              + " " + connection.getResponseMessage());
          return;
        }

        InputStream input = new BufferedInputStream(connection.getInputStream());
        FileOutputStream output = new FileOutputStream(destinationPath);

        byte[] data = new byte[1024];
        int count;
        while ((count = input.read(data)) != -1) {
          output.write(data, 0, count);
        }

        output.flush();
        output.close();
        input.close();

        callbackContext.success("{\"success\": 1}");

      } catch (Exception e) {
        callbackContext.error("Error downloading file: " + e.getMessage());
      }
    });
  }

  public void mesh_uploadMeshBlobToServer(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      String uri = args.getString(0);
      String token = args.getString(1);
      // if (uploadFile(cordova.getActivity().getFilesDir().getAbsolutePath() + "/" +
      // FILE_NAME, uri,
      // token) != 200) {
      // Util.sendPluginResult(callbackContext, "{\"success\": 0}");
      // }
      Util.sendPluginResult(callbackContext, "{\"success\": 1}");
    } catch (Exception e) {
      Util.sendPluginResult(callbackContext, e.getMessage());
    }
  }

  public int uploadFile(String sourceFileUri, String upLoadServerUri, String token) {

    String fileName = sourceFileUri;
    int serverResponseCode = 0;

    HttpURLConnection conn = null;
    DataOutputStream dos = null;
    String lineEnd = "\r\n";
    String twoHyphens = "--";
    String boundary = "*****";
    int bytesRead, bytesAvailable, bufferSize;
    byte[] buffer;
    int maxBufferSize = 2 * 1024 * 1024;
    File sourceFile = new File(sourceFileUri);

    if (!sourceFile.isFile()) {
      return 0;
    } else {
      try {

        // open a URL connection to the Servlet
        FileInputStream fileInputStream = new FileInputStream(sourceFile);
        URL url = new URL(upLoadServerUri);

        // Open a HTTP connection to the URL
        conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true); // Allow Inputs
        conn.setDoOutput(true); // Allow Outputs
        conn.setUseCaches(false); // Don't use a Cached Copy
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("ENCTYPE", "multipart/form-data");
        conn.setRequestProperty("Authorization", token);
        conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        conn.setRequestProperty("file", fileName);

        dos = new DataOutputStream(conn.getOutputStream());

        dos.writeBytes(twoHyphens + boundary + lineEnd);
        dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + fileName + "\"" + lineEnd);

        dos.writeBytes(lineEnd);

        // create a buffer of maximum size
        bytesAvailable = fileInputStream.available();

        bufferSize = Math.min(bytesAvailable, maxBufferSize);
        buffer = new byte[bufferSize];

        // read file and write it into form...
        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

        while (bytesRead > 0) {

          dos.write(buffer, 0, bufferSize);
          bytesAvailable = fileInputStream.available();
          bufferSize = Math.min(bytesAvailable, maxBufferSize);
          bytesRead = fileInputStream.read(buffer, 0, bufferSize);

        }

        // send multipart form data necesssary after file data...
        dos.writeBytes(lineEnd);
        dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

        // Responses from the server (code and message)
        serverResponseCode = conn.getResponseCode();
        String serverResponseMessage = conn.getResponseMessage();

        Log.i(TAG, "HTTP Response is : "
            + serverResponseMessage + ": " + serverResponseCode);

        if (serverResponseCode == 200) {

        }

        // close the streams //
        fileInputStream.close();
        dos.flush();
        dos.close();

      } catch (MalformedURLException ex) {

        ex.printStackTrace();

        Log.e(TAG, "error: " + ex.getMessage(), ex);
      } catch (Exception e) {

        e.printStackTrace();

        Log.e(TAG, "Exception : " + e.getMessage(), e);
      }
      return serverResponseCode;

    } // End else block
  }

  CallbackContext networkInfoCallback;

  public void mesh_registerNetworkInfoCallback(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      networkInfoCallback = callbackContext;
    } catch (Exception e) {
      callbackContext.error(e.getMessage());
    }
  }

  public void mesh_updateIvIndexAndSeqNumber(CordovaArgs args, CallbackContext callbackContext) throws Exception {
    try {
      int newIvIndex = args.getInt(0);
      int newSeqNumber = args.getInt(1);
      if (TelinkMeshApplication.getInstance() != null) {
        MeshInfo meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
        boolean changeDetected = false;
        if (meshInfo.ivIndex != newIvIndex && newIvIndex != -1) {
          meshInfo.ivIndex = newIvIndex;
          changeDetected = true;
        }
        if (meshInfo.sequenceNumber != newSeqNumber && newSeqNumber != -1) {
          meshInfo.sequenceNumber = newSeqNumber;
          changeDetected = true;
        }
        if (changeDetected) {
          meshInfo.saveOrUpdate();
           MeshService.getInstance().setSequenceNumber(meshInfo.sequenceNumber, false);
          // MeshService.getInstance().idle(true);
          // TelinkMeshApplication.getInstance().setupMesh(meshInfo);
          // meshHandler.setMeshInfo(meshInfo);
          // MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
          if (MeshService.getInstance().getCurrentMode() == MeshController.Mode.IDLE) {
            MeshService.getInstance().autoConnect(new AutoConnectParameters());
          }
        }
      }
      callbackContext.success();

    } catch (Exception e) {
      callbackContext.error(e.getMessage());
    }
  }

  private void onKickOutFinish() {
    if (targetDevice != null) {
      MeshService.getInstance().removeDevice(targetDevice.meshAddress);
      meshHandler.getMeshInfo().removeDeviceByMeshAddress(targetDevice.meshAddress);
      meshHandler.getMeshInfo().saveOrUpdate();
      targetDevice = null;
    }
    if (nodeKickCallback != null) {
      nodeKickCallback.success();
      nodeKickCallback = null;
    }
  }

  @Override
  public void performed(Event<String> event) {
    if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_SUCCESS)) {
      onBindSuccess((BindingEvent) event);
    } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_FAIL)) {
      // onBindFail((BindingEvent) event);
    } else if (event.getType().equals(MeshEvent.EVENT_TYPE_DISCONNECTED)) {
      if (kickDirect) {
        onKickOutFinish(); // TODO: Check is it something we did ? in their code they are only remoing
        // callbackandmessages on mhandler.
        // finish();
      }
      mHandler.removeCallbacksAndMessages(null);
      LOG.d(TAG, "BLECENTRALPLUGIN:performed:DISCONNECTED");
    } else if (event.getType().equals(NodeResetStatusMessage.class.getName())) {
      if (!kickDirect) {
        onKickOutFinish();
      }
    } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS)) {
      // onProvisionSuccess((ProvisioningEvent) event);
    } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL)) {
      // onProvisionFail((ProvisioningEvent) event);
    } else if (event.getType().equals(ModelPublicationStatusMessage.class.getName())) {
      MeshLogger.d("pub setting status: " + isPubSetting);
      if (!isPubSetting) {
        return;
      }
      mHandler.removeCallbacks(timePubSetTimeoutTask);
      final ModelPublicationStatusMessage statusMessage = (ModelPublicationStatusMessage) ((StatusNotificationEvent) event)
          .getNotificationMessage().getStatusMessage();

      if (statusMessage.getStatus() == ConfigStatus.SUCCESS.code) {
        onTimePublishComplete(true, "time pub set success");
      } else {
        onTimePublishComplete(false, "time pub set status err: " + statusMessage.getStatus());
        MeshLogger.log("publication err: " + statusMessage.getStatus());
      }
    } else if (event.getType().equals(ModelSubscriptionStatusMessage.class.getName())) {
      NotificationMessage notificationMessage = ((StatusNotificationEvent) event).getNotificationMessage();
      ModelSubscriptionStatusMessage statusMessage = (ModelSubscriptionStatusMessage) notificationMessage
          .getStatusMessage();
      if (statusMessage.getStatus() == ConfigStatus.SUCCESS.code) {
        if (addDeviceToGroupCallback != null) {
          if (addDeviceToGroupMeta != null && !addDeviceToGroupMeta.isEmpty()
              && addDeviceToGroupMeta.get(0) == 1) {
            MeshInfo meshInfo = meshHandler.getMeshInfo();
            NodeInfo deviceInfo = meshInfo.getDeviceByMeshAddress(addDeviceToGroupMeta.get(1));
            int groupAddress = addDeviceToGroupMeta.get(2);
            if (addDeviceToGroupMeta.get(3) == 0) {
              // deviceInfo.subList.add(Integer.valueOf(String
              // .valueOf(Integer.valueOf(String.format("%04X",
              // addDeviceToGroupMeta.get(2))))));
              deviceInfo.subList.add(String.valueOf(groupAddress));
              boolean groupAdded = false;
              for (int i = 0; i < meshInfo.groups.size(); i++) {
                if (meshInfo.groups.get(i).address == groupAddress) {
                  groupAdded = true;
                  break;
                }
              }
              if (!groupAdded) {
                GroupInfo newGroupInfo = new GroupInfo();
                newGroupInfo.address = groupAddress;
                newGroupInfo.name = "appnewgrp";
                meshInfo.groups.add(newGroupInfo);
              }
            } else {
              // deviceInfo.subList.remove(String.format("%04X",
              // addDeviceToGroupMeta.get(2)));
              int ind = deviceInfo.subList.indexOf(groupAddress);
              if (ind != -1) {
                deviceInfo.subList.remove(ind);
              }
            }
            meshInfo.saveOrUpdate();
          }
          addDeviceToGroupCallback.success("successgroup");
        }

      } else if (addDeviceToGroupCallback != null) {
        Util.sendPluginResult(addDeviceToGroupCallback, "failed");
      }
      addDeviceToGroupCallback = null;
      addDeviceToGroupMeta.clear();

    } else if (event.getType().equals(AutoConnectEvent.EVENT_TYPE_AUTO_CONNECT_LOGIN)) {
      LOG.d(TAG, "BLECENTRALPLUGIN:performed:AUTO_CONNECT_LOGIN");
      // get all device on off status when auto connect success
      AppSettings.ONLINE_STATUS_ENABLE = MeshService.getInstance().getOnlineStatus();
      if (!AppSettings.ONLINE_STATUS_ENABLE) {
        MeshService.getInstance().getOnlineStatus();
        int rspMax = meshHandler.getMeshInfo().getOnlineCountInAll();
        int appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
        OnOffGetMessage message = OnOffGetMessage.getSimple(0xFFFF, appKeyIndex, rspMax);
        LevelGetMessage message2 = new LevelGetMessage(0xFFFF, appKeyIndex);

        MeshService.getInstance().sendMeshMessage(message);
        MeshService.getInstance().sendMeshMessage(message2);
        LOG.d(TAG, "BLECENTRALPLUGIN:performed:AUTO_CONNECT_LOGIN:END");
      } else {
        MeshLogger.log("online status enabled");
      }
      sendTimeStatus();

      // mHandler.postDelayed(new Runnable() {
      // @Override
      // public void run() {
      // checkMeshOtaState();
      // }
      // }, 3 * 1000);
    } else if (event.getType().equals(NodeStatusChangedEvent.EVENT_TYPE_NODE_STATUS_CHANGED)) {
      ArrayList<MeshNodeStatus> meshNodeStatuses = (ArrayList<MeshNodeStatus>) (TelinkMeshApplication.getInstance()
          .getAllMeshNodeStatusObjects()).clone();
      String json = new Gson().toJson(meshNodeStatuses);
      if (sendOnOffcallback != null) {
        sendOnOffcallback.success(json);
        sendOnOffcallback = null;
      }
      if (sendLightnesscallback != null) {
        sendLightnesscallback.success(json);
        sendLightnesscallback = null;
      }
      if (sendCTLcallback != null) {
        sendCTLcallback.success(json);
        sendCTLcallback = null;
      }
      if (onoffstatuscallback != null) {
        onoffstatuscallback.success(json);
        onoffstatuscallback = null;
      }
      if (meshEventCallback != null) {
        // PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
        // pluginResult.setKeepCallback(true);
        // meshEventCallback.sendPluginResult(pluginResult);
      }
    } else if (event.getType().equals(GattOtaEvent.EVENT_TYPE_OTA_SUCCESS)) {
      deviceotacallback.success("ota_success");
      deviceotacallback = null;
    } else if (event.getType().equals(GattOtaEvent.EVENT_TYPE_OTA_FAIL)) {
      deviceotacallback.success("ota_fail");
      deviceotacallback = null;
    } else if (event.getType().equals(GattOtaEvent.EVENT_TYPE_OTA_PROGRESS)) {
      int progress = ((GattOtaEvent) event).getProgress();
      // PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
      // String.valueOf(progress));
      // pluginResult.setKeepCallback(true);
      // deviceotacallback.sendPluginResult(pluginResult);
      // Util.sendPluginResult(deviceotaProgress, String.valueOf(progress));
    } else if (event.getType().equals(NetworkInfoUpdateEvent.EVENT_TYPE_NETWORKD_INFO_UPDATE)) {
      if (networkInfoCallback != null) {
        NetworkInfoUpdateEvent networkInfoUpdateEvent = (NetworkInfoUpdateEvent) event;
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
            "{\"ivIndex\": " + networkInfoUpdateEvent.getIvIndex() + ", \"sequenceNumber\":"
                + networkInfoUpdateEvent.getSequenceNumber() + "}");
        pluginResult.setKeepCallback(true);
        networkInfoCallback.sendPluginResult(pluginResult);
      }
    }
  }

  public void sendTimeStatus() {
    mHandler.postDelayed(() -> {
      long time = MeshUtils.getTaiTime();
      int offset = UnitConvert.getZoneOffset();
      final int address = 0xFFFF;
      MeshInfo meshInfo = meshHandler.getInstance().getMeshInfo();
      TimeSetMessage timeSetMessage = TimeSetMessage.getSimple(address, meshInfo.getDefaultAppKeyIndex(), time,
          offset, 1);
      timeSetMessage.setAck(false);
      MeshService.getInstance().sendMeshMessage(timeSetMessage);
    }, 1500);
  }

  private void onBindSuccess(BindingEvent event) {
    BindingDevice remote = event.getBindingDevice();
    MeshInfo mesh = meshHandler.getMeshInfo();
    try {
      if (pvDevice != null) {
        pvDevice.addLog(NetworkingDevice.TAG_BIND, "success");
        pvDevice.nodeInfo.bound = true;
        // if is default bound, composition data has been valued ahead of binding action
        if (!remote.isDefaultBound()) {
          pvDevice.nodeInfo.compositionData = remote.getCompositionData();
        }

        if (setTimePublish(pvDevice)) {
          pvDevice.state = NetworkingState.TIME_PUB_SETTING;
          pvDevice.addLog(NetworkingDevice.TAG_PUB_SET, "action start");
          isPubSetting = true;
          MeshLogger.d("waiting for time publication status");
        } else {
          // no need to set time publish
          pvDevice.state = NetworkingState.BIND_SUCCESS;
          // mesh.updateNodeByUUID(pvDevice.nodeInfo.deviceUUID, pvDevice.nodeInfo);
          mesh.saveOrUpdate();
          provisionComplete();
        }
        mesh.saveOrUpdate();
      } else {
        NodeInfo local = mesh.getDeviceByUUID(remote.getDeviceUUID());
        if (local == null)
          return;

        local.bound = true;
        // local. = remote.boundModels;
        local.compositionData = remote.getCompositionData();
        mesh.saveOrUpdate();
        if (meshBindDeviceCallbackContext != null) {
          meshBindDeviceCallbackContext.success();
          meshBindDeviceCallbackContext = null;
        }
      }
    } catch (Exception e) {
      Log.e("edede", e.toString());
    }
  }

  private void onBindFail(BindingEvent event) {
    if (meshBindDeviceCallbackContext != null) {
      meshBindDeviceCallbackContext.error(event.toString());
      meshBindDeviceCallbackContext = null;
    }
    if (pvDevice != null) {
      pvDevice.state = NetworkingState.BIND_FAIL;
      pvDevice.addLog(NetworkingDevice.TAG_BIND, "failed - " + event.getDesc());
      meshHandler.getMeshInfo().saveOrUpdate();
      provisionComplete();
    }
  }

  private boolean setTimePublish(NetworkingDevice networkingDevice) {
    int modelId = MeshSigModel.SIG_MD_TIME_S.modelId;
    int pubEleAdr = networkingDevice.nodeInfo.getTargetEleAdr(modelId);
    if (pubEleAdr != -1) {
      final int period = 30 * 1000;
      final int pubAdr = 0xFFFF;
      int appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
      ModelPublication modelPublication = ModelPublication.createDefault(pubEleAdr, pubAdr, appKeyIndex, period,
          modelId, true);

      ModelPublicationSetMessage publicationSetMessage = new ModelPublicationSetMessage(
          networkingDevice.nodeInfo.meshAddress, modelPublication);
      boolean result = MeshService.getInstance().sendMeshMessage(publicationSetMessage);
      if (result) {
        mHandler.removeCallbacks(timePubSetTimeoutTask);
        mHandler.postDelayed(timePubSetTimeoutTask, 5 * 1000);
      }
      return result;
    } else {
      return false;
    }
  }

  private Runnable timePubSetTimeoutTask = new Runnable() {
    @Override
    public void run() {
      onTimePublishComplete(false, "time pub set timeout");
    }
  };

  private void onTimePublishComplete(boolean success, String desc) {
    if (!isPubSetting)
      return;
    MeshLogger.d("pub set complete: " + success + " -- " + desc);
    isPubSetting = false;

    if (pvDevice == null) {
      MeshLogger.d("pv device not found pub set success");
      return;
    }
    pvDevice.addLog(NetworkingDevice.TAG_PUB_SET, success ? "success" : ("failed : " + desc));
    pvDevice.state = success ? NetworkingState.TIME_PUB_SET_SUCCESS : NetworkingState.TIME_PUB_SET_FAIL;
    pvDevice.addLog(NetworkingDevice.TAG_PUB_SET, desc);
    meshHandler.getMeshInfo().saveOrUpdate();
    provisionComplete();
  }

  private void provisionComplete() {
    if (meshStartProvisionCallbackContext != null) {

      MeshInfo meshInfo = meshHandler.getMeshInfo();
      List<MeshNetKey> selectedNetKeys = new ArrayList<MeshNetKey>(meshInfo.meshNetKeyList);
      String meshInfoStr = MeshStorageService.getInstance().meshToJsonString(meshInfo, selectedNetKeys);

      meshStartProvisionCallbackContext.success(meshInfoStr);
      meshStartProvisionCallbackContext = null;
    }
  }

  private void onProvisionSuccess(ProvisioningEvent event) {
    if (pvDevice != null) {
      ProvisioningDevice remote = event.getProvisioningDevice();
      pvDevice.state = NetworkingState.BINDING;
      pvDevice.addLog(NetworkingDevice.TAG_PROVISION, "success");
      NodeInfo nodeInfo = pvDevice.nodeInfo;
      int elementCnt = remote.getDeviceCapability().eleNum;
      nodeInfo.elementCnt = elementCnt;
      nodeInfo.deviceKey = remote.getDeviceKey();
      nodeInfo.netKeyIndexes.add(String.valueOf(meshHandler.getMeshInfo().getDefaultNetKey().index));

      // remove the device if it already existing in the mesh with same UUID - safety
      meshHandler.getMeshInfo().removeDeviceByUUID(nodeInfo.deviceUUID);
      meshHandler.getMeshInfo().removeDeviceByMeshAddress(nodeInfo.meshAddress);

      meshHandler.getMeshInfo().insertDevice(nodeInfo, true);
      meshHandler.getMeshInfo().increaseProvisionIndex(elementCnt);
      meshHandler.getMeshInfo().saveOrUpdate();

      // check if private mode opened
      final boolean privateMode = SharedPreferenceHelper.isPrivateMode(cordova.getActivity().getApplicationContext());

      // check if device support fast bind
      boolean defaultBound = false;
      if (privateMode && remote.getDeviceUUID() != null) {
        PrivateDevice device = PrivateDevice.filter(remote.getDeviceUUID());
        if (device != null) {
          MeshLogger.d("private device");
          final byte[] cpsData = device.getCpsData();
          nodeInfo.compositionData = CompositionData.from(cpsData);
          defaultBound = true;
        } else {
          MeshLogger.d("private device null");
        }
      }

      nodeInfo.setDefaultBind(defaultBound);
      pvDevice.addLog(NetworkingDevice.TAG_BIND, "action start");
      int appKeyIndex = meshHandler.getMeshInfo().getDefaultAppKeyIndex();
      BindingDevice bindingDevice = new BindingDevice(nodeInfo.meshAddress, nodeInfo.deviceUUID, appKeyIndex);
      bindingDevice.setDefaultBound(defaultBound);
      bindingDevice.setBearer(BindingBearer.GattOnly);
      // bindingDevice.setDefaultBound(false);
      MeshService.getInstance().startBinding(new BindingParameters(bindingDevice));
    }
  }

  private void onProvisionFail(ProvisioningEvent event) {
    if (meshStartProvisionCallbackContext != null) {
      meshStartProvisionCallbackContext.error(event.toString());
      meshStartProvisionCallbackContext = null;
    }
  }
}
