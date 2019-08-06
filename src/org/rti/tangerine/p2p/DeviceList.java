package org.rti.tangerine.p2p;

import android.app.ProgressDialog;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DeviceList implements WifiP2pManager.PeerListListener {

    private static final String TAG = "TangyP2PPlugin:DList";
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    View mContentView = null;
    private WifiP2pDevice device;

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
            JSONObject deviceInfo = new JSONObject();
            try {
                deviceInfo.put("deviceAddress", device.deviceAddress);
                deviceInfo.put("deviceName", device.deviceName);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            jsonArray.put(deviceInfo);
        }
    }
}
