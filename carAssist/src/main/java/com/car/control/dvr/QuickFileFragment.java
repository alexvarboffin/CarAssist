
package com.car.control.dvr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class QuickFileFragment extends RelativeLayout implements View.OnClickListener {
	private static final String TAG = "CarSvc_QuickFileFrag";
	
	private Bitmap mIcon_folder;
	private ImageView mLockImage;
	private ImageView mCaptureImage;
	private ImageView mLoopImage;
	
	private boolean mSyncFileAuto = true;
	
	public QuickFileFragment(Context context) {
		super(context);
		initView();
	}

	public QuickFileFragment(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public QuickFileFragment(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}
	
	public void refresh(){
		refreshAllImageView();
		syncFile();
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        mSyncFileAuto = true;//sp.getBoolean(SettingView.KEY_SYNC_CAPTURE, true);
	}
	
	public void onDestroy(){

	}
	
	public void setSyncFile(final String path, final String type, final List<FileInfo> list){
		Log.i(TAG, "setSyncFile : path = " + path + ",list = " + list);
		
		if(type.equals("new")){
			if(mSyncFileAuto){
				for(FileInfo fi : list){
					if(!fi.isDirectory)
						downloadFile(fi);
				}
			}
		}else if(type.equals("all")){
			if(mSyncFileAuto){
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
	
	@Override
	public void onClick(View v) {
		if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
			Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
			return;
		}
		
		int type = 0;
		switch(v.getId()){
			case R.id.quick_lock_file:
				type = RemoteFileActivity.TYPE_REMOTE_FILE_LOCK;
				break;
			case R.id.quick_capture_file:
				type = RemoteFileActivity.TYPE_REMOTE_FILE_CAPTURE;
				break;
			case R.id.quick_loop_file:
				type = RemoteFileActivity.TYPE_REMOTE_FILE_LOOP;
				break;
		}
		Intent intent = new Intent(getContext(), RemoteFileActivity.class);
		intent.putExtra(RemoteFileActivity.KEY_TYPE_REMOTE_FILE, type);
		getContext().startActivity(intent);
	}

	private void initView() {
		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.quick_file_fragment, this);
        
        findViewById(R.id.quick_lock_file).setOnClickListener(this);
        findViewById(R.id.quick_capture_file).setOnClickListener(this);
        findViewById(R.id.quick_loop_file).setOnClickListener(this);
        
        mLockImage = (ImageView)findViewById(R.id.quick_lock_file_image);
        mCaptureImage = (ImageView)findViewById(R.id.quick_capture_file_image);
        mLoopImage = (ImageView)findViewById(R.id.quick_loop_file_image);
        
        mIcon_folder = BitmapFactory.decodeResource(getContext().getResources(),
				R.drawable.folder);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        mSyncFileAuto = true; //sp.getBoolean(SettingView.KEY_SYNC_CAPTURE, true);
	}
	
	private void refreshAllImageView(){
		refreshImageView(mLockImage, RemoteFileActivity.LOCK_PATH);
		refreshImageView(mCaptureImage, RemoteFileActivity.CAPTURE_PATH);
		refreshImageView(mLoopImage, RemoteFileActivity.LOOP_PATH);
	}
	
	private void refreshImageView(ImageView imageview, String path){
		if(RemoteCameraConnectManager.getCurrentServerInfo() != null){
			String url = "";
			String key = "";
			
			try {
				url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + 
						RemoteCameraConnectManager.HTTP_SERVER_PORT + 
						 "/cgi-bin/Config.cgi?action=thumbnail&property=path&value=" + 
						 URLEncoder.encode(path, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				url = "";
			}

			Glide.with(this.getContext())
					.load(url)
					.placeholder(R.drawable.folder)
					.error(R.drawable.folder)
					.centerCrop()
					.into(imageview);
		}else{
			imageview.setImageBitmap(mIcon_folder);
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
