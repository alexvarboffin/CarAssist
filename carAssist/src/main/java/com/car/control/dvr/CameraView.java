package com.car.control.dvr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.car.common.util.WorkReq;
import com.car.common.util.WorkThread;
import com.car.control.CarControlActivity;
import com.car.control.Config;
import com.car.control.R;
import com.car.control.util.HttpRequestManager;
import com.media.tool.GPSData;
import com.media.tool.MediaCapture;
import com.media.tool.MediaPlayer;
import com.media.tool.MediaPlayer.onVideoLocationListener;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CameraView extends FrameLayout implements View.OnClickListener, SurfaceTextureListener,
		View.OnTouchListener, MediaCapture.onCaptureListener, onVideoLocationListener {

	private static final String TAG = "CameraView";

	private boolean mRecording = false;
	public Bitmap mBitmap;
	private MyWebSocketClient mMyWebSocketClient;
	private ImageView mPreviewImage;
	//private SurfaceView mPreviewSurface;
	//private SurfaceHolder mSurfaceHolder;
	private TextureView mTextureView;
	private SurfaceTexture mSurfaceTexture;
	private View mEmptyView;
	private WorkThread mWorkThread;
	private boolean mActivate = false;
	private MediaPlayer mMediaPlayer = null;
	private MediaCapture mMediaCapture = null;
	private ImageView mPreviewStart;
	private TextView mStatusText;
	private Object mLock = new Object();

	private Point mTouchStart = new Point();
	private int mStartProgress = 0;
	private View mPreviwContainer;
	private View mSeekContainer;
	private SeekBar mCameraSeekBar;
	private static final int BACKWARD[] = { -1, -2, -4, -8 };
	private int mBackwardIndex = 0;
	private static final int SPEED[] = { 1, 2, 4, 8 };

	private static final int VERSION_SUPPORT_MULTICAMERA = 1;
    private static final int VERSION_SUPPORT_SWITCH_RESOLUTION = 2; //camera don't do any auto switch such as switch from fast/prev to 
                                                               //SEEKMODE_REALTIME_PREVIEW or SEEKMODE_PASTVIDEO_PLAYBACK
	private static final int VERSION_SUPPORT_GPS_INFO = 3;	//when preview and fast forward and rewind, gps info also be sent through websocket
	private int mSpeedIndex = 0;
	private TextView mSpeedTip;

	private final int SEEKMODE_NONE = 1;
	// real time H264 raw data preview
	private final int SEEKMODE_REALTIME_PREVIEW = 1; 
	// feed back past H264 key Frame
	private final int SEEKMODE_PASTVIDEO_SEEK = 2; 
	// feed back past TS packet
	private final int SEEKMODE_PASTVIDEO_PLAYBACK = 3; 
	// cut some video split
	private final int SEEKMODE_PASTVIDEO_SNAPSHOT = 4;

	private final int SEEKERROR_NO_STORAGE = 1;
	private final int SEEKERROR_SEEK_FAIL = 2;
	private final int SEEKERROR_PLAYBACK_FAIL = 3;
	
	private int mSeekMode = SEEKMODE_NONE;
	private TextView mMiddleTextView = null;
	private int mSeekCookie = 0;

	private boolean mIsFullscreenMode = false;
	private Context mContext;
	private View mControlBar;
	private View mFullscreenBtn;
	private View mExitFullscreenBtn;
	private final int DELAY_HIDE_CONTROL_BAR = 5000;
	private QuickTrackFragment mQuickTrackFragment = null;
	
	private ProgressBar mProgressBar;
	
	private int mCameraNumber = 0;
	public static final String FRONT_CAMERA_STRING = "F";
	public static final String BACK_CAMERA_STRING = "B";
	public static final String INSIDE_CAMERA_STRING = "I";
	
    private String mCameraDir = FRONT_CAMERA_STRING;
    private List<String> mCameraLists = new ArrayList<String>(4);
    private int mAPPVersion = VERSION_SUPPORT_GPS_INFO;

	public CameraView(Context context) {
		super(context);
		initView(context);
	}

	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView(context);
	}

	public CameraView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initView(context);
	}
	
	public void setQuickTrackFragment(QuickTrackFragment qf){
		mQuickTrackFragment = qf;
	}

	public void showContect() {
		mStatusText.setText(R.string.tip_connected);
		mPreviewStart.setVisibility(View.VISIBLE);
		mProgressBar.setVisibility(View.GONE);
	}

	public void showDiscontect() {
		mPreviewStart.setVisibility(View.INVISIBLE);
		mProgressBar.setVisibility(View.GONE);
		mPreviewImage.setImageResource(R.drawable.no_connect);
		mPreviewImage.setVisibility(View.VISIBLE);
		mStatusText.setVisibility(View.GONE);
		mStatusText.setText(R.string.tip_no_connect);
		mCameraNumber = 0;
	    mCameraDir = FRONT_CAMERA_STRING;
	    mCameraLists.clear();
	}
	
	public void showContectting(){
		mPreviewStart.setVisibility(View.INVISIBLE);
		mProgressBar.setVisibility(View.VISIBLE);
		mPreviewImage.setImageResource(R.drawable.bg_connect);
		mStatusText.setVisibility(View.VISIBLE);
		mStatusText.setText(R.string.tip_connecting);
	}

	public boolean getActivate() {
		return mActivate;
	}
	
	public void refreshMiddleText(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
		int time = 10; //Integer.parseInt(sp.getString(SettingView.KEY_CAPTURE_TIME, "10"));
		mMiddleTextView.setText(getContext().getString(R.string.capture_short_video_time, time));
	}

	public void setDVRSDcardStatus(boolean mount) {
		if (mount) {
			mHandler.removeMessages(MSG_DISMISS_SD_UNMOUNT);
			mHandler.sendEmptyMessage(MSG_DISMISS_SD_UNMOUNT);
		} else {
			mHandler.removeMessages(MSG_SHOW_SD_UNMOUNT);
			mHandler.sendEmptyMessage(MSG_SHOW_SD_UNMOUNT);
		}
	}

	// 根据录像状态显示按钮的图片
	public void setRecordingButton(final boolean recording) {
		Log.i(TAG, "setRecordingButton:recording = " + recording);
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				mRecording = recording;
			}
		});

	}
	
	public void switchCamera() {
        String cameradir = mCameraDir;
        if(mCameraNumber != 0) {
            synchronized (mLock) {
                int i = 0;
                for(i = 0; i < mCameraNumber; i++) {
                    if(mCameraDir.equals(mCameraLists.get(i))) {
                        break;
                    }
                }

                i = (i + 1) % mCameraNumber;
                cameradir = mCameraLists.get(i);
            }
        }

        Log.d(TAG, "Switch From Camera " + mCameraDir + " to " + cameradir);
        mCameraDir = cameradir;
        startMediaPlayer();
        // request preview data
		String command = "seekmode:" + SEEKMODE_REALTIME_PREVIEW + ",cameraDir:" + mCameraDir;
		setCameraSeekMode(command);
		mHandler.post(new Runnable() {
    		@Override
    		public void run() {
                mSpeedTip.setVisibility(View.GONE);
                refreshMiddleText();

                if(mSwitchCameraView != null) {
                    mSwitchCameraView.setText(mCameraDir);
                }
            }
        });
    }

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		Log.i(TAG, "onAttachedToWindow()");
		mActivate = true;
		requestDVRSDcardStatus();
		requestDVRRecordStatus();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		Log.i(TAG, "onDetachedFromWindow()");
		mActivate = false;
		stopPreview();
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		Log.i(TAG, "drawableStateChanged()");
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.preview_container: {
			if (!mIsFullscreenMode) {
				if (mMyWebSocketClient != null && mMyWebSocketClient.isOpen()) {
					stopPreview();
				} else {
					startPreview();
				}
			} else {
				if (mControlBar.getVisibility() == View.VISIBLE) {
					hideControlBar();
				} else {
					showControlBar();
				}
			}
			break;
		}
        case R.id.switch_camera: {
            switchCamera();
            break;
        }
        
		}
	}

	MODE mMode;

	protected int mCaptureDuration = 5000; // default 5s capture

	private TextView mSwitchCameraView;

	enum MODE {
		NUL, SEEK
	};

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		int rawX = (int) event.getRawX();
		int rawY = (int) event.getRawY();
		//Log.i(TAG, "Action = " + event.getAction() + ", x =  " + rawX + ", y = " + rawY);
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mTouchStart.x = rawX;
			mTouchStart.y = rawY;
			mStartProgress = mCameraSeekBar.getProgress();
			mMode = MODE.NUL;
			if (!mIsFullscreenMode && getContext() instanceof CarControlActivity) {
				((CarControlActivity) getContext()).setPaperViewEnable(false);
			}
			break;
		case MotionEvent.ACTION_MOVE:
			int xAxisOffset = rawX - mTouchStart.x;
			if (mMode == MODE.NUL) {
				if (RemoteCameraConnectManager.getCurrentServerInfo() != null && Math.abs(xAxisOffset) > dip2px(10)) {
					mHandler.removeMessages(MSG_DISMISS_SEEKBAR_CONTAINER);
					mTouchStart.x = rawX;
					mTouchStart.y = rawY;
					mMode = MODE.SEEK;
					if (mSeekContainer.getVisibility() == View.INVISIBLE) {
						mSeekContainer.setAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.alpha_show));
						mSeekContainer.setBackgroundColor(getResources().getColor(R.color.cameraview_controlbar_background_translucent));
						mSeekContainer.setVisibility(View.VISIBLE);
					}
				}
			} else if (Math.abs(xAxisOffset) > 0) {
				if (mMode == MODE.SEEK) {
					SeekBar seekbar = mCameraSeekBar;
					int value = mStartProgress;
					value = (int)(1.0f*xAxisOffset/seekbar.getWidth()*100) + value;
					if (value > seekbar.getMax()){
						value = seekbar.getMax();
						mTouchStart.x = rawX;
						mTouchStart.y = rawY;
						mStartProgress = value;
					}
					else if (value < 0){
						value = 0;
						mTouchStart.x = rawX;
						mTouchStart.y = rawY;
						mStartProgress = value;
					}

					seekbar.setProgress(value);
				}

				//mTouchStart.x = rawX;
				//mTouchStart.y = rawY;
			}
			break;
		case MotionEvent.ACTION_UP:
			mHandler.removeMessages(MSG_DISMISS_SEEKBAR_CONTAINER);
			mHandler.sendEmptyMessageDelayed(MSG_DISMISS_SEEKBAR_CONTAINER, 2000);
			if (!mIsFullscreenMode && getContext() instanceof CarControlActivity) {
				((CarControlActivity) getContext()).setPaperViewEnable(true);
			}
			if (mMode == MODE.NUL) {
				mPreviwContainer.performClick();
			} else {
				if (mMyWebSocketClient != null && mMyWebSocketClient.isOpen()) {
					SeekBar seekbar = mCameraSeekBar;
					int value = seekbar.getProgress();
					String str;
					try {
						if (value == 100) {
							mSeekMode = SEEKMODE_REALTIME_PREVIEW;
							str = "seekmode:" + mSeekMode;
							mSpeedTip.setVisibility(View.GONE);
						} else {
							mBackwardIndex = 0;
							mSpeedIndex = 0;
							mSpeedTip.setText("X" + SPEED[0]);
							mSpeedTip.setVisibility(View.GONE);
							// switch to old file playback mode
							mSeekMode = SEEKMODE_PASTVIDEO_PLAYBACK;
							str = "seekmode:" + mSeekMode + ", seekpercent:" + value;
						}
						refreshMiddleText();

						setCameraSeekMode(str);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			break;
		}
		return true;
	}
	
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
		// TODO Auto-generated method stub
		Log.i(TAG, "onSurfaceTextureAvailable");
		mSurfaceTexture = surface;
		if(mIsFullscreenMode == true) {
            startPreview();
        }
		mEmptyView.setVisibility(View.VISIBLE);
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
		// TODO Auto-generated method stub
		Log.i(TAG, "onSurfaceTextureSizeChanged");
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		// TODO Auto-generated method stub
		Log.i(TAG, "onSurfaceTextureDestroyed");
		mSurfaceTexture = null;
		if(mIsFullscreenMode == true) {
            stopPreview();
        }
		return true;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		// TODO Auto-generated method stub
		Log.i(TAG, "onSurfaceTextureUpdated");
		if(mEmptyView.getVisibility() == View.VISIBLE)
			mEmptyView.setVisibility(View.GONE);
	}

	// 开始录像
	private void startRecording() {
		if (RemoteCameraConnectManager.supportWebsocket()) {
			try {
				JSONObject jso = new JSONObject();
				jso.put("action", "set");
				JSONObject items = new JSONObject();
				items.put(Config.PROPERTY_CAMERA_RECORDING_START, "");
				jso.put("list", items);
				jso.toString();
				Log.i(TAG, "jso.toString() = " + jso.toString());
				HttpRequestManager.instance().requestWebSocket(jso.toString());
				// setRecordingButton(true);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":"
					+ RemoteCameraConnectManager.HTTP_SERVER_PORT
					+ "/cgi-bin/Config.cgi?action=set&property=Camera.Recording.Start";
			Log.i(TAG, "url = " + url);
			HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener() {

				@Override
				public void onHttpResponse(String result) {
					Log.i(TAG, "result = " + result);
					if (result != null && result.contains("OK")) {
						setRecordingButton(true);
					}
				}

			});
		}
	}

	// 暂停录像
	private void stopRecording() {
		if (RemoteCameraConnectManager.supportWebsocket()) {
			try {
				JSONObject jso = new JSONObject();
				jso.put("action", "set");
				JSONObject items = new JSONObject();
				items.put(Config.PROPERTY_CAMERA_RECORDING_STOP, "");
				jso.put("list", items);
				jso.toString();
				Log.i(TAG, "jso.toString() = " + jso.toString());
				HttpRequestManager.instance().requestWebSocket(jso.toString());
				// setRecordingButton(false);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":"
					+ RemoteCameraConnectManager.HTTP_SERVER_PORT
					+ "/cgi-bin/Config.cgi?action=set&property=Camera.Recording.Stop";
			Log.i(TAG, "url = " + url);
			HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener() {

				@Override
				public void onHttpResponse(String result) {
					Log.i(TAG, "result = " + result);
					if (result != null && result.contains("OK")) {
						setRecordingButton(false);
					}
				}

			});
		}
	}

	// 拍照
	private void takePhoto() {
		if (RemoteCameraConnectManager.supportWebsocket()) {
			try {
				JSONObject jso = new JSONObject();
				jso.put("action", "set");
				JSONObject items = new JSONObject();
				items.put(Config.PROPERTY_CAMERA_TAKE_PHOTO, "");
				jso.put("list", items);
				jso.toString();
				Log.i(TAG, "jso.toString() = " + jso.toString());
				HttpRequestManager.instance().requestWebSocket(jso.toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":"
					+ RemoteCameraConnectManager.HTTP_SERVER_PORT
					+ "/cgi-bin/Config.cgi?action=set&property=Camera.Take.Photo";
			Log.i(TAG, "url = " + url);
			HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener() {

				@Override
				public void onHttpResponse(String result) {
					Log.i(TAG, "result = " + result);
					if (result != null && result.contains("OK")) {
						Log.i(TAG, "take photo success");
					} else {
						Log.i(TAG, "take photo fail");
					}
				}

			});
		}
	}

	public void requestDVRSDcardStatus() {
		if (RemoteCameraConnectManager.supportWebsocket()) {
			try {
				JSONObject jso = new JSONObject();
				jso.put("action", "get");
				JSONArray items = new JSONArray();
				items.put(Config.PROPERTY_DVRSDCARD_STATUS_MOUNT);
				jso.put("list", items);
				jso.toString();
				Log.i(TAG, "jso.toString() = " + jso.toString());
				HttpRequestManager.instance().requestWebSocket(jso.toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":"
					+ RemoteCameraConnectManager.HTTP_SERVER_PORT
					+ "/cgi-bin/Config.cgi?action=get&property=Dvr.Sdcard.Status.Mount";
			Log.i(TAG, "url = " + url);
			HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener() {

				@Override
				public void onHttpResponse(String result) {
					Log.i(TAG, "result = " + result);
					if (result == null)
						return;
					String params[] = result.split("\n");
					for (String str : params) {
						try {
							if (str.startsWith(Config.PROPERTY_DVRSDCARD_STATUS_MOUNT)) {
								String mount = str.split("=")[1];
								setDVRSDcardStatus(Boolean.valueOf(mount));
							}
						} catch (Exception e) {
							Log.i(TAG, "Exception", e);
						}
					}
				}

			});
		}
	}

	public void requestDVRRecordStatus() {
		if (RemoteCameraConnectManager.supportWebsocket()) {
			try {
				JSONObject jso = new JSONObject();
				jso.put("action", "get");
				JSONArray items = new JSONArray();
				items.put(Config.PROPERTY_CAMERA_RECORDING_STATUS);
				jso.put("list", items);
				jso.toString();
				Log.i(TAG, "jso.toString() = " + jso.toString());
				HttpRequestManager.instance().requestWebSocket(jso.toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			String url = "http://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":"
					+ RemoteCameraConnectManager.HTTP_SERVER_PORT
					+ "/cgi-bin/Config.cgi?action=get&property=Camera.Recording.Status";
			Log.i(TAG, "url = " + url);
			HttpRequestManager.instance().requestHttp(url, new HttpRequestManager.OnHttpResponseListener() {

				@Override
				public void onHttpResponse(String result) {
					Log.i(TAG, "result = " + result);
					if (result == null)
						return;
					String params[] = result.split("\n");
					for (String str : params) {
						if (str.startsWith(Config.PROPERTY_CAMERA_RECORDING_STATUS)) {
							// "Camera.Recording.Status=true\n" or
							// "Camera.Recording.Status=false\n"
							try {
								setRecordingButton(Boolean.parseBoolean(str.split("=")[1]));
							} catch (Exception e) {
								Log.i(TAG, "Exception", e);
							}
						}
					}
				}

			});
		}
	}

	private void initView(Context context) {
		mContext = context;
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            inflater.inflate(R.layout.camera_view, this);
        }else{
            inflater.inflate(R.layout.camera_view_land, this);
        }

		mControlBar = findViewById(R.id.control_bar);

		mPreviewImage = (ImageView) findViewById(R.id.preview_image);
		mTextureView = (TextureView) findViewById(R.id.preview_surface);
		mTextureView.setSurfaceTextureListener(this);
		mEmptyView = findViewById(R.id.empty_preview_surface);

		mPreviwContainer = findViewById(R.id.preview_container);
		mPreviwContainer.setOnClickListener(this);
		mSwitchCameraView = (TextView) findViewById(R.id.switch_camera);
		mSwitchCameraView.setOnClickListener(this);
		mSwitchCameraView.setVisibility(GONE);
        mSwitchCameraView.setText(mCameraDir);
        
		mPreviwContainer.setOnTouchListener(this);
		mPreviewStart = (ImageView) findViewById(R.id.preview_start);
		mStatusText = (TextView) findViewById(R.id.status_camera);
		mSeekContainer = findViewById(R.id.camera_seek_container);
		mCameraSeekBar = (SeekBar) findViewById(R.id.camera_seekbar);
		mProgressBar = (ProgressBar) findViewById(R.id.preview_progressbar);

		mMiddleTextView = (TextView) findViewById(R.id.capture_short_video);
		refreshMiddleText();
		mMiddleTextView.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String str = null;
				
				if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
					Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
					return;
				}
				
				if(mMediaPlayer == null) {
					mPreviwContainer.performClick();
					return;
				}
				
				if (mSeekMode == SEEKMODE_PASTVIDEO_SEEK) {
					// resume playback mode
					mSeekMode = SEEKMODE_PASTVIDEO_PLAYBACK;
					str = "seekmode: " + mSeekMode + ",seekInumber:" +mMediaPlayer.getVideoShowNumber();
					refreshMiddleText();
					setCameraSeekMode(str);
				} else {
					// capture mode
					SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
					int time = 10;// Integer.parseInt(sp.getString(SettingView.KEY_CAPTURE_TIME, "10"));
					int seekoffset = 0 - mCaptureDuration;
                    int lastseemmode = mSeekMode;
					if (lastseemmode != SEEKMODE_REALTIME_PREVIEW) {
						seekoffset = (mMediaPlayer.getPastDurationFromLastPlayback() - mCaptureDuration);
					}
					mSeekMode = SEEKMODE_PASTVIDEO_SNAPSHOT;
					str = "seekmode: " + mSeekMode + ", seekoffset:" + seekoffset;
					Log.d(TAG, "mMediaPlayer.getCurrentPositionMS() " + mMediaPlayer.getPastDurationFromLastPlayback()
							+ " mCaptureDuration" + mCaptureDuration + " minus "
							+ (mMediaPlayer.getPastDurationFromLastPlayback() - mCaptureDuration));
					setCameraSeekMode(str);
                    if(lastseemmode == SEEKMODE_REALTIME_PREVIEW) {
                        mPreviewModeCapture = 1;
                    }
					mMediaCapture = new MediaCapture();
					// FIXME: hardcode the filename
					String filename = Config.CARDVR_CAPTURE_PATH + "/C"
							+ DateFormat.format("yyyyMMddHHmmss", new Date()).toString() + ".mp4";
                    File dirFile = new File(Config.CARDVR_CAPTURE_PATH);
                    if(!dirFile.exists())
                        dirFile.mkdirs();
                    
					String tmpName = filename + ".tmp";
					mMediaCapture.setCaptureListener(CameraView.this);
					mMediaCapture.startCapture(tmpName, filename, time*1000);
				}
                mSpeedIndex = 0;
                mBackwardIndex = 0;
				mSpeedTip.setVisibility(View.GONE);
			}
		});

		findViewById(R.id.camera_speed).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
					Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
					return;
				}
				
                if(mMediaPlayer == null) {
                	mPreviwContainer.performClick();
                    return;
                }
				if (mMyWebSocketClient != null && mMyWebSocketClient.isOpen()) {
					mBackwardIndex = 0;
                    int lastmode = mSeekMode;
					mSeekMode = SEEKMODE_PASTVIDEO_SEEK;
					String str = "seekmode: " + mSeekMode + ", seekspeed:" + SPEED[mSpeedIndex];
                    if(lastmode == SEEKMODE_PASTVIDEO_SEEK) {
                        //send the show frame number to device when last mode is also seek.
                        str += ",seekInumber:" +mMediaPlayer.getVideoShowNumber();
                    }
                    
					setCameraSeekMode(str);
					mSpeedTip.setText("X" + SPEED[mSpeedIndex]);
					mSpeedTip.setVisibility(View.VISIBLE);
					mSpeedIndex = (mSpeedIndex + 1) % SPEED.length;
					mMiddleTextView.setText(R.string.playback);
				}
			}

		});

		findViewById(R.id.camera_backward).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
					Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
					return;
				}
				
                if(mMediaPlayer == null) {
                	mPreviwContainer.performClick();
                    return;
                }
				if (mMyWebSocketClient != null && mMyWebSocketClient.isOpen()) {
					mSpeedIndex = 0;
                    int lastmode = mSeekMode;
					mSeekMode = SEEKMODE_PASTVIDEO_SEEK;
					String str = "seekmode: " + mSeekMode + ", seekspeed:"
							+ BACKWARD[mBackwardIndex];
                    if(lastmode == SEEKMODE_PASTVIDEO_SEEK) {
                        str += ",seekInumber:" +mMediaPlayer.getVideoShowNumber();
                    }
					setCameraSeekMode(str);
					mSpeedTip.setText("X" + BACKWARD[mBackwardIndex]);
					mSpeedTip.setVisibility(View.VISIBLE);
					mBackwardIndex = (mBackwardIndex + 1) % BACKWARD.length;
					mMiddleTextView.setText(R.string.playback);
				}
			}

		});
		
		findViewById(R.id.take_picture).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if(RemoteCameraConnectManager.getCurrentServerInfo() == null){
					Toast.makeText(getContext(), R.string.no_connect, Toast.LENGTH_SHORT).show();
					return;
				}
				takePhoto();
			}

		});

		mFullscreenBtn = findViewById(R.id.fullscreen);
		mExitFullscreenBtn = findViewById(R.id.exit_fullscreen);
		mFullscreenBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getContext(), CameraFullscreenActivity.class);
				getContext().startActivity(intent);
			}
		});
		
		mExitFullscreenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((CameraFullscreenActivity)getContext()).onBackPressed();                
            }
        });

		mSpeedTip = (TextView) findViewById(R.id.speed_tip);

		mWorkThread = new WorkThread("preview decode");
		mWorkThread.setDispatchMode(WorkThread.FIFO);
		mWorkThread.setReqListSize(5);
		mWorkThread.start();
	}

    private void startMediaPlayer()
    {
		synchronized (mLock) {
			if (mMediaPlayer != null) {
				mMediaPlayer.stop();
			}
			if(mSurfaceTexture == null)
				return;
			mMediaPlayer = new MediaPlayer(new Surface(mSurfaceTexture));
			mMediaPlayer.setDataSource(null); // default callback mode
			mMediaPlayer.setLocationListener(this); // default callback mode
			mMediaPlayer.start();
		}
    }
	public void startPreview() {
		String uri;
		try {
			if (mMyWebSocketClient != null) {
				mMyWebSocketClient.close();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
		if (RemoteCameraConnectManager.supportWebsocket()) {
			mPreviewImage.setVisibility(View.GONE);
            startMediaPlayer();
			uri = "ws://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":8129/webcam_preview_high";
		} else {
			mPreviewImage.setVisibility(View.VISIBLE);
			uri = "ws://" + RemoteCameraConnectManager.HTTP_SERVER_IP + ":8129/webcam_preview";
		}
		Log.i(TAG, "preview uri = " + uri);
		try {
			mMyWebSocketClient = new MyWebSocketClient(new URI(uri), RemoteCameraConnectManager.supportWebsocket());
			mMyWebSocketClient.connect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void stopPreview() {
		
		if (mMyWebSocketClient != null) {
			mMyWebSocketClient.close();
			mMyWebSocketClient = null;
		}

		if (RemoteCameraConnectManager.supportWebsocket()) {
			synchronized (mLock) {
    			if (mMediaCapture != null) {
    				mMediaCapture.stopCapture();
                    mPreviewModeCapture = 0;
    			}
                
				if (mMediaPlayer != null) {
					mMediaPlayer.stop();
					mMediaPlayer.setLocationListener(null);
                    mMediaPlayer = null;
				}
			}
		}
		mPreviewImage.setVisibility(View.VISIBLE);

	}

	private int setCameraSeekMode(String command) {
		String fullcommand = null;
		synchronized (mLock) {
			fullcommand = "{seekcookie:" + (++mSeekCookie) + "," + "appVerion:" + mAPPVersion + "," + command + "}";
			mWorkThread.cancelReqsList();
            if(mMediaPlayer != null) {
			    mMediaPlayer.flush();
			    if(mSeekMode == SEEKMODE_PASTVIDEO_SEEK) {
			    	mMediaPlayer.setFastMode(true);
			    } else {
			    	mMediaPlayer.setFastMode(false);
			    }
            }

            startMediaPlayer();

			if (mMediaCapture != null) {
				mMediaCapture.stopCapture();
                mPreviewModeCapture = 0;
			}
		}

		Log.d(TAG, "setCameraSeekMode WebSocket Send command " + fullcommand);
		if (mMyWebSocketClient != null && mMyWebSocketClient.isOpen()) {
			mMyWebSocketClient.send(fullcommand);
		}

		return 0;
	}

	private int dip2px(float dpValue) {
		final float scale = getResources().getDisplayMetrics().density;
		return (int) (dpValue * scale + 0.5f);
	}

	private static final int MSG_UPDATE_PREVIEW = 1000;
	private static final int MSG_SHOW_SD_UNMOUNT = 1001;
	private static final int MSG_DISMISS_SD_UNMOUNT = 1002;
	private static final int MSG_SHOW_PREVIEW_START = 1003;
	private static final int MSG_DISMISS_PREVIEW_START = 1004;
	private static final int MSG_DISMISS_SEEKBAR_CONTAINER = 1005;
	private static final int MSG_DISMISS_SPEED_TIP = 1006;
	private static final int MSG_HIDE_CONTROL_BAR = 1007;
	private static final int MSG_UPDATE_PREVIEW_STATUS = 1008;
    private static final int MSG_SWITCH_TO_PREVIEW = 1009;
	private static final int MSG_PROCESS_COMMENT = 1010;

	private static final int COMMENT_CANNOT_SWITCH_WHEN_LIVING = 1;


	private final MyHandler mHandler = new MyHandler(this);

	private int mPreviewModeCapture = 0;

	private static final class MyHandler extends Handler {
		private final WeakReference<CameraView> mThis;

		public MyHandler(CameraView cameraView) {
			mThis = new WeakReference<CameraView>(cameraView);
		}

		@Override
		public void handleMessage(Message msg) {
			CameraView This = mThis.get();
			if (This != null) {
				This.doHandleMessage(msg);
			}
		}
	}

	private void doHandleMessage(Message msg) {
		if (msg.what == MSG_UPDATE_PREVIEW) {
			mPreviewImage.setImageBitmap(mBitmap);
		} else if (msg.what == MSG_SHOW_SD_UNMOUNT) {
			// mNoSDView.setVisibility(View.VISIBLE);
		} else if (msg.what == MSG_DISMISS_SD_UNMOUNT) {
			// mNoSDView.setVisibility(View.INVISIBLE);
		} else if (msg.what == MSG_SHOW_PREVIEW_START) {
			if(RemoteCameraConnectManager.getCurrentServerInfo() != null){
				mPreviewStart.setVisibility(View.VISIBLE);
				mStatusText.setVisibility(View.VISIBLE);
				mPreviewImage.setImageResource(R.drawable.bg_connect);
			}else{
				mPreviewStart.setVisibility(View.INVISIBLE);
				mStatusText.setVisibility(View.GONE);
				mPreviewImage.setImageResource(R.drawable.no_connect);
			}
            mSwitchCameraView.setVisibility(GONE);
            mCameraNumber = 0;
		    mCameraDir = FRONT_CAMERA_STRING;
		    mCameraLists.clear();
		} else if (msg.what == MSG_DISMISS_PREVIEW_START) {
			mPreviewStart.setVisibility(View.INVISIBLE);
			mStatusText.setVisibility(View.GONE);
            if(mCameraNumber > 1) {
                mSwitchCameraView.setText(mCameraDir);
                mSwitchCameraView.setVisibility(VISIBLE);
            }
		} else if (msg.what == MSG_DISMISS_SEEKBAR_CONTAINER) {
			if (mSeekContainer.getVisibility() == View.VISIBLE) {
				mSeekContainer.setAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.alpha_dismiss));
				mSeekContainer.setVisibility(View.INVISIBLE);
			}
		} else if (msg.what == MSG_DISMISS_SPEED_TIP) {
			mSpeedTip.setVisibility(View.GONE);
		} else if (msg.what == MSG_HIDE_CONTROL_BAR) {
			hideControlBar();
		} else if (msg.what == MSG_UPDATE_PREVIEW_STATUS) {
			updatePreviewError(msg.arg1, msg.arg2);
		} else if(msg.what == MSG_SWITCH_TO_PREVIEW) {
			if(mPreviewModeCapture == 1) {
				mSeekMode = SEEKMODE_REALTIME_PREVIEW;
				String str = "seekmode:" + mSeekMode;
				setCameraSeekMode(str);
				Toast toast = Toast.makeText(mContext, mContext.getString(R.string.seek_switch_preview), Toast.LENGTH_LONG);
				toast.show();
				mPreviewModeCapture = 0;
			}
		} else if(msg.what == MSG_PROCESS_COMMENT) {
			processComment(msg.arg1);
		}
	}

	private void processComment(int arg1) {
		//TODO:
	}

	private void updatePreviewError(int arg1, int arg2) {
		String mode = null;
		String errorString = null;
		switch (arg1) {
		case SEEKMODE_REALTIME_PREVIEW:
			mode = mContext.getString(R.string.seek_switch_preview);
			break;
		case SEEKMODE_PASTVIDEO_PLAYBACK:
			mode = mContext.getString(R.string.seek_switch_playback);
			break;
		default:
			return;
		}
		refreshMiddleText();
		
		switch (arg2) {
		case SEEKERROR_NO_STORAGE:
			errorString = mContext.getString(R.string.seek_error_nostorage);
			break;
		case SEEKERROR_SEEK_FAIL:
			errorString = mContext.getString(R.string.seek_error_seek);
			break;
		case SEEKERROR_PLAYBACK_FAIL:
			errorString = mContext.getString(R.string.seek_error_playback);
			break;

		default:
			return;
		}

        if(mMediaPlayer != null) {
        	mSeekMode = arg1;
	    	mMediaPlayer.setFastMode(false);
        }

        if(mMediaCapture != null) {
            mMediaCapture.stopCapture();
            mPreviewModeCapture = 0;
        }

		Toast toast = Toast.makeText(mContext, errorString + "! " + mode, Toast.LENGTH_LONG);
		toast.show();
		
		mSpeedTip.setVisibility(View.GONE);

        //error happen, switch mode
        startMediaPlayer();
        String command = null;
        if(arg1 == SEEKMODE_REALTIME_PREVIEW) {
            // request preview data
    		command = "seekmode:" + SEEKMODE_REALTIME_PREVIEW + ",cameraDir:" + mCameraDir;
        } else {
			command = "seekmode:" + SEEKMODE_PASTVIDEO_PLAYBACK + ", seekpercent:" + 0;
        }        
        setCameraSeekMode(command);
	}

	class MyWebSocketClient extends WebSocketClient {

		boolean mPreviewHigh = false;

		public MyWebSocketClient(URI serverURI, boolean high) {
			super(serverURI);
			mPreviewHigh = high;
		}

		@Override
		public void onOpen(ServerHandshake handshakedata) {
			// TODO Auto-generated method stub
			Log.i(TAG, "onOpen");
			mBackwardIndex = 0;
			mSpeedIndex = 0;
			mCameraSeekBar.setProgress(mCameraSeekBar.getMax());
			mHandler.sendEmptyMessage(MSG_DISMISS_PREVIEW_START);
			mHandler.sendEmptyMessage(MSG_DISMISS_SPEED_TIP);

            switchCamera();
		}

		@Override
		public void onMessage(String message) {
			int seekmode = -1;
			int seekerror = -1;
            int width = 0;
			int height = 0;
			
			synchronized (mLock) {
    			Log.i(TAG, "onMessage:" + message);
    			String command = message.substring(4); //skip the first 4byte seek cookie
    			String[] config = command.toString().split(",");
    			for(int i = 0; i < config.length; i++) {
    				String[] valuepair = config[i].split(":");
    				if(valuepair[0].indexOf("seekmode") != -1) {
    					seekmode = Integer.parseInt(valuepair[1]);
    				} else if(valuepair[0].indexOf("seekError") != -1) {
    					seekerror = Integer.parseInt(valuepair[1]);
    				} else if(valuepair[0].indexOf("cameraNum") != -1) {
    					mCameraNumber = Integer.parseInt(valuepair[1]);
    					mCameraLists.clear();
    					mHandler.sendEmptyMessage(MSG_DISMISS_PREVIEW_START);
    				} else if(valuepair[0].indexOf("cameraDir") != -1) {
    					mCameraLists.add(valuepair[1]);
    				} else if(valuepair[0].indexOf("cameraWidth") != -1) {
    					width = Integer.parseInt(valuepair[1]);
    				} else if(valuepair[0].indexOf("cameraHeight") != -1) {
    					height = Integer.parseInt(valuepair[1]);
    				} else if(valuepair[0].indexOf("cameraCur") != -1) {
    					mCameraDir = valuepair[1];
    				} else if(valuepair[0].indexOf("cameraComment") != -1) {
    					int commentid = Integer.parseInt(valuepair[1]);
    	    			Message msgMessage = mHandler.obtainMessage(MSG_PROCESS_COMMENT, commentid, 0);
    	    			mHandler.sendMessage(msgMessage);
    				}
    			}
			}

            if((seekmode != -1) && (seekerror != -1)) {
    			Message msgMessage = mHandler.obtainMessage(MSG_UPDATE_PREVIEW_STATUS, seekmode, seekerror);
    			mHandler.sendMessage(msgMessage);
            }
            
			if((width != 0) && (height != 0)) {
            	synchronized (mLock) {
					if(mMediaPlayer != null) {
						mMediaPlayer.setVideoResolution(width, height);
					}
				}
            }
		}

		@Override
		public void onClose(int code, String reason, boolean remote) {
			Log.i(TAG, "onClose");
			mHandler.sendEmptyMessage(MSG_SHOW_PREVIEW_START);
		}

		@Override
		public void onError(Exception ex) {
			Log.i(TAG, "WebSocket onError: " + ex);
			mHandler.sendEmptyMessage(MSG_SHOW_PREVIEW_START);
		}

		@Override
		public void onMessage(final ByteBuffer bytes) {
			if (mPreviewHigh) {
				// don't buffer too much data
				synchronized (mLock) {
					int seekIndex = bytes.getInt();
					if (seekIndex != mSeekCookie) {
						Log.d(TAG, "skip this WebSocket Msg mSeekCookie = " + mSeekCookie + " receive seekIndex = "
								+ seekIndex);
						return;
					}
					try{
						int position = bytes.position();
						if (mMediaPlayer != null) {
							mMediaPlayer.writeRawData(bytes);
						}
	
						bytes.position(position);
						if (mMediaCapture != null) {
							mMediaCapture.WriteTSData(bytes);
						}
					}catch(OutOfMemoryError e){
						e.printStackTrace();
						mHandler.post(new Runnable() {
							
							@Override
							public void run() {
								mPreviwContainer.performClick();
							}
						});
					}
				}
			} else {
				mWorkThread.addReqWithLimit(new WorkReq() {

					@Override
					public void execute() {
						try{
							mBitmap = BitmapFactory.decodeByteArray(bytes.array(), 0, bytes.array().length);
						}catch(OutOfMemoryError e){
							e.printStackTrace();
							mHandler.post(new Runnable() {
								
								@Override
								public void run() {
									mPreviwContainer.performClick();
								}
							});
						}
						// mGLSurfaceView.requestRender();
						mHandler.removeMessages(MSG_UPDATE_PREVIEW);
						mHandler.sendEmptyMessage(MSG_UPDATE_PREVIEW);
					}

					@Override
					public void cancel() {

					}

				});
			}
		}

		@Override
		public void onFragment(Framedata frame) {

		}
	}

	@Override
	public int onCaptureFinish() {
		if (mMediaCapture != null) {
			mMediaCapture.stopCapture();
			mMediaCapture = null;
		}

        if(mPreviewModeCapture  == 1) {
        	mHandler.sendEmptyMessageDelayed(MSG_SWITCH_TO_PREVIEW, mCaptureDuration);
        }

		mHandler.post(new Runnable() {
			@Override
			public void run() {
			    if((mSeekMode == SEEKMODE_PASTVIDEO_PLAYBACK) || (mSeekMode == SEEKMODE_PASTVIDEO_SNAPSHOT)){
				    refreshMiddleText();
                }
				Toast.makeText(mContext, getContext().getResources().getText(R.string.capture_complete),
						Toast.LENGTH_SHORT).show();
				if (mIsFullscreenMode) {
					mHandler.removeMessages(MSG_HIDE_CONTROL_BAR);
					mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROL_BAR, DELAY_HIDE_CONTROL_BAR);
				}

			}
		});

		return 0;
	}

	@Override
	public int onCaptureProcess(int leftMS) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
		int total = 10; //Integer.parseInt(sp.getString(SettingView.KEY_CAPTURE_TIME, "10"));
		final int time = (total - mCaptureDuration/1000) - leftMS/1000;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mMiddleTextView.setText("" + time + "S");
				if (mIsFullscreenMode) {
					mHandler.removeMessages(MSG_HIDE_CONTROL_BAR);
					mControlBar.setVisibility(View.VISIBLE);
				}
			}
		});

		return 0;
	}

	@Override
	public int onVideoLocationChange(GPSData data) {
		// TODO Auto-generated method stub
		if(mQuickTrackFragment != null)
			mQuickTrackFragment.setVideoLocation(data);
		return 0;
	}

	public void setFullscreenMode() {
		mIsFullscreenMode = true;

		ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mPreviwContainer.getLayoutParams();
		lp.bottomMargin = 0;
		mPreviwContainer.setLayoutParams(lp);
		mFullscreenBtn.setVisibility(View.GONE);
		mExitFullscreenBtn.setVisibility(View.VISIBLE);
		
		mControlBar.setBackgroundColor(getResources().getColor(R.color.cameraview_controlbar_background_translucent));
	}

	public void hideControlBar() {
	    
	    int flag = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
	    
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
	        flag |= View.SYSTEM_UI_FLAG_IMMERSIVE;
	    }
		((Activity)mContext)
				.getWindow()
				.getDecorView()
				.setSystemUiVisibility(flag);

		mControlBar.setVisibility(View.GONE);
		mHandler.removeMessages(MSG_HIDE_CONTROL_BAR);
	}

	public void showControlBar() {
		if (mIsFullscreenMode) {		    
		    ((Activity)mContext)
            .getWindow()
            .getDecorView()
            .setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		}
		mControlBar.bringToFront();
		mControlBar.setVisibility(View.VISIBLE);

		mHandler.removeMessages(MSG_HIDE_CONTROL_BAR);
		mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROL_BAR, DELAY_HIDE_CONTROL_BAR);
	}

	@Override
	public int onVideoLocationDataBuffer(ByteBuffer buffer) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isPlayButtonVisibile() {
		if (mStatusText.getVisibility() == View.VISIBLE &&
				mStatusText.getText().toString().equals(getResources().getString(R.string.tip_no_connect))) {
			return true;
		}

		return false;
	}
}
