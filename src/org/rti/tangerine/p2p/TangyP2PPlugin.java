/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package org.rti.tangerine.p2p;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.pm.PackageManager;
import android.Manifest;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;

import org.apache.cordova.*;
import org.drulabs.localdash.db.DBAdapter;
import org.drulabs.localdash.model.DeviceDTO;
import org.drulabs.localdash.transfer.ConnectionListener;
import org.drulabs.localdash.transfer.DataHandler;
import org.drulabs.localdash.transfer.DataSender;
import org.drulabs.localdash.transfer.TransferConstants;
import org.drulabs.localdash.utils.ConnectionUtils;
import org.drulabs.localdash.utils.Utility;
import org.drulabs.localdash.wifidirect.WiFiDirectBroadcastReceiver;

import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

public class TangyP2PPlugin extends CordovaPlugin implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener
{
    public static final String FIRST_DEVICE_CONNECTED = "first_device_connected";
    public static final String KEY_FIRST_DEVICE_IP = "first_device_ip";

    public static final String ACTION_CHAT_RECEIVED = "org.drulabs.localdash.chatreceived";
    public static final String KEY_CHAT_DATA = "chat_data_key";

    private static final String WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int WRITE_PERM_REQ_CODE = 19;

    private static final String TAG = "TangyP2PPlugin";

    private ConnectionListener connListener;
    private int myPort;

    private boolean isConnectionListenerRunning = false;

    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel wifip2pChannel;
    WiFiDirectBroadcastReceiver wiFiDirectBroadcastReceiver;
    private boolean isWDConnected = false;
    private boolean isWifiP2pEnabled = false;
    private String pluginMessage;

    public static final int SEARCH_REQ_CODE = 0;

    public CallbackContext cbContext;

    public static final String PERMISSION_TO_WIFI = Manifest.permission.CHANGE_WIFI_STATE;
//    public static final int PERMISSION_DENIED_ERROR = 20;
    private static final String PERMISSION_DENIED_ERROR = "Permission denied";
    String [] permissions = { PERMISSION_TO_WIFI };

    public PluginResult pluginResult;

    private List<WifiP2pDevice> devices = new ArrayList<WifiP2pDevice>();
    final HashMap<String, String> buddies = new HashMap<String, String>();
    private final String serviceName = "Tangerine" + (int) (Math.random() * 1000);


