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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.drulabs.localdash.transfer.DataSender;
import org.drulabs.localdash.transfer.TransferConstants;
import org.drulabs.localdash.utils.Utility;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TangyP2PPlugin extends CordovaPlugin implements WifiP2pManager.ChannelListener, WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, WiFiDirectBroadcastReceiver.DeviceActionListener
{
    public static final String FIRST_DEVICE_CONNECTED = "first_device_connected";
    public static final String KEY_FIRST_DEVICE_IP = "first_device_ip";

    public static final String ACTION_CHAT_RECEIVED = "org.drulabs.localdash.chatreceived";
    public static final String KEY_CHAT_DATA = "chat_data_key";

    private static final String WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int WRITE_PERM_REQ_CODE = 19;

    public static final String TAG = "TangyP2PPlugin";

//    private ConnectionListener connListener;
    private int myPort;

    private boolean isConnectionListenerRunning = false;

//    WifiP2pManager wifiP2pManager;
//    WifiP2pManager.Channel wifip2pChannel;
//    WiFiDirectBroadcastReceiver wiFiDirectBroadcastReceiver;
    private boolean isWDConnected = false;
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
    private static final String SERVICE_INSTANCE = "Tangerine";
    private final String serviceName = SERVICE_INSTANCE + (int) (Math.random() * 1000);

    WifiP2pDnsSdServiceRequest serviceRequest = null;
    private String peerIP = null;
    private int peerPort = -1;
    WiFiP2pServiceHolder serviceHolder;
    private boolean initFileServer = false;

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1001;

    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver = null;

    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private WifiP2pDevice device;

    public HashMap<String, Object> responses;
    public NanoHTTPDWebserver nanoHTTPDWebserver;

    /**
     * Sets the context of the Command.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cbContext = null;
        this.responses = new HashMap<String, Object>();
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        LOG.d(TAG, "We are entering execute of TangyP2PPlugin");
        cbContext = callbackContext;
        if(action.equals("getPermission"))
        {
            LOG.d(TAG, "Checking permissions.");
            if(hasPermisssion())
            {
//                PluginResult r = new PluginResult(PluginResult.Status.OK);
//                cbContext.sendPluginResult(r);
                sendPluginMessage(PluginResult.Status.OK.toString(), true);
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
            if (hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "init");
                        Context context = cordova.getActivity().getApplicationContext();
                        setupPeerDiscovery(context);
                        myPort = TransferConstants.INITIAL_DEFAULT_PORT;
                    }
                });
                return true;
            } else {
                String message = "Requesting permissions";
//                Log.i(TAG, message);
                PermissionHelper.requestPermissions(this, 0, permissions);
                sendPluginMessage(message, true);
            }
            return true;
        } else if ("discoverPeers".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "discoverPeers");
                        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                pluginMessage = "Discovery Initiated";
                                sendPluginMessage(pluginMessage, true);
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                switch(reasonCode) {
                                    case 2:
                                        pluginMessage = "Peer discovery failure: BUSY - Indicates that the operation failed because the framework is busy and unable to service the request. reasonCode: " + reasonCode;
                                        break;
                                    case 1:
                                        pluginMessage = "Peer discovery failure: P2P_UNSUPPORTED -  Indicates that the operation failed because p2p is unsupported on the device. reasonCode:" + reasonCode;
                                        break;
                                    case 0:
                                        pluginMessage = "Peer discovery failure: ERROR - Indicates that the operation failed due to an internal error. " + reasonCode;
                                        break;
                                    default:
                                        pluginMessage = "Peer discovery failure: ERROR - reasonCode: " + reasonCode;
                                }
                                sendPluginMessage(pluginMessage, true);
                            }
                        });
                        pluginMessage = "discoverPeers; Connection Listener Running: " + isConnectionListenerRunning;
                        sendPluginMessage(pluginMessage, true);
//                        callbackContext.success(pluginMessage); // Thread-safe.
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
            }
            return true;
        } else if ("transferTo".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
//                        discoverService();
                        try {
                            String safeDeviceAddress = args.getString(0);
                            String deviceAddress = safeDeviceAddress.replaceAll("_", ":");
                            pluginMessage = "transferTo: " + deviceAddress;
                            WifiP2pConfig config = new WifiP2pConfig();
                            config.deviceAddress = deviceAddress;
                            config.wps.setup = WpsInfo.PBC;
                            connect(config);
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
            }
            return true;
        }
        return false;  // Returning false results in a "MethodNotFound" error.
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        Log.d(TAG, "onResume");
        Context context = cordova.getActivity().getApplicationContext();
//        setupPeerDiscovery(context);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver,
                intentFilter);

    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        Context context = cordova.getActivity().getApplicationContext();
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
    }


    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        peers.clear();
        Log.d(TAG, "Clearing peers.");
        JSONObject deviceInfo = new JSONObject();
        String jsonStr = deviceInfo.toString();
        sendPluginMessage(jsonStr, true);
    }

    private void setupPeerDiscovery(Context context) {
        Log.i(TAG, "initialize intentFilter, manager, channel, and receiver");
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, context.getMainLooper(), null);

//      Context context = cordova.getActivity().getApplicationContext();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        context.registerReceiver(receiver, intentFilter);
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

    /**
     * Sends a message to the PluginResult and debug log.
     * @param pluginMessage
     * @param keepCallback
     */
    public static void sendPluginMessage(String pluginMessage, boolean keepCallback, CallbackContext cbContext, String tag) {
        if (tag == null) {
            tag = TAG;
        }
        Log.d(tag, pluginMessage);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
        pluginResult.setKeepCallback(keepCallback);
        cbContext.sendPluginResult(pluginResult);
    }


    public int getPort(){
        return myPort;
    }

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
        sendPluginMessage("Is WifiP2P enabled? " + isWifiP2pEnabled, true);
    }

    /**
     * @return this device
     */
    public WifiP2pDevice getDevice() {
        return device;
    }

    private static String getDeviceStatus(int deviceStatus) {
        Log.d(TAG, "Peer status :" + deviceStatus);
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";

        }
    }

    /**
     * Update UI for this device.
     *
     * @param device WifiP2pDevice object
     */
    public void updateThisDevice(WifiP2pDevice device) {
        this.device = device;
        JSONObject deviceInfo = new JSONObject();
        String deviceStatus = getDeviceStatus(device.status);
        try {
            deviceInfo.put("deviceAddress", device.deviceAddress);
            deviceInfo.put("deviceName", device.deviceName);
            deviceInfo.put("type", "self");
            deviceInfo.put("deviceStatus", deviceStatus);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String jsonStr = deviceInfo.toString();
        sendPluginMessage(jsonStr, true);
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        JSONArray jsonArray = new JSONArray();
        peers.clear();
        peers.addAll(peerList.getDeviceList());
//        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
        if (peers.size() == 0) {
            Log.d(TAG, "No devices found");
            return;
        } else {
            for (WifiP2pDevice peer : peers) {
//            if(peer.deviceAddress.equals(deviceAddress)) device = peer;
//        }
                JSONObject deviceInfo = new JSONObject();
                String deviceStatus = getDeviceStatus(peer.status);
                try {
                    deviceInfo.put("deviceAddress", peer.deviceAddress);
                    deviceInfo.put("deviceName", peer.deviceName);
                    deviceInfo.put("type", "peer");
                    deviceInfo.put("deviceStatus", deviceStatus);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                jsonArray.put(deviceInfo);
            }
        }
        String jsonStr = jsonArray.toString();
        sendPluginMessage(jsonStr, true);
    }

    boolean isConnectionInfoSent = false;

    // TODO: Probably don't need this: only for cases where multiple devices are going to be connected to a single device
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
//        String gOwnerIp = "";
        String gOwner = "";
        if (!wifiP2pInfo.groupFormed) {
            peerIP = null;
            gOwner = "null";
        } else {
            peerIP = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            gOwner = (wifiP2pInfo.isGroupOwner == true) ? "Yes" : "No";
        }
        String pluginMessage = "onConnectionInfoAvailable: isConnectionInfoSent: " + isConnectionInfoSent + " peerIP: " + peerIP + " gOwner: " + gOwner;
        sendPluginMessage(pluginMessage, true);
        Context context = cordova.getActivity().getApplicationContext();

        // Dotted-decimal IP address.

        String myIP = Utility.getWiFiIPAddress(context);

        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
            if (initFileServer == false) {

                // start services.
//                new FileServerAsyncTask(cordova.getActivity(), "Here is my statusText.", 8080, cbContext)
////                        .execute();
//                          .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//                initFileServer = true;
//                pluginMessage = "I am the server! Time to make my wares available at: " + peerIP;
//                sendPluginMessage(pluginMessage, true);

//                cordova.getActivity().runOnUiThread(new Runnable() {
//                cordova.getThreadPool().execute(new Runnable() {
//                    public void run() {
//                        // start services.
//                        initFileServer = true;
//                        try {
//                            Log.d(TAG, "FileServer started.");
//
//                            /**
//                             * Create a server socket and wait for client connections. This
//                             * call blocks until a connection is accepted from a client
//                             */
//                            ServerSocket serverSocket = new ServerSocket(myPort);
//                            Socket client = serverSocket.accept();
//                            String message = "Accepting connections on the server at " + myPort;
//                            TangyP2PPlugin.sendPluginMessage(message, true, cbContext, TAG);
//
//                            /**
//                             * If this code is reached, a client has connected and transferred data
//                             * Save the input stream from the client as a JPEG file
//                             */
//                            final File f = new File(Environment.getExternalStorageDirectory() + "/"
//                                    + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
//                                    + ".jpg");
//
//                            File dirs = new File(f.getParent());
//                            if (!dirs.exists())
//                                dirs.mkdirs();
//                            f.createNewFile();
//                            InputStream inputstream = client.getInputStream();
//                            copyFile(inputstream, new FileOutputStream(f));
//
//                            serverSocket.close();
////            return f.getAbsolutePath();
//                        } catch (IOException e) {
//                            TangyP2PPlugin.sendPluginMessage(e.getMessage(), true, cbContext, TAG);
////                            return null;
//                        }
//                        String pluginMessage = "I am the server! Time to make my wares available at: " + peerIP;
//                        sendPluginMessage(pluginMessage, true);
//                    }
//            });
                initFileServer = true;
                pluginMessage = "I am the server! Time to make my wares available at: " + peerIP;
                sendPluginMessage(pluginMessage, true);
                try {
                    JSONArray args = new JSONArray();
                    this.start(args, cbContext);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

        } else if (wifiP2pInfo.groupFormed) {
            pluginMessage = "I am the client! Click the connect button to send data to: " + myIP;
            sendPluginMessage(pluginMessage, true);
        }
        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner && !isConnectionInfoSent) {

            isWDConnected = true;
            LOG.d(TAG, "onConnectionInfoAvailable: groupFormed; isWDConnected: " + isWDConnected);

//            connListener.tearDown();
//            connListener = new ConnectionListener(LocalDashWiFiDirect.this, ConnectionUtils.getPort
//                    (LocalDashWiFiDirect.this));
//            connListener.start();
//            appController.stopConnectionListener();
//            appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
//            this.restartConnectionListenerWith(ConnectionUtils.getPort(context));
//            this.restartConnectionListenerWith(TransferConstants.INITIAL_DEFAULT_PORT);

            String groupOwnerAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            pluginMessage = "I am the client and i'd like to download from: " + groupOwnerAddress + ":" + TransferConstants
                    .INITIAL_DEFAULT_PORT;
            sendPluginMessage(pluginMessage, true);
            DataSender.sendCurrentDeviceDataWD(context, groupOwnerAddress, TransferConstants
                    .INITIAL_DEFAULT_PORT, true);
            isConnectionInfoSent = true;
        }
    }
    }


