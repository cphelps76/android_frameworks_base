/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.display;

import com.android.internal.util.DumpUtils;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplaySessionInfo;
import android.hardware.display.WifiDisplayStatus;
import android.media.RemoteDisplay;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;
import android.view.Surface;
import android.view.WindowManager;
import android.view.Display;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import libcore.util.Objects;

import android.util.Slog;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.FileObserver;
import android.os.FileUtils;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import android.os.UserHandle;

import android.os.PowerManager;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
/**
 * Manages all of the various asynchronous interactions with the {@link WifiP2pManager}
 * on behalf of {@link WifiDisplayAdapter}.
 * <p>
 * This code is isolated from {@link WifiDisplayAdapter} so that we can avoid
 * accidentally introducing any deadlocks due to the display manager calling
 * outside of itself while holding its lock.  It's also way easier to write this
 * asynchronous code if we can assume that it is single-threaded.
 * </p><p>
 * The controller must be instantiated on the handler thread.
 * </p>
 */
final class WifiDisplayController implements DumpUtils.Dump {
    private static final String TAG = "WifiDisplayController";
    private static final boolean DEBUG = false;

    private static final int DEFAULT_CONTROL_PORT = 7236;
    private static final int MAX_THROUGHPUT = 50;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int RTSP_TIMEOUT_SECONDS = 30;
    private static final int RTSP_TIMEOUT_SECONDS_CERT_MODE = 120;

    // We repeatedly issue calls to discover peers every so often for a few reasons.
    // 1. The initial request may fail and need to retried.
    // 2. Discovery will self-abort after any group is initiated, which may not necessarily
    //    be what we want to have happen.
    // 3. Discovery will self-timeout after 2 minutes, whereas we want discovery to
    //    be occur for as long as a client is requesting it be.
    // 4. We don't seem to get updated results for displays we've already found until
    //    we ask to discover again, particularly for the isSessionAvailable() property.
    private static final int DISCOVER_PEERS_INTERVAL_MILLIS = 10000;

    private static final int CONNECT_MAX_RETRIES = 3;
    private static final int CONNECT_RETRY_DELAY_MILLIS = 500;

    private final Context mContext;
    private final Handler mHandler;
    private final Listener mListener;

    private final WindowManager mWindowManager;
    private final WifiP2pManager mWifiP2pManager;
    private final Channel mWifiP2pChannel;
    private PowerManager.WakeLock mWakeLock;
    private boolean isLockedByMiracast;
    private boolean mWifiP2pEnabled;
    private boolean mWfdEnabled;
    private boolean mWfdEnabling;
    private NetworkInfo mNetworkInfo;

    private final ArrayList<WifiP2pDevice> mAvailableWifiDisplayPeers =
            new ArrayList<WifiP2pDevice>();

    // True if Wifi display is enabled by the user.
    private boolean mWifiDisplayOnSetting;

    // True if a scan was requested independent of whether one is actually in progress.
    private boolean mScanRequested;

    // True if there is a call to discoverPeers in progress.
    private boolean mDiscoverPeersInProgress;

    // The device to which we want to connect, or null if we want to be disconnected.
    private WifiP2pDevice mDesiredDevice;

    // The device to which we are currently connecting, or null if we have already connected
    // or are not trying to connect.
    private WifiP2pDevice mConnectingDevice;

    // The device from which we are currently disconnecting.
    private WifiP2pDevice mDisconnectingDevice;

    // The device to which we were previously trying to connect and are now canceling.
    private WifiP2pDevice mCancelingDevice;

    // The device to which we are currently connected, which means we have an active P2P group.
    private WifiP2pDevice mConnectedDevice;

    // The group info obtained after connecting.
    private WifiP2pGroup mConnectedDeviceGroupInfo;

    // Number of connection retries remaining.
    private int mConnectionRetriesLeft;

    // The remote display that is listening on the connection.
    // Created after the Wifi P2P network is connected.
    private RemoteDisplay mRemoteDisplay;

    // The remote display interface.
    private String mRemoteDisplayInterface;

    // True if RTSP has connected.
    private boolean mRemoteDisplayConnected;

    // The information we have most recently told WifiDisplayAdapter about.
    private WifiDisplay mAdvertisedDisplay;
    private Surface mAdvertisedDisplaySurface;
    private int mAdvertisedDisplayWidth;
    private int mAdvertisedDisplayHeight;
    private int mAdvertisedDisplayFlags;

	public static final String DNSMASQ_IP_ADDR_ACTION = "android.net.dnsmasq.IP_ADDR";
	public static final String DNSMASQ_MAC_EXTRA = "MAC_EXTRA";
	public static final String DNSMASQ_IP_EXTRA = "IP_EXTRA";
	public static final String DNSMASQ_PORT_EXTRA = "PORT_EXTRA";

	private WifiP2pInfo mP2pInfo;
	private FileObserver mAddrObserver = null;
	private File mFolder = new File("/data/misc/adb");
	private boolean mAutoStartWfd = false;
	private int mWfdPort;
	private int mRotation = -1;
	private int lastRotation = -1;
	private int hwrot = 0;
	private Display display;
	
	private void connectToRtspServer(String ip, int wfdPort) {

		String port = String.valueOf(wfdPort);
		sendDnsmasqBroadcast(new String("xx.xx.xx.xx.xx.xx"), ip, port);
	}
	
