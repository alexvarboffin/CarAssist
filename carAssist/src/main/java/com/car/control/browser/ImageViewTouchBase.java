
package com.car.control.browser;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.ImageView;

public class ImageViewTouchBase extends ImageView implements CarScrollerListener {

	@SuppressWarnings("unused")
	private static final String TAG = "CarSvc_ImageViewTouchBase";

	// This is the base transformation which is used to show the image
	// initially. The current computation for this shows the image in
	// it's entirety, letterboxing as needed. One could choose to
	// show the image as cropped instead.
	//
	// This matrix is recomputed when we go from the thumbnail image to
	// the full size image.
	protected Matrix mBaseMatrix = new Matrix();

	// This is the supplementary transformation which reflects what
	// the user has done in terms of zooming and panning.
	//
	// This matrix remains the same when we go from the thumbnail image
	// to the full size image.
	protected Matrix mSuppMatrix = new Matrix();

	// This is the final matrix which is computed as the concatentation
	// of the base matrix and the supplementary matrix.
	private final Matrix mDisplayMatrix = new Matrix();

	// Temporary buffer used for getting the values out of a matrix.
	private final float[] mMatrixValues = new float[9];

	// The current bitmap being displayed.
	protected RotateBitmap mBitmapDisplayed = new RotateBitmap(null);

	int mThisWidth = -1, mThisHeight = -1;

	float mMaxZoom;

	private float mScrollX, mLastX;
	private float mScrollY, mLastY;
	private float mMaxX, mMaxY;
	private float mMinX, mMinY;

	private static final float TOUCH_TOLERANCE = 10;
	private static final float MINIMUM_SCALE_INCREMENT = 0.01f;
	private ScaleGestureDetector mScaleDetector;
	private boolean mSupportMultiTouch;
	private Paint mPaint = new Paint();
	private Bitmap mPrevBmp;
	private Bitmap mNextBmp;
	private Runnable mOnBounceEndRunnable = null;
	private Runnable mOnLayoutPrevRunnable = null;
	private Runnable mOnLayoutNextRunnable = null;
	protected Matrix mBasePrevMatrix = new Matrix();
	protected Matrix mBaseNextMatrix = new Matrix();
	private CarScroller mScroller;
	private VelocityTracker mVelocityTracker;
	private int mTouchSlop;
	private int mMinimumVelocity;
	private int mMaximumVelocity;
	private int mInitialVelocityX;
	private int mInitialVelocityY;
	private int mBounceDirection;
	private static final int BOUNCE_TO_CURR = 0;
	private static final int BOUNCE_TO_PREV = 1;
	private static final int BOUNCE_TO_NEXT = 2;
	private OnBitmapMoveListener mOnBitmapMoveListener;
	private ScaleType mMyScaleType = ScaleType.MATRIX;
	private boolean mIsStandPhoto = false;
	private boolean mMoveToggleOn = false;

	/**
	 * identify whether is photo or not
	 */
	private boolean mIsPhoto = false;

	/**
	 * this flag been set true when setImageBitmap called and cleared to false when user touch move
	 * or gesture processed
	 */
	private boolean mNewPicture = false;

	/**
	 * not use now
	 */
	private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

	public ImageViewTouchBase(Context context) {
		super(context);
		init(context);
	}

	public ImageViewTouchBase(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	private void init(Context context) {
		setScaleType(ImageView.ScaleType.MATRIX);
		updateMultiTouchSupport(context);

		mPaint.setColor(Color.RED);
		mPaint.setDither(true);
		// mPaint.setFilterBitmap(true);

		mScroller = new CarScroller(getContext(), 20f);
		final ViewConfiguration configuration = ViewConfiguration.get(context);
		mTouchSlop = configuration.getScaledTouchSlop();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
		mScroller.setScrollerListener(this);

		mOrientation = getResources().getConfiguration().orientation;
	}

	private void updateMultiTouchSupport(Context context) {
		mSupportMultiTouch = context.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH);
		mSupportMultiTouch = true;

		// ScaleGestureDetector support since api 8
		if (Build.VERSION.SDK_INT < 8) {
			mSupportMultiTouch = false;
		}

		if (mSupportMultiTouch && (mScaleDetector == null)) {
			mScaleDetector = new ScaleGestureDetector(context, new ScaleDetectorListener());
		} else if (!mSupportMultiTouch && (mScaleDetector != null)) {
			mScaleDetector = null;
		}
	}

