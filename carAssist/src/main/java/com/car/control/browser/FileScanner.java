package com.car.control.browser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import com.car.common.util.FileMediaType;
import com.car.common.util.Match4Req;
import com.car.common.util.WorkReq;
import com.car.common.util.WorkThread;
import com.car.control.dvr.RemoteCameraConnectManager;

import android.os.Handler;
import android.util.Log;
import android.util.Xml;

public class FileScanner {
	private static final String TAG = "CarSvc_FileScanner";
	public static final int RESULT_TYPE_SCANNER = 1;
	public static final int RESULT_TYPE_SEARCH = 2;
	public static final int RESULT_TYPE_SORT = 3;
	
	public static final int SORY_BY_NAME = 1;
	public static final int SORY_BY_TIME_UP = 2;
	public static final int SORY_BY_TIME_DOWN = 3;
	public static final int SORY_BY_SIZE_UP = 4;
	public static final int SORY_BY_SIZE_DOWN = 5;
	public static final int SORY_BY_NAME_TIME_DOWN = 6;
	
	private Handler mHandler;
	private WorkThread mScannerThread;
	private static int mSortType = SORY_BY_TIME_DOWN;
	private FileScannerReq mLastScannerReq;

	/**
	 * should override this to get the result. 
	 *Invoked in UI thread.
	 * @param scanPath
	 * @param fileList
	 */
	public void onResult(final int type, final String scanPath, final ArrayList<FileInfo> fileList){
	}
	
	static Comparator<FileInfo> mFileComByName = new Comparator<FileInfo>() {
		public int compare(FileInfo object1, FileInfo object2) {
			return object1.name.compareToIgnoreCase(object2.name);
		}
	};
	static Comparator<FileInfo> mFileComBySizeUp = new Comparator<FileInfo>() {
		public int compare(FileInfo object1, FileInfo object2) {
			if(object1.lsize==object2.lsize){
				return 0;
			}
			return object1.lsize>object2.lsize?1:-1;
		}
	};
	static Comparator<FileInfo> mFileComBySizeDown = new Comparator<FileInfo>() {
		public int compare(FileInfo object1, FileInfo object2) {
			if(object1.lsize==object2.lsize){
				return 0;
			}
			return object1.lsize<object2.lsize?1:-1;
		}
	};
	static Comparator<FileInfo> mFileComByTimeUp = new Comparator<FileInfo>() {
		public int compare(FileInfo object1, FileInfo object2) {
			if(object1.modifytime==object2.modifytime){
				return 0;
			}
			return object1.modifytime>object2.modifytime?1:-1;
		}
	};
	
	static Comparator<FileInfo> mFileComByTimeDown = new Comparator<FileInfo>() {
		public int compare(FileInfo object1, FileInfo object2) {
			if(object1.modifytime==object2.modifytime){
				return 0;
			}
			return object1.modifytime<object2.modifytime?1:-1;
		}
	};
	
	static Comparator<FileInfo> mFileComByNameTimeDown = new Comparator<FileInfo>() {
		public int compare(FileInfo object1, FileInfo object2) {
			String name1 = object1.name.substring(0, object1.name.indexOf('.'));
			name1 = name1.replaceAll("[^0-9]", "");
			String name2 = object2.name.substring(0, object2.name.indexOf('.'));
			name2 = name2.replaceAll("[^0-9]", "");
			return name2.compareTo(name1);
		}
	};

	public FileScanner(){
		mHandler = new Handler();
	}
	
	private  class FileScannerReq implements WorkReq,Match4Req{

		public String mScanPath;
		public Handler mHandler;
		public int mListType;
		private boolean mCancel = false;
		private boolean mNeedUp = false;
		
		public FileScannerReq (String path , Handler handler, int listType, boolean needUp){
			mScanPath = path;
			mHandler = handler;
			mListType = listType;
			mNeedUp = needUp;
		}
		
		@Override
		public boolean matchs(WorkReq req) {
			if(req instanceof FileScannerReq){
				FileScannerReq req2 = (FileScannerReq) req;
				if(mScanPath.equals(req2.mScanPath) && mListType==req2.mListType){
					return true;
				}
			}
			return false;
		}