	private void parseDnsmasqAddr(String fileName) {
		File file = new File(fileName);
		BufferedReader reader = null;
		String mac = new String();
		String ip = new String();
		try {
			reader = new BufferedReader(new FileReader(file));
			mac = reader.readLine();
			ip = reader.readLine();
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (!mac.isEmpty() && !ip.isEmpty()) {
			connectToRtspServer(ip, mWfdPort);
		}
	}
	
    // Certification
    private boolean mWifiDisplayCertMode;
    private int mWifiDisplayWpsConfig = WpsInfo.INVALID;

    private WifiP2pDevice mThisDevice;

    public WifiDisplayController(Context context, Handler handler, Listener listener) {
        mContext = context;
        mHandler = handler;
        mListener = listener;
        isLockedByMiracast = false;
		
		mAddrObserver = new FileObserver(mFolder.getPath(), /*FileObserver.CLOSE_WRITE |*/ FileObserver.MODIFY) {
			public void onEvent(int event, String path) {
				File ipFile = new File(mFolder, path);
				String fullName = ipFile.getPath();
				Slog.d(TAG, "WFD : File changed : path=" + path + " event=" + event);
				if (mAutoStartWfd == true && path.equals(new String("dnsmasq.txt"))) {
					parseDnsmasqAddr(fullName);
				}
			}
		};
		mAddrObserver.startWatching();

        mWifiP2pManager = (WifiP2pManager)context.getSystemService(Context.WIFI_P2P_SERVICE);
        mWifiP2pChannel = mWifiP2pManager.initialize(context, handler.getLooper(), null);

        hwrot = SystemProperties.getInt("ro.sf.hwrotation", 0);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        display = mWindowManager.getDefaultDisplay();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(DNSMASQ_IP_ADDR_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        context.registerReceiver(mWifiP2pReceiver, intentFilter, null, mHandler);

        ContentObserver settingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateSettings();
            }
        };

        final ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.WIFI_DISPLAY_ON), false, settingsObserver);
        resolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.WIFI_DISPLAY_CERTIFICATION_ON), false, settingsObserver);
        resolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.WIFI_DISPLAY_WPS_CONFIG), false, settingsObserver);
        updateSettings();
    }

    private void updateSettings() {
        final ContentResolver resolver = mContext.getContentResolver();
        mWifiDisplayOnSetting = Settings.Global.getInt(resolver,
                Settings.Global.WIFI_DISPLAY_ON, 0) != 0;
        mWifiDisplayCertMode = Settings.Global.getInt(resolver,
                Settings.Global.WIFI_DISPLAY_CERTIFICATION_ON, 0) != 0;

        mWifiDisplayWpsConfig = WpsInfo.INVALID;
        if (mWifiDisplayCertMode) {
            mWifiDisplayWpsConfig = Settings.Global.getInt(resolver,
                  Settings.Global.WIFI_DISPLAY_WPS_CONFIG, WpsInfo.INVALID);
        }

        updateWfdEnableState();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("mWifiDisplayOnSetting=" + mWifiDisplayOnSetting);
        pw.println("mWifiP2pEnabled=" + mWifiP2pEnabled);
        pw.println("mWfdEnabled=" + mWfdEnabled);
        pw.println("mWfdEnabling=" + mWfdEnabling);
        pw.println("mNetworkInfo=" + mNetworkInfo);
        pw.println("mScanRequested=" + mScanRequested);
        pw.println("mDiscoverPeersInProgress=" + mDiscoverPeersInProgress);
        pw.println("mDesiredDevice=" + describeWifiP2pDevice(mDesiredDevice));
        pw.println("mConnectingDisplay=" + describeWifiP2pDevice(mConnectingDevice));
        pw.println("mDisconnectingDisplay=" + describeWifiP2pDevice(mDisconnectingDevice));
        pw.println("mCancelingDisplay=" + describeWifiP2pDevice(mCancelingDevice));
        pw.println("mConnectedDevice=" + describeWifiP2pDevice(mConnectedDevice));
        pw.println("mConnectionRetriesLeft=" + mConnectionRetriesLeft);
        pw.println("mRemoteDisplay=" + mRemoteDisplay);
        pw.println("mRemoteDisplayInterface=" + mRemoteDisplayInterface);
        pw.println("mRemoteDisplayConnected=" + mRemoteDisplayConnected);
        pw.println("mAdvertisedDisplay=" + mAdvertisedDisplay);
        pw.println("mAdvertisedDisplaySurface=" + mAdvertisedDisplaySurface);
        pw.println("mAdvertisedDisplayWidth=" + mAdvertisedDisplayWidth);
        pw.println("mAdvertisedDisplayHeight=" + mAdvertisedDisplayHeight);
        pw.println("mAdvertisedDisplayFlags=" + mAdvertisedDisplayFlags);

        pw.println("mAvailableWifiDisplayPeers: size=" + mAvailableWifiDisplayPeers.size());
        for (WifiP2pDevice device : mAvailableWifiDisplayPeers) {
            pw.println("  " + describeWifiP2pDevice(device));
        }
    }

    public void requestStartScan() {
        if (!mScanRequested) {
            mScanRequested = true;
            updateScanState();
        }
    }

    public void requestStopScan() {
        if (mScanRequested) {
            mScanRequested = false;
            updateScanState();
        }
    }

    public void requestConnect(String address) {
        for (WifiP2pDevice device : mAvailableWifiDisplayPeers) {
            if (device.deviceAddress.equals(address)) {
                connect(device);
            }
        }
    }

    public void requestPause() {
        if (mRemoteDisplay != null) {
            mRemoteDisplay.pause();
        }
    }

    public void requestResume() {
        if (mRemoteDisplay != null) {
            mRemoteDisplay.resume();
        }
    }

    public void requestDisconnect() {
        disconnect();
    }

    private void updateWfdEnableState() {
        if (mWifiDisplayOnSetting && mWifiP2pEnabled) {
            // WFD should be enabled.
            if (!mWfdEnabled && !mWfdEnabling) {
                mWfdEnabling = true;

                WifiP2pWfdInfo wfdInfo = new WifiP2pWfdInfo();
                wfdInfo.setWfdEnabled(true);
                wfdInfo.setDeviceType(WifiP2pWfdInfo.WFD_SOURCE);
                wfdInfo.setSessionAvailable(true);
                wfdInfo.setControlPort(DEFAULT_CONTROL_PORT);
                wfdInfo.setMaxThroughput(MAX_THROUGHPUT);
                mWifiP2pManager.setWFDInfo(mWifiP2pChannel, wfdInfo, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        if (DEBUG) {
                            Slog.d(TAG, "Successfully set WFD info.");
                        }
                        if (mWfdEnabling) {
                            mWfdEnabling = false;
                            mWfdEnabled = true;
                            reportFeatureState();
                            updateScanState();
                        }
                    }

                    @Override
                    public void onFailure(int reason) {
                        if (DEBUG) {
                            Slog.d(TAG, "Failed to set WFD info with reason " + reason + ".");
                        }
                        mWfdEnabling = false;
                    }
                });
            }
        } else {
            // WFD should be disabled.
            if (mWfdEnabled || mWfdEnabling) {
                WifiP2pWfdInfo wfdInfo = new WifiP2pWfdInfo();
                wfdInfo.setWfdEnabled(false);
                mWifiP2pManager.setWFDInfo(mWifiP2pChannel, wfdInfo, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        if (DEBUG) {
                            Slog.d(TAG, "Successfully set WFD info.");
                        }
                    }

                    @Override
                    public void onFailure(int reason) {
                        if (DEBUG) {
                            Slog.d(TAG, "Failed to set WFD info with reason " + reason + ".");
                        }
                    }
                });
            }
            mWfdEnabling = false;
            mWfdEnabled = false;
            reportFeatureState();
            updateScanState();
            disconnect();
        }
    }

    private void reportFeatureState() {
        final int featureState = computeFeatureState();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onFeatureStateChanged(featureState);
            }
        });
    }

    private int computeFeatureState() {
        if (!mWifiP2pEnabled) {
            return WifiDisplayStatus.FEATURE_STATE_DISABLED;
        }
        return mWifiDisplayOnSetting ? WifiDisplayStatus.FEATURE_STATE_ON :
                WifiDisplayStatus.FEATURE_STATE_OFF;
    }

    private void updateScanState() {
        if (mScanRequested && mWfdEnabled && mDesiredDevice == null) {
            if (!mDiscoverPeersInProgress) {
                Slog.i(TAG, "Starting Wifi display scan.");
                mDiscoverPeersInProgress = true;
                handleScanStarted();
                tryDiscoverPeers();
            }
        } else {
            if (mDiscoverPeersInProgress) {
                // Cancel automatic retry right away.
                mHandler.removeCallbacks(mDiscoverPeers);

                // Defer actually stopping discovery if we have a connection attempt in progress.
                // The wifi display connection attempt often fails if we are not in discovery
                // mode.  So we allow discovery to continue until we give up trying to connect.
                if (mDesiredDevice == null || mDesiredDevice == mConnectedDevice) {
                    Slog.i(TAG, "Stopping Wifi display scan.");
                    mDiscoverPeersInProgress = false;
                    stopPeerDiscovery();
                    handleScanFinished();
                }
            }
        }
    }

    private void tryDiscoverPeers() {
        mWifiP2pManager.discoverPeers(mWifiP2pChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                if (DEBUG) {
                    Slog.d(TAG, "Discover peers succeeded.  Requesting peers now.");
                }

                if (mDiscoverPeersInProgress) {
                    requestPeers();
                }
            }

            @Override
            public void onFailure(int reason) {
                if (DEBUG) {
                    Slog.d(TAG, "Discover peers failed with reason " + reason + ".");
                }

                // Ignore the error.
                // We will retry automatically in a little bit.
            }
        });

        // Retry discover peers periodically until stopped.
        mHandler.postDelayed(mDiscoverPeers, DISCOVER_PEERS_INTERVAL_MILLIS);
    }

    private void stopPeerDiscovery() {
        mWifiP2pManager.stopPeerDiscovery(mWifiP2pChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                if (DEBUG) {
                    Slog.d(TAG, "Stop peer discovery succeeded.");
                }
            }

            @Override
            public void onFailure(int reason) {
                if (DEBUG) {
                    Slog.d(TAG, "Stop peer discovery failed with reason " + reason + ".");
                }
            }
        });
    }

    private void requestPeers() {
        mWifiP2pManager.requestPeers(mWifiP2pChannel, new PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                if (DEBUG) {
                    Slog.d(TAG, "Received list of peers.");
                }

                mAvailableWifiDisplayPeers.clear();
                for (WifiP2pDevice device : peers.getDeviceList()) {
                    if (DEBUG) {
                        Slog.d(TAG, "  " + describeWifiP2pDevice(device));
                    }

                    if (isWifiDisplay(device)) {
                        mAvailableWifiDisplayPeers.add(device);
                    }
                }

                if (mDiscoverPeersInProgress) {
                    handleScanResults();
                }
            }
        });
    }

    private void handleScanStarted() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onScanStarted();
            }
        });
    }

    private void handleScanResults() {
        final int count = mAvailableWifiDisplayPeers.size();
        final WifiDisplay[] displays = WifiDisplay.CREATOR.newArray(count);
        for (int i = 0; i < count; i++) {
            WifiP2pDevice device = mAvailableWifiDisplayPeers.get(i);
            displays[i] = createWifiDisplay(device);
            updateDesiredDevice(device);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onScanResults(displays);
            }
        });
    }

    private void handleScanFinished() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onScanFinished();
            }
        });
    }

    private void updateDesiredDevice(WifiP2pDevice device) {
        // Handle the case where the device to which we are connecting or connected
        // may have been renamed or reported different properties in the latest scan.
        final String address = device.deviceAddress;
        if (mDesiredDevice != null && mDesiredDevice.deviceAddress.equals(address)) {
            if (DEBUG) {
                Slog.d(TAG, "updateDesiredDevice: new information "
                        + describeWifiP2pDevice(device));
            }
            mDesiredDevice.update(device);
            if (mAdvertisedDisplay != null
                    && mAdvertisedDisplay.getDeviceAddress().equals(address)) {
                readvertiseDisplay(createWifiDisplay(mDesiredDevice));
            }
        }
    }

    private void connect(final WifiP2pDevice device) {
        if (mDesiredDevice != null
                && !mDesiredDevice.deviceAddress.equals(device.deviceAddress)) {
            if (DEBUG) {
                Slog.d(TAG, "connect: nothing to do, already connecting to "
                        + describeWifiP2pDevice(device));
            }
            return;
        }

        if (mConnectedDevice != null
                && !mConnectedDevice.deviceAddress.equals(device.deviceAddress)
                && mDesiredDevice == null) {
            if (DEBUG) {
                Slog.d(TAG, "connect: nothing to do, already connected to "
                        + describeWifiP2pDevice(device) + " and not part way through "
                        + "connecting to a different device.");
            }
            return;
        }

        if (!mWfdEnabled) {
            Slog.i(TAG, "Ignoring request to connect to Wifi display because the "
                    +" feature is currently disabled: " + device.deviceName);
            return;
        }

        mDesiredDevice = device;
        mConnectionRetriesLeft = CONNECT_MAX_RETRIES;
        updateConnection();
    }

    private void disconnect() {
        mDesiredDevice = null;
        Slog.d(TAG,"disconnectv mWakeLock != null"+(mWakeLock != null)+" isLockedByMiracast?"+isLockedByMiracast);
        if(mWakeLock != null){
            mWakeLock.release();
            mWakeLock = null;
            if(isLockedByMiracast){
                setRotationLockForAccessibility(mContext,false);
                isLockedByMiracast = false;
            }
        }
        updateConnection();
    }

    private void retryConnection() {
        // Cheap hack.  Make a new instance of the device object so that we
        // can distinguish it from the previous connection attempt.
        // This will cause us to tear everything down before we try again.
        mDesiredDevice = new WifiP2pDevice(mDesiredDevice);
        updateConnection();
    }

    /**
     * This function is called repeatedly after each asynchronous operation
     * until all preconditions for the connection have been satisfied and the
     * connection is established (or not).
     */
    private void updateConnection() {
        // Step 0. Stop scans if necessary to prevent interference while connected.
        // Resume scans later when no longer attempting to connect.
        updateScanState();

        // Step 1. Before we try to connect to a new device, tell the system we
        // have disconnected from the old one.
        if (mRemoteDisplay != null && mConnectedDevice != mDesiredDevice) {
            Slog.i(TAG, "Stopped listening for RTSP connection on " + mRemoteDisplayInterface
                    + " from Wifi display: " + mConnectedDevice.deviceName);

            mRemoteDisplay.dispose();
            mRemoteDisplay = null;
            mRemoteDisplayInterface = null;
            mRemoteDisplayConnected = false;
            mHandler.removeCallbacks(mRtspTimeout);

            mWifiP2pManager.setMiracastMode(WifiP2pManager.MIRACAST_DISABLED);
            unadvertiseDisplay();

            // continue to next step
        }

        // Step 2. Before we try to connect to a new device, disconnect from the old one.
        if (mDisconnectingDevice != null) {
            return; // wait for asynchronous callback
        }
        if (mConnectedDevice != null && mConnectedDevice != mDesiredDevice) {
            Slog.i(TAG, "Disconnecting from Wifi display: " + mConnectedDevice.deviceName);
            mDisconnectingDevice = mConnectedDevice;
            mConnectedDevice = null;
            mConnectedDeviceGroupInfo = null;

            unadvertiseDisplay();

            final WifiP2pDevice oldDevice = mDisconnectingDevice;
            mWifiP2pManager.removeGroup(mWifiP2pChannel, new ActionListener() {
                @Override
                public void onSuccess() {
                    Slog.i(TAG, "Disconnected from Wifi display: " + oldDevice.deviceName);
                    next();
                }

                @Override
                public void onFailure(int reason) {
                    Slog.i(TAG, "Failed to disconnect from Wifi display: "
                            + oldDevice.deviceName + ", reason=" + reason);
                    next();
                }

                private void next() {
                    if (mDisconnectingDevice == oldDevice) {
                        mDisconnectingDevice = null;
                        updateConnection();
                    }
                }
            });
            return; // wait for asynchronous callback
        }

        // Step 3. Before we try to connect to a new device, stop trying to connect
        // to the old one.
        if (mCancelingDevice != null) {
            return; // wait for asynchronous callback
        }
        if (mConnectingDevice != null && mConnectingDevice != mDesiredDevice) {
            Slog.i(TAG, "Canceling connection to Wifi display: " + mConnectingDevice.deviceName);
            mCancelingDevice = mConnectingDevice;
            mConnectingDevice = null;

            unadvertiseDisplay();
            mHandler.removeCallbacks(mConnectionTimeout);

            final WifiP2pDevice oldDevice = mCancelingDevice;
            mWifiP2pManager.cancelConnect(mWifiP2pChannel, new ActionListener() {
                @Override
                public void onSuccess() {
                    Slog.i(TAG, "Canceled connection to Wifi display: " + oldDevice.deviceName);
                    next();
                }

                @Override
                public void onFailure(int reason) {
                    Slog.i(TAG, "Failed to cancel connection to Wifi display: "
                            + oldDevice.deviceName + ", reason=" + reason);
                    next();
                }

                private void next() {
                    if (mCancelingDevice == oldDevice) {
                        mCancelingDevice = null;
                        updateConnection();
                    }
                }
            });
            return; // wait for asynchronous callback
        }

        // Step 4. If we wanted to disconnect, or we're updating after starting an
        // autonomous GO, then mission accomplished.
        if (mDesiredDevice == null) {
            if (mWifiDisplayCertMode) {
                mListener.onDisplaySessionInfo(getSessionInfo(mConnectedDeviceGroupInfo, 0));
            }
            unadvertiseDisplay();
            return; // done
        }

        // Step 5. Try to connect.
        if (mConnectedDevice == null && mConnectingDevice == null) {
            Slog.i(TAG, "Connecting to Wifi display: " + mDesiredDevice.deviceName);

            mConnectingDevice = mDesiredDevice;
            WifiP2pConfig config = new WifiP2pConfig();
            WpsInfo wps = new WpsInfo();
            if (mWifiDisplayWpsConfig != WpsInfo.INVALID) {
                wps.setup = mWifiDisplayWpsConfig;
            } else if (mConnectingDevice.wpsPbcSupported()) {
                wps.setup = WpsInfo.PBC;
            } else if (mConnectingDevice.wpsDisplaySupported()) {
                // We do keypad if peer does display
                wps.setup = WpsInfo.KEYPAD;
            } else {
                wps.setup = WpsInfo.DISPLAY;
            }
            config.wps = wps;
            config.deviceAddress = mConnectingDevice.deviceAddress;
            // Helps with STA & P2P concurrency
            config.groupOwnerIntent = WifiP2pConfig.MIN_GROUP_OWNER_INTENT;

            WifiDisplay display = createWifiDisplay(mConnectingDevice);
            advertiseDisplay(display, null, 0, 0, 0);

            final WifiP2pDevice newDevice = mDesiredDevice;
            mWifiP2pManager.connect(mWifiP2pChannel, config, new ActionListener() {
                @Override
                public void onSuccess() {
                    // The connection may not yet be established.  We still need to wait
                    // for WIFI_P2P_CONNECTION_CHANGED_ACTION.  However, we might never
                    // get that broadcast, so we register a timeout.
                    Slog.i(TAG, "Initiated connection to Wifi display: " + newDevice.deviceName);
                    PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
                    if(mWakeLock != null){
                         mWakeLock.acquire();
                         Slog.d(TAG,"isRotationLocked?"+isRotationLocked(mContext));
                         if(!isRotationLocked(mContext)){
                            isLockedByMiracast = true;
                            setRotationLockForAccessibility(mContext,true);
                         }
                    }
                    mHandler.postDelayed(mConnectionTimeout, CONNECTION_TIMEOUT_SECONDS * 1000);
                }

                @Override
                public void onFailure(int reason) {
                    if (mConnectingDevice == newDevice) {
                        Slog.i(TAG, "Failed to initiate connection to Wifi display: "
                                + newDevice.deviceName + ", reason=" + reason);
                        mConnectingDevice = null;
                        handleConnectionFailure(false);
                    }
                }
            });
            return; // wait for asynchronous callback
        }

        // Step 6. Listen for incoming RTSP connection.
        if (mConnectedDevice != null && mRemoteDisplay == null) {
            Inet4Address addr = getInterfaceAddress(mConnectedDeviceGroupInfo);
            if (addr == null) {
                Slog.i(TAG, "Failed to get local interface address for communicating "
                        + "with Wifi display: " + mConnectedDevice.deviceName);
                handleConnectionFailure(false);
                return; // done
            }

            mWifiP2pManager.setMiracastMode(WifiP2pManager.MIRACAST_SOURCE);

            final WifiP2pDevice oldDevice = mConnectedDevice;
            final int port = getPortNumber(mConnectedDevice);
            final String iface = addr.getHostAddress() + ":" + port;
            mRemoteDisplayInterface = iface;

            Slog.i(TAG, "Listening for RTSP connection on " + iface
                    + " from Wifi display: " + mConnectedDevice.deviceName);

            mRemoteDisplay = RemoteDisplay.listen(iface, new RemoteDisplay.Listener() {
                @Override
                public void onDisplayConnected(Surface surface,
                        int width, int height, int flags, int session) {
                    if (mConnectedDevice == oldDevice && !mRemoteDisplayConnected) {
                        Slog.i(TAG, "Opened RTSP connection with Wifi display: "
                                + mConnectedDevice.deviceName);
                        mRemoteDisplayConnected = true;
                        mHandler.removeCallbacks(mRtspTimeout);

                        if (mWifiDisplayCertMode) {
                            mListener.onDisplaySessionInfo(
                                    getSessionInfo(mConnectedDeviceGroupInfo, session));
                        }

                        final WifiDisplay display = createWifiDisplay(mConnectedDevice);
                        advertiseDisplay(display, surface, width, height, flags);
                    }
                }

                @Override
                public void onDisplayDisconnected() {
                    if (mConnectedDevice == oldDevice) {
                        Slog.i(TAG, "Closed RTSP connection with Wifi display: "
                                + mConnectedDevice.deviceName);
                        mHandler.removeCallbacks(mRtspTimeout);
                        disconnect();
                    }
                }

                @Override
                public void onDisplayError(int error) {
                    if (mConnectedDevice == oldDevice) {
                        Slog.i(TAG, "Lost RTSP connection with Wifi display due to error "
                                + error + ": " + mConnectedDevice.deviceName);
                        mHandler.removeCallbacks(mRtspTimeout);
                        handleConnectionFailure(false);
                    }
                }
            }, mHandler);

            // Use extended timeout value for certification, as some tests require user inputs
            int rtspTimeout = mWifiDisplayCertMode ?
                    RTSP_TIMEOUT_SECONDS_CERT_MODE : RTSP_TIMEOUT_SECONDS;

            mHandler.postDelayed(mRtspTimeout, rtspTimeout * 1000);
        }
    }

    /**
    *Returns true if rotation lock is enabled.
    **/
    public static boolean isRotationLocked(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT) == 0;
    }

     public static void setRotationLockForAccessibility(Context context, final boolean enabled) {

         AsyncTask.execute(new Runnable() {
             @Override
             public void run() {
                try {
                    IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
                    if (enabled) {
                        wm.freezeRotation(-1);
                    } else {
                        wm.thawRotation();
                    }
                }catch (RemoteException exc) {
                    Slog.w(TAG, "Unable to save auto-rotate setting");
                }
             }
         });
     }
    private WifiDisplaySessionInfo getSessionInfo(WifiP2pGroup info, int session) {
        if (info == null) {
            return null;
        }
        Inet4Address addr = getInterfaceAddress(info);
        WifiDisplaySessionInfo sessionInfo = new WifiDisplaySessionInfo(
                !info.getOwner().deviceAddress.equals(mThisDevice.deviceAddress),
                session,
                info.getOwner().deviceAddress + " " + info.getNetworkName(),
                info.getPassphrase(),
                (addr != null) ? addr.getHostAddress() : "");
        if (DEBUG) {
            Slog.d(TAG, sessionInfo.toString());
        }
        return sessionInfo;
    }

    private void handleStateChanged(boolean enabled) {
        mWifiP2pEnabled = enabled;
        updateWfdEnableState();
    }

    private void handlePeersChanged() {
        // Even if wfd is disabled, it is best to get the latest set of peers to
        // keep in sync with the p2p framework
        requestPeers();
    }

    private void handleConnectionChanged(NetworkInfo networkInfo) {
        mNetworkInfo = networkInfo;
        if (mWfdEnabled && networkInfo.isConnected()) {
            if (mDesiredDevice != null || mWifiDisplayCertMode) {
                mWifiP2pManager.requestGroupInfo(mWifiP2pChannel, new GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup info) {
                        if (DEBUG) {
                            Slog.d(TAG, "Received group info: " + describeWifiP2pGroup(info));
                        }

                        if (mConnectingDevice != null && !info.contains(mConnectingDevice)) {
                            Slog.i(TAG, "Aborting connection to Wifi display because "
                                    + "the current P2P group does not contain the device "
                                    + "we expected to find: " + mConnectingDevice.deviceName
                                    + ", group info was: " + describeWifiP2pGroup(info));
                            handleConnectionFailure(false);
                            return;
                        }

                        if (mDesiredDevice != null && !info.contains(mDesiredDevice)) {
                            disconnect();
                            return;
                        }

                        if (mWifiDisplayCertMode) {
                            boolean owner = info.getOwner().deviceAddress
                                    .equals(mThisDevice.deviceAddress);
                            if (owner && info.getClientList().isEmpty()) {
                                // this is the case when we started Autonomous GO,
                                // and no client has connected, save group info
                                // and updateConnection()
                                mConnectingDevice = mDesiredDevice = null;
                                mConnectedDeviceGroupInfo = info;
                                updateConnection();
                            } else if (mConnectingDevice == null && mDesiredDevice == null) {
                                // this is the case when we received an incoming connection
                                // from the sink, update both mConnectingDevice and mDesiredDevice
                                // then proceed to updateConnection() below
                                mConnectingDevice = mDesiredDevice = owner ?
                                        info.getClientList().iterator().next() : info.getOwner();
                            }
                        }

                        if (mConnectingDevice != null && mConnectingDevice == mDesiredDevice) {
                            Slog.i(TAG, "Connected to Wifi display: "
                                    + mConnectingDevice.deviceName);

                            mHandler.removeCallbacks(mConnectionTimeout);
                            mConnectedDeviceGroupInfo = info;
                            mConnectedDevice = mConnectingDevice;
                            mConnectingDevice = null;
                            updateConnection();
                        }
                    }
                });
            }
			else {
                mWifiP2pManager.requestGroupInfo(mWifiP2pChannel, new GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup info) {
                        if (DEBUG) {
                            Slog.d(TAG, "Received group info: " + describeWifiP2pGroup(info));
                        }
						
						if (info.isGroupOwner() == true) {
							Slog.d(TAG, "WFD : I am GO");
							WifiP2pDevice device = null;
							for (WifiP2pDevice c : info.getClientList()) {
								device = c;
								break;
							}
							if (device != null && device.wfdInfo != null) {
								mWfdPort = device.wfdInfo.getControlPort();
								mAutoStartWfd = true;
							}
						}
						else {
							Slog.d(TAG, "WFD : I am GC");
							InetAddress addr = null;							
							WifiP2pDevice device = info.getOwner();
							if (device != null && device.wfdInfo != null && mP2pInfo != null) {
								mWfdPort = device.wfdInfo.getControlPort();
								addr = mP2pInfo.groupOwnerAddress;
								connectToRtspServer(addr.getHostAddress(), mWfdPort);
							}
						}
                    }
                });			
			}
        } else {
        	mAutoStartWfd = false;
            mConnectedDeviceGroupInfo = null;

            // Disconnect if we lost the network while connecting or connected to a display.
            if (mConnectingDevice != null || mConnectedDevice != null) {
                disconnect();
            }

            // After disconnection for a group, for some reason we have a tendency
            // to get a peer change notification with an empty list of peers.
            // Perform a fresh scan.
            if (mWfdEnabled) {
                requestPeers();
            }
        }
    }

    private final Runnable mDiscoverPeers = new Runnable() {
        @Override
        public void run() {
            tryDiscoverPeers();
        }
    };

    private final Runnable mConnectionTimeout = new Runnable() {
        @Override
        public void run() {
            if (mConnectingDevice != null && mConnectingDevice == mDesiredDevice) {
                Slog.i(TAG, "Timed out waiting for Wifi display connection after "
                        + CONNECTION_TIMEOUT_SECONDS + " seconds: "
                        + mConnectingDevice.deviceName);
                handleConnectionFailure(true);
            }
        }
    };

    private final Runnable mRtspTimeout = new Runnable() {
        @Override
        public void run() {
            if (mConnectedDevice != null
                    && mRemoteDisplay != null && !mRemoteDisplayConnected) {
                Slog.i(TAG, "Timed out waiting for Wifi display RTSP connection after "
                        + RTSP_TIMEOUT_SECONDS + " seconds: "
                        + mConnectedDevice.deviceName);
                handleConnectionFailure(true);
            }
        }
    };

    private void handleConnectionFailure(boolean timeoutOccurred) {
        Slog.i(TAG, "Wifi display connection failed!");

        if (mDesiredDevice != null) {
            if (mConnectionRetriesLeft > 0) {
                final WifiP2pDevice oldDevice = mDesiredDevice;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mDesiredDevice == oldDevice && mConnectionRetriesLeft > 0) {
                            mConnectionRetriesLeft -= 1;
                            Slog.i(TAG, "Retrying Wifi display connection.  Retries left: "
                                    + mConnectionRetriesLeft);
                            retryConnection();
                        }
                    }
                }, timeoutOccurred ? 0 : CONNECT_RETRY_DELAY_MILLIS);
            } else {
                disconnect();
            }
        }
    }

    private void advertiseDisplay(final WifiDisplay display,
            final Surface surface, final int width, final int height, final int flags) {
        if (!Objects.equal(mAdvertisedDisplay, display)
                || mAdvertisedDisplaySurface != surface
                || mAdvertisedDisplayWidth != width
                || mAdvertisedDisplayHeight != height
                || mAdvertisedDisplayFlags != flags) {
            final WifiDisplay oldDisplay = mAdvertisedDisplay;
            final Surface oldSurface = mAdvertisedDisplaySurface;

            mAdvertisedDisplay = display;
            mAdvertisedDisplaySurface = surface;
            mAdvertisedDisplayWidth = width;
            mAdvertisedDisplayHeight = height;
            mAdvertisedDisplayFlags = flags;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //if (oldSurface != null && surface != oldSurface) {
                    if (oldDisplay != null && mAdvertisedDisplay == null) {
                        mListener.onDisplayDisconnected();
                    } else if (oldDisplay != null && !oldDisplay.hasSameAddress(display)) {
                        mListener.onDisplayConnectionFailed();
                    }

                    if (display != null) {
                        if (!display.hasSameAddress(oldDisplay)) {
                            mListener.onDisplayConnecting(display);
                        } else if (!display.equals(oldDisplay)) {
                            // The address is the same but some other property such as the
                            // name must have changed.
                            mListener.onDisplayChanged(display);
                        }
                        if (width >0 && height >0) {
                            mListener.onDisplayConnected(display, surface, width, height, flags);
                        }
                    }
                }
            });
        }
    }

    private void unadvertiseDisplay() {
        advertiseDisplay(null, null, 0, 0, 0);
    }

    private void readvertiseDisplay(WifiDisplay display) {
        advertiseDisplay(display, mAdvertisedDisplaySurface,
                mAdvertisedDisplayWidth, mAdvertisedDisplayHeight,
                mAdvertisedDisplayFlags);
    }

    private static Inet4Address getInterfaceAddress(WifiP2pGroup info) {
        NetworkInterface iface;
        try {
            iface = NetworkInterface.getByName(info.getInterface());
        } catch (SocketException ex) {
            Slog.w(TAG, "Could not obtain address of network interface "
                    + info.getInterface(), ex);
            return null;
        }

        Enumeration<InetAddress> addrs = iface.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress addr = addrs.nextElement();
            if (addr instanceof Inet4Address) {
                return (Inet4Address)addr;
            }
        }

        Slog.w(TAG, "Could not obtain address of network interface "
                + info.getInterface() + " because it had no IPv4 addresses.");
        return null;
    }

    private static int getPortNumber(WifiP2pDevice device) {
        if (device.deviceName.startsWith("DIRECT-")
                && device.deviceName.endsWith("Broadcom")) {
            // These dongles ignore the port we broadcast in our WFD IE.
            return 8554;
        }
        return DEFAULT_CONTROL_PORT;
    }

    private static boolean isWifiDisplay(WifiP2pDevice device) {
        return device.wfdInfo != null
                && device.wfdInfo.isWfdEnabled()
                && isPrimarySinkDeviceType(device.wfdInfo.getDeviceType());
    }

    private static boolean isPrimarySinkDeviceType(int deviceType) {
        return deviceType == WifiP2pWfdInfo.PRIMARY_SINK
                || deviceType == WifiP2pWfdInfo.SOURCE_OR_PRIMARY_SINK;
    }

    private static String describeWifiP2pDevice(WifiP2pDevice device) {
        return device != null ? device.toString().replace('\n', ',') : "null";
    }

    private static String describeWifiP2pGroup(WifiP2pGroup group) {
        return group != null ? group.toString().replace('\n', ',') : "null";
    }

    private static WifiDisplay createWifiDisplay(WifiP2pDevice device) {
        return new WifiDisplay(device.deviceAddress, device.deviceName, null,
                true, device.wfdInfo.isSessionAvailable(), false);
    }

    private final BroadcastReceiver mWifiP2pReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                // This broadcast is sticky so we'll always get the initial Wifi P2P state
                // on startup.
                boolean enabled = (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED)) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                if (DEBUG) {
                    Slog.d(TAG, "Received WIFI_P2P_STATE_CHANGED_ACTION: enabled="
                            + enabled);
                }

                handleStateChanged(enabled);
            } else if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {
                if (DEBUG) {
                    Slog.d(TAG, "Received WIFI_P2P_PEERS_CHANGED_ACTION.");
                }

                handlePeersChanged();
            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo)intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);
				WifiP2pInfo p2pInfo = (WifiP2pInfo)intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (DEBUG) {
                    Slog.d(TAG, "Received WIFI_P2P_CONNECTION_CHANGED_ACTION: networkInfo="
                            + networkInfo + " p2pInfo=" + p2pInfo);
                }

				mP2pInfo = p2pInfo;
                handleConnectionChanged(networkInfo);
            } else if (action.equals(DNSMASQ_IP_ADDR_ACTION)) {
            	String mac = intent.getStringExtra(DNSMASQ_MAC_EXTRA);
				String ip = intent.getStringExtra(DNSMASQ_IP_EXTRA);
				String port = intent.getStringExtra(DNSMASQ_PORT_EXTRA);
				Slog.d(TAG, "Received DNSMASQ_IP_ADDR_ACTION : mac=" + mac + " ip=" + ip + " port=" + port);
            } else if (action.equals(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)) {
                mThisDevice = (WifiP2pDevice) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (DEBUG) {
                    Slog.d(TAG, "Received WIFI_P2P_THIS_DEVICE_CHANGED_ACTION: mThisDevice= "
                            + mThisDevice);
                }
            }else if(action.equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                WifiDisplayStatus mWifiDisplayStatus = (WifiDisplayStatus)intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                if(mWifiDisplayStatus.getActiveDisplayState() == WifiDisplayStatus.DISPLAY_STATE_CONNECTED){
                   mRotation = lastRotation = display.getRotation();
                   if(mRemoteDisplay != null && mRotation >= 0){
                        mRemoteDisplay.setRotation(computeWifiDisplayRotation(display.getWidth(),display.getHeight(),mRotation));
                   }
                }else {
                    mRotation = lastRotation = -1;
                }
            }else if(action.equals(Intent.ACTION_CONFIGURATION_CHANGED)){
                if (mConnectedDevice != null
                    && mRemoteDisplay != null && mRemoteDisplayConnected) {
                   mRotation = display.getRotation();
                   if(mRotation >= 0 && mRotation != lastRotation){
                       lastRotation = mRotation;
                       mRemoteDisplay.setRotation(computeWifiDisplayRotation(display.getWidth(),display.getHeight(),mRotation));
                    }    
                }    
            }
        }
    };
	public int computeWifiDisplayRotation(int width, int height, int rot){
	    if(width >= height){
		if(rot == 1 || rot == 3){
		    rot = rot +1;
		 }
	    }else{
		 if(rot == 2 || rot == 0){
		    rot = rot +1;
		 }
	    }
	    return (rot+ (hwrot/90))%4;
	}
	public void sendDnsmasqBroadcast(String mac, String ip, String port) {
		Slog.d(TAG, "Sending DNSMASQ_IP_ADDR_ACTION broadcast : mac=" + mac + " ip=" + ip + " port=" + port);
		Intent intent = new Intent(DNSMASQ_IP_ADDR_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
		intent.putExtra(DNSMASQ_MAC_EXTRA, mac);
		intent.putExtra(DNSMASQ_IP_EXTRA, ip);
		intent.putExtra(DNSMASQ_PORT_EXTRA, port);
		mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
	}

    /**
     * Called on the handler thread when displays are connected or disconnected.
     */
    public interface Listener {
        void onFeatureStateChanged(int featureState);

        void onScanStarted();
        void onScanResults(WifiDisplay[] availableDisplays);
        void onScanFinished();

        void onDisplayConnecting(WifiDisplay display);
        void onDisplayConnectionFailed();
        void onDisplayChanged(WifiDisplay display);
        void onDisplayConnected(WifiDisplay display,
                Surface surface, int width, int height, int flags);
        void onDisplaySessionInfo(WifiDisplaySessionInfo sessionInfo);
        void onDisplayDisconnected();
    }
}