	// These keep track of the center point of the zoom. They are used to
	// determine the point around which we should zoom.
	private float mZoomCenterX;
	private float mZoomCenterY;
	private boolean mIsOnScale;
	private boolean mIsMouseDown;

	private class ScaleDetectorListener implements ScaleGestureDetector.OnScaleGestureListener {

		public boolean onScaleBegin(ScaleGestureDetector detector) {
			mIsOnScale = true;
			return true;
		}

		public void onScaleEnd(ScaleGestureDetector detector) {
			//donot clear this flag here.
			//will clear on event up
			//mIsOnScale = false;
		}

		public boolean onScale(ScaleGestureDetector detector) {
			float scale = (float) (Math.round(detector.getScaleFactor() * 100) / 100.0);
			if (Math.abs(scale - 1) >= MINIMUM_SCALE_INCREMENT) {
				// limit the scale change per step
				if (scale > 1) {
					scale = Math.min(scale, 1.25f);
					zoomIn(scale);
				} else {
					scale = Math.max(scale, 0.8f);
					zoomOut(1F / scale);
				}
				mZoomCenterX = detector.getFocusX();
				mZoomCenterY = detector.getFocusY();
				return true;
			}
			return false;
		}
	}

	// ImageViewTouchBase will pass a Bitmap to the Recycler if it has finished
	// its use of that Bitmap.
	public interface Recycler {
		public void recycle(Bitmap b);
	}

	public void setRecycler(Recycler r) {
		mRecycler = r;
	}

	private Recycler mRecycler;

	/**
	 * only called by photo
	 */
	public void fromPhoto() {
		mIsPhoto = true;
	}

	private boolean mUpdateMatrix = false;

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mOrientation = newConfig.orientation;
		mUpdateMatrix = true;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		mThisWidth = right - left;
		mThisHeight = bottom - top;
		Runnable r = mOnLayoutRunnable;
		if (r != null) {
			mOnLayoutRunnable = null;
			r.run();
		}
		r = mOnLayoutPrevRunnable;
		if (r != null) {
			mOnLayoutPrevRunnable = null;
			r.run();
		}
		r = mOnLayoutNextRunnable;
		if (r != null) {
			mOnLayoutNextRunnable = null;
			r.run();
		}
		if (mBitmapDisplayed.getBitmap() != null) {
			getProperBaseMatrix(mBitmapDisplayed, mBaseMatrix);
			setImageMatrix(getImageViewMatrix());
		}

