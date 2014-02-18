package android.net.pppoe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothHeadset;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.DhcpInfoInternal;
import android.net.InterfaceConfiguration;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.RouteInfo;
import android.net.LinkAddress;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkStateTracker;
import android.net.NetworkUtils;
import android.net.NetworkInfo.DetailedState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.os.ServiceManager;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.util.*;

/**
 * Track the state of Pppoe connectivity. All event handling is done here,
 * and all changes in connectivity state are initiated here.
 *
 * @hide
 */
public class PppoeStateTracker implements NetworkStateTracker {

	private static final String TAG="PppoeStateTracker";
	private static final String PROP_PPP_ADDR = "dhcp.ppp0.ipaddress";
	private static final String PROP_PPP_MASK = "dhcp.ppp0.mask";
	private static final String PROP_PPP_DNS1 = "dhcp.ppp0.dns1";
	private static final String PROP_PPP_DNS2 = "dhcp.ppp0.dns2";
	private static final String PROP_PPP_GW = "dhcp.ppp0.gateway";

	private static final String PROP_VAL_PPP_NOERR = "0:0";
	private static final String PROP_NAME_PPP_ERRCODE = "net.ppp.errcode";
    
	public static final int EVENT_CONNECTED			= 3;
	public static final int EVENT_DISCONNECTED		= 4;
	public static final int EVENT_CONNECT_FAILED	= 5;
	public static final int EVENT_DISCONNECT_FAILED	= 6;

	private PppoeManager mEM;
	private boolean mServiceStarted;
	private boolean mInterfaceStopped;
	private boolean mPppInterfaceAdded = false;
	private String mInterfaceName = "ppp0";
    private DhcpInfoInternal mDhcpInfoInternal;
	private PppoeMonitor mMonitor;

	private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
	private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
	private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

	private LinkProperties mLinkProperties;
	private LinkCapabilities mLinkCapabilities;
	private NetworkInfo mNetworkInfo;

	private Handler mTarget;
	private Handler mTrackerTarget;
	private Context mContext;
    
	public PppoeStateTracker(int netType, String networkName) {
		Slog.i(TAG,"Starts ...");

		mNetworkInfo = new NetworkInfo(netType, 0, networkName, "");
		mNetworkInfo.setIsAvailable(false);
		setTeardownRequested(false);

        mLinkProperties = new LinkProperties();
		mLinkCapabilities = new LinkCapabilities();

		if (PppoeNative.initPppoeNative() != 0 ) {
			Slog.e(TAG,"Can not init pppoe device layers");
			return;
		}
		Slog.i(TAG,"Successed");
		
		mServiceStarted = true;

		mMonitor = new PppoeMonitor(this);
	}

	public boolean stopInterface(boolean suspend) {
		if (mEM != null) {
			PppoeDevInfo info = mEM.getSavedPppoeConfig();
			if (info != null && mEM.pppoeConfigured())
			{
				mInterfaceStopped = true;
				Slog.i(TAG, "stop interface");
				String ifname = info.getIfName();

				NetworkUtils.resetConnections(ifname, NetworkUtils.RESET_ALL_ADDRESSES);
				if (!suspend)
					NetworkUtils.disableInterface(ifname);
			}
		}

		return true;
	}


	private boolean configureInterfaceStatic(String ifname, DhcpInfoInternal dhcpInfoInternal) {
		IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
		INetworkManagementService netd = INetworkManagementService.Stub.asInterface(b);
		InterfaceConfiguration ifcg = new InterfaceConfiguration();
		ifcg.setLinkAddress(dhcpInfoInternal.makeLinkAddress());
		Log.v(TAG, "configureInterfaceStatic: ifname:" + ifname);
		try {
			netd.setInterfaceConfig(ifname, ifcg);
			mLinkProperties = dhcpInfoInternal.makeLinkProperties();
			mLinkProperties.setInterfaceName(ifname);
			Log.v(TAG, "IP configuration succeeded");
			return true;
		} catch (RemoteException re) {
			Log.v(TAG, "IP configuration failed: " + re);
			return false;
		} catch (IllegalStateException e) {
			Log.v(TAG, "IP configuration failed: " + e);
			return false;
		}
	}

