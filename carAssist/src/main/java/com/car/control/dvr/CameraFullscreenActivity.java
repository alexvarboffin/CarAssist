package com.car.control.dvr;

import android.app.ActionBar;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.car.control.BaseActivity;
import com.car.control.R;

public class CameraFullscreenActivity extends BaseActivity {
    private static final String TAG = "CameraFullscreenActivity";
    
    private CameraView mCameraView;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            this.getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
//                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
//        }
        
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_fullscreen);
		
		int newUiOptions = getWindow().getDecorView().getSystemUiVisibility();
        // Navigation bar hiding:  Backwards compatible to ICS.
        if (Build.VERSION.SDK_INT >= 14) {
            newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        // Status bar hiding: Backwards compatible to Jellybean
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        if (Build.VERSION.SDK_INT >= 18) {
            newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
		
		ActionBar a = getActionBar();
        if(a != null){
            a.hide();
        }
		
		mCameraView = (CameraView)findViewById(R.id.camera_view);
		mCameraView.setKeepScreenOn(true);
		
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mCameraView.setFullscreenMode();
		mCameraView.showControlBar();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
}