		@Override
		public void execute() {
			final ArrayList<FileInfo>  list = guardRun();
			//always report scanDone even this folder is not readable or error.
			
			if (!mCancel) {
				mHandler.post( new Runnable() {
					@Override
					public void run() {
						if( mLastScannerReq == FileScannerReq.this) {
							if( !mCancel ) { 
								if(list == null)
									onResult(RESULT_TYPE_SCANNER , mScanPath, new ArrayList<FileInfo>());
								else
									onResult(RESULT_TYPE_SCANNER ,mScanPath, list);
							}
							mLastScannerReq = null;
						}
					}
				});
			}
		}

		@Override
		public void cancel() {
			mCancel = true;
		}
		
		public ArrayList<FileInfo> guardRun() {
			File fDest = new File(mScanPath); 
			if (!fDest.isDirectory() || !fDest.exists())
				return null;
			
			File[] files = fDest.listFiles();
			if (files == null)
				return null;
				
			final ArrayList<FileInfo> folderList = new ArrayList<FileInfo>();
			boolean isAddDir = isNeedDir();
			String name;
			if(isAddDir){
				for (int i = 0; i < files.length; i++) {
					File f = files[i]; 
					if (f.isDirectory()) {
						name = f.getName();
						if ((name.length() > 0) && !name.startsWith(".")
								&& f.canRead()) {
							FileInfo fi = new FileInfo(name, true);
							fi.modifytime = f.lastModified();
							fi.isDirectory = true;
							fi.lsize = 0;
							fi.sub = f.listFiles().length;
							folderList.add(fi);
						}
					}
					if (mCancel) {
						Log.d(TAG, "scan abort1: " + i + "/" + files.length);
						return null;
					}
				}
				Collections.sort(folderList, getDirSortComp(mSortType));
			}
			
			if(mNeedUp){
				FileInfo up = new FileInfo("..", true);
				up.modifytime = System.currentTimeMillis();
				up.isDirectory = true;
				up.lsize = 0;
				folderList.add(0, up);
			}
			
			String filePath = mScanPath + "/";
			for (int i = 0; i < folderList.size(); i++) {
				folderList.get(i).path = filePath ;
				folderList.get(i).size = "";
			}
			

			LinkedList<FileInfo> fileList = new LinkedList<FileInfo>();
			for (int i = 0; i < files.length; i++) {
				File f = files[i]; 
				if (!f.isDirectory()) {
					name = f.getName();					
					if ((name.length() > 0) && (!name.startsWith(".")) && isMatchListType(name)) {
						FileInfo fi = new FileInfo(name, false);
						fi.size = fileSizeMsg(f.getPath());
						fi.lsize = f.length();
						fi.modifytime = name2Date(name);
						if(fi.modifytime == 0)
							fi.modifytime = f.lastModified();
						fi.fileType = FileMediaType.getMediaType(name);
						fi.isDirectory = false;
						if(fi.fileType == FileMediaType.IMAGE_TYPE || 
								fi.fileType == FileMediaType.VIDEO_TYPE)
							fileList.add(fi);
					}
				}
				if (mCancel) {
					Log.d(TAG, "scan abort2: " + i + "/" + files.length);
					return null;
				}
			}
			Collections.sort(fileList, getFileSortComp(mSortType));
			
			for (int i = 0; i < fileList.size(); i++) {
				fileList.get(i).path = filePath ;
			}

			folderList.addAll(fileList);	//now folder list contains folder and file.
			generateTimeHeaderId(folderList);
			return folderList;
		}
		
		private boolean isNeedDir(){
            return false;
		}

		private  boolean isMatchListType(String fname) {
            if (mListType==FileMediaType.ALL_TYPES) {
                return true;
            }

            int type = FileMediaType.getMediaType(fname);
            return (mListType & type) == type;
		}
	}
	
	private  class FileSortReq implements WorkReq,Match4Req{
		public ArrayList<FileInfo> mFileList;
		public int mType;
		private String mFilePath;
		public boolean mCancel = false;
		public FileSortReq(int type , ArrayList<FileInfo> fileList , String filePath){
			mFileList = fileList;
			mType = type;
			mFilePath = filePath;
		}
		@Override
		public boolean matchs(WorkReq req) {
			if(req instanceof FileSortReq){
				FileSortReq req2 = (FileSortReq) req;
				if(mType == req2.mType && mFileList == req2.mFileList){
					return true;
				}
			}
			return false;
		}

