
package com.car.control.dvr;

import java.util.List;

import com.bumptech.glide.Glide;
import com.car.control.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class UserAdapter extends BaseAdapter{
	private static final String TAG = "Car_UserAdapter";
	private Context mContext;
	private List<UserItem> mUserItemList;
	private LayoutInflater mInflater;

	
	public UserAdapter(Context c, List<UserItem> list){
		mContext = c;
		mUserItemList = list;
		mInflater = LayoutInflater.from(c);
	}
	
	public void setList(List<UserItem> list) {
		mUserItemList = list;
	}

	@Override
	public int getCount() {
		return mUserItemList.size();
	}

	@Override
	public Object getItem(int arg0) {
		return mUserItemList.get(arg0);
	}

	@Override
	public long getItemId(int arg0) {
		return arg0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup par) {
		UserItem item = mUserItemList.get(position);
		final ViewHolder holder;
	
		if(convertView == null){
			convertView = mInflater.inflate(R.layout.user_item, null);
			holder =  new ViewHolder();
			holder.mNameView = (TextView)convertView.findViewById(R.id.user_name);
			holder.mImg = (ImageView)convertView.findViewById(R.id.user_head);
			convertView.setTag(holder);
		}
		else {
			holder = (ViewHolder) convertView.getTag();
		}
		
		String name = item.name;
		if(name == null || name.length() <= 0)
			holder.mNameView.setText(R.string.no_name);
		else
			holder.mNameView.setText(name);

		Glide.with(mContext)
				.load(item.headImg)
				.placeholder(R.drawable.head_img)
				.error(R.drawable.head_img)
				.override(200, 200)
				.into(holder.mImg);
				
		return convertView;
	}
	
	private class ViewHolder {
		TextView mNameView;
		ImageView mImg;
	}
}
