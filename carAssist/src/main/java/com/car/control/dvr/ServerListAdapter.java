
package com.car.control.dvr;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.car.control.R;
import com.car.control.R.id;
import com.car.control.R.layout;
import com.car.control.util.NetworkListener;

public class ServerListAdapter extends BaseAdapter{
	
	private List<NetworkListener.ServerInfo> mServerList;
	private LayoutInflater mInflater;
	private String mCurrentSerialNo = "0000";
	
	public ServerListAdapter(List<NetworkListener.ServerInfo> list, Context c){
		mServerList = list;
		mInflater = LayoutInflater.from(c);
	}

	@Override
	public int getCount() {
		return mServerList.size();
	}

	@Override
	public Object getItem(int arg0) {
		return mServerList.get(arg0);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	public void setCurrentSelect(String no){
		mCurrentSerialNo = no;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		NetworkListener.ServerInfo info = mServerList.get(position);
		
		if (convertView == null) {
			convertView = mInflater.inflate(R.layout.server_item, null);
        }
		
		ViewHolder vh = (ViewHolder)convertView.getTag();
		if(vh == null){
			vh = new ViewHolder();
			vh.mServerName = (TextView)convertView.findViewById(R.id.server_name);
			vh.mServerSerial = (TextView)convertView.findViewById(R.id.server_serial);
			vh.mServerIP = (TextView)convertView.findViewById(R.id.server_ipaddr);
			vh.mServerImageCur = (ImageView)convertView.findViewById(R.id.server_select);
			vh.mServerCheckBox = (CheckBox)convertView.findViewById(R.id.server_checkbox);
			convertView.setTag(vh);
		}
		
		vh.mServerName.setText(info.toString());
		vh.mServerSerial.setText(info.serialNo);
		vh.mServerIP.setText(info.ipAddr);
		if(info.serialNo.equals(mCurrentSerialNo))
		    vh.mServerImageCur.setVisibility(View.VISIBLE);
		else
		    vh.mServerImageCur.setVisibility(View.GONE);
		vh.mServerCheckBox.setChecked(info.serialNo.equals(mCurrentSerialNo));
		
		return convertView;
	}
	
	private class ViewHolder{
		TextView mServerName;
		TextView mServerSerial;
		TextView mServerIP;
		ImageView mServerImageCur;
		CheckBox mServerCheckBox;
	}
	
}
