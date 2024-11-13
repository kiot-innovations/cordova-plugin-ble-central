package com.megster.cordova.ble.central;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.telecom.Call;
import android.util.Log;

import com.google.gson.Gson;
import com.megster.cordova.ble.central.model.AppSettings;
import com.megster.cordova.ble.central.model.MeshInfo;
import com.megster.cordova.ble.central.model.NodeInfo;
import com.megster.cordova.ble.central.model.NodeStatusChangedEvent;
import com.megster.cordova.ble.central.model.OnlineState;
import com.megster.cordova.ble.central.model.UnitConvert;
import com.megster.cordova.ble.central.model.db.MeshInfoService;
import com.megster.cordova.ble.central.model.db.ObjectBox;
import com.megster.cordova.ble.central.model.MeshNodeStatus;
import com.telink.ble.mesh.core.message.MeshSigModel;
import com.telink.ble.mesh.core.message.NotificationMessage;
import com.telink.ble.mesh.core.message.StatusMessage;
import com.telink.ble.mesh.core.message.generic.LevelStatusMessage;
import com.telink.ble.mesh.core.message.generic.OnOffStatusMessage;
import com.telink.ble.mesh.core.message.lighting.CtlStatusMessage;
import com.telink.ble.mesh.core.message.lighting.CtlTemperatureStatusMessage;
import com.telink.ble.mesh.core.message.lighting.LightnessStatusMessage;
import com.telink.ble.mesh.entity.CompositionData;
import com.telink.ble.mesh.entity.OnlineStatusInfo;
import com.telink.ble.mesh.foundation.Event;
import com.telink.ble.mesh.foundation.EventBus;
import com.telink.ble.mesh.foundation.EventHandler;
import com.telink.ble.mesh.foundation.EventListener;
import com.telink.ble.mesh.foundation.MeshApplication;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.foundation.MeshController;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.event.MeshEvent;
import com.telink.ble.mesh.foundation.event.NetworkInfoUpdateEvent;
import com.telink.ble.mesh.foundation.event.OnlineStatusEvent;
import com.telink.ble.mesh.foundation.event.StatusNotificationEvent;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.util.FileSystem;
import com.telink.ble.mesh.util.MeshLogger;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TelinkBleMeshHandler extends MeshApplication implements EventHandler {
  private String TAG = "TelinkBleMeshHandler";
  private static TelinkBleMeshHandler mThis;
  private Handler mOfflineCheckHandler;
  private MeshInfo meshInfo;
  private EventBus<String> mEventBus;
  private Context mCtx;

  // Very Important to call this before anything.
  // Because we are setting mThis to this.
  public void initialize(Context ctx, String meshName) {
    // From TelinkMeshApplication.onCreate
    mThis = this;
    mEventBus = new EventBus<>();
    HandlerThread offlineCheckThread = new HandlerThread("offline check thread");
    offlineCheckThread.start();
    mOfflineCheckHandler = new Handler(offlineCheckThread.getLooper());
    initMesh(ctx, meshName);
    // CertCacheService.getInstance().load(ctx);
    MeshLogger.enableRecord(true);
    // From MainActivity.onCreate
    startMeshService(ctx);
    resetNodeState();
    autoConnect();
  }

  private void startMeshService(Context ctx) {
    try {
      // Init
      MeshService.getInstance().init(ctx, TelinkBleMeshHandler.getInstance());
      // Convert mesh info to mesh configuration
      MeshConfiguration meshConfiguration = getMeshInfo().convertToConfiguration();
      MeshService.getInstance().setupMeshNetwork(meshConfiguration);
      MeshService.getInstance().checkBluetoothState();
      // Set DLE Enable
      MeshService.getInstance().resetExtendBearerMode(SharedPreferenceHelper.getExtendBearerMode(ctx));
    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
    // set DLE enable
    MeshService.getInstance().resetExtendBearerMode(SharedPreferenceHelper.getExtendBearerMode(ctx));
  }

  private void initMesh(Context ctx, String meshName) {
    // Object configObj = FileSystem.readAsObject(ctx, MeshInfo.FILE_NAME);
    // Log.d(TAG, "MeshInfoFileName" + MeshInfo.FILE_NAME);
    MeshInfoService.getInstance().init(ObjectBox.get());
    this.mCtx = ctx;
    MeshInfo meshInfo = null;
    long id = SharedPreferenceHelper.getSelectedMeshId(this);
    if (id != -1) {
      // exists
      meshInfo = MeshInfoService.getInstance().getById(id);
    }
    if (meshInfo == null) {
      meshInfo = MeshInfo.createNewMesh(ctx, meshName);
    } else {
      meshInfo = MeshInfo.createNewMesh(this, meshName);
      // Log.d(TAG, "MeshInfoFileName" + MeshInfo.FILE_NAME);
    }

    // clearMesh(ctx);
  }

  private void clearMesh(Context ctx, String meshName) {
    MeshService meshService = MeshService.getInstance();
    if (meshService != null) {
      // meshService.idle(true); this throws null poiunter expcetoiion if we call ion
      // init
    }
    MeshInfo meshInfo = MeshInfo.createNewMesh(ctx, meshName);
    meshInfo.saveOrUpdate();
    MeshLogger.d("created new mesh");
    getInstance().setupMesh(meshInfo);
    if (meshService != null) {
      // MeshService.getInstance().setupMeshNetwork(meshInfo.convertToConfiguration());
      // this throws null poiunter expcetoiion if we call ion init
    }
    MeshInfo meshInfo2 = getInstance().getMeshInfo();
    MeshLogger.d("created new mesh");

  }

  public void setupMesh(MeshInfo mesh) {
    MeshLogger.d("setup mesh info: " + meshInfo.toString());
    this.meshInfo = mesh;
    dispatchEvent(new MeshEvent(this, MeshEvent.EVENT_TYPE_MESH_RESET, "mesh reset"));
  }

  public void resetNodeState() {
    MeshInfo mesh = TelinkBleMeshHandler.getInstance().getMeshInfo();
    if (mesh.nodes != null) {
      for (NodeInfo deviceInfo : mesh.nodes) {
        MeshNodeStatus meshNodeStatusObject = getMeshNodeStatusObject(deviceInfo.meshAddress);
        deviceInfo.setOnlineState(OnlineState.OFFLINE);
        meshNodeStatusObject.setOnlineStatus(false);
        deviceInfo.lum = 0;
        deviceInfo.temp = 0;
        meshNodeStatusObject.setLum(0);
        meshNodeStatusObject.setTemp(0);
      }
    }
  }

  public MeshInfo getMeshInfo() {
    return meshInfo;
  }

  public void setMeshInfo(MeshInfo newMeshInfo) {
    meshInfo = newMeshInfo;
  }

  public static TelinkBleMeshHandler getInstance() {
    return mThis;
  }

  protected void onNetworkInfoUpdate(NetworkInfoUpdateEvent networkInfoUpdateEvent) {
    MeshLogger.d(String.format(
        "mesh info update from local sequenceNumber-%06X ivIndex-%08X to sequenceNumber-%06X ivIndex-%08X",
        meshInfo.sequenceNumber, meshInfo.ivIndex,
        networkInfoUpdateEvent.getSequenceNumber(), networkInfoUpdateEvent.getIvIndex()));
    this.meshInfo.ivIndex = networkInfoUpdateEvent.getIvIndex();
    this.meshInfo.sequenceNumber = networkInfoUpdateEvent.getSequenceNumber();
    this.meshInfo.saveOrUpdate();
    dispatchEvent(new NetworkInfoUpdateEvent(this, NetworkInfoUpdateEvent.EVENT_TYPE_NETWORKD_INFO_UPDATE,
        networkInfoUpdateEvent.getSequenceNumber(), networkInfoUpdateEvent.getIvIndex()));
  }

  private boolean onLumStatus(NodeInfo nodeInfo, int lum, int meshAddress) {
    boolean statusChanged = false;
    int tarOnOff = lum > 0 ? 1 : 0;
    if (nodeInfo.getOnlineState().st != tarOnOff) {
      statusChanged = true;
    }
    nodeInfo.setOnlineState(OnlineState.getBySt(tarOnOff));
    MeshNodeStatus meshNodeStatusObject = getMeshNodeStatusObject(meshAddress);
    // meshNodeStatusObject.setOnOff(tarOnOff == 1);
    meshNodeStatusObject.setOnlineStatus(true);
    if (nodeInfo.lum != lum && nodeInfo.meshAddress == meshAddress) {
      statusChanged = true;
      nodeInfo.lum = lum;
    }
    if (meshNodeStatusObject.getOnOff()) {
      meshNodeStatusObject.setLum(lum);
    }
    return statusChanged;
  }

  public Handler getOfflineCheckHandler() {
    return mOfflineCheckHandler;
  }

  private boolean onTempStatus(NodeInfo nodeInfo, int temp) {
    boolean statusChanged = false;
    if (nodeInfo.temp != temp) {
      statusChanged = true;
      nodeInfo.temp = temp;
    }
    MeshNodeStatus meshNodeStatusObject = getMeshNodeStatusObject(nodeInfo.meshAddress);
    meshNodeStatusObject.setOnlineStatus(true);
    meshNodeStatusObject.setTemp(temp);
    return statusChanged;
  }

  @Override
  protected void onStatusNotificationEvent(StatusNotificationEvent statusNotificationEvent) {
    NotificationMessage message = statusNotificationEvent.getNotificationMessage();

    StatusMessage statusMessage = message.getStatusMessage();
    if (statusMessage != null) {
      NodeInfo statusChangedNode = null;
      MeshNodeStatus meshNodeStatusObject = getMeshNodeStatusObject(message.getSrc());
      meshNodeStatusObject.setOnlineStatus(true);
      if (message.getStatusMessage() instanceof OnOffStatusMessage) {
        OnOffStatusMessage onOffStatusMessage = (OnOffStatusMessage) statusMessage;
        int onOff = onOffStatusMessage.isComplete() ? onOffStatusMessage.getTargetOnOff()
            : onOffStatusMessage.getPresentOnOff();
        for (NodeInfo nodeInfo : meshInfo.nodes) {
          int[] onOffEleAdr = getTargetEleAdresses(nodeInfo, MeshSigModel.SIG_MD_G_ONOFF_S.modelId);
          if (onOffEleAdr != null) {
            for (int i = 0; i < onOffEleAdr.length; i++) {
              if (onOffEleAdr[i] == message.getSrc()) {
                if (nodeInfo.getOnlineState().st != onOff) {
                  statusChangedNode = nodeInfo;
                }
                nodeInfo.setOnlineState(OnlineState.getBySt(onOff)); // Added new thing
                meshNodeStatusObject.setOnOff(onOff == 1);
                break;
              }
            }
          }
        }
      } else if (message.getStatusMessage() instanceof LevelStatusMessage) {
        LevelStatusMessage levelStatusMessage = (LevelStatusMessage) statusMessage;
        int srcAdr = message.getSrc();
        int level = levelStatusMessage.isComplete() ? levelStatusMessage.getTargetLevel()
            : levelStatusMessage.getPresentLevel();
        int tarVal = UnitConvert.level2lum((short) level);
        for (NodeInfo onlineDevice : meshInfo.nodes) {
          if (onlineDevice.compositionData == null) {
            continue;
          }
          int[] lightnessEleAdr = getTargetEleAdresses(onlineDevice, MeshSigModel.SIG_MD_LIGHTNESS_S.modelId);
          if (lightnessEleAdr != null) {
            for (int i = 0; i < lightnessEleAdr.length; i++) {
              try {
                if (lightnessEleAdr[i] == srcAdr) {
                  if (onLumStatus(onlineDevice, tarVal, srcAdr)) {
                    statusChangedNode = onlineDevice;
                  }

                } else {
                  int[] tempEleAdr = getTargetEleAdresses(onlineDevice, MeshSigModel.SIG_MD_LIGHT_CTL_TEMP_S.modelId);
                  if (tempEleAdr != null) {
                    for (int j = 0; i < tempEleAdr.length; j++) {
                      if (tempEleAdr[j] == srcAdr) {
                        if (onlineDevice.temp != tarVal) {
                          statusChangedNode = onlineDevice;
                          onlineDevice.temp = tarVal;
                        }
                      }
                    }
                  }
                }
              } catch (Exception e) {
                Log.d(TAG, e.toString());
              }
            }
          }
        }
      } else if (message.getStatusMessage() instanceof CtlStatusMessage) {
        CtlStatusMessage ctlStatusMessage = (CtlStatusMessage) statusMessage;
        MeshLogger.d("ctl : " + ctlStatusMessage.toString());
        int srcAdr = message.getSrc();
        for (NodeInfo onlineDevice : meshInfo.nodes) {
          if (onlineDevice.meshAddress == srcAdr) {
            int lum = ctlStatusMessage.isComplete() ? ctlStatusMessage.getTargetLightness()
                : ctlStatusMessage.getPresentLightness();
            if (onLumStatus(onlineDevice, UnitConvert.lightness2lum(lum), srcAdr)) {
              statusChangedNode = onlineDevice;
            }

            int temp = ctlStatusMessage.isComplete() ? ctlStatusMessage.getTargetTemperature()
                : ctlStatusMessage.getPresentTemperature();
            if (onTempStatus(onlineDevice, UnitConvert.tempToTemp100(temp))) {
              statusChangedNode = onlineDevice;
            }
            break;
          }
        }
      } else if (message.getStatusMessage() instanceof LightnessStatusMessage) {
        LightnessStatusMessage lightnessStatusMessage = (LightnessStatusMessage) statusMessage;
        int srcAdr = message.getSrc();
        for (NodeInfo onlineDevice : meshInfo.nodes) {
          if (onlineDevice.meshAddress == srcAdr) {
            int lum = lightnessStatusMessage.isComplete() ? lightnessStatusMessage.getTargetLightness()
                : lightnessStatusMessage.getPresentLightness();
            if (onLumStatus(onlineDevice, UnitConvert.lightness2lum(lum), srcAdr)) {
              statusChangedNode = onlineDevice;
            }
            break;
          }
        }
      } else if (message.getStatusMessage() instanceof CtlTemperatureStatusMessage) {
        CtlTemperatureStatusMessage ctlTemp = (CtlTemperatureStatusMessage) statusMessage;
        int srcAdr = message.getSrc();
        for (NodeInfo onlineDevice : meshInfo.nodes) {
          if (onlineDevice.meshAddress == srcAdr) {
            int temp = ctlTemp.isComplete() ? ctlTemp.getTargetTemperature() : ctlTemp.getPresentTemperature();
            if (onTempStatus(onlineDevice, UnitConvert.lightness2lum(temp))) {
              statusChangedNode = onlineDevice;
            }
            break;
          }
        }
      }

      if (statusChangedNode != null) {
        onNodeInfoStatusChanged(statusChangedNode);
      }
    }
  }

  private void onNodeInfoStatusChanged(NodeInfo nodeInfo) {
    dispatchEvent(new NodeStatusChangedEvent(this, NodeStatusChangedEvent.EVENT_TYPE_NODE_STATUS_CHANGED, nodeInfo));
  }

  protected void onOnlineStatusEvent(OnlineStatusEvent onlineStatusEvent) {
    List<OnlineStatusInfo> infoList = onlineStatusEvent.getOnlineStatusInfoList();
    if (infoList != null && meshInfo != null) {
      NodeInfo statusChangedNode = null;
      for (OnlineStatusInfo onlineStatusInfo : infoList) {
        if (onlineStatusInfo.status == null || onlineStatusInfo.status.length < 3)
          break;
        NodeInfo deviceInfo = meshInfo.getDeviceByMeshAddress(onlineStatusInfo.address);
        MeshNodeStatus meshNodeStatusObject = getMeshNodeStatusObject(onlineStatusInfo.address);
        if (deviceInfo == null)
          continue;
        int onOff;
        if (onlineStatusInfo.sn == 0) {
          onOff = -1;
        } else {
          if (onlineStatusInfo.status[0] == 0) {
            onOff = 0;
          } else {
            onOff = 1;
          }

        }
        /*
         * if (deviceInfo.getOnOff() != onOff){
         * 
         * }
         */
        if (deviceInfo.getOnlineState().st != onOff) {
          statusChangedNode = deviceInfo;
        }
        deviceInfo.setOnlineState(OnlineState.getBySt(onOff));
        meshNodeStatusObject.setOnlineStatus(onOff != -1);
        meshNodeStatusObject.setOnOff(onOff == 1);
        if (deviceInfo.lum != onlineStatusInfo.status[0]) {
          statusChangedNode = deviceInfo;
          deviceInfo.lum = onlineStatusInfo.status[0];
          meshNodeStatusObject.setLum(onlineStatusInfo.status[0]);
        }

        if (deviceInfo.temp != onlineStatusInfo.status[1]) {
          statusChangedNode = deviceInfo;
          deviceInfo.temp = onlineStatusInfo.status[1];
          meshNodeStatusObject.setTemp(onlineStatusInfo.status[1]);
        }

      }
      // if (statusChangedNode != null) {
      onNodeInfoStatusChanged(statusChangedNode);
      // }
    }
  }

  List<Long> autoConnectFailures = new ArrayList<>();

  @Override
  protected void onMeshEvent(MeshEvent meshEvent) {
    String eventType = meshEvent.getType();
    if (MeshEvent.EVENT_TYPE_DISCONNECTED.equals(eventType)) {
      AppSettings.ONLINE_STATUS_ENABLE = false;
      for (NodeInfo nodeInfo : meshInfo.nodes) {
        nodeInfo.setOnlineState(OnlineState.OFFLINE);
        MeshNodeStatus meshNodeStatusObject = getMeshNodeStatusObject(nodeInfo.meshAddress);
        meshNodeStatusObject.setOnlineStatus(false);
      }
      // ToVerfy:
//      if (MeshService.getInstance() != null
//          && MeshService.getInstance().getActionMode().equals(MeshController.Mode.AUTO_CONNECT)) {
//        if (autoConnectFailures.size() > 10) {
//          autoConnectFailures.subList(0, autoConnectFailures.size() - 3).clear();
//        }
//        autoConnectFailures.add(System.currentTimeMillis());
//      }
    }
  }

  @Override
  public void onEventHandle(Event<String> event) {
    if (event instanceof NetworkInfoUpdateEvent) {
      // update network info: ivIndex , sequence number
      this.onNetworkInfoUpdate((NetworkInfoUpdateEvent) event);
    } else if (event instanceof StatusNotificationEvent) {
      onStatusNotificationEvent((StatusNotificationEvent) event);
    } else if (event instanceof OnlineStatusEvent) {
      onOnlineStatusEvent((OnlineStatusEvent) event);
    } else if (event instanceof MeshEvent) {
      onMeshEvent((MeshEvent) event);
    }
    dispatchEvent(event);
  }

  /********************************************************************************
   * Event API
   *******************************************************************************/

  /**
   * add event listener
   *
   * @param eventType event type
   * @param listener  listener
   */
  public void addEventListener(String eventType, EventListener<String> listener) {
    this.mEventBus.addEventListener(eventType, listener);
  }

  /**
   * remove listener
   */
  public void removeEventListener(EventListener<String> listener) {
    this.mEventBus.removeEventListener(listener);
  }

  /**
   * remove target event from listener
   *
   * @param eventType type
   * @param listener  ls
   */
  public void removeEventListener(String eventType, EventListener<String> listener) {
    this.mEventBus.removeEventListener(eventType, listener);
  }

  /**
   * remove all
   */
  public void removeEventListeners() {
    this.mEventBus.removeEventListeners();
  }

  /**
   * dispatch event from application
   */
  public void dispatchEvent(Event<String> event) {
    this.mEventBus.dispatchEvent(event);
  }

  public void autoConnect() {
    try {
      if (MeshService.getInstance() != null) {
        MeshLogger.log("main auto connect");
        MeshInfo meshInfo = TelinkBleMeshHandler.getInstance().getMeshInfo();
        if (meshInfo.nodes.size() == 0) {
          MeshService.getInstance().idle(true);
        } else {
          int directAdr = MeshService.getInstance().getDirectConnectedNodeAddress();
          NodeInfo nodeInfo = meshInfo.getDeviceByMeshAddress(directAdr);
          if (nodeInfo != null && nodeInfo.compositionData != null
              && nodeInfo.compositionData.pid == AppSettings.PID_REMOTE) {
            // if direct connected device is remote-control, disconnect
            MeshService.getInstance().idle(true);
          }
          MeshService.getInstance().autoConnect(new AutoConnectParameters());
          autoConnectFailures.clear();
          Handler handler = new Handler();
          int delayTime = 40 * 1000;
          int autoHealSeqNumberIncBy = 1024;
          handler.postDelayed(new Runnable() {
            @Override
            public void run() {
              LOG.d(TAG, "checking for auto heal -  seq number loss");
              if (!MeshService.getInstance().isProxyLogin() && autoConnectFailures.size() > 0) {
                Long currentMillis = System.currentTimeMillis();
                int failedCount = 0;
                for (int i = 0; i < autoConnectFailures.size(); i++) {
                  if (currentMillis - autoConnectFailures.get(i) < delayTime) {
                    failedCount++;
                  }
                }
                if (failedCount > 2) {
                  // ToVerify:
//                  MeshService.getInstance()
//                      .setSequenceNumber(MeshService.getInstance().getSequenceNumber() + autoHealSeqNumberIncBy, false);
                }
              }
            }
          }, delayTime);
        }
      }
    } catch (Exception e) {
      Log.d(TAG, e.toString());
    }
  }

  ArrayList<MeshNodeStatus> meshNodeStatuses = new ArrayList<MeshNodeStatus>();

  MeshNodeStatus getMeshNodeStatusObject(int meshAddress) {
    for (int i = 0; i < meshNodeStatuses.size(); i++) {
      if (meshNodeStatuses.get(i).getMeshAddress() == meshAddress) {
        return meshNodeStatuses.get(i);
      }
    }
    MeshNodeStatus newMeshNodeStatus = new MeshNodeStatus(meshAddress);
    newMeshNodeStatus.setEventsCallback(nodeStatus -> {
      if (meshEventCallback != null) {
        ArrayList<MeshNodeStatus> meshNodeStatuses = new ArrayList<>();
        meshNodeStatuses.add(nodeStatus);
        String json = new Gson().toJson(meshNodeStatuses);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
        pluginResult.setKeepCallback(true);
        meshEventCallback.sendPluginResult(pluginResult);
      }
    });
    meshNodeStatuses.add(newMeshNodeStatus);
    return newMeshNodeStatus;
  }

  ArrayList<MeshNodeStatus> getAllMeshNodeStatusObjects() {
    return meshNodeStatuses;
  }

  public int[] getTargetEleAdresses(NodeInfo nodeInfo, int tarModelId) {
    if (nodeInfo.compositionData == null)
      return null;
    int[] res = new int[nodeInfo.compositionData.elements.size()];
    for (int i = 0; i < nodeInfo.compositionData.elements.size(); i++) {
      res[i] = -1;
    }
    int c = 0;
    int eleAdr = nodeInfo.meshAddress;
    // ToVerfy:
//    for (CompositionData.Element element : nodeInfo.compositionData.elements) {
//      if (element.sigModels != null) {
//        for (int modelId : element.sigModels) {
//          if (modelId == tarModelId) {
//            res[c++] = eleAdr;
//          }
//        }
//      }
//
//      if (element.vendorModels != null) {
//        for (int modelId : element.vendorModels) {
//          if (modelId == tarModelId) {
//            res[c++] = eleAdr;
//          }
//        }
//      }
//
//      eleAdr++;
//    }

    if (c == 0) {
      return null;
    }
    return res;
  }

  CallbackContext meshEventCallback;

  public void setMeshEventCallback(CallbackContext meshEventCallback) {
    this.meshEventCallback = meshEventCallback;
  }
}
