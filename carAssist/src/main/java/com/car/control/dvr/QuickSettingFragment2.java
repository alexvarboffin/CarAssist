
package com.car.control.dvr;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.car.control.CarWebSocketClient;
import com.car.control.Config;
import com.car.control.R;
import com.car.control.util.HttpRequestManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class QuickSettingFragment2 extends RelativeLayout{
	
	private static final String TAG = "TAG_QuickSettingFrag2";
	final int MSG_GWAKEUP_SET = 100;
	final int MSG_GLOCK_SET = 101;
	final int MSG_MUTE_SET = 102;
	final int MSG_GPS_SET = 103;	
	
	AlertDialog mAutoSleepAlertDialog, mSensityDialog, mSaveTimeDialog, mQualityDialog;
	SettingListAdapter mAutoSleepAdapter, mSensityAdapter, mSaveTimeAdapter, mQualityAdapter;
	TextView mMobileTitle, mMobileDetail, mAutoSleepDetail, mSensityDetail, mSaveTimeDetail, mQualityDetail, mVersionTitle, mVersionDetail;
	Switch mMobileSwitch, mGWakeupSwitch, mGLockSwitch, mMuteSwitch, mGpsSwitch;
	LinearLayout mSoftapLayout, mAutoSleepLayout, mSensityLayout, mSaveTimeLayout, mQualityLayout, mDvrRestartLayout;
	RelativeLayout mBrightnessLayout, mUpdateLayout;
	private SeekBar mVolumeSeekBar;
	private SeekBar mBrightnessSeekBar;
	
	ImageView mUpdateNotify;
	
	TextView mBondTitle;
	LinearLayout mSdcardExist;
	TextView mSdcardTitle;
	TextView mSdcardSize;
	TextView mAutoSleep;
	TextView mSoftApConfig;
	EditText mSsidName, mPwd;
	String mSsid, mPassword;
	
	int mDvrSaveTime = -1;
	int mDvrMute = -1;
	int mDvrGps = -1;
	String mDvrMode = "";
	long mTotalSize = 0;
	boolean mAdasReport, mAdasReport2, mAdasReport3;
	AlertDialog mInstallDialog = null;

	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_GWAKEUP_SET: {
					try {
						JSONObject jso = new JSONObject();
						jso.put("f", "set");
						JSONObject item = new JSONObject();
						item.put(CarWebSocketClient.GSENSOR_ENABLE, mGWakeup);
						jso.put("generic", item);
						HttpRequestManager.instance().requestWebSocket(jso.toString());
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}				
				}

				break;
			case MSG_GLOCK_SET: {
				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject item = new JSONObject();
					item.put(CarWebSocketClient.VIDEO_LOCK_ENABLE, mGLock);
					jso.put("generic", item);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
			case MSG_MUTE_SET: {
				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject item = new JSONObject();
					item.put(CarWebSocketClient.KEY_MUTE_RECORD, mDvrMute == 1);
					jso.put("dvr", item);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}					
			}
			break;
			case MSG_GPS_SET: {
				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject item = new JSONObject();
					item.put(CarWebSocketClient.KEY_GPS_WATERMARK, mDvrGps == 1);
					jso.put("dvr", item);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			break;
			}
		}
		
	};
	
	int mGsensorVal = -1, mAutoSleepTtime = -1, mVol = -1, mBrightness = -1, mGWakeup = -1, mGLock = -1;
	
	public QuickSettingFragment2(Context context) {
		super(context);
		initView();
	}

	public QuickSettingFragment2(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public QuickSettingFragment2(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}
	
	public void setBrightnessVisible(boolean visible) {
		mBrightnessLayout.setVisibility(visible? View.VISIBLE: View.GONE);
	}
	
	public void refreshSetting(){
		if(RemoteCameraConnectManager.supportWebsocket()){
			if (RemoteCameraConnectManager.supportNewSetting()) {
				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "get");
					JSONArray items = new JSONArray();
					items.put("generic");
					items.put("mobile");
					items.put("softap");
					items.put("dvr");
					items.put("sdcard");
					items.put("bondlist");
					items.put("update");
					items.put("adas");
					jso.put("what", items);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			} else {
				try {
					JSONObject jso = new JSONObject();
					jso.put("action", "get");
					JSONArray items = new JSONArray();
					items.put(Config.PROPERTY_SETTING_STATUS_BRIGHTNESS);
					items.put(Config.PROPERTY_SETTING_STATUS_VOLUME);
					items.put(Config.PROPERTY_SETTING_STATUS_WAKE_UP);
					items.put(Config.PROPERTY_SETTING_STATUS_VOICE_PROMPT);
					items.put(Config.PROPERTY_CARDVR_STATUS_ABILITY);
					jso.put("list", items);
					jso.toString();
					Log.i(TAG,"jso.toString() = " + jso.toString());
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public void setUpdate(final int percent, final String version) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mVersionTitle.setText(version);
				handlePercent(percent);
			}
		});		
	}
	
	void handlePercent(int percent) {
		Log.d(TAG, "percent=" + percent);
		if (percent == -1 || percent == -2) {
			mVersionDetail.setText(R.string.version_latest);
			mUpdateNotify.setVisibility(View.INVISIBLE);
		} else if (percent == 101){
			mUpdateNotify.setVisibility(View.VISIBLE);
			mVersionDetail.setText(R.string.version_available);
			if (mInstallDialog == null) {
				mInstallDialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.version_install)
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
								try {
									JSONObject jso = new JSONObject();
									jso.put("f", "set");
									JSONObject items = new JSONObject();
									items.put("install", true);
									jso.put("update", items);
									HttpRequestManager.instance().requestWebSocket(jso.toString());
								} catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						})
						.setNegativeButton(R.string.cancel, null)
						.create();
			}
			if (!mInstallDialog.isShowing())
				mInstallDialog.show();
		} else if (percent >= 0){
			mUpdateNotify.setVisibility(View.VISIBLE);
			mVersionDetail.setText(String.format(getResources().getString(R.string.version_downloading), percent));
		}
	}	
	
	public void setMobileEnabled(final boolean ready, final boolean enabled, final boolean connected, final int type, final long usage) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				if (ready) {
					mMobileSwitch.setEnabled(true);
					mMobileSwitch.setChecked(enabled);
					String tip = getResources().getString(R.string.mobile_disconnect);
					if (connected) {
						if (type == TelephonyManager.NETWORK_TYPE_LTE) tip = "4G";
						else if (type >= TelephonyManager.NETWORK_TYPE_HSDPA) tip = "3G";
						else tip = "Unknown";
					}
					mMobileTitle.setText(String.format(getResources().getString(R.string.mobile_title), tip));
					mMobileDetail.setText(String.format(getResources().getString(R.string.mobile_config), Formatter.formatFileSize(getContext(), usage)));
				} else {
					mMobileSwitch.setEnabled(false);
					String tip = getResources().getString(R.string.nosim);
					mMobileTitle.setText(String.format(getResources().getString(R.string.mobile_title), tip));
					mMobileDetail.setText(String.format(getResources().getString(R.string.mobile_config), Formatter.formatFileSize(getContext(), 0)));
				}
			}
		});		
	}
	
	public void setGsensorWakeup(final int enable) {		
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				switch(enable){
					case 0:
						if (mGWakeupSwitch.isChecked()) {
							mGWakeupSwitch.setChecked(false);
							mGWakeup = 0;
						}
						break;
					case 1:
						if (!mGWakeupSwitch.isChecked()) {
							mGWakeupSwitch.setChecked(true);
							mGWakeup = 1;
						}
						break;
				}
			}
		});
	}
	
	public void setDvrSaveTime(final int time) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				int value = 0;
				switch(time){
					case 60:
						value = 0;
						mSaveTimeDetail.setText(R.string.auto_save_m1);
						break;
					case 120:
						value = 1;
						mSaveTimeDetail.setText(R.string.auto_save_m2);
						break;
					case 180:
						value = 2;
						mSaveTimeDetail.setText(R.string.auto_save_m3);
						break;					
				}
				mSaveTimeAdapter.setSelected(value);
			}			
		});
	}

	public void setDvrMode(final String mode) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				if (mode.equals("high")) {
					mQualityAdapter.setSelected(0);
					mQualityDetail.setText(R.string.high);
				} else {
					mQualityAdapter.setSelected(1);
					mQualityDetail.setText(R.string.normal);
				}
			}			
		});
	}
	
	public void setUserList(final ArrayList<UserItem> list) {
	}
	
	public void setSdcardSize(final long total, final long left, final long dvrdir) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				mTotalSize = total;
				String tips = String.format(getResources().getString(R.string.sdcard_storage_info), Formatter.formatFileSize(getContext(), total)
						,  Formatter.formatFileSize(getContext(), left),  Formatter.formatFileSize(getContext(), dvrdir)
						,  Formatter.formatFileSize(getContext(), total - left - dvrdir));
				mSdcardSize.setText(tips);
				if (mTotalSize == 0) {
					mSdcardTitle.setText(R.string.nosdcard);
				} else {
					mSdcardTitle.setText(R.string.sdcard_title);
				}
			}
		});
	}

	public void setDvrMute(final boolean mute) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				mMuteSwitch.setChecked(mute);
			}			
		});
	}

	public void setDvrGps(final boolean show) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				mGpsSwitch.setChecked(show);
			}			
		});
	}   	
	
	public void setSoftApConfig(String ssid, String pwd) {
		mSoftApConfig.setText(String.format(getResources().getString(R.string.softap_config), ssid, pwd));
		mSsid = ssid;
		mPassword = pwd;
	}
	
	public void setGsensorLock(final int enable) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				switch(enable){
					case 0:
						if (mGLockSwitch.isChecked()) {
							mGLockSwitch.setChecked(false);
							mGLock = 0;
						}
						break;
					case 1:
						if (!mGLockSwitch.isChecked()) {
							mGLockSwitch.setChecked(true);
							mGLock = 1;
						}
						break;
				}
			}			
		});	
	}	
	
	public void setGsensorSensitive(final int val) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				switch(val){
				case 0:
					mSensityDetail.setText(R.string.setting_wake_up_low);
					break;
				case 1:
					mSensityDetail.setText(R.string.setting_wake_up_mid);
					break;
				case 2:
					mSensityDetail.setText(R.string.setting_wake_up_high);
					break;					
				}
				mSensityAdapter.setSelected(val);
			}			
		});
	}	
	
	public void setAutoSleepTime(final int time) {
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				int value = 0;
				switch(time){
				case 15:
					value = 0;
					mAutoSleepDetail.setText(R.string.auto_sleep_15minutes);
					break;
				case 30:
					value = 1;
					mAutoSleepDetail.setText(R.string.auto_sleep_30minutes);
					break;
				case 60:
					value = 2;
					mAutoSleepDetail.setText(R.string.auto_sleep_60minutes);
					break;
				case 0:
					value = 3;
					mAutoSleepDetail.setText(R.string.auto_sleep_forbidden);
					break;						
			}				
				mAutoSleepAdapter.setSelected(value);
			}			
		});
	}
		
	//设置声音
	public void setVolumeState(int max, int current){		
		mVolumeSeekBar.setMax(max);
		mVolumeSeekBar.setProgress(current);
	}
		
	//设置亮度
	public void setBrightnessPercent(int current){
		mBrightnessSeekBar.setMax(100);
		mBrightnessSeekBar.setProgress(current);
	}
	
    public static void setListViewHeightBasedOnChildren(ListView listView) {  
        ListAdapter listAdapter = listView.getAdapter();  
        if (listAdapter == null) {  
            // pre-condition  
            return;  
        }  
  
        int totalHeight = 0;  
        for (int i = 0; i < listAdapter.getCount(); i++) {  
            View listItem = listAdapter.getView(i, null, listView);  
            listItem.measure(0, 0);  
            totalHeight += listItem.getMeasuredHeight();  
        }  
  
        ViewGroup.LayoutParams params = listView.getLayoutParams();  
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));  
        listView.setLayoutParams(params);  
    }  	
	
	private void initView() {
		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.quick_setting_fragment2, this);
        
        mUpdateNotify = (ImageView)findViewById(R.id.update_notify);
        mVersionTitle = (TextView)findViewById(R.id.version_title);
        mVersionDetail = (TextView)findViewById(R.id.version_detail);
        mUpdateLayout = (RelativeLayout)findViewById(R.id.version_layout);
        mUpdateLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mVersionDetail.setText(R.string.version_check);
				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject items = new JSONObject();
					items.put("check", true);
					jso.put("update", items);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}					
			}
        });          

        mDvrRestartLayout = (LinearLayout)findViewById(R.id.dvrrestart_layout);
        mDvrRestartLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {

		        AlertDialog formatDialog = new AlertDialog.Builder(getContext())  
	            .setTitle(R.string.dvr_restart)
	            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						try {
							JSONObject jso = new JSONObject();
							jso.put("f", "set");
							JSONObject items = new JSONObject();
							items.put("restart", true);
							jso.put("dvr", items);
							HttpRequestManager.instance().requestWebSocket(jso.toString());
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				})
	            .setNegativeButton(R.string.cancel, null)  
	            .create();
		        formatDialog.show(); 
			}

        });        
        
        mMobileSwitch = (Switch)findViewById(R.id.switch_mobile);
        mMobileSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject items = new JSONObject();
					items.put("enable", isChecked);
					jso.put("mobile", items);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
		});
        
        mMobileTitle = (TextView)findViewById(R.id.mobile_title);
        mMobileDetail = (TextView)findViewById(R.id.mobile_config);
        
        mBrightnessLayout = (RelativeLayout)findViewById(R.id.brightnesslayout);
        
        mBondTitle = (TextView)findViewById(R.id.bond_title);
        
        mSdcardTitle = (TextView)findViewById(R.id.sdcard_title);
        mSdcardExist = (LinearLayout)findViewById(R.id.sdcard_exist);		
        mSdcardSize = (TextView) findViewById(R.id.sdcard_size);
        mSdcardExist.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (mTotalSize == 0) {
					Toast.makeText(getContext(), R.string.nosdcard, Toast.LENGTH_LONG).show();
				} else {
			        AlertDialog formatDialog = new AlertDialog.Builder(getContext())  
		            .setTitle(R.string.format_sdcard)
		            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							try {
								JSONObject jso = new JSONObject();
								jso.put("f", "set");
								JSONObject items = new JSONObject();
								items.put("format", true);
								jso.put("sdcard", items);
								HttpRequestManager.instance().requestWebSocket(jso.toString());
								setSdcardSize(0, 0, 0);
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					})
		            .setNegativeButton(R.string.cancel, null)  
		            .create();
			        formatDialog.show(); 
				}
			}
        });
        
		mVolumeSeekBar = (SeekBar)findViewById(R.id.volume);
        mVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Log.i(TAG,"mVolumeSeekBar:" + seekBar.getProgress());
				if(RemoteCameraConnectManager.supportWebsocket()){
					try {
						JSONObject jso = new JSONObject();
						jso.put("f", "set");
						JSONObject items = new JSONObject();
						items.put(CarWebSocketClient.SYSTEM_VOLUME, seekBar.getProgress());
						jso.put("generic", items);
						HttpRequestManager.instance().requestWebSocket(jso.toString());
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
        });
        
        mBrightnessSeekBar = (SeekBar)findViewById(R.id.brightness);
        mBrightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Log.i(TAG,"mBrightnessSeekBar:" + seekBar.getProgress());
				if(RemoteCameraConnectManager.supportWebsocket()){
					try {
						JSONObject jso = new JSONObject();
						jso.put("f", "set");
						JSONObject items = new JSONObject();
						items.put(CarWebSocketClient.SCREEN_BRIGHTNESS, seekBar.getProgress());
						jso.put("generic", items);
						HttpRequestManager.instance().requestWebSocket(jso.toString());
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
        	
        });
        
        initAllReports();
        initSoftAp();
        initAutoSleep();        
        initSensity();
        initGwakeup();
        initGLock();
        initSaveTime();
        initQuality();
        initRecordMute();
        initGpsWatermark();
    	setSdcardSize(0, 0, 0);
	}
	
	void initAllReports() {

	}		
	
	void initSoftAp() {
        mSoftApConfig = (TextView) findViewById(R.id.softap_config);
        mSoftapLayout = (LinearLayout)findViewById(R.id.softap_layout);
        mSoftapLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
		        LayoutInflater layoutInflater = LayoutInflater.from(getContext());  
		        View longinDialogView = layoutInflater.inflate(R.layout.softap_dialog, null);  
		              
		        //获取布局中的控件  
		        mSsidName = (EditText)longinDialogView.findViewById(R.id.edit_username);  
		        mPwd = (EditText)longinDialogView.findViewById(R.id.edit_password);  
		        
		        mSsidName.setText(mSsid);
		        mSsidName.setSelectAllOnFocus(true);
		        mPwd.setText(mPassword);
		        mPwd.setSelectAllOnFocus(true);
		              
		        AlertDialog longinDialog = new AlertDialog.Builder(getContext())  
		            .setTitle(R.string. softap_title)
		            .setView(longinDialogView)
		            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String ssid = mSsidName.getText().toString();
							String pwd = mPwd.getText().toString();
							if (ssid.length() > 0 && pwd.length() >= 8) {
								try {
									JSONObject jso = new JSONObject();
									jso.put("f", "set");
									JSONObject items = new JSONObject();
									items.put("ssid", ssid);
									items.put("pwd", pwd);
									jso.put("softap", items);
									HttpRequestManager.instance().requestWebSocket(jso.toString());
									setSoftApConfig(ssid, pwd);
								} catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							} else {
								Toast.makeText(getContext(), R.string.softap_prompt, Toast.LENGTH_LONG).show();
							}
							
						}
					})  
		            .setNegativeButton(R.string.cancel, null)  
		            .create();
		        longinDialog.show();  
				
			}
		});		
	}
	
	void initGpsWatermark() {
        mGpsSwitch = (Switch)findViewById(R.id.switch_gps);
        mGpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				int val = isChecked? 1: 0;
				if (mDvrGps == val) return;
				mDvrGps = val;
				mHandler.removeMessages(MSG_GPS_SET);
				mHandler.sendEmptyMessageDelayed(MSG_GPS_SET, 500);				
			}
		});		
	}	
	
	void initRecordMute() {
        mMuteSwitch = (Switch)findViewById(R.id.switch_mute);
        mMuteSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				int val = isChecked? 1: 0;
				if (mDvrMute == val) return;
				mDvrMute = val;
				mHandler.removeMessages(MSG_MUTE_SET);
				mHandler.sendEmptyMessageDelayed(MSG_MUTE_SET, 500);
			}
		});		
	}	
	
	void initGLock() {
        mGLockSwitch = (Switch)findViewById(R.id.switch_glock);
        mGLockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				int val = isChecked? 1: 0;
				if (mGLock == val) return;
				mGLock = val;
				mHandler.removeMessages(MSG_GLOCK_SET);
				mHandler.sendEmptyMessageDelayed(MSG_GLOCK_SET, 500);			
			}
		});		
	}	
	
	void initGwakeup() {
        mGWakeupSwitch = (Switch)findViewById(R.id.switch_gsensorwakeup);
        mGWakeupSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				int val = isChecked? 1: 0;
				if (mGWakeup == val) return;
				mGWakeup = val;
				mHandler.removeMessages(MSG_GWAKEUP_SET);
				mHandler.sendEmptyMessageDelayed(MSG_GWAKEUP_SET, 500);
			}
		});		
	}
	
	void initQuality() {
        mQualityDetail = (TextView) findViewById(R.id.quality_config);
		ListView listView = new ListView(getContext());
		ArrayList<String> list = new ArrayList<String>();
		list.add(getResources().getString(R.string.high));
		list.add(getResources().getString(R.string.normal));
		
		mQualityAdapter = new SettingListAdapter(list, getContext(), getResources().getString(R.string.high));
		listView.setAdapter(mQualityAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int id, long arg3) {
				mQualityDialog.dismiss();
				String mode = "";
				switch(id){
					case 0:
						mode = "high";
						mQualityDetail.setText(R.string.high);
						break;
					case 1:
						mode = "normal";
						mQualityDetail.setText(R.string.normal);
						break;				
				}
				
				if (mDvrMode.equals(mode)) return;
				mDvrMode = mode;

				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject item = new JSONObject();
					item.put(CarWebSocketClient.KEY_FRONT_CAMERA, mode);
					jso.put("dvr", item);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		});
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.front_quality_title);
		builder.setView(listView);
		mQualityDialog = builder.create();
		
		
        mQualityLayout = (LinearLayout)findViewById(R.id.quality_layout);
        mQualityLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) { 
				mQualityDialog.show();
			}
		});
	}	
	
	void initSaveTime() {
        mSaveTimeDetail = (TextView) findViewById(R.id.savetime_config);
		ListView listView = new ListView(getContext());
		ArrayList<String> list = new ArrayList<String>();
		list.add(getResources().getString(R.string.auto_save_m1));
		list.add(getResources().getString(R.string.auto_save_m2));
		list.add(getResources().getString(R.string.auto_save_m3));
		
		mSaveTimeAdapter = new SettingListAdapter(list, getContext(), getResources().getString(R.string.auto_save_m2));
		listView.setAdapter(mSaveTimeAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int id, long arg3) {
				mSaveTimeDialog.dismiss();
				int value = 0;
				switch(id){
					case 0:
						value = 60;
						mSaveTimeDetail.setText(R.string.auto_save_m1);
						break;
					case 1:
						value = 120;
						mSaveTimeDetail.setText(R.string.auto_save_m2);
						break;
					case 2:
						value = 180;
						mSaveTimeDetail.setText(R.string.auto_save_m3);
						break;					
				}
				
				if (mDvrSaveTime == value) return;
				mDvrSaveTime = value;

				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject item = new JSONObject();
					item.put(CarWebSocketClient.KEY_AUTO_SAVE_TIME, value);
					jso.put("dvr", item);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		});
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.autosave_time_title);
		builder.setView(listView);
		mSaveTimeDialog = builder.create();
		
		
        mSaveTimeLayout = (LinearLayout)findViewById(R.id.savetime_layout);
        mSaveTimeLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) { 
				mSaveTimeDialog.show();
			}
		});
	}
	
	void initSensity() {
        mSensityDetail = (TextView) findViewById(R.id.sensity_config);
		ListView listView = new ListView(getContext());
		ArrayList<String> list = new ArrayList<String>();
		list.add(getResources().getString(R.string.setting_wake_up_low));
		list.add(getResources().getString(R.string.setting_wake_up_mid));
		list.add(getResources().getString(R.string.setting_wake_up_high));
		
		mSensityAdapter = new SettingListAdapter(list, getContext(), getResources().getString(R.string.setting_wake_up_mid));
		listView.setAdapter(mSensityAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int id, long arg3) {
				mSensityDialog.dismiss();
				switch(id){
					case 0:
						mSensityDetail.setText(R.string.setting_wake_up_low);
						break;
					case 1:
						mSensityDetail.setText(R.string.setting_wake_up_mid);
						break;
					case 2:
						mSensityDetail.setText(R.string.setting_wake_up_high);
						break;					
				}
				
				if (mGsensorVal == id) return;
				mGsensorVal = id;

				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject items = new JSONObject();
					items.put(CarWebSocketClient.GSENSOR_SENSITIVE, id);
					jso.put("generic", items);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		});
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.sensitive_title);
		builder.setView(listView);
		mSensityDialog = builder.create();
		
		
        mSensityLayout = (LinearLayout)findViewById(R.id.sensity_layout);
        mSensityLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) { 
				mSensityDialog.show();
			}
		});		
	}
	
	void initAutoSleep() {
        mAutoSleepDetail = (TextView) findViewById(R.id.autosleep_config);
        mAutoSleep = (TextView) findViewById(R.id.autosleep);
		ListView listView = new ListView(getContext());
		ArrayList<String> list = new ArrayList<String>();
		list.add(getResources().getString(R.string.auto_sleep_m15));
		list.add(getResources().getString(R.string.auto_sleep_m30));
		list.add(getResources().getString(R.string.auto_sleep_m60));
		list.add(getResources().getString(R.string.auto_sleep_mf));
		
		mAutoSleepAdapter = new SettingListAdapter(list, getContext(), getResources().getString(R.string.auto_sleep_m15));
		listView.setAdapter(mAutoSleepAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int id, long arg3) {
				mAutoSleepAlertDialog.dismiss();
				int value = 0;
				switch(id){
					case 0:
						value = 15;
						mAutoSleepDetail.setText(R.string.auto_sleep_15minutes);
						break;
					case 1:
						value = 30;
						mAutoSleepDetail.setText(R.string.auto_sleep_30minutes);
						break;
					case 2:
						value = 60;
						mAutoSleepDetail.setText(R.string.auto_sleep_60minutes);
						break;
					case 3:
						value = 0;
						mAutoSleepDetail.setText(R.string.auto_sleep_forbidden);
						break;						
				}
				if (mAutoSleepTtime == value) return;
				
				mAutoSleepTtime = value;

				try {
					JSONObject jso = new JSONObject();
					jso.put("f", "set");
					JSONObject item = new JSONObject();
					item.put(CarWebSocketClient.AUTOSLEEP_TIME, value);
					jso.put("generic", item);
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.auto_sleep);
		builder.setView(listView);
		mAutoSleepAlertDialog = builder.create();
		
		
        mAutoSleepLayout = (LinearLayout)findViewById(R.id.autosleep_layout);
        mAutoSleepLayout.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) { 
				mAutoSleepAlertDialog.show();
			}
		});  		
	}
	
	class SettingListAdapter extends BaseAdapter{
		
		private List<String> mList;
		private Context mContext;
		private LayoutInflater mInflater;
		private String mCurrent;
		
		SettingListAdapter(List<String> list, Context context, String current){
			mList = list;
			mContext = context;
			mCurrent = current;
			mInflater = LayoutInflater.from(mContext);
		}
		
		public void setCurrent(String current){
			mCurrent = current;
			notifyDataSetChanged();
		}
		
		public void setSelected(int index) {
			mCurrent = mList.get(index);
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mList.size();
		}

		@Override
		public Object getItem(int position) {
			return mList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			String item = mList.get(position);
			
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.setting_item, null);
	        }
			
			ViewHolder vh = (ViewHolder)convertView.getTag();
			if(vh == null){
				vh = new ViewHolder();
				vh.mServerName = (TextView)convertView.findViewById(R.id.setting_name);
				vh.mServerCheckBox = (CheckBox)convertView.findViewById(R.id.setting_checkbox);
				convertView.setTag(vh);
			}
			
			vh.mServerName.setText(item);
			vh.mServerCheckBox.setChecked(item.equals(mCurrent));
			
			return convertView;
		}
		
	}
	
	private class ViewHolder{
		TextView mServerName;
		CheckBox mServerCheckBox;
	}	
}
