
package com.car.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import libcore.io.DiskLruCache;
import libcore.io.DiskLruCache.Snapshot;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;

import com.car.common.util.FileMediaType;
import com.car.common.util.Match4Req;
import com.car.common.util.WorkReq;
import com.car.common.util.WorkThread;

/**
 * 一个用于管理图片和视频缩略图缓存的类，分两级缓存，内存缓存和本地缓存
 * @author iveszhong
 *
 * 例子：
 * ThumbnailCacheManager.initialize(context)
 * ThumbnailCacheManager.instance().addThumbnailCacheListener(listener);
 *
 * do some work
 *
 * ThumbnailCacheManager.instance().removeThumbnailCacheListener(listener);
 */
public class ThumbnailCacheManager {

    private static final String TAG = "CarSvc_ThumbnailCacheManager";

    private static volatile ThumbnailCacheManager sIns = null;

    private static final int CACHE_MANAGER_VERSION = 28;
    private static File  CACHE_DIR = null;
    private static Handler mHandler;

    /**
     * 获取网络原图
     */
    public static final int TYPE_NET_ORIGINAL = 1;
    /**
     * 获取网络图片的缩略图
     */
    public static final int TYPE_NET_THUMB = 2;
    /**
     * 获取本地图片缩略图，还支持本地视频的缩略图
     */
    public static final int TYPE_LOCAL_THUMB = 3;

    private List<ThumbnailCacheListener> mThumbnailCacheListenerList;
    /**
     * 图片缓存技术的核心类，用于缓存所有下载好的图片，在程序内存达到设定值时会将最少最近使用的图片移除掉。
     */
    private LruCache<String, Bitmap> mMemoryCache;
    /**
     * 图片硬盘缓存核心类。
     */
    private DiskLruCache mDiskLruCache;

    private WorkThread mWorkThread;

    /**
     * 初始化ThumbnailCacheManager环境，一般置于程序Application中初始化
     * @param context
     */
    public static void initialize(Context context){
        CACHE_DIR = getDiskCacheDir(context, "thumb");
        mHandler = new Handler();
    }