		@Override
		public void execute() {
			if(mFileList == null){
				return;
			}
			Iterator<FileInfo> it = mFileList.iterator();
			Log.i(TAG, "mFileList.size() : " + mFileList.size());
			int dirCount = 0;
			while(it.hasNext()){
				if(it.next().isDirectory){
					dirCount++;
				}
			}
			Collections.sort(mFileList.subList(0, dirCount), getDirSortComp(mType));
			Collections.sort(mFileList.subList(dirCount , mFileList.size()), getFileSortComp(mType));
			if (!mCancel) {
				mHandler.post( new Runnable() {
					@Override
					public void run() {
						onResult(RESULT_TYPE_SORT ,mFilePath, mFileList);
					}
				});
			}
		}

		@Override
		public void cancel() {
			mCancel = true;
		}
		
	}
	private  class SearchReq implements WorkReq,Match4Req{
		public String mFilePath;
		public String mKey;
		public ArrayList<FileInfo> mSearchDirList = new ArrayList<FileInfo>();
		public ArrayList<FileInfo> mSearchFileList = new ArrayList<FileInfo>();
		public boolean mCancel = false;
		
		public SearchReq(String filePath , String key){
			mFilePath = filePath;
			mKey = key;
		}
		
		@Override
		public boolean matchs(WorkReq req) {
			if(req instanceof SearchReq){
				SearchReq req2 = (SearchReq) req;
				if(mFilePath.equals(req2.mFilePath) && mKey.equals(req2.mKey)){
					return true;
				}
			}
			return false;
		}

		@Override
		public void execute() {
			mSearchDirList.clear();
			mSearchFileList.clear();
			searchFile(new File(mFilePath));
			mSearchDirList.addAll(mSearchFileList);
			if (!mCancel) {
				mHandler.post( new Runnable() {
					@Override
					public void run() {
						onResult(RESULT_TYPE_SEARCH ,mFilePath, mSearchDirList);
					}
				});
			}
			
		}

		private void searchFile(File fDest){
			if(mCancel){
				return;
			}
			if (fDest == null || !fDest.isDirectory() || !fDest.exists())
				return ;
			
			File[] files = fDest.listFiles();
			if (files == null)
				return ;
			String name;
			FileInfo fi;
			for (int i = 0; i < files.length; i++) {
				File f = files[i]; 
				name = f.getName();
				if (f.isDirectory()) {					
					if ((name.length() > 0) && !name.startsWith(".")&& name.indexOf(mKey)>=0) {
						fi = new FileInfo(name, true);
						fi.path = f.getParentFile().getPath()+"/";
						Log.i(TAG, "TRUE name : " + name);
						fi.modifytime = f.lastModified();
						mSearchDirList.add(fi);
					}
					searchFile(f);
				}else{
					if ((name.length() > 0) && !name.startsWith(".")&& name.indexOf(mKey)>=0) {
						fi = new FileInfo(name, false);
						Log.i(TAG, "FALSE  name : " + name);
						fi.path = f.getParentFile().getPath()+"/";
						fi.size = fileSizeMsg(f.getPath());
						fi.lsize = f.length();
						fi.modifytime = f.lastModified();
						fi.fileType = FileMediaType.getMediaType(name);
						
						mSearchFileList.add(fi);
					}
				}
					
			}
		}
        @Override
        public void cancel() {
                mCancel = true;
        }

    }    

	
	static private Comparator<FileInfo> getDirSortComp(int type){
		switch (type) {
		case SORY_BY_NAME:
			return mFileComByName;
		case SORY_BY_TIME_DOWN:
			return mFileComByTimeDown;
		case SORY_BY_TIME_UP:
			return mFileComByTimeUp;
		default:
			break;
		}
		return mFileComByName;
	}
	static private Comparator<FileInfo> getFileSortComp(int type){
		switch (type) {
		case SORY_BY_NAME:
			return mFileComByName;
		case SORY_BY_SIZE_DOWN:
			return mFileComBySizeDown;
		case SORY_BY_SIZE_UP:
			return mFileComBySizeUp;
		case SORY_BY_TIME_DOWN:
			return mFileComByTimeDown;
		case SORY_BY_TIME_UP:
			return mFileComByTimeUp;
		case SORY_BY_NAME_TIME_DOWN:
			return mFileComByNameTimeDown;
		default:
			break;
		}
		return mFileComByName;
	}
	public void setSortType(int sortType , final ArrayList<FileInfo> fileList , String filePath){
		mSortType = sortType;
		if(fileList == null){
			return;
		}
		if(null == mScannerThread) {
			mScannerThread = new WorkThread();
			mScannerThread.start();
		}
		FileSortReq fSortReq = new  FileSortReq(sortType , fileList , filePath);
		if(!mScannerThread.isDuplicateWorking(fSortReq)){
			mScannerThread.addReq(fSortReq);						
		}
	}