    /**
     * Sets the context of the Command.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cbContext = null;
        Context context = cordova.getActivity().getApplicationContext();
        myPort = ConnectionUtils.getPort(context);
        connListener = new ConnectionListener(context, myPort);
        setupPeerDiscovery(context);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        LOG.d(TAG, "We are entering execute of TangyP2PPlugin");
        cbContext = callbackContext;
        if(action.equals("getPermission"))
        {
            LOG.d(TAG, "Checking permissions.");
            if(hasPermisssion())
            {
                PluginResult r = new PluginResult(PluginResult.Status.OK);
                cbContext.sendPluginResult(r);
                return true;
            }
            else {
                Log.i(TAG, "Requesting permissions.");
                PermissionHelper.requestPermissions(this, 0, permissions);
            }
            return true;
        }
        else if ("init".equals(action)) {
            //callbackContext.success();
            //return true;
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
//                    TangyP2PPlugin.init();
                        Log.i(TAG, "init");
                        Context context = cordova.getActivity().getApplicationContext();
                        // use cbContext?
                        String myIP = Utility.getWiFiIPAddress(context);
                        Log.i(TAG, "Saving myIP: " + myIP);
                        Utility.saveString(context, TransferConstants.KEY_MY_IP, myIP);

//                        startConnectionListener();

                        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
                        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

                        startRegistration(ConnectionUtils.getPort(context));

//                        wifiP2pManager.discoverPeers(wifip2pChannel, new WifiP2pManager.ActionListener() {
//                            @Override
//                            public void onSuccess() {
//                                Log.i(TAG, "Peer discovery started");
//                            }
//
//                            @Override
//                            public void onFailure(int reasonCode) {
//                                // reasonCode 2: BUSY - Indicates that the operation failed because the framework is busy and unable to service the request
//                                // reasonCode 0: ERROR - Indicates that the operation failed due to an internal error.
//                                // reasonCode 1: P2P_UNSUPPORTED -  Indicates that the operation failed because p2p is unsupported on the device.
//                                Log.e(TAG, "Peer discovery failure: " + reasonCode);
//                            }
//                        });
//
//                        startConnectionListener(TransferConstants.INITIAL_DEFAULT_PORT);
//                        pluginMessage = "Connection Listener Running: " + isConnectionListenerRunning;
//                        Log.i(TAG, pluginMessage);
//                        setupPeerDiscovery(context);
//                        pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
//                        pluginResult.setKeepCallback(true);
//                        cbContext.sendPluginMessage(pluginResult);
                    }
                });
                return true;
            } else {
                String message = "Requesting permissions";
                Log.i(TAG, message);
                PermissionHelper.requestPermissions(this, 0, permissions);
                pluginResult = new PluginResult(PluginResult.Status.OK, message);
                pluginResult.setKeepCallback(true);
                cbContext.sendPluginResult(pluginResult);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        } else if ("startRegistration".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "startRegistration");
                        Context context = cordova.getActivity().getApplicationContext();
                        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
                        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

                        startRegistration(ConnectionUtils.getPort(context));
                        pluginMessage = "startRegistration initiated. isConnectionListenerRunning: " + isConnectionListenerRunning;
                        sendPluginMessage(pluginMessage, true);
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        } else if ("discoverPeers".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
//                    TangyP2PPlugin.init();
                        Log.i(TAG, "discoverPeers");
                        Context context = cordova.getActivity().getApplicationContext();
                        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
                        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

                        wifiP2pManager.discoverPeers(wifip2pChannel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "Peer discovery started");
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                // reasonCode 2: BUSY - Indicates that the operation failed because the framework is busy and unable to service the request
                                // reasonCode 0: ERROR - Indicates that the operation failed due to an internal error.
                                // reasonCode 1: P2P_UNSUPPORTED -  Indicates that the operation failed because p2p is unsupported on the device.
                                Log.e(TAG, "Peer discovery failure: " + reasonCode);
                            }
                        });

                        startConnectionListener(TransferConstants.INITIAL_DEFAULT_PORT);
                        pluginMessage = "discoverPeers; Connection Listener Running: " + isConnectionListenerRunning;
                        sendPluginMessage(pluginMessage, true);
//                        callbackContext.success(pluginMessage); // Thread-safe.
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        } else if ("transferTo".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        discoverService();
                        try {
                            String safeDeviceAddress = args.getString(0);
                            String deviceAddress = safeDeviceAddress.replaceAll("_", ":");
                            pluginMessage = "transferTo: " + deviceAddress;
                            connect(deviceAddress);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        sendPluginMessage(pluginMessage, true);
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        }
        return false;  // Returning false results in a "MethodNotFound" error.
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);

        Context context = cordova.getActivity().getApplicationContext();
        setupPeerDiscovery(context);
    }

    private void setupPeerDiscovery(Context context) {
        IntentFilter localFilter = new IntentFilter();
        localFilter.addAction(DataHandler.DEVICE_LIST_CHANGED);
        localFilter.addAction(FIRST_DEVICE_CONNECTED);
        localFilter.addAction(DataHandler.CHAT_REQUEST_RECEIVED);
        localFilter.addAction(DataHandler.CHAT_RESPONSE_RECEIVED);
        LocalBroadcastManager.getInstance(context).registerReceiver(localDashReceiver,
                localFilter);

        IntentFilter wifip2pFilter = new IntentFilter();
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // When an application needs to fetch the current list of peers, it can request the list of
        // peers with requestPeers from wiFiDirectBroadcastReceiver
        wiFiDirectBroadcastReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager,
                wifip2pChannel, this);
//        registerReceiver(wiFiDirectBroadcastReceiver, wifip2pFilter);
        context.registerReceiver(wiFiDirectBroadcastReceiver, wifip2pFilter);

        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(DataHandler.DEVICE_LIST_CHANGED));
    }

    private BroadcastReceiver localDashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case FIRST_DEVICE_CONNECTED:
//                    connListener.tearDown();
//                    int newPort = ConnectionUtils.getPort(LocalDashWiFiDirect.this);
//                    connListener = new ConnectionListener(LocalDashWiFiDirect.this,
//                            newPort);
//                    connListener.start();
//                    appController.stopConnectionListener();
//                    appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
                    TangyP2PPlugin.this.restartConnectionListenerWith(ConnectionUtils.getPort(context));

                    String senderIP = intent.getStringExtra(KEY_FIRST_DEVICE_IP);
                    int port = DBAdapter.getInstance(context).getDevice
                            (senderIP).getPort();
                    DataSender.sendCurrentDeviceData(context, senderIP, port, true);
                    isWDConnected = true;
                    String pluginMessage = "First device connected at " + senderIP + " port: " + port;
                    sendPluginMessage(pluginMessage, true);
                    break;
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    // Determine if Wifi P2P mode is enabled or not, alert
                    // the Activity.
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
//                        activity.setIsWifiP2pEnabled(true);
                        pluginMessage = "WIFI P2P is enabled";
                        sendPluginMessage(pluginMessage, true);
                    } else {
//                        activity.setIsWifiP2pEnabled(false);
                        pluginMessage = "WIFI P2P is NOT enabled";
                        sendPluginMessage(pluginMessage, true);
                    }
                    break;
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    if (wifiP2pManager == null) {
                        return;
                    }

                    NetworkInfo networkInfo = (NetworkInfo) intent
                            .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                    if (networkInfo.isConnected()) {

                        // We are connected with the other device, request connection
                        // info to find group owner IP
                        pluginMessage = "Connected to p2p network. " ;
                        sendPluginMessage(pluginMessage, true);
//                        wifiP2pManager.requestConnectionInfo(wifip2pChannel, connectionListener);
                    }
                case DataHandler.DEVICE_LIST_CHANGED:
                    ArrayList<DeviceDTO> devices = DBAdapter.getInstance(context)
                            .getDeviceList();
                    int peerCount = (devices == null) ? 0 : devices.size();
                    if (peerCount > 0) {
//                        progressBarLocalDash.setVisibility(View.GONE);
//                        deviceListFragment = new PeerListFragment();
//                        Bundle args = new Bundle();
//                        args.putSerializable(PeerListFragment.ARG_DEVICE_LIST, devices);
//                        deviceListFragment.setArguments(args);
//
//                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//                        ft.replace(R.id.deviceListHolder, deviceListFragment);
//                        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
//                        ft.commit();
                        StringBuilder sb = new StringBuilder();
                        for (DeviceDTO d : devices)
                        {
                            String name = d.getDeviceName();
                            String ip = d.getIp();
                            int devicePort = d.getPort();
                            String player = d.getPlayerName();
                            String identifier = "name: " + name + " ip: " + ip + " port: " + devicePort + " player: " + player;

                            sb.append(identifier);
                            sb.append(System.lineSeparator());
                        }
                        String devicesString = sb.toString();
                        Log.i(TAG,"Devices: " + devicesString);
                    }
//                    setToolBarTitle(peerCount);
                    Log.i(TAG,"peerCount: " + peerCount);
                    break;
//                case DataHandler.CHAT_REQUEST_RECEIVED:
//                    DeviceDTO chatRequesterDevice = (DeviceDTO) intent.getSerializableExtra(DataHandler
//                            .KEY_CHAT_REQUEST);
//                    DialogUtils.getChatRequestDialog(TangyP2PPlugin.this,
//                            chatRequesterDevice).show();
//                    break;
//                case DataHandler.CHAT_RESPONSE_RECEIVED:
//                    boolean isChatRequestAccepted = intent.getBooleanExtra(DataHandler
//                            .KEY_IS_CHAT_REQUEST_ACCEPTED, false);
//                    if (!isChatRequestAccepted) {
//                        NotificationToast.showToast(TangyP2PPlugin.this, "Chat request " +
//                                "rejected");
//                    } else {
//                        DeviceDTO chatDevice = (DeviceDTO) intent.getSerializableExtra(DataHandler
//                                .KEY_CHAT_REQUEST);
//                        DialogUtils.openChatActivity(TangyP2PPlugin.this, chatDevice);
//                        NotificationToast.showToast(TangyP2PPlugin.this, chatDevice
//                                .getPlayerName() + "Accepted Chat request");
//                    }
//                    break;
                default:
                    break;
            }
        }
    };

    private void startRegistration(int port) {

        Context context = cordova.getActivity().getApplicationContext();

        //  Create a string map containing information about your service.
        Map record = new HashMap();
        record.put("listenport", String.valueOf(port));
        record.put("buddyname", serviceName);
        record.put("available", "visible");

        Log.i(TAG,"startRegistration for : " + serviceName);

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
//        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

        wifiP2pManager.addLocalService(wifip2pChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                String ipAddress = Utility.getString(context, TransferConstants.KEY_MY_IP);
//                pluginMessage = "Now available as: " + serviceName + ": " + ipAddress + ":" + String.valueOf(port);
                String message = "Local service";
                String portStr = String.valueOf(port);
                JSONObject deviceInfo = new JSONObject();
                try {
                    deviceInfo.put("serviceName", serviceName);
                    deviceInfo.put("ipAddress", ipAddress);
                    deviceInfo.put("port", portStr);
                    deviceInfo.put("message", message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String jsonStr = deviceInfo.toString();
                sendPluginMessage(jsonStr, false);
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.e(TAG,"local service addition FAILED for: " + serviceName);
            }
        });
    }

    /**
     * Sends a message to the PluginResult and debug log.
     * @param pluginMessage
     * @param keepCallback
     */
    private void sendPluginMessage(String pluginMessage, boolean keepCallback) {
        Log.d(TAG, pluginMessage);
        pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
        pluginResult.setKeepCallback(keepCallback);
        cbContext.sendPluginResult(pluginResult);
    }