    /**
     * 根据传入的uniqueName获取硬盘缓存的路径地址。
     */
    private static File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if ((Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) && context.getExternalCacheDir() != null) {
            cachePath = context.getExternalCacheDir().getPath();
        } else if (context.getCacheDir() != null){
            cachePath = context.getCacheDir().getPath();
        } else {
        	cachePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/carcache";
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    public static ThumbnailCacheManager instance(){
        if(sIns == null){
            synchronized (ThumbnailCacheManager.class) {
                if(sIns == null){
                    int maxMemory = (int) Runtime.getRuntime().maxMemory();
                    Log.i(TAG, "maxMemory = " + maxMemory);
                    int cacheSize = maxMemory / 8;
                    sIns = new ThumbnailCacheManager(cacheSize, 10 * 1024 * 1024);
                }
            }
        }
        return sIns;
    }

    /**
     *根据key值 从内存缓存中获取图片，如内存缓存中无对应的key图片
     *则会在本地缓存中查找，如果还找不到，则通过url获取图片
     *并将图片保存到本地缓存和内存缓存
     * @param url 网络图片地址或者本地文件路径
     * @param key 从缓存中查找图片的key值，应保证唯一性
     * @param type 图片的类型，可以取三个值:{@link #TYPE_NET_ORIGINAL},
     * 				{@link #TYPE_NET_THUMB},{@link #TYPE_LOCAL_THUMB}
     * @return 缓存中对应的bitmap,如果内存缓存无，则返回空
     */
    public Bitmap getThumbnail(String url, String key, int type){

        Bitmap bitmap = mMemoryCache.get(key);
        if(bitmap != null && !bitmap.isRecycled())
            return bitmap;

        BitmapDecodeReq req = new BitmapDecodeReq(url, key, type);
        if(mWorkThread != null && !mWorkThread.isDuplicateWorking(req)){
            mWorkThread.addReq(req);
        }

        return null;
    }

    public void clearTask(){
        if(mWorkThread != null)
            mWorkThread.cancelReqsList();
    }

    /**
     * 增加监听，不需要监听时务必调用removeThumbnailCacheListener移除监听
     * @param l
     */
    public void addThumbnailCacheListener(ThumbnailCacheListener l){
        synchronized (mThumbnailCacheListenerList) {
            mThumbnailCacheListenerList.add(l);
        }
    }

    /**
     * 移除监听，当
     * @param l
     */
    public void removeThumbnailCacheListener(ThumbnailCacheListener l){
        synchronized (mThumbnailCacheListenerList) {
            mThumbnailCacheListenerList.remove(l);
            if(mThumbnailCacheListenerList.size() == 0){
                destory();
            }
        }
    }

    /**
     *
     * @param context
     * @param memoryCacheSize 内存缓存大小
     * @param diskCacheSize 本地缓存大小
     */
    private ThumbnailCacheManager(int memoryCacheSize, int diskCacheSize){
        Log.i(TAG, "=======ThumbnailCacheManager=======");
        mThumbnailCacheListenerList = new ArrayList<ThumbnailCacheListener>();
        mMemoryCache = new LruCache<String, Bitmap>(memoryCacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
            
            /*当缓存大于我们设定的最大值时，会调用这个方法，我们可以用来做内存释放操作
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
                if (evicted && oldValue != null){
                    oldValue.recycle();
                }
            }*/
        };
        try {
            // 获取图片缓存路径
            File cacheDir = CACHE_DIR;
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            // 创建DiskLruCache实例，初始化缓存数据
            mDiskLruCache = DiskLruCache
                    .open(cacheDir, CACHE_MANAGER_VERSION, 1, diskCacheSize);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mWorkThread = new WorkThread("thumbnail decode");
        mWorkThread.setDispatchMode(WorkThread.FIFO);
        mWorkThread.start();
    }

    private void destory(){
        Log.i(TAG, "=======destory=======");
        synchronized (ThumbnailCacheManager.class){
            if(mDiskLruCache != null) {
                try {
                    mDiskLruCache.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(mWorkThread != null) {
                mWorkThread.exit();
            }
            sIns = null;
        }
    }

    /**
     * 获取当前应用程序的版本号。
     */
    private int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    0);
            return info.versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * 使用MD5算法对传入的key进行加密并返回。
     */
    public  String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    class BitmapDecodeReq implements WorkReq, Match4Req{

        private static final int DEFAULT_THUMBNAIL_WIDTH = 456;
        private static final int DEFAULT_THUMBNAIL_HEIGHT = 256;

        private String mUrl;
        private String mKey;
        private int mType;

        BitmapDecodeReq(String url, String key, int type){
            mUrl = url;
            mKey = key;
            mType = type;
        }

        @Override
        public boolean matchs(WorkReq req) {
            if(req instanceof BitmapDecodeReq){
                BitmapDecodeReq req2 = (BitmapDecodeReq) req;
                if(mKey.equals(req2.mKey)){
                    return true;
                }
            }
            return false;
        }

        @Override
        public void execute() {
            final Bitmap bitmap = doDecodeBitmap();
            if(bitmap != null){
                synchronized (mThumbnailCacheListenerList) {
                    mHandler.post(new Runnable(){

                        @Override
                        public void run() {
                            for(ThumbnailCacheListener l : mThumbnailCacheListenerList)
                                l.onThumbnailCacheDone(mUrl, mKey, mType, bitmap);
                        }

                    });

                }
            }
        }

        @Override
        public void cancel() {

        }

        Bitmap doDecodeBitmap(){
            FileDescriptor fileDescriptor = null;
            FileInputStream fileInputStream = null;
            OutputStream outputStream = null;
            Snapshot snapShot = null;
            DiskLruCache.Editor editor = null;
            ByteArrayOutputStream baos = null;
            Bitmap bitmap = null;
            try {
                // 生成图片URL对应的key
                final String key = hashKeyForDisk(mKey);
                // 查找key对应的缓存
                snapShot = mDiskLruCache.get(key);
                if (snapShot == null) {
                    if(mType == TYPE_NET_ORIGINAL){
                        // 如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存
                        editor = mDiskLruCache.edit(key);
                        if (editor != null) {
                            outputStream = editor.newOutputStream(0);
                            if (downloadUrlToStream(mUrl, outputStream)) {
                                editor.commit();
                            } else {
                                editor.abort();
                            }
                            editor = null;
                        }
                    }else if(mType == TYPE_NET_THUMB){
                        baos = new ByteArrayOutputStream();
                        //FIXME 有无优化的空间？不用下载整张图片就可以获取缩略图???
                        if (downloadUrlToStream(mUrl, baos)) {
                            byte[] data = baos.toByteArray();
                            bitmap = getImageThumbnail(data, DEFAULT_THUMBNAIL_WIDTH,
                                    DEFAULT_THUMBNAIL_HEIGHT);
                            if(bitmap != null){
                                editor = mDiskLruCache.edit(key);
                                if(editor != null){
                                    outputStream = editor.newOutputStream(0);
                                    boolean sucess = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                                    if(sucess)
                                        editor.commit();
                                    else
                                        editor.abort();
                                    editor = null;
                                }
                            }
                        }
                        if(baos != null)
                            baos.close();
                    }else if(mType == TYPE_LOCAL_THUMB){
                        editor = mDiskLruCache.edit(key);
                        if (editor != null) {
                            outputStream = editor.newOutputStream(0);
                            bitmap = null;
                            File file = new File(mUrl);
                            if(file.isDirectory()){
                                File[] files = file.listFiles();
                                for(File f : files){
                                    String name = f.getName();
                                    int t = FileMediaType.getMediaType(name);
                                    if(t == FileMediaType.IMAGE_TYPE){
                                        bitmap = getImageThumbnail(f.getAbsolutePath(), DEFAULT_THUMBNAIL_WIDTH,
                                                DEFAULT_THUMBNAIL_HEIGHT);
                                    }else if(t == FileMediaType.VIDEO_TYPE){
                                        bitmap = getVideoThumbnail(f.getAbsolutePath(), DEFAULT_THUMBNAIL_WIDTH,
                                                DEFAULT_THUMBNAIL_HEIGHT, MediaStore.Images.Thumbnails.MINI_KIND);
                                    }
                                    if(bitmap != null)
                                        break;
                                }
                            }else{
                                int t = FileMediaType.getMediaType(mUrl);
                                if(t == FileMediaType.IMAGE_TYPE){
                                    bitmap = getImageThumbnail(mUrl, DEFAULT_THUMBNAIL_WIDTH,
                                            DEFAULT_THUMBNAIL_HEIGHT);
                                }else if(t == FileMediaType.VIDEO_TYPE){
                                    bitmap = getVideoThumbnail(mUrl, DEFAULT_THUMBNAIL_WIDTH,
                                            DEFAULT_THUMBNAIL_HEIGHT, MediaStore.Images.Thumbnails.MINI_KIND);
                                }
                            }

                            if(bitmap != null){
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                                editor.commit();
                            }else {
                                editor.abort();
                            }
                            editor = null;
                        }
                    }
                    mDiskLruCache.flush();
                    // 缓存被写入后，再次查找key对应的缓存
                    snapShot = mDiskLruCache.get(key);
                }
                if (snapShot != null) {
                    fileInputStream = (FileInputStream) snapShot.getInputStream(0);
                    fileDescriptor = fileInputStream.getFD();
                }
                // 将缓存数据解析成Bitmap对象
                bitmap = null;
                if (fileDescriptor != null) {
                    bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                }
                if (bitmap != null) {
                    // 将Bitmap对象添加到内存缓存当中
                	Bitmap old = mMemoryCache.get(mKey);
                	if(old != null){
                		old.recycle();
                	}
                    mMemoryCache.put(mKey, bitmap);
                }else{
                    mDiskLruCache.remove(key);
                    mDiskLruCache.flush();
                }
                return bitmap;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                
                if(outputStream != null){
                	try {
						outputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
                }
                
                if(baos != null){
                	try {
						baos.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                }
                
                if(editor != null){
                    try {
                        editor.abort();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            
            if (bitmap != null) {
                // 将Bitmap对象添加到内存缓存当中
            	Bitmap old = mMemoryCache.get(mKey);
            	if(old != null){
            		old.recycle();
            	}
                mMemoryCache.put(mKey, bitmap);
            }
            
            return bitmap;
        }

        /**
         * 建立HTTP请求，并获取Bitmap对象。
         *
         * @param urlString 图片的URL地址
         * @return 解析后的Bitmap对象
         */
        private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
            HttpURLConnection urlConnection = null;
            BufferedOutputStream out = null;
            BufferedInputStream in = null;
            try {
                final URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                if(urlConnection.getResponseCode() != 200)
                    return false;
                in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
                out = new BufferedOutputStream(outputStream, 8 * 1024);
                int b;
                while ((b = in.read()) != -1) {
                    out.write(b);
                }
                return true;
            } catch (final IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        /**
         * 根据指定的图像路径和大小来获取缩略图
         * 此方法有两点好处：
         *     1. 使用较小的内存空间，第一次获取的bitmap实际上为null，只是为了读取宽度和高度，
         *        第二次读取的bitmap是根据比例压缩过的图像，第三次读取的bitmap是所要的缩略图。
         *     2. 缩略图对于原图像来讲没有拉伸，这里使用了2.2版本的新工具ThumbnailUtils，使
         *        用这个工具生成的图像不会被拉伸。
         * @param imagePath 图像的路径
         * @param width 指定输出图像的宽度
         * @param height 指定输出图像的高度
         * @return 生成的缩略图
         */
        private Bitmap getImageThumbnail(String imagePath, int width, int height) {
            Bitmap bitmap = null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            // 获取这个图片的宽和高，注意此处的bitmap为null
            bitmap = BitmapFactory.decodeFile(imagePath, options);
            options.inJustDecodeBounds = false; // 设为 false
            // 计算缩放比
            int h = options.outHeight;
            int w = options.outWidth;
            int beWidth = w / width;
            int beHeight = h / height;
            int be = 1;
            if (beWidth < beHeight) {
                be = beWidth;
            } else {
                be = beHeight;
            }
            if (be <= 0) {
                be = 1;
            }
            options.inSampleSize = be;
            // 重新读入图片，读取缩放后的bitmap，注意这次要把options.inJustDecodeBounds 设为 false
            bitmap = BitmapFactory.decodeFile(imagePath, options);
            // 利用ThumbnailUtils来创建缩略图，这里要指定要缩放哪个Bitmap对象
            Bitmap result = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            if(bitmap != null && bitmap != result){
                bitmap.recycle();
                bitmap = null;
            }
            return result;
        }

        /**
         * 根据指定的图像路径和大小来获取缩略图
         * 此方法有两点好处：
         *     1. 使用较小的内存空间，第一次获取的bitmap实际上为null，只是为了读取宽度和高度，
         *        第二次读取的bitmap是根据比例压缩过的图像，第三次读取的bitmap是所要的缩略图。
         *     2. 缩略图对于原图像来讲没有拉伸，这里使用了2.2版本的新工具ThumbnailUtils，使
         *        用这个工具生成的图像不会被拉伸。
         * @param data 图像的二进制数据
         * @param width 指定输出图像的宽度
         * @param height 指定输出图像的高度
         * @return 生成的缩略图
         */
        private Bitmap getImageThumbnail(byte data[], int width, int height) {
            Bitmap bitmap = null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            // 获取这个图片的宽和高，注意此处的bitmap为null
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            options.inJustDecodeBounds = false; // 设为 false
            // 计算缩放比
            int h = options.outHeight;
            int w = options.outWidth;
            int beWidth = w / width;
            int beHeight = h / height;
            int be = 1;
            if (beWidth < beHeight) {
                be = beWidth;
            } else {
                be = beHeight;
            }
            if (be <= 0) {
                be = 1;
            }
            options.inSampleSize = be;
            // 重新读入图片，读取缩放后的bitmap，注意这次要把options.inJustDecodeBounds 设为 false
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            // 利用ThumbnailUtils来创建缩略图，这里要指定要缩放哪个Bitmap对象
            Bitmap result = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            if(bitmap != null && bitmap != result){
                bitmap.recycle();
                bitmap = null;
            }
            return result;
        }

        /**
         * 获取视频的缩略图
         * 先通过ThumbnailUtils来创建一个视频的缩略图，然后再利用ThumbnailUtils来生成指定大小的缩略图。
         * 如果想要的缩略图的宽和高都小于MICRO_KIND，则类型要使用MICRO_KIND作为kind的值，这样会节省内存。
         * @param videoPath 视频的路径
         * @param width 指定输出视频缩略图的宽度
         * @param height 指定输出视频缩略图的高度度
         * @param kind 参照MediaStore.Images.Thumbnails类中的常量MINI_KIND和MICRO_KIND。
         *            其中，MINI_KIND: 512 x 384，MICRO_KIND: 96 x 96
         * @return 指定大小的视频缩略图
         */
        private Bitmap getVideoThumbnail(String videoPath, int width, int height,
                int kind) {
            Bitmap bitmap = null;
            // 获取视频的缩略图
            bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
            Bitmap result = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            if(bitmap != null && bitmap != result){
                bitmap.recycle();
                bitmap = null;
            }
            return result;
        }

    }

    public interface ThumbnailCacheListener{
        public void onThumbnailCacheDone(String url, String key, int type, Bitmap bitmap);
    }
}