	public void startScanner(String filePath, boolean needUp) {
	    startScanner(filePath, FileMediaType.ALL_TYPES, needUp);
    }

	//因为只需要扫描最后一个，那么不需要队列来存储path
	public void startScanner(String filePath, int listType, boolean needUp) {
		if( filePath == null)
			return;
		
		if(null == mScannerThread) {
			mScannerThread = new WorkThread();
			mScannerThread.start();
		}
		
		FileScannerReq fScannerReq = new  FileScannerReq(filePath , mHandler, listType, needUp);
		if(!mScannerThread.isDuplicateWorking(fScannerReq)){
			mScannerThread.cancelReqsList();
			mScannerThread.addReq(fScannerReq);
			
			//save the last scanner req
			mLastScannerReq = fScannerReq;
		}
	}

	public void searchFileByName(String filePath , String key){
		if(filePath == null || key == null){
			return;
		}
		if(null == mScannerThread) {
			mScannerThread = new WorkThread();
			mScannerThread.start();
		}
		SearchReq searchReq = new  SearchReq(filePath , key);
		if(!mScannerThread.isDuplicateWorking(searchReq)){
			mScannerThread.addReq(searchReq);						
		}
	}
	public String getScanneringPath(){
		if( mLastScannerReq != null)
			return mLastScannerReq.mScanPath;
		return null;
	}
	
	public void stopScanner() {
		if(null != mScannerThread) {
			mScannerThread.cancelReqsList();
		}	
		mLastScannerReq = null;		//set to null so won't report onResult
	}
	
	public void onDestroy(){
		if(null != mScannerThread) {
			mScannerThread.exit();
		}
		mScannerThread = null;
	}	
	
	private static String fileSizeMsg(String path) {
		File f = new File(path);
		int sub_index = 0;
		String show = "";
		if ((f != null) && (f.isFile())) {
			long length = f.length();
			if (length >= 1073741824) {
				sub_index = (String.valueOf((float) length / 1073741824))
						.indexOf(".");
				show = ((float) length / 1073741824 + "000").substring(0,
						sub_index + 2) + "GB";
			} else if (length >= 1048576) {
				sub_index = (String.valueOf((float) length / 1048576))
						.indexOf(".");
				show = ((float) length / 1048576 + "000").substring(0,
						sub_index + 2) + "MB";
			} else if (length >= 1024) {
				sub_index = (String.valueOf((float) length / 1024))
						.indexOf(".");
				show = ((float) length / 1024 + "000").substring(0,
						sub_index + 2) + "KB";
			} else if (length < 1024) {
				show = String.valueOf(length) + "B";
			}
		}
		return show;
	}
	