	private boolean configureInterface(PppoeDevInfo info) throws UnknownHostException {
		mInterfaceStopped = false;

		mDhcpInfoInternal = new DhcpInfoInternal();
		mDhcpInfoInternal.ipAddress = info.getIpAddress();
		mDhcpInfoInternal.addRoute(new RouteInfo(NetworkUtils.numericToInetAddress(info.getRouteAddr())));

		InetAddress ia = NetworkUtils.numericToInetAddress(info.getNetMask());
		mDhcpInfoInternal.prefixLength = NetworkUtils.netmaskIntToPrefixLength(
											NetworkUtils.inetAddressToInt(ia));
		mDhcpInfoInternal.dns1 = info.getDnsAddr();

		configureInterfaceStatic(info.getIfName(), mDhcpInfoInternal);
		return true;
	}


	public boolean resetInterface()  throws UnknownHostException{
		/*
		 * This will guide us to enabled the enabled device
		 */
		Slog.i(TAG, ">>>resetInterface");
		if (mEM != null) {
			Slog.i(TAG, "pppoeConfigured: " + mEM.pppoeConfigured());
			PppoeDevInfo info = mEM.getSavedPppoeConfig();

			if (info != null && mEM.pppoeConfigured()) {
				Slog.i(TAG, "IfName:" + info.getIfName());
				Slog.i(TAG, "IP:" + info.getIpAddress());
				Slog.i(TAG, "Mask:" + info.getNetMask());
				Slog.i(TAG, "DNS:" + info.getDnsAddr());

				synchronized(this) {
					if(mInterfaceName != null) {
						Slog.i(TAG, "reset device " + mInterfaceName);
						NetworkUtils.resetConnections(mInterfaceName, NetworkUtils.RESET_ALL_ADDRESSES);
					}

					Slog.i(TAG, "Force the connection disconnected before configuration");
					setPppoeState( false, EVENT_DISCONNECTED, PppoeManager.PROP_VAL_PPP_NOERR);

					configureInterface(info);
				}
			}
		}
		return true;
	}


	@Override
	public String getTcpBufferSizesPropName() {
		// TODO Auto-generated method stub
		return "net.tcp.buffersize.default";
	}

	public void StartPolling() {
		Slog.i(TAG, "start monitoring");
		mMonitor.startMonitoring();
	}
	@Override
	public boolean isAvailable() {
		// Only say available if we have interfaces and user did not disable us.
		return ((mEM.getTotalInterface() != 0) && (mEM.getPppoeState() != PppoeManager.PPPOE_STATE_DISABLED));
	}

