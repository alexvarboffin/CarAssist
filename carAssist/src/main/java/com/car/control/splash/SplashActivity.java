package com.car.control.splash;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.car.control.BaseActivity;
import com.car.control.R;

public class SplashActivity extends BaseActivity {
	
	private static final String TAG = "CarSvc_SplashActivity";
	
	private Handler mHandler = new Handler();
	private boolean mShowAD = false;
	ImageView mImageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);
		
		mImageView = (ImageView)findViewById(R.id.welcome);
		mImageView.setAnimation(AnimationUtils.loadAnimation(this, R.anim.small2big));
		mImageView.setVisibility(View.VISIBLE);

		mHandler.postDelayed(SplashActivity.this::finish, 1500);
	}
	
	@Override
	public void onBackPressed() {
		
	}
}
