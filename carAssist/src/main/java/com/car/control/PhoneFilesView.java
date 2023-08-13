
package com.car.control;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.car.common.util.FileMediaType;
import com.car.control.browser.FileInfo;
import com.car.control.browser.FileListAdapter;
import com.car.control.browser.FileScanner;
import com.car.control.browser.PhotoActivity;
import com.car.control.browser.VideoActivity;
import com.tonicartos.widget.stickygridheaders.StickyGridHeadersGridView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//本地文件列表
@SuppressLint("NewApi")
public class PhoneFilesView extends IPagerView {

	private static final String TAG = "CarSvc_PhoneFilesView";

	private static final String LOCK_PATH = Config.CARDVR_LOCK_PATH;
	private static final String CAPTURE_PATH = Config.CARDVR_CAPTURE_PATH;
	private static final String LOOP_PATH = Config.CARDVR_LOOP_PATH;
	private static final String EDIT_PATH = Config.CARDVR_EDIT_PATH;
	// 未加锁，所有对mFileList的操作需在UI线程
	private List<FileInfo> mFileList = new ArrayList<FileInfo>();
	private List<FileInfo> mSelectFileList = new ArrayList<FileInfo>();

	private FileScanner mFileScanner;
	private FileListAdapter mAdapter;
	private StickyGridHeadersGridView mGridView;

	private String mCurrentPath = LOCK_PATH;

	private boolean mSelectMode = false;
	private boolean isSharing = false;

	private ProgressDialog mProgressDialog;
	private DeleteThread mDeleteThread;
	private RadioGroup mTabRadioGroup;
	private TextView mNoFile;

	private View mCurPhoneFilesView = null;

	public PhoneFilesView(Context context) {
		super(context);
		initView();
	}