    public void stopConnectionListener() {
        if (!isConnectionListenerRunning) {
            return;
        }
        if (connListener != null) {
            connListener.tearDown();
            connListener = null;
        }
        isConnectionListenerRunning = false;
    }

    public void startConnectionListener() {
        Context context = cordova.getActivity().getApplicationContext();
        if (isConnectionListenerRunning) {
            return;
        }
        if (connListener == null) {
            connListener = new ConnectionListener(context, myPort);
        }
        if (!connListener.isAlive()) {
            connListener.interrupt();
            connListener.tearDown();
            connListener = null;
        }
        connListener = new ConnectionListener(context, myPort);
        connListener.start();
        isConnectionListenerRunning = true;
    }

    public void startConnectionListener(int port) {
        myPort = port;
        startConnectionListener();
    }

    public void restartConnectionListenerWith(int port) {
        stopConnectionListener();
        startConnectionListener(port);
    }

    public boolean isConnListenerRunning() {
        return isConnectionListenerRunning;
    }

    public int getPort(){
        return myPort;
    }

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {

        PluginResult result;

        ArrayList<DeviceDTO> deviceDTOs = new ArrayList<>();

        List<WifiP2pDevice> refreshedPeers = (new ArrayList<>());
        refreshedPeers.addAll(peerList.getDeviceList());
        if (!refreshedPeers.equals(devices)) {
            devices.clear();
            devices.addAll(refreshedPeers);
        }
        String deviceNames = "";
        JSONArray jsonArray = new JSONArray();
        for (WifiP2pDevice device : devices) {
            DeviceDTO deviceDTO = new DeviceDTO();
            deviceDTO.setIp(device.deviceAddress);
            deviceDTO.setPlayerName(device.deviceName);
            deviceDTO.setDeviceName(new String());
            deviceDTO.setOsVersion(new String());
            deviceDTO.setPort(-1);
            deviceDTOs.add(deviceDTO);
            String identifier = " MAC: " + device.deviceAddress + " device.deviceName: " + device.deviceName;
            pluginMessage = deviceNames + "<br>" + identifier;
            Log.i(TAG,"Peer found! : " + identifier);
            JSONObject deviceInfo = new JSONObject();
            try {
                deviceInfo.put("deviceAddress", device.deviceAddress);
                deviceInfo.put("deviceName", device.deviceName);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            jsonArray.put(deviceInfo);
        }
        String jsonStr = jsonArray.toString();

//        navigator.notification.alert(
//                deviceNames,  // message
//                alertDismissed,         // callback
//                'Peers Available',            // title
//                'Done'                  // buttonName
//        );
        String message = "";
        if (devices.size() == 0) {
            message = "No peers found...";
        } else {
            message = "Peers found";
        }
        Log.i(TAG,message);
        pluginResult = new PluginResult(PluginResult.Status.OK, jsonStr);
        pluginResult.setKeepCallback(true);
        cbContext.sendPluginResult(pluginResult);

//        progressBarLocalDash.setVisibility(View.GONE);
//        deviceListFragment = new PeerListFragment();
//        Bundle args = new Bundle();
//        args.putSerializable(PeerListFragment.ARG_DEVICE_LIST, deviceDTOs);
//        deviceListFragment.setArguments(args);
//
//        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//        ft.replace(R.id.deviceListHolder, deviceListFragment);
//        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
//        ft.commit();
    }

    boolean isConnectionInfoSent = false;

    // TODO: Probably don't need this: only for cases where multiple devices are going to be connected to a single device
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        LOG.d(TAG, "onConnectionInfoAvailable: isConnectionInfoSent: " + isConnectionInfoSent);
        Context context = cordova.getActivity().getApplicationContext();
        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner && !isConnectionInfoSent) {

            isWDConnected = true;
            LOG.d(TAG, "onConnectionInfoAvailable: groupFormed; isWDConnected: " + isWDConnected);

//            connListener.tearDown();
//            connListener = new ConnectionListener(LocalDashWiFiDirect.this, ConnectionUtils.getPort
//                    (LocalDashWiFiDirect.this));
//            connListener.start();
//            appController.stopConnectionListener();
//            appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
            this.restartConnectionListenerWith(ConnectionUtils.getPort(context));

            String groupOwnerAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            DataSender.sendCurrentDeviceDataWD(context, groupOwnerAddress, TransferConstants
                    .INITIAL_DEFAULT_PORT, true);
            isConnectionInfoSent = true;
        }
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
        LOG.d(TAG, "onRequestPermissionResult: requestCode: " + requestCode);
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if(cbContext != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "onRequestPermissionResult: Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR);
                    result.setKeepCallback(true);
                    cbContext.sendPluginResult(result);
                    return;
                }

            }
            LOG.d(TAG, "onRequestPermissionResult: ok");
            result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            cbContext.sendPluginResult(result);
        }
    }

    public boolean hasPermisssion() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