//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
//                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
//        != PackageManager.PERMISSION_GRANTED) {
//    requestPermissions(this.PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
//    // After this point you wait for callback in
//    // onRequestPermissionsResult(int, String[], int[]) overridden method
//}

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


    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                String pluginMessage = "Connect failed. Retry.";
                sendPluginMessage(pluginMessage, true);
            }
        });
    }

    @Override
    public void disconnect() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                Log.d(TAG, "Disconnected. ");
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            pluginMessage = "Channel lost. Trying again ";
            sendPluginMessage(pluginMessage, true);
            resetData();
            retryChannel = true;
            Context context = cordova.getActivity().getApplicationContext();
            manager.initialize(context, context.getMainLooper(),  this);
        } else {
            pluginMessage = "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.";
            sendPluginMessage(pluginMessage, true);
        }
    }

    private class WiFiP2pServiceHolder {
        WifiP2pDevice device;
        String instanceName;
        String registrationType;
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
        return true;
    }

    /**
     * Starts the server
     * @param args
     * @param callbackContext
     */
    private void start(JSONArray args, CallbackContext callbackContext) throws JSONException, IOException {
        int port = 8080;

        if (args.length() == 1) {
            port = args.getInt(0);
        }

        if (this.nanoHTTPDWebserver != null){
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Server already running"));
            return;
        }

        try {
            this.nanoHTTPDWebserver = new NanoHTTPDWebserver(port, this);
            this.nanoHTTPDWebserver.start();
        }catch (Exception e){
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            return;
        }

        Log.d(
                this.getClass().getName(),
                "Server is running on: " +
                        this.nanoHTTPDWebserver.getHostname() + ":" +
                        this.nanoHTTPDWebserver.getListeningPort()
        );
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }

    /**
     * Stops the server
     * @param args
     * @param callbackContext
     */
    private void stop(JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (this.nanoHTTPDWebserver != null) {
            this.nanoHTTPDWebserver.stop();
            this.nanoHTTPDWebserver = null;
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }

    /**
     * Will be called if the js context sends an response to the webserver
     * @param args {UUID: {...}}
     * @param callbackContext
     * @throws JSONException
     */
    private void sendResponse(JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(this.getClass().getName(), "Got sendResponse: " + args.toString());
        this.responses.put(args.getString(0), args.get(1));
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }

    /**
     * Just register the onRequest and send no result. This is needed to save the callbackContext to
     * invoke it later
     * @param args
     * @param callbackContext
     */
    private void onRequest(JSONArray args, CallbackContext callbackContext) {
        this.cbContext = callbackContext;
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.cbContext.sendPluginResult(pluginResult);
    }

}



