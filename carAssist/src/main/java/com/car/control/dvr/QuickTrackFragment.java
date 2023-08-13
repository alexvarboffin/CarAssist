package com.car.control.dvr;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.model.LatLng;
import com.media.tool.GPSData;
import com.car.control.R;
import com.car.common.map.MapTrackView;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import java.util.Objects;

public class QuickTrackFragment extends RelativeLayout implements BaiduMap.OnMapClickListener {

    private static final String TAG = "CarSvc_QuickTrackFragment";

    private MapTrackView mMapTrackView;
    private Handler mHandler = new Handler();

    public QuickTrackFragment(Context context) {
        super(context);
        initView();
    }

    public QuickTrackFragment(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public QuickTrackFragment(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    public int setVideoLocation(GPSData data) {

        mMapTrackView.drawTrackCar(data);

        return 0;
    }


    public void onCreate(Bundle savedInstanceState) {
        if (null != mMapTrackView) {
            mMapTrackView.onCreate(savedInstanceState);
        }
    }

    public void onPause() {
        mHandler.postDelayed(() -> {
            if (null != mMapTrackView) {
                mMapTrackView.onPause();
                if (getVisibility() == View.VISIBLE) {
                    mMapTrackView.setLocationEnabled(false);
                }
            }
        }, 500);


    }

    public void onResume(final boolean activity) {
        mHandler.postDelayed(() -> {
            if (null != mMapTrackView) {
                mMapTrackView.onResume();
                if (getVisibility() == View.VISIBLE && activity) {
                    mMapTrackView.setLocationEnabled(true);
                }
            }
        }, 500);

    }

    public void onDestroy() {
        mHandler.postDelayed(() -> {
            if (null != mMapTrackView) {
                mMapTrackView.onDestroy();
            }
        }, 500);
    }

    @Override
    public void setVisibility(int visibility) {
        if (getVisibility() == visibility)
            return;
        super.setVisibility(visibility);
        if (null != mMapTrackView) {
            if (visibility == View.VISIBLE) {
                mMapTrackView.setLocationEnabled(true);
            } else {
                mMapTrackView.setLocationEnabled(false);
            }
        }
    }

    @Override
    public void onMapClick(LatLng arg0) {

    }

    @Override
    public boolean onMapPoiClick(MapPoi arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    private void initView() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            inflater.inflate(R.layout.quick_track_fragment, this);
        }

        try {
            mMapTrackView = MapTrackView.create(getContext());
            addView(mMapTrackView);
        } catch (Exception r) {

        }
    }
}
