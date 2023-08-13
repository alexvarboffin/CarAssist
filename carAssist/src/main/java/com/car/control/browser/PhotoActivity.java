
package com.car.control.browser;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import com.car.common.ThumbnailCacheManager;
import com.car.common.util.FileMediaType;
import com.car.common.util.Match4Req;
import com.car.common.util.WorkReq;
import com.car.common.util.WorkThread;
import com.car.control.BaseActivity;
import com.car.control.R;
import com.car.control.browser.ImageViewTouchBase.OnBitmapMoveListener;
import com.car.control.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class PhotoActivity extends BaseActivity implements ThumbnailCacheManager.ThumbnailCacheListener {

	private static final String TAG = "CarSvc_PhotoActivity";

	public static final String KEY_PHOTO_PATH = "key_photo_path";
	public static final String KEY_PHOTO_CURRENT = "key_photo_current";
	public static final String KEY_REMOTE = "key_remote";
	public static final String KEY_JSON_STRING = "key_json_string";

	private int mMaxNumOfPixels = 800 * 480;
	private ImageViewTouchBase mImageView;
	private GestureDetector mImageGestureDetector;
	private int mCurrentPosition = 0;
	private ArrayList<FileInfo> mFileList = new ArrayList<FileInfo>();
	private FileScanner mFileScanner;
	private String mCurrentName;
	private WorkThread mDecodeWorkThread = null;
	private int mWidth;
	private int mHeight;
	private LruCache<String, Bitmap> mImageLruCache = new LruCache<String, Bitmap>(5);
	private CarGridView mThumbView;
	private PhotoThumbAdapter mThumbAdapter;
	private boolean mRemote = false;

	private View mPhotoMainLayout, mPhotoShareLayout;

	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		setContentView(R.layout.activity_photo);

		ActionBar a = getActionBar();
		if (a != null) {
			a.setBackgroundDrawable(getResources().getDrawable(R.drawable.action_bar));
			setActionBarMidtitleAndUpIndicator("", R.drawable.back);
		}

		handleIntent(getIntent());

		mPhotoMainLayout = findViewById(R.id.photo_main_layout);
		mPhotoShareLayout = findViewById(R.id.photo_share_layout);

		mImageGestureDetector = new GestureDetector(this, new ImageGestureListener());

		mImageView = (ImageViewTouchBase) findViewById(R.id.image_view);
		mImageView.setOnBitmapMoveListener(mOnBitmapMoveListener);
		mImageView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				mImageGestureDetector.onTouchEvent(event);
				return false;
			}
		});
		mImageView.fromPhoto();

		mThumbView = (CarGridView) findViewById(R.id.image_thumb_grid);
		mThumbView.setViewPadding(0, 0, 0, 0);
		mThumbView.setSurfaceViewMode(CarGridView.SURFACEVIEW_HORIZONTAL);
		mThumbView.setBackgroundMode(CarGridView.BACKGROUND_STILL);
		int wh = (int) getResources().getDimensionPixelSize(R.dimen.image_thumb_grid_height);
		mThumbAdapter = new PhotoThumbAdapter(this, wh, wh);
		if (mThumbAdapter.getDefaultBmp() == null)
			mThumbAdapter.setDefaultBmp(BitmapFactory.decodeResource(getResources(), R.drawable.file_bitmap));
		mThumbAdapter.setUIHandler(mHandler);
		mThumbAdapter.setPhotoList(mFileList);
		mThumbView.setAdapter(mThumbAdapter);
		mThumbView.setOnItemClickListener(new CarGridView.OnItemClickListener() {

			public void onItemClick(CarGridView parent, int position, long id) {
				setImageBitmap(position);
			}
		});

		LayoutTransition transition = new LayoutTransition();
		transition.getAnimator(LayoutTransition.APPEARING).setStartDelay(0);
		transition.getAnimator(LayoutTransition.DISAPPEARING).setStartDelay(0);
		((ViewGroup) mThumbView.getParent()).setLayoutTransition(transition);

		Display display = getWindowManager().getDefaultDisplay();
		mWidth = display.getWidth();
		mHeight = display.getHeight();
		mMaxNumOfPixels = mWidth * mHeight;

		ThumbnailCacheManager.instance().addThumbnailCacheListener(this);

		mImageLruCache.setWeakRemoveListener(new WeakListener());

		mDecodeWorkThread = new WorkThread("CacheDecode");
		mDecodeWorkThread.start();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		ThumbnailCacheManager.instance().removeThumbnailCacheListener(this);
		mDecodeWorkThread.exit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (mRemote){
			getMenuInflater().inflate(R.menu.photo_activity_remote, menu);
		}
		else{
			if (mPhotoShareLayout.getVisibility() == View.VISIBLE) {
				getMenuInflater().inflate(R.menu.photo_activity_remote, menu);
			} else {
				getMenuInflater().inflate(R.menu.photo_activity_local, menu);
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
		}
		return true;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent m) {
		if (!super.dispatchTouchEvent(m)) {
			mImageGestureDetector.onTouchEvent(m);
			return mImageView.onTouchEvent(m);
		}
		return true;
	}
	@Override
	public void onBackPressed() {
		if (mPhotoShareLayout.getVisibility() == View.VISIBLE) {
			setShareViewVisibility(View.GONE);
			return;
		}
		super.onBackPressed();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mThumbView.updateAdapter(true);
		setImageBitmap(mCurrentPosition);
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			ActionBar a = getActionBar();
			if (a != null) {
				a.hide();
			}
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else {
			ActionBar a = getActionBar();
			if (a != null) {
				a.show();
			}
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	@Override
	public void onThumbnailCacheDone(String url, String key, int type, Bitmap bitmap) {
		mThumbView.invalidateAll();
		int size = 0;
		if (mFileList != null) {
			size = mFileList.size();
		}

		if (mCurrentPosition >= 0 && mCurrentPosition < size
				&& mFileList.get(mCurrentPosition).getThunbnailUrl().equals(url)) {
			mImageView.setImageBitmapResetBase(
					getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition), mHandler), true);
		} else if (mCurrentPosition > 0 && mCurrentPosition - 1 < size
				&& mFileList.get(mCurrentPosition - 1).getThunbnailUrl().equals(url)) {
			mImageView.setPrevImageBitmap(
					getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition - 1), mHandler));
		} else if (mCurrentPosition + 1 < size && mCurrentPosition + 1 >= 0
				&& mFileList.get(mCurrentPosition + 1).getThunbnailUrl().equals(url)) {
			mImageView.setNextImageBitmap(
					getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition + 1), mHandler));
		}
		mImageView.invalidate();
	}

	private void handleIntent(Intent intent) {
		mRemote = intent.getBooleanExtra(KEY_REMOTE, false);
		if (mRemote) {
			mCurrentName = intent.getStringExtra(KEY_PHOTO_CURRENT);
			String str = intent.getExtras().getString(KEY_JSON_STRING, "");
			try {
				JSONArray array = new JSONArray(str);
				List<FileInfo> list = FileScanner.readJSONArray(array, false);
				int index = 0;
				if (!TextUtils.isEmpty(mCurrentName)) {
					for (int i = 0; i < list.size(); i++) {
						FileInfo fi = list.get(i);
						if (fi.name.equals(mCurrentName)) {
							index = i;
							break;
						}
					}
				}
				mHandler.removeMessages(SCAN_FINISHED);
				Message msg = mHandler.obtainMessage(SCAN_FINISHED, index, 0, list);
				mHandler.sendMessage(msg);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		} else {
			String path = intent.getStringExtra(KEY_PHOTO_PATH);
			mCurrentName = intent.getStringExtra(KEY_PHOTO_CURRENT);
			runFileList(path);
		}
		invalidateOptionsMenu();
	}

	private void setActionBarMidtitleAndUpIndicator(String title, int upRes) {
		ActionBar bar = this.getActionBar();
		bar.setDisplayHomeAsUpEnabled(true);
		if (Build.VERSION.SDK_INT >= 18)
			bar.setHomeAsUpIndicator(upRes);
		bar.setTitle(R.string.back);
		bar.setDisplayShowTitleEnabled(true);
		bar.setDisplayShowHomeEnabled(false);
		TextView textview = new TextView(this);
		textview.setText(title);
		textview.setTextColor(Color.WHITE);
		textview.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.title_size));
		bar.setCustomView(textview,
				new ActionBar.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		bar.setDisplayShowCustomEnabled(true);
	}

	private void setImageBitmap(int index) {
		Log.i(TAG, "setImageBitmap: index = " + index);
		if (index < 0 || index >= mFileList.size()) {
			Log.w(TAG, "setImageBitmap indxe out of arrayindex");
			return;
		}

		mCurrentPosition = index;

		mThumbView.setSelectedItem(mCurrentPosition);
		mThumbView.invalidateAll();

		int count = mFileList.size();
		mImageView.setImageBitmapResetBase(
				getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition), mHandler), true);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		String title = Util.name2DateString(mFileList.get(mCurrentPosition).name);
		if (title == null){
			if(mFileList.get(mCurrentPosition).modifytime != -1)
				title = sdf.format(new Date(mFileList.get(mCurrentPosition).modifytime));
			else
				title = "";
		}
		setActionBarMidtitleAndUpIndicator(title, R.drawable.back);
		if (mCurrentPosition - 1 >= 0) {
			mImageView.setPrevImageBitmap(
					getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition - 1), mHandler));
		} else {
			mImageView.setPrevImageBitmap(null);
		}
		if (mCurrentPosition + 1 < count) {
			mImageView.setNextImageBitmap(
					getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition + 1), mHandler));
		} else {
			mImageView.setNextImageBitmap(null);
		}
		mImageView.invalidate();
	}

	private boolean runFileList(String filePath) {
		if (TextUtils.isEmpty(filePath))
			return false;

		Log.d(TAG, "runFileList, path=" + filePath);
		if (mFileScanner == null) {
			mFileScanner = new FileScanner() {
				@Override
				public void onResult(int type, String scanPath, ArrayList<FileInfo> fileList) {
					int index = 0;
					if (!TextUtils.isEmpty(mCurrentName)) {
						for (int i = 0; i < fileList.size(); i++) {
							FileInfo fi = fileList.get(i);
							if (fi.name.equals(mCurrentName)) {
								index = i;
								break;
							}
						}
					}
					mHandler.removeMessages(SCAN_FINISHED);
					Message msg = mHandler.obtainMessage(SCAN_FINISHED, index, 0, fileList);
					mHandler.sendMessage(msg);
				}
			};
		}

		mFileScanner.startScanner(filePath, FileMediaType.IMAGE_TYPE, false);

		return true;
	}

	private Bitmap getMediaBitmap(Context context, FileInfo fInfo, Handler handler) {
		synchronized (mImageLruCache) {
			Bitmap bitmap = mImageLruCache.get(fInfo.getFullPath());
			int type = fInfo.fileType;
			if (bitmap == null) {
				BitmapDecodeReq req = new BitmapDecodeReq(context, fInfo, handler);
				if (mDecodeWorkThread != null && !mDecodeWorkThread.isDuplicateWorking(req)) {
					mDecodeWorkThread.addReq(req);
				}
				if (type == FileMediaType.IMAGE_TYPE) {

					String url = fInfo.getThunbnailUrl();
					String key = fInfo.getFullPath();
					if(url.startsWith("http"))
						return ThumbnailCacheManager.instance().getThumbnail(url, key,
								ThumbnailCacheManager.TYPE_NET_THUMB);
					else
						return ThumbnailCacheManager.instance().getThumbnail(url, key,
								ThumbnailCacheManager.TYPE_LOCAL_THUMB);

				}
				return null;
			}
			return bitmap;
		}

	}

	private static final int SCAN_FINISHED = 1001;
	private static final int DECODE_START = 1002;
	private static final int DECODE_FINISH = 1003;
	final Handler mHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case SCAN_FINISHED:
				mFileList.clear();
				List<FileInfo> list = (List<FileInfo>) msg.obj;
				mFileList.addAll(list);
				int index = msg.arg1;
				mThumbView.updateAdapter(true);
				setImageBitmap(index);
				break;
			case DECODE_START:
				break;
			case DECODE_FINISH: {
				String path = (String) msg.obj;
				int size = 0;
				if (mFileList != null) {
					size = mFileList.size();
				}
				Log.i(TAG, "path : " + path);
				if (mCurrentPosition >= 0 && mCurrentPosition < size
						&& mFileList.get(mCurrentPosition).getFullPath().equals(path)) {
					mImageView.setImageBitmapResetBase(
							getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition), mHandler), true);
				} else if (mCurrentPosition > 0 && mCurrentPosition - 1 < size
						&& mFileList.get(mCurrentPosition - 1).getFullPath().equals(path)) {
					mImageView.setPrevImageBitmap(
							getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition - 1), mHandler));
				} else if (mCurrentPosition + 1 < size && mCurrentPosition + 1 >= 0
						&& mFileList.get(mCurrentPosition + 1).getFullPath().equals(path)) {
					mImageView.setNextImageBitmap(
							getMediaBitmap(PhotoActivity.this, mFileList.get(mCurrentPosition + 1), mHandler));
				}
				mImageView.invalidate();
				break;
			}
			}
		}
	};

	private class ImageGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			if (Math.abs(e.getX() - x) > 20)
				return false;
			if (mThumbView.getVisibility() == View.VISIBLE) {
				mThumbView.setVisibility(View.GONE);
			} else {
				mThumbView.setVisibility(View.VISIBLE);
				mThumbView.invalidateAll();
			}
			return false;
		}

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			Log.i(TAG, "mImageView.getScale() = " + mImageView.getScale());
			if (mImageView.getScale() == 1.0f) {
				mImageView.zoomIn(2.0f, 200);
			} else {
				mImageView.zoomOut(1.0f, 200);
			}
			return super.onDoubleTap(e);
		}

		float x = 0;

		public boolean onDown(MotionEvent e) {
			x = e.getX();
			return false;
		}
	}

	private OnBitmapMoveListener mOnBitmapMoveListener = new OnBitmapMoveListener() {

		@Override
		public void onMoveToPrev(ImageViewTouchBase parent) {
			if (mCurrentPosition > 0) {
				mCurrentPosition--;
				setImageBitmap(mCurrentPosition);
			}

		}

		@Override
		public void onMoveToNext(ImageViewTouchBase parent) {
			if (mCurrentPosition + 1 < mFileList.size()) {
				mCurrentPosition++;
				setImageBitmap(mCurrentPosition);
			}
		}
	};

	private class BitmapDecodeReq implements WorkReq, Match4Req {
		public FileInfo mFileInfo;
		public Handler mHandler;
		public Context mContext;
		public boolean mCancel = false;
		public String mFilePath;
		public String mUrl;

		public BitmapDecodeReq(Context context, FileInfo fInfo, Handler handler) {
			mFileInfo = fInfo;
			mHandler = handler;
			mContext = context;
			mUrl = fInfo.getUrl();
			mFilePath = fInfo.getFullPath();
		}

		@Override
		public boolean matchs(WorkReq req) {
			if (req instanceof BitmapDecodeReq) {
				BitmapDecodeReq req2 = (BitmapDecodeReq) req;
				if (mFilePath.equals(req2.mFilePath)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void execute() {
			sendMessage(DECODE_START);
			int type = mFileInfo.fileType;
			Bitmap bitmap = null;
			if (type == FileMediaType.IMAGE_TYPE) {
				bitmap = Util.makeBitmap(-1, mMaxNumOfPixels, mUrl);
			}
			if (bitmap == null) {
				bitmap = Util.sNullBitmap;
			}
			synchronized (mImageLruCache) {
				mImageLruCache.put(mFilePath, bitmap);
			}
			sendMessage(DECODE_FINISH);
		}

		private void sendMessage(int what) {
			if (mHandler != null) {
				Message msg = new Message();
				msg.what = what;
				msg.obj = mFilePath;
				mHandler.sendMessage(msg);
			}
		}

		@Override
		public void cancel() {
			mCancel = true;
		}
	}

	class WeakListener implements LruCache.OnWeakRemoveListener {
		@Override
		public void onWeakRemove(Object object) {
			try {
				if (object != null && object instanceof Bitmap) {
					((Bitmap) object).recycle();
				}
			} catch (Exception e) {
			}
		}
	}

	private void setShareViewVisibility(int visibility) {
		if (mPhotoShareLayout.getVisibility() == visibility) {
			return;
		}

		invalidateOptionsMenu();
	}

	private void shareToCarLife() {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("com.car.control", "com.car.control.share.SendPhotoActivity"));
		intent.setType(FileMediaType.getOpenMIMEType(FileMediaType.IMAGE_TYPE));
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.carlife_photo_video));
		intent.setAction(Intent.ACTION_SEND);
		String filepath = mFileList.get(mCurrentPosition).getFullPath();
		File file = new File(filepath);
		Uri u = Uri.fromFile(file);
		intent.putExtra(Intent.EXTRA_STREAM, u);
		try {
			PhotoActivity.this.startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sharePhoto2Facebook() {
		shareToOthers("com.facebook.katana");

//		String filepath = mFileList.get(mCurrentPosition).getFullPath();
//		File file = new File(filepath);
//		Uri uri = Uri.fromFile(file);
//
//		List<SharePhoto> photos = new ArrayList<SharePhoto>();
//		SharePhoto photo = new SharePhoto.Builder().setImageUrl(uri).build();
//		photos.add(photo);
//
//		SharePhotoContent content = new SharePhotoContent.Builder().addPhotos(photos).build();
//		ShareDialog.show(PhotoActivity.this, content);
	}

	private void shareToWechat() {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareImgUI"));
		intent.setType(FileMediaType.getOpenMIMEType(FileMediaType.IMAGE_TYPE));
		intent.putExtra("Kdescription", getString(R.string.carlife_photo_video));
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.carlife_photo_video));
		intent.setAction(Intent.ACTION_SEND);
		String filepath = mFileList.get(mCurrentPosition).getFullPath();
		File file = new File(filepath);
		Uri u = Uri.fromFile(file);
		intent.putExtra(Intent.EXTRA_STREAM, u);
		try {
			PhotoActivity.this.startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void shareToPengYouQuan() {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("com.tencent.mm", "com.tencent.mm.ui.tools.ShareToTimeLineUI"));
		intent.setType(FileMediaType.getOpenMIMEType(FileMediaType.IMAGE_TYPE));
		intent.putExtra("Kdescription", getString(R.string.carlife_photo_video));
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.carlife_photo_video));
		intent.setAction(Intent.ACTION_SEND);
		String filepath = mFileList.get(mCurrentPosition).getFullPath();
		File file = new File(filepath);
		Uri u = Uri.fromFile(file);
		intent.putExtra(Intent.EXTRA_STREAM, u);
		try {
			PhotoActivity.this.startActivity(intent);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void shareToOthers() {
		shareToOthers(null);
	}

	private void shareToOthers(String packageName) {
		Intent intent = new Intent();
		if (packageName != null) {
			intent.setPackage(packageName);
		}
		intent.setAction(Intent.ACTION_SEND_MULTIPLE);
		intent.setType(FileMediaType.getOpenMIMEType(FileMediaType.IMAGE_TYPE));
		intent.setAction(Intent.ACTION_SEND);
		String filepath = mFileList.get(mCurrentPosition).getFullPath();
		File file = new File(filepath);
		Uri u = Uri.fromFile(file);
		intent.putExtra(Intent.EXTRA_STREAM, u);
		try {
			PhotoActivity.this.startActivity(Intent.createChooser(intent, getResources().getText(R.string.share_flie)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
