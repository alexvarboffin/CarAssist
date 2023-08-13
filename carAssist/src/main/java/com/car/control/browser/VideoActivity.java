package com.car.control.browser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.car.common.map.MapTrackView;
import com.car.control.Config;
import com.car.control.R;
import com.media.tool.GPSData;
import com.media.tool.MediaPlayer;
import com.media.tool.MediaPlayer.OnBufferListener;
import com.media.tool.MediaPlayer.OnInfoListener;
import com.media.tool.MediaPlayer.onVideoLocationListener;
import com.media.tool.MediaProcess;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoActivity extends FragmentActivity implements OnSeekBarChangeListener, OnTouchListener, SurfaceTextureListener,
        OnInfoListener, OnBufferListener, onVideoLocationListener, MapTrackView.MapListener {

    private static final String TAG = "CarSvc_VideoActivity";
    public static final String KEY_FILE_TIME = "key_file_time";
    public static final String KEY_FILE_NAME = "key_file_name";
    public static final String KEY_LIVING_FLAG = "key_living";
    public static final String KEY_LIVING_SN = "key_living_sn";
    public static final String KEY_LIVING_JSON = "key_living_json";

    private static final int START_LIVING = 1;
    private static final int STOP_LIVING = 0;
    public static final int CAMERA_FRONT = 0;

    private Uri mIntentUri;
    private TextView mDuration;
    private TextView mTime;
    private SeekBar mPlayerSeekBar;
    private ImageView mStartPlayer;
    ProgressBar mProgressBar;
    TextView mTipPrompt;
    boolean mIsPlaying = false;

    private Toast mToast;
    private TextView mToastView;
    private Drawable mBrightnessIcon;
    private Drawable mVolumnIcon;
    private Drawable mSeekIconBackward;
    private Drawable mSeekIconforward;
    private Point mTouchStart = new Point();
    private AudioManager mAudioManager;
    private int mMaxVolume;
    private int mCurrentVolume;
    private View mBottomView;
    //private ProgressDialog mProgressDialog;
    private View mMapContainer;
    private MapTrackView mMapTrackView;

    private MediaPlayer mMediaPlayer;
    private TextureView mTextureView;
    private RelativeLayout mVideoPreviewContainer;
    private boolean mRemoteFile = false;
    private boolean mLiving = false;
    private String mActionbarTitle = "";
    private ImageView mStartImage;
    private ImageView mFullScreen;
    private Map<Integer, GPSData> mGPSDataMap;
    private int mFirstGPSDataTime;

    private boolean mIsVideoFullScreenMode = false;
    private int mMarginBottom = 0;
    private Surface mSurface;

    private View mVideoMainLayout, mVideoShareLayout;
    private String mSerialNum = null;
    private AliyunOSSDataSource mAliyunOSSDataSource = null;
    private String mAliyunOSSBufferFileName = Config.CARDVR_PATH + "/live_streaimg_file";
    boolean mPauseFromBack = false;
    ArrayList<GPSData> mGPSDataList = new ArrayList<GPSData>();
    private ImageView mSaveImage;
    ImageView mVoiceVolume;
    private int mLivingRetryCount = 0;    //try about 5 seconds
    RelativeLayout mVolume_container;

    private int mCameraNumber = 0;
    private int mCameraDir = 'F';
    private List<String> mCameraLists = new ArrayList<String>(4);
    private TextView mSwitchButton;
    private JSONObject mIntentJson;

    private String getHumanTime(long sendTime) {
        String formatTimeString;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatTimeString = formatter.format(new Date(sendTime));
        return formatTimeString;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(getResources().getColor(R.color.photo_color));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mVolume_container = (RelativeLayout) findViewById(R.id.volume_container);
        mVolume_container.setVisibility(View.INVISIBLE);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar1);
        mProgressBar.setVisibility(View.INVISIBLE);
        mTipPrompt = (TextView) findViewById(R.id.tip_prompt);
        mTipPrompt.setVisibility(View.INVISIBLE);

        mVideoMainLayout = findViewById(R.id.video_main_layout);
        mVideoShareLayout = findViewById(R.id.video_share_layout);

        mBottomView = findViewById(R.id.video_bar_bottom);
        mDuration = (TextView) findViewById(R.id.video_duration);
        mTime = (TextView) findViewById(R.id.video_time);
        mPlayerSeekBar = (SeekBar) findViewById(R.id.player_seekbar);
        mPlayerSeekBar.setOnSeekBarChangeListener(this);
        mStartPlayer = (ImageView) findViewById(R.id.video_play);
        mStartPlayer.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!mLiving)
                    doPauseResume();
                if (mIsVideoFullScreenMode) {
                    mHandler.removeMessages(MSG_HIDE_CONTROL_BOTTOMBAR);
                    mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROL_BOTTOMBAR, 6000);
                }
            }

        });
        mStartImage = (ImageView) findViewById(R.id.video_activity_start);
        mStartImage.setOnClickListener(v -> {
            if (!mLiving)
                doPauseResume();
        });
        mStartImage.setVisibility(View.INVISIBLE);

        mSaveImage = (ImageView) findViewById(R.id.video_save);
        mSaveImage.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(VideoActivity.this);
            builder.setTitle(R.string.video_save);
            builder.setMessage(R.string.video_view);
            builder.setPositiveButton(R.string.ok, (dialog, which) -> {
                storeLiving();
                finish();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.create().show();
        });

        mVoiceVolume = (ImageView) findViewById(R.id.voice_vol);

        mTextureView = (TextureView) findViewById(R.id.video_textureview);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOnTouchListener(this);

        mVideoPreviewContainer = (RelativeLayout) findViewById(R.id.video_preview_container);

        mFullScreen = (ImageView) findViewById(R.id.video_activity_fullscreen);
        mFullScreen.setOnClickListener(v -> {
            mStartImage.setVisibility(View.GONE);
            mHandler.removeMessages(MSG_HIDE_CONTROL_BOTTOMBAR);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                fullScreen();
            else
                exitFullScreen();
        });
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mFullScreen.setImageResource(R.drawable.fullscreen_black);
        } else {
            mFullScreen.setImageResource(R.drawable.small_screen_black);
        }

        mToastView = (TextView) getLayoutInflater().inflate(R.layout.toast_note, (ViewGroup) mTextureView.getParent(),
                false);
        mBrightnessIcon = this.getResources().getDrawable(R.drawable.icon_toast_brightness);
        mVolumnIcon = this.getResources().getDrawable(R.drawable.icon_toast_volume);
        mSeekIconBackward = this.getResources().getDrawable(R.drawable.icon_toast_seekbackward);
        mSeekIconforward = this.getResources().getDrawable(R.drawable.icon_toast_seekforward);
        mBrightnessIcon.setBounds(0, 0, mBrightnessIcon.getIntrinsicWidth(), mBrightnessIcon.getIntrinsicHeight());
        mVolumnIcon.setBounds(0, 0, mBrightnessIcon.getIntrinsicWidth(), mBrightnessIcon.getIntrinsicHeight());
        mSeekIconBackward.setBounds(0, 0, mBrightnessIcon.getIntrinsicWidth(), mBrightnessIcon.getIntrinsicHeight());
        mSeekIconforward.setBounds(0, 0, mBrightnessIcon.getIntrinsicWidth(), mBrightnessIcon.getIntrinsicHeight());

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100;
        mMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 100;

        mMapContainer = findViewById(R.id.video_map_container);
        mMapTrackView = MapTrackView.create(this);
        mMapTrackView.onCreate(savedInstanceState);
        mMapTrackView.setMapListener(this);
        RelativeLayout mapParent = (RelativeLayout) findViewById(R.id.tarck_map_parent_view);
        mapParent.addView(mMapTrackView);
        mSwitchButton = (TextView) findViewById(R.id.switch_camera);
        mSwitchButton.setVisibility(View.GONE);
        mSwitchButton.setOnClickListener(v -> switchLivingCamera());

        handleIntent(getIntent());

        ActionBar a = getActionBar();
        if (a != null) {
            a.setBackgroundDrawable(getResources().getDrawable(R.drawable.action_bar));
            String title = mActionbarTitle;
            if (mLiving)
                title = getResources().getString(R.string.monitor_live_preview);
            setActionBarMidtitleAndUpIndicator(title, R.drawable.back);
        }

        if (mLiving) {
            mMapTrackView.setShowCarInfoTime(false);
            mStartImage.setVisibility(View.INVISIBLE);
            mStartPlayer.setVisibility(View.INVISIBLE);
            mPlayerSeekBar.setVisibility(View.INVISIBLE);
            mTime.setVisibility(View.INVISIBLE);
            mSaveImage.setVisibility(View.VISIBLE);
            mSaveImage.setEnabled(false);
            mVoiceVolume.setImageResource(R.drawable.v1);
            mProgressBar.setVisibility(View.VISIBLE);
        }

        if (mIntentUri.toString().startsWith("http")) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    mProgressBar.setVisibility(View.VISIBLE);
                    mTipPrompt.setText(R.string.wait_for_buffer);
                    mTipPrompt.setVisibility(View.VISIBLE);
                }
            });

        }

        mMapContainer = findViewById(R.id.video_map_container);

        mVoiceToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mVoiceToast.setDuration(Toast.LENGTH_SHORT);
        mVoiceToast.setGravity(Gravity.TOP, 0, 100);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPauseFromBack = false;
        mMapTrackView.onResume();
        mMapTrackView.setLocationEnabled(true);
        if (mMediaPlayer != null) {
            mHandler.removeMessages(MSG_PROGRESS);
            mHandler.sendEmptyMessage(MSG_PROGRESS);
            mMediaPlayer.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapTrackView.onDestroy();
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer = null;
            mGPSDataMap = null;
        }

        stopLiving();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapTrackView.onPause();
        mMapTrackView.setLocationEnabled(false);
        if (mMediaPlayer != null) {
            mHandler.removeMessages(MSG_PROGRESS);
            mMediaPlayer.pause();
        }

        if (mLiving) {
            if (!mPauseFromBack && mIsPlaying) {
                //save default
                storeLiving();
                Toast.makeText(this, getResources().getString(R.string.video_save_default), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "onNewIntent");
        super.onNewIntent(intent);
        handleIntent(intent);
        if (mIntentUri != null && mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer = null;
            mGPSDataMap = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mRemoteFile) {
            getMenuInflater().inflate(R.menu.video_activity_remote, menu);
        } else {
            if (mVideoShareLayout.getVisibility() == View.VISIBLE) {
                getMenuInflater().inflate(R.menu.video_activity_remote, menu);
            } else {
                getMenuInflater().inflate(R.menu.video_activity_local, menu);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }

    void sendLiveHeartbeat() {
        //FIXME: Send Living heart packet
        mHandler.sendEmptyMessageDelayed(MSG_LIVE_HEARTBEAT, 10 * 1000);
    }

    private void startLiving(JSONObject jso) {
        int ret = -1;
        String accessKey = null, secretKey = null, endpoint = null, bucket = null, streamingFile = null;
        try {
            ret = jso.getInt("ret");
            accessKey = jso.getString("access");
            secretKey = jso.getString("secret");
            endpoint = jso.getString("ep");
            bucket = jso.getString("bk");
            streamingFile = jso.getString("sf");

        } catch (JSONException e1) {
            e1.printStackTrace();
        }

        String cameralist = jso.optString("cl", null);
        if (cameralist != null) {
            Log.d(TAG, "mCameraDir = " + mCameraDir + " cameralist = " + cameralist);
            parseLivingCameraList(cameralist);
        }

        if (ret == 0) {
            if (mProgressBar.getVisibility() == View.VISIBLE) {
                mTipPrompt.setText(R.string.video_ok);
                mTipPrompt.setVisibility(View.VISIBLE);
            }
            startMediaPlayer();
            mAliyunOSSDataSource = new AliyunOSSDataSource(getApplicationContext(), accessKey, secretKey, endpoint, bucket, streamingFile);
            mAliyunOSSDataSource.setMediaPlayer(mMediaPlayer);
            mAliyunOSSDataSource.setBufferFilename(mAliyunOSSBufferFileName);
            mAliyunOSSDataSource.start();
            sendLiveHeartbeat();
            mHandler.removeMessages(MSG_LIVE_TIMEOUT);
            mHandler.sendEmptyMessageDelayed(MSG_LIVE_TIMEOUT, 200 * 1000);
            mGPSDataList.clear();
            mLastStreamSize = -1;
            mLastDuration = -1;
        }
    }

    private void startLiving() {
        if (mSerialNum == null) {
            Log.e(TAG, "mSerialNum is null, error...");
            return;
        }

        if (mProgressBar.getVisibility() == View.VISIBLE) {
            mTipPrompt.setText(R.string.video_request);
            mTipPrompt.setVisibility(View.VISIBLE);
        }

        //FIXME: send start living command to device side
        mHandler.sendEmptyMessageDelayed(MSG_REQUEST_TIMEOUT, 10000);
        if (mIntentJson != null) {
            startLiving(mIntentJson);
        }
        Log.d(TAG, "Send start Living command to device, and wait..., count=" + mLivingRetryCount + " mCameraDir = " + mCameraDir);
    }

    protected void parseLivingCameraList(String list) {
        mCameraLists.clear();
        for (int i = 0; i < list.length(); i++) {
            mCameraLists.add(list.substring(i, i + 1));
        }

        Log.d(TAG, "mCameraLists.size() = " + mCameraLists.size());
        if (mCameraLists.size() > 1) {
            mSwitchButton.setText(String.format("%c", mCameraDir));
            mSwitchButton.setVisibility(View.VISIBLE);
        } else {
            mSwitchButton.setVisibility(View.GONE);
        }
    }

    private void switchLivingCamera() {
        stopLiving();

        int i = 0;
        int number = mCameraLists.size();
        for (i = 0; i < number; i++) {
            if (mCameraDir == (mCameraLists.get(i).charAt(0))) {
                i = (i + 1) % number;
                mCameraDir = mCameraLists.get(i).charAt(0);
                break;
            }
        }

        startLiving();
    }

    private void stopLiving() {
        mIsPlaying = false;
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer = null;
            mGPSDataMap = null;
        }

        if (mAliyunOSSDataSource != null) {
            mAliyunOSSDataSource.stop();
            mAliyunOSSDataSource = null;
            //FIXME: send stop living command to device side
            mHandler.removeMessages(MSG_LIVE_HEARTBEAT);
            mHandler.removeMessages(MSG_LIVE_TIMEOUT);
            mHandler.removeMessages(MSG_STREAM_CHECK);
            mGPSDataList.clear();
            Log.d(TAG, "Send stop Living command to device");
        }
        mSaveImage.setEnabled(false);
        mProgressBar.setVisibility(View.INVISIBLE);
        mTipPrompt.setVisibility(View.INVISIBLE);

        mHandler.removeMessages(MSG_REQUEST_TIMEOUT);
        mHandler.removeMessages(MSG_BUFFER_TIMEOUT);
        mHandler.removeMessages(MSG_START_LIVE);
        //onRecordState(VoiceRecordImage.STATE_RECORD_FINISH);
    }

    private void storeLiving() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                String saveFile = Config.CARDVR_CAPTURE_PATH + "/livestream-" + getHumanTime(System.currentTimeMillis()) + ".mp4";
                if (mLiving) {
                    MediaProcess mp = new MediaProcess(MediaProcess.CONVERT);
                    mp.setInputFile(mAliyunOSSBufferFileName);
                    mp.setOutFile(saveFile);
                    //mp.setListener(this);
                    //native block here
                    mp.start();

                    mp.destroy();
                    mp = null;
                }
            }
        }).start();
    }

    private void startMediaPlayer() {
        try {
            mMediaPlayer = new MediaPlayer(mSurface);
            mMediaPlayer.setInfoListener(this);
            mMediaPlayer.setBufferingListener(this);
            mMediaPlayer.setLocationListener(this);
            if (mLiving) {
                mMediaPlayer.setLiveStreamingFlag();
                mMediaPlayer.setDataSource("living://" + mSerialNum);
            } else {
                mMediaPlayer.setDataSource(mIntentUri.toString());
            }
            mMediaPlayer.start();
            mHandler.removeMessages(MSG_PROGRESS);
            mHandler.sendEmptyMessage(MSG_PROGRESS);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable");

        mSurface = new Surface(surface);
        if (mLiving) {
            mLivingRetryCount = 0;
            startLiving();
        } else {
            startMediaPlayer();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed");
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.setLocationListener(null);
            mMediaPlayer = null;
        }

        stopLiving();
        mSurface = null;
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureSizeChanged to width = " + width + " height = " + height);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //Log.i(TAG, "onSurfaceTextureUpdated");

        mHandler.removeMessages(MSG_BUFFER_TIMEOUT);
        mHandler.removeMessages(MSG_BUFFERING_START);
        if (mProgressBar.getVisibility() == View.VISIBLE) {
            mProgressBar.setVisibility(View.INVISIBLE);
            mTipPrompt.setVisibility(View.INVISIBLE);
        }
    }

    private void handleIntent(Intent intent) {
        mIntentUri = intent.getData();
        Log.i(TAG, "mIntentUri = " + mIntentUri);
        if (mIntentUri == null) {
            finish();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        if (mIntentUri.getScheme().compareTo("file") == 0) {
            mRemoteFile = false;
            String path = mIntentUri.toString().replace("file://", "");
            File file = new File(path);
            mActionbarTitle = Util.name2DateString(file.getName());
            if (mActionbarTitle == null)
                mActionbarTitle = sdf.format(new Date(file.lastModified()));
        } else {
            mRemoteFile = true;
            String name = intent.getStringExtra(KEY_FILE_NAME);
            if (name != null)
                mActionbarTitle = Util.name2DateString(name);
            if (mActionbarTitle == null) {
                long time = intent.getLongExtra(KEY_FILE_TIME, 0);
                if (time > 0) {
                    mActionbarTitle = sdf.format(new Date(intent.getLongExtra(KEY_FILE_TIME, 0)));
                } else if (name != null) {
                    mActionbarTitle = name;
                }
            }
            if (name != null) {
                name = name.replace(".ts", ".gps");
                String path = "/~cache/" + name;
                Log.i(TAG, "gps url path = " + path);
                mMapTrackView.drawRemoteTrackLine(path);
            }

            mLiving = intent.getBooleanExtra(KEY_LIVING_FLAG, false);
            mSerialNum = intent.getStringExtra(KEY_LIVING_SN);
            String livingJson = intent.getStringExtra(KEY_LIVING_JSON);
            if (livingJson != null) {
                try {
                    mIntentJson = new JSONObject(livingJson);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        invalidateOptionsMenu();
    }

    private String stringForTime(long millis) {
        int totalSeconds = (int) millis / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return String.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private void updateProgress() {
        if (mMediaPlayer != null) {
            if (!mLiving) {
                int postion = mMediaPlayer.getCurrentPosition();
                int duration = mMediaPlayer.getDuration();
                if ((duration != 0) && ((duration - postion) < 500)) {
                    mPlaybackCompleted = 1;
                } else {
                    mPlaybackCompleted = 0;
                }
                mTime.setText(stringForTime(postion));
                mPlayerSeekBar.setProgress(postion);
                updatePausePlay();
                GPSData data = findBestPointGPSData();
                if (data != null) {
                    drawVideoLocation(data);
                }
            } else {
                updatePausePlay();
                mDuration.setText(stringForTime(mMediaPlayer.getPastDurationFromLastPlayback()));
            }
        }
    }

    private GPSData findBestPointGPSData() {
        if (mGPSDataMap == null)
            return null;
        int postion = mMediaPlayer.getCurrentPosition();
        int time = postion / 1000;
        return mGPSDataMap.get(mFirstGPSDataTime + time);
    }

    private void doPauseResume() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                if (mPlaybackCompleted == 1) {
                    mMediaPlayer.seekTo(0);
                    mPlaybackCompleted = 0;
                    mHandler.removeMessages(MSG_PROGRESS);
                    mHandler.sendEmptyMessage(MSG_PROGRESS);
                } else {
                    mMediaPlayer.pause();
                    updatePausePlay();
                }
            } else {
                mMediaPlayer.resume();
                updatePausePlay();
            }
        }
    }

    private void updatePausePlay() {
        if (mLiving) return;
        if (mMediaPlayer.isPlaying() && (mPlaybackCompleted == 0)) {
            mStartPlayer.setImageResource(R.drawable.btn_pause);
            mStartImage.setVisibility(View.GONE);
        } else {
            mStartPlayer.setImageResource(R.drawable.btn_play);
            mStartImage.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
            mTipPrompt.setVisibility(View.INVISIBLE);
        }
    }

    private void showTouchMsg(Drawable icon, String title) {
        if (mToast == null) {
            mToast = new Toast(this);
            mToast.setView(mToastView);
            mToast.setDuration(Toast.LENGTH_SHORT);
            mToast.setGravity(Gravity.CENTER, 0, 0);
        }
        mToastView.setCompoundDrawables(icon, null, null, null);
        mToastView.setText(title);
        mToast.show();
    }

    private int dip2px(float dpValue) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    protected static final int MSG_PROGRESS = 0x110;
    protected static final int MSG_DURATION = 0x111;
    protected static final int MSG_BUFFERING_START = 0x112;
    protected static final int MSG_BUFFERING_END = 0x113;
    protected static final int MSG_HIDE_CONTROL_BOTTOMBAR = 0x114;
    protected static final int MSG_LIVE_HEARTBEAT = 0x115;
    protected static final int MSG_LIVE_TIMEOUT = 0x116;
    protected static final int MSG_STREAM_CHECK = 0x117;
    protected static final int MSG_VOICE_HANDLING = 0x118;
    protected static final int MSG_VOICE_VOL = 0x119;
    protected static final int MSG_VOICE_RECORD = 0x11a;
    protected static final int MSG_REQUEST_TIMEOUT = 0x11b;
    protected static final int MSG_BUFFER_TIMEOUT = 0x11c;
    protected static final int MSG_START_LIVE = 0x11d;

    private int mPlaybackCompleted = 0;
    long mLastStreamSize = 0;
    long mLastDuration = 0;

    static int[] sVolDrawables = {
            R.drawable.v1,
            R.drawable.v2,
            R.drawable.v3,
            R.drawable.v4,
            R.drawable.v5,
            R.drawable.v6,
            R.drawable.v7,
    };

    String mOSSAccessKeyID = "LTAIKe1Jrhpitzc4";
    String mOSSAccessKeySecret = "aEhBl5nkj0CvY4Wz1c5XalOa5Xpi9Q";
    private static final float BASE_NUMBER = 32768;


    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_BUFFER_TIMEOUT: {
                    Toast.makeText(VideoActivity.this, R.string.video_buffer_failed, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                case MSG_REQUEST_TIMEOUT: {
                    if (mLivingRetryCount < 6) {
                        //retry after 2 seconds, try 3 times about half a minute
                        mHandler.removeMessages(MSG_REQUEST_TIMEOUT);
                        mHandler.removeMessages(MSG_START_LIVE);
                        mHandler.sendEmptyMessageDelayed(MSG_START_LIVE, 2000);
                        mLivingRetryCount += 2;
                    } else {
                        Toast.makeText(VideoActivity.this, R.string.video_failed, Toast.LENGTH_LONG).show();
                        finish();
                    }
                    return;
                }
                case MSG_VOICE_RECORD: {
                    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    long[] pattern = {0, 100};
                    vibrator.vibrate(pattern, -1);
                    mIsRecording = true;
                    mLastVoiceFile = Config.CARDVR_PATH + "/myvoice-" + Build.SERIAL + getHumanTime(System.currentTimeMillis()) + ".aac";
                    doVoiceRecord2(mLastVoiceFile);
                    mVolume_container.setVisibility(View.VISIBLE);
                    //mVoiceVolume.setVisibility(View.VISIBLE);
                    mHandler.sendEmptyMessage(MSG_VOICE_VOL);
                    mVoiceToast.setText(getResources().getString(R.string.voice_cancel));
                    mVoiceToast.show();
                }
                break;
                case MSG_VOICE_VOL: {
                    if (mVolume_container.getVisibility() == View.VISIBLE && mIsRecording && mMediaRecorder != null) {
                        int step = 0;
                        step = (int) ((float) 7 * mMediaRecorder.getMaxAmplitude() / BASE_NUMBER);
                        if (step >= 6) step = 6;
                        else if (step <= 0) step = 0;

                        mVoiceVolume.setImageResource(sVolDrawables[step]);
                        this.sendEmptyMessageDelayed(MSG_VOICE_VOL, 100);
                    }
                }
                break;
                case MSG_VOICE_HANDLING: {
                }
                break;
                case MSG_STREAM_CHECK: {
                    if (mAliyunOSSDataSource != null) {
                        this.sendEmptyMessageDelayed(MSG_STREAM_CHECK, 15 * 1000);
                        long curSize = mAliyunOSSDataSource.getFileSize();
                        long duration = mMediaPlayer.getPastDurationFromLastPlayback();
                        if (curSize == mLastStreamSize && duration == mLastDuration) {
                            Log.d(TAG, "File size in server not changed, stop living now");
                            AlertDialog.Builder builder = new AlertDialog.Builder(VideoActivity.this);
                            builder.setTitle(R.string.live_size);
                            builder.setMessage(R.string.video_view);
                            builder.setPositiveButton(R.string.ok, new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    storeLiving();
                                    onBackPressed();
                                }
                            });
                            builder.setNegativeButton(R.string.cancel, new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    onBackPressed();
                                }
                            });
                            builder.create().show();
                            stopLiving();
                        } else {
                            mLastStreamSize = curSize;
                            mLastDuration = duration;
                        }
                    }
                }
                break;
                case MSG_PROGRESS:
                    updateProgress();
                    this.sendEmptyMessageDelayed(MSG_PROGRESS, 1000);
                    break;
                case MSG_DURATION:
                    mPlayerSeekBar.setMax(msg.arg1);
                    mDuration.setText(stringForTime(msg.arg1));
                    break;
                case MSG_BUFFERING_START:
                    if (mIntentUri.toString().startsWith("http")) {
                        if (mMediaPlayer != null && mMediaPlayer.isEOF() != 1) {
                            mProgressBar.setVisibility(View.VISIBLE);
                            mTipPrompt.setVisibility(View.VISIBLE);
                        }
                    } else if (mLiving) {
                        int percent = msg.arg1;
                        mProgressBar.setVisibility(View.VISIBLE);
                        mTipPrompt.setText(getResources().getString(R.string.video_buffering) + " " + percent + "%");
                        mTipPrompt.setVisibility(View.VISIBLE);
                        if (!mIsPlaying && mAliyunOSSDataSource != null) {
                            if (!this.hasMessages(MSG_BUFFER_TIMEOUT))
                                this.sendEmptyMessageDelayed(MSG_BUFFER_TIMEOUT, 15 * 1000);
                        }
                    }
                    break;
                case MSG_BUFFERING_END:
                    if (mIntentUri.toString().startsWith("http")) {
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mTipPrompt.setVisibility(View.INVISIBLE);
                    } else if (mLiving) {
                        mIsPlaying = true;
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mTipPrompt.setVisibility(View.INVISIBLE);
                        mSaveImage.setEnabled(true);
                        this.removeMessages(MSG_STREAM_CHECK);
                        this.sendEmptyMessageDelayed(MSG_STREAM_CHECK, 15 * 1000);
                        this.removeMessages(MSG_BUFFER_TIMEOUT);
                    }
                    break;
                case MSG_HIDE_CONTROL_BOTTOMBAR:
                    hideControlBar();
                    break;
                case MSG_LIVE_HEARTBEAT:
                    sendLiveHeartbeat();
                    break;
                case MSG_LIVE_TIMEOUT: {
                    AlertDialog.Builder builder = new AlertDialog.Builder(VideoActivity.this);
                    builder.setTitle(R.string.live_timeout);
                    builder.setMessage(R.string.video_view);
                    builder.setPositiveButton(R.string.ok, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            storeLiving();
                            onBackPressed();
                        }
                    });
                    builder.setNegativeButton(R.string.cancel, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onBackPressed();
                        }
                    });
                    builder.create().show();
                    stopLiving();
                }
                break;
                case MSG_START_LIVE:
                    startLiving();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int progress = seekBar.getProgress();
        if (mMediaPlayer != null) {
            mHandler.removeMessages(MSG_PROGRESS);
            mHandler.sendEmptyMessageDelayed(MSG_PROGRESS, 3000);
            mMediaPlayer.seekTo(progress);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
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

    MODE mMode;

    enum MODE {
        NUL, BRIGHTNESS, VOLUMN, SEEK
    }

    ;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int rawX = (int) event.getRawX();
        int rawY = (int) event.getRawY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchStart.x = rawX;
                mTouchStart.y = rawY;
                mMode = MODE.NUL;
                break;
            case MotionEvent.ACTION_MOVE:
                int xAxisOffset = rawX - mTouchStart.x;
                int yAxisOffset = rawY - mTouchStart.y;
                if (mMode == MODE.NUL) {
                    if (Math.abs(xAxisOffset) > dip2px(20)) {
                        mMode = MODE.SEEK;
                    } else if (Math.abs(yAxisOffset) > dip2px(20)) {
                        if (mTouchStart.x < v.getWidth() / 2)
                            mMode = MODE.BRIGHTNESS;
                        else
                            mMode = MODE.VOLUMN;
                    }
                } else if (Math.abs(xAxisOffset) > 0 || Math.abs(yAxisOffset) > 0) {
                    if (mMode == MODE.BRIGHTNESS) {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        float value = lp.screenBrightness * 100f;
                        value += yAxisOffset < 0 ? 1 : -1;
                        if (value > 100)
                            value = 100;
                        else if (value < 0)
                            value = 0;

                        lp.screenBrightness = value / 100;
                        getWindow().setAttributes(lp);
                        showTouchMsg(mBrightnessIcon, String.valueOf((int) value));
                    } else if (mMode == MODE.VOLUMN) {

                        int per = mMaxVolume / 45;
                        mCurrentVolume += yAxisOffset < 0 ? per : -per;
                        if (mCurrentVolume > mMaxVolume)
                            mCurrentVolume = mMaxVolume;
                        else if (mCurrentVolume < 0)
                            mCurrentVolume = 0;

                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mCurrentVolume / 100, 0);
                        showTouchMsg(mVolumnIcon, String.valueOf(mCurrentVolume / 100));
                    } else {
                        if (!mLiving) {
                            SeekBar seekbar = mPlayerSeekBar;
                            int value = seekbar.getProgress();
                            int per = seekbar.getMax() / 100;
                            value += xAxisOffset > 0 ? per : -per;
                            if (value > seekbar.getMax())
                                value = seekbar.getMax();
                            else if (value < per)
                                value = 0;
                            if (mMediaPlayer != null) {
                                mMediaPlayer.seekTo(value);
                            }
                            seekbar.setProgress(value);
                            if (xAxisOffset > 0)
                                showTouchMsg(mSeekIconforward, stringForTime(value));
                            else
                                showTouchMsg(mSeekIconBackward, stringForTime(value));
                        }
                    }

                    mTouchStart.x = rawX;
                    mTouchStart.y = rawY;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mMode == MODE.NUL) {
                    doHideShowControlBar();
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra1, int extra2) {
        switch (what) {
            case MediaPlayer.MEDIA_DURATION_UPDATE:
                Message msg = mHandler.obtainMessage(MSG_DURATION, extra1, 0);
                mHandler.sendMessage(msg);
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public int onVideoLocationChange(GPSData data) {
        //mMapTrackView.drawTrackCar(data);
        drawVideoLocation(data);
        mGPSDataList.add(data);
        //mMapTrackView.drawTrackLine(mGPSDataList);
        return 0;
    }

    private void drawVideoLocation(final GPSData data) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                mMapTrackView.drawTrackCar(data);
            }
        });
    }

    @Override
    public boolean onBuffeing(int what, int extra1, int extra2) {
        Log.d(TAG, "onBuffeing:" + what + " extra1 = " + extra1);
        if (what == MediaPlayer.MEDIAPLAYER_BUFFERING_START) {
            Message msg = new Message();
            msg.what = MSG_BUFFERING_START;
            msg.arg1 = extra1;
            mHandler.sendMessage(msg);
        } else if (what == MediaPlayer.MEDIAPLAYER_BUFFERING_END) {
            mHandler.sendEmptyMessage(MSG_BUFFERING_END);
        }
        return true;
    }

    @Override
    public int onVideoLocationDataBuffer(final ByteBuffer buffer) {
        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                if (buffer == null) {
                    mMapTrackView.drawTrackLine(null);
                }

                List<GPSData> list = com.car.control.util.GPSFile.parseGPSList(buffer.array(), false, true, false);
                mMapTrackView.drawTrackLine(list);
            }
        }, 2000);

        return 0;
    }

    @Override
    public void onPreDrawLineTrack() {

    }

    @SuppressLint("UseSparseArrays")
    @Override
    public void onAfterDrawLineTrack(List<GPSData> list) {
        if (list != null && list.size() > 0) {
            mGPSDataMap = new HashMap<Integer, GPSData>();
            mFirstGPSDataTime = list.get(0).time;
            for (GPSData data : list) {
                mGPSDataMap.put(data.time, data);
            }
        }
    }

    @Override
    public void onBackPressed() {
        mPauseFromBack = true;
        if (mVideoShareLayout.getVisibility() == View.VISIBLE) {
            if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
                mMediaPlayer.resume();
            }
            return;
        }

        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            mHandler.removeMessages(MSG_HIDE_CONTROL_BOTTOMBAR);
            exitFullScreen();
            return;
        } else
            super.onBackPressed();

    }

    public void exitFullScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        int newUiOptions = getWindow().getDecorView().getSystemUiVisibility();
        // Navigation bar hiding: Backwards compatible to ICS.
        // if (Build.VERSION.SDK_INT >= 14) {
        // newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        // }

        // Status bar hiding: Backwards compatible to Jellybean
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        if (Build.VERSION.SDK_INT >= 18) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        // getWindow().clearFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getActionBar().show();
        setVideoFullScreenMode(false);
        mMapContainer.setVisibility(View.VISIBLE);
        showControlBar();
        mFullScreen.setImageResource(R.drawable.fullscreen_black);

    }

    private void fullScreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getActionBar().hide();

        int newUiOptions = getWindow().getDecorView().getSystemUiVisibility();
        // Navigation bar hiding: Backwards compatible to ICS.
        // if (Build.VERSION.SDK_INT >= 14) {
        // newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        // }

        // Status bar hiding: Backwards compatible to Jellybean
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        if (Build.VERSION.SDK_INT >= 18) {
            newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);

        // getWindow().addFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setVideoFullScreenMode(true);
        mMapContainer.setVisibility(View.GONE);
        mFullScreen.setImageResource(R.drawable.small_screen_black);
    }

    private void setVideoFullScreenMode(boolean bFull) {
        mIsVideoFullScreenMode = bFull;

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mVideoPreviewContainer.getLayoutParams();
        if (bFull) {
            mMarginBottom = lp.bottomMargin;
            lp.bottomMargin = 0;
            mBottomView.setBackgroundColor(getResources().getColor(R.color.process_b));
        } else {
            lp.bottomMargin = mMarginBottom;
            mBottomView.setBackgroundColor(Color.WHITE);
        }
        mVideoPreviewContainer.setLayoutParams(lp);
    }

    private void doHideShowControlBar() {
        if (!mIsVideoFullScreenMode) {
            if (!mLiving)
                doPauseResume();
        } else {
            if (mBottomView.getVisibility() == View.VISIBLE) {
                hideControlBar();
            } else {
                showControlBar();
            }
        }
    }

    public void hideControlBar() {
        mBottomView.setVisibility(View.INVISIBLE);
        mHandler.removeMessages(MSG_HIDE_CONTROL_BOTTOMBAR);
    }

    public void showControlBar() {
        if (mIsVideoFullScreenMode) {
            mHandler.removeMessages(MSG_HIDE_CONTROL_BOTTOMBAR);
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROL_BOTTOMBAR, 6000);
        }
        mBottomView.bringToFront();
        mBottomView.setVisibility(View.VISIBLE);
    }

    boolean mIsRecording = false;
    String mLastVoiceFile = null;
    long mLastVolTime = 0;
    int mUpIndex = 0;
    Toast mVoiceToast;
    MediaRecorder mMediaRecorder;

    void doVoiceRecord2(final String voiceFile) {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioChannels(2);
        mMediaRecorder.setAudioSamplingRate(48000);
        mMediaRecorder.setAudioEncodingBitRate(128000);
        mMediaRecorder.setOutputFile(voiceFile);
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private OnAudioFocusChangeListener mOnAudioFocusChangeListener = new OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "onAudioFocusChange:focusChange = " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:        //-1
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:        //-2
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:    //-3
                    break;
            }
        }
    };
}
