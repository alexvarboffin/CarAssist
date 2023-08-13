
package com.car.control.browser;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import com.car.common.util.FileMediaType;
//
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;

import android.util.Log;

public class Util {
	private static final String TAG = "Car_Util";
	
	//list type
	public final static int LIST_ALL = 0;
	public final static int LIST_IMAGE = 1;
	public final static int LIST_VIDEO = 2;
	public final static int LIST_AUDIO = 3;
	public final static int LIST_APK = 4;	
	public final static int LIST_DOCUMENT = 5;	
	public final static int LIST_APP = 6;	
	public final static int LIST_WIFI = 7;	
	public final static int LIST_AIR = 8;	
	
	public final static String ROOT_PATH = "/";
	
	public static final int THUMB_BASE_WH = 96;
	public static  int THUMB_WH = 96;
	public static Bitmap sNullBitmap = Bitmap.createBitmap(1, 1, Config.RGB_565);
	
	public static int dip2px(Context context, float dpValue) {  
		final float scale = context.getResources().getDisplayMetrics().density;  
		return (int) (dpValue * scale + 0.5f);  
	} 

	public static String getPostfix(String fName) {
		int postfixPos = fName.lastIndexOf(".");
		String end = "*";
		if (postfixPos > 0) {
			end = fName.substring(postfixPos + 1, fName.length()).toLowerCase(Locale.ENGLISH);
		}
		return end;
	}
	
	public static String getMainName(String fName) {
		int postfixPos = fName.lastIndexOf(".");
		String end = "*";
		if (postfixPos > 0) {
			end = fName.substring(0, postfixPos);
		}
		return end;
	}
	
	public static String fileSizeMsg(String path) {
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
	
	public static Bitmap getMediaThumb(Context context ,FileInfo fInfo)  {
		String path = fInfo.getFullPath();
		if(path == null){
			return null;
		}
		Bitmap bitmap = null;
		int type = fInfo.fileType;
		switch (type) {
		case FileMediaType.AUDIO_TYPE:
			return sNullBitmap;
		case FileMediaType.VIDEO_TYPE:
			bitmap = getVideoThumb(context, path, Images.Thumbnails.MICRO_KIND);
			if(bitmap != null)
				return Util.zoomImg(bitmap, Util.THUMB_WH , Util.THUMB_WH , false);
			return sNullBitmap;
		case FileMediaType.IMAGE_TYPE:
			if(!path.startsWith("/")){
				bitmap = makeNetBitmap(-1  , Util.THUMB_WH*Util.THUMB_WH , path);
			}else{
				bitmap = Util.getPhotoThumb(path);
			}
			if(bitmap == null){
				return sNullBitmap;
			}else{
				return bitmap;
			}
		default:
			break;
		}
		return null;
	}
	
	public static Bitmap getPhotoThumb(String path){
		Bitmap bitmap = null;
		ExifInterface exif = null;
        byte [] thumbData = null;
        try {
            exif = new ExifInterface(path);
            if (exif != null) {
                thumbData = exif.getThumbnail();
            }
        } catch (Throwable t) {
            Log.w(TAG, "fail to get exif thumb", t);
        }
        if (thumbData != null) {
        	bitmap = BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length);
        	if(bitmap == null)
        		return null;
        }else{        	
        	bitmap = makeBitmap(-1  , Util.THUMB_WH*Util.THUMB_WH , path);
        }

		return zoomImg(bitmap, Util.THUMB_WH , Util.THUMB_WH , false);
	}
	
