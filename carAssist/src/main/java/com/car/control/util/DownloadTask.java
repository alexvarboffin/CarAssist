package com.car.control.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import android.graphics.Bitmap;
import android.util.Log;

import com.car.control.Config;
import com.car.control.dvr.RemoteCameraConnectManager;
import com.media.tool.MediaProcess;
import com.media.tool.MediaProcess.Listener;


public class DownloadTask implements Runnable, Listener{
	private static final String TAG = "CarSvc_DownloadTask";
	
	private String mFilePath;
	private String mFullPathName;
	private HttpDownloadManager.OnDownloadListener mListener;
	private boolean mCancel = false;
	private boolean mDownloadSuccess = false;
	private int mProgress = 0;
	private String mUrl = null;
	private String mDownloadSavePath = null;

	private MediaProcess mMediaProcess = null;
	
	private boolean mDeleteAfterDownload = false;
	
	public DownloadTask(String filePath, HttpDownloadManager.OnDownloadListener listener){
		mFilePath = filePath;
		mListener = listener;
	}
	
	public DownloadTask(String filePath, HttpDownloadManager.OnDownloadListener listener
			,String url, String savepath){
		mFilePath = filePath;
		mListener = listener;
		mUrl = url;
		mDownloadSavePath = savepath;
	}
	
	public DownloadTask cloneTask(){
		return new DownloadTask(mFilePath, mListener, mUrl, mDownloadSavePath);
	}
	
	public void  cancelDownload(){
		if(!mCancel){
			mCancel = true;
			if(mMediaProcess != null){
				mMediaProcess.stop();
			}
		}
	}
	
	public String getFilePath(){
		return mFilePath;
	}
	
	public String getPathName(){
        return mFullPathName;
    }
	
	public int getProgress(){
		return mProgress;
	}
	
	public void setListener(HttpDownloadManager.OnDownloadListener listener){
		mListener = listener;
	}
	
	public void setDeleteAfterDownload(boolean delete){
		mDeleteAfterDownload = delete;
	}

