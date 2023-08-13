package com.car.control;

import android.support.multidex.MultiDexApplication;

import com.baidu.mapapi.SDKInitializer;
import com.car.common.ThumbnailCacheManager;
import com.car.control.util.HttpDownloadManager;
import com.car.control.util.HttpRequestManager;

import java.io.File;

public class CarControlApplication extends MultiDexApplication {
	private static final String TAG = "CarSvc_CarControlApplication";

	private static CarControlApplication mInstance;

	@Override
	public void onCreate() {
		super.onCreate();
		mInstance = this;

		try {
			SDKInitializer.initialize(this);
		}catch (java.lang.UnsatisfiedLinkError e){

		}
		ThumbnailCacheManager.initialize(this);
		HttpDownloadManager.create();
		HttpRequestManager.create();
		File file = new File(Config.CARDVR_CACHE_PATH);
		if (!file.exists()) file.mkdirs();
		file = new File(Config.CARDVR_AD_PATH);
		if (!file.exists()) file.mkdirs();
	}
	
	public static CarControlApplication getInstance() {
		return mInstance;
	}
}
