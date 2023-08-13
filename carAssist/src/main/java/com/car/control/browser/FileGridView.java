package com.car.control.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.GridView;

public class FileGridView extends GridView {

	static final String TAG = "CarSvc_FileGridView";
	private CheckForBlankAreaLongPress mPendingCheckForBlankAreaLongPress;

	public FileGridView(Context context) {
		super(context);
	}

	public FileGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public FileGridView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	private OnLongClickListener mOnBlankAreaLongClickListener;

	public boolean performBlankAreaLongClick() {
		boolean handled = false;
		if (mOnBlankAreaLongClickListener != null) {
			handled = mOnBlankAreaLongClickListener
					.onLongClick(FileGridView.this);
		}

		return handled;
	}

	public void setOnBlankAreaLongClickListener(OnLongClickListener l) {
		if (!isLongClickable()) {
			setLongClickable(true);
		}
		mOnBlankAreaLongClickListener = l;
	}

	class CheckForBlankAreaLongPress implements Runnable {

		public void run() {
			if (isPressed()) {
				performBlankAreaLongClick();
			}
		}
	}

	private void postCheckForBlankAreaLongClick() {

		if (mPendingCheckForBlankAreaLongPress == null) {
			mPendingCheckForBlankAreaLongPress = new CheckForBlankAreaLongPress();
		}
		postDelayed(mPendingCheckForBlankAreaLongPress,
				ViewConfiguration.getLongPressTimeout());
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		int x = (int) ev.getRawX();
		int y = (int) ev.getRawY();
		int[] absPos = new int[2];

		final int count = getChildCount();
		View vChild;
		for (int i = 0; i < count; i++) {
			vChild = getChildAt(i);
			vChild.getLocationOnScreen(absPos);
			if ((x >= absPos[0]) && (x < (absPos[0] + vChild.getWidth()))
					&& (y >= absPos[1])
					&& (y < (absPos[1] + vChild.getHeight()))) {
				return super.onTouchEvent(ev);
			}
		}

		if (ev.getAction() == MotionEvent.ACTION_UP) {
			// Log.d(TAG, "onTouchEvent.ACTION_UP: (x, y)=" + x + "," + y);
			if (isPressed())
				setPressed(false);
			return true;
		} else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			// Log.d(TAG, "onTouchEvent.ACTION_DOWN: (x, y)=" + x + "," + y);
			setPressed(true);
			postCheckForBlankAreaLongClick();
			return true;
		}

		return super.onTouchEvent(ev);
	}
}
