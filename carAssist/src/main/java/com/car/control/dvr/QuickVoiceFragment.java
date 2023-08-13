
package com.car.control.dvr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.car.control.CarControlActivity;
import com.car.control.Config;
import com.car.control.R;
import com.car.control.browser.FileInfo;
import com.car.control.browser.RemoteFileActivity;
import com.car.control.util.DownloadTask;
import com.car.control.util.HttpDownloadManager;
import com.car.control.util.HttpRequestManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class QuickVoiceFragment extends RelativeLayout {
	
	private static final String TAG = "@@@";
	
	private View mStartRecordView;

	private Handler mHandler = new Handler();
	private AudioManager mAudioManager;
	private PowerManager.WakeLock mWakeLock;
	private String mInputAddress;
	LinearLayout mVoiceLayout, mMapLayout;

	public QuickVoiceFragment(Context context) {
		super(context);
		initView();
	}

	public QuickVoiceFragment(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public QuickVoiceFragment(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}
	
	public void setHeadless(boolean headless) {
		if (headless) {
			mVoiceLayout.setVisibility(View.GONE);
			mMapLayout.setVisibility(View.GONE);
		} else {
			mVoiceLayout.setVisibility(View.VISIBLE);
			mMapLayout.setVisibility(View.VISIBLE);
		}
	}
	
	public void onDestroy(){
	}
	
	public void onPause(){

	}
	
	public void onResume(){
		
	}
	
	public void refresh(){
		syncFile();
	}
	
	public void setSyncFile(final String path, final String type, final List<FileInfo> list){
		Log.i(TAG, "setSyncFile : path = " + path + ",list = " + list);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean syncFileAuto = true; //sp.getBoolean(SettingView.KEY_SYNC_CAPTURE, true);
		if(type.equals("new")){
			if(syncFileAuto){
				for(FileInfo fi : list){
					if(!fi.isDirectory)
						downloadFile(fi);
				}
			}
		}else if(type.equals("all")){
			if(syncFileAuto){
				for(FileInfo fi : list){
					String filePath = fi.path + fi.name; 
	    		    String pathName = null;
	    		    pathName = Config.CARDVR_PATH + filePath;
	    		    if(fi.name.endsWith(".ts"))
	    		    	pathName = pathName.substring(0, pathName.lastIndexOf(".ts")) + ".mp4" ;
	    		    Log.i(TAG,"pathName = " + pathName);
		            File file = new File(pathName);   
		            if(!file.exists()){
		            	if(!fi.isDirectory)
							downloadFile(fi);
		            }
				}
			}
			
	
		}
		
	}
	
	public boolean onBackPressed() {
		Log.i(TAG,"onBackPressed()");
		return false;
	}
	
	public void startNavi(double latitude, double longitude, String name){
		if(RemoteCameraConnectManager.getCurrentServerInfo() != null){
			if(RemoteCameraConnectManager.supportWebsocket()){
				try {
					JSONObject jso = new JSONObject();
					jso.put("action", Config.ACTION_NAVI);
					jso.put("latitude", latitude);
					jso.put("longitude", longitude);
					jso.put("name", name);
					jso.toString();
					Log.i(TAG,"jso.toString() = " + jso.toString());
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else{
				String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
						"/cgi-bin/Config.cgi?action=navi&property=latitude&value="+latitude+"&property=longitude&value="+longitude +
						"&property=name&value="+name;
				Log.i(TAG,"url = " + url);
				HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){
	
					@Override
					public void onHttpResponse(String result) {
						Log.i(TAG, "result = " + result);
					}
					
				});
			}
		}
		else{
			Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
		}
	}

	private void initView() {

		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (inflater != null) {
			inflater.inflate(R.layout.quick_voice_fragment, this);
		}

		mVoiceLayout = (LinearLayout)findViewById(R.id.start_record_container);
        mMapLayout = (LinearLayout)findViewById(R.id.map_layout);

		View mMessageView = findViewById(R.id.input_text);
        mMessageView.setOnClickListener(v -> {
			if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
				Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
				return;
			}

			int type = RemoteFileActivity.TYPE_REMOTE_FILE_CAPTURE;
			Intent intent = new Intent(getContext(), RemoteFileActivity.class);
			intent.putExtra(RemoteFileActivity.KEY_TYPE_REMOTE_FILE, type);
			getContext().startActivity(intent);
		});

		View mLivingButton = findViewById(R.id.camera_living);
        mLivingButton.setOnClickListener(arg0 -> {
			Intent intent = new Intent(getContext(), OSSLivingActivity.class);
			((CarControlActivity)getContext()).startActivityForResult(intent, 0);
		});

    	mAudioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
    	PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
		if (pm != null) {
			mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, this.getClass().getCanonicalName());
		}
	}

	//下载文件
	private void downloadFile(FileInfo info){
		if(!info.isDirectory){
			String filePath = info.path + info.name;
			
			//如果需要下载的文件已经正在下载，先取消下载
			DownloadTask old = HttpDownloadManager.instance().getDownloadTask(filePath);
			if(old != null){
				HttpDownloadManager.instance().cancelDownload(old);
			}	
			DownloadTask task = new DownloadTask(filePath, null);
			task.setDeleteAfterDownload(true);
			HttpDownloadManager.instance().requestDownload(task);
		}
	}
	
	private void syncFile(){
		if(RemoteCameraConnectManager.supportWebsocket()){
			try {
				JSONObject jso = new JSONObject();
				jso.put("action", Config.ACTION_SYNC_FILE);
				Log.i(TAG,"jso.toString() = " + jso.toString());
				HttpRequestManager.instance().requestWebSocket(jso.toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
