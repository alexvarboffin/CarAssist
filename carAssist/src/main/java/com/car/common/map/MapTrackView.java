package com.car.common.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.baidu.mapapi.map.BaiduMap;
import com.media.tool.GPSData;

import java.util.List;

public abstract class MapTrackView extends RelativeLayout {

	protected MapListener mMapListener;
	Context mContext;

	public MapTrackView(Context context) {
		super(context);
		mContext = context;
	}

	public MapTrackView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		// TODO Auto-generated constructor stub
	}

	public MapTrackView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		// TODO Auto-generated constructor stub
	}

	public static MapTrackView create(Context context) {
		MapTrackView view;
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			view = new BaiduMapTrackView(context);

		view.setLayoutParams(lp);
		return view;
	}

	public interface MapListener {
		void onPreDrawLineTrack();
		void onAfterDrawLineTrack(List<GPSData> list);
	}

	public void setMapListener(MapListener l) {
		mMapListener = l;
	}

	public void drawTrackCar(GPSData data){
		drawTrackCar(data, false);
	}

	public interface SnapshotReadyCallback {
		void onSnapshotReady(final Bitmap bitmap);
	}

	public void toBitmap(final SnapshotReadyCallback callback) {
		if (this instanceof BaiduMapTrackView) {
			((BaiduMapTrackView)this).getBaiduMap().snapshot(new BaiduMap.SnapshotReadyCallback() {

				@Override
				public void onSnapshotReady(final Bitmap bitmap) {
					callback.onSnapshotReady(bitmap);
				}

			});
			return;
		}
	}

	public abstract void drawRemoteTrackLine(String url);

	public abstract void drawTrackLine(List<GPSData> list);

	public abstract void drawTrackCar(GPSData data, boolean center);

	public abstract void drawTrackCar(GPSData data, boolean center, boolean connectLine);

	public abstract void clear();

	public abstract void setShowCarInfo(boolean show);

	public abstract void setShowCarInfoTime(boolean show);

	public abstract void resetFirstCarLoc();

	public abstract void setLocationEnabled(boolean enable);

	public abstract void onCreate(Bundle savedInstanceState);

	public abstract void onPause();

	public abstract void onResume();

	public abstract void onDestroy();

}
