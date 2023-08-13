/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.car.control;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.FocusFinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class Cling extends FrameLayout {

    static final String WORKSPACE_CLING_DISMISSED_KEY = "cling.workspace.dismissed";
    static final String ALLAPPS_CLING_DISMISSED_KEY = "cling.allapps.dismissed";
    static final String FOLDER_CLING_DISMISSED_KEY = "cling.folder.dismissed";

    private static String CLING_CONTROL = "control";
    private static String CLING_PREVIEW = "preview";
    private static String CLING_CAR = "car";
    private static String CLING_PHONE = "phone";
    
    private boolean mIsInitialized;
    private String mDrawIdentifier;
    private Drawable mBackground;
    private Drawable mPunchThroughGraphic;
    private Drawable mHandTouchGraphic;
    private int mPunchThroughGraphicCenterRadius;
    private int mAppIconSize;
    private int mButtonBarHeight;
    private float mRevealRadius;
    private int[] mPositionData;

    private Paint mErasePaint;

    public Cling(Context context) {
        this(context, null, 0);
    }

    public Cling(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Cling(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Cling, defStyle, 0);
        mDrawIdentifier = a.getString(R.styleable.Cling_drawIdentifier);
        a.recycle();

        setClickable(true);
    }

    void init(int[] positionData) {
        if (!mIsInitialized) {
            mPositionData = positionData;

            Resources r = getContext().getResources();

            mPunchThroughGraphic = r.getDrawable(R.drawable.cling);
            mPunchThroughGraphicCenterRadius =
                r.getDimensionPixelSize(R.dimen.clingPunchThroughGraphicCenterRadius);
            mAppIconSize = r.getDimensionPixelSize(R.dimen.app_icon_size);
            mRevealRadius = r.getDimensionPixelSize(R.dimen.reveal_radius) * 1f;
            mButtonBarHeight = r.getDimensionPixelSize(R.dimen.button_bar_height);

            mErasePaint = new Paint();
            mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mErasePaint.setColor(0xFFFFFF);
            mErasePaint.setAlpha(0);

            mIsInitialized = true;
        }
    }

    void cleanup() {
        mBackground = null;
        mPunchThroughGraphic = null;
        mHandTouchGraphic = null;
        mIsInitialized = false;
    }

    public String getDrawIdentifier() {
        return mDrawIdentifier;
    }

    private int[] getPunchThroughPositions() {
        //if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT)) {
            //return new int[]{getMeasuredWidth() / 2, getMeasuredHeight() - (mButtonBarHeight / 2)};
        //}
        return new int[]{-1, -1};
    }

    @Override
    public View focusSearch(int direction) {
        return this.focusSearch(this, direction);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        return FocusFinder.getInstance().findNextFocus(this, focused, direction);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return (mDrawIdentifier.equals(CLING_CONTROL) || mDrawIdentifier.equals(CLING_PREVIEW)
        		|| mDrawIdentifier.equals(CLING_CAR) || mDrawIdentifier.equals(CLING_PHONE));
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (mDrawIdentifier.equals(CLING_CONTROL) || mDrawIdentifier.equals(CLING_PREVIEW) 
        		|| mDrawIdentifier.equals(CLING_CAR) || mDrawIdentifier.equals(CLING_PHONE)) {

            int[] positions = getPunchThroughPositions();
            for (int i = 0; i < positions.length; i += 2) {
                double diff = Math.sqrt(Math.pow(event.getX() - positions[i], 2) +
                        Math.pow(event.getY() - positions[i + 1], 2));
                if (diff < mRevealRadius) {
                    return false;
                }
            }
        }
        return true;
    };

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsInitialized) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(metrics);

            // Initialize the draw buffer (to allow punching through)
            Bitmap b = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);

            // Draw the background
            if (mBackground == null) {
                if (mDrawIdentifier.equals(CLING_CONTROL) || mDrawIdentifier.equals(CLING_PREVIEW)
                		|| mDrawIdentifier.equals(CLING_CAR) || mDrawIdentifier.equals(CLING_PHONE)) {
                    mBackground = getResources().getDrawable(R.drawable.bg_cling1);
                }
            }
            if (mBackground != null) {
                mBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                mBackground.draw(c);
            } else {
                c.drawColor(0x99000000);
            }

            int cx = -1;
            int cy = -1;
            float scale = mRevealRadius / mPunchThroughGraphicCenterRadius;
            int dw = (int) (scale * mPunchThroughGraphic.getIntrinsicWidth());
            int dh = (int) (scale * mPunchThroughGraphic.getIntrinsicHeight());

            // Determine where to draw the punch through graphic
            int[] positions = getPunchThroughPositions();
            for (int i = 0; i < positions.length; i += 2) {
                cx = positions[i];
                cy = positions[i + 1];
                if (cx > -1 && cy > -1) {
                    c.drawCircle(cx, cy, mRevealRadius, mErasePaint);
                    mPunchThroughGraphic.setBounds(cx - dw/2, cy - dh/2, cx + dw/2, cy + dh/2);
                    mPunchThroughGraphic.draw(c);
                }
            }

            canvas.drawBitmap(b, 0, 0, null);
            //c.setBitmap(null);
            b = null;
        }

        // Draw the rest of the cling
        super.dispatchDraw(canvas);
    };
}