	@Override
	public void run() {
		if(mListener != null){
			mListener.onDownloadStart(this);
			mListener.onDownloadProgress(this, 0);
		}
		HttpURLConnection urlConnection = null; 
		OutputStream output=null; 
		try {
			String strUrl = null;
			if(mUrl == null)
				strUrl = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + RemoteCameraConnectManager.HTTP_SERVER_PORT + 
					"/cgi-bin/Config.cgi?action=download&property=path&value=" + URLEncoder.encode(mFilePath, "UTF-8");
			else{
				strUrl = mUrl;
			}
		    
            if(mFilePath.endsWith(".ts") || mFilePath.endsWith(".tstmp")){ 
    		    String pathName = null;
    		    String tmpName = null;
    		    if(mDownloadSavePath == null){
    		    	pathName = Config.CARDVR_PATH + mFilePath;
    		    	tmpName = pathName + ".tmp";
    		    }else{
    		    	pathName = mDownloadSavePath + "/" + mFilePath;
    		    	tmpName = pathName + ".tmp";
    		    }
    		    if(mFilePath.endsWith(".ts"))
    		        pathName = pathName.substring(0, pathName.lastIndexOf(".ts")) + ".mp4";
    		    else if(mFilePath.endsWith(".tstmp"))
    		        pathName = pathName.substring(0, pathName.lastIndexOf(".tstmp")) + ".mp4";
    		    
    		    Log.i(TAG,"pathName = " + pathName);
    		    mFullPathName = pathName;
    		    
	            File file = new File(pathName);
	            File tmpFile = new File(tmpName);
	            if(file.exists()){  
	                Log.i(TAG,"file exists,delete it"); 
	                file.delete(); 
	            }
	            
	            if(tmpFile.exists()){
	            	tmpFile.delete();
	            }
	            
	            String dir = file.getParentFile().getAbsolutePath(); 
	            Log.i(TAG,"dir = " + dir);
	            File dirFile = new File(dir);
	            if(!dirFile.exists())
	            	dirFile.mkdirs();
	            mMediaProcess = new MediaProcess(MediaProcess.CONVERT);
	            mMediaProcess.setInputFile(strUrl);
	            mMediaProcess.setOutFile(tmpName);
	            mMediaProcess.setListener(this);
	            //native block here
	            int ret = mMediaProcess.start();
	            
	            mMediaProcess.destroy();
                mMediaProcess = null;

	            if(ret != -1){
	            	tmpFile.renameTo(file);
	            	if(mListener != null)
	            		mListener.onDownloadEnd(this, true);
	            	
	            	if(mDeleteAfterDownload && mFilePath != null)
	            		doDeleteFile(mFilePath);
	            }else{
	            	if(mListener != null)
	            		mListener.onDownloadEnd(this, false);
	            }
	            
	            if(tmpFile.exists()){
	            	tmpFile.delete();
	            }
                
                
            }else{
            	URL url = new URL(strUrl);  
    		    urlConnection = (HttpURLConnection)url.openConnection();   
    		    String pathName = null;
    		    String tmpName = null;
    		    if(mDownloadSavePath == null){
    		    	pathName = Config.CARDVR_PATH + mFilePath;
    		    	tmpName = pathName + ".tmp";
    		    }else{
    		    	pathName = mDownloadSavePath + "/" + mFilePath;
    		    	tmpName = pathName + ".tmp";
            	}	
	            Log.d(TAG,"pathName = " + pathName);
	            mFullPathName = pathName;
	            File file = new File(pathName);
	            File tmpFile = new File(tmpName);
	            InputStream input = urlConnection.getInputStream();  
	            if(file.exists()){  
	                Log.i(TAG,"file exists,delete it"); 
	                file.delete(); 
	            }
	            
	            if(tmpFile.exists()){
	            	tmpFile.delete();
	            }
	            
	            String dir = file.getParentFile().getAbsolutePath(); 
	            Log.i(TAG,"dir = " + dir);
	            File dirFile = new File(dir);
	            if(!dirFile.exists())
	            	dirFile.mkdirs();
	            //file.createNewFile();
	            tmpFile.createNewFile();
	            output = new FileOutputStream(tmpFile);
	
	            byte[] buffer = new byte[1024];      
	            int total = urlConnection.getContentLength();
	            int current = 0;
	            int percent = 0;
	            int p;
	            int len = input.read(buffer);
	            while(len != -1 && !mCancel){
	                output.write(buffer, 0, len);
	                current = current + len;
	                p = (int)(((current*1.0f)/total)*100);
	                if(p > percent){
	                	percent = p;
	                	mProgress = percent;
	                	if(mListener != null)
	                		mListener.onDownloadProgress(this, percent);
	                }
	                len = input.read(buffer);
	            }  
	            output.flush();
	            output.close();
	            input.close();
	            if(mCancel){
	            	if(mListener != null)
	            		mListener.onDownloadEnd(this, false);
	            }else{
	            	tmpFile.renameTo(file);
	            	mProgress = 100;
	            	if(mListener != null){
			            mListener.onDownloadProgress(this, 100);
			            mListener.onDownloadEnd(this, true);
	            	}
	            	if(mDeleteAfterDownload && mFilePath != null)
	            		doDeleteFile(mFilePath);
	            }
	            
	            if(tmpFile.exists()){
	            	tmpFile.delete();
	            }
            }
		} catch (MalformedURLException e) {  
		    e.printStackTrace(); 
		    if(mListener != null)
		    	mListener.onDownloadEnd(this, false);
		} catch (IOException e) {  
		    e.printStackTrace();
		    if(mListener != null)
		    	mListener.onDownloadEnd(this, false);
		} finally { 
			if(urlConnection != null)
				urlConnection.disconnect();
			HttpDownloadManager.instance().cancelDownload(this);
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
			}
			
		});
		
	}
	

	@Override
	public void MediaProcessCallback(int msgType, int value, Bitmap bmp) {
		if(msgType == MediaProcess.CONVERT) {
			if(mListener != null)
				mListener.onDownloadProgress(this, value);
			if(value == 100)
				mDownloadSuccess = true;
		} else {
			//no need message
		} 
	}
}