	@Override
	public boolean reconnect() {
		Slog.i(TAG, ">>>reconnect");
		try {
			if (mEM.getPppoeState() != PppoeManager.PPPOE_STATE_DISABLED ) {
				// maybe this is the first time we run, so set it to enabled
				mEM.setPppoeEnabled(true);
				if (!mEM.pppoeConfigured()) {
					mEM.pppoeSetDefaultConf();
				}
				return resetInterface();
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;

	}

	@Override
	public boolean setRadio(boolean turnOn) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void startMonitoring(Context context, Handler target) {
		Slog.i(TAG,"start to monitor the pppoe devices");
		if (mServiceStarted) {
			mContext = context;
			mEM = (PppoeManager)mContext.getSystemService(Context.PPPOE_SERVICE);
			mTarget = target;
			mTrackerTarget = new Handler(target.getLooper(), mTrackerHandlerCallback);
			if (mEM == null) {
				Slog.i(TAG,"failed to start startMonitoring");
				return;
			}
			
			int state = mEM.getPppoeState();
			if (state != mEM.PPPOE_STATE_DISABLED) {
				if (state == mEM.PPPOE_STATE_UNKNOWN){
					// maybe this is the first time we run, so set it to enabled
					mEM.setPppoeEnabled(true);
				} else {
					try {
						resetInterface();
					} catch (UnknownHostException e) {
						Slog.e(TAG, "Wrong pppoe configuration");
					}
				}
			}
		}
	}


	public boolean teardown() {
		return (mEM != null) ? stopInterface(false) : false;
	}

	public void captivePortalCheckComplete() {
        //TODO
    }
	
	private void postNotification(int event, String errcode) {
		final Intent intent = new Intent(PppoeManager.PPPOE_STATE_CHANGED_ACTION);
		intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
		intent.putExtra(PppoeManager.EXTRA_PPPOE_STATE, event);
		intent.putExtra(PppoeManager.EXTRA_PPPOE_ERRCODE, errcode);
		mContext.sendBroadcast(intent);

		Slog.d(TAG, "Send PPPOE_STATE_CHANGED_ACTION");
	}

	private void setPppoeState(boolean state, int event, String errcode) {
		Slog.d(TAG, "PST.setPppoeState()" + mNetworkInfo.isConnected() + " ==> " + state);
		if (event == EVENT_CONNECT_FAILED || mNetworkInfo.isConnected() != state) {
			mNetworkInfo.setIsAvailable(state);
			postNotification(event, errcode);
		}

		if (mNetworkInfo.isConnected() != state) {
			if (state) {
				mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
			} else {
				mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
				if( EVENT_DISCONNECTED == event ) {
					Slog.d(TAG, "EVENT_DISCONNECTED: StopInterface");
					stopInterface(true);
				}		
			}
            
			Message msg = mTarget.obtainMessage(EVENT_CONFIGURATION_CHANGED, mNetworkInfo);
			msg.sendToTarget();

			msg = mTarget.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
			msg.sendToTarget();
		}
	}
	
	public DhcpInfo getDhcpInfo() {
		return mDhcpInfoInternal.makeDhcpInfo();
	}

	private Handler.Callback mTrackerHandlerCallback = new Handler.Callback() {
		/** {@inheritDoc} */
		public boolean handleMessage(Message msg) {
			synchronized (this) {
				boolean newNetworkstate = false;
				PppoeDevInfo info = new PppoeDevInfo();

				switch (msg.what) {
				case EVENT_DISCONNECTED:
                    mPppInterfaceAdded = !mPppInterfaceAdded;
                    
                    if (mPppInterfaceAdded)
    					Slog.i(TAG, "[EVENT: PPP interface is ADDED]");
                    else
    					Slog.i(TAG, "[EVENT: PPP interface is REMOVED]");                    

					Slog.i(TAG, "clear PPP IP Config");
					SystemProperties.set(PROP_PPP_ADDR, "0.0.0.0");
					SystemProperties.set(PROP_PPP_MASK, "0.0.0.0");
					SystemProperties.set(PROP_PPP_DNS1, "0.0.0.0");
					SystemProperties.set(PROP_PPP_DNS2, "0.0.0.0");
					SystemProperties.set(PROP_PPP_GW, "0.0.0.0");
                    
					info.setIfName(mInterfaceName);
					info.setIpAddress("0.0.0.0");
					info.setNetMask("0.0.0.0");
					info.setDnsAddr("0.0.0.0");
					info.setRouteAddr("0.0.0.0");
					mEM.UpdatePppoeDevInfo(info);

					try {
						configureInterface(info);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
					newNetworkstate = false;

					String ppp_err = SystemProperties.get(PROP_NAME_PPP_ERRCODE, PppoeManager.PROP_VAL_PPP_NOERR);
					Slog.i(TAG, "ppp_err:" + ppp_err);
					if (ppp_err.equals(PROP_VAL_PPP_NOERR)) {
						setPppoeState(newNetworkstate, EVENT_DISCONNECTED, PppoeManager.PROP_VAL_PPP_NOERR);
					} else {
						setPppoeState(newNetworkstate, EVENT_CONNECT_FAILED, ppp_err);
					}
					break;

				case EVENT_CONNECTED:
					Slog.i(TAG, "[EVENT: PPP interface is READY]");
					newNetworkstate = true;

					int i=0;

					info.setIfName(mInterfaceName);
					String prop_val = null;
					do {
						prop_val = SystemProperties.get(PROP_PPP_ADDR, "0.0.0.0");
						info.setIpAddress(prop_val);
						Slog.i(TAG, "ip:" + prop_val);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException ex) {
							// Shut up!
						}
						i++;
					} while(info.getIpAddress().equals("0.0.0.0") && i<10);
                    

					prop_val = SystemProperties.get(PROP_PPP_MASK, "0.0.0.0");
					info.setNetMask(prop_val);
					Slog.i(TAG, "mask:" + prop_val);

					prop_val = SystemProperties.get(PROP_PPP_DNS1, "0.0.0.0");
					info.setDnsAddr(prop_val);
					Slog.i(TAG, "dns:" + prop_val);

					prop_val = SystemProperties.get(PROP_PPP_GW, "0.0.0.0");
					info.setRouteAddr(prop_val);
					Slog.i(TAG, "gw:" + prop_val);

					mEM.UpdatePppoeDevInfo(info);
					try {
						configureInterface(info);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
                    
					setPppoeState(newNetworkstate, EVENT_CONNECTED, PppoeManager.PROP_VAL_PPP_NOERR);
					break;
				}
			}
			return true;
		}
	};

	public void notifyPppConnected(String ifname) {
		Slog.i(TAG, "report interface is up for " + ifname);
		synchronized(this) {
			mTrackerTarget.sendEmptyMessage(EVENT_CONNECTED);
		}

	}
	public void notifyStateChange(String ifname,DetailedState state) {
		Slog.i(TAG, "report new state " + state.toString() + " on dev " + ifname);
		if (ifname.equals(mInterfaceName)) {
			Slog.i(TAG, "update network state tracker");
			synchronized(this) {
				mTrackerTarget.sendEmptyMessage(state.equals(DetailedState.CONNECTED)
					? EVENT_CONNECTED : EVENT_DISCONNECTED);
			}
		}
	}


	/**
	 * Fetch NetworkInfo for the network
	 */
	public NetworkInfo getNetworkInfo() {
		return new NetworkInfo(mNetworkInfo);
	}


	/**
	 * Fetch LinkProperties for the network
	 */
	public LinkProperties getLinkProperties() {
		return new LinkProperties(mLinkProperties);
	}

	/**
	 * A capability is an Integer/String pair, the capabilities
	 * are defined in the class LinkSocket#Key.
	 *
	 * @return a copy of this connections capabilities, may be empty but never null.
	 */
	public LinkCapabilities getLinkCapabilities() {
		return new LinkCapabilities(mLinkCapabilities);
	}


	public void setUserDataEnable(boolean enabled) {
		Slog.d(TAG, "ignoring setUserDataEnable(" + enabled + ")");
	}

	public void setPolicyDataEnable(boolean enabled) {
		Slog.d(TAG, "ignoring setPolicyDataEnable(" + enabled + ")");
	}


	/**
	 * Check if private DNS route is set for the network
	 */
	public boolean isPrivateDnsRouteSet() {
		Slog.v(TAG, "isPrivateDnsRouteSet");
		return mPrivateDnsRouteSet.get();
	}

	/**
	 * Set a flag indicating private DNS route is set
	 */
	public void privateDnsRouteSet(boolean enabled) {
		Slog.v(TAG, "privateDnsRouteSet");
		mPrivateDnsRouteSet.set(enabled);
	}


	/**
	 * Check if default route is set
	 */
	public boolean isDefaultRouteSet() {
		Slog.v(TAG, "isDefaultRouteSet");
		return mDefaultRouteSet.get();
	}


	/**
	 * Set a flag indicating default route is set for the network
	 */
	public void defaultRouteSet(boolean enabled) {
		Slog.v(TAG, "defaultRouteSet");
		mDefaultRouteSet.set(enabled);
	}

	public void setTeardownRequested(boolean isRequested) {
		Slog.v(TAG, "setTeardownRequested(" + isRequested + ")");
		mTeardownRequested.set(isRequested);
	}

	public boolean isTeardownRequested() {
		boolean flag = mTeardownRequested.get();
		Slog.v(TAG, "isTeardownRequested: " + flag);
		return flag;
	}

	public void setDependencyMet(boolean met) {
		// not supported on this network
	}
}
