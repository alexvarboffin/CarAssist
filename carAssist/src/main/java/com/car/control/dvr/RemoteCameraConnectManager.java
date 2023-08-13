
package com.car.control.dvr;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.car.common.map.MipcaActivityCapture;
import com.car.control.CarAssistMainView;
import com.car.control.CarWebSocketClient;
import com.car.control.CarWebSocketClient.CarWebSocketClientCallback;
import com.car.control.Config;
import com.car.control.R;
import com.car.control.browser.FileInfo;
import com.car.control.browser.FileScanner;
import com.car.control.browser.RemoteFileActivity;
import com.car.control.util.DownloadTask;
import com.car.control.util.HttpDownloadManager;
import com.car.control.util.HttpRequestManager;
import com.car.control.util.NetworkListener;
import com.car.control.util.NetworkListener.ServerInfo;

import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class RemoteCameraConnectManager implements CarWebSocketClientCallback {
	
	private static final String TAG = "CarSvc_RemoteCameraConnectManager";
	
	public static final String KEY_PRESERVER_SERIALNO = "key_preserver_serialno";
	
	public static String HTTP_SERVER_IP = "";
	public static String HTTP_SERVER_PORT = "8080";
	public static String WEBSOCK_SERVER_PORT = "8081";
	
	private static final int STATUS_CONNECT = 1;
	private static final int STATUS_DISCONNECT = 2;
	private static final int STATUS_CONNECTTING = 3;
	
	private int mConntectStatus = STATUS_DISCONNECT;
	private static RemoteCameraConnectManager sIns;
	
	public static void create(Context ctx, CameraPreviewView view){
		sIns =  new RemoteCameraConnectManager(ctx, view);
	}
	
	public static RemoteCameraConnectManager instance(){
		return sIns;
	}
	
	public static void destory(){
		if (sIns != null && sIns.mNetworkListener != null)
			sIns.mNetworkListener.deinit();
		CarWebSocketClient.destory();
	}

	public static boolean isOversea(){
		if(mCurrentServerInfo != null && mCurrentServerInfo.oversea)
			return true;
		return false;
	}

	public static boolean isHeadless(){
		if(mCurrentServerInfo != null && mCurrentServerInfo.headless)
			return true;
		return false;
	}		
	
	public static boolean supportNewSetting(){
		if(mCurrentServerInfo != null && mCurrentServerInfo.newSetting)
			return true;
		return false;
	}	
	
	public static boolean supportWebsocket(){
		if(mCurrentServerInfo != null && mCurrentServerInfo.supportWebsocket)
			return true;
		return false;
	}
	
	public static NetworkListener.ServerInfo getCurrentServerInfo(){
		return mCurrentServerInfo;
	}
	
	private Context mContext;
	private CameraPreviewView mCameraPreviewView;
	private ListView mServerListView;
	private List<NetworkListener.ServerInfo> mServerList;
	private ServerListAdapter mServerListAdapter;
	private Dialog mServerDialog;
	private NetworkListener.ServerInfo mNoServer;
	private static NetworkListener.ServerInfo mCurrentServerInfo;
	private String mPreserverSerialNO = "";
	private NetworkListener mNetworkListener;
	private static final int PRESERVER_SERVER_WAIT_COUNT = 10;
	private int mSumOfServerChecked = 0;
	private List<FileInfo> mLockFileList = new ArrayList<FileInfo>();
	private List<FileInfo> mLoopFileList = new ArrayList<FileInfo>();
	private List<FileInfo> mCaptureFileList = new ArrayList<FileInfo>();
	private List<FileInfo> mDownloadingFileList = new ArrayList<FileInfo>();
	private List<OnRemoteFileListChange> mRemoteFileListeners = 
			new ArrayList<OnRemoteFileListChange>();
	private String mVersion;
	
	boolean mJustBindRequest = false;
	private boolean mSplashViewDismissed = false;

	public void setSplashViewDismissed(){
		mSplashViewDismissed = true;
	}
	
	public void showServerDialog(boolean justBindRequest){
		mJustBindRequest = justBindRequest;
		mServerDialog.show();
	}	
	
	public void showServerDialog(){
		mJustBindRequest = false;
		showServerDialog(false);
	}

	public NetworkListener getNetworkListener(){
		return mNetworkListener;
	}

	public void release() {
		mContext = null;
		sIns = null;
	}

	public void setAutoConnectSerial(String sn){
		mPreserverSerialNO = sn;
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
		SharedPreferences.Editor ed = sp.edit();
		ed.putString(KEY_PRESERVER_SERIALNO, mPreserverSerialNO);
		ed.commit();
		mHandler.sendEmptyMessage(MSG_DISCONNECT_SERVER);
		mNetworkListener.deinit();
		mNetworkListener.init(mContext.getApplicationContext(), mServerFoundCallBack);
	}
	
	private RemoteCameraConnectManager(Context ctx, CameraPreviewView view){
		mContext = ctx;
		mCameraPreviewView = view;
		
		mNetworkListener = new NetworkListener();
		mNetworkListener.init(mContext.getApplicationContext(), mServerFoundCallBack);
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
		mPreserverSerialNO = sp.getString(KEY_PRESERVER_SERIALNO, "");
		
		mServerListView = new ListView(mContext.getApplicationContext());
		mServerList = new ArrayList<NetworkListener.ServerInfo>();
		mNoServer = new ServerInfo();
		mNoServer.ipAddr = mContext.getString(R.string.tip_no_searched_device);
		mNoServer.serialNo = mContext.getString(R.string.no_recorder);
		mNoServer.name = mContext.getString(R.string.no_recorder);
		mServerList.add(mNoServer);
		mServerListAdapter = new ServerListAdapter(mServerList, mContext.getApplicationContext());
		mServerListView.setAdapter(mServerListAdapter);
		mServerListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				String ip = mServerList.get(arg2).ipAddr;
				String name = mServerList.get(arg2).name;

				boolean supportWebsocket = mServerList.get(arg2).supportWebsocket;
				Log.i(TAG,"ip = " + ip);
				if(!name.equals(mContext.getString(R.string.no_recorder))){
					connectServer(ip, supportWebsocket);
				}else{
					mCurrentServerInfo = null;
				}
				mServerDialog.dismiss();
			}
			
		});
		//mServerListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setTitle(R.string.ip_setting);
		builder.setView(mServerListView);
		builder.setPositiveButton(R.string.scan_recorder, new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(mContext, MipcaActivityCapture.class);
				intent.putExtra(MipcaActivityCapture.SHOW_SCAN_RECORDER_TIP, true);
				Activity a = (Activity)mContext;
				a.startActivityForResult(intent, CarAssistMainView.SCANNIN_GREQUEST_CODE);
			}
			
		});
		mServerDialog = builder.create();
	}
	
	private void connectServer(String ip, boolean supprotWebsocket){
		setConnectStatus(STATUS_CONNECTTING);
		mHandler.removeMessages(MSG_CONNECT_TIMEOUT);
		mHandler.sendEmptyMessageDelayed(MSG_CONNECT_TIMEOUT, 10000);
		if(supprotWebsocket){
			final String uri = "ws://" + ip + ":" + WEBSOCK_SERVER_PORT;
			Log.i(TAG, "uri = " + uri);
			
			try {
				CarWebSocketClient.create(new URI(uri));
				CarWebSocketClient.instance().registerCallback(this);
				CarWebSocketClient.instance().connect();
			} catch (Exception e) {
				Log.i(TAG,"Exception:" + e);
			}
		}else{
			String url = "http://" + ip + ":" + HTTP_SERVER_PORT + 
					"/cgi-bin/Config.cgi?action=get&property=CarDvr.Status.*";
			Log.i(TAG,"url = " + url);
			HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){
	
				@Override
				public void onHttpResponse(String result) {
					Log.i(TAG, "result = " + result);
					if(result == null)
						return;
					String params[] = result.split("\n");
					for(String str : params){
						try{
							if(str.startsWith(Config.PROPERTY_CARDVR_STATUS_SERIALNO)){
								String serialNo = str.split("=")[1];
								Message msg = mHandler.obtainMessage(MSG_CONNECT_SERVER, serialNo);
								mHandler.removeMessages(MSG_CONNECT_SERVER);
								mHandler.sendMessage(msg);
							}
						}catch(Exception e){
							Log.i(TAG,"Exception",e);
						}
					}
				}
				
			});
		}
	}
	
	private static final int MSG_UPDATE_SERVER_LIST = 1;
	private static final int MSG_CONNECT_SERVER = 2;
	private static final int MSG_DISCONNECT_SERVER = 3;
	private static final int MSG_CONNECT_TIMEOUT = 4;
	private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
				case MSG_UPDATE_SERVER_LIST:
					Log.i(TAG, "MSG_UPDATE_SERVER_LIST");
					@SuppressWarnings("unchecked")
					ArrayList<NetworkListener.ServerInfo> list = (ArrayList<NetworkListener.ServerInfo>)msg.obj;
					mServerList.clear();
					mServerList.addAll(list);
					if(mCurrentServerInfo != null){
						NetworkListener.ServerInfo info;
						int i = 0;
						for(i= 0;i < mServerList.size(); i++){
							info = mServerList.get(i);
							if(info.serialNo.equals(mCurrentServerInfo.serialNo)){
								mServerListAdapter.setCurrentSelect(info.serialNo);
								HTTP_SERVER_IP = info.ipAddr;
								//setConnectStatus(STATUS_CONNECT);
								mHandler.removeMessages(MSG_CONNECT_TIMEOUT);
								break;
							}
						}
						if(i >= mServerList.size()){
							mServerListAdapter.setCurrentSelect("0000");
							mCurrentServerInfo = null;
							setConnectStatus(STATUS_DISCONNECT);
						}
						mSumOfServerChecked = 0;
						
					}else if(!mPreserverSerialNO.equals("")){
						mServerListAdapter.setCurrentSelect("0000");
						mCurrentServerInfo = null;
						setConnectStatus(STATUS_DISCONNECT);
						boolean match = false;
						for(NetworkListener.ServerInfo info : mServerList){
							if(mPreserverSerialNO.equals(info.serialNo)){
								match = true;
								connectServer(info.ipAddr, info.supportWebsocket);
								break;
							}
							
						}
						if(!match && mServerList.size() != 0 && mCameraPreviewView.getActivate() && mSplashViewDismissed){
						    if(mSumOfServerChecked > PRESERVER_SERVER_WAIT_COUNT){
						        mServerDialog.show();
						        mSumOfServerChecked = 0;
						    }
						    else
						        mSumOfServerChecked++;
						}
					}else{
						mServerListAdapter.setCurrentSelect("0000");
						mCurrentServerInfo = null;
						setConnectStatus(STATUS_DISCONNECT);
						if(mServerList.size() != 0 && mCameraPreviewView.getActivate() && mSplashViewDismissed)
							mServerDialog.show();
						mSumOfServerChecked = 0;
					}
					if(mServerList.size() == 0){
						mServerList.add(mNoServer);
					}
					mServerListAdapter.notifyDataSetChanged();
					break;
				case MSG_CONNECT_SERVER:
					Log.i(TAG,"MSG_CONNECT_SERVER");
					String serialNo = (String)msg.obj;
					for(NetworkListener.ServerInfo info : mServerList){
						if(info.serialNo.equals(serialNo)){
							mCurrentServerInfo = info;
							mServerListAdapter.setCurrentSelect(mCurrentServerInfo.serialNo);
							setConnectStatus(STATUS_CONNECT);
							mHandler.removeMessages(MSG_CONNECT_TIMEOUT);
							HTTP_SERVER_IP = mCurrentServerInfo.ipAddr;
							mPreserverSerialNO = mCurrentServerInfo.serialNo;
							SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
							SharedPreferences.Editor ed = sp.edit();
							ed.putString(KEY_PRESERVER_SERIALNO, mPreserverSerialNO);
							ed.commit();
							if(mServerDialog.isShowing())
								mServerDialog.dismiss();
							//UpgradeManager.instance().checkVersion(false);
							mCameraPreviewView.refresh();
							refreshRemoteFileList(RemoteFileActivity.CAPTURE_PATH);
							refreshRemoteFileList(RemoteFileActivity.LOCK_PATH);
							refreshRemoteFileList(RemoteFileActivity.LOOP_PATH);
						}
					}
					break;
				case MSG_DISCONNECT_SERVER:
					Log.i(TAG,"MSG_DISCONNECT_SERVER");
					mServerListAdapter.setCurrentSelect("0000");
					mCurrentServerInfo = null;
					setConnectStatus(STATUS_DISCONNECT);
					mCameraPreviewView.refresh();
					mLockFileList.clear();
					mLoopFileList.clear();
					mCaptureFileList.clear();
					mDownloadingFileList.clear();
					break;
				case MSG_CONNECT_TIMEOUT:
					Log.i(TAG,"MSG_CONNECT_TIMEOUT");
					mServerListAdapter.setCurrentSelect("0000");
					mCurrentServerInfo = null;
					setConnectStatus(STATUS_DISCONNECT);
					mCameraPreviewView.refresh();
					mLockFileList.clear();
					mLoopFileList.clear();
					mCaptureFileList.clear();
					mDownloadingFileList.clear();
					break;
			}
		}
	};

	public boolean isConnected() {
		return mConntectStatus == STATUS_CONNECT;
	}
	
	private void setConnectStatus(final int status){
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				mConntectStatus = status;
				if(mConntectStatus == STATUS_CONNECT)
					mCameraPreviewView.showContect();
				else if(mConntectStatus == STATUS_DISCONNECT)
					mCameraPreviewView.showDiscontect();
				else if(mConntectStatus == STATUS_CONNECTTING){
					mCameraPreviewView.showContectting();
					if(mServerDialog.isShowing())
						mServerDialog.dismiss();
				}
			}
			
		});
	}
	
	public boolean refreshRemoteFileList(final String filePath) {
		if(filePath.equals(RemoteFileActivity.LOCK_PATH))
			mLockFileList.clear();
		else if(filePath.equals(RemoteFileActivity.LOOP_PATH))
			mLoopFileList.clear();
		else if(filePath.equals(RemoteFileActivity.CAPTURE_PATH))
			mCaptureFileList.clear();
		else if(filePath.equals(RemoteFileActivity.DOWNLOADING_PATH)){
			mDownloadingFileList.clear();
			refreshDownloadingFileList();
			return true;
		}
		String url = "/";
		try {
			url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
					"/cgi-bin/Config.cgi?action=dir&property=path&value=" + URLEncoder.encode(filePath, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		Log.i(TAG,"url = " + url);
		HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){

			@Override
			public void onHttpResponse(String result) {
				Log.i(TAG, "result = " + result);
				if(result == null)
					return;
				List<FileInfo> list = FileScanner.readStringXML(result, false);
				if(filePath.equals(RemoteFileActivity.LOCK_PATH))
					mLockFileList.addAll(list);
				else if(filePath.equals(RemoteFileActivity.LOOP_PATH))
					mLoopFileList.addAll(list);
				else if(filePath.equals(RemoteFileActivity.CAPTURE_PATH))
					mCaptureFileList.addAll(list);
				synchronized (mRemoteFileListeners) {
					for(OnRemoteFileListChange l : mRemoteFileListeners){
						l.onRemoteFileListChange(filePath, list);
					}
				}
			}
			
		});

		return true;
	}
	
	public List<FileInfo> getRemoteFileList(final String filePath){
		if(filePath.equals(RemoteFileActivity.LOCK_PATH))
			return mLockFileList;
		else if(filePath.equals(RemoteFileActivity.LOOP_PATH))
			return mLoopFileList;
		else if(filePath.equals(RemoteFileActivity.CAPTURE_PATH))
			return mCaptureFileList;
		else if (filePath.equals(RemoteFileActivity.DOWNLOADING_PATH))
			return mDownloadingFileList;
		return new ArrayList<FileInfo>();
	}
	
	public void addOnRemoteFileListChange(OnRemoteFileListChange l){
		synchronized (mRemoteFileListeners) {
			mRemoteFileListeners.add(l);
		}
	}
	public void removeOnRemoteFileListChange(OnRemoteFileListChange l){
		synchronized (mRemoteFileListeners) {
			mRemoteFileListeners.remove(l);
		}
	}
	
	public void refreshDownloadingFileList(){
		mDownloadingFileList.clear();
		List<FileInfo> all = new ArrayList<FileInfo>();
		all.addAll(mCaptureFileList);
		all.addAll(mLockFileList);
		all.addAll(mLoopFileList);
		for(FileInfo info : all){
			String path = info.path + info.name;
			DownloadTask task = HttpDownloadManager.instance().getDownloadTask(path);
			if(task != null){
				mDownloadingFileList.add(info);
			}
		}
		
		synchronized (mRemoteFileListeners) {
			for(OnRemoteFileListChange l : mRemoteFileListeners){
				l.onRemoteFileListChange(RemoteFileActivity.DOWNLOADING_PATH, mDownloadingFileList);
			}
		}
	}
	
	private NetworkListener.ServerFoundCallBack mServerFoundCallBack = new NetworkListener.ServerFoundCallBack(){

		@Override
		public void serverNotify(ArrayList<ServerInfo> list, boolean change) {
			if(list != null && mConntectStatus != STATUS_CONNECTTING){
				Log.i(TAG,"list = " + list);
				Log.i(TAG,"change = " + change);
				mHandler.removeMessages(MSG_UPDATE_SERVER_LIST);
				Message msg = mHandler.obtainMessage(MSG_UPDATE_SERVER_LIST, list);
				mHandler.sendMessage(msg);
			}
		}
		
	};
	
	@Override
	public void onOpen(ServerHandshake handshakedata) {
		Log.i(TAG,"onOpen = " + handshakedata);
		try {
			JSONObject jso = new JSONObject();
			jso.put("action", "get");
			JSONArray items = new JSONArray();
			items.put(Config.PROPERTY_CARDVR_STATUS_SERIALNO);
			jso.put("list", items);
			jso.toString();
			Log.i(TAG,"jso.toString() = " + jso.toString());
			HttpRequestManager.instance().requestWebSocket(jso.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		Log.i(TAG,"onClose");
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				mServerListAdapter.setCurrentSelect("0000");
				mCurrentServerInfo = null;
				setConnectStatus(STATUS_DISCONNECT);
			}
			
		});
	}
	
	@Override
	public void onError(Exception ex) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				mServerListAdapter.setCurrentSelect("0000");
				mCurrentServerInfo = null;
				setConnectStatus(STATUS_DISCONNECT);
			}
			
		});
	}

	@Override
	public void onSetSerialNo(String serial) {
		Message msg = mHandler.obtainMessage(MSG_CONNECT_SERVER, serial);
		mHandler.removeMessages(MSG_CONNECT_SERVER);
		mHandler.sendMessage(msg);
	}

	@Override
	public void onSetAbilityStatue(String ability) {
		mCameraPreviewView.setAbilityStatue(ability);
	}

	@Override
	public void onSetVolumeStatue(int min, int max, int current) {
		mCameraPreviewView.setVolumeStatue(min, max, current);
	}

	@Override
	public void onSetBrightnessStatue(int min, int max, int current) {
		mCameraPreviewView.setBrightnessStatue(min, max, current);
	}

	@Override
	public void onSetWakeUpStatue(int value) {
		mCameraPreviewView.setWakeUpStatue(value);
	}

	@Override
	public void onSetVoicePromptStatue(boolean enable) {
		//mCameraPreviewView.setVoicePromptStatue(enable);
	}

	@Override
	public void onSetDVRRecordStatus(boolean recording) {
		mCameraPreviewView.setRecordingButton(recording);
	}

	@Override
	public void onSetDVRSDcardStatus(boolean mount) {
		mCameraPreviewView.setDVRSDcardStatus(mount);
	}

	@Override
	public void onDirDVRFiles(String path, JSONArray array) {
		
	}

	@Override
	public void onDeleteDVRFile(boolean succes) {
		
	}

	@Override
	public void onSyncFile(String path, String type, List<FileInfo> list) {
		mCameraPreviewView.onSyncFile(path, type, list);
	}
	
	public interface OnRemoteFileListChange{
		public void onRemoteFileListChange(String filePath, List<FileInfo> list);
	}

	@Override
	public void onSetAutoSleepTime(int time) {
		mCameraPreviewView.setAutoSleepTime(time);
		
	}

	@Override
	public void onGsensorSensity(int sensity) {
		mCameraPreviewView.setGsensorSensity(sensity);
	}

	@Override
	public void onSetBrightnessPercent(int percent) {
		mCameraPreviewView.setBrightnessPercent(percent);
		
	}

	@Override
	public void onGsensorWakeup(int enable) {
		mCameraPreviewView.setGsensorWakeup(enable);
		
	}

	@Override
	public void onGsensorLock(int enable) {
		mCameraPreviewView.setGsensorLock(enable);
		
	}

	@Override
	public void onSoftApConfig(String ssid, String pwd) {
		mCameraPreviewView.setSoftApConfig(ssid, pwd);
		
	}

	@Override
	public void onDvrSaveTime(int time) {
		mCameraPreviewView.setDvrSaveTime(time);
		
	}

	@Override
	public void onDvrMode(String mode) {
		mCameraPreviewView.setDvrMode(mode);
		
	}

	@Override
	public void onDvrMute(boolean mute) {
		mCameraPreviewView.setDvrMute(mute);		
	}

	@Override
	public void onDvrGps(boolean show) {
		mCameraPreviewView.setDvrGps(show);		
	}

	@Override
	public void onSdcardSize(long total, long left, long dvrdir) {
		mCameraPreviewView.setSdcardSize(total, left, dvrdir);
		
	}

	@Override
	public void onUserList(String serialNum, int cloudID, ArrayList<UserItem> list) {
		mCameraPreviewView.setUserList(list);
		
	}

	@Override
	public void onRecordStatus(boolean start, int num, int time) {
		mCameraPreviewView.setRecordStatus(start, num, time);
		
	}

	@Override
	public void onMobileStatus(String imei, boolean ready, int dBm, boolean enable, boolean connected, int type,
			long usage, boolean registered, String flag) {
		mCameraPreviewView.setMobileStatus(ready, enable, connected, type,usage);
		
	}

	@Override
	public void onSatellites(boolean enabled, int num, long timestamp, String nmea) {
		mCameraPreviewView.setSatellites(num);
	}

	@Override
	public void onUpdate(int percent, String version) {
		mCameraPreviewView.setUpdate(percent, version);
		mVersion = version;
	}

	@Override
	public void onCpuInfo(double cpuTemp, double pmuTemp, int core, int freq, boolean isFull, boolean isAccOn) {
	}

	@Override
	public void onGsensor(final float x, final float y, final float z, final boolean passed) {
	}

	@Override
	public void onAdas(String key, boolean value) {
	}

	public String getSoftwareVersion() {
		return mVersion;
	}
}
