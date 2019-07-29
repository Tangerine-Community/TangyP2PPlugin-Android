/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drulabs.localdash.wifidirect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;

//import org.drulabs.localdash.LocalDashWiFiDirect;
import org.apache.cordova.PluginResult;
import org.drulabs.localdash.transfer.DataSender;
import org.drulabs.localdash.utils.Utility;
import org.rti.tangerine.p2p.TangyP2PPlugin;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager manager;
    private Channel channel;
//    private LocalDashWiFiDirect activity;
    private TangyP2PPlugin activity;

    private static final String TAG = "WiFiDirectReceiver";

    /**
     * @param manager  WifiP2pManager system service
     * @param channel  Wifi p2p channel
     * @param activity activity associated with the receiver
     */
    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel,
                                       TangyP2PPlugin activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            // UI update to indicate wifi p2p status.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct mode is enabled
                activity.setIsWifiP2pEnabled(true);
                Log.i(TAG,"WIFI_P2P_STATE_ENABLED: true");
            } else {
                activity.setIsWifiP2pEnabled(false);
                Log.i(TAG,"WIFI_P2P_STATE_ENABLED: false");

            }
            Log.d(TAG, "P2P state changed - " + state);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null) {
                Log.d(TAG, "P2P peers changed, requestPeers");
                manager.requestPeers(channel, activity);
            }
            Log.d(TAG, "P2P peers changed");
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (manager == null) {
                return;
            }
            NetworkInfo networkInfo = intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            WifiP2pInfo p2pInfo = intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            String goAddress = "";
            if (p2pInfo != null && p2pInfo.groupOwnerAddress != null) {
                goAddress = Utility.getDottedDecimalIP(p2pInfo.groupOwnerAddress
                        .getAddress());
                boolean isGroupOwner = p2pInfo.isGroupOwner;
                Log.i(TAG,"WIFI_P2P_CONNECTION_CHANGED_ACTION: owner address" + goAddress + " isGroupOwner: " + isGroupOwner);
            }
            if (networkInfo.isConnected()) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                Log.i(TAG,"WIFI_P2P_CONNECTION_CHANGED_ACTION: isConnected: " + networkInfo.isConnected() + " to address: " + goAddress);
                String pluginMessage = "We connected to: " + goAddress;
//                Log.i(TAG, pluginMessage);
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                pluginResult.setKeepCallback(true);
                activity.cbContext.sendPluginResult(pluginResult);
                manager.requestConnectionInfo(channel, activity);
//                Uri imageUri = data.getData();
//                DataSender.sendFile(LocalDashWiFiDirect.this, selectedDevice.getIp(),
//                        selectedDevice.getPort(), imageUri);
            } else {
                Log.i(TAG,"WIFI_P2P_CONNECTION_CHANGED_ACTION: disconnected - isConnected" + networkInfo.isConnected());
                // It's a disconnect
                // activity.resetData();
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            //NOT needed for our use case
//            DeviceListFragment fragment = (DeviceListFragment) activity.getFragmentManager()
//                    .findFragmentById(R.id.frag_list);
//            WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(
//                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
//            fragment.updateThisDevice(device);
        }
    }
}
