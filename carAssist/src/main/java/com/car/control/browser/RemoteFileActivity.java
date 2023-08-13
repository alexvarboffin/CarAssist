
package com.car.control.browser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.car.common.util.FileMediaType;
import com.car.control.BaseActivity;
import com.car.control.CarWebSocketClient.CarWebSocketClientCallback;
import com.car.control.CarWebSocketClient;
import com.car.control.Config;
import com.car.control.R;
import com.car.control.dvr.RemoteCameraConnectManager;
import com.car.control.dvr.UserItem;
import com.car.control.util.DownloadTask;
import com.car.control.util.HttpDownloadManager;
import com.car.control.util.HttpRequestManager;
import com.tonicartos.widget.stickygridheaders.StickyGridHeadersGridView;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class RemoteFileActivity extends BaseActivity implements CarWebSocketClientCallback,
				RemoteCameraConnectManager.OnRemoteFileListChange{
	
	private static final String TAG = "RemoteFileActivity";
	
	public static final String KEY_TYPE_REMOTE_FILE = "key_type_remote_file";
	public static final int TYPE_REMOTE_FILE_LOCK = 1;
	public static final int TYPE_REMOTE_FILE_CAPTURE = 2;
	public static final int TYPE_REMOTE_FILE_LOOP = 3;
	public static final int TYPE_REMOTE_FILE_DOWNLOADING = 4;
	
	public static final String LOCK_PATH = Config.REMOTE_LOCK_PATH;
	public static final String CAPTURE_PATH = Config.REMOTE_CAPTURE_PATH;
	public static final String LOOP_PATH = Config.REMOTE_LOOP_PATH;
	//虚构
	public static final String DOWNLOADING_PATH = "/downloading";
	
	private FileListAdapter mAdapter;
	private StickyGridHeadersGridView mGridView;
	//未加锁，所有对mFileList, mDownloadInfos的操作需在UI线程
	private List<FileInfo> mFileList = new ArrayList<FileInfo>();
	private List<FileInfo> mSelectFileList = new ArrayList<FileInfo>();
	private Map<DownloadTask, FileInfo> mDownloadInfos = new HashMap<DownloadTask, FileInfo>();
	private ProgressBar mProgressBar;
	
	private String mCurrentPath = "";
	
	private boolean mSelectMode = false;
	private TextView mNoFile;
	private int mType;
	private RadioGroup mTabRadioGroup;

	@SuppressLint("NewApi") @Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		setContentView(R.layout.activity_remote_file);
	
		mNoFile = (TextView)findViewById(R.id.remote_no_file);
        mGridView = (StickyGridHeadersGridView) findViewById(R.id.remote_file_gridview);
        
        mGridView.setOnItemClickListener(new ItemClickListener());
		mGridView.setOnItemSelectedListener(new ItemSelectedListener());
		mGridView.setOnItemLongClickListener(new ItemLongClickListener());
		
		mAdapter = new FileListAdapter(this, mFileList, true);
		mGridView.setAdapter(mAdapter);
		
		mTabRadioGroup = (RadioGroup) findViewById(R.id.remote_file_fragmen_tab);
		mTabRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				switch (checkedId) {
				case R.id.remote_file_lock:
					mType = TYPE_REMOTE_FILE_LOCK;
					runFileList(LOCK_PATH);
					break;
				case R.id.remote_file_capture:
					mType = TYPE_REMOTE_FILE_CAPTURE;
					runFileList(CAPTURE_PATH);
					break;
				case R.id.remote_file_loop:
					mType = TYPE_REMOTE_FILE_LOOP;
					runFileList(LOOP_PATH);
					break;
				case R.id.remote_file_downloading:
					mType = TYPE_REMOTE_FILE_DOWNLOADING;
					runFileList(DOWNLOADING_PATH);
					break;
				}
			}
		});

		mProgressBar = (ProgressBar)findViewById(R.id.remote_file_progressbar);
		
		if(Build.VERSION.SDK_INT >= 14){
			ActionBar a = getActionBar();
			if(a != null){
				a.setBackgroundDrawable(getResources().getDrawable(R.drawable.action_bar));
				a.setDisplayHomeAsUpEnabled(true);
				if (Build.VERSION.SDK_INT >= 18)
					a.setHomeAsUpIndicator(R.drawable.back);
				a.setTitle(R.string.back);
				a.setDisplayShowTitleEnabled(true);
				a.setDisplayShowHomeEnabled(false);
			}
		}
		
		mType = getIntent().getIntExtra(KEY_TYPE_REMOTE_FILE, 0);
		switch(mType){
			case TYPE_REMOTE_FILE_LOCK:
				runFileList(LOCK_PATH);
				((RadioButton) findViewById(R.id.remote_file_lock)).setChecked(true);
				break;
			case TYPE_REMOTE_FILE_CAPTURE:
				runFileList(CAPTURE_PATH);
				((RadioButton) findViewById(R.id.remote_file_capture)).setChecked(true);
				break;
			case TYPE_REMOTE_FILE_LOOP:
				runFileList(LOOP_PATH);
				((RadioButton) findViewById(R.id.remote_file_loop)).setChecked(true);
				break;
			case TYPE_REMOTE_FILE_DOWNLOADING:
				runFileList(DOWNLOADING_PATH);
				((RadioButton) findViewById(R.id.remote_file_downloading)).setChecked(true);
			default:
				Log.e(TAG, "wrong type, finish activity");
				finish();
		}
		if(RemoteCameraConnectManager.supportWebsocket())
			CarWebSocketClient.instance().registerCallback(this);
		RemoteCameraConnectManager.instance().addOnRemoteFileListChange(this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(CarWebSocketClient.instance() != null)
			CarWebSocketClient.instance().unregisterCallback(this);
		RemoteCameraConnectManager.instance().removeOnRemoteFileListChange(this);
	}
	
	@Override
	public void onBackPressed() {
		Log.i(TAG,"onBackPressed()");
		if(!mSelectMode){
			finish();
		}else{
			mSelectMode = false;
			mSelectFileList.clear();
			for(FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			invalidateOptionsMenu();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i(TAG, "onCreateOptionsMenu");
		if(!mSelectMode){
			getMenuInflater().inflate(R.menu.car_files, menu);
		}else{
			if(mType != TYPE_REMOTE_FILE_DOWNLOADING){
				getMenuInflater().inflate(R.menu.car_files_multiple, menu);
				MenuItem item = menu.findItem(R.id.car_file_select);
				if(isSelectAll())
					item.setIcon(R.drawable.unselect_all);
				else
					item.setIcon(R.drawable.select_all);
			}else{
				getMenuInflater().inflate(R.menu.car_files_multiple_download, menu);
				MenuItem item = menu.findItem(R.id.car_file_download_select);
				if(isSelectAll())
					item.setIcon(R.drawable.unselect_all);
				else
					item.setIcon(R.drawable.select_all);
			}
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.multiple){
			mSelectMode = true;
			mSelectFileList.clear();
			for(FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			invalidateOptionsMenu();
			return true;
		}else if(item.getItemId() == R.id.refresh){
			mHandler.removeMessages(SHOW_PROGRESSBAR);
			mHandler.sendEmptyMessage(SHOW_PROGRESSBAR);
			RemoteCameraConnectManager.instance().refreshRemoteFileList(mCurrentPath);
			return true;
		}else if(item.getItemId() == R.id.car_file_delete){
			if(RemoteCameraConnectManager.supportWebsocket()){
				JSONObject jso = new JSONObject();
				try {
					jso.put("action", "delete");
					JSONArray array = new JSONArray();
					for(FileInfo fi : mSelectFileList){
						JSONObject file = new JSONObject();
						file.put("name", fi.name);
						file.put("path", fi.path);
						file.put("size", fi.lsize);
						file.put("dir", fi.isDirectory);
						file.put("time", fi.modifytime);
						file.put("sub", fi.sub);
						array.put(file);
					}
					jso.put("list", array);
					Log.i(TAG,"jso.toString() = " + jso.toString());
					HttpRequestManager.instance().requestWebSocket(jso.toString());
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			else{
				for(FileInfo info : mSelectFileList){
					String path = info.path + info.name;
					doDeleteFile(path);
				}
			}
			mSelectMode = false;
			mSelectFileList.clear();
			for(FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			RemoteFileActivity.this.invalidateOptionsMenu();
			return true;
		}else if(item.getItemId() == R.id.car_file_download){
			for(FileInfo info : mSelectFileList){
				if(info.isDirectory)
					downloadFileInDir(info);
				else
					downloadFile(info);
			}
			mSelectMode = false;
			mSelectFileList.clear();
			for(FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			RemoteFileActivity.this.invalidateOptionsMenu();
			RemoteCameraConnectManager.instance().refreshDownloadingFileList();
			return true;
		}else if(item.getItemId() == R.id.car_file_select){
			if(isSelectAll()){
				for(FileInfo info : mFileList)
					info.selected = false;
				mSelectFileList.clear();
				mAdapter.setSelectMode(mSelectMode);
			}else{
				mSelectFileList.clear();
				for(FileInfo info : mFileList){
					if(info.name.equals(".."))
						continue;
					info.selected = true;
					mSelectFileList.add(info);
				}
				mAdapter.setSelectMode(mSelectMode);
			}
			if(Build.VERSION.SDK_INT >= 14)
				invalidateOptionsMenu();
			return true;
		}else if(item.getItemId() == R.id.car_file_download_select){
			if(isSelectAll()){
				for(FileInfo info : mFileList)
					info.selected = false;
				mSelectFileList.clear();
				mAdapter.setSelectMode(mSelectMode);
			}else{
				mSelectFileList.clear();
				for(FileInfo info : mFileList){
					if(info.name.equals(".."))
						continue;
					info.selected = true;
					mSelectFileList.add(info);
				}
				mAdapter.setSelectMode(mSelectMode);
			}
			if(Build.VERSION.SDK_INT >= 14)
				invalidateOptionsMenu();
			return true;
		}else if(item.getItemId() == R.id.car_file_download_cancel){
			for(FileInfo info : mSelectFileList){
				String path = info.path + info.name;
				DownloadTask task = HttpDownloadManager.instance().getDownloadTask(path);
				if(task != null){
					HttpDownloadManager.instance().cancelDownload(task);
				}
			}
			mSelectMode = false;
			mSelectFileList.clear();
			for(FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			RemoteFileActivity.this.invalidateOptionsMenu();
			RemoteCameraConnectManager.instance().refreshDownloadingFileList();
		}else if (item.getItemId() == android.R.id.home) {
			onBackPressed();
		}
		
		return false;
	}
	
	//更新文件列表
	private boolean runFileList(final String filePath) {
		mHandler.removeMessages(SHOW_PROGRESSBAR);
		mHandler.sendEmptyMessage(SHOW_PROGRESSBAR);
		
		/*String url = "/";
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
				mCurrentPath = filePath;
				mHandler.removeMessages(SCAN_FINISHED);
				Message msg = mHandler.obtainMessage(SCAN_FINISHED, list);
				mHandler.sendMessage(msg);
			}
			
		});*/
		if (RemoteCameraConnectManager.instance() != null) {
			List<FileInfo> list = RemoteCameraConnectManager.instance().getRemoteFileList(filePath);
			mCurrentPath = filePath;
			mHandler.removeMessages(SCAN_FINISHED);
			Message msg = mHandler.obtainMessage(SCAN_FINISHED, list);
			mHandler.sendMessage(msg);
		}
		return true;
	}
	
	//下载文件
	private void downloadFile(FileInfo info){
		if(!info.isDirectory){
			String filePath = info.path + info.name;
			
			//如果需要下载的文件已经正在下载，先取消下载
			DownloadTask old = HttpDownloadManager.instance().getDownloadTask(filePath);
			if(old != null){
				HttpDownloadManager.instance().cancelDownload(old);
				FileInfo finfo = mDownloadInfos.get(old);
				if(finfo != null){
					finfo.downloading = false;
					finfo.downloadProgress = 0;
					mAdapter.notifyDataSetChanged();
				}
			}
				
			DownloadTask task = new DownloadTask(filePath, mOnDownloadListener);
			mDownloadInfos.put(task, info);
			info.downloading = true;
			info.downloadProgress = task.getProgress();
			mAdapter.notifyDataSetChanged();
			HttpDownloadManager.instance().requestDownload(task);
		}
	}
	
	//下载文件夹下的所有文件
	private void downloadFileInDir(FileInfo info){
		if(info.isDirectory){
			String filePath = info.path + info.name;
			String url = null;
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
					Message msg = mHandler.obtainMessage(DOWNLOAD_FILE_IN_DIR, list);
					mHandler.sendMessage(msg);
				}
				
			});
		}
	}
	
	//打开文件
	private void openFile(FileInfo info){
		Intent intent = new Intent();
		if(info.fileType == FileMediaType.IMAGE_TYPE){
        	intent = new Intent(this, PhotoActivity.class);
        	intent.putExtra(PhotoActivity.KEY_REMOTE, true);
        	intent.putExtra(PhotoActivity.KEY_PHOTO_CURRENT, info.name);
        	Bundle bundle = new Bundle();
        	bundle.putString(PhotoActivity.KEY_JSON_STRING, getJsonStringForFileList(mFileList));
            intent.putExtras(bundle);
        }else if(info.fileType == FileMediaType.VIDEO_TYPE){
        	intent = new Intent(this, VideoActivity.class);
        	String strUrl = "";
			try {
				strUrl = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
						"/cgi-bin/Config.cgi?action=download&property=path&value=" + URLEncoder.encode(info.getFullPath(), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
	        int type = FileMediaType.getMediaType(info.getFullPath());
	        intent.setDataAndType(Uri.parse(strUrl), FileMediaType.getOpenMIMEType(type));
	        intent.putExtra(VideoActivity.KEY_FILE_TIME, info.modifytime);
	        intent.putExtra(VideoActivity.KEY_FILE_NAME, info.name);
        }else{
	        String strUrl = "";
			try {
				strUrl = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
						"/cgi-bin/Config.cgi?action=download&property=path&value=" + URLEncoder.encode(info.getFullPath(), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}	
	        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        intent.setAction(Intent.ACTION_VIEW);
	        int type = FileMediaType.getMediaType(info.getFullPath());
	        intent.setDataAndType(Uri.parse(strUrl), FileMediaType.getOpenMIMEType(type));
	        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
		
		try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	//删除文件
	private void doDeleteFile(String filePath){
		
		String url = "";
		try {
			url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
					"/cgi-bin/Config.cgi?action=delete&property=path&value=" + URLEncoder.encode(filePath, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		Log.i(TAG,"url = " + url);
		HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener(){

			@Override
			public void onHttpResponse(final String result) {
				Log.i(TAG, "result = " + result);
				if(result == null)
					return;
				mHandler.post(new Runnable(){
					@Override
					public void run() {
						if(result.contains("OK")){
							Toast.makeText(RemoteFileActivity.this, R.string.tip_delete_success, Toast.LENGTH_SHORT).show();
							RemoteCameraConnectManager.instance().refreshRemoteFileList(mCurrentPath);
						}
						else
							Toast.makeText(RemoteFileActivity.this, R.string.tip_delete_fail, Toast.LENGTH_SHORT).show();
						
					}
					
				});
			}
			
		});
		
	}
	
	private String getJsonStringForFileList(List<FileInfo> list){
		Log.i(TAG,"===================");
		Log.i(TAG,"list size = " + list.size());
		for(FileInfo fi : list)
			Log.i(TAG,"" + fi.name);
		Log.i(TAG,"===================");
		
		try {
			JSONArray array = new JSONArray();
			for(FileInfo fi : list){
				if(fi.fileType == FileMediaType.IMAGE_TYPE){
					JSONObject file = new JSONObject();
					file.put("name", fi.name);
					file.put("path", fi.path);
					file.put("size", fi.lsize);
					file.put("dir", fi.isDirectory);
					file.put("time", fi.modifytime);
					file.put("sub", fi.sub);
					array.put(file);
				}
			}
			return array.toString();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return "";

	}
	
	private boolean isSelectAll(){
		return (mFileList.size() != 0 && mFileList.size() == mSelectFileList.size());
	}
	
	private static final int SCAN_FINISHED = 998;
	private static final int SHOW_PROGRESSBAR = 999;
	private static final int DISMISS_PROGRESSBAR = 1000;
	private static final int DOWNLOAD_FILE_IN_DIR = 1001;

	final Handler mHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
				case SCAN_FINISHED:
					mFileList.clear();
					mDownloadInfos.clear();
					@SuppressWarnings("unchecked")
					List<FileInfo> list = (List<FileInfo>)msg.obj;
					if(list.size() > 0){
						mNoFile.setVisibility(View.GONE);
					}else{
						mNoFile.setVisibility(View.VISIBLE);
						switch(mType){
						case TYPE_REMOTE_FILE_LOCK:
							mNoFile.setText(getString(R.string.no_lock_files));
							break;
						case TYPE_REMOTE_FILE_CAPTURE:
							mNoFile.setText(getString(R.string.no_capture_files));
							break;
						case TYPE_REMOTE_FILE_LOOP:
							mNoFile.setText(getString(R.string.no_loop_files));
							break;
						case TYPE_REMOTE_FILE_DOWNLOADING:
							mNoFile.setText(getString(R.string.no_downloading_files));
							break;
						default:
							mNoFile.setText(getString(R.string.no_file));
						}
					}
					mFileList.addAll(list);
					Log.i(TAG,"==========================");
					for(FileInfo info : mFileList){
						Log.i(TAG,"" + info.name);
						info.downloading = false;
						info.downloadProgress = 0;
						String path = info.path + info.name;
						DownloadTask task = HttpDownloadManager.instance().getDownloadTask(path);
						if(task != null){
							task.setListener(mOnDownloadListener);
							mDownloadInfos.put(task, info);
							info.downloading = true;
							info.downloadProgress = task.getProgress();
						}
					}
					Log.i(TAG,"==========================");
					mAdapter.notifyDataSetChanged();
					mProgressBar.setVisibility(View.INVISIBLE);
					mSelectMode = false;
					mSelectFileList.clear();
					for(FileInfo info : mFileList)
						info.selected = false;
					mAdapter.setSelectMode(mSelectMode);
					if(Build.VERSION.SDK_INT >= 14)
						invalidateOptionsMenu();
					break;
				case SHOW_PROGRESSBAR:
					mNoFile.setVisibility(View.GONE);
					mFileList.clear();
					mDownloadInfos.clear();
					mAdapter.notifyDataSetChanged();
					mProgressBar.setVisibility(View.VISIBLE);
					break;
				case DISMISS_PROGRESSBAR:
					mProgressBar.setVisibility(View.INVISIBLE);
					break;
				case DOWNLOAD_FILE_IN_DIR:
					@SuppressWarnings("unchecked")
					List<FileInfo> downlist = (List<FileInfo>)msg.obj;
					for(FileInfo info : downlist){
						if(info.isDirectory)
							downloadFileInDir(info);
						else
							downloadFile(info);
					}
					break;
			}
		}
	};
	
	private class ItemClickListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
			if(position < 0 || position >= mFileList.size())
				return;
			FileInfo finfo = mFileList.get(position);
        	if(!mSelectMode){
	            if (finfo.isDirectory) {
	            	runFileList(finfo.path + finfo.name);
	            } else {
	                openFile(finfo);
	                mAdapter.setCurrentPosition(position);
	            }
        	}else{
        		
        		if(finfo.name.equals("..")){
        			return;
        		}
        		boolean old = isSelectAll();
        		if(mSelectFileList.contains(finfo)){
        			finfo.selected = false;
        			mSelectFileList.remove(finfo);
        			mAdapter.notifyDataSetChanged();
        		}else{
        			finfo.selected = true;
        			mSelectFileList.add(finfo);
        			mAdapter.notifyDataSetChanged();
        		}
        		boolean now = isSelectAll();
        		if(old != now){
        			if(Build.VERSION.SDK_INT >= 14)
        				invalidateOptionsMenu();
        		}
        	}
           
        }
    }
	
	private HttpDownloadManager.OnDownloadListener mOnDownloadListener = new HttpDownloadManager.OnDownloadListener(){

		@Override
		public void onDownloadStart(final DownloadTask task) {
			Log.i(TAG,"onDownloadStart()");
			mHandler.post(new Runnable(){

				@Override
				public void run() {
					//Toast.makeText(getContext(), "开始下载", Toast.LENGTH_SHORT).show();
					FileInfo finfo = mDownloadInfos.get(task);
					if(finfo != null){
						finfo.downloading = true;
						finfo.downloadProgress = task.getProgress();
						mAdapter.notifyDataSetChanged();
					}
				}
				
			});
		}

		@Override
		public void onDownloadEnd(final DownloadTask task, final boolean succeed) {
			Log.i(TAG,"onDownloadEnd:succeed = " + succeed);
			mHandler.post(new Runnable(){

				@Override
				public void run() {
					FileInfo finfo = mDownloadInfos.get(task);
					if(finfo != null){
						finfo.downloading = false;
						finfo.downloadProgress = 0;
						mAdapter.notifyDataSetChanged();
					}
					HttpDownloadManager.instance().cancelDownload(task);
					RemoteCameraConnectManager.instance().refreshDownloadingFileList();
				}
				
			});
		}

		@Override
		public void onDownloadProgress(final DownloadTask task, final int progress) {
			Log.i(TAG,"onDownloadProgress:progress = " + progress);
			mHandler.post(new Runnable(){

				@Override
				public void run() {
					FileInfo finfo = mDownloadInfos.get(task);
					if(finfo != null){
						finfo.downloading = true;
						finfo.downloadProgress = progress;
						mAdapter.notifyDataSetChanged();
					}
				}
				
			});
		}
		
	};

	private class ItemSelectedListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

	private class ItemLongClickListener implements OnItemLongClickListener {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view,
                int position, long id) {
			if(position < 0 || position >= mFileList.size())
				return true;
			FileInfo finfo = mFileList.get(position);
        	if(mSelectMode){
        		mSelectMode = false;
    			mSelectFileList.clear();
    			for(FileInfo info : mFileList)
    				info.selected = false;
    			mAdapter.setSelectMode(mSelectMode);
    			if(Build.VERSION.SDK_INT >= 14)
    				invalidateOptionsMenu();
        	}else{
        		mSelectMode = true;
    			mSelectFileList.clear();
    			for(FileInfo info : mFileList)
    				info.selected = false;
    			mAdapter.setSelectMode(mSelectMode);
    			finfo.selected = true;
    			mSelectFileList.add(finfo);
    			mAdapter.notifyDataSetChanged();
    			if(Build.VERSION.SDK_INT >= 14)
    				invalidateOptionsMenu();
        	}
            return true;
        }
    }

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onError(Exception ex) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetSerialNo(String serial) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetAbilityStatue(String ability) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetVolumeStatue(int min, int max, int current) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetBrightnessStatue(int min, int max, int current) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetWakeUpStatue(int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetVoicePromptStatue(boolean enable) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetDVRRecordStatus(boolean recording) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetDVRSDcardStatus(boolean mount) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDirDVRFiles(String path, JSONArray array) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDeleteDVRFile(final boolean succes) {
		mHandler.post(new Runnable(){
			@Override
			public void run() {
				if(succes){
					Toast.makeText(RemoteFileActivity.this, R.string.tip_delete_success, Toast.LENGTH_SHORT).show();
					RemoteCameraConnectManager.instance().refreshRemoteFileList(mCurrentPath);
				}
				else
					Toast.makeText(RemoteFileActivity.this, R.string.tip_delete_fail, Toast.LENGTH_SHORT).show();
			}
			
		});
	}

	@Override
	public void onSyncFile(String path, String type, List<FileInfo> list) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onRemoteFileListChange(String filePath, List<FileInfo> list) {
		if(filePath.equals(mCurrentPath)){
			mHandler.removeMessages(SCAN_FINISHED);
			Message msg = mHandler.obtainMessage(SCAN_FINISHED, list);
			mHandler.sendMessage(msg);
		}
	}

	@Override
	public void onSetAutoSleepTime(int time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onGsensorSensity(int sensity) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetBrightnessPercent(int percent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onGsensorWakeup(int enable) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onGsensorLock(int enable) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSoftApConfig(String ssid, String pwd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDvrSaveTime(int time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDvrMode(String mode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDvrMute(boolean mute) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDvrGps(boolean show) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSdcardSize(long total, long left, long dvrdir) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUserList(String serialNum, int cloudID, ArrayList<UserItem> list) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onRecordStatus(boolean start, int num, int time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMobileStatus(String imei, boolean ready, int dBm, boolean enable, boolean connected, int type,
			long usage, boolean registered, String flag) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSatellites(boolean enabled, int num, long timestamp, String nmea) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onUpdate(int percent, String version) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCpuInfo(double cpuTemp, double pmuTemp, int core, int freq, boolean isFull, boolean isAccOn) {
	}

	@Override
	public void onGsensor(final float x, final float y, final float z, final boolean passed) {
	}

	@Override
	public void onAdas(String key, boolean value) {
		// TODO Auto-generated method stub
		
	}

}
