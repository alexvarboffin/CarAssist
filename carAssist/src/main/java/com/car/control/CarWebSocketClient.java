package com.car.control;

import android.provider.Settings;
import android.util.Log;

import com.car.control.browser.FileInfo;
import com.car.control.browser.FileScanner;
import com.car.control.dvr.RemoteCameraConnectManager;
import com.car.control.dvr.UserItem;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CarWebSocketClient extends WebSocketClient{
	
	private static final String TAG = "CA_CarWebSocketClient";
	public static final String FM_ENABLE = "fm_enable";
	public static final String FM_FREQ = "fm_freq";
    public static final String TTS_MUTE = "ttsmute";
    public static final String WAKEUP_SENSITY = "sensity";
    public static final String AUTO_CLEAR = "autoclear";
    public static final String REMOTE_CTRL = "remotectrl";
	public static final String AUTOSLEEP_TIME = "autosleeptime";
	public static final String GSENSOR_ENABLE = "gsensor_enable";
    public static final String GSENSOR_SENSITIVE = "gsensor_sensitive";
    public static final String VIDEO_LOCK_ENABLE = "video_lock_enable";
    public static final String NAVI_HUD_ENABLE = "navi_hud_enable";
    public static final String BT_KEYBOARD_ENABLE = "bt_keyboard_enable";
	public static final String GPS_ENABLE = "gps_enable";
    
    public static final String KEY_AUTO_SAVE_TIME      = "autosave_time";
    public static final String KEY_STOP_PREVIEW_TIME      = "stoppreview_time";
    public static final String KEY_FRONT_CAMERA        = "front_quality";
    public static final String KEY_BACK_CAMERA         = "back_quality";
    public static final  String KEY_MUTE_RECORD         = "mute_record";
    public static final  String KEY_AUTO_START_RECORD   = "auto_start_record";
    public static final  String KEY_SHOW_FLOAT_BUTTON   = "show_float_button";
    public static final  String KEY_GPS_WATERMARK       = "gps_watermark";
    public static final  String KEY_FLASH_LIGHT         = "flash_light";
    
    public static final  String SCREEN_BRIGHTNESS         = Settings.System.SCREEN_BRIGHTNESS;
    public static final  String SYSTEM_VOLUME         = "system_volume";
    public static final  String MOBILE_NETWORK         = "mobile_network";	
	
	private List<CarWebSocketClientCallback> mListCallbacks = new ArrayList<CarWebSocketClientCallback>();
	private static CarWebSocketClient sIns;
	
	public static void create(URI serverURI){
		if(sIns != null){
			sIns.clearCallback();
			sIns.close();
		}
		sIns = new CarWebSocketClient(serverURI);
	}
	
	public static void destory(){
		if(sIns != null) {
			sIns.clearCallback();
			if (sIns != null) {
				sIns.close();
			}
			sIns = null;
		}
	}
	
	public static CarWebSocketClient instance(){
		return sIns;
	}
	
	public void registerCallback(CarWebSocketClientCallback cb){
		synchronized (mListCallbacks) {
			mListCallbacks.add(cb);
		}
	}
	
	public void unregisterCallback(CarWebSocketClientCallback cb){
		synchronized (mListCallbacks) {
			mListCallbacks.remove(cb);
		}
	}
	
	public void clearCallback(){
		synchronized (mListCallbacks) {
			mListCallbacks.clear();
		}
	}
	
	private CarWebSocketClient(URI serverURI) {
		super(serverURI);
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		Log.i(TAG, "onOpen");
		synchronized (mListCallbacks) {
			for(CarWebSocketClientCallback cb : mListCallbacks)
				cb.onOpen(handshakedata);
		}
	}

	@Override
	public void onMessage(String message) {
//		Log.i(TAG, "onMessage:message = " + message);
		try {
			JSONObject jsb = new JSONObject(message);
			String action = jsb.optString("action");
			if (action != null && action.length() > 0) {
				if(action.equals(Config.ACTION_GET)){
					JSONObject items = jsb.getJSONObject("list");
					processGetActions(items);
				}else if(action.equals(Config.ACTION_DIR)){
					JSONArray array = jsb.getJSONArray("list");
					String path = jsb.getString("path");
					synchronized (mListCallbacks) {
						for(CarWebSocketClientCallback cb : mListCallbacks)
							cb.onDirDVRFiles(path, array);
					}
				}else if(action.equals(Config.ACTION_DELETE)){
					boolean succes = jsb.get("result").equals("OK");
					synchronized (mListCallbacks) {
						for(CarWebSocketClientCallback cb : mListCallbacks)
							cb.onDeleteDVRFile(succes);
					}
				}else if(action.equals(Config.ACTION_SYNC_FILE)){
					String path = jsb.getString("path");
					String type = jsb.getString("type");
					JSONArray array = jsb.getJSONArray("list");
					List<FileInfo> list = FileScanner.readJSONArray(array, false);
					synchronized (mListCallbacks) {
						for(CarWebSocketClientCallback cb : mListCallbacks)
							cb.onSyncFile(path, type, list);
					}
				}
			}
			
			String f = jsb.optString("f");
			if (f != null && f.length() > 0) {
				if (f.equals("report")) {
					JSONObject generc = jsb.optJSONObject("generic");
					if (generc != null) {
						processGeneric(generc);
					}
					JSONObject softap = jsb.optJSONObject("softap");
					if (softap != null) {
						String ssid = softap.getString("ssid");
						String pwd = softap.getString("pwd");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onSoftApConfig(ssid, pwd);
						}
					}
					JSONObject dvr = jsb.optJSONObject("dvr");
					if (dvr != null) {
						processDvr(dvr);
					}
					
					JSONObject sdcard = jsb.optJSONObject("sdcard");
					if (sdcard != null) {
						long total = sdcard.getLong("total");
						long left = sdcard.getLong("left");
						long dvrdir = sdcard.getLong("dvrdir");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onSdcardSize(total, left, dvrdir);
						}
					}		
					
					ArrayList<UserItem> userList = new ArrayList<UserItem>();
					JSONArray bondlist = jsb.optJSONArray("bondlist");
					if (bondlist != null) {
						String serialNum = jsb.optString("deviceid");
						int cloudID = jsb.optInt("cloudid");
						if (serialNum.length() > 0 && cloudID > 0) {
							Log.d(TAG, "onUserList, cloudID:" + cloudID);
							if (RemoteCameraConnectManager.getCurrentServerInfo() != null) {
								RemoteCameraConnectManager.getCurrentServerInfo().cloudID = cloudID;
							}
						}

			            for (int i = 0; i < bondlist.length(); i++) {
			                JSONObject item = bondlist.getJSONObject(i);
			                UserItem ui = new UserItem(item.optString("uname"), item.optString("uid"));
			                ui.headImg = item.optString("img");
			                userList.add(ui);
			            }
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onUserList(serialNum, cloudID, userList);
						}
					}
					
					JSONObject record = jsb.optJSONObject("record");
					if (record != null) {
						boolean start = record.getBoolean("start");
						int num = record.getInt("num");
						int time = record.getInt("time");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onRecordStatus(start, num, time);
						}
					}
					
					JSONObject mobile = jsb.optJSONObject("mobile");
					if (mobile != null) {
						String imei = mobile.optString("imei");
						boolean ready = mobile.optBoolean("ready");
						int dbm = mobile.optInt("dbm");
						boolean enable = mobile.optBoolean("enable");
						boolean connected = mobile.optBoolean("connected");
						int type = mobile.getInt("type");
						long usage = mobile.getInt("usage");
						boolean registered = mobile.optBoolean("registered");
						String flag = mobile.optString("flag");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onMobileStatus(imei, ready, dbm, enable, connected, type, usage, registered, flag);
						}
					}
					
					JSONObject gps = jsb.optJSONObject("gps");
					if (gps != null) {
						int num = gps.getInt("satellites");
						boolean enabled = gps.optBoolean("enable");
						long timestamp = gps.getInt("timestamp");
						String nmea = gps.getString("nmea");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onSatellites(enabled, num, timestamp, nmea);
						}
					}
					
					JSONObject update = jsb.optJSONObject("update");
					if (update != null) {
						int percent = update.getInt("percent");
						String version = update.getString("version");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onUpdate(percent, version);
						}
					}
					
					JSONObject cpuinfo = jsb.optJSONObject("cpuinfo");
                    if (cpuinfo != null) {
                        double cpuTemp = cpuinfo.getDouble("cputemp");
                        double pmuTemp = cpuinfo.getDouble("pmutemp");
                        int core = cpuinfo.getInt("core");
                        int freq = cpuinfo.getInt("freq");
                        boolean isFull = cpuinfo.getBoolean("isfull");
						boolean isAccOn = cpuinfo.getBoolean("acc_on");
                        synchronized (mListCallbacks) {
                            for(CarWebSocketClientCallback cb : mListCallbacks)
                                cb.onCpuInfo(cpuTemp, pmuTemp, core, freq, isFull, isAccOn);
                        }
                    }

					JSONObject gsensor = jsb.optJSONObject("gsensor");
					if (gsensor != null) {
						float x = (float)gsensor.getDouble("x");
						float y = (float)gsensor.getDouble("y");
						float z = (float)gsensor.getDouble("z");
						boolean passed = gsensor.getBoolean("passed");
						synchronized (mListCallbacks) {
							for(CarWebSocketClientCallback cb : mListCallbacks)
								cb.onGsensor(x, y, z, passed);
						}
					}
                    
                    JSONObject adas = jsb.optJSONObject("adas");
                    if (adas != null) {
                    	if (adas.has("enable")) {
                    		boolean enable = adas.getBoolean("enable");
                            synchronized (mListCallbacks) {
                                for(CarWebSocketClientCallback cb : mListCallbacks)
                                    cb.onAdas("enable", enable);
                            }                    		
                    	}
                    	if (adas.has("adas_calibration")) {
                    		boolean adas_calibration = adas.getBoolean("adas_calibration");
                            synchronized (mListCallbacks) {
                                for(CarWebSocketClientCallback cb : mListCallbacks)
                                    cb.onAdas("adas_calibration", adas_calibration);
                            }
                    	}
                    	if (adas.has("adas_report")) {
                    		boolean adas_report = adas.getBoolean("adas_report");
                            synchronized (mListCallbacks) {
                                for(CarWebSocketClientCallback cb : mListCallbacks)
                                    cb.onAdas("adas_report", adas_report);
                            }
                    	}          
                    	
                      	if (adas.has("adas_report2")) {
                    		boolean adas_report2 = adas.getBoolean("adas_report2");
                            synchronized (mListCallbacks) {
                                for(CarWebSocketClientCallback cb : mListCallbacks)
                                    cb.onAdas("adas_report2", adas_report2);
                            }
                    	}   
                      	
                      	if (adas.has("adas_report3")) {
                    		boolean adas_report3 = adas.getBoolean("adas_report3");
                            synchronized (mListCallbacks) {
                                for(CarWebSocketClientCallback cb : mListCallbacks)
                                    cb.onAdas("adas_report3", adas_report3);
                            }
                    	}
					}
				}
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	
	
	private void processDvr(JSONObject jso) throws JSONException{
		if(jso == null)
			return;
		
		Iterator<?> it = jso.keys();
		String key;
		while(it.hasNext()){
			key = (String) it.next().toString();
			if (key.equals(KEY_AUTO_SAVE_TIME)) {
				int val = jso.getInt(key);
				synchronized (mListCallbacks) {
					for(CarWebSocketClientCallback cb : mListCallbacks)
						cb.onDvrSaveTime(val);
				}
			} else if (key.equals(KEY_MUTE_RECORD)) {
				boolean mute = jso.getBoolean(key);
				synchronized (mListCallbacks) {
					for(CarWebSocketClientCallback cb : mListCallbacks)
						cb.onDvrMute(mute);
				}
			} else if (key.equals(KEY_FRONT_CAMERA)) {
				String mode = jso.getString(key);
				synchronized (mListCallbacks) {
					for(CarWebSocketClientCallback cb : mListCallbacks)
						cb.onDvrMode(mode);
				}
			} else if (key.equals(KEY_GPS_WATERMARK)) {
				boolean gps = jso.getBoolean(key);
				synchronized (mListCallbacks) {
					for(CarWebSocketClientCallback cb : mListCallbacks)
						cb.onDvrGps(gps);
				}
			}
			
			
		}
		
	}
	
	private void processGeneric(JSONObject jso) {
		if(jso == null)
			return;
		
		Iterator<?> it = jso.keys();
		String key;
		int value;
		while(it.hasNext()){
			key = (String) it.next().toString();
			try {
				value = jso.getInt(key);
			} catch (JSONException e) {
				Log.w(TAG, e);
				continue;
			}
			doParseGenericSetting(key, value);
		}
	}	
	
	private void doParseGenericSetting(String key, int value){
		if (key.equals(SYSTEM_VOLUME)) {
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetVolumeStatue(0, 15, value);
			}
		} else if (key.equals(SCREEN_BRIGHTNESS)) {
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetBrightnessPercent(value);
			}
		} else if (key.equals(AUTOSLEEP_TIME)) {
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetAutoSleepTime(value);
			}
		} else if (key.equals(GSENSOR_SENSITIVE)) {
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onGsensorSensity(value);
			}	
		} else if (key.equals(GSENSOR_ENABLE)) {
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onGsensorWakeup(value);
			}	
		} else if (key.equals(VIDEO_LOCK_ENABLE)) {
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onGsensorLock(value);
			}
		}
	}
	
	private void processGetActions(JSONObject jso) throws JSONException{
		if(jso == null)
			return;
		
		Iterator<?> it = jso.keys();
		String key;
		String value;
		while(it.hasNext()){
			key = (String) it.next().toString();
			value = jso.getString(key);
			doProcessGetAction(key, value);
		}
	}
	
	private void doProcessGetAction(String key, String value){
		
		if(key.equals(Config.PROPERTY_SETTING_STATUS_VOLUME)){
			String strs[] = value.split(",");
			int min = Integer.parseInt(strs[0].split(":")[1]);
			int max = Integer.parseInt(strs[1].split(":")[1]);
			int current = Integer.parseInt(strs[2].split(":")[1]);
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetVolumeStatue(min, max, current);
			}
		}else if(key.equals(Config.PROPERTY_SETTING_STATUS_BRIGHTNESS)){
			String strs[] = value.split(",");
			int min = Integer.parseInt(strs[0].split(":")[1]);
			int max = Integer.parseInt(strs[1].split(":")[1]);
			int current = Integer.parseInt(strs[2].split(":")[1]);
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetBrightnessStatue(min, max, current);
			}
		}else if(key.equals(Config.PROPERTY_SETTING_STATUS_WAKE_UP)){
			int v = Integer.parseInt(value);
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetWakeUpStatue(v);
			}
		}else if(key.equals(Config.PROPERTY_SETTING_STATUS_VOICE_PROMPT)){
			boolean enable = Boolean.parseBoolean(value);
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetVoicePromptStatue(enable);
			}
		}else if(key.equals(Config.PROPERTY_CARDVR_STATUS_ABILITY)){
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetAbilityStatue(value);
			}
		}else if(key.equals(Config.PROPERTY_CARDVR_STATUS_SERIALNO)){
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetSerialNo(value);
			}
		}else if(key.equals(Config.PROPERTY_CAMERA_RECORDING_STATUS)){
			boolean recording = Boolean.parseBoolean(value);
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetDVRRecordStatus(recording);
			}
		}else if(key.equals(Config.PROPERTY_DVRSDCARD_STATUS_MOUNT)){
			boolean mount = Boolean.parseBoolean(value);
			synchronized (mListCallbacks) {
				for(CarWebSocketClientCallback cb : mListCallbacks)
					cb.onSetDVRSDcardStatus(mount);
			}
		}
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		Log.i(TAG, "onClose");
		synchronized (mListCallbacks) {
			for(CarWebSocketClientCallback cb : mListCallbacks)
				cb.onClose(code, reason, remote);
		}
	}

	@Override
	public void onError(Exception ex) {
		Log.i(TAG, "onError");
		synchronized (mListCallbacks) {
			for(CarWebSocketClientCallback cb : mListCallbacks)
				cb.onError(ex);
		}
	}
	
	public interface CarWebSocketClientCallback{
		
		public void onOpen(ServerHandshake handshakedata);
		public void onClose(int code, String reason, boolean remote);
		public void onError(Exception ex);
		public void onSetSerialNo(String serial);
		public void onSetAbilityStatue(String ability);
		public void onSetVolumeStatue(int min, int max, int current);
		public void onSetBrightnessStatue(int min, int max, int current);
		public void onSetWakeUpStatue(int value);
		public void onSetVoicePromptStatue(boolean enable);
		public void onSetDVRRecordStatus(boolean recording);
		public void onSetDVRSDcardStatus(boolean mount);
		public void onDirDVRFiles(String path, JSONArray array);
		public void onDeleteDVRFile(boolean succes);
		public void onSyncFile(String path, String type, List<FileInfo> list);
		
		public void onSetBrightnessPercent(int percent);
		public void onSetAutoSleepTime(int time);
		public void onGsensorSensity(int sensity);
		public void onGsensorWakeup(int enable);
		public void onGsensorLock(int enable);
		public void onSoftApConfig(String ssid, String pwd);
		
		public void onDvrSaveTime(int time);
		public void onDvrMode(String mode);
		public void onDvrMute(boolean mute);
		public void onDvrGps(boolean show);
		public void onSdcardSize(long total, long left, long dvrdir);
		public void onUserList(String serialNum, int cloudID, ArrayList<UserItem> list);
		public void onRecordStatus(boolean start, int num, int time);
		public void onMobileStatus(String imei, boolean ready, int dBm, boolean enable, boolean connected,
								   int type, long usage, boolean registered, String flag);
		public void onSatellites(boolean enabled, int num, long timestamp, String nmea);
		public void onUpdate(int percent, String version);
		public void onCpuInfo(double cpuTemp, double pmuTemp, int core, int freq, boolean isFull, boolean isAccOn);
		public void onGsensor(float x, float y, float z, boolean passed);
		public void onAdas(String key, boolean value);
	}
}
