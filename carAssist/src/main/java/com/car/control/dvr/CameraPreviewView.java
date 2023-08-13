package com.car.control.dvr;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.car.control.CarAssistMainView;
import com.car.control.CarControlActivity;
import com.car.control.IPagerView;
import com.car.control.R;
import com.car.control.browser.FileInfo;
import com.car.control.util.Util;
import com.car.control.util.WifiAdmin;
import com.car.control.util.WifiHideAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CameraPreviewView extends IPagerView {
	
	private static final String TAG = "CarSvc_CameraPrvView";
	
	private CameraView mCameraView;
	private boolean mActivate = false;
	
	RelativeLayout mTab1, mTab2;
	boolean mUsingTab1 = true;
	
	LinearLayout mFragmentNormal;
	FrameLayout mFragmentSetting;
	private AboutFragment mAboutFragment;
    private QuickVoiceFragment mQuickVoiceFragment;
    private QuickSettingFragment mQuickSettingFragment;
    private QuickSettingFragment2 mQuickSettingFragment2;
    private QuickTrackFragment mQuickTrackFragment;
    private RadioGroup mTabRadioGroup, mTabRadioGroup2;
    private View mCurrentFragment;
    private Map<View, Integer> mFragmentMap = new HashMap<View, Integer>();
    private int mSoftAPState = WifiHideAPI.WIFI_AP_STATE_FAILED;
    private WifiManager mWifiManager;
	private WifiAdmin mWifiAdmin;
    
	private boolean mFirstWifiApState = true;
	private ProgressDialog mScanRecorderDialog;
	private Handler mHandler = new Handler();
	
	public CameraPreviewView(Context context) {
		super(context);
		initView();
	}
	
	public CameraPreviewView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public CameraPreviewView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}
	
	public void setQuickSetting2(CarAssistMainView carAssistMainView){
		mFragmentSetting = (FrameLayout)carAssistMainView.findViewById(R.id.fragment_setting);
		mQuickSettingFragment2 = (QuickSettingFragment2)carAssistMainView.findViewById(R.id.quick_setting_fragment2);
		mFragmentMap.put(mQuickSettingFragment2, 2);
	}
	
	public void showContect(){
        mCameraView.showContect();
   		if (RemoteCameraConnectManager.isHeadless()) {mQuickSettingFragment2.setBrightnessVisible(false);
   			mQuickVoiceFragment.setHeadless(true);
   		}
		else {
			mQuickSettingFragment2.setBrightnessVisible(true);
			mQuickVoiceFragment.setHeadless(false);
		}	
   		
   		boolean needReset = false;
   		if (RemoteCameraConnectManager.isHeadless() || RemoteCameraConnectManager.isOversea()) {
   			if (mUsingTab1) needReset = true;
   		} else {
   			if (!mUsingTab1) needReset = true;
   		}   		
   		
   		if (needReset) {
   			showDvrSetting(false);
	   		mCurrentFragment.setVisibility(View.INVISIBLE);
	   		if (RemoteCameraConnectManager.isHeadless() || RemoteCameraConnectManager.isOversea()) {
	   			mTab1.setVisibility(View.GONE);
	   			mTab2.setVisibility(View.VISIBLE);
	   			mUsingTab1 = false;
	   			getContext().getSharedPreferences("newsetting", 0).edit().putBoolean("newtab", true).commit();
	   			((RadioButton)findViewById(R.id.about_button)).setChecked(true);
	   			mCurrentFragment = mAboutFragment;  
	   		} else {
	   			mTab2.setVisibility(View.GONE);
	   			mTab1.setVisibility(View.VISIBLE);
	   			mUsingTab1 = true;
	   			getContext().getSharedPreferences("newsetting", 0).edit().putBoolean("newtab", false).commit();
	   			((RadioButton)findViewById(R.id.voice_button)).setChecked(true);
	   			mCurrentFragment = mQuickVoiceFragment;
	   		}
	   		mCurrentFragment.setVisibility(View.VISIBLE); 
   		}
   		
   		if (RemoteCameraConnectManager.supportNewSetting()) {
   			mQuickSettingFragment.showDvrMore(true);
   		} else {
   			mQuickSettingFragment.showDvrMore(false);
   		}
   		
   		mQuickSettingFragment.setAbilityStatue(null);
    }
	
	public void showDvrSetting(boolean show) {	
		if (show) {
        	mFragmentNormal.setAnimation(AnimationUtils.loadAnimation(getContext(), 
        			R.anim.fragment_exit_left));
        	mFragmentSetting.setAnimation(AnimationUtils.loadAnimation(getContext(), 
        			R.anim.fragment_enter_left));			
			mFragmentSetting.setVisibility(View.VISIBLE);
			mFragmentNormal.setVisibility(View.GONE);
		} else {
			if (mFragmentSetting.getVisibility() == View.VISIBLE) {
				mFragmentSetting.setAnimation(AnimationUtils.loadAnimation(getContext(), 
	        			R.anim.fragment_exit_right));
	        	mFragmentNormal.setAnimation(AnimationUtils.loadAnimation(getContext(), 
	        			R.anim.fragment_enter_right));			
				mFragmentSetting.setVisibility(View.GONE);
				mFragmentNormal.setVisibility(View.VISIBLE);
			}
		}
	}
    
    public void showDiscontect(){
        mCameraView.showDiscontect();
        //((RadioButton)findViewById(R.id.voice_button)).setChecked(true);
    }
    
    public void showContectting(){
    	mCameraView.showContectting();
    }
    
    public boolean getActivate(){
        return mActivate;
    }
    
    public void setDVRSDcardStatus(boolean mount){
        mCameraView.setDVRSDcardStatus(mount);
    }
    
    public void setRecordingButton(final boolean recording){
        mCameraView.setRecordingButton(recording);
    }

	public void setDvrSaveTime(int time) {
		mQuickSettingFragment2.setDvrSaveTime(time);
	}

	public void setDvrMode(String mode) {
		mQuickSettingFragment2.setDvrMode(mode);
	}

	public void setDvrMute(boolean mute) {
		mQuickSettingFragment2.setDvrMute(mute);
	}

	public void setUpdate(int percent, String version) {
		mQuickSettingFragment2.setUpdate(percent, version);
	}	
	
	public void setSatellites(int num) {
		mAboutFragment.setSatellites(num);
	}
	
	public void setMobileStatus(boolean ready, boolean enable, boolean connected, int type,
			long usage) {
		mAboutFragment.setNetworkType(ready, connected, type);
		mQuickSettingFragment2.setMobileEnabled(ready, enable, connected, type, usage);
	}
	
	public void setRecordStatus(boolean start, int num, int time) {
		mAboutFragment.setRecordingStatus(start, num, time);
	}
	
	public void setUserList(ArrayList<UserItem> list) {
		mQuickSettingFragment2.setUserList(list);
	}
	
	public void setSdcardSize(long total, long left, long dvrdir) {
		mQuickSettingFragment2.setSdcardSize(total, left, dvrdir);
	}

	public void setDvrGps(boolean show) {
		mQuickSettingFragment2.setDvrGps(show);
	}    
    
    public void setSoftApConfig(String ssid, String pwd) {
    	mQuickSettingFragment2.setSoftApConfig(ssid, pwd);
    }
    
	public void setGsensorLock(int enable){		
		mQuickSettingFragment2.setGsensorLock(enable);
	}    
	
	public void setGsensorWakeup(int enable){		
		mQuickSettingFragment2.setGsensorWakeup(enable);
	}       
    
	public void setAutoSleepTime(int time){		
		mQuickSettingFragment2.setAutoSleepTime(time);
	}    
	
	public void setGsensorSensity(int val){		
		mQuickSettingFragment2.setGsensorSensitive(val);
	}   	

	//根据属性显示或者隐藏控件
	public void setAbilityStatue(String ability){
		mQuickSettingFragment.setAbilityStatue(ability);
	}
	
	//设置声音
	public void setVolumeStatue(int min, int max, int current){		
		mQuickSettingFragment.setVolumeStatue(min, max, current);
		if (RemoteCameraConnectManager.supportNewSetting())
			mQuickSettingFragment2.setVolumeState(max, current);
	}
	
	public void setBrightnessPercent(int current){
		mQuickSettingFragment2.setBrightnessPercent(current);
	}
	
	//设置亮度
	public void setBrightnessStatue(int min, int max, int current){
		mQuickSettingFragment.setBrightnessStatue(min, max, current);
	}
	
	//设置唤醒灵敏度
	public void setWakeUpStatue(final int value){
		mQuickSettingFragment.setWakeUpStatue(value);
	}
	
	public void onSyncFile(String path, String type, List<FileInfo> list) {
		mQuickVoiceFragment.setSyncFile(path, type, list);
	}
	
	@Override
	public boolean onCreateOptionsMenu(MenuInflater mi, Menu menu) {
		mi.inflate(R.menu.camera_preview, menu);
		/*MenuItem item = menu.findItem(R.id.softap);
		if(item != null){
			if(mSoftAPState == WifiHideAPI.WIFI_AP_STATE_ENABLED)
				item.setTitle(R.string.softap_close);
			else if(mSoftAPState == WifiHideAPI.WIFI_AP_STATE_DISABLED)
				item.setTitle(R.string.softap_open);
			else if(mSoftAPState == WifiHideAPI.WIFI_AP_STATE_DISABLING)
				item.setTitle(R.string.softap_closing);
			else if(mSoftAPState == WifiHideAPI.WIFI_AP_STATE_ENABLING)
				item.setTitle(R.string.softap_opening);
		}*/
		return true;
	}	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		} else if(item.getItemId() == R.id.ip_setting){
			RemoteCameraConnectManager.instance().showServerDialog();
			return true;
		}/*else if(item.getItemId() == R.id.softap){
			if(mSoftAPState == WifiHideAPI.WIFI_AP_STATE_ENABLED)
				RemoteCameraConnectManager.instance().getNetworkListener().setWifiApEnable(false);
			else if(mSoftAPState == WifiHideAPI.WIFI_AP_STATE_DISABLED)
				RemoteCameraConnectManager.instance().getNetworkListener().setWifiApEnable(true);
			return true;
		}*/
		return false;
	}

	@Override
	public void onActivate() {
		Log.i(TAG,"onActivate()");
		mActivate = true;
		if (RemoteCameraConnectManager.supportNewSetting()) {
			mQuickSettingFragment2.refreshSetting();
		} else
			mQuickSettingFragment.refreshSetting();
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean show = sp.getBoolean(CarAssistMainView.KEY_PREVIEW_CLING, true);
		if(show)
			((CarControlActivity)getContext()).initCling(R.id.preview_cling, null, false, 0);
		mQuickTrackFragment.onResume(mActivate);
		mCameraView.refreshMiddleText();
	}

	@Override
	public void onDeactivate() {
		Log.i(TAG,"onDeactivate()");
		mActivate = false;
		mCameraView.stopPreview();
		mQuickTrackFragment.onPause();
	}

	@Override
	public void onActivityCreate(Bundle savedInstanceState) {
		Log.i(TAG,"onActivityCreate()");
		mQuickTrackFragment.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityPause() {
		Log.i(TAG,"onActivityPause()");
		mQuickTrackFragment.onPause();
		mQuickVoiceFragment.onPause();
		if(mActivate){
			onDeactivate();
			mActivate = true;
		}
	}

	@Override
	public void onAcitvityResume() {
		Log.i(TAG,"onAcitvityResume()");
		mQuickTrackFragment.onResume(mActivate);
		mQuickVoiceFragment.onResume();
		if(mActivate)
			onActivate();
	}
	
	@Override
	public void onActivityStart() {
		Log.i(TAG,"onActivityStart()");
	}

	@Override
	public void onActivityStop() {
		Log.i(TAG,"onActivityStop()");
	}

	@Override
	public void onActivityDestroy() {
		Log.i(TAG,"onActivityDestroy()");
		mQuickTrackFragment.onDestroy();
		mQuickVoiceFragment.onDestroy();
		RemoteCameraConnectManager.destory();
		getContext().unregisterReceiver(mBroadcastReceiver);
		if(mWifiAdmin != null){
			mWifiAdmin.cancelAddNetwork();
		}
		RemoteCameraConnectManager.instance().release();
	}

	@Override
	public boolean onBackPressed() {
		Log.i(TAG,"onBackPressed()");
		if (mFragmentNormal.getVisibility() == View.GONE) {
			showDvrSetting(false);
			((Activity) getContext()).invalidateOptionsMenu();
			ActionBar bar = ((Activity) getContext()).getActionBar();
			bar.setDisplayHomeAsUpEnabled(false);
			bar.setHomeButtonEnabled(false);
			bar.setTitle(R.string.tab_preview);
			bar.setDisplayShowCustomEnabled(false);			
			return true;
		}
		
		return mQuickVoiceFragment.onBackPressed();
	}
	
	@Override
	public void refresh() {	
		if(mActivate){
			onDeactivate();
			onActivate();
		}else{
			mCameraView.requestDVRSDcardStatus();
			mCameraView.requestDVRRecordStatus();
		}
		mQuickVoiceFragment.refresh();
		if (RemoteCameraConnectManager.supportNewSetting()) {
			mQuickSettingFragment2.refreshSetting();
		} else
			mQuickSettingFragment.refreshSetting();	
	}

	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case 0:
				if(resultCode == Activity.RESULT_OK){
					double latitude = data.getDoubleExtra(MapSelectActivity.KEY_LATITUDE, 0);
					double longitude = data.getDoubleExtra(MapSelectActivity.KEY_LONGITUDE, 0);
					String name = data.getStringExtra(MapSelectActivity.KEY_NAME);
					mQuickVoiceFragment.startNavi(latitude, longitude, name);
				}
				break;
			case CarAssistMainView.SCANNIN_GREQUEST_CODE:
				if (resultCode == Activity.RESULT_OK) {
					Bundle bundle = data.getExtras();
					Log.i(TAG, bundle.getString("result"));
					String scanResult = bundle.getString("result");
					if(scanResult.startsWith("http")){
						final String sn;
						if(scanResult.indexOf("?sn=") != -1 && scanResult.indexOf("&online") != -1)
							sn = scanResult.substring(
									scanResult.indexOf("?sn=") + "?sn=".length(), scanResult.indexOf("&"));
						else if(scanResult.indexOf("?sn=") != -1 && scanResult.indexOf("&online") == -1)
							sn = scanResult.substring(
									scanResult.indexOf("?sn=") + "?sn=".length());
						else
							sn = null;
						//check ssid & pwd
						String ssidAp = "";
						String pwdAp = "";
						Map<String, String> mapRequest = Util.URLRequest(scanResult);
						for(String strRequestKey: mapRequest.keySet()) {
				            String strRequestValue=mapRequest.get(strRequestKey);
				            if(strRequestKey.equals("ssid"))
				            	ssidAp = strRequestValue;
				            if(strRequestKey.equals("pwd"))
				            	pwdAp = strRequestValue;
				        }
						
						Log.d(TAG, "ssid:" + ssidAp + " pwdAp:" + pwdAp);
						if(ssidAp.length() != 0){
							//connect softap
							mWifiAdmin = new WifiAdmin(getContext()){
								
								@Override
								public Intent myRegisterReceiver(BroadcastReceiver receiver, IntentFilter filter) {
									getContext().registerReceiver(receiver, filter);  
			                        return null;  
								}
								@Override
								public void myUnregisterReceiver(BroadcastReceiver receiver) {
									// TODO Auto-generated method stub
									getContext().unregisterReceiver(receiver);
								}
								@Override
								public void onNotifyWifiConnected() {
									// TODO Auto-generated method stub
									mHandler.post(new Runnable() {
										
										@Override
										public void run() {
											// TODO Auto-generated method stub
											Log.v(TAG, "have connected success!");
											mScanRecorderDialog.dismiss();
											Toast.makeText(getContext(), R.string.connect_ap_success, 
						                    		Toast.LENGTH_SHORT).show();
											if(sn != null){
												RemoteCameraConnectManager.instance().setAutoConnectSerial(sn);
											}
										}
									});
									
								}
								@Override
								public void onNotifyWifiConnectFailed() {
									// TODO Auto-generated method stub
									mHandler.post(new Runnable() {
										
										@Override
										public void run() {
											// TODO Auto-generated method stub
											Log.v(TAG, "have connected failed!");
											mScanRecorderDialog.dismiss();
											Toast.makeText(getContext(), R.string.connect_ap_fail, 
						                    		Toast.LENGTH_SHORT).show();
										}
									});
									
								}
							};
							//mWifiAdmin.openWifi();
							if(mWifiManager.isWifiEnabled() == false){
								Toast.makeText(getContext(), getContext().getString(R.string.msg_bond_device_wifi_isoff), Toast.LENGTH_SHORT).show();
								return;
							}else{
								Log.d(TAG, "start addNetwork");
								mScanRecorderDialog.show();
								if(pwdAp.length() != 0)
									mWifiAdmin.addNetwork(mWifiAdmin.createWifiInfo(ssidAp, pwdAp, WifiAdmin.TYPE_WPA));
								else
									mWifiAdmin.addNetwork(mWifiAdmin.createWifiInfo(ssidAp, "", WifiAdmin.TYPE_NO_PASSWD));
							}
						}else{
							Toast.makeText(getContext(), R.string.open_recorder_ap_tip, 
		                    		Toast.LENGTH_SHORT).show();
						}
					}
				}
				break;
		}
		
	}
	
	private void initView(){
		
		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.camera_preview_view, this);
        
        mCameraView = (CameraView)findViewById(R.id.camera_view);
        RemoteCameraConnectManager.create(getContext(), this);

        mFragmentNormal = (LinearLayout)findViewById(R.id.fragment_normal);
         
        mAboutFragment = (AboutFragment)findViewById(R.id.about_fragment);
        mAboutFragment.setCameraPreviewView(this);
        mQuickVoiceFragment = (QuickVoiceFragment)findViewById(R.id.quick_voice_fragment);
        mQuickSettingFragment = (QuickSettingFragment)findViewById(R.id.quick_setting_fragment);
        mQuickSettingFragment.setCameraPreviewView(this);
        
        mQuickTrackFragment = (QuickTrackFragment)findViewById(R.id.quick_track_fragment);
        mFragmentMap.put(mQuickVoiceFragment, 0);
        mFragmentMap.put(mQuickSettingFragment, 1);
        //mFragmentMap.put(mQuickSettingFragment2, 2);
        mFragmentMap.put(mAboutFragment, 3);
        mFragmentMap.put(mQuickTrackFragment, 4);
        
        mCameraView.setQuickTrackFragment(mQuickTrackFragment);
        
        mTab1 = (RelativeLayout)findViewById(R.id.tab1);
        mTab2 = (RelativeLayout)findViewById(R.id.tab2);
        
        mTabRadioGroup2 = (RadioGroup) findViewById(R.id.fragmen_tab2);
        ((RadioButton)findViewById(R.id.about_button)).setChecked(true);
        mTabRadioGroup2.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {  
            @Override  
            public void onCheckedChanged(RadioGroup group, int checkedId) { 
            	View oldFragment = mCurrentFragment;
                switch(checkedId){
                	case R.id.about_button:
                		mCurrentFragment = mAboutFragment;
                		break;
                	case R.id.track2_button:
                		mCurrentFragment = mQuickTrackFragment;
                		break;
                }
                if(mFragmentMap.get(oldFragment) < mFragmentMap.get(mCurrentFragment)){
                	oldFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_exit_left));
                	mCurrentFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_enter_left));
                }else{
                	oldFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_exit_right));
                	mCurrentFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_enter_right));
                }
                oldFragment.setVisibility(View.GONE);
            	mCurrentFragment.setVisibility(View.VISIBLE);
            }
        });        
        
        mTabRadioGroup = (RadioGroup) findViewById(R.id.fragmen_tab);
        ((RadioButton)findViewById(R.id.voice_button)).setChecked(true);            
        mTabRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {  
            @Override  
            public void onCheckedChanged(RadioGroup group, int checkedId) { 
            	View oldFragment = mCurrentFragment;
                switch(checkedId){
                	case R.id.voice_button:
                		mCurrentFragment = mQuickVoiceFragment;
                		break;
                	case R.id.setting_button: {
               			mCurrentFragment = mQuickSettingFragment;                		
                	}
                		break;
                	case R.id.track_button:
                		mCurrentFragment = mQuickTrackFragment;
                		break;
                }
                if(mFragmentMap.get(oldFragment) < mFragmentMap.get(mCurrentFragment)){
                	oldFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_exit_left));
                	mCurrentFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_enter_left));
                }else{
                	oldFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_exit_right));
                	mCurrentFragment.setAnimation(AnimationUtils.loadAnimation(getContext(), 
                			R.anim.fragment_enter_right));
                }
                oldFragment.setVisibility(View.GONE);
            	mCurrentFragment.setVisibility(View.VISIBLE);
            }  
        });        	        

        boolean newSetting = getContext().getSharedPreferences("newsetting", 0).getBoolean("newtab", false);
        if (newSetting) {
        	mTab1.setVisibility(View.GONE);
        	mTab2.setVisibility(View.VISIBLE);
        	mUsingTab1 = false;
            mCurrentFragment = mAboutFragment;    
        } else {
        	mTab1.setVisibility(View.VISIBLE);
        	mTab2.setVisibility(View.GONE);
        	mUsingTab1 = true;
        	mCurrentFragment = mQuickVoiceFragment;
        }
         
        mCurrentFragment.setVisibility(View.VISIBLE);
        
        mSoftAPState = RemoteCameraConnectManager.instance().getNetworkListener().getWifiApState();
        
        IntentFilter mIntentFilter = new IntentFilter(WifiHideAPI.WIFI_AP_STATE_CHANGED_ACTION);
		getContext().registerReceiver(mBroadcastReceiver, mIntentFilter);
		
		mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
		
		mScanRecorderDialog = new ProgressDialog(getContext());
		mScanRecorderDialog.setMessage(getContext().getString(R.string.connecting_ap));
	}
	
	private void handleWifiApStateChanged(int state) {
		switch (state) {
			case WifiHideAPI.WIFI_AP_STATE_ENABLING:
				mSoftAPState = WifiHideAPI.WIFI_AP_STATE_ENABLING;
				if(Build.VERSION.SDK_INT >= 14)
					((Activity) getContext()).invalidateOptionsMenu();
				break;
			case WifiHideAPI.WIFI_AP_STATE_ENABLED:
				if(!mFirstWifiApState)
					Toast.makeText(getContext(), R.string.tip_softap_open, Toast.LENGTH_SHORT).show();
				mSoftAPState = WifiHideAPI.WIFI_AP_STATE_ENABLED;
				if(Build.VERSION.SDK_INT >= 14)
					((Activity) getContext()).invalidateOptionsMenu();
				break;
			case WifiHideAPI.WIFI_AP_STATE_DISABLING:
				mSoftAPState = WifiHideAPI.WIFI_AP_STATE_DISABLING;
				if(Build.VERSION.SDK_INT >= 14)
					((Activity) getContext()).invalidateOptionsMenu();
				break;
			case WifiHideAPI.WIFI_AP_STATE_DISABLED:
				if(!mFirstWifiApState)
					Toast.makeText(getContext(), R.string.tip_softap_close, Toast.LENGTH_SHORT).show();
				mSoftAPState = WifiHideAPI.WIFI_AP_STATE_DISABLED;
				if(Build.VERSION.SDK_INT >= 14)
					((Activity) getContext()).invalidateOptionsMenu();
				break;
			default:
		}
		mFirstWifiApState = false;
	}
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			String action = arg1.getAction();
			if (WifiHideAPI.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
				handleWifiApStateChanged(arg1.getIntExtra(WifiHideAPI.EXTRA_WIFI_AP_STATE,
						WifiHideAPI.WIFI_AP_STATE_FAILED));
			}
		}
		
	};
}