	public PhoneFilesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public PhoneFilesView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView();
	}

	public void initPhoneFilesView(CarAssistMainView carAssistMainView) {
	}

	@Override
	public boolean onCreateOptionsMenu(MenuInflater mi, Menu menu) {
		Log.d(TAG, "isSharing=" + isSharing);
		if (isSharing) {
			if (mCurPhoneFilesView != null){
				mi.inflate(R.menu.phone_files_share, menu);
			}else{
				mi.inflate(R.menu.phone_files, menu);
			}
			return true;
		}
		if (!mSelectMode) {
			mi.inflate(R.menu.phone_files, menu);
		} else {
			mi.inflate(R.menu.phone_files_mutiple, menu);
			MenuItem item = menu.findItem(R.id.phone_file_select);
			if (isSelectAll())
				item.setIcon(R.drawable.unselect_all);
			else
				item.setIcon(R.drawable.select_all);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.multiple) {
			mSelectMode = true;
			mSelectFileList.clear();
			for (FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			if (Build.VERSION.SDK_INT >= 14)
				((CarControlActivity) getContext()).invalidateOptionsMenu();
			return true;
		} else if (item.getItemId() == R.id.phone_file_delete) {
			List<FileInfo> list = new ArrayList<FileInfo>();
			list.addAll(mSelectFileList);
			mDeleteThread = new DeleteThread(list);
			mDeleteThread.start();
			mSelectMode = false;
			mSelectFileList.clear();
			for (FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			if (Build.VERSION.SDK_INT >= 14)
				((CarControlActivity) PhoneFilesView.this.getContext()).invalidateOptionsMenu();
			return true;
		} else if (item.getItemId() == R.id.phone_file_select) {
			if (isSelectAll()) {
				for (FileInfo info : mFileList)
					info.selected = false;
				mSelectFileList.clear();
				mAdapter.setSelectMode(mSelectMode);
			} else {
				mSelectFileList.clear();
				for (FileInfo info : mFileList) {
					if (info.name.equals(".."))
						continue;
					info.selected = true;
					mSelectFileList.add(info);
				}
				mAdapter.setSelectMode(mSelectMode);
			}
			if (Build.VERSION.SDK_INT >= 14)
				((CarControlActivity) PhoneFilesView.this.getContext()).invalidateOptionsMenu();
			return true;
		}
		return false;
	}

	@Override
	public void onActivate() {
		Log.i(TAG, "onActivate()");
		mHandler.removeMessages(REFRESH_PAHT);
		mHandler.sendEmptyMessageDelayed(REFRESH_PAHT, 500);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean show = sp.getBoolean(CarAssistMainView.KEY_PHONE_CLING, true);
		if (show)
			((CarControlActivity) getContext()).initCling(R.id.phone_cling, null, false, 0);
	}

	@Override
	public void onDeactivate() {
		Log.i(TAG, "onDeactivate()");
		mSelectMode = false;
		mSelectFileList.clear();
		for (FileInfo info : mFileList)
			info.selected = false;
		mAdapter.setSelectMode(mSelectMode);
		if (Build.VERSION.SDK_INT >= 14)
			((CarControlActivity) getContext()).invalidateOptionsMenu();
	}

	@Override
	public void onActivityCreate(Bundle savedInstanceState) {}

	@Override
	public void onActivityPause() {
		Log.i(TAG, "onActivityPause()");
	}

	@Override
	public void onAcitvityResume() {
		Log.i(TAG, "onAcitvityResume()");
	}

	@Override
	public void onActivityStart() {
		Log.i(TAG, "onActivityStart()");
	}

	@Override
	public void onActivityStop() {
		Log.i(TAG, "onActivityStop()");
		mSelectMode = false;
		mSelectFileList.clear();
		for (FileInfo info : mFileList)
			info.selected = false;
		mAdapter.setSelectMode(mSelectMode);
		if (Build.VERSION.SDK_INT >= 14)
			((CarControlActivity) PhoneFilesView.this.getContext()).invalidateOptionsMenu();
	}

	@Override
	public void onActivityDestroy() {
		Log.i(TAG, "onActivityDestroy()");
	}

	@Override
	public boolean onBackPressed() {
		Log.i(TAG, "onBackPressed()");
		if (isSharing) {
			isSharing = false;
		}

		if (mCurPhoneFilesView != null) {
			mCurPhoneFilesView.setVisibility(View.GONE);
			setCurPhoneFilesView(null);
			if (Build.VERSION.SDK_INT >= 14)
				((CarControlActivity) getContext()).invalidateOptionsMenu();
			return true;
		}

		if (mSelectMode) {
			mSelectMode = false;
			mSelectFileList.clear();
			for (FileInfo info : mFileList)
				info.selected = false;
			mAdapter.setSelectMode(mSelectMode);
			if (Build.VERSION.SDK_INT >= 14)
				((CarControlActivity) getContext()).invalidateOptionsMenu();
			return true;
		}

		return false;
	}

	@Override
	public void refresh() {
		runFileList(mCurrentPath);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

	}

	private void initView() {
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.phone_files_list, this);
		mNoFile = (TextView)findViewById(R.id.phone_no_file);
		mGridView = (StickyGridHeadersGridView) findViewById(R.id.gridview);

		mGridView.setOnItemClickListener(new ItemClickListener());
		mGridView.setOnItemSelectedListener(new ItemSelectedListener());
		mGridView.setOnItemLongClickListener(new ItemLongClickListener());

		mAdapter = new FileListAdapter(getContext(), mFileList, false);
		mGridView.setAdapter(mAdapter);

		mProgressDialog = new ProgressDialog(getContext());
		mProgressDialog.setMessage(getContext().getString(R.string.deleting_files));
		mProgressDialog.setCancelable(false);

		mTabRadioGroup = (RadioGroup) findViewById(R.id.phone_file_fragmen_tab);
		((RadioButton) findViewById(R.id.phone_file_capture)).setChecked(true);
		mTabRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				switch (checkedId) {
				case R.id.phone_file_lock:
					runFileList(LOCK_PATH, FileScanner.RESULT_TYPE_SCANNER);
					break;
				case R.id.phone_file_capture:
					runFileList(CAPTURE_PATH, FileScanner.RESULT_TYPE_SCANNER);
					break;
				case R.id.phone_file_loop:
					runFileList(LOOP_PATH, FileScanner.RESULT_TYPE_SCANNER);
					break;
				case R.id.phone_file_edit:
					runFileList(EDIT_PATH, FileScanner.RESULT_TYPE_SCANNER);
					break;
				}
			}
		});

		runFileList(CAPTURE_PATH, FileScanner.RESULT_TYPE_SCANNER);
	}

	private boolean runFileList(String filePath) {
		return runFileList(filePath, FileScanner.RESULT_TYPE_SCANNER);
	}

	private boolean runFileList(String filePath, int type) {
		if (TextUtils.isEmpty(filePath))
			return false;

		Log.d(TAG, "runFileList, path=" + filePath);
		if (mFileScanner == null) {
			mFileScanner = new FileScanner() {
				@Override
				public void onResult(int type, String scanPath, ArrayList<FileInfo> fileList) {
					mCurrentPath = scanPath;
					mHandler.removeMessages(SCAN_FINISHED);
					Message msg = mHandler.obtainMessage(SCAN_FINISHED, fileList);
					mHandler.sendMessage(msg);
				}
			};
		}

		if (type == FileScanner.RESULT_TYPE_SCANNER) {
			mFileScanner.startScanner(filePath, false);
		}

		return true;
	}

	private void openFile(FileInfo finfo) {

		File f = new File(finfo.getFullPath());
		Intent intent = null;

		if (finfo.fileType == FileMediaType.IMAGE_TYPE) {
			intent = new Intent(getContext(), PhotoActivity.class);
			intent.putExtra(PhotoActivity.KEY_PHOTO_PATH, mCurrentPath);
			intent.putExtra(PhotoActivity.KEY_PHOTO_CURRENT, finfo.name);
		} else if (finfo.fileType == FileMediaType.VIDEO_TYPE) {
			intent = new Intent(getContext(), VideoActivity.class);
			intent.setDataAndType(Uri.fromFile(f), FileMediaType.getOpenMIMEType(finfo.fileType));
		} else {
			intent = new Intent();
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setAction(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(f), FileMediaType.getOpenMIMEType(finfo.fileType));
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		}

		try {
			getContext().startActivity(intent);
			return;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setCurPhoneFilesView(View view) {
		mCurPhoneFilesView = view;
		if (view != null) {
			if (Build.VERSION.SDK_INT >= 14)
				((CarControlActivity) getContext()).invalidateOptionsMenu();
		}
	}

	public void enableShare(boolean enabled) {
		isSharing = enabled;
	}

	private boolean shareFile(List<FileInfo> list) {
		if (list.size() == 0) {
			Toast.makeText(getContext(), R.string.share_file_mimetype_not_supported, Toast.LENGTH_SHORT).show();
			return false;
		}

		ArrayList<Uri> uris = new ArrayList<Uri>();
		FileInfo finfo;
		String mimeType = null;
		boolean sameType = true;
		for (int i = 0; i < list.size(); i++) {
			finfo = list.get(i);
			File file = new File(finfo.getFullPath());
			String type = FileMediaType.getOpenMIMEType(finfo.fileType);
			if (mimeType != null && !mimeType.equals(type)) {
				sameType = false;
			}
			mimeType = type;
			Uri u = Uri.fromFile(file);
			uris.add(u);
		}

		if (!sameType) {
			Toast.makeText(getContext(), R.string.share_file_mimetype_not_supported, Toast.LENGTH_SHORT).show();
			return false;
		}

		boolean multiple = uris.size() > 1;
		if (multiple && mimeType.equals(FileMediaType.getOpenMIMEType(FileMediaType.VIDEO_TYPE))) {
			Toast.makeText(getContext(), R.string.share_file_mimetype_not_supported, Toast.LENGTH_SHORT).show();
			return false;
		}

		return true;
	}

	private boolean isSelectAll() {
		return (mFileList.size() != 0 && mFileList.size() == mSelectFileList.size());
	}

	public static final int SCAN_FINISHED = 998;
	public static final int SHOW_PROGRESS_DIALOG = 999;
	public static final int DISMISS_PROGRESS_DIALOG = 1000;
	public static final int REFRESH_PAHT = 1001;
	final Handler mHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case SCAN_FINISHED:
				mFileList.clear();
				List<FileInfo> list = (List<FileInfo>) msg.obj;
				if (list.size() > 0){
					mNoFile.setVisibility(View.GONE);
				}else{
					mNoFile.setVisibility(View.VISIBLE);
					switch (mTabRadioGroup.getCheckedRadioButtonId()) {
					case R.id.phone_file_lock:
						mNoFile.setText(getContext().getString(R.string.no_lock_files));
						break;
					case R.id.phone_file_capture:
						mNoFile.setText(getContext().getString(R.string.no_capture_files));
						break;
					case R.id.phone_file_loop:
						mNoFile.setText(getContext().getString(R.string.no_loop_files));
						break;
					case R.id.phone_file_edit:
						mNoFile.setText(getContext().getString(R.string.no_edit_files));
						break;
					default:
						mNoFile.setText(getContext().getString(R.string.no_file));
					}
				}
				mFileList.addAll(list);
				mAdapter.notifyDataSetChanged();
				mSelectMode = false;
				mSelectFileList.clear();
				for (FileInfo info : mFileList)
					info.selected = false;
				mAdapter.setSelectMode(mSelectMode);
				if (Build.VERSION.SDK_INT >= 14)
					((CarControlActivity) getContext()).invalidateOptionsMenu();
				break;
			case SHOW_PROGRESS_DIALOG:
				mProgressDialog.show();
				break;
			case DISMISS_PROGRESS_DIALOG:
				mProgressDialog.dismiss();
				break;
			case REFRESH_PAHT:
				refresh();
				break;
			}
		}
	};

	private class ItemClickListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if(position < 0 || position >= mFileList.size())
				return;
			FileInfo finfo = mFileList.get(position);
			if (!mSelectMode) {

				if (finfo.name.equals("..")) {
					runFileList(new File(mCurrentPath).getParent());
					return;
				}

				String path = finfo.getFullPath();
				File f = new File(path);
				if (f.isDirectory()) {
					runFileList(path);
				} else {
					openFile(finfo);
					mAdapter.setCurrentPosition(position);
				}
			} else {
				boolean old = isSelectAll();
				if (mSelectFileList.contains(finfo)) {
					finfo.selected = false;
					mSelectFileList.remove(finfo);
					mAdapter.notifyDataSetChanged();
				} else {
					finfo.selected = true;
					mSelectFileList.add(finfo);
					mAdapter.notifyDataSetChanged();
				}
				boolean now = isSelectAll();
				if (old != now) {
					if (Build.VERSION.SDK_INT >= 14)
						((CarControlActivity) PhoneFilesView.this.getContext()).invalidateOptionsMenu();
				}

			}

		}
	}

	private class ItemSelectedListener implements OnItemSelectedListener {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	}

	private class ItemLongClickListener implements OnItemLongClickListener {
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			if(position < 0 || position >= mFileList.size())
				return true;
			FileInfo finfo = mFileList.get(position);
			if (mSelectMode) {
				mSelectMode = false;
				mSelectFileList.clear();
				for (FileInfo info : mFileList)
					info.selected = false;
				mAdapter.setSelectMode(mSelectMode);
				if (Build.VERSION.SDK_INT >= 14)
					((CarControlActivity) getContext()).invalidateOptionsMenu();
			} else {
				mSelectMode = true;
				mSelectFileList.clear();
				for (FileInfo info : mFileList)
					info.selected = false;
				mAdapter.setSelectMode(mSelectMode);
				finfo.selected = true;
				mSelectFileList.add(finfo);
				mAdapter.notifyDataSetChanged();
				if (Build.VERSION.SDK_INT >= 14)
					((CarControlActivity) getContext()).invalidateOptionsMenu();
			}
			return true;
		}
	}

	private class DeleteThread extends Thread {

		private List<FileInfo> mDeleteList;
		private boolean mCancel = false;

		public DeleteThread(List<FileInfo> list) {
			mDeleteList = list;
		}

		@Override
		public void run() {
			mHandler.removeMessages(SHOW_PROGRESS_DIALOG);
			mHandler.sendEmptyMessage(SHOW_PROGRESS_DIALOG);
			boolean success = true;
			for (FileInfo info : mDeleteList) {
				if (mCancel) {
					success = false;
					break;
				}

				if (info.name.equals("..")) {
					continue;
				}

				String path = info.getFullPath();
				File file = new File(path);
				if (file.exists() && file.isDirectory() && !delDir(file)) {
					success = false;
					break;
				} else if (file.exists() && file.isFile() && !delFile(file)) {
					success = false;
					break;
				}
			}

			mHandler.removeMessages(DISMISS_PROGRESS_DIALOG);
			mHandler.sendEmptyMessage(DISMISS_PROGRESS_DIALOG);

			final boolean s = success;
			mHandler.post(new Runnable() {

				@Override
				public void run() {
					if (s) {
						Toast.makeText(getContext(), R.string.tip_delete_success, Toast.LENGTH_SHORT).show();
						runFileList(mCurrentPath);
					} else
						Toast.makeText(getContext(), R.string.tip_delete_success, Toast.LENGTH_SHORT).show();
				}

			});
		}

		public void setCancel(boolean cancel) {
			mCancel = cancel;
		}

		private boolean delFile(File f) {

			if (mCancel)
				return false;

			boolean ret = true;
			try {
				if (f.exists()) {
					ret = f.delete();
				}
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return ret;
		}

		private boolean delDir(File f) {
			boolean ret = true;
			try {
				if (f.exists()) {
					File[] files = f.listFiles();
					for (int i = 0; i < files.length; i++) {

						if (mCancel)
							return false;

						if (files[i].exists() && files[i].isDirectory()) {
							if (!delDir(files[i])) {
								return false;
							}
						} else {
							if (files[i].exists() && !files[i].delete()) {
								return false;
							}
						}
					}

					if (mCancel)
						return false;

					if (f.exists())
						ret = f.delete();
				}
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return ret;
		}

	}
}
