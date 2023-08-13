package com.car.control.dvr;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.baidu.mapapi.search.sug.SuggestionResult.SuggestionInfo;
import com.car.control.R;

import java.util.List;

public class MapSearchListAdapter extends BaseAdapter {
	private static final String TAG = MapSearchListAdapter.class.getSimpleName();

	private Context mContext;
	private ListItemView mListItemView;
	private LayoutInflater mLayoutInflater;
	private List<SuggestionInfo> mItemList;

	public class ListItemView {
		public TextView txtviewAddress;
		public TextView txtviewCity;
		public View address;
		public View cleanHistory;
	}

	public MapSearchListAdapter(Context context, List<SuggestionInfo> itemList) {
		this.mContext = context;
		mLayoutInflater = LayoutInflater.from(mContext);
		this.mItemList = itemList;
	}

	@Override
	public int getCount() {
		return mItemList.size();
	}

	@Override
	public Object getItem(int position) {
		return mItemList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			mListItemView = new ListItemView();
			convertView = mLayoutInflater.inflate(R.layout.listview_item_mapsearch, null);
			mListItemView.txtviewAddress = (TextView) convertView.findViewById(R.id.mapsearch_listview_item_txtview_address);
			mListItemView.txtviewCity = (TextView) convertView.findViewById(R.id.mapsearch_listview_item_txtview_city);
			mListItemView.address = convertView.findViewById(R.id.mapsearch_listview_item_address);
			mListItemView.cleanHistory = convertView.findViewById(R.id.mapsearch_listview_item_clean_history);
			convertView.setTag(mListItemView);
		} else {
			mListItemView = (ListItemView) convertView.getTag();
		}
		if(position == mItemList.size()-1 && mItemList.get(position) instanceof SuggestionInfo){
			mListItemView.address.setVisibility(View.GONE);
			mListItemView.cleanHistory.setVisibility(View.VISIBLE);
		}else{
			mListItemView.address.setVisibility(View.VISIBLE);
			mListItemView.cleanHistory.setVisibility(View.GONE);
			mListItemView.txtviewAddress.setText(mItemList.get(position).key);
			mListItemView.txtviewCity.setText(mItemList.get(position).city);
		}
		return convertView;
	}
}