//                LOG.d(TAG, "hasPermisssion() is false for: " + p);
                LOG.d(TAG, "hasPermisssion() is false for: " + p + " but we will let this pass for now. TODO fisx.");
//                return false;
            }
        }
        LOG.d(TAG, "Plugin has the correct permissions.");
        return true;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */

//    public void requestPermissions(int requestCode)
//    {
//        PermissionHelper.requestPermissions(this, requestCode, permissions);
//    }

    //    @Override
    public void connect(String deviceAddress) {
        WifiP2pDevice device = null;
        for(WifiP2pDevice peer : devices) {
            if(peer.deviceAddress.equals(deviceAddress)) device = peer;
        }
        if (device != null) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            config.wps.setup = WpsInfo.PBC;

            WifiP2pDevice finalDevice = device;
            wifiP2pManager.connect(wifip2pChannel, config, new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    // WiFiDirectBroadcastReceiver notifies us. Ignore for now.
                    pluginMessage = "connect success: " + finalDevice.deviceAddress;
                    Log.d(TAG, pluginMessage);
                    pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                    pluginResult.setKeepCallback(true);
                    cbContext.sendPluginResult(pluginResult);
                }

                @Override
                public void onFailure(int reason) {
//                Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.",
//                        Toast.LENGTH_SHORT).show();
                    pluginMessage = "Connect failed. Retry.";
                    Log.d(TAG, pluginMessage);
                    pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                    pluginResult.setKeepCallback(true);
                    cbContext.sendPluginResult(pluginResult);
                }
            });
        } else {
            pluginMessage = "Cannot connect to " + deviceAddress;
            Log.d(TAG, pluginMessage);
            pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
            pluginResult.setKeepCallback(true);
            cbContext.sendPluginResult(pluginResult);
        }
    }

    private void discoverService() {

        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            /* Callback includes:
             * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
             * record: TXT record dta as a map of key/value pairs.
             * device: The device running the advertised service.
             */

            public void onDnsSdTxtRecordAvailable(
                    String fullDomain, Map record, WifiP2pDevice device) {
                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
                buddies.put(device.deviceAddress, String.valueOf(record.get("buddyname")));
            }
        };

        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                                WifiP2pDevice resourceType) {

                // Update the device name with the human-friendly version from
                // the DnsTxtRecord, assuming one arrived.
                resourceType.deviceName = buddies
                        .containsKey(resourceType.deviceAddress) ? buddies
                        .get(resourceType.deviceAddress) : resourceType.deviceName;

                // Add to the custom adapter defined specifically for showing
                // wifi devices.
//                WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
//                        .findFragmentById(R.id.frag_peerlist);
//                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
//                        .getListAdapter());
//
//                adapter.add(resourceType);
//                adapter.notifyDataSetChanged();

                pluginMessage = "onBonjourServiceAvailable " + instanceName
                        + " at " + resourceType.deviceAddress
                        + " named: " + resourceType.deviceName;
                Log.d(TAG, pluginMessage);
                pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                pluginResult.setKeepCallback(true);
                cbContext.sendPluginResult(pluginResult);
            }
        };

        wifiP2pManager.setDnsSdResponseListeners(wifip2pChannel, servListener, txtListener);

        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        wifiP2pManager.addServiceRequest(wifip2pChannel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        // Success!
                        pluginMessage = "Request to join service is successful! ";
                        sendPluginMessage(pluginMessage, true);
                    }

                    @Override
                    public void onFailure(int code) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                        pluginMessage = "Request to join service FAILED:  " + code;
                        sendPluginMessage(pluginMessage, true);
                    }
                });

        wifiP2pManager.discoverServices(wifip2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // Success!
                pluginMessage = "Request to discover service is successful! ";
                sendPluginMessage(pluginMessage, true);
            }

            @Override
            public void onFailure(int code) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                if (code == WifiP2pManager.P2P_UNSUPPORTED) {
                    Log.d(TAG, "P2P isn't supported on this device.");
                } else {
                    Log.d(TAG, "P2P discoverServices failed: " + code);
                }
            }
        });
    }
}