	public  static ArrayList<FileInfo> readStringXML(String in, boolean needUp) {
		
		XmlPullParser parser = Xml.newPullParser();
		ArrayList<FileInfo> list = new ArrayList<FileInfo>();
		ByteArrayInputStream inStream = null;
		
		if(needUp){
			FileInfo up = new FileInfo("..", true);
			up.modifytime = System.currentTimeMillis();
			up.isDirectory = true;
			up.lsize = 0;
			list.add(up);
		}

        try {
        	
        	inStream = new ByteArrayInputStream(in.getBytes());
        	parser.setInput(inStream, "UTF-8");
        	int eventType = parser.getEventType();
        	FileInfo info = null;
        	while (eventType != XmlPullParser.END_DOCUMENT) {
        		switch (eventType) {
        			case XmlPullParser.START_DOCUMENT:
	                	break;
        			case XmlPullParser.START_TAG:
        				String name = parser.getName();
        				if(name.equals("file")){
        					info = new FileInfo();
        				}else if(name.equals("name")){
        					info.name = parser.nextText();
        				}else if(name.equals("path")){
        					info.path = parser.nextText();
        				}else if(name.equals("size")){
        					info.lsize = Integer.parseInt(parser.nextText());
        				}else if(name.equals("dir")){
        					info.isDirectory = Boolean.parseBoolean(parser.nextText());
        				}else if(name.equals("time")){
        					info.modifytime = name2Date(info.name);
        					if(info.modifytime == 0)
        						info.modifytime = Long.parseLong(parser.nextText());
        				}else if(name.equals("sub")){
        					info.sub = Integer.parseInt(parser.nextText());
        				}
                        break;
                	case XmlPullParser.END_TAG:
                		if(parser.getName().equals("file")){
                			try {
            					info.url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + 
            							RemoteCameraConnectManager.HTTP_SERVER_PORT + 
            							"/cgi-bin/Config.cgi?action=download&property=path&value=" + 
            							URLEncoder.encode(info.getFullPath(), "UTF-8");
            					info.thunbnailUrl = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + 
            							RemoteCameraConnectManager.HTTP_SERVER_PORT + 
            							 "/cgi-bin/Config.cgi?action=thumbnail&property=path&value=" + 
            							 URLEncoder.encode(info.getFullPath(), "UTF-8");
            				} catch (UnsupportedEncodingException e) {
            					// TODO Auto-generated catch block
            					e.printStackTrace();
            				}
                			info.fileType = FileMediaType.getMediaType(info.name);
                			if(!info.isDirectory && (info.fileType == FileMediaType.IMAGE_TYPE ||
            						info.fileType == FileMediaType.VIDEO_TYPE))
            					list.add(info);
                		}
                        break;
                }

                eventType = parser.next();
	        }
        	
        	Collections.sort(list, getFileSortComp(mSortType));
			generateTimeHeaderId(list);
	        return list;
        } catch (Exception e) {
        	e.printStackTrace();
        }finally{
        	if(inStream != null){
				try {
					inStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }

        return list;
	}
	
	public  static ArrayList<FileInfo> readJSONArray(JSONArray array, boolean needUp){
		ArrayList<FileInfo> list = new ArrayList<FileInfo>();
		
		if(needUp){
			FileInfo up = new FileInfo("..", true);
			up.modifytime = System.currentTimeMillis();
			up.isDirectory = true;
			up.lsize = 0;
			list.add(up);
		}
		
		
		try {
			for(int i = 0;i < array.length(); i++){
				JSONObject jso = array.getJSONObject(i);
				FileInfo info = new FileInfo();
				info.name = jso.optString("name");
				info.path = jso.optString("path");
				info.lsize = jso.optLong("size");
				info.isDirectory = jso.optBoolean("dir");
				info.modifytime = name2Date(info.name);
				if(info.modifytime == 0)
					info.modifytime = jso.optLong("time");
				info.sub = jso.optInt("sub");
				info.fileType = FileMediaType.getMediaType(info.name);
				info.url = jso.optString("url", null);
				info.thunbnailUrl = jso.optString("thunbnailUrl", null);
				try {
					if(info.url == null){
						info.url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + 
							RemoteCameraConnectManager.HTTP_SERVER_PORT + 
							"/cgi-bin/Config.cgi?action=download&property=path&value=" + 
							URLEncoder.encode(info.getFullPath(), "UTF-8");
					}
					if(info.thunbnailUrl == null){
						info.thunbnailUrl = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":" + 
							RemoteCameraConnectManager.HTTP_SERVER_PORT + 
							 "/cgi-bin/Config.cgi?action=thumbnail&property=path&value=" + 
							 URLEncoder.encode(info.getFullPath(), "UTF-8");
					}
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(!info.isDirectory && (info.fileType == FileMediaType.IMAGE_TYPE ||
						info.fileType == FileMediaType.VIDEO_TYPE))
					list.add(info);
			}
		} catch (JSONException e) {
			Log.i(TAG,"JSONException = " + e);
		}
		
		Collections.sort(list, getFileSortComp(mSortType));
		generateTimeHeaderId(list);
		return list;
	}
	
	private static long name2Date(String name){
		if(name == null)
			return 0;
		try{
			name = name.substring(0, name.indexOf('.'));
			name = name.replaceAll("[^0-9]", "");
			if(name.length() != 14)
				return 0;
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			Date date = sdf.parse(name);
			return date.getTime();
		}catch(Exception e){
			e.printStackTrace();
		}
		return 0;
	}

	private static void generateTimeHeaderId(List<FileInfo> list) {
		if (list != null && !list.isEmpty()) {
			Iterator<FileInfo> it = list.iterator();
			String lastTimeString = null;
			int headerId = 1;
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			while (it.hasNext()) {
				FileInfo fileinfo = it.next();
				String dateStr = sdf.format(new Date(fileinfo.modifytime));
				if (lastTimeString == null) {
					lastTimeString = dateStr;
				}
				if (lastTimeString.equals(dateStr)) {
					fileinfo.setHeaderId(headerId);
				} else {
					headerId++;
					lastTimeString = dateStr;
					fileinfo.setHeaderId(headerId);
				}
			}
		}
	}
}
