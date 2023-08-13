package com.car.control.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class NetworkListener extends BroadcastReceiver {

	private static final String TAG = "CarSvc_NetworkListener";
	private static final String MULTICAST_ADDR = "224.0.1.1";
	private static final int MULTICAST_PORT = 8127;
	static final int VOICE_TCP_PORT = 8128;
	
	private static final int NETWORK_CHECK = 100;
	private static final int SERVER_CHECK = 101;
    private static final int SNOOP_SERVER = 102;
	private static int SERVER_CHECK_TIME = 1000 * 30;
	private static final String MSG_HAND_SHAKE = "carservice";
	private static final String MSG_CLIENT_SNOOP = "snoop";

	private Context mContext;
	private Object mLock = new Object();
	private static MyRunnable mMyRunnable;
	private MulticastSocket mReceiveSocket;
	private ServerFoundCallBack mServerFoundCallback;
	private ArrayList<ServerInfo> mServerList = new ArrayList<NetworkListener.ServerInfo>();
	boolean mIsInited = false;
	
	private InetAddress mBroadcastAddr;
	private InetAddress mMulticastAddr;
	private MulticastSocket mSendSocket;
	
	public final static class ServerInfo {
		public String name = "";
		public String ipAddr = "";
		public String serialNo = "";
		public int cloudID = -1;
		public int port;
		public int receiveCount = 0;
		public boolean supportWebsocket = false;
		public boolean newSetting = false;
		public boolean headless = false;
		public boolean oversea = false;
		public String toString(){
			String str = "";
			if(!serialNo.equals(""))
				str = serialNo.substring(0, 4);
			return name + ":" + str + " " + ipAddr;
		}
	}
	
	public interface ServerFoundCallBack {
		abstract void serverNotify(ArrayList<ServerInfo> list, boolean change);
	}
		
	Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			if (msg.what == NETWORK_CHECK) {
				removeMessages(NETWORK_CHECK);
				if(listeningReady()) {
					
				} else {
					if (isWifiNetworkConnected() || isWifiApEnabled()) {
						sendEmptyMessageDelayed(NETWORK_CHECK, 1000);
					}
				}
				sendEmptyMessage(SNOOP_SERVER);
			}else if(msg.what == SERVER_CHECK){
				Log.i(TAG,"SERVER_CHECK");
				synchronized (mServerList) {
					boolean change = false;
					for (Iterator<ServerInfo> iter = mServerList.iterator();iter.hasNext();) {
	        			ServerInfo si = iter.next();
	        			if(si.receiveCount == 0){
	        				iter.remove();
	        				change = true;
	        			}else
	        				si.receiveCount = 0;
					}
					mServerFoundCallback.serverNotify(mServerList, change);
				}
				removeMessages(NETWORK_CHECK);
				sendEmptyMessageDelayed(SERVER_CHECK, SERVER_CHECK_TIME);
			}else if(msg.what == SNOOP_SERVER) {
			    removeMessages(SNOOP_SERVER);
			    synchronized (mLock) {
                    new Thread(new Runnable() {
        				@Override
        				public void run() {
        					snoopServer();
        				}
        			}).start();

                    if(mMyRunnable != null) {
                        sendEmptyMessageDelayed(SNOOP_SERVER, 10000);
                    }
                }
            }
		}
	};
	
	public ArrayList<ServerInfo> getServerList() {
		return mServerList;
	}

	boolean createSocket() {
		try {		
	        Object[] ret = Util.getBroadcast();
	        if( ret == null) {
		        Log.d(TAG, "Can not find broadcast address");
		        mBroadcastAddr = InetAddress.getByName("255.255.255.255");
		        return false;
	        }
	        else {
	        	mBroadcastAddr = (InetAddress) ret[0];
	        }
	        
	        mMulticastAddr = InetAddress.getByName(MULTICAST_ADDR);
	        
	        Log.d(TAG, "Bind to " + mBroadcastAddr);        
	        
			mSendSocket = new MulticastSocket();
			mSendSocket.setTimeToLive(4);
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		return true;
	}	
	
	protected void closeSocket() {
		synchronized (this) {
			if (mSendSocket != null) {
				try {
					try {
						if (mSendSocket instanceof MulticastSocket) {
							((MulticastSocket) mSendSocket).leaveGroup(mMulticastAddr);
						}
					} catch (SocketException exception) {
					}
					mSendSocket.close();
				} catch (Exception exception) {
				}
				mSendSocket = null;
			}
		}
	}
	
	void snoopServer() {
		createSocket();
		String info = MSG_CLIENT_SNOOP;
		byte[] content = info.getBytes();
		final DatagramPacket packet = new DatagramPacket(content, content.length, mMulticastAddr, MULTICAST_PORT);
		final DatagramPacket packet2 = new DatagramPacket(content, content.length, mBroadcastAddr, MULTICAST_PORT);
		try {
			if (mSendSocket != null) {
				mSendSocket.send(packet);
				mSendSocket.send(packet2);
			}
		} catch (IOException e) {
			e.printStackTrace();
			if (mSendSocket != null) {
				try {
					mSendSocket.send(packet2);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		closeSocket();
	}

	public class MyRunnable implements Runnable {

		boolean mStop = false;
		MyRunnable mMe;

		public MyRunnable() {
			mMe = this;
		}

		public void stop2Exit() {
			mStop = true;
			if (mReceiveSocket != null) {
				mReceiveSocket.close();
				mReceiveSocket = null;
			}
		}

		public boolean isRunning() {
			return !mStop;
		}

		@Override
		public void run() {
			mStop = false;
			WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
			WifiManager.MulticastLock lock= manager.createMulticastLock("MyRunnable");			
			while (!mStop) {
				try {
					if (mReceiveSocket == null) break;
			        byte[] buf = new byte[1024];
			        if (!lock.isHeld())
			        	lock.acquire();
			        DatagramPacket dp = new DatagramPacket(buf,buf.length);
			        mReceiveSocket.receive(dp);
			        String str = new String(dp.getData(), 0, dp.getLength());
			        InetAddress address = dp.getAddress();
			        Log.d(TAG, "Get message: " + str + " from " + address.getHostAddress());	
			        if (str.startsWith(MSG_HAND_SHAKE)) {
			        	
			        	String msg = str.substring(MSG_HAND_SHAKE.length() + 1);
			        	String args[] = msg.split("::");
			        	if(args.length <= 1)
			        		continue;
			        	String name = args[0];
			        	String serialNo = args[1];
			        	boolean supprotWebsocket = false;
			        	if(args.length >= 3 && args[2].equals("websocket"))
			        		supprotWebsocket = true;
			        	boolean newSetting = false;
			        	boolean headless = false;
						boolean oversea = false;
			        	if(args.length >= 4 && args[3].equals("newsetting"))
			        		newSetting = true;
			        	if(args.length >= 5 && args[4].equals("headless"))
			        		headless = true;
						if(args.length >= 6 && args[5].equals("oversea"))
							oversea = true;
			        	
			        	synchronized (mServerList) {													
				        	if (mServerList.size() <= 0) {
				        		ServerInfo si = new ServerInfo();
				        		si.ipAddr = address.getHostAddress();
				        		si.name = name;
				        		si.serialNo = serialNo;
				        		si.port = VOICE_TCP_PORT;
				        		si.receiveCount++;
				        		si.supportWebsocket = supprotWebsocket;
				        		si.newSetting = newSetting;
				        		si.headless = headless;
								si.oversea = oversea;
				        		mServerList.add(si);
				        		mServerFoundCallback.serverNotify(mServerList, true);
				        	} else {
				        		boolean found = false;
				        		for (Iterator<ServerInfo> iter = mServerList.iterator();iter.hasNext();) {
				        			ServerInfo si = iter.next();
				        			if (si.serialNo.equals(serialNo)) {
				        				if(si.ipAddr.equals(address.getHostAddress()) && 
				        						si.name.equals(name)){
				        					si.receiveCount++;
				        					found = true;
				        					
				        				}else{
				        					mServerList.remove(si);
				        				}		
				        				break;
				        			}
				        		}
				        		if (found) {
				        			mServerFoundCallback.serverNotify(mServerList, false);
				        		} else {
					        		ServerInfo si = new ServerInfo();
					        		si.ipAddr = address.getHostAddress();
					        		si.name = name;
					        		si.serialNo = serialNo;
					        		si.port = VOICE_TCP_PORT;
					        		si.receiveCount++;
					        		si.supportWebsocket = supprotWebsocket;
					        		si.newSetting = newSetting;
					        		si.headless = headless;
									si.oversea = oversea;
					        		mServerList.add(si);	
					        		mServerFoundCallback.serverNotify(mServerList, true);
				        		}
				        	}
			        	}
			        }
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(lock.isHeld())
					lock.release();
				
				if (mMe != mMyRunnable)
					break;
			}
			mStop = true;
			synchronized (mServerList) {
				mServerList.clear();
				mServerFoundCallback.serverNotify(mServerList, false);
			}
		}
	};
		
	boolean listeningReady() {
		try {
			mReceiveSocket = new MulticastSocket(MULTICAST_PORT);
			InetAddress multicastAddr = InetAddress.getByName(MULTICAST_ADDR);
			//mReceiveSocket.setSoTimeout(15000);
			mReceiveSocket.joinGroup(multicastAddr);
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		
		synchronized (mLock) {
			mMyRunnable = new MyRunnable();
			new Thread(mMyRunnable).start();
		}		
	
		return true;
	}
	
	void stopReceiving() {
		synchronized (mLock) {
			if (mMyRunnable != null) {
				mMyRunnable.stop2Exit();
				mMyRunnable = null;
			}
		}		
	}

	public void init(Context ctx, ServerFoundCallBack cb) {
		mIsInited = true;
		mContext = ctx;
		mServerFoundCallback = cb;
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(WifiHideAPI.WIFI_AP_STATE_CHANGED_ACTION);
		Intent intent = mContext.registerReceiver(this, filter);
		if (intent != null) checkIntent(intent);
		mHandler.removeMessages(SERVER_CHECK);
		mHandler.sendEmptyMessageDelayed(SERVER_CHECK, SERVER_CHECK_TIME);
	}
	
	public void deinit(){
		if (!mIsInited) return;
		mContext.unregisterReceiver(this);
		mHandler.removeMessages(NETWORK_CHECK);
		mHandler.removeMessages(SERVER_CHECK);
		stopReceiving();
		mIsInited = false;
		mContext = null;
	}
	
	boolean isWifiApEnabled() {
    	WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

    	try {
        	Method wmMethod = wifi.getClass().getMethod("isWifiApEnabled");
            return (Boolean)wmMethod.invoke(wifi);
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        } catch (IllegalAccessException e){
            e.printStackTrace();
        } catch (InvocationTargetException e){
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
			e.printStackTrace();
		}		
    	return false;
	}
	
	public int getWifiApState() {
    	WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

    	try {
        	Method wmMethod = wifi.getClass().getMethod("getWifiApState");
            return (Integer)wmMethod.invoke(wifi);
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        } catch (IllegalAccessException e){
            e.printStackTrace();
        } catch (InvocationTargetException e){
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
			e.printStackTrace();
		}		
    	return WifiHideAPI.WIFI_AP_STATE_FAILED;
	}
	
	private int mPreWifiState = 0; 
	public void setWifiApEnable(boolean enabled) {	
		
    	WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    
    	int wifiState = wifi.getWifiState();
        if (enabled && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
        	wifi.setWifiEnabled(false);
        	mPreWifiState = 1;
        }
        
        try {
        	Method wmMethod = wifi.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
	        wmMethod.invoke(wifi, null, enabled);
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        } catch (IllegalAccessException e){
            e.printStackTrace();
        } catch (InvocationTargetException e){
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
        
        if (!enabled) {
            int wifiSavedState = mPreWifiState;
            if (wifiSavedState == 1) {
            	wifi.setWifiEnabled(true);
            	mPreWifiState = 0;
            }
        }
    }


	boolean isWifiNetworkConnected() {
		ConnectivityManager connectivity = (ConnectivityManager) mContext
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity != null) {
			NetworkInfo info = connectivity.getActiveNetworkInfo();
			if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.getState() == NetworkInfo.State.CONNECTED) {
				return true;
			}
		}
		return false;
	}
	
	void checkIntent(Intent intent) {
		if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)
			|| intent.getAction().equals(WifiHideAPI.WIFI_AP_STATE_CHANGED_ACTION)) {
			
			Log.d(TAG, "checkIntent() " + intent.getAction());
			
			if (isWifiNetworkConnected() || isWifiApEnabled()) {
				if (mMyRunnable == null || !mMyRunnable.isRunning()) {
					mHandler.removeMessages(NETWORK_CHECK);
					mHandler.sendEmptyMessage(NETWORK_CHECK);
				}
			} else {
				mHandler.removeMessages(NETWORK_CHECK);
				stopReceiving();
			}
		}		
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		checkIntent(intent);
	}

}
