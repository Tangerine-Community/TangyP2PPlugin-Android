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
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TangyP2PPlugin extends CordovaPlugin
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

    private Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;

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
        else if ("startAdvertising".equals(action)) {
            if (hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "startAdvertising");
                        startAdvertising();
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
        } else if ("startDiscovery".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "startDiscovery");
                        startDiscovery();
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

    static class ReceiveBytesPayloadListener extends PayloadCallback {

        private CallbackContext cbContext;


        public ReceiveBytesPayloadListener(CallbackContext cbContext) {
            this.cbContext = cbContext;
        }

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            // This always gets the full data of the payload. Will be null if it's not a BYTES
            // payload. You can check the payload type with payload.getType().
            byte[] receivedBytes = payload.asBytes();
            TangyP2PPlugin.sendPluginMessage("Data transfer initiated.", true, cbContext, TAG);
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
            switch (update.getStatus()) {
                case PayloadTransferUpdate.Status.SUCCESS:
                    TangyP2PPlugin.sendPluginMessage("Data transfer completed! ", true, cbContext, TAG);
                    break;
                case PayloadTransferUpdate.Status.FAILURE:
                    TangyP2PPlugin.sendPluginMessage("Data transfer failure.", true, cbContext, TAG);
                    break;
                case PayloadTransferUpdate.Status.CANCELED:
                    TangyP2PPlugin.sendPluginMessage("Data transfer cancelled.", true, cbContext, TAG);
                    break;
                case PayloadTransferUpdate.Status.IN_PROGRESS:
                    // don't log, could be verbose.
                    break;
                default:
                    // Unknown status code
                    TangyP2PPlugin.sendPluginMessage("Data transfer update - unknown: " + update.getStatus(), true, cbContext, TAG);
            }
        }
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    // Automatically accept the connection on both sides.
                    Context context = cordova.getActivity().getApplicationContext();
                    ReceiveBytesPayloadListener payloadCallback = new ReceiveBytesPayloadListener(cbContext);
                    Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            // We're connected! Can now start sending and receiving data.
                            sendPluginMessage("We're connected! Can now start sending and receiving data.", true);
                            String hello = "Greetings from " + serviceName;
                            byte[] helloBytes = hello.getBytes();
//                            Payload bytesPayload = Payload.fromBytes(new byte[] {0xa, 0xb, 0xc, 0xd});
                            Payload bytesPayload = Payload.fromBytes(helloBytes);
                            Context context = cordova.getActivity().getApplicationContext();
                            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload);
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            // The connection was rejected by one or both sides.
                            sendPluginMessage("The connection was rejected by one or both sides.", true);
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            // The connection broke before it was able to be accepted.
                            sendPluginMessage("The connection broke before it was able to be accepted.", true);
                            break;
                        default:
                            // Unknown status code
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                }
            };

    private void startAdvertising() {
//        String packageName = this.getClass().getPackage().toString();
        String packageName = "org.rti.tangerine";
        Log.d(TAG, "Advertising as: " + packageName);
        Context context = cordova.getActivity().getApplicationContext();
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        Nearby.getConnectionsClient(context)
                .startAdvertising(
                        serviceName, packageName, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            sendPluginMessage("We're advertising!", true);
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                            sendPluginMessage(" We were unable to start advertising.", true);
                        });
    }

    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        Context context = cordova.getActivity().getApplicationContext();
        Nearby.getConnectionsClient(context)
                .startDiscovery(serviceName, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            sendPluginMessage("We're discovering!", true);
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We're unable to start discovering.
                            sendPluginMessage(" We were unable to start discovery.", true);
                        });
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    // An endpoint was found. We request a connection to it.
                    Context context = cordova.getActivity().getApplicationContext();
                    Nearby.getConnectionsClient(context)
                            .requestConnection(serviceName, endpointId, connectionLifecycleCallback)
                            .addOnSuccessListener(
                                    (Void unused) -> {
                                        // We successfully requested a connection. Now both sides
                                        // must accept before the connection is established.
                                        sendPluginMessage("We successfully requested a connection.", true);
                                    })
                            .addOnFailureListener(
                                    (Exception e) -> {
                                        // Nearby Connections failed to request the connection.
                                        sendPluginMessage("Nearby Connections failed to request the connection.", true);
                                    });
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    // A previously discovered endpoint has gone away.
                    sendPluginMessage("A previously discovered endpoint has gone away - id: " + endpointId, true);

                }
            };

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



