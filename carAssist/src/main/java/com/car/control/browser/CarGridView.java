
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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

public class CarGridView extends View implements OnClickListener, OnLongClickListener,
		CarScrollerListener {

	private static final String TAG = "CarSvc_CarGridView";
	private int mBgColor = Color.argb(0, 0, 0, 0);
	private Bitmap mBgBmp;
	private Bitmap mHeadBmp, mTailBmp;
	private int mChildCount = 0;

	private Bitmap mCacheBitmap;
	private int mCacheX, mCacheY;
	private int mCacheWidth, mCacheHeight;

	private boolean mMouseDown = false;
	private boolean mMouseMoving = false;
	private boolean mShowScroller = false;
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
	private float mItemPaddingX, mItemPaddingY;
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

	private static final int SCROLLER_STROKE_WIDTH = 6;
	private static final int TOLERANCE_CLICK = 10;

	public static final int SURFACEVIEW_HORIZONTAL = 0;
	public static final int SURFACEVIEW_VERTICAL = 1;
	public static final int BACKGROUND_STILL = 0;
	public static final int BACKGROUND_FLOW = 1;

	private static final int FLING_BORDER = 8; // width / 8, or height / 8

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

	public CarGridView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public CarGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public CarGridView(Context context) {
		super(context);
		init();
	}

	private void init() {

		mPaint = new Paint();
		mScrollerPaint = new Paint();
		mScrollerPaint.setColor(Color.LTGRAY);
		mScrollerPaint.setAlpha(alpha);
		mScrollerPaint.setStrokeCap(Paint.Cap.ROUND);
		mScrollerPaint.setStrokeWidth(SCROLLER_STROKE_WIDTH);
		mMinX = (int) (-getWidth() / FLING_BORDER);
		mMaxX = (int) (getWidth() / FLING_BORDER);
		initScrollView();

		setFocusable(true);
		setClickable(true);
		setLongClickable(true);
		setOnClickListener(this);
		setOnLongClickListener(this);

		mTimer = new Timer();

	}

	public ISurfaceAdapter getAdapter() {
		return mISurfaceAdapter;
	}

	public void setAdapter(ISurfaceAdapter adapter) {

		mISurfaceAdapter = adapter;
		computeRowsCols();

		if (true) {
			mScrollX = 0;
			mScrollY = 0;
		}

		postInvalidate();
	}

	public void updateAdapter(boolean reset) {

		computeRowsCols();

		if (reset) {
			mScrollX = 0;
			mScrollY = 0;
		}

		postInvalidate();
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
		void onItemClick(CarGridView parent, int position, long id);
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
		boolean onItemLongClick(CarGridView parent, int position, long id);
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
		void onItemSelected(CarGridView parent, int position, long id);

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

	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		computeRowsCols();

		if (mInitSelectedItem != 0) {
			setSelectedItem(mInitSelectedItem);
			mInitSelectedItem = 0;
		} else {
			if (changed)
				setSelectedItem(0);
		}
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

		invalidate();
	}

	private void touch_move(int x, int y) {

		int dx = (x - mLastMotionX);
		int dy = (y - mLastMotionY);

		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			if (mScrollX <= 0 || mScrollX >= mMaxX - getWidth() / FLING_BORDER) {
				mScrollX -= dx / 2;
			} else {
				mScrollX -= dx;
			}
		} else {
			if (mScrollY <= 0 || mScrollY >= mMaxY - getHeight() / FLING_BORDER) {
				mScrollY -= dy / 2;
			} else {
				mScrollY -= dy;
			}
		}

		mLastMotionX = x;
		mLastMotionY = y;

		invalidate();
	}

	private void touch_up(int x, int y) {

		bounce();

		mLastMotionX = x;
		mLastMotionY = y;

		invalidate();
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
			mMouseDown = true;
			mMouseMoving = false;
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}			
			touch_down(x, y);
			break;
		case MotionEvent.ACTION_MOVE:
			if(Math.abs(x-mTouchDownX)>10 || Math.abs(y-mTouchDownY)>10){
				mMouseMoving = true;
			}

			touch_move(x, y);
			break;
		case MotionEvent.ACTION_UP:
			mMouseDown = false;
			mMouseMoving = false;
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
				mShowScroller = true;
				setSelectedItem(index);
				invalidate();
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

	public boolean compute() {
		if (mScroller.computeScrollOffset()) {
			// mLastMotionX = mScrollX;
			// mLastMotionY = mScrollY;
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
			int w = getWidth() - mPaddingLeft - mPaddingRight;
			int h = getHeight() - mPaddingBottom - mPaddingTop;
			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				if (x < 0 || x > mMaxX - w / FLING_BORDER) {
					mScroller.crossBorder();
				}
			} else {
				if (y < 0 || y > mMaxY - h / FLING_BORDER) {
					mScroller.crossBorder();
				}
			}

			// Keep on drawing until the animation has finished.
			postInvalidate();
			return true;
		}
		return false;
	}

	public void bounce() {
		float totalDistance;
		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			if (mScrollX < 0 || mScrollX > mMaxX - getWidth() / FLING_BORDER) {

				if (mScrollX < 0) {
					totalDistance = -mScrollX;
				} else {
					totalDistance = mMaxX - getWidth() / FLING_BORDER - mScrollX;
				}
				mScroller.bounce(totalDistance, mScrollX, 0, 0);
			}
		} else {
			if (mScrollY < 0 || mScrollY > mMaxY - getHeight() / FLING_BORDER) {

				if (mScrollY < 0) {
					totalDistance = -mScrollY;
				} else {
					totalDistance = mMaxY - getHeight() / FLING_BORDER - mScrollY;
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

	Paint mPaint;
	Paint mScrollerPaint;
	int alpha = 255;
	int fade = 10;

	int i = 0;
	long lasttime = 0;
	long time;
	int fps = 0;
	int num = 0;

	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (!compute() && !mMouseMoving && !mShowScroller) {
			stepFade();
		} else {
			resetFade();
		}
		doDraw(canvas);
	}

	void stepFade() {
		if (fade > 5) {

		} else if (alpha > 0) {
			alpha -= 255 / 5;
			mScrollerPaint.setAlpha(alpha);
		}

		fade--;
		if (fade > 0) {
			postInvalidate();
		}
	}

	void resetFade() {
		if (mShowScroller) {
			mShowScroller = false;
			postInvalidate();
		}
		fade = 10;
		alpha = 255;
		mScrollerPaint.setAlpha(alpha);
	}

	private void doDraw(Canvas canvas) {

		drawBackground(canvas);
		drawItems(canvas);
		drawMasks(canvas);
		drawScroller(canvas);
		// drawFPS(canvas);
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
		canvas.drawText("FPS:" + fps, 10, 10, mPaint);
		i++;
	}

	void drawScroller(Canvas canvas) {
		int offset;
		if (mScrollerSize == 0) {
			return;
		}

		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			if (mAdapterWidth <= getWidth())
				return;
			offset = (getWidth() - mScrollerSize) * mScrollX / (mAdapterWidth - getWidth());
			canvas.drawLine(offset + SCROLLER_STROKE_WIDTH / 2, getHeight() - SCROLLER_STROKE_WIDTH
					/ 2, offset + mScrollerSize - SCROLLER_STROKE_WIDTH / 2, getHeight()
					- SCROLLER_STROKE_WIDTH / 2, mScrollerPaint);
		} else {
			if (mAdapterHeight <= getHeight())
				return;
			offset = (getHeight() - mScrollerSize) * mScrollY / (mAdapterHeight - getHeight());
			canvas.drawLine(getWidth() - SCROLLER_STROKE_WIDTH / 2, offset + SCROLLER_STROKE_WIDTH
					/ 2, getWidth() - SCROLLER_STROKE_WIDTH / 2, offset + mScrollerSize
					- SCROLLER_STROKE_WIDTH / 2, mScrollerPaint);
		}
	}

	void drawItems(Canvas canvas) {

		if (mISurfaceAdapter == null || mISurfaceAdapter.getItemCount() <= 0) {
			return;
		}

		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();
		if (itemw == -1) {
			itemw = getWidth() - mPaddingLeft - mPaddingRight;
		}
		if (itemh == -1) {
			itemh = getHeight() - mPaddingTop - mPaddingBottom;
			;
		}
		int offset;
		int start;

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
					if (i * mPageRows + j >= mISurfaceAdapter.getItemCount()) {
						return;
					}
					mISurfaceAdapter.drawItem(i * mPageRows + j, canvas, -offset + (i - start)
							* itemw, j * itemh + (2 * j + 1) * mItemPaddingY + mPaddingTop, 0, 1);
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
					if (i * mPageCols + j >= mISurfaceAdapter.getItemCount()) {
						return;
					}
					mISurfaceAdapter.drawItem(i * mPageCols + j, canvas, j * itemw + (2 * j + 1)
							* mItemPaddingX + mPaddingLeft, -offset + (i - start) * itemh, 0, 1);
				}
			}
		}
	}

	void drawMasks(Canvas canvas) {

		if (mISurfaceAdapter == null || mISurfaceAdapter.getItemCount() <= 0) {
			return;
		}

		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();
		if (itemw == -1) {
			itemw = getWidth() - mPaddingLeft - mPaddingRight;
		}
		if (itemh == -1) {
			itemh = getHeight() - mPaddingTop - mPaddingBottom;
			;
		}
		int offset;
		int start;

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
					if (i * mPageRows + j >= mISurfaceAdapter.getItemCount()) {
						return;
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
					if (i * mPageCols + j >= mISurfaceAdapter.getItemCount()) {
						return;
					}
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

			if (bgW <= 0 || bgH <= 0) {
				canvas.drawColor(mBgColor);
				return;
			}

			if (mBackgroundMode == BACKGROUND_STILL) {
				int num = 1;
				int n = 1;
				if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
					if (bgW < thisW) {
						num = thisW / bgW + 1;
					}
					if (bgH < thisH) {
						n = thisH / bgH + 1;
					}

					for (int i = 0; i < num; i++) {
						for (int j = 0; j < n; j++) {
							canvas.drawBitmap(mBgBmp, i * bgW, j * bgH, mPaint);
						}
					}
				} else {
					if (bgH < thisH) {
						num = thisH / bgH + 1;
					}
					if (bgW < thisW) {
						n = thisW / bgW + 1;
					}

					for (int i = 0; i < num; i++) {
						for (int j = 0; j < n; j++) {
							canvas.drawBitmap(mBgBmp, j * bgW, i * bgH, mPaint);
						}
					}
				}
			} else {
				int num = 2;
				int n = 1;
				if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
					if (bgW < thisW) {
						num = thisW / bgW + 2;
					}
					if (bgH < thisH) {
						n = thisH / bgH + 1;
					}

					int start = (int) (mScrollX - mScrollX / bgW * bgW);
					for (int i = 0; i < num; i++) {
						canvas.drawBitmap(mBgBmp, -start + i * bgW, 0, mPaint);
						for (int j = 0; j < n; j++) {
							canvas.drawBitmap(mBgBmp, -start + i * bgW, j * bgH, mPaint);
						}
					}
				} else {
					if (bgH < thisH) {
						num = thisH / bgH + 2;
					}
					if (bgW < thisW) {
						n = thisW / bgW + 1;
					}

					int start = (int) (mScrollY - mScrollY / bgH * bgH);
					for (int i = 0; i < num; i++) {
						for (int j = 0; j < n; j++) {
							canvas.drawBitmap(mBgBmp, j * bgW, -start + i * bgH, mPaint);
						}
					}
				}
			}
		}

		if (mBackgroundMode == BACKGROUND_FLOW) {
			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				int startx;
				if (mHeadBmp != null && mScrollX < 0) {
					startx = -mHeadBmp.getWidth() - mScrollX;
					canvas.drawBitmap(mHeadBmp, startx, 0, mPaint);
				}
				if (mTailBmp != null && mScrollX > mMaxX - getWidth() / FLING_BORDER) {
					startx = getWidth() - mScrollX + mMaxX - getWidth() / FLING_BORDER;
					canvas.drawBitmap(mTailBmp, startx, 0, mPaint);
				}
			} else {
				int starty;
				if (mHeadBmp != null && mScrollY < 0) {
					starty = -mHeadBmp.getHeight() - mScrollY;
					canvas.drawBitmap(mHeadBmp, 0, starty, mPaint);
				}
				if (mTailBmp != null && mScrollY > mMaxY - getHeight() / FLING_BORDER) {
					starty = getHeight() - mScrollY + mMaxY - getHeight() / FLING_BORDER;
					canvas.drawBitmap(mTailBmp, 0, starty, mPaint);
				}
			}
		}
	}

	public void onBounceBegin() {

	}

	public void onBounceEnd() {

	}

	public void onFlingBegin() {

	}

	public void onFlingEnd() {
		mScrollX = mScroller.getCurrX();
		mScrollY = mScroller.getCurrY();
		bounce();
		invalidate();
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
		if (itemw == -1) {
			itemw = w;
		}
		if (itemh == -1) {
			itemh = h;
		}

		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			mPageRows = (h) / itemh;
			if (mPageRows == 0) {
				mPageRows = 1;
			}
			mPageCols = (w + itemw - 1) / itemw;
			mRows = mISurfaceAdapter.getItemCount() < mPageRows ? mISurfaceAdapter.getItemCount()
					: mPageRows;
			mCols = mISurfaceAdapter.getItemCount() < mPageRows ? 1 : (mISurfaceAdapter
					.getItemCount() + mRows - 1)
					/ mRows;
			mMinX = (int) (-w / FLING_BORDER);
			mMaxX = (int) (mCols * itemw > w ? mCols * itemw - w * (FLING_BORDER - 1)
					/ FLING_BORDER : w / FLING_BORDER);
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
			mItemPaddingY = (h - itemh * mPageRows) / (mPageRows * 2F);

		} else {
			mPageCols = (w) / itemw;
			if (mPageCols == 0) {
				mPageCols = 1;
			}
			mPageRows = (h + itemh - 1) / itemh;
			mCols = mISurfaceAdapter.getItemCount() < mPageCols ? mISurfaceAdapter.getItemCount()
					: mPageCols;
			mRows = mISurfaceAdapter.getItemCount() < mPageCols ? 1 : (mISurfaceAdapter
					.getItemCount() + mCols - 1)
					/ mCols;
			mMinX = 0;
			mMaxX = 0;
			mMinY = (int) (-h / FLING_BORDER);
			mMaxY = (int) (mRows * itemh > h ? mRows * itemh - h * (FLING_BORDER - 1)
					/ FLING_BORDER : h / FLING_BORDER);
			mAdapterWidth = w;
			mAdapterHeight = mRows * itemh;
			if (mAdapterHeight <= h) {
				mScrollerSize = 0;
			} else {
				mScrollerSize = h * h / mAdapterHeight;
			}
			mItemPaddingX = (w - itemw * mPageCols) / (mPageCols * 2F);
			mItemPaddingY = 0;
		}
	}

	public void setBgColor(int color) {
		mBgColor = color;
	}

	public void setBgBmp(Bitmap bmp) {
		mBgBmp = bmp;
	}

	public void setHeadBackground(Bitmap bmp) {
		mHeadBmp = bmp;
	}

	public void setTailBackground(Bitmap bmp) {
		mTailBmp = bmp;
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
//		Log.d(TAG, "onClick");
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
//		Log.d(TAG, "onLongClick");
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

	public boolean getItemRect(int item, Rect rect) {
		if (item >= 0 && item < mISurfaceAdapter.getItemCount()) {

			int itemw = mISurfaceAdapter.getItemWidth();
			int itemh = mISurfaceAdapter.getItemHeight();
			if (itemw == -1) {
				itemw = getWidth() - mPaddingLeft - mPaddingRight;
			}
			if (itemh == -1) {
				itemh = getHeight() - mPaddingTop - mPaddingBottom;
				;
			}

			rect.set(-mScrollX, -mScrollY, -mScrollX + itemw, -mScrollY + itemh);

			if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
				int col = item / mPageRows;
				int row = item % mPageRows;
				rect.offset(col * itemw,
						(int) (row * itemh + (2 * row + 1) * mItemPaddingY + mPaddingTop));
			} else {
				int row = item / mPageCols;
				int col = item % mPageCols;
				rect.offset((int) (col * itemw + (2 * col + 1) * mItemPaddingX + mPaddingLeft), row
						* itemh);
			}

			return true;
		}

		return false;
	}

	private int findClickItem(int lastx, int lasty) {
		if (mISurfaceAdapter == null)
			return -1;
		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();
		int count = mISurfaceAdapter.getItemCount();
		int offset;
		int start;

		if (itemw == -1) {
			itemw = getWidth() - mPaddingLeft - mPaddingRight;
		}
		if (itemh == -1) {
			itemh = getHeight() - mPaddingTop - mPaddingBottom;
			;
		}

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
					rc.offsetTo(-offset + (i - start) * itemw, (int) (j * itemh + (2 * j + 1)
							* mItemPaddingY + mPaddingTop));
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
					rc.offsetTo((int) (j * itemw + (2 * j + 1) * mItemPaddingX + mPaddingLeft),
							-offset + (i - start) * itemh);
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

		if (itemw == -1) {
			itemw = w;
		}
		if (itemh == -1) {
			itemh = h;
		}

		if (mPageRows == 0 && mPageCols == 0) {
			computeRowsCols();
		}

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
					x = x > mMaxX - w / FLING_BORDER ? mMaxX - w / FLING_BORDER : x;
					mScrollX = x;
					mScrollY = 0;
				}

			} else {
				int rows = index / mPageCols;
				int y = rows * itemh;
				if (mScrollY < y && mScrollY > y - (h - itemh)) {

				} else {
					y = y > mMaxY - h / FLING_BORDER ? mMaxY - h / FLING_BORDER : y;
					mScrollX = 0;
					mScrollY = y;
				}
			}
			invalidate();
		}
	}

	public void invalidateItem(int index) {
		// TODO : change to invalidate(dirty);
		invalidate();
	}

	public void invalidateAll() {
		invalidate();
	}

	public int[] getItemsOnScreen() {
		if (mISurfaceAdapter == null || mISurfaceAdapter.getItemCount() <= 0) {
			return null;
		}

		int itemw = mISurfaceAdapter.getItemWidth();
		int itemh = mISurfaceAdapter.getItemHeight();
		if (itemw == -1) {
			itemw = getWidth() - mPaddingLeft - mPaddingRight;
		}
		if (itemh == -1) {
			itemh = getHeight() - mPaddingTop - mPaddingBottom;
			;
		}

		int startIndex, endIndex;
		int count = mISurfaceAdapter.getItemCount();
		if (mSurfaceViewMode == SURFACEVIEW_HORIZONTAL) {
			if (mScrollX > 0) {
				startIndex = mScrollX / itemw;
			} else {
				startIndex = 0;
			}
			endIndex = (startIndex + mPageCols) * mPageRows + mPageRows - 1;
			endIndex = endIndex > count ? count : endIndex;
			startIndex *= mPageRows;
		} else {
			if (mScrollY > 0) {
				startIndex = mScrollY / itemh;
			} else {
				startIndex = 0;
			}
			endIndex = (startIndex + mPageRows) * mPageCols + mPageCols - 1;
			endIndex = endIndex > count ? count : endIndex;
			startIndex *= mPageCols;
		}
		return new int[] { startIndex, endIndex };
	}
}
