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
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

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
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class TangyP2PPlugin extends CordovaPlugin
{


    public static final String TAG = "TangyP2PPlugin";

    public CallbackContext cbContext;

    public static final String PERMISSION_TO_WIFI = Manifest.permission.CHANGE_WIFI_STATE;
    private static final String PERMISSION_DENIED_ERROR = "Permission denied";
    String [] permissions = { PERMISSION_TO_WIFI };

    public PluginResult pluginResult;

    public HashMap<String, Object> responses;

    private Endpoint endpoint = null;

    /**
     * The connection strategy we'll use for Nearby Connections. In this case, we've decided on
     * P2P_STAR, which is a combination of Bluetooth Classic and WiFi Hotspots.
     */
    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    /**
     * This service id lets us find other nearby devices that are interested in the same thing. Our
     * sample does exactly one thing, so we hardcode the ID.
     */
    private static final String SERVICE_ID = "org.rti.tangerine.SERVICE_ID";

    /**
     * The state of the app. As the app changes states, the UI will update and advertising/discovery
     * will start/stop.
     */
    private State mState = State.UNKNOWN;

    /** A random UID used as this device's endpoint name. */
    private String mName;

    /** Our handler to Nearby Connections. */
    private ConnectionsClient mConnectionsClient;

    /** The devices we've discovered near us. */
    private final Map<String, Endpoint> mDiscoveredEndpoints = new HashMap<>();

    /**
     * The devices we have pending connections to. They will stay pending until we call {@link
     * #acceptConnection(Endpoint)} or {@link #rejectConnection(Endpoint)}.
     */
    private final Map<String, Endpoint> mPendingConnections = new HashMap<>();

    /**
     * The devices we are currently connected to. For advertisers, this may be large. For discoverers,
     * there will only be one entry in this map.
     */
    private final Map<String, Endpoint> mEstablishedConnections = new HashMap<>();

    /**
     * True if we are asking a discovered device to connect to us. While we ask, we cannot ask another
     * device.
     */
    private boolean mIsConnecting = false;

    /** True if we are discovering. */
    private boolean mIsDiscovering = false;

    /** True if we are advertising. */
    private boolean mIsAdvertising = false;

    /**
     * Sets the context of the Command.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        Log.d(TAG, "Plugin is initializing.");
        super.initialize(cordova, webView);
        this.cbContext = null;
        this.responses = new HashMap<String, Object>();
        mName = generateRandomName();
        Context context = cordova.getActivity().getApplicationContext();
        mConnectionsClient = Nearby.getConnectionsClient(context);
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        cbContext = callbackContext;
        if(action.equals("getPermission"))
        {
            LOG.d(TAG, "Checking permissions.");
            if(hasPermisssion())
            {
//                PluginResult r = new PluginResult(PluginResult.Status.OK);
//                cbContext.sendPluginResult(r);
                sendPluginMessage(PluginResult.Status.OK.toString(), true, "log", null);
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
//                        startAdvertising();
                        setState(State.SEARCHING);
                    }
                });
                return true;
            } else {
                String message = "Requesting permissions";
//                Log.i(TAG, message);
                PermissionHelper.requestPermissions(this, 0, permissions);
                sendPluginMessage(message, true, "log", null);
            }
            return true;
        }
        else if ("listenForTransfer".equals(action)) {
            if (hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "listenForTransfer");
                        setState(State.SEARCHING);
                    }
                });
                return true;
            } else {
                String message = "Requesting permissions";
//                Log.i(TAG, message);
                PermissionHelper.requestPermissions(this, 0, permissions);
                sendPluginMessage(message, true, "log", null);
            }
            return true;
        } else if ("startDiscovery".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "startDiscovery");
                        connectToEndpoint(endpoint);
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
            }
            return true;
        } else if ("transferData".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "transferData");
                        String payloadString = "";
                        try {
                            payloadString = args.getString(0);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        sendPluginMessage("sending payloadString beginning with: " + payloadString.subSequence(0,30), true, "log", null);
                        byte[] payloadBytes = payloadString.getBytes();
                        Payload bytesPayload = Payload.fromBytes(payloadBytes);
                        send(bytesPayload);
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

    /**
     * Sends a message to the PluginResult and debug log.
     * @param pluginMessage
     * @param keepCallback
     * @param messageType
     * @param object
     */
    private void sendPluginMessage(String pluginMessage, boolean keepCallback, String messageType, JSONObject object) {
        Log.d(TAG, pluginMessage);
//        Message messageObject = new Message(messageType,pluginMessage, object);
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("messageType", messageType);
            jsonObject.put("message", pluginMessage);
            jsonObject.put("object", object);
        } catch (JSONException e) {
            Log.e(TAG, "sendPluginMessage Error: " + e.getMessage());
            e.printStackTrace();
        }
        String jsonString = jsonObject.toString();
        Log.d(TAG, "jsonString: "  + jsonString);
        pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
        pluginResult.setKeepCallback(keepCallback);
        cbContext.sendPluginResult(pluginResult);
    }

    /**
     * Sends a message to the PluginResult and debug log.
     * @param pluginMessage
     * @param keepCallback
     * @param messageType
     * @param object
     */
    public static void sendPluginMessage(String pluginMessage, boolean keepCallback, CallbackContext cbContext, String tag, String messageType, JSONObject object) {
        if (tag == null) {
            tag = TAG;
        }
        Log.d(tag, pluginMessage);
//        Message messageObject = new Message(messageType,pluginMessage, object);
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("messageType", messageType);
            jsonObject.put("message", pluginMessage);
            jsonObject.put("object", object);
        } catch (JSONException e) {
            Log.e(tag, "sendPluginMessage Error: " + e.getMessage());
            e.printStackTrace();
        }
        String jsonString = jsonObject.toString();
        Log.d(tag, "jsonString: "  + jsonString);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
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
                LOG.d(TAG, "hasPermisssion() is false for: " + p + " but we will let this pass for now. TODO fix.");
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

    /** Callbacks for connections to other devices. */
    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    logD(
                            String.format(
                                    "mConnectionLifecycleCallback onConnectionInitiated(endpointId=%s, endpointName=%s)",
                                    endpointId, connectionInfo.getEndpointName()));
                    Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
                    mPendingConnections.put(endpointId, endpoint);
                    TangyP2PPlugin.this.onConnectionInitiated(endpoint, connectionInfo);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    logD(String.format("onConnectionResponse(endpointId=%s, result=%s)", endpointId, result));

                    // We're no longer connecting
                    mIsConnecting = false;

                    if (!result.getStatus().isSuccess()) {
                        logW(
                                String.format(
                                        "Connection failed. Received status %s.",
                                        TangyP2PPlugin.toString(result.getStatus())));
                        onConnectionFailed(mPendingConnections.remove(endpointId));
                        return;
                    }
                    connectedToEndpoint(mPendingConnections.remove(endpointId));
                }

                @Override
                public void onDisconnected(String endpointId) {
                    if (!mEstablishedConnections.containsKey(endpointId)) {
                        logW("Unexpected disconnection from endpoint " + endpointId);
                        return;
                    }
                    disconnectedFromEndpoint(mEstablishedConnections.get(endpointId));
                }
            };

    /** Callbacks for payloads (bytes of data) sent from another device to us. */
    private final PayloadCallback mPayloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