		if (mUpdateMatrix) {
			mUpdateMatrix = false;
			updateNextImageMatrix();
			updatePreImageMatrix();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && getScale() > 1.0f) {
			// If we're zoomed in, pressing Back jumps out to show the entire
			// image, otherwise Back returns the user to the gallery.
			zoomTo(1.0f);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(event);

		if (mSupportMultiTouch && mIsPhoto) {
			try {
				mScaleDetector.onTouchEvent(event);
				if (mScaleDetector.isInProgress()) {
					// clear new picture flag
					mNewPicture = false;
					return true;
				}
			} catch (IllegalArgumentException e) {

			}

		}

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mIsMouseDown = true;
			mMoveToggleOn = false;
			mLastX = x;
			mLastY = y;
			if (!mScroller.isFinished()) {
				mScroller.forceFinished(true);
				onBounceEnd();
			}
			invalidate();
			break;
		case MotionEvent.ACTION_MOVE:
			float dx = (x - mLastX);
			float dy = (y - mLastY);
			if (mIsMouseDown && !mIsOnScale) {
				// clear new picture flag
				mNewPicture = false;
				float deltaX = 0;
				float deltaY = 0;

				if (mMoveToggleOn || Math.abs(dx) > 5 * TOUCH_TOLERANCE
						|| (Math.abs(dx) > TOUCH_TOLERANCE && !mIsStandPhoto)) {
					mLastX = x;
					mScrollX += dx;
					deltaX = dx;
					mMoveToggleOn = true;
				}

				if (Math.abs(dy) > TOUCH_TOLERANCE) {
					mLastY = y;
					if (!mIsPhoto) {
						if (mScrollY + dy < mMinY) {
							dy = mMinY - mScrollY;
							mScrollY = mMinY;
						} else if (mScrollY + dy > mMaxY) {
							dy = mMaxY - mScrollY;
							mScrollY = mMaxY;
						} else {
							mScrollY += dy;
						}
					} else {
						float topTranY = getValue(mDisplayMatrix, Matrix.MTRANS_Y);
						float topBaseTranY = getValue(mBaseMatrix, Matrix.MTRANS_Y);
						float baseBitmapHeight = getHeight() - topBaseTranY * 2;
						float currentHeight = baseBitmapHeight
								* getValue(mSuppMatrix, Matrix.MSCALE_Y);

						float bottomTranY = topTranY + currentHeight;
						float bottomBaseTranY = getHeight() - topBaseTranY;
						if (dy > 0 && (topTranY + dy) > topBaseTranY) {
							dy = topBaseTranY - topTranY;
						} else if (dy < 0 && (dy + bottomTranY < bottomBaseTranY)) {
							dy = bottomBaseTranY - bottomTranY;
						}
					}
					deltaY = dy;
				}

				if (getScale() > 1F || ScaleType.CENTER_CROP == mMyScaleType) {
					panBy(deltaX, deltaY);
				} else {
					panBy(deltaX, 0);
				}

				invalidate();
			}
			break;
		case MotionEvent.ACTION_UP:
			mLastX = x;
			mLastY = y;

			final VelocityTracker velocityTracker = mVelocityTracker;
			velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

			mInitialVelocityX = (int) velocityTracker.getXVelocity();
			mInitialVelocityY = (int) velocityTracker.getYVelocity();

			if (mVelocityTracker != null) {
				mVelocityTracker.recycle();
				mVelocityTracker = null;
			}

			if (mIsMouseDown && !mIsOnScale) {
				bounce();
			}
			mIsMouseDown = false;
			mIsOnScale = false;
			
			invalidate();
			break;
		default:
			break;
		}
		return true;
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();

			if (getScale() > 1F || ScaleType.CENTER_CROP == mMyScaleType) {
				panBy(x - mScrollX, y - mScrollY);
			} else {
				panBy(x - mScrollX, 0);
			}
			mScrollX = x;
			mScrollY = y;

