
package com.car.control.browser;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

public class CarSurfaceView extends SurfaceView implements SurfaceHolder.Callback, OnClickListener,
		OnLongClickListener, CarScrollerListener {

	private static final String TAG = "CarSvc_CarSurfaceView";
	private SurfaceHolder mSurfaceHolder;
	private int mBgColor = Color.argb(0xFF, 24, 24, 24);
	private Bitmap mBgBmp;
	private Bitmap mHeadBmp, mTailBmp;
	private Object mRenderLock = new Object();
	private Object mUpdateLock = new Object();
	private Object mCacheLock;
	private int mChildCount = 0;

	public RenderThread mRenderThread;
	private Thread mCacheThread;
	private Boolean mCacheReady = false;
	private Bitmap mCacheBitmap;
	private int mCacheX, mCacheY;
	private int mCacheWidth, mCacheHeight;

	private CarScroller mScroller;
	private VelocityTracker mVelocityTracker;
	private int mTouchSlop;
	private int mMinimumVelocity;
	private int mMaximumVelocity;
	private int mPaddingRight;
	private int mPaddingLeft;
	private int mPaddingBottom;
	private int mPaddingTop;
	private int mScrollX, mLastMotionX, mTouchDownX;
	private int mScrollY, mLastMotionY, mTouchDownY;
	private int mMinX, mMaxX;
	private int mMinY, mMaxY;
	private int mItemPaddingX, mItemPaddingY;
	private int mAdapterWidth, mAdapterHeight;
	private int mScrollerSize;
	private int mInitSelectedItem;

	private int mRows, mCols;
	private int mPageRows, mPageCols;

	private Handler mHandler;
	private Timer mTimer;
	private TimerTask mTimerTask;

	private ISurfaceAdapter mISurfaceAdapter;

	protected static final int UPDATE_FLING = 0;

	private static final int SCROLLER_STROKE_WIDTH = 8;
	private static final int TOLERANCE_CLICK = 10;

	public static final int SURFACEVIEW_HORIZONTAL = 0;
	public static final int SURFACEVIEW_VERTICAL = 1;
	public static final int BACKGROUND_STILL = 0;
	public static final int BACKGROUND_FLOW = 1;

	private int mSurfaceViewMode = SURFACEVIEW_HORIZONTAL;
	private int mBackgroundMode = BACKGROUND_FLOW;

	/**
	 * The listener that receives notifications when an item is selected.
	 */
	OnItemSelectedListener mOnItemSelectedListener;

	/**
	 * The listener that receives notifications when an item is clicked.
	 */
	OnItemClickListener mOnItemClickListener;

	/**
	 * The listener that receives notifications when an item is long clicked.
	 */
	OnItemLongClickListener mOnItemLongClickListener;

	public CarSurfaceView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public CarSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public CarSurfaceView(Context context) {
		super(context);
		init();
	}

	private void init() {
		mSurfaceHolder = getHolder();
		mSurfaceHolder.addCallback(this);
		// mBmp = BitmapFactory.decodeFile(mFullPath);
		// mBgBmp = BitmapFactory.decodeResource(context.getResources(),
		// R.drawable.muwen);
		// mRenderLock = new Object();
		mCacheLock = new Object();
		mMinX = (int) (-0.5 * getWidth());
		mMaxX = (int) (2.5 * getWidth());
		initScrollView();

		setFocusable(true);
		setClickable(true);
		setLongClickable(true);
		setOnClickListener(this);
		setOnLongClickListener(this);

		mTimer = new Timer();
		// mHandler = new Handler() {
		// public void handleMessage(Message msg) {
		// switch (msg.what) {
		// case UPDATE_FLING:
		// break;
		// }
		// };
		// };

	}

	public ISurfaceAdapter getAdapter() {
		return mISurfaceAdapter;
	}

	public void setAdapter(ISurfaceAdapter adapter) {
		synchronized (mUpdateLock) {
			mISurfaceAdapter = adapter;
			computeRowsCols();
		}
		synchronized (mRenderLock) {
			mScrollX = 0;
			mScrollY = 0;

			mRenderLock.notify();
			if (mRenderThread != null) {
				mRenderThread.forceRefresh(true);
			}
		}
	}

	public void updateAdapter(boolean reset) {

		synchronized (mUpdateLock) {
			computeRowsCols();
		}
		synchronized (mRenderLock) {
			if (reset) {
				mScrollX = 0;
				mScrollY = 0;
			}
			mRenderLock.notify();
			if (mRenderThread != null) {
				mRenderThread.forceRefresh(true);
			}
		}
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this AdapterView has been
	 * clicked.
	 */
	public interface OnItemClickListener {

		/**
		 * Callback method to be invoked when an item in this AdapterView has been clicked.
		 * <p>
		 * Implementers can call getItemAtPosition(position) if they need to access the data
		 * associated with the selected item.
		 * 
		 * @param parent
		 *            The AdapterView where the click happened.
		 * @param view
		 *            The view within the AdapterView that was clicked (this will be a view provided
		 *            by the adapter)
		 * @param position
		 *            The position of the view in the adapter.
		 * @param id
		 *            The row id of the item that was clicked.
		 */
		void onItemClick(CarSurfaceView parent, int position, long id);
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has been clicked.
	 * 
	 * @param listener
	 *            The callback that will be invoked.
	 */
	public void setOnItemClickListener(OnItemClickListener listener) {
		mOnItemClickListener = listener;
	}

	/**
	 * @return The callback to be invoked with an item in this AdapterView has been clicked, or null
	 *         id no callback has been set.
	 */
	public final OnItemClickListener getOnItemClickListener() {
		return mOnItemClickListener;
	}

	/**
	 * Call the OnItemClickListener, if it is defined.
	 * 
	 * @param view
	 *            The view within the AdapterView that was clicked.
	 * @param position
	 *            The position of the view in the adapter.
	 * @param id
	 *            The row id of the item that was clicked.
	 * @return True if there was an assigned OnItemClickListener that was called, false otherwise is
	 *         returned.
	 */
	public boolean performItemClick(int position, long id) {
		if (mOnItemClickListener != null) {
			playSoundEffect(SoundEffectConstants.CLICK);
			mOnItemClickListener.onItemClick(this, position, id);
			return true;
		}

		return false;
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this view has been clicked
	 * and held.
	 */
	public interface OnItemLongClickListener {
		/**
		 * Callback method to be invoked when an item in this view has been clicked and held.
		 * 
		 * Implementers can call getItemAtPosition(position) if they need to access the data
		 * associated with the selected item.
		 * 
		 * @param parent
		 *            The AbsListView where the click happened
		 * @param view
		 *            The view within the AbsListView that was clicked
		 * @param position
		 *            The position of the view in the list
		 * @param id
		 *            The row id of the item that was clicked
		 * 
		 * @return true if the callback consumed the long click, false otherwise
		 */
		boolean onItemLongClick(CarSurfaceView parent, int position, long id);
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has been clicked and held
	 * 
	 * @param listener
	 *            The callback that will run
	 */
	public void setOnItemLongClickListener(OnItemLongClickListener listener) {
		if (!isLongClickable()) {
			setLongClickable(true);
		}
		mOnItemLongClickListener = listener;
	}

	/**
	 * @return The callback to be invoked with an item in this AdapterView has been clicked and
	 *         held, or null id no callback as been set.
	 */
	public final OnItemLongClickListener getOnItemLongClickListener() {
		return mOnItemLongClickListener;
	}

	/**
	 * Interface definition for a callback to be invoked when an item in this view has been
	 * selected.
	 */
	public interface OnItemSelectedListener {
		/**
		 * Callback method to be invoked when an item in this view has been selected.
		 * 
		 * Impelmenters can call getItemAtPosition(position) if they need to access the data
		 * associated with the selected item.
		 * 
		 * @param parent
		 *            The AdapterView where the selection happened
		 * @param view
		 *            The view within the AdapterView that was clicked
		 * @param position
		 *            The position of the view in the adapter
		 * @param id
		 *            The row id of the item that is selected
		 */
		void onItemSelected(CarSurfaceView parent, int position, long id);

		/**
		 * Callback method to be invoked when the selection disappears from this view. The selection
		 * can disappear for instance when touch is activated or when the adapter becomes empty.
		 * 
		 * @param parent
		 *            The AdapterView that now contains no selected item.
		 */
		void onNothingSelected(CarSurfaceView parent);
	}

	/**
	 * Register a callback to be invoked when an item in this AdapterView has been selected.
	 * 
	 * @param listener
	 *            The callback that will run
	 */
	public void setOnItemSelectedListener(OnItemSelectedListener listener) {
		mOnItemSelectedListener = listener;
	}

	public final OnItemSelectedListener getOnItemSelectedListener() {
		return mOnItemSelectedListener;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d(TAG, "surfaceChanged");
		computeRowsCols();
		// mScrollX = 0;
		// mScrollY = 0;

	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(TAG, "surfaceCreated");
		computeRowsCols();

		// mCacheThread = new CacheThread();
		// mCacheThread.start();
		if (mInitSelectedItem != 0) {
			setSelectedItem(mInitSelectedItem);
			mInitSelectedItem = 0;
		}

		mRenderThread = new RenderThread();
		mRenderThread.start();
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(TAG, "surfaceDestroyed");
		// TODO Auto-generated method stub
		mRenderThread.cancel();
		// Canvas canvas = null;
		// try {
		// canvas = mSurfaceHolder.lockCanvas();
		//
		// if (mBgBmp != null && !mBgBmp.isRecycled()) {
		// mBgBmp.recycle();
		// mBgBmp = null;
		// }
		// if (mFrontBmp != null && !mFrontBmp.isRecycled()) {
		// mFrontBmp.recycle();
		// mFrontBmp = null;
		// }
		// if (mBackBmp != null && !mBackBmp.isRecycled()) {
		// mBackBmp.recycle();
		// mBackBmp = null;
		// }
		// } finally {
		// // do this in a finally so that if an exception is thrown
		// // during the above, we don't leave the Surface in an
		// // inconsistent state
		// mSurfaceHolder.unlockCanvasAndPost(canvas);
		// }
	}

	private void initScrollView() {
		mScroller = new CarScroller(getContext());
		final ViewConfiguration configuration = ViewConfiguration.get(getContext());
		mTouchSlop = configuration.getScaledTouchSlop();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
		mScroller.setScrollerListener(this);
	}

	public int getChildCount() {
		return mChildCount;
	}

	private void touch_down(int x, int y) {
		Log.d(TAG, "synchronized : touch_down");
		synchronized (mRenderLock) {
			mLastMotionX = x;
			mLastMotionY = y;
			mTouchDownX = x;
			mTouchDownY = y;
			int index = findClickItem(x, y);
			if (index >= 0 && mISurfaceAdapter.getSelectedItem() != index) {
				mISurfaceAdapter.setSelectedItem(index);

				if (mOnItemSelectedListener != null) {
					mOnItemSelectedListener.onItemSelected(this, index, index);
				}
			}

			Log.d(TAG, "mRenderLock.notify()");
			mRenderLock.notify();
		}
		Log.d(TAG, "touch_down synchronized!");
	}

	private void touch_move(int x, int y) {
		// Log.d(TAG, "synchronized : touch_move");
		synchronized (mRenderLock) {
			int dx = (x - mLastMotionX);
			int dy = (y - mLastMotionY);

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				if (mScrollX < 0 || mScrollX > mMaxX - getWidth() / 2) {
					mScrollX -= dx / 2;
				} else {
					mScrollX -= dx;
				}
			} else {
				if (mScrollY < 0 || mScrollY > mMaxY - getHeight() / 2) {
					mScrollY -= dy / 2;
				} else {
					mScrollY -= dy;
				}
			}

			mLastMotionX = x;
			mLastMotionY = y;
			// Log.d(TAG, "mRenderLock.notify()");
			mRenderLock.notify();
		}
		// Log.d(TAG, "touch_move synchronized!");
	}

	private void touch_up(int x, int y) {
		Log.d(TAG, "synchronized : touch_up");
		synchronized (mRenderLock) {
			bounce();

			mLastMotionX = x;
			mLastMotionY = y;

			Log.d(TAG, "mRenderLock.notify()");
			mRenderLock.notify();
		}
		Log.d(TAG, "touch_up synchronized!");
	}

	public boolean onTouchEvent(MotionEvent event) {
		int x = (int) event.getX();
		int y = (int) event.getY();

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(event);

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}
			touch_down(x, y);
			break;
		case MotionEvent.ACTION_MOVE:
			touch_move(x, y);
			break;
		case MotionEvent.ACTION_UP:
			final VelocityTracker velocityTracker = mVelocityTracker;
			velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

			int initialVelocityX = (int) velocityTracker.getXVelocity();
			int initialVelocityY = (int) velocityTracker.getYVelocity();

			if (Math.abs(initialVelocityX) > mMinimumVelocity
					|| Math.abs(initialVelocityY) > mMinimumVelocity) {
				fling(-initialVelocityX, -initialVelocityY);
			}

			if (mVelocityTracker != null) {
				mVelocityTracker.recycle();
				mVelocityTracker = null;
			}
			touch_up(x, y);
			break;
		default:
			break;
		}
		return super.onTouchEvent(event);
	}

	// @Override
	// public boolean onKeyLongPress(int keyCode, KeyEvent event) {
	// // TODO Auto-generated method stub
	// return super.onKeyLongPress(keyCode, event);
	// }

	private boolean onNavigation(int keyCode) {
		if (mISurfaceAdapter != null) {
			int index = mISurfaceAdapter.getSelectedItem();
			int old = index;
			int count = mISurfaceAdapter.getItemCount();

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_LEFT:
					if (index >= mRows) {
						index -= mRows;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					if (index < count - mRows) {
						index += mRows;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_UP:
					if (index > 0) {
						index--;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_DOWN:
					if (index < count - 1) {
						index++;
					}
					break;
				}
			} else {
				switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_LEFT:
					if (index > 0) {
						index--;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					if (index < count - 1) {
						index++;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_UP:
					if (index >= mCols) {
						index -= mCols;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_DOWN:
					if (index < count - mCols) {
						index += mCols;
					}
					break;
				}
			}

			if (index != old) {
				synchronized (mRenderLock) {
					setSelectedItem(index);
					mRenderLock.notify();
				}
				return false;
			}
		}
		return true;
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			if (mISurfaceAdapter != null && mISurfaceAdapter.getSelectedItem() != -1) {
				int index = mISurfaceAdapter.getSelectedItem();
				if (index < mISurfaceAdapter.getItemCount() && mOnItemClickListener != null) {
					mOnItemClickListener.onItemClick(this, index, index);
				}
			}
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
			return onNavigation(keyCode);
		}
		return super.onKeyDown(keyCode, event);
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		return super.onKeyUp(keyCode, event);
	}

	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			mLastMotionX = mScrollX;
			mLastMotionY = mScrollY;
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();
			if (getChildCount() > 0) {
				mScrollX = clamp(x, getWidth() - mPaddingRight - mPaddingLeft, 0);
				mScrollY = clamp(y, getHeight() - mPaddingBottom - mPaddingTop, 0);
			} else {
				mScrollX = x;
				mScrollY = y;
			}
			// if (oldX != mScrollX || oldY != mScrollY) {
			// onScrollChanged(mScrollX, mScrollY, oldX, oldY);
			// }

			// Keep on drawing until the animation has finished.
			// postInvalidate();
		}
	}

	public void bounce() {
		float totalDistance;
		Log.d(TAG, "bounce: mScrollX=" + mScrollX + ", mScrollY=" + mScrollY);
		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			if (mScrollX < 0 || mScrollX > mMaxX - getWidth() / 2) {

				if (mScrollX < 0) {
					totalDistance = -mScrollX;
				} else {
					totalDistance = mMaxX - getWidth() / 2 - mScrollX;
				}
				mScroller.bounce(totalDistance, mScrollX, 0, 0);
			}
		} else {
			if (mScrollY < 0 || mScrollY > mMaxY - getHeight() / 2) {

				if (mScrollY < 0) {
					totalDistance = -mScrollY;
				} else {
					totalDistance = mMaxY - getHeight() / 2 - mScrollY;
				}
				mScroller.bounce(0, 0, totalDistance, mScrollY);
			}
		}
	}

	public void fling(int velocityx, int velocityy) {
		if (true) {

			int width = getWidth() - mPaddingRight - mPaddingLeft;
			int right = 0;
			int height = getHeight() - mPaddingBottom - mPaddingTop;
			int bottom = 0;

			mScroller.fling(mScrollX, mScrollY, velocityx, velocityy, mMinX, mMaxX, mMinY, mMaxY);

			final boolean movingRight = velocityx > 0;
			final boolean movingBottom = velocityy > 0;

			// TimerTask tt = new TimerTask() {
			// @Override
			// public void run() {
			// mHandler.sendEmptyMessage(UPDATE_FLING);
			// }
			// };
			// mTimer.schedule(tt, 0, 30);
		}
	}

	private int clamp(int n, int my, int child) {
		if (my >= child || n < 0) {
			return 0;
		}
		if ((my + n) > child) {
			return child - my;
		}
		return n;
	}

	class CacheThread extends Thread {

		private Canvas mCanvas;
		private Paint mPaint;
		private Boolean mCacheThreadRunning = true;
		private Bitmap mBitmapFree = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);

		private int mLastx, mLasty;

		public CacheThread() {
			super("SV_Cache");
			mCanvas = new Canvas();
			mPaint = new Paint();

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				mCacheWidth = Math.min(getWidth() * 3, mAdapterWidth);
				mCacheHeight = mAdapterHeight;
			} else {
				mCacheWidth = mAdapterWidth;
				mCacheHeight = Math.min(getHeight() * 3, mAdapterHeight);
			}

			mCacheBitmap = Bitmap.createBitmap(mCacheWidth, mCacheHeight, Bitmap.Config.RGB_565);
			mCanvas.setBitmap(mCacheBitmap);
		}

		public void UpdateCacheSize() {
			if (mCacheWidth != getWidth() || mCacheHeight != getHeight()) {

				if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
					mCacheWidth = Math.min(getWidth() * 3, mAdapterWidth);
					mCacheHeight = mAdapterHeight;
				} else {
					mCacheWidth = mAdapterWidth;
					mCacheHeight = Math.min(getHeight() * 3, mAdapterHeight);
				}

				if (mCacheBitmap != null) {
					mCanvas.setBitmap(mBitmapFree);
					mCacheBitmap.recycle();
					mCacheBitmap = null;
				}

				mCacheBitmap = Bitmap
						.createBitmap(mCacheWidth, mCacheHeight, Bitmap.Config.RGB_565);
				mCanvas.setBitmap(mCacheBitmap);
			}

		}

		public void run() {

			while (mCacheThreadRunning) {

				computeScroll();

				mCacheX = mScrollX;
				mCacheY = mScrollY;

				mLastx = mCacheX;
				mLasty = mCacheY;

				doDrawCache(mCanvas);

				synchronized (mRenderLock) {
					mRenderLock.notify();
				}

				synchronized (mCacheLock) {
					try {
						mCacheLock.wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		}

		void doDrawCache(Canvas canvas) {
			drawBackground(canvas);
			drawItems(canvas);
			mCacheReady = true;
		}

		void drawItems(Canvas canvas) {
			int itemw = mISurfaceAdapter.getItemWidth();
			int itemh = mISurfaceAdapter.getItemHeight();
			int cols = mCacheWidth / itemw;
			int rows = mCacheHeight / itemh;
			int offset;
			int start;

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				if (mLastx > 0) {
					offset = mLastx - mLastx / itemw * itemw;
					start = mLastx / itemw;
				} else {
					offset = mLastx;
					start = 0;
				}
				for (int i = start; i < start + cols + 1; i++) {
					for (int j = 0; j < rows; j++) {
						mISurfaceAdapter.drawItem(i * rows + j, canvas, -offset + (i - start)
								* itemw, j * itemh, 0, 1);
					}
				}
			} else {

				if (mLasty > 0) {
					offset = mLasty - mLasty / itemh * itemh;
					start = mLasty / itemh;
				} else {
					offset = mLasty;
					start = 0;
				}
				for (int i = start; i < start + rows + 1; i++) {
					for (int j = 0; j < cols; j++) {
						mISurfaceAdapter.drawItem(i * cols + j, canvas, j * itemw, -offset
								+ (i - start) * itemh, 0, 1);
					}
				}
			}
		}

		void drawBackground(Canvas canvas) {

			canvas.drawColor(mBgColor);

			if (mBgBmp == null) {
				return;
			}

			int thisW = mCacheWidth;
			int thisH = mCacheHeight;
			int bgW = mBgBmp.getWidth();
			int bgH = mBgBmp.getHeight();

			int num;

			if (mBackgroundMode == BACKGROUND_STILL) {
				if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
					if (bgW >= thisW) {
						num = 2;
					} else {
						num = thisW / bgW + 1;
					}

					for (int i = 0; i < num; i++) {
						canvas.drawBitmap(mBgBmp, i * bgW, 0, mPaint);
					}
				} else {
					if (bgH >= thisH) {
						num = 2;
					} else {
						num = thisH / bgH + 1;
					}

					for (int i = 0; i < num; i++) {
						canvas.drawBitmap(mBgBmp, 0, i * bgH, mPaint);
					}
				}
			} else {
				if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
					if (bgW >= thisW) {
						num = 2;
					} else {
						num = thisW / bgW + 2;
					}

					int start = (int) (mLastx - mLastx / bgW * bgW);
					for (int i = 0; i < num; i++) {
						canvas.drawBitmap(mBgBmp, -start + i * bgW, 0, mPaint);
					}
				} else {
					if (bgH >= thisH) {
						num = 2;
					} else {
						num = thisH / bgH + 2;
					}

					int start = (int) (mLasty - mLasty / bgH * bgH);
					for (int i = 0; i < num; i++) {
						canvas.drawBitmap(mBgBmp, 0, -start + i * bgH, mPaint);
					}
				}
			}
		}

	}

	class RenderThread extends Thread {
		Paint mPaint;
		Paint mScrollerPaint;

		private boolean mForceRefresh = false;
		private boolean mRunning;
		private int mLastx = -1;
		private int mLasty = -1;
		int i = 0;
		long lasttime = 0;
		long time;
		int fps = 0;
		int num = 0;
		int alpha = 0xAA;
		int fade = 15;

		public RenderThread() {
			super("SV_Render");
			mRunning = true;
			mPaint = new Paint();
			mScrollerPaint = new Paint();
			mScrollerPaint.setColor(Color.DKGRAY);
			mScrollerPaint.setAlpha(alpha);
			mScrollerPaint.setStrokeCap(Paint.Cap.ROUND);
			mScrollerPaint.setStrokeWidth(SCROLLER_STROKE_WIDTH);
		}

		public void cancel() {
			// TODO Auto-generated method stub
			mRunning = false;
		}

		public void forceRefresh(boolean refresh) {
			mForceRefresh = refresh;
		}

		public void run() {

			while (mRunning) {

				// Log.d(TAG, "synchronized : run");
				synchronized (mRenderLock) {

					computeScroll();

					if (!mForceRefresh && mLastx == mScrollX && mLasty == mScrollY) {

						if (fade > 0) {
							stepFade();
						} else {

							try {
								// Log.e(TAG, "-----------------mRenderLock.wait()------------");
								mRenderLock.wait();
								// Log.e(TAG,
								// "-----------------awake fome mRenderLock.wait()------------");
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					} else {
						if (mForceRefresh) {
							mForceRefresh = false;
						}
						if (fade < 15) {
							resetFade();
						}
					}

				}
				// Log.d(TAG, "run synchronized!");

				Canvas canvas = null;
				try {
					canvas = mSurfaceHolder.lockCanvas();
					if (canvas != null) {
						doDraw(canvas);
					}
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (canvas != null) {
						mSurfaceHolder.unlockCanvasAndPost(canvas);
					}
				}
			}
		}

		void stepFade() {
			if (fade > 10) {

			} else {
				alpha -= 0xAA / 10;
				mScrollerPaint.setAlpha(alpha);
			}

			fade--;
		}

		void resetFade() {
			fade = 15;
			alpha = 0xAA;
			mScrollerPaint.setAlpha(alpha);
		}

		private void doDraw(Canvas canvas) {

			// Log.d(TAG, "---doDraw---");
			synchronized (mUpdateLock) {
				mLastx = mScrollX;
				mLasty = mScrollY;

				drawBackground(canvas);

				drawItems(canvas);

				drawScroller(canvas);

				// drawFPS(canvas);
			}
		}

		void drawFPS(Canvas canvas) {
			time = System.currentTimeMillis();
			if (lasttime == 0) {
				lasttime = time;
			}
			if (time - lasttime > 1000) {
				fps = num;
				num = 0;
				lasttime = time;
			}
			num++;
			mPaint.setColor(Color.RED);
			// canvas.drawText("SurfaceView", 0, 18, paint);
			canvas.drawText("FPS:" + fps, 10, 10, mPaint);
			i++;
		}

		void drawScroller(Canvas canvas) {
			int offset;
			if (mScrollerSize == 0) {
				return;
			}

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				offset = (getWidth() - mScrollerSize) * mLastx / (mAdapterWidth - getWidth());
				canvas.drawLine(offset + SCROLLER_STROKE_WIDTH / 2, getHeight()
						- SCROLLER_STROKE_WIDTH / 2, offset + mScrollerSize - SCROLLER_STROKE_WIDTH
						/ 2, getHeight() - SCROLLER_STROKE_WIDTH / 2, mScrollerPaint);
			} else {
				offset = (getHeight() - mScrollerSize) * mLasty / (mAdapterHeight - getHeight());
				canvas.drawLine(getWidth() - SCROLLER_STROKE_WIDTH / 2, offset
						+ SCROLLER_STROKE_WIDTH / 2, getWidth() - SCROLLER_STROKE_WIDTH / 2, offset
						+ mScrollerSize - SCROLLER_STROKE_WIDTH / 2, mScrollerPaint);
			}
		}

		void drawItems(Canvas canvas) {

			if (mISurfaceAdapter == null || mISurfaceAdapter.getItemCount() <= 0) {
				return;
			}

			int itemw = mISurfaceAdapter.getItemWidth();
			int itemh = mISurfaceAdapter.getItemHeight();
			int offset;
			int start;

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				if (mLastx > 0) {
					offset = mLastx - mLastx / itemw * itemw;
					start = mLastx / itemw;
				} else {
					offset = mLastx;
					start = 0;
				}
				for (int i = start; i < start + mPageCols + 1; i++) {
					for (int j = 0; j < mPageRows; j++) {
						if (i * mPageRows + j >= mISurfaceAdapter.getItemCount()) {
							return;
						}
						mISurfaceAdapter.drawItem(i * mPageRows + j, canvas, -offset + (i - start)
								* itemw, j * itemh + (2 * j + 1) * mItemPaddingY + mPaddingTop, 0,
								1);
					}
				}
			} else {

				if (mLasty > 0) {
					offset = mLasty - mLasty / itemh * itemh;
					start = mLasty / itemh;
				} else {
					offset = mLasty;
					start = 0;
				}
				for (int i = start; i < start + mPageRows + 1; i++) {
					for (int j = 0; j < mPageCols; j++) {
						if (i * mPageCols + j >= mISurfaceAdapter.getItemCount()) {
							return;
						}
						mISurfaceAdapter.drawItem(i * mPageCols + j, canvas, j * itemw
								+ (2 * j + 1) * mItemPaddingX + mPaddingLeft, -offset + (i - start)
								* itemh, 0, 1);
					}
				}
			}
		}

		void drawBackground(Canvas canvas) {

			// canvas.drawColor(mBgColor);

			int thisW = getWidth();
			int thisH = getHeight();

			if (mBgBmp == null) {
				canvas.drawColor(mBgColor);
			} else {

				int bgW = mBgBmp.getWidth();
				int bgH = mBgBmp.getHeight();
				int num;

				if (mBackgroundMode == BACKGROUND_STILL) {
					if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
						if (bgW >= thisW) {
							num = 2;
						} else {
							num = thisW / bgW + 1;
						}

						for (int i = 0; i < num; i++) {
							canvas.drawBitmap(mBgBmp, i * bgW, 0, mPaint);
						}
					} else {
						if (bgH >= thisH) {
							num = 2;
						} else {
							num = thisH / bgH + 1;
						}

						for (int i = 0; i < num; i++) {
							canvas.drawBitmap(mBgBmp, 0, i * bgH, mPaint);
						}
					}
				} else {
					if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
						if (bgW >= thisW) {
							num = 2;
						} else {
							num = thisW / bgW + 2;
						}

						int start = (int) (mLastx - mLastx / bgW * bgW);
						for (int i = 0; i < num; i++) {
							canvas.drawBitmap(mBgBmp, -start + i * bgW, 0, mPaint);
						}
					} else {
						if (bgH >= thisH) {
							num = 2;
						} else {
							num = thisH / bgH + 2;
						}

						int start = (int) (mLasty - mLasty / bgH * bgH);
						for (int i = 0; i < num; i++) {
							canvas.drawBitmap(mBgBmp, 0, -start + i * bgH, mPaint);
						}
					}
				}
			}

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				int startx;
				if (mHeadBmp != null && mLastx < 0) {
					startx = -mHeadBmp.getWidth() - mLastx;
					canvas.drawBitmap(mHeadBmp, startx, 0, mPaint);
				}
				if (mTailBmp != null && mLastx > mMaxX - getWidth() / 2) {
					startx = getWidth() - mLastx + mMaxX - getWidth() / 2;
					canvas.drawBitmap(mTailBmp, startx, 0, mPaint);
				}
			} else {
				int starty;
				if (mHeadBmp != null && mLasty < 0) {
					starty = -mHeadBmp.getHeight() - mLasty;
					canvas.drawBitmap(mHeadBmp, 0, starty, mPaint);
				}
				if (mTailBmp != null && mLasty > mMaxY - getHeight() / 2) {
					starty = getHeight() - mLasty + mMaxY - getHeight() / 2;
					canvas.drawBitmap(mTailBmp, 0, starty, mPaint);
				}
			}
		}
	}

	public void onBounceBegin() {
		// TODO Auto-generated method stub

	}

	public void onBounceEnd() {
		// TODO Auto-generated method stub

	}

	public void onFlingBegin() {
		// TODO Auto-generated method stub

	}

	public void onFlingEnd() {
		Log.d(TAG, "synchronized : onFlingEnd");
		synchronized (mRenderLock) {
			mScrollX = mScroller.getCurrX();
			mScrollY = mScroller.getCurrY();
			bounce();
			Log.d(TAG, "mRenderLock.notify()");
			mRenderLock.notify();
		}
		Log.d(TAG, "onFlingEnd synchronized!");
	}

	public int getBackgroundMode() {
		return mBackgroundMode;
	}

	public void setBackgroundMode(int mode) {
		mBackgroundMode = mode;
	}

	public int getSurfaceViewMode() {
		return mSurfaceViewMode;
	}

	public void setSurfaceViewMode(int mode) {
		mSurfaceViewMode = mode;
	}

	private void computeRowsCols() {
		int w = getWidth() - mPaddingLeft - mPaddingRight;
		int h = getHeight() - mPaddingBottom - mPaddingTop;

		if (w == 0 || h == 0 || mISurfaceAdapter == null) {
			return;
		}

		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();

		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			mPageRows = (h) / itemh;
			mPageCols = (w + itemw - 1) / itemw;
			mRows = mISurfaceAdapter.getItemCount() < mPageRows ? mISurfaceAdapter.getItemCount()
					: mPageRows;
			mCols = mISurfaceAdapter.getItemCount() < mPageRows ? 1 : (mISurfaceAdapter
					.getItemCount() + mRows - 1)
					/ mRows;
			mMinX = (int) (-0.5 * w);
			mMaxX = (int) (mCols * itemw > w ? mCols * itemw - 0.5 * w : 0.5 * w);
			mMinY = 0;
			mMaxY = 0;
			mAdapterWidth = mCols * itemw;
			mAdapterHeight = h;
			if (mAdapterWidth <= w) {
				mScrollerSize = 0;
			} else {
				mScrollerSize = w * w / mAdapterWidth;
			}
			mItemPaddingX = 0;
			mItemPaddingY = (h - itemh * mPageRows) / (mPageRows * 2);

		} else {
			mPageCols = (w) / itemw;
			mPageRows = (h + itemh - 1) / itemh;
			mCols = mISurfaceAdapter.getItemCount() < mPageCols ? mISurfaceAdapter.getItemCount()
					: mPageCols;
			mRows = mISurfaceAdapter.getItemCount() < mPageCols ? 1 : (mISurfaceAdapter
					.getItemCount() + mCols - 1)
					/ mCols;
			mMinX = 0;
			mMaxX = 0;
			mMinY = (int) (-0.5 * h);
			mMaxY = (int) (mRows * itemh > h ? mRows * itemh - 0.5 * h : 0.5 * h);
			mAdapterWidth = w;
			mAdapterHeight = mRows * itemh;
			if (mAdapterHeight <= h) {
				mScrollerSize = 0;
			} else {
				mScrollerSize = h * h / mAdapterHeight;
			}
			mItemPaddingX = (w - itemw * mPageCols) / (mPageCols * 2);
			mItemPaddingY = 0;
		}
	}

	public void setBgColor(int color) {
		mBgColor = color;
	}

	public void setBgBmp(Bitmap bmp) {
		synchronized (mUpdateLock) {
			mBgBmp = bmp;
		}
	}

	public void setHeadBackground(Bitmap bmp) {
		synchronized (mUpdateLock) {
			mHeadBmp = bmp;
		}
	}

	public void setTailBackground(Bitmap bmp) {
		synchronized (mUpdateLock) {
			mTailBmp = bmp;
		}
	}

	public Bitmap getBgBmp() {
		return mBgBmp;
	}

	public Bitmap getHeadBackground() {
		return mHeadBmp;
	}

	public Bitmap getTailBackground() {
		return mTailBmp;
	}

	public void onClick(View v) {
		Log.d(TAG, "onClick");
		if (mOnItemClickListener == null || Math.abs(mLastMotionX - mTouchDownX) > TOLERANCE_CLICK
				|| Math.abs(mLastMotionY - mTouchDownY) > TOLERANCE_CLICK) {
			return;
		}

		int index = findClickItem(mLastMotionX, mLastMotionY);
		if (index >= 0 && index < mISurfaceAdapter.getItemCount()) {
			mOnItemClickListener.onItemClick(this, index, index);
		}
	}

	public boolean onLongClick(View v) {
		Log.d(TAG, "onLongClick");
		if (mOnItemLongClickListener == null
				|| Math.abs(mLastMotionX - mTouchDownX) > TOLERANCE_CLICK
				|| Math.abs(mLastMotionY - mTouchDownY) > TOLERANCE_CLICK) {
			return false;
		}

		int index = findClickItem(mLastMotionX, mLastMotionY);
		if (index >= 0 && index < mISurfaceAdapter.getItemCount()) {
			return mOnItemLongClickListener.onItemLongClick(this, index, index);
		}

		return false;
	}

	private int findClickItem(int lastx, int lasty) {
		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();
		int count = mISurfaceAdapter.getItemCount();
		int offset;
		int start;

		Rect rc = new Rect(0, 0, itemw, itemh);

		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			if (mScrollX > 0) {
				offset = mScrollX - mScrollX / itemw * itemw;
				start = mScrollX / itemw;
			} else {
				offset = mScrollX;
				start = 0;
			}
			for (int i = start; i < start + mPageCols + 1; i++) {
				for (int j = 0; j < mPageRows; j++) {
					rc.offsetTo(-offset + (i - start) * itemw, j * itemh + (2 * j + 1)
							* mItemPaddingY + mPaddingTop);
					if (rc.contains(lastx, lasty)) {
						if (i * mPageRows + j < count) {
							return i * mPageRows + j;
						} else {
							return -1;
						}
					}
				}
			}
		} else {
			if (mScrollY > 0) {
				offset = mScrollY - mScrollY / itemh * itemh;
				start = mScrollY / itemh;
			} else {
				offset = mScrollY;
				start = 0;
			}
			for (int i = start; i < start + mPageRows + 1; i++) {
				for (int j = 0; j < mPageCols; j++) {
					rc.offsetTo(j * itemw + (2 * j + 1) * mItemPaddingX + mPaddingLeft, -offset
							+ (i - start) * itemh);
					if (rc.contains(lastx, lasty)) {
						if (i * mPageCols + j < count) {
							return i * mPageCols + j;
						} else {
							return -1;
						}
					}
				}
			}
		}
		return -1;
	}

	public void setViewPadding(int left, int top, int right, int bottom) {
		mPaddingLeft = left;
		mPaddingTop = top;
		mPaddingRight = right;
		mPaddingBottom = bottom;
	}

	public void setSelectedItem(int index) {
		int w = getWidth() - mPaddingLeft - mPaddingRight;
		int h = getHeight() - mPaddingBottom - mPaddingTop;

		if (w == 0 || h == 0 || mISurfaceAdapter == null) {
			mInitSelectedItem = index;
			return;
		}

		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();

		if (mPageRows == 0 && mPageCols == 0) {
			computeRowsCols();
		}

		Log.d(TAG, "synchronized : setSelectedItem");
		synchronized (mRenderLock) {

			if (index >= 0 && index < mISurfaceAdapter.getItemCount()) {
				mISurfaceAdapter.setSelectedItem(index);
				if (mOnItemSelectedListener != null) {
					mOnItemSelectedListener.onItemSelected(this, index, index);
				}
				if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
					int cols = index / mPageRows;
					int x = cols * itemw;
					if (mScrollX < x && mScrollX > x - (w - itemw)) {

					} else {
						x = x > mMaxX - w / 2 ? mMaxX - w / 2 : x;
						mScrollX = x;
						mScrollY = 0;
					}

				} else {
					int rows = index / mPageCols;
					int y = rows * itemh;
					if (mScrollY < y && mScrollY > y - (h - itemh)) {

					} else {
						y = y > mMaxY - h / 2 ? mMaxY - h / 2 : y;
						mScrollX = 0;
						mScrollY = y;
					}
				}
				Log.d(TAG, "mRenderLock.notify()");
				mRenderLock.notify();
				if (mRenderThread != null) {
					mRenderThread.forceRefresh(true);
				}
			}
		}
		Log.d(TAG, "setSelectedItem synchronized!");
	}

	public void invalidateItem(int index) {
		synchronized (mRenderLock) {
			if (mRenderThread != null) {
				mRenderThread.forceRefresh(true);
			}
			mRenderLock.notify();
		}
	}

	public void invalidateAll() {
		synchronized (mRenderLock) {
			if (mRenderThread != null) {
				mRenderThread.forceRefresh(true);
			}
			mRenderLock.notify();
		}
	}
}