	public static Bitmap getVideoThumb(Context context , String path , int kind){
		
		Bitmap bitmap = null;
		bitmap = getVideoThumbFromDB(context, path, kind);
		if(bitmap == null && Build.VERSION.SDK_INT >= 8){
			try {
				bitmap = ThumbnailUtils.createVideoThumbnail(path, kind);
			} catch (Exception e) {
				
			}
		}
		return bitmap;
	}
	public static Bitmap getVideoThumbFromDB(Context context , String path , int kind){
		Bitmap bitmap = null;
		Cursor cursor = null;
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inDither = false;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;

			cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
			new String[] { MediaStore.Video.Media._ID }, MediaStore.Audio.Media.DATA +"=?",
			new String[]{path}, null);
			if (cursor == null || cursor.getCount() == 0) {
				return null;
			}
			cursor.moveToFirst();
			//image id in image table.
			String videoId = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media._ID));
			if (videoId == null) {
				return null;
			}
			long videoIdLong = Long.parseLong(videoId);
			bitmap = MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver() , videoIdLong,
					kind, options);
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				if(cursor != null){
					cursor.close();
					cursor = null;
				}
			} catch (Exception e2) {
			}
			
		}
		return bitmap;
	}
	
	
	public static Bitmap getAudioThumbFromDB(Context context , String path){
		Bitmap bitmap = null;
		int album_id = -1;
		Cursor c = null;
		try {
			c = context.getContentResolver().query(  
		            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Audio.Media.DATA +"=?" 
		            , new String[]{path},  
		            null);
		    if(c != null && c.getCount() > 0 && c.moveToFirst()){
		    	album_id = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)) ;
		    }
		    if(c != null){
		    	c.close();
		    	c = null; 
		    }
		    
		    if(album_id > 0){
		    	String mUriAlbums = "content://media/external/audio/albums";  
		        String[] projection = new String[] { "album_art" };  
		        c = context.getContentResolver().query(  
		                Uri.parse(mUriAlbums + "/" + Integer.toString(album_id)),  
		                projection, null, null, null);  
		        String album_art = null;  
		        if (c != null && c.getCount() > 0 && c.moveToFirst()) {  
		            album_art = c.getString(0);  
		        }  
		        if(c != null){
			        c.close();  
			        c = null;  
		        }
		        if(album_art != null)
		        	bitmap = BitmapFactory.decodeFile(album_art);  		        
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				if(c != null){
			    	c.close();
			    	c = null; 
			    }
			} catch (Exception e2) {
			}
		}
	    
		return bitmap;
	}
	public static Bitmap zoomImg(Bitmap bm, int newWidth, int newHeight , boolean isforce) {
		if(bm == null){
			return null;
		}
		
		int width = bm.getWidth();
		int height = bm.getHeight();
		if(width <= newWidth && height <= newHeight){
			return bm;
		}
		float scaleWidth = ((float) newWidth) / width;
		float scaleHeight = ((float) newHeight) / height;
		Matrix matrix = new Matrix();
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
		if (bm != null && !bm.isRecycled() && bm != newbm) {  
			bm.recycle();  
        } 
		return newbm;
	}
	
	public static int computeSampleSize(BitmapFactory.Options options,
            int minSideLength, int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(options, minSideLength,
                maxNumOfPixels);

        int roundedSize;
        if (initialSize <= 8) {
            roundedSize = 1;
            while (roundedSize < initialSize) {
                roundedSize <<= 1;
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8;
        }

        return roundedSize;
    }

    private static int computeInitialSampleSize(BitmapFactory.Options options,
            int minSideLength, int maxNumOfPixels) {
        double w = options.outWidth;
        double h = options.outHeight;

        int lowerBound = (maxNumOfPixels == -1) ? 1 :
                (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
        int upperBound = (minSideLength == -1) ? 128 :
                (int) Math.min(Math.floor(w / minSideLength),
                Math.floor(h / minSideLength));

        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound;
        }

        if ((maxNumOfPixels == -1) &&
                (minSideLength == -1)) {
            return 1;
        } else if (minSideLength == -1) {
            return lowerBound;
        } else {
            return upperBound;
        }
    }
    public static Bitmap makeBitmap(int minSideLength, int maxNumOfPixels,String filepath) {
    	if(filepath != null && !filepath.startsWith("/")){
    		return makeNetBitmap(minSideLength, maxNumOfPixels, filepath);
    	}
        try {
        	BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filepath , options);
            if (options.mCancel || options.outWidth == -1
                    || options.outHeight == -1) {
                return null;
            }
            options.inSampleSize = computeSampleSize(
                    options, minSideLength, maxNumOfPixels);
            options.inJustDecodeBounds = false;

            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(filepath , options);
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Got oom exception ", ex);
            return null;
        } finally {
            
        }
    }
	public static Bitmap makeNetBitmap(int minSideLength, int maxNumOfPixels, String uri){
		Bitmap bitmap = null;
		try {
			Options opt = new BitmapFactory.Options();
			opt.inJustDecodeBounds = true;
			opt.inDither = true;
			opt.inPreferredConfig = Bitmap.Config.RGB_565;

			URL p_url = new URL(uri);
			InputStream inStream = p_url.openStream();
			BitmapFactory.decodeStream(inStream, null, opt);			
			inStream.close();
			
			opt.inSampleSize = computeSampleSize(
					opt, minSideLength , maxNumOfPixels);
			opt.inJustDecodeBounds = false;
			inStream = p_url.openStream();
			bitmap = BitmapFactory
					.decodeStream(inStream, null, opt);
			inStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}		
		return bitmap;
	}
	
    public static FileInfo getFileInfoFromUri(Context context , Uri uri){
    	if(uri == null || context == null){
    		return null;
    	}
    	FileInfo fileInfo = null;
    	
    	String filePath;
    	String scheme = uri.getScheme();
    	
    	if (scheme != null && "file".startsWith(uri.getScheme())){
    		filePath = uri.getPath();
    		File file = new File(filePath);
        	fileInfo = new FileInfo(file.getName(), file.isDirectory());
        	fileInfo.path = file.getParent();
        	if(fileInfo.path != null && !fileInfo.path.endsWith("/")){
        		fileInfo.path += "/";
        	}
    	}else if (scheme != null && "content".equals(uri.getScheme())){
    		 ContentResolver cResolver = context.getContentResolver();
    		 Cursor c = cResolver.query(uri,null,null,null,null);
    		 try {
    			 c.moveToFirst();
                 filePath = c.getString(c.getColumnIndexOrThrow(Images.Media.DATA));
			} catch (Exception e) {
				return null;
			}finally{
				try {
					if(c != null){
						c.close();
						c = null;
					}
				} catch (Exception e2) {
				}
			}
             
             File file = new File(filePath);
         	 fileInfo = new FileInfo(file.getName(), file.isDirectory());
         	 fileInfo.path = file.getParent();
         	 if(fileInfo.path != null && !fileInfo.path.endsWith("/")){
        		fileInfo.path += "/";
         	 }
    	}else {
    		filePath = uri.toString();  
    		int index = filePath.lastIndexOf("/");
    		String path;
    		String name;
    		try {
    			if(index < filePath.length() && filePath.length() > 0){
        			name = filePath.substring(index+1);
        			path = filePath.substring(0, index+1);
        		}else{
        			name = "";
        			path = filePath;
        		}
			} catch (Exception e) {
				name = filePath;
				path = "";
			}
    		
    		fileInfo = new FileInfo(name, false); 
    		fileInfo.path = path;
    	}
    	
    	
    	return fileInfo;
    }
    
    public static  ArrayList<FileInfo> getShareFilePathList(Context context , Intent intent){
	    ArrayList<FileInfo> shareList = new ArrayList<FileInfo>();	
	    FileInfo fileInfo;
	    String action = intent.getAction();
	    try {
	    	
	    	if(Intent.ACTION_VIEW.equals(action)){
	    		Uri uri = intent.getData();
	            fileInfo = getFileInfoFromUri(context, uri);
	            if(fileInfo != null)
	            	shareList.add(fileInfo);
	    	}else if(Intent.ACTION_SEND.equals(action)){ 
		        Bundle extras = intent.getExtras();
		        if(extras.containsKey(Intent.EXTRA_STREAM)){
		            Uri uri = (Uri)extras.getParcelable(Intent.EXTRA_STREAM);
		            fileInfo = getFileInfoFromUri(context, uri);
		            if(fileInfo != null)
		            	shareList.add(fileInfo);
				} else if (extras.containsKey(Intent.EXTRA_SUBJECT)
						&& extras.containsKey(Intent.EXTRA_TEXT)) {
					fileInfo = new FileInfo(
							intent.getStringExtra(Intent.EXTRA_SUBJECT), false);
					fileInfo.path = intent.getStringExtra(Intent.EXTRA_TEXT);
					//fileInfo.fileType = FileMediaType.DOCUMENT_WEB_PAGE_TYPE;
					shareList.add(fileInfo);
				}
		    }else if(Intent.ACTION_SEND_MULTIPLE.equals(action)){ //分享多个
		        Bundle extras = intent.getExtras();
		        if(extras.containsKey(Intent.EXTRA_STREAM)){
		            ArrayList<Parcelable> list = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
		            for(Parcelable pa:list){
		                Uri uri = (Uri)pa;
		                fileInfo = getFileInfoFromUri(context, uri);
		                if(fileInfo != null)
		                	shareList.add(fileInfo);
		            }
		        } 
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
	    
	    return shareList;
	}

	public static String getPackageVersion(Context context) {
		PackageInfo info;
		try {
			info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return info.versionName;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "getPackageVersion Error", e);
			return "unknown";
		}
	}
	
	public static String name2DateString(String name){
		try{
			name = name.substring(0, name.indexOf('.'));
			name = name.replaceAll("[^0-9]", "");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss"); 
			Date date = sdf.parse(name);
			sdf.applyPattern("yyyy-MM-dd HH:mm");
	        String dateStr = sdf.format(date);
	        return dateStr;
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public static String name2HourString(String name){
		try{
			name = name.substring(0, name.indexOf('.'));
			name = name.replaceAll("[^0-9]", "");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			Date date = sdf.parse(name);
			sdf.applyPattern("HH:mm");
			String dateStr = sdf.format(date);
			return dateStr;
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

}
