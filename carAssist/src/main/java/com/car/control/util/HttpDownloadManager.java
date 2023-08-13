
package com.car.control.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class HttpDownloadManager {
	
	private static final String TAG = "CarSvc_HttpDownloadManager";
	
	private HandlerThread mWorkThread;
	private Handler mWorkHandler;
	private List<DownloadTask> mTaskLists = new ArrayList<DownloadTask>();
	private static HttpDownloadManager sIns;
	
	private HttpDownloadManager(){
		mWorkThread = new HandlerThread("HttpDownload");
		mWorkThread.start();
		mWorkHandler = new Handler(mWorkThread.getLooper());
	}
	
	public static void create(){
		sIns =  new HttpDownloadManager();
	}
	
	public static void destory(){
		synchronized (sIns.mTaskLists) {
			for(DownloadTask task : sIns.mTaskLists){
				task.cancelDownload();
				sIns.mWorkHandler.removeCallbacks(task);
			}
			
			sIns.mTaskLists.clear();
		}
		sIns.mWorkThread.quit();
	}
	
	public static HttpDownloadManager instance(){
		return sIns;
	}
	
	public void requestDownload(DownloadTask task){
		synchronized (mTaskLists) {
			mTaskLists.add(task);
		}
		mWorkHandler.post(task);
	}
	
	public void requestMultiDownload(DownloadTask task){
	    synchronized (mTaskLists) {
            mTaskLists.add(task);
        }   
        Thread thread = new Thread(task);
        thread.start();
	}
	
	public void requestDownloadWithNoAddToList(DownloadTask task){
		mWorkHandler.post(task);
	}
	
	public void cancelDownload(DownloadTask task){
		synchronized (mTaskLists) {
			mTaskLists.remove(task);
		}
		
		task.cancelDownload();
		mWorkHandler.removeCallbacks(task);
	}
	
	public DownloadTask getDownloadTask(String filePath){
		synchronized (mTaskLists) {
			for(DownloadTask task : mTaskLists){
				if(task.getFilePath().equals(filePath))
					return task;
			}
		}
		return null;
	}
	
	//判断文件夹下面的文件有无正在下载的文件
	public boolean hasDownloadingFile(String filePath){
		synchronized (mTaskLists) {
			for(DownloadTask task : mTaskLists){
				if(task.getFilePath().startsWith(filePath))
					return true;
			}
		}
		return false;
	}
	
	//取消文件夹下面的所有文件下载
	public void cancelDownloadInDir(String filePath){
		synchronized (mTaskLists) {
			DownloadTask task = null;
			for (Iterator<DownloadTask> iter = mTaskLists.iterator();iter.hasNext();) {
				task = iter.next();
				if(task.getFilePath().startsWith(filePath)){
					iter.remove();
					task.cancelDownload();
					mWorkHandler.removeCallbacks(task);
				}
			}
		}
		
	}
	
	public interface OnDownloadListener{
		public void onDownloadStart(DownloadTask task);
		public void onDownloadEnd(DownloadTask task, boolean succeed);
		public void onDownloadProgress(DownloadTask task, int progress);
	}
}


