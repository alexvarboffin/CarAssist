
package com.car.control.dvr;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.car.control.Config;
import com.car.control.R;
import com.car.control.util.HttpRequestManager;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout.LayoutParams;

public class QuickSettingFragment extends RelativeLayout{
	
	private static final String TAG = "TAG_QuickSettingFragment";
	
	LinearLayout mVoiceWakeupLayout;
	TextView mDvrMore;
	CameraPreviewView mCameraPreviewView;
	private SeekBar mVolumeSeekBar;
	private SeekBar mBrightnessSeekBar;
	private RadioGroup mWakeUpRadioGroup;
	private RadioButton mCloseRadioButton;
	private RadioButton mLowRadioButton;
	private RadioButton mMidRadioButton;
	private RadioButton mHighRadioButton;
	private Handler mHandler = new Handler();
	
	public QuickSettingFragment(Context context) {
		super(context);
		initView();
	}

	public QuickSettingFragment(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public QuickSettingFragment(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}
	
	public void refreshSetting(){
		if(RemoteCameraConnectManager.supportWebsocket()){
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
		}else{
			String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
					"/cgi-bin/Config.cgi?action=get&property=Setting.Status.*&property=CarDvr.Status.Ability";
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
							if(str.startsWith(Config.PROPERTY_SETTING_STATUS_BRIGHTNESS)){
								String brightness[] = str.split("=");
								String strs[] = brightness[1].split(",");
								int min = Integer.parseInt(strs[0].split(":")[1]);
								int max = Integer.parseInt(strs[1].split(":")[1]);
								int current = Integer.parseInt(strs[2].split(":")[1]);
								setBrightnessStatue(min, max, current);
							}else if(str.startsWith(Config.PROPERTY_SETTING_STATUS_VOLUME)){
								String volume[] = str.split("=");
								String strs[] = volume[1].split(",");
								int min = Integer.parseInt(strs[0].split(":")[1]);
								int max = Integer.parseInt(strs[1].split(":")[1]);
								int current = Integer.parseInt(strs[2].split(":")[1]);
								setVolumeStatue(min, max, current);
							}else if(str.startsWith(Config.PROPERTY_SETTING_STATUS_WAKE_UP)){
								String param = str.split("=")[1];
								int value = Integer.parseInt(param);
								setWakeUpStatue(value);
							}else if(str.startsWith(Config.PROPERTY_CARDVR_STATUS_ABILITY)){
								String ability = str.split("=")[1];
								setAbilityStatue(ability);
							}
						}catch(Exception e){
							Log.i(TAG,"Exception",e);
						}
					}
				}
				
			});
		}
	}
	
	//根据属性显示或者隐藏控件
	public void setAbilityStatue(String ability){
		if(ability != null && ability.contains("voice")){
			mHandler.post(new Runnable(){

				@Override
				public void run() {
					mVoiceWakeupLayout.setVisibility(View.VISIBLE);
				}
				
			});
		}else{
			mHandler.post(new Runnable(){

				@Override
				public void run() {
					mVoiceWakeupLayout.setVisibility(View.GONE);
				}
				
			});
		}
	}
		
	//设置声音
	public void setVolumeStatue(int min, int max, int current){		
		mVolumeSeekBar.setMax(max);
		mVolumeSeekBar.setProgress(current);
	}
		
	//设置亮度
	public void setBrightnessStatue(int min, int max, int current){
		mBrightnessSeekBar.setMax(max);
		mBrightnessSeekBar.setProgress(current);
	}
		
	//设置唤醒灵敏度
	public void setWakeUpStatue(final int value){
		mHandler.post(new Runnable(){

			@Override
			public void run() {
				switch(value){
					case 0:
						mCloseRadioButton.setChecked(true);
						break;
					case 1:
						mLowRadioButton.setChecked(true);
						break;
					case 2:
						mMidRadioButton.setChecked(true);
						break;
					case 3:
						mHighRadioButton.setChecked(true);
						break;
				}
			}
			
		});
	}
	
	public void showDvrMore(boolean show) {
		mDvrMore.setVisibility(show? View.VISIBLE: View.GONE);
	}
	public void setCameraPreviewView(CameraPreviewView cpv) {
		mCameraPreviewView = cpv;
	}
	
	private void setActionBarMidtitleAndUpIndicator(int midtitleRes, int upRes){
		ActionBar bar = ((Activity) getContext()).getActionBar();
		bar.setDisplayHomeAsUpEnabled(true);
		if( Build.VERSION.SDK_INT >= 18)
			bar.setHomeAsUpIndicator(upRes);
		bar.setTitle(R.string.tab_preview);
		bar.setDisplayShowTitleEnabled(true);
		bar.setDisplayShowHomeEnabled(false);
		TextView textview = new TextView(getContext());
		textview.setText(midtitleRes);
		textview.setTextColor(Color.WHITE);
		textview.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.title_size));
		bar.setCustomView(textview, new ActionBar.LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		bar.setDisplayShowCustomEnabled(true);
	}	
	
	private void initView() {
		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.quick_setting_fragment, this);
        
        mVoiceWakeupLayout = (LinearLayout)findViewById(R.id.linearlayout);
        
        mDvrMore = (TextView)findViewById(R.id.dvrmore);
        mDvrMore.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
					Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
					return;
				}				
				mCameraPreviewView.showDvrSetting(true);		
				((Activity) getContext()).invalidateOptionsMenu();
				setActionBarMidtitleAndUpIndicator(R.string.dvrset, R.drawable.back);					
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
						jso.put("action", "set");
						JSONObject items = new JSONObject();
						items.put(Config.PROPERTY_SETTING_STATUS_VOLUME, seekBar.getProgress());
						jso.put("list", items);
						jso.toString();
						Log.i(TAG,"jso.toString() = " + jso.toString());
						HttpRequestManager.instance().requestWebSocket(jso.toString());
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else{
					String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
							"/cgi-bin/Config.cgi?action=set&property=Setting.Status.Volume&value=" + seekBar.getProgress();
					Log.i(TAG,"url = " + url);
					HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){
	
						@Override
						public void onHttpResponse(String result) {
							// TODO Auto-generated method stub
							Log.i(TAG, "result = " + result);
						}
						
					});
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
						jso.put("action", "set");
						JSONObject items = new JSONObject();
						items.put(Config.PROPERTY_SETTING_STATUS_BRIGHTNESS, seekBar.getProgress());
						jso.put("list", items);
						jso.toString();
						Log.i(TAG,"jso.toString() = " + jso.toString());
						HttpRequestManager.instance().requestWebSocket(jso.toString());
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else{
					String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
							"/cgi-bin/Config.cgi?action=set&property=Setting.Status.Brightness&value=" + seekBar.getProgress();
					Log.i(TAG,"url = " + url);
					HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){
	
						@Override
						public void onHttpResponse(String result) {
							// TODO Auto-generated method stub
							Log.i(TAG, "result = " + result);
						}
						
					});
				}
			}
        	
        });
        
        mWakeUpRadioGroup = (RadioGroup)findViewById(R.id.radio_group);
        mWakeUpRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener(){

			@Override
			public void onCheckedChanged(RadioGroup arg0, int arg1) {
				int id = arg0.getCheckedRadioButtonId();
				int value = 0;
				switch(id){
					case R.id.close:
						Log.i(TAG,"mWakeUpRadioGroup:CLOSE");
						value = 0;
						break;
					case R.id.low:
						Log.i(TAG,"mWakeUpRadioGroup:LOW");
						value = 1;
						break;
					case R.id.mid:
						Log.i(TAG,"mWakeUpRadioGroup:MID");
						value = 2;
						break;
					case R.id.high:
						Log.i(TAG,"mWakeUpRadioGroup:HIGH");
						value = 3;
						break;
				}
				if(RemoteCameraConnectManager.supportWebsocket()){
					try {
						JSONObject jso = new JSONObject();
						jso.put("action", "set");
						JSONObject items = new JSONObject();
						items.put(Config.PROPERTY_SETTING_STATUS_WAKE_UP, value);
						jso.put("list", items);
						jso.toString();
						Log.i(TAG,"jso.toString() = " + jso.toString());
						HttpRequestManager.instance().requestWebSocket(jso.toString());
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else{
					String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
							"/cgi-bin/Config.cgi?action=set&property=Setting.Status.Wake.Up&value=" + value;
					Log.i(TAG,"url = " + url);
					HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){
	
						@Override
						public void onHttpResponse(String result) {
							// TODO Auto-generated method stub
							Log.i(TAG, "result = " + result);
						}
						
					});
				}
			}
        	
        });
        
        mVoiceWakeupLayout.setVisibility(View.GONE);
        
        mCloseRadioButton = (RadioButton)findViewById(R.id.close);
        mLowRadioButton = (RadioButton)findViewById(R.id.low);
    	mMidRadioButton = (RadioButton)findViewById(R.id.mid);
    	mHighRadioButton = (RadioButton)findViewById(R.id.high);
	}
}
