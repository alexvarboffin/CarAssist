package com.car.common;

import java.util.ArrayList;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * a class used to call Runnable when network is connected.
 * 
 * Usage:
 * call init() in Application.onCreate firstly,
 * 
 * call registerNetConnected to register runnable.
 * 
 * Note: the runnable just call once if network connected.
 * 
 * 
 * NetChangeReceiver.isConnected is a helper static class can be used freely.
 */
public class NetChangeReceiver extends BroadcastReceiver {

	private static final String TAG = "CarSvc_NetChange";
	private static NetChangeReceiver sInst;
	
	private Application mApplication;
	private final ArrayList<Runnable> mRunList = new ArrayList<Runnable>();
	 

	public static NetChangeReceiver instance() {
		return sInst;
	}
	
	public void init(Application application) {
		mApplication = application;

		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		mApplication.registerReceiver(this, filter);
		sInst = this;
	}

	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (!mRunList.isEmpty()) {
			if (isConnected(context)) {
				//use try/finally to prevent r.run exception.
				try {
					for (Runnable r : mRunList) {
						r.run();
					}
				} finally {
					// clear if noticed
					mRunList.clear();
				}
			}
		}
	}

	/**
	 * Note: just call once and auto unregister if net connected.
	 */
	public void registerNetConnected(Runnable runnable) {
		mRunList.add(runnable);
	}

	public void unregisterNetConnected(Runnable runnable) {
		mRunList.remove(runnable);
	}
	
	//Note: this function can be called directly as it's a static function.
	public static boolean isConnected(Context context) {
		try {
			ConnectivityManager connectivity = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (connectivity != null) {
				NetworkInfo info = connectivity.getActiveNetworkInfo();
				if (info != null && info.isConnected()) {
					Log.d(TAG, info.getTypeName() + " return network connected.");
					return true;
				}
			}
		} catch (Exception e) {
			Log.w("CarSvc_NetCheck", "isConnected error", e);
		}
		return false;
	}
}
