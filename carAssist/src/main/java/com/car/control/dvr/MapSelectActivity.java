package com.car.control.dvr;

import android.app.ActionBar;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import com.car.control.BaseActivity;
import com.car.control.R;


public class MapSelectActivity extends BaseActivity {
	
	private static final String TAG = "CarSvc_MapSelectActivity";

	public static final String KEY_LATITUDE = "key_latitude";
	public static final String KEY_LONGITUDE = "key_longitude";
	public static final String KEY_NAME = "key_name";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map_select);

		ActionBar a = getActionBar();
		if (a != null) {
			a.setBackgroundDrawable(getResources().getDrawable(R.drawable.action_bar));
			setActionBarMidtitleAndUpIndicator(getString(R.string.title_select_point), R.drawable.back);
		}
	}
	@Override
    public void onBackPressed() {
    }
	
	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        if (item.getItemId() == android.R.id.home){
        	onBackPressed();
        }
        return true;
    }
	
	private void setActionBarMidtitleAndUpIndicator(String title, int upRes) {
		ActionBar bar = this.getActionBar();
		bar.setDisplayHomeAsUpEnabled(true);
		if (Build.VERSION.SDK_INT >= 18)
			bar.setHomeAsUpIndicator(upRes);
		bar.setTitle(R.string.back);
		bar.setDisplayShowTitleEnabled(true);
		bar.setDisplayShowHomeEnabled(false);
		TextView textview = new TextView(this);
		textview.setText(title);
		textview.setTextColor(Color.WHITE);
		textview.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.title_size));
		bar.setCustomView(textview,
				new ActionBar.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		bar.setDisplayShowCustomEnabled(true);
	}
}
