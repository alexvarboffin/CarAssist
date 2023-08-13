
package com.car.control.browser;

import java.util.ArrayList;

import com.car.common.ThumbnailCacheManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;

public class PhotoThumbAdapter extends CarSurfaceAdapter {
	
	private static final String TAG = "CarSvc_PhotoThumbAdapter";

	private ArrayList<FileInfo> mList;
	private Bitmap mBmp;
	private Handler mHandler;
	private Bitmap mDefaultBmp;
	private Bitmap mCheckBmp;
	private Bitmap mUncheckBmp;
	private Context mContext;
	private Rect mSrcRect = new Rect();
	private RectF mDstRect = new RectF();

	PhotoThumbAdapter(Context context, int w, int h) {
		super(w, h);
		mContext = context;
		initPaint();
	}

	void setDefaultBmp(Bitmap bmp) {
		mDefaultBmp = bmp;
	}

	Bitmap getDefaultBmp() {
		return mDefaultBmp;
	}

	boolean checkBmpAdded() {
		return ((mCheckBmp != null) && (mUncheckBmp != null));
	}

	void setCheckBmp(Bitmap check, Bitmap uncheck) {
		mCheckBmp = check;
		mUncheckBmp = uncheck;
	}

	void initPaint() {
		mPaint.setTextAlign(Align.CENTER);
		mPaint.setTextSize(20);
		mPaint.setColor(Color.rgb(0, 153, 204));
		mPaint.setStyle(Style.STROKE);
		mPaint.setStrokeCap(Cap.ROUND);
		mPaint.setStrokeWidth(3);
	}

	void setUIHandler(Handler handler) {
		mHandler = handler;
	}

	void setPhotoList(ArrayList<FileInfo> list) {
		mList = list;
	}

	@Override
	public void drawItem(int itemIndex, Canvas canvas, float x, float y, float rotate, float scale) {

		int offsetX = 0;
		int offsetY = 0;
		if (mSelected == itemIndex) {
			offsetX = 0;
			offsetY = 0;
		}

		String url = mList.get(itemIndex).getThunbnailUrl();
		String key = mList.get(itemIndex).getFullPath();
		if(url.startsWith("http"))
			mBmp = ThumbnailCacheManager.instance().getThumbnail(url, key, 
					ThumbnailCacheManager.TYPE_NET_THUMB);
		else
			mBmp = ThumbnailCacheManager.instance().getThumbnail(url, key, 
					ThumbnailCacheManager.TYPE_LOCAL_THUMB);
		if (mBmp == null || mBmp.equals(Util.sNullBitmap)){
			mBmp = mDefaultBmp;
			canvas.drawBitmap(mBmp, offsetX + x + (mWidth - mBmp.getWidth()) / 2, offsetY + y
					+ (mHeight - mBmp.getHeight()) / 2, mPaint);
		}else{
			mSrcRect.set(0, 0, mBmp.getWidth() , mBmp.getHeight());
			mDstRect.set(offsetX + x , offsetY + y , 
					offsetX + x + mWidth, offsetY + y + mHeight);
			canvas.drawBitmap(mBmp , mSrcRect , mDstRect, mPaint);
		}
		
		if (mSelected == itemIndex) {
			RectF rect = new RectF(x+1, y+1, x + mWidth-2, y + mHeight-2);
			canvas.drawRect(rect, mPaint);
		}

	}

	@Override
	public Object getItem(int index) {
		if (mList == null || mList.size() <= 0)
			return null;
		else {
			return mList.get(index);
		}
	}
	public int getRows() {
		return 1;
	}
	@Override
	public int getItemCount() {
		if (mList == null)
			return 0;
		return mList.size();
	}

}
