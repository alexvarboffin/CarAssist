
package com.car.control.browser;

import com.car.control.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;


public class SquareProgressBar extends View{
	
	private static String TAG = "CarSvc_SquareProgressBar";
	
	private int mProgressColor;
	private int mFullColor;
	private int mProgress = 0;
	private Paint mPaint;
	
	public SquareProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.SquareProgressBar);
		mProgressColor = a.getColor(R.styleable.SquareProgressBar_progress, Color.TRANSPARENT);
		mFullColor = a.getColor(R.styleable.SquareProgressBar_full, Color.TRANSPARENT);
		mPaint = new Paint();
		mPaint.setStyle(Style.FILL);
	}

	public SquareProgressBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.SquareProgressBar);
		mProgressColor = a.getColor(R.styleable.SquareProgressBar_progress, Color.TRANSPARENT);
		mFullColor = a.getColor(R.styleable.SquareProgressBar_full, Color.TRANSPARENT);
		mPaint = new Paint();
		mPaint.setStyle(Style.FILL);
	}
	
	public void setProgress(int progress){
		mProgress = progress;
		invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		int width = getWidth();
		int height = getHeight();
		
		Log.i(TAG,"width = " + width);
		Log.i(TAG,"height = " + height);
		
		Log.i(TAG,"mProgressColor = " + mProgressColor);
		Log.i(TAG,"mFullColor = " + mFullColor);
		
		Log.i(TAG, "mProgress = " + mProgress);
		
		float progressHeight = height * mProgress * 1f / 100;
		mPaint.setColor(mProgressColor);
		canvas.drawRect(0, 0, width, progressHeight, mPaint);
		
		mPaint.setColor(mFullColor);
		canvas.drawRect(0, progressHeight, width, height, mPaint);
		
		super.onDraw(canvas);
	}
	
	
}
