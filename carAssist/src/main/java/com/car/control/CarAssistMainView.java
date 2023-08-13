package com.car.control;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;

import com.car.control.dvr.CameraPreviewView;
import com.car.control.util.Util;

import java.util.ArrayList;
import java.util.List;

public class CarAssistMainView extends IPagerView implements View.OnClickListener{
	
	public final static int SCANNIN_GREQUEST_CODE = 1;

	private static final String TAG = "CA_CarAssistMainView";
	public static final String KEY_PRESERVER_SERIALNO = "key_preserver_serialno";
	public static final String KEY_PREVIEW_CLING = "key_preview_cling";
	public static final String KEY_CAR_CLING = "key_car_cling";
	public static final String KEY_PHONE_CLING = "key_phone_cling";
	public static final String KEY_CLOUD_CLING = "key_cloud_cling";
	
	private static final int SHOW_CLING_DURATION = 550;
	private static final int DISMISS_CLING_DURATION = 250;

	private CarViewPager mViewPager;
	private MyPagerAdapter mMyPagerAdapter;
	private IPagerView mCurrentPagerView;
	private List<IPagerView> mListViews = new ArrayList<IPagerView>();
	private TabPageView mTabPageViews[] = new TabPageView[2];
	private int mTitleRes[] = new int[2];
	private CameraPreviewView mCameraPreviewView;
	private PhoneFilesView mPhoneFilesView;
	private Dialog mQuitDialog;
	private int mCountClick = 5;
	private static final int MSG_RESET_COUNT = 1;
	private int mCurrentTitle = 0;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == MSG_RESET_COUNT){
                mCountClick = 5;
            }
        }
        
    };
	
	private boolean sShowAllTabs = true;	// !CloudConfig.curUser().isFBLogin();

	public CarAssistMainView(Context context) {
		super(context);
		initView();
	}
	
	public CarAssistMainView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public CarAssistMainView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}
	
	public void setPaperViewEnable(boolean enable){
		mViewPager.setSlideEnable(enable);
	}

	@Override
	public boolean onCreateOptionsMenu(MenuInflater mi, Menu menu) {
		Log.i(TAG,"onCreateOptionsMenu");
		
		if(mCurrentPagerView != null){
			mCurrentPagerView.onCreateOptionsMenu(
					((Activity)getContext()).getMenuInflater(), menu);
		}else{
			((Activity)getContext()).getMenuInflater().inflate(
					R.menu.camera_preview, menu);
		}
	
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(mCurrentPagerView != null && mCurrentPagerView.onOptionsItemSelected(item))
			return true;
		return false;
	}

	@Override
	public void onActivate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDeactivate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onActivityCreate(Bundle savedInstanceState) {
		for(IPagerView ipv : mListViews)
			ipv.onActivityCreate(savedInstanceState);
	}

	@Override
	public void onActivityPause() {
		for(IPagerView ipv : mListViews)
			ipv.onActivityPause();
	}

	@Override
	public void onAcitvityResume() {
		// 半小时内不做重复的流量检查
		for(IPagerView ipv : mListViews)
			ipv.onAcitvityResume();
	}
	
	@Override
	public void onActivityStart() {
		for(IPagerView ipv : mListViews)
			ipv.onActivityStart();
	}

	@Override
	public void onActivityStop() {
		for(IPagerView ipv : mListViews)
			ipv.onActivityStop();
	}

	@Override
	public void onActivityDestroy() {
		for(IPagerView ipv : mListViews)
			ipv.onActivityDestroy();
	}

	@Override
	public boolean onBackPressed() {
		if(mCurrentPagerView != null && mCurrentPagerView.onBackPressed())
			;
		else
			mQuitDialog.show();
		return false;
	}

	@Override
	public void refresh() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(mCurrentPagerView != null)
        	mCurrentPagerView.onActivityResult(requestCode, resultCode, data);
	}
	
	private void initView(){
		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (inflater != null) {
			inflater.inflate(R.layout.carassist_main_view, this);
		}

		Util.initContext(getContext());

		findViewById(R.id.camera_preview).setOnClickListener(this);
		findViewById(R.id.phone_files).setOnClickListener(this);

        mTabPageViews[0] = (TabPageView)findViewById(R.id.camera_preview);
        mTabPageViews[1] = (TabPageView)findViewById(R.id.phone_files);
        mTitleRes[0] = R.string.tab_preview;
        mTitleRes[1] = R.string.tab_phone_file;

		mCameraPreviewView = new CameraPreviewView(getContext());
		mCameraPreviewView.setQuickSetting2(this);
		mPhoneFilesView = new PhoneFilesView(getContext());
		mPhoneFilesView.initPhoneFilesView(this);

		mListViews.add(mCameraPreviewView);
		mListViews.add(mPhoneFilesView);

		mViewPager = (CarViewPager)findViewById(R.id.viewpager);
		mMyPagerAdapter = new MyPagerAdapter();
		mViewPager.setAdapter(mMyPagerAdapter);
		mViewPager.setOnPageChangeListener(mOnPageChangeListener);
	
		ActionBar a = ((Activity)getContext()).getActionBar();
		if(a != null){
			a.setBackgroundDrawable(getResources().getDrawable(R.drawable.action_bar));
			a.setDisplayShowHomeEnabled(false);
			a.setDisplayShowTitleEnabled(true);
			//a.show();
		}
		//后续默认显示第一个页面，暂时先默认设置为第二个页面
		mViewPager.setCurrentItem(1);
		mViewPager.setCurrentItem(0);

		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.quit_title);
		builder.setIcon(R.drawable.ic_launcher);
		builder.setMessage(R.string.quit_message);
		builder.setPositiveButton(android.R.string.ok, (arg0, arg1) -> {
			arg0.dismiss();
			((Activity)getContext()).finish();
		});
		
		builder.setNeutralButton(android.R.string.cancel, (arg0, arg1) -> arg0.dismiss());
		
		mQuitDialog = builder.create(); 
	}
		
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Log.i(TAG,"onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public void onClick(View v) {
		switch(v.getId()){
			case R.id.camera_preview:
				mViewPager.setCurrentItem(0, true);
				break;
			case R.id.phone_files:
				Log.i(TAG,"onClick:phone_files");
				mViewPager.setCurrentItem(1, true);
				break;
		}
	}
	
	public Cling initCling(int clingId, int[] positionData, boolean animate, int delay) {
        final Cling cling = (Cling) findViewById(clingId);
        if (cling != null) {
            cling.init(positionData);
            cling.setVisibility(View.VISIBLE);
            if(Build.VERSION.SDK_INT >= 11){
	            cling.setLayerType(View.LAYER_TYPE_HARDWARE, null);
	            if (animate) {
	                cling.buildLayer();
	                cling.setAlpha(0f);
	                cling.animate()
	                    .alpha(1f)
	                    .setInterpolator(new AccelerateInterpolator())
	                    .setDuration(SHOW_CLING_DURATION)
	                    .setStartDelay(delay)
	                    .start();
	            } else {
	                cling.setAlpha(1f);
	            }
            }
            cling.setFocusableInTouchMode(true);
            cling.post(new Runnable() {
                public void run() {
                    cling.setFocusable(true);
                    cling.requestFocus();
                }
            });
        }
        return cling;
	}
	
	public void dismissPreviewCling(View v) {
	     Cling cling = (Cling) findViewById(R.id.preview_cling);
	     dismissCling(cling, Cling.WORKSPACE_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
	     SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
	     SharedPreferences.Editor ed = sp.edit();
	     ed.putBoolean(KEY_PREVIEW_CLING, false);
	     ed.commit();
	}
	
	public void dismissCarCling(View v) {
	     Cling cling = (Cling) findViewById(R.id.car_cling);
	     dismissCling(cling, Cling.WORKSPACE_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
	     SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
	     SharedPreferences.Editor ed = sp.edit();
	     ed.putBoolean(KEY_CAR_CLING, false);
	     ed.commit();
	}
	
	public void dismissPhoneCling(View v) {
	     Cling cling = (Cling) findViewById(R.id.phone_cling);
	     dismissCling(cling, Cling.WORKSPACE_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
	     SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
	     SharedPreferences.Editor ed = sp.edit();
	     ed.putBoolean(KEY_PHONE_CLING, false);
	     ed.commit();
	}
	
	public void dismissCloudCling(View v){
	}
	
	private void dismissCling(final Cling cling, final String flag, int duration) {
        // To catch cases where siblings of top-level views are made invisible, just check whether
        // the cling is directly set to GONE before dismissing it.
        if (cling != null && cling.getVisibility() != View.GONE) {
        	if(Build.VERSION.SDK_INT >= 11){
	            ObjectAnimator anim = ofFloat(cling, "alpha", 0f);
	            anim.setDuration(duration);
	            anim.addListener(new AnimatorListenerAdapter() {
	                public void onAnimationEnd(Animator animation) {
	                    cling.setVisibility(View.GONE);
	                    cling.cleanup();
	                };
	            });
	            anim.start();
        	}else{
        		cling.setVisibility(View.GONE);
                cling.cleanup();
        	}
        }
    }
	
	private ObjectAnimator ofFloat(View target, String propertyName, float... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setPropertyName(propertyName);
        anim.setFloatValues(values);
        return anim;
    }
	
	private ViewPager.OnPageChangeListener mOnPageChangeListener = new ViewPager.OnPageChangeListener(){

		@Override
		public void onPageScrollStateChanged(int state) {
			Log.i(TAG,"onPageScrollStateChanged:state = " + state);
		}

		@Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
			//Log.i(TAG,"onPageScrolled:position = " + position + ", positionOffset = " + positionOffset + ", positionOffsetPixels = " + positionOffsetPixels );
		}

		@Override
		public void onPageSelected(int position) {
			//切换页面
			Log.i(TAG,"onPageSelected:position = " + position);
			if(mCurrentPagerView != null)
				mCurrentPagerView.onDeactivate();
			
			mCurrentPagerView = mListViews.get(position);
			mCurrentPagerView.onActivate();
			
			for(TabPageView v : mTabPageViews)
				v.setTabSelect(false);
			
			mTabPageViews[sShowAllTabs?position:(position+1)].setTabSelect(true);
			ActionBar a = ((Activity)CarAssistMainView.this.getContext()).getActionBar();
			mCurrentTitle = mTitleRes[sShowAllTabs?position:(position+1)];
			if(a != null){
				a.setTitle(mCurrentTitle);
			}
			
			if(Build.VERSION.SDK_INT >= 14)
				((Activity)CarAssistMainView.this.getContext()).invalidateOptionsMenu();
		}
		
	};
	
	private class MyPagerAdapter extends PagerAdapter {

		@Override  
		public boolean isViewFromObject(View arg0, Object arg1) {  
		    return arg0 == arg1;  
		}  
		
		@Override  
		public int getCount() {  
		    return mListViews.size();  
		}  
		
		@Override  
		public void destroyItem(ViewGroup container, int position, Object object) {  
		    container.removeView(mListViews.get(position));  
		}  
		
		@Override  
		public int getItemPosition(Object object) {  
		    return super.getItemPosition(object);  
		}  
		
		@Override  
		public Object instantiateItem(ViewGroup container, int position) {  
		    container.addView(mListViews.get(position));  
		    return mListViews.get(position);  
		}  
		
	}
}
