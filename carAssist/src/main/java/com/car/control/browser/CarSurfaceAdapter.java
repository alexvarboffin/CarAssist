
package com.car.control.browser;

import android.graphics.Canvas;
import android.graphics.Paint;

public abstract class CarSurfaceAdapter implements ISurfaceAdapter {

	public int mWidth;
	public int mHeight;
	public int mSelected;
	public Paint mPaint = new Paint();

	public abstract void drawItem(int itemIndex, Canvas canvas, float x, float y, float rotate,
			float scale);

	public abstract int getItemCount();

	public abstract Object getItem(int index);

	public CarSurfaceAdapter() {

	}

	void setWH(int w, int h) {
		mWidth = w;
		mHeight = h;
	}

	// item width and height
	public CarSurfaceAdapter(int w, int h) {
		mWidth = w;
		mHeight = h;
	}

	public int getColumns() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getItemHeight() {
		return mHeight;
	}

	public int getItemWidth() {
		return mWidth;
	}

	public int getRows() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getSelectedItem() {
		return mSelected;
	}

	public void setSelectedItem(int item) {
		mSelected = item;
	}
}
