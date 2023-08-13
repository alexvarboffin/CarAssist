
package com.car.control;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.RelativeLayout;

public abstract class IPagerView extends RelativeLayout{

	public IPagerView(Context context) {
		super(context);
	}
	
	public IPagerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public IPagerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	public abstract void onActivityCreate(Bundle savedInstanceState);
	
	public abstract boolean onCreateOptionsMenu(MenuInflater mi, Menu menu);
	
	public abstract boolean onOptionsItemSelected(MenuItem item);

	public abstract void onActivate();
	
	public abstract void onDeactivate();
	
	public abstract void onActivityPause();
	
	public abstract void onAcitvityResume();

	public abstract void onActivityDestroy();
	
	public abstract void onActivityStart();
	
	public abstract void onActivityStop();
	
	public abstract boolean onBackPressed();
	
	public abstract void refresh();
	
	public abstract void onActivityResult(int requestCode, int resultCode, Intent data);

}
