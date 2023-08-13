package com.car.control.splash;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.car.control.R;

public class SplashView extends RelativeLayout {

    private static final String TAG = "CarSvc_SplashView";

    private Handler mHandler = new Handler();
    private boolean mShowAD = false;
    private boolean mDismiss = false;
    private SplashViewListener mSplashViewListener;

    public SplashView(Context context) {
        super(context);
        initView();
    }

    public SplashView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public SplashView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    public void setSplashViewListener(SplashViewListener l) {
        mSplashViewListener = l;
    }

    private void initView() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            View v = inflater.inflate(R.layout.splash_view, this);
            TextView t = (TextView) v.findViewById(R.id.version);
            t.setText("KWORK " + android.os.Build.SERIAL);
        }

        mHandler.postDelayed(() -> Looper.myQueue().addIdleHandler(() -> {
            dismissSplashView();
            return false;
        }), 2000);

        mHandler.postDelayed(this::dismissSplashView, 5000);

    }


    private void dismissSplashView() {
        if (mDismiss)
            return;
        mDismiss = true;
        startDismissAnimation();
    }

    private void startDismissAnimation() {
        Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.slpash_dismiss);
        animation.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
                if (mSplashViewListener != null)
                    mSplashViewListener.onSplashViewDismissed();
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        startAnimation(animation);
    }

    public interface SplashViewListener {
        void onSplashViewDismissed();
    }
}
