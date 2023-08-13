package com.car.control;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.car.control.dvr.RemoteCameraConnectManager;
import com.car.control.splash.SplashView;
import com.car.control.util.Util;

public class CarControlActivity extends FragmentActivity implements SplashView.SplashViewListener {
	
	private static final String TAG = "CarSvc_CarCtrllActivity";
	
	public static final String ACTION_FINISH_CARCONTROL = "action_finish_carcontrol";
	
	private CarAssistMainView mCarAssistMainView = null;
	private RelativeLayout mMainContainer;
	private Handler mHandler = new Handler();
	private Bundle mSavedInstanceState;
	
	public void setPaperViewEnable(boolean enable){
		if(mCarAssistMainView != null)
			mCarAssistMainView.setPaperViewEnable(enable);
	}
	
	public Cling initCling(int clingId, int[] positionData, boolean animate, int delay) {
		if(mCarAssistMainView != null)
			return mCarAssistMainView.initCling(clingId, positionData, animate, delay);
		return null;
	}
	
	public void dismissPreviewCling(View v) {
		if(mCarAssistMainView != null)
			mCarAssistMainView.dismissPreviewCling(v);
	}
	
	public void dismissCarCling(View v) {
		if(mCarAssistMainView != null)
			mCarAssistMainView.dismissCarCling(v);
	}
	
	public void dismissPhoneCling(View v) {
		if(mCarAssistMainView != null)
			mCarAssistMainView.dismissPhoneCling(v);
	}
	
	public void dismissCloudCling(View v){
		if(mCarAssistMainView != null)
			mCarAssistMainView.dismissCloudCling(v);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSavedInstanceState = savedInstanceState;

		Log.d(TAG, "onCreate");

		setContentView(R.layout.activity_car_control);

		//WindowManager.LayoutParams params = getWindow().getAttributes();
		//params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
		//getWindow().setAttributes(params);

		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				Window window = getWindow();
				window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
				window.setStatusBarColor(getResources().getColor(R.color.photo_color));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		ActionBar a = getActionBar();
		if(a != null){
			a.setTitle("");
			a.hide();
		}

		mMainContainer = (RelativeLayout)findViewById(R.id.main_container);
		((SplashView)findViewById(R.id.main_splash_view)).setSplashViewListener(this);
		
		mHandler.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				CarAssistMainView view = new CarAssistMainView(CarControlActivity.this);
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT, 
						FrameLayout.LayoutParams.MATCH_PARENT);
				mMainContainer.addView(view, lp);
				mCarAssistMainView = view;
				mCarAssistMainView.onActivityCreate(mSavedInstanceState);
				mCarAssistMainView.onAcitvityResume();

                //mMainContainer.startAnimation(AnimationUtils.loadAnimation(
                        //CarControlActivity.this, R.anim.alpha_show));
                mMainContainer.setVisibility(View.VISIBLE);
			}
		}, 200);
		
		IntentFilter filter = new IntentFilter(ACTION_FINISH_CARCONTROL);
		registerReceiver(mBroadcastReceiver, filter);
		
		//from CarDVR=>CarAssist
		String strOld = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CarDVR";
        String strNew = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CarAssist";
        Util.renameDirectory(strOld, strNew);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mBroadcastReceiver);
		if(mCarAssistMainView != null)
			mCarAssistMainView.onActivityDestroy();
		Log.d(TAG, "onDestroy");
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if(mCarAssistMainView != null)
			mCarAssistMainView.onAcitvityResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if(mCarAssistMainView != null)
			mCarAssistMainView.onActivityPause();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if(mCarAssistMainView != null)
			mCarAssistMainView.onActivityStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if(mCarAssistMainView != null)
			mCarAssistMainView.onActivityStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i(TAG,"onCreateOptionsMenu");
		
		if(mCarAssistMainView != null){
			mCarAssistMainView.onCreateOptionsMenu(getMenuInflater(), menu);
		}
	
		return true;
	}
		
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if(mCarAssistMainView != null && mCarAssistMainView.onOptionsItemSelected(item))
			return true;
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Log.i(TAG,"onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public void onBackPressed() {
		if(mCarAssistMainView != null && mCarAssistMainView.onBackPressed())
			return;
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(mCarAssistMainView != null)
        	mCarAssistMainView.onActivityResult(requestCode, resultCode, data);
    }
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(ACTION_FINISH_CARCONTROL)){
				Log.d(TAG, "CarControlActivity finish");
				CarControlActivity.this.finish();
			}
		}
		
	};

	@Override
	public void onSplashViewDismissed() {
		ActionBar a = getActionBar();
		if(a != null){
			a.show();
		}
		//WindowManager.LayoutParams params = getWindow().getAttributes();
		//params.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
		//getWindow().setAttributes(params);
		RemoteCameraConnectManager.instance().setSplashViewDismissed();
	}
}
