
package com.car.control;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.car.control.R;

public class TabPageView extends RelativeLayout{

	
	private ImageView mTabImageView;
	private TextView mTextView;
	private ImageView mTabImageViewRedpoint;
	private int mTabPageNormalID = -1;
	private int mTabPageSelectID = -1;
	private int mTextID = -1;

	public TabPageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.TabPage); 
		mTabPageNormalID = a.getResourceId(R.styleable.TabPage_tab_normal, -1);
		mTabPageSelectID = a.getResourceId(R.styleable.TabPage_tab_select, -1);
		mTextID = a.getResourceId(R.styleable.TabPage_tab_text, -1);
		initView();
	}
	
	
	public TabPageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.TabPage); 
		mTabPageNormalID = a.getResourceId(R.styleable.TabPage_tab_normal, -1);
		mTabPageSelectID = a.getResourceId(R.styleable.TabPage_tab_select, -1);
		mTextID = a.getResourceId(R.styleable.TabPage_tab_text, -1);
		initView();
	}
	
	public void setTabSelect(boolean select){
		if(select){
			if(mTabPageNormalID != -1)
	        	mTabImageView.setImageResource(mTabPageSelectID);
			mTextView.setTextColor(getResources().getColor(R.color.photo_color));
		}else{
			if(mTabPageNormalID != -1)
	        	mTabImageView.setImageResource(mTabPageNormalID);
			mTextView.setTextColor(getResources().getColor(R.color.grey));
		}
	}

	private void initView(){
		LayoutInflater inflater=(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.tab_page_view, this);
        mTabImageView = (ImageView)findViewById(R.id.tab_imageview);
        mTabImageViewRedpoint = (ImageView)findViewById(R.id.tab_imageview_redpoint);
        if(mTabPageNormalID != -1)
        	mTabImageView.setImageResource(mTabPageNormalID);
        mTextView = (TextView)findViewById(R.id.tab_text);
        if(mTextID != -1)
        	mTextView.setText(mTextID);
	}
	
	public void setNofifyFlag(boolean flag){
	    if(flag)
	        mTabImageViewRedpoint.setVisibility(View.VISIBLE);
	    else 
	        mTabImageViewRedpoint.setVisibility(View.GONE);
	}
	
}