			// Keep on drawing until the animation has finished.
			postInvalidate();
		}
	}

	public void bounce() {
		float totalDistanceX = 0;
		float totalDistanceY = 0;
		mBounceDirection = BOUNCE_TO_CURR;

		if (mScrollY < mMinY || mScrollY > mMaxY) {
			if (!mIsPhoto) {
				if (mScrollY > mMaxY) {
					totalDistanceY = mMaxY - mScrollY;
				} else {
					totalDistanceY = mMinY - mScrollY;
				}
			}
		}

		if (mScrollX < mMinX || mScrollX > mMaxX) {

			if (mScrollX > mMaxX) {

				float dx = mScrollX - mMaxX - getWidth();
				if (mPrevBmp == null || mInitialVelocityX <= 0) {
					// to curr image
					mBounceDirection = BOUNCE_TO_CURR;
					totalDistanceX = mMaxX - mScrollX;
				} else {
					// to prev image
					mBounceDirection = BOUNCE_TO_PREV;
					totalDistanceX = -dx;
				}

			} else {

				float dx = getWidth() + mScrollX - mMinX;
				if (mNextBmp == null || mInitialVelocityX >= 0) {
					// to curr image
					mBounceDirection = BOUNCE_TO_CURR;
					totalDistanceX = mMinX - mScrollX;
				} else {
					// to next image
					mBounceDirection = BOUNCE_TO_NEXT;
					totalDistanceX = -dx;
				}
			}
		}

		if (totalDistanceX != 0 || totalDistanceY != 0) {
			mScroller.bounce(totalDistanceX, (int) mScrollX, totalDistanceY, (int) mScrollY);
		}
	}

	private int mFps = 0;
	private int mFpsNum = 0;
	private long mFpsLastTime;

	private boolean DEBUG = true;

	@Override
	protected void onDraw(Canvas canvas) {
		computeScroll();

		if (mScrollX > mMaxX && mPrevBmp != null) {
			float dx = mScrollX - mMaxX - getWidth();
			canvas.translate(dx, 0);
			canvas.drawBitmap(mPrevBmp, mBasePrevMatrix, mPaint);
			canvas.translate(-dx, 0);
		} else if (mScrollX < mMinX && mNextBmp != null) {
			float dx = getWidth() + mScrollX - mMinX;
			canvas.translate(dx, 0);
			canvas.drawBitmap(mNextBmp, mBaseNextMatrix, mPaint);
			canvas.translate(-dx, 0);

		}

		super.onDraw(canvas);

		// drawFPS(canvas);

		if (mOnBounceEndRunnable != null) {
			mOnBounceEndRunnable.run();
			mOnBounceEndRunnable = null;
		}

	}

	private void drawFPS(Canvas canvas) {
		if (DEBUG) {
			long time = System.currentTimeMillis();
			if (mFpsLastTime == 0) {
				mFpsLastTime = time;
			}
			if (time - mFpsLastTime > 1000) {
				mFps = mFpsNum;
				mFpsNum = 0;
				mFpsLastTime = time;
			}
			mFpsNum++;
			canvas.drawText("FPS:" + mFps, 20, 20, mPaint);
		}
	}

	protected Handler mHandler = new Handler();

	protected int mLastXTouchPos;
	protected int mLastYTouchPos;

	private void updatePreImageMatrix() {
		if (mPrevBmp != null) {
			final int viewWidth = getWidth();
			if (viewWidth <= 0) {
				mOnLayoutPrevRunnable = new Runnable() {
					public void run() {
						getProperBaseMatrix(new RotateBitmap(mPrevBmp), mBasePrevMatrix);
					}
				};
				return;
			}
			getProperBaseMatrix(new RotateBitmap(mPrevBmp), mBasePrevMatrix);
		}
	}

	public void setPrevImageBitmap(Bitmap bitmap) {
		mPrevBmp = bitmap;
		updatePreImageMatrix();
	}

	private void updateNextImageMatrix() {
		if (mNextBmp != null) {
			final int viewWidth = getWidth();
			if (viewWidth <= 0) {
				mOnLayoutNextRunnable = new Runnable() {
					public void run() {
						getProperBaseMatrix(new RotateBitmap(mNextBmp), mBaseNextMatrix);
					}
				};
				return;
			}
			getProperBaseMatrix(new RotateBitmap(mNextBmp), mBaseNextMatrix);
		}
	}

	public void setNextImageBitmap(Bitmap bitmap) {
		mNextBmp = bitmap;
		updateNextImageMatrix();
	}

	@Override
	public void setImageBitmap(Bitmap bitmap) {
		setImageBitmap(bitmap, 0);
	}

	private void setImageBitmap(Bitmap bitmap, int rotation) {
		mNewPicture = true;
		super.setImageBitmap(bitmap);
		Drawable d = getDrawable();
		if (d != null) {
			d.setDither(true);
		}

		Bitmap old = mBitmapDisplayed.getBitmap();
		mBitmapDisplayed.setBitmap(bitmap);
		mBitmapDisplayed.setRotation(rotation);

		if (old != null && old != bitmap && mRecycler != null) {
			mRecycler.recycle(old);
		}
	}

	protected void postTranslateCenter(float dx, float dy) {
		postTranslate(dx, dy);
		center(true, true);
	}

	public void clear() {
		setImageBitmapResetBase(null, true);
		setPrevImageBitmap(null);
		setNextImageBitmap(null);
	}

	private Runnable mOnLayoutRunnable = null;

	// This function changes bitmap, reset base matrix according to the size
	// of the bitmap, and optionally reset the supplementary matrix.
	public void setImageBitmapResetBase(final Bitmap bitmap, final boolean resetSupp) {
		setImageRotateBitmapResetBase(new RotateBitmap(bitmap), resetSupp);
	}

	public void setImageRotateBitmapResetBase(final RotateBitmap bitmap, final boolean resetSupp) {
		if(bitmap == null || bitmap.getBitmap() == null)
			return;
		
		final int viewWidth = getWidth();

		if (viewWidth <= 0) {
			mOnLayoutRunnable = new Runnable() {
				public void run() {
					setImageRotateBitmapResetBase(bitmap, resetSupp);
				}
			};
			return;
		}

		if (bitmap.getBitmap() != null) {
			mIsStandPhoto = false;
			getProperBaseMatrix(bitmap, mBaseMatrix);
			setImageBitmap(bitmap.getBitmap(), bitmap.getRotation());

			if (ScaleType.CENTER_CROP == mMyScaleType) {
				float vw = getWidth();
				float vh = getHeight();
				float dw = bitmap.getWidth();
				float dh = bitmap.getHeight();
				float scale;
				float dx = 0, dy = 0;

				if (dw * vh > vw * dh) {
					scale = (float) vh / (float) dh;
					dx = (vw - dw * scale) * 0.5f;

					mMinY = (int) (dy + 0.5f);
					mMaxY = -(int) (dy + 0.5f);
				} else {
					scale = (float) vw / (float) dw;
					if ((float) dh / dw < (float) 4 / 3) {
						dy = (vh - dh * scale) * 0.5f;

						mMinY = (int) (dy + 0.5f);
						mMaxY = -(int) (dy + 0.5f);
					} else {
						dy = (vh - dh * scale) * 0.2f;

						mMinY = (int) (dy * 4 + 0.5f);
						mMaxY = -(int) (dy + 0.5f);

						mIsStandPhoto = true;
					}
				}

				mMinX = (int) (dx + 0.5f);
				mMaxX = -(int) (dx + 0.5f);
			}
		} else {
			mBaseMatrix.reset();
			setImageBitmap(null);
		}

		if (resetSupp) {
			mSuppMatrix.reset();
		}
		setImageMatrix(getImageViewMatrix());
		mMaxZoom = maxZoom();
	}

	// Center as much as possible in one or both axis. Centering is
	// defined as follows: if the image is scaled down below the
	// view's dimensions then center it (literally). If the image
	// is scaled larger than the view and is translated out of view
	// then translate it back into view (i.e. eliminate black bars).
	protected void center(boolean horizontal, boolean vertical) {
		if (mBitmapDisplayed.getBitmap() == null) {
			return;
		}

		Matrix m = getImageViewMatrix();

		RectF rect = new RectF(0, 0, mBitmapDisplayed.getBitmap().getWidth(), mBitmapDisplayed
				.getBitmap().getHeight());

		m.mapRect(rect);

		float height = rect.height();
		float width = rect.width();

		float deltaX = 0, deltaY = 0;

		if (vertical) {
			int viewHeight = getHeight();
			if (height < viewHeight) {
				deltaY = (viewHeight - height) / 2 - rect.top;
			} else if (rect.top > 0) {
				deltaY = -rect.top;
			} else if (rect.bottom < viewHeight) {
				deltaY = getHeight() - rect.bottom;
			}
		}

		if (horizontal) {
			int viewWidth = getWidth();
			if (width < viewWidth) {
				deltaX = (viewWidth - width) / 2 - rect.left;
			} else if (rect.left > 0) {
				deltaX = -rect.left;
			} else if (rect.right < viewWidth) {
				deltaX = viewWidth - rect.right;
			}
		}

		postTranslate(deltaX, deltaY);
		setImageMatrix(getImageViewMatrix());
	}

	protected float getValue(Matrix matrix, int whichValue) {
		matrix.getValues(mMatrixValues);
		return mMatrixValues[whichValue];
	}

	// Get the scale factor out of the matrix.
	protected float getScale(Matrix matrix) {
		return getValue(matrix, Matrix.MSCALE_X);
	}

	public float getScale() {
		return getScale(mSuppMatrix);
	}

	// Setup the base matrix so that the image is centered and scaled properly.
	private void getProperBaseMatrix(RotateBitmap bitmap, Matrix matrix) {
		if(bitmap == null || bitmap.getBitmap() == null)
			return;
		
		float viewWidth = getWidth();
		float viewHeight = getHeight();

		float w = bitmap.getWidth();
		float h = bitmap.getHeight();
		matrix.reset();

		if (ScaleType.CENTER_CROP == mMyScaleType) {

			float scale;
			float dx = 0, dy = 0;

			if (w * viewHeight > viewWidth * h) {
				scale = (float) viewHeight / (float) h;
				dx = (viewWidth - w * scale) * 0.5f;
			} else {
				scale = (float) viewWidth / (float) w;
				if ((float) h / w < (float) 4 / 3) {
					dy = (viewHeight - h * scale) * 0.5f;
				} else {
					dy = (viewHeight - h * scale) * 0.2f;
				}
			}

			matrix.postConcat(bitmap.getRotateMatrix());
			matrix.setScale(scale, scale);
			matrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
		} else {
			// We limit up-scaling to 2x otherwise the result may look bad if it's
			// a small icon.
			float widthScale = Math.min(viewWidth / w, 2.0f);
			float heightScale = Math.min(viewHeight / h, 2.0f);
			float scale = Math.min(widthScale, heightScale);

			matrix.postConcat(bitmap.getRotateMatrix());
			matrix.postScale(scale, scale);

			matrix.postTranslate((viewWidth - w * scale) / 2F, (viewHeight - h * scale) / 2F);
		}
	}

	// Combine the base matrix and the supp matrix to make the final matrix.
	protected Matrix getImageViewMatrix() {
		// The final matrix is computed as the concatentation of the base matrix
		// and the supplementary matrix.
		mDisplayMatrix.set(mBaseMatrix);
		mDisplayMatrix.postConcat(mSuppMatrix);

		return mDisplayMatrix;
	}

	static final float SCALE_RATE = 1.25F;

	// Sets the maximum zoom, which is a scale relative to the base matrix. It
	// is calculated to show the image at 400% zoom regardless of screen or
	// image orientation. If in the future we decode the full 3 megapixel image,
	// rather than the current 1024x768, this should be changed down to 200%.
	protected float maxZoom() {
		if (mBitmapDisplayed.getBitmap() == null) {
			return 1F;
		}

		float fw = (float) mBitmapDisplayed.getWidth() / (float) mThisWidth;
		float fh = (float) mBitmapDisplayed.getHeight() / (float) mThisHeight;
		float max = Math.max(fw, fh) * 4;
		return max;
	}

	protected void zoomTo(float scale, float centerX, float centerY) {
		if (scale > mMaxZoom) {
			scale = mMaxZoom;
		}

		float oldScale = getScale();
		float deltaScale = scale / oldScale;

		mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
		setImageMatrix(getImageViewMatrix());
		center(true, true);
	}

	protected void zoomTo(final float scale, final float centerX, final float centerY,
			final float durationMs) {
		final float incrementPerMs = (scale - getScale()) / durationMs;
		final float oldScale = getScale();
		final long startTime = System.currentTimeMillis();

		mHandler.post(new Runnable() {
			public void run() {
				long now = System.currentTimeMillis();
				float currentMs = Math.min(durationMs, now - startTime);
				float target = oldScale + (incrementPerMs * currentMs);
				zoomTo(target, centerX, centerY);

				if (currentMs < durationMs) {
					mHandler.post(this);
				}
			}
		});
	}
	
	public void zoomIn(final float scale,  final float durationMs) {
		final float incrementPerMs = (scale - getScale()) / durationMs;
		final float oldScale = getScale();
		final long startTime = System.currentTimeMillis();

		mHandler.post(new Runnable() {
			public void run() {
				long now = System.currentTimeMillis();
				float currentMs = Math.min(durationMs, now - startTime);
				float target = oldScale + (incrementPerMs * currentMs);
				//zoomTo(target, centerX, centerY);
				zoomIn(target/getScale());
				if (currentMs < durationMs) {
					mHandler.post(this);
				}
			}
		});
	}
	
	public void zoomOut(final float scale,  final float durationMs) {
		final float incrementPerMs = (scale - getScale()) / durationMs;
		final float oldScale = getScale();
		final long startTime = System.currentTimeMillis();

		mHandler.post(new Runnable() {
			public void run() {
				long now = System.currentTimeMillis();
				float currentMs = Math.min(durationMs, now - startTime);
				float target = oldScale + (incrementPerMs * currentMs);
				//zoomTo(target, centerX, centerY);
				zoomOut(getScale()/target);
				if (currentMs < durationMs) {
					mHandler.post(this);
				}
			}
		});
	}

	protected void zoomTo(float scale) {
		float cx = getWidth() / 2F;
		float cy = getHeight() / 2F;

		zoomTo(scale, cx, cy);
	}

	protected void zoomIn() {
		zoomIn(SCALE_RATE);
	}

	protected void zoomOut() {
		zoomOut(SCALE_RATE);
	}

	public void zoomFit() {
		if (mBitmapDisplayed.getBitmap() == null) {
			return;
		}
		float w = (float) mBitmapDisplayed.getBitmap().getWidth();
		float h = (float) mBitmapDisplayed.getBitmap().getHeight();
		float rate = 1F;
		if (w >= getWidth() && h <= getHeight()) {
			rate = (float) (w / getWidth()) * (float) (getHeight() / h);
		} else if (w <= getWidth() && h >= getHeight()) {
			rate = (float) (getWidth() / w) * (float) (h / getHeight());
		} else {
			rate = Math.max(w / getWidth(), h / getHeight());
		}
		if (rate < SCALE_RATE)
			zoomIn(SCALE_RATE);
		else {
			zoomIn(rate);
		}
	}

	protected void zoomIn(float rate) {
		if (getScaleType() == ScaleType.CENTER_CROP) {
			return;
		}
		// vincent add start
		if (mBitmapDisplayed.getBitmap() == null) {
			return;
		}
		float lastW = mBitmapDisplayed.getWidth() * getScale(mBaseMatrix) * getScale(mSuppMatrix);
		float lastH = mBitmapDisplayed.getHeight() * getScale(mBaseMatrix) * getScale(mSuppMatrix);
		if (lastW < getWidth()) {
			lastW = getWidth();
		}
		if (lastH < getHeight()) {
			lastH = getHeight();
		}
		// vincent add end

		if (getScale() >= mMaxZoom) {
			return; // Don't let the user zoom into the molecular level.
		}

		float cx = getWidth() / 2F;
		float cy = getHeight() / 2F;

		mSuppMatrix.postScale(rate, rate, cx, cy);
		setImageMatrix(getImageViewMatrix());

		// vincent add start
		int bmpW = mBitmapDisplayed.getWidth();
		int bmpH = mBitmapDisplayed.getHeight();
		float baseS = getScale(mBaseMatrix);
		float suppS = getScale(mSuppMatrix);
		float incrementX = (bmpW * baseS * suppS - lastW);
		float incrementY = (bmpH * baseS * suppS - lastH);
		if (incrementX > 0) {
			float max = (getWidth() / 2 + (mMaxX - mScrollX)) / lastW;
			mMaxX += incrementX * max;
			mMinX -= incrementX * (1 - max);
		}
		if (incrementY > 0) {
			float max = (getHeight() / 2 + (mMaxY - mScrollY)) / lastH;
			mMaxY += incrementY * max;
			mMinY -= incrementY * (1 - max);
		}
		// vincent add end
	}

	protected void zoomOut(float rate) {
		if (getScaleType() == ScaleType.CENTER_CROP) {
			return;
		}
		if (mBitmapDisplayed.getBitmap() == null) {
			return;
		}
		// vincent add start
		float lastW = mBitmapDisplayed.getWidth() * getScale(mBaseMatrix) * getScale(mSuppMatrix);
		float lastH = mBitmapDisplayed.getHeight() * getScale(mBaseMatrix) * getScale(mSuppMatrix);
		if (lastW < getWidth()) {
			lastW = getWidth();
		}
		if (lastH < getHeight()) {
			lastH = getHeight();
		}
		// vincent add start

		float cx = getWidth() / 2F;
		float cy = getHeight() / 2F;

		// Zoom out to at most 1x.
		Matrix tmp = new Matrix(mSuppMatrix);
		tmp.postScale(1F / rate, 1F / rate, cx, cy);

		if (getScale(tmp) < 1F) {
			mSuppMatrix.setScale(1F, 1F, cx, cy);
		} else {
			mSuppMatrix.postScale(1F / rate, 1F / rate, cx, cy);
		}
		setImageMatrix(getImageViewMatrix());
		// center(true, true);

		// vincent add start
		int bmpW = mBitmapDisplayed.getWidth();
		int bmpH = mBitmapDisplayed.getHeight();
		float baseS = getScale(mBaseMatrix);
		float suppS = getScale(mSuppMatrix);
		float decrementX = (lastW - bmpW * baseS * suppS);
		float decrementY = (lastH - bmpH * baseS * suppS);
		if (decrementX <= 0 && decrementY <= 0) {
			return;
		}
		float maxX = (getWidth() / 2 + (mMaxX - mScrollX)) / lastW;
		mMaxX -= decrementX * maxX;
		mMinX += decrementX * (1 - maxX);
		float maxY = (getHeight() / 2 + (mMaxY - mScrollY)) / lastH;
		mMaxY -= decrementY * maxY;
		mMinY += decrementY * (1 - maxY);
		if (mMaxX < 0 || mMinX > 0 || mMaxY < 0 || mMinY > 0) {
			boolean horizontal = false;
			boolean vertical = false;
			if (mMaxX < 0 || mMinX > 0) {
				mScrollX = 0;
				mMaxX = 0;
				mMinX = 0;
				horizontal = true;
			}
			if (mMaxY < 0 || mMinY > 0) {
				mScrollY = 0;
				mMaxY = 0;
				mMinY = 0;
				vertical = true;
			}
			center(horizontal, vertical);
		} else {
			if (!mIsOnScale) {
				bounce();
			}
		}
		// vincent add start
	}

	protected void postTranslate(float dx, float dy) {
		mSuppMatrix.postTranslate(dx, dy);
	}

	protected void panBy(float dx, float dy) {
		postTranslate(dx, dy);
		setImageMatrix(getImageViewMatrix());
	}

	private void resetLocalVar() {
		mScrollX = 0;
		mScrollY = 0;
		mMinX = 0;
		mMaxX = 0;
		mMinY = 0;
		mMaxY = 0;
	}

	public void onBounceBegin() {

	}

	public void onBounceEnd() {
		if (mBounceDirection == BOUNCE_TO_PREV) {

			mOnBounceEndRunnable = new Runnable() {
				public void run() {
					resetLocalVar();
					setNextImageBitmap(mBitmapDisplayed.getBitmap());
					setImageBitmapResetBase(mPrevBmp, true);
					setPrevImageBitmap(null);
					if (mOnBitmapMoveListener != null) {
						mOnBitmapMoveListener.onMoveToPrev(ImageViewTouchBase.this);
					}
				}
			};
		} else if (mBounceDirection == BOUNCE_TO_NEXT) {

			mOnBounceEndRunnable = new Runnable() {
				public void run() {
					resetLocalVar();
					setPrevImageBitmap(mBitmapDisplayed.getBitmap());
					setImageBitmapResetBase(mNextBmp, true);
					setNextImageBitmap(null);
					if (mOnBitmapMoveListener != null) {
						mOnBitmapMoveListener.onMoveToNext(ImageViewTouchBase.this);
					}
				}
			};
		}
	}

	public void onFlingBegin() {

	}

	public void onFlingEnd() {

	}

	public interface OnBitmapMoveListener {

		void onMoveToPrev(ImageViewTouchBase parent);

		void onMoveToNext(ImageViewTouchBase parent);
	}

	public void setOnBitmapMoveListener(OnBitmapMoveListener listener) {
		mOnBitmapMoveListener = listener;
	}

	public void setMyScaleType(ScaleType scaleType) {
		if (scaleType == null) {
			throw new NullPointerException();
		}

		if (mMyScaleType != scaleType) {
			mMyScaleType = scaleType;

			setWillNotCacheDrawing(mMyScaleType == ScaleType.CENTER);

			requestLayout();
			invalidate();

			setPrevImageBitmap(mPrevBmp);
			setNextImageBitmap(mNextBmp);
		}
	}

	public ScaleType getMyScaleType() {
		return mMyScaleType;
	}
}