//                    logD(String.format("onPayloadReceived from (endpointId=%s)", endpointId));
                    String pluginMessage = String.format("onPayloadReceived from (endpointId=%s)", endpointId);
                    Log.d(TAG, pluginMessage);
                    onReceive(mEstablishedConnections.get(endpointId), payload);
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    logD(
                            String.format(
                                    "onPayloadTransferUpdate(endpointId=%s, update=%s)", endpointId, update));
                    //            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
            switch (update.getStatus()) {
                case PayloadTransferUpdate.Status.SUCCESS:
                    TangyP2PPlugin.sendPluginMessage("Data transfer completed! ", true, cbContext, TAG, "log", null);
                    break;
                case PayloadTransferUpdate.Status.FAILURE:
                    TangyP2PPlugin.sendPluginMessage("Data transfer failure.", true, cbContext, TAG, "log", null);
                    break;
                case PayloadTransferUpdate.Status.CANCELED:
                    TangyP2PPlugin.sendPluginMessage("Data transfer cancelled.", true, cbContext, TAG, "log", null);
                    break;
                case PayloadTransferUpdate.Status.IN_PROGRESS:
                    // don't log, could be verbose.
                    break;
                default:
                    // Unknown status code
                    TangyP2PPlugin.sendPluginMessage("Data transfer update - unknown: " + update.getStatus(), true, cbContext, TAG, "log", null);
            }
                }
            };

    /**
     * The state has changed. I wonder what we'll be doing now.
     *
     * @param state The new state.
     */
    private void setState(State state) {
        if (mState == state) {
            logW("State set to " + state + " but already in that state");
            return;
        }

        logD("State set to " + state);
        State oldState = mState;
        mState = state;
        onStateChanged(oldState, state);
    }

    /** @return The current state. */
    private State getState() {
        return mState;
    }

    /**
     * State has changed.
     *
     * @param oldState The previous state we were in. Clean up anything related to this state.
     * @param newState The new state we're now in. Prepare the UI for this state.
     */
    private void onStateChanged(State oldState, State newState) {
//        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
//            mCurrentAnimator.cancel();
//        }

        // Update Nearby Connections to the new state.
        switch (newState) {
            case SEARCHING:
                disconnectFromAllEndpoints();
                startDiscovering();
                startAdvertising();
                break;
            case CONNECTED:
                stopDiscovering();
                stopAdvertising();
                break;
            case UNKNOWN:
                stopAllEndpoints();
                break;
            default:
                // no-op
                break;
        }

        // Update the UI.
//        switch (oldState) {
//            case UNKNOWN:
//                // Unknown is our initial state. Whatever state we move to,
//                // we're transitioning forwards.
//                transitionForward(oldState, newState);
//                break;
//            case SEARCHING:
//                switch (newState) {
//                    case UNKNOWN:
//                        transitionBackward(oldState, newState);
//                        break;
//                    case CONNECTED:
//                        transitionForward(oldState, newState);
//                        break;
//                    default:
//                        // no-op
//                        break;
//                }
//                break;
//            case CONNECTED:
//                // Connected is our final state. Whatever new state we move to,
//                // we're transitioning backwards.
//                transitionBackward(oldState, newState);
//                break;
//        }
    }

    /** Represents a device we can talk to. */
    protected static class Endpoint {
        @NonNull
        private final String id;
        @NonNull private final String name;

        private Endpoint(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Endpoint) {
                Endpoint other = (Endpoint) obj;
                return id.equals(other.id);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return String.format("Endpoint{id=%s, name=%s}", id, name);
        }
    }

    /**
     * Queries the phone's contacts for their own profile, and returns their name. Used when
     * connecting to another device.
     */
    protected String getName() {
        return mName;
    }

    /** {@see ConnectionsActivity#getServiceId()} */
    public String getServiceId() {
        return SERVICE_ID;
    }

    /** {@see ConnectionsActivity#getStrategy()} */
    public Strategy getStrategy() {
        return STRATEGY;
    }

    /**
     * Transforms a {@link Status} into a English-readable message for logging.
     *
     * @param status The current status
     * @return A readable String. eg. [404]File not found.
     */
    private static String toString(Status status) {
        return String.format(
                Locale.US,
                "[%d]%s",
                status.getStatusCode(),
                status.getStatusMessage() != null
                        ? status.getStatusMessage()
                        : ConnectionsStatusCodes.getStatusCodeString(status.getStatusCode()));
    }

    protected void logV(String msg) {
        sendPluginMessage(msg, true, "log", null);
    }

    protected void logD(String msg) {
        sendPluginMessage(msg, true, "log", null);
    }

    protected void logW(String msg) {
        sendPluginMessage(msg, true, "log", null);
    }

    protected void logW(String msg, Throwable e) {
        e.printStackTrace();
        String error = e.getMessage();
        sendPluginMessage(msg + " error: " + error, true, "log", null);
    }

    protected void logE(String msg, Throwable e) {
        sendPluginMessage(msg, true, "log", null);
    }

    private static String generateRandomName() {
        String name = "";
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            name += random.nextInt(10);
        }
        return name;
    }

    /** States that the UI goes through. */
    public enum State {
        UNKNOWN,
        SEARCHING,
        CONNECTED
    }

    /**
     * Sets the device to advertising mode. It will broadcast to other devices in discovery mode.
     * Either {@link #onAdvertisingStarted()} or {@link #onAdvertisingFailed()} will be called once
     * we've found out if we successfully entered this mode.
     */
    protected void startAdvertising() {
        mIsAdvertising = true;
        final String localEndpointName = getName();

        AdvertisingOptions.Builder advertisingOptions = new AdvertisingOptions.Builder();
        advertisingOptions.setStrategy(getStrategy());

        mConnectionsClient
                .startAdvertising(
                        localEndpointName,
                        getServiceId(),
                        mConnectionLifecycleCallback,
                        advertisingOptions.build())
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                logV("Now advertising endpoint " + localEndpointName);
                                onAdvertisingStarted();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mIsAdvertising = false;
                                logW("startAdvertising() failed.", e);
                                onAdvertisingFailed();
                            }
                        });
    }

    /** Stops advertising. */
    protected void stopAdvertising() {
        mIsAdvertising = false;
        mConnectionsClient.stopAdvertising();
    }

    /** Returns {@code true} if currently advertising. */
    protected boolean isAdvertising() {
        return mIsAdvertising;
    }

    /** Called when advertising successfully starts. Override this method to act on the event. */
    protected void onAdvertisingStarted() {}

    /** Called when advertising fails to start. Override this method to act on the event. */
    protected void onAdvertisingFailed() {}

    /**
     * Called when a pending connection with a remote endpoint is created. Use {@link ConnectionInfo}
     * for metadata about the connection (like incoming vs outgoing, or the authentication token). If
     * we want to continue with the connection, call {@link #acceptConnection(Endpoint)}. Otherwise,
     * call {@link #rejectConnection(Endpoint)}.
     */
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        // We accept the connection immediately.
        logD(
                String.format(
                        "onConnectionInitiated(endpoint=%s, isIncomingConnection=%s, endpointName=%s)",
                        endpoint, connectionInfo.isIncomingConnection(), connectionInfo.getEndpointName()));
        acceptConnection(endpoint);
    }

    /** Accepts a connection request. */
    protected void acceptConnection(final Endpoint endpoint) {
        mConnectionsClient
                .acceptConnection(endpoint.getId(), mPayloadCallback)
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                logW("acceptConnection() failed.", e);
                            }
                        });
    }

    /** Rejects a connection request. */
    protected void rejectConnection(Endpoint endpoint) {
        mConnectionsClient
                .rejectConnection(endpoint.getId())
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                logW("rejectConnection() failed.", e);
                            }
                        });
    }

    /**
     * Sets the device to discovery mode. It will now listen for devices in advertising mode. Either
     * {@link #onDiscoveryStarted()} or {@link #onDiscoveryFailed()} will be called once we've found
     * out if we successfully entered this mode.
     */
    protected void startDiscovering() {
        mIsDiscovering = true;
        mDiscoveredEndpoints.clear();
        DiscoveryOptions.Builder discoveryOptions = new DiscoveryOptions.Builder();
        discoveryOptions.setStrategy(getStrategy());
        mConnectionsClient
                .startDiscovery(
                        getServiceId(),
                        new EndpointDiscoveryCallback() {
                            @Override
                            public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                                logD(
                                        String.format(
                                                "onEndpointFound(endpointId=%s, serviceId=%s, endpointName=%s)",
                                                endpointId, info.getServiceId(), info.getEndpointName()));

                                if (getServiceId().equals(info.getServiceId())) {
                                    Endpoint endpoint = new Endpoint(endpointId, info.getEndpointName());
                                    mDiscoveredEndpoints.put(endpointId, endpoint);
                                    onEndpointDiscovered(endpoint);
                                }
                            }

                            @Override
                            public void onEndpointLost(String endpointId) {
                                logD(String.format("onEndpointLost(endpointId=%s)", endpointId));
                            }
                        },
                        discoveryOptions.build())
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                onDiscoveryStarted();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mIsDiscovering = false;
                                logW("startDiscovering() failed.", e);
                                onDiscoveryFailed();
                            }
                        });
    }

    /** Stops discovery. */
    protected void stopDiscovering() {
        mIsDiscovering = false;
        mConnectionsClient.stopDiscovery();
    }

    /** Returns {@code true} if currently discovering. */
    protected boolean isDiscovering() {
        return mIsDiscovering;
    }

    /** Called when discovery successfully starts. Override this method to act on the event. */
    protected void onDiscoveryStarted() {}

    /** Called when discovery fails to start. Override this method to act on the event. */
    protected void onDiscoveryFailed() {}

    /**
     * Called when a remote endpoint is discovered. To connect to the device, call {@link
     * #connectToEndpoint(Endpoint)}.
     */
    protected void onEndpointDiscovered(Endpoint endpoint) {
        // We found an advertiser!
        stopDiscovering();
        this.endpoint = endpoint;
//        connectToEndpoint(endpoint);
    }

    /** Disconnects from the given endpoint. */
    protected void disconnect(Endpoint endpoint) {
        mConnectionsClient.disconnectFromEndpoint(endpoint.getId());
        mEstablishedConnections.remove(endpoint.getId());
    }

    /** Disconnects from all currently connected endpoints. */
    protected void disconnectFromAllEndpoints() {
        for (Endpoint endpoint : mEstablishedConnections.values()) {
            mConnectionsClient.disconnectFromEndpoint(endpoint.getId());
        }
        mEstablishedConnections.clear();
    }

    /** Resets and clears all state in Nearby Connections. */
    protected void stopAllEndpoints() {
        mConnectionsClient.stopAllEndpoints();
        mIsAdvertising = false;
        mIsDiscovering = false;
        mIsConnecting = false;
        mDiscoveredEndpoints.clear();
        mPendingConnections.clear();
        mEstablishedConnections.clear();
    }

    /**
     * Sends a connection request to the endpoint. Either {@link #onConnectionInitiated(Endpoint,
     * ConnectionInfo)} or {@link #onConnectionFailed(Endpoint)} will be called once we've found out
     * if we successfully reached the device.
     */
    protected void connectToEndpoint(final Endpoint endpoint) {
        logV("Sending a connection request to endpoint " + endpoint);
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true;

        // Ask to connect
        mConnectionsClient
                .requestConnection(getName(), endpoint.getId(), mConnectionLifecycleCallback)
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                logW("requestConnection() failed.", e);
                                mIsConnecting = false;
                                onConnectionFailed(endpoint);
                            }
                        });
    }

    /** Returns {@code true} if we're currently attempting to connect to another device. */
    protected final boolean isConnecting() {
        return mIsConnecting;
    }

    private void connectedToEndpoint(Endpoint endpoint) {
        logD(String.format("connectedToEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.put(endpoint.getId(), endpoint);
        onEndpointConnected(endpoint);
    }

    private void disconnectedFromEndpoint(Endpoint endpoint) {
        logD(String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.remove(endpoint.getId());
        onEndpointDisconnected(endpoint);
    }

    /** Called when someone has connected to us. Override this method to act on the event. */
    protected void onEndpointConnected(Endpoint endpoint) {
        logD(String.format("onEndpointConnected(endpoint=%s)", endpoint));
        setState(State.CONNECTED);
    }

    /** Called when someone has disconnected. Override this method to act on the event. */
    protected void onEndpointDisconnected(Endpoint endpoint) {
        logD(String.format("onEndpointDisconnected(endpoint=%s)", endpoint));
        setState(State.SEARCHING);
    }

    /**
     * Called when a connection with this endpoint has failed. Override this method to act on the
     * event.
     */
    protected void onConnectionFailed(Endpoint endpoint) {
        // Let's try someone else.
        if (getState() == State.SEARCHING) {
            startDiscovering();
        }
    }

    /** Returns a list of currently connected endpoints. */
    protected Set<Endpoint> getDiscoveredEndpoints() {
        return new HashSet<>(mDiscoveredEndpoints.values());
    }

    /** Returns a list of currently connected endpoints. */
    protected Set<Endpoint> getConnectedEndpoints() {
        return new HashSet<>(mEstablishedConnections.values());
    }

    /**
     * Sends a {@link Payload} to all currently connected endpoints.
     *
     * @param payload The data you want to send.
     */
    protected void send(Payload payload) {
        send(payload, mEstablishedConnections.keySet());
    }

    private void send(Payload payload, Set<String> endpoints) {
//        int count = endpoints.size();
//        Log.d(TAG, "Send: Number of endpoints: " + count);
//        Iterator<String> itr = endpoints.iterator();
//        while(itr.hasNext()){
//            String endPoint = itr.next();
//            Log.d(TAG, "Sending to endpoint: " + endPoint);
//        }
        mConnectionsClient
                .sendPayload(new ArrayList<>(endpoints), payload)
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                logW("sendPayload() failed.", e);
                            }
                        })
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                sendPluginMessage("Payload was sent!", true, "log", null);
                            }
                        });

    }

    /**
     * Someone connected to us has sent us data. Override this method to act on the event.
     *
     * @param endpoint The sender.
     * @param payload The data.
     */
    protected void onReceive(Endpoint endpoint, Payload payload) {
        // This always gets the full data of the payload. Will be null if it's not a BYTES
//            // payload. You can check the payload type with payload.getType().
            byte[] receivedBytes = payload.asBytes();
            String message = new String(receivedBytes);
        try {
            JSONObject object = new JSONObject(message);
            TangyP2PPlugin.sendPluginMessage(message, true, cbContext, TAG, "payload", object);
        } catch (JSONException e) {
            TangyP2PPlugin.sendPluginMessage(e.getMessage(), true, cbContext, TAG, "log", null);

        }
    }

}



