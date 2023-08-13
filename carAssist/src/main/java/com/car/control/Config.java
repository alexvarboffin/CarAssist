
package com.car.control;

import android.os.Environment;

public class Config {
	public static final String CARDVR_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() 
			+ "/CarAssist";
	public static final String CARDVR_LOCK_PATH = CARDVR_PATH + "/lock";
	public static final String CARDVR_CAPTURE_PATH = CARDVR_PATH + "/capture";
	public static final String CARDVR_LOOP_PATH = CARDVR_PATH;
	public static final String CARDVR_EDIT_PATH  = CARDVR_PATH + "/edit";
	public static final String CARDVR_CACHE_PATH  = CARDVR_PATH + "/cache";
	public static final String CARDVR_AD_PATH  = CARDVR_PATH + "/ad";
	
	public static final String REMOTE_LOCK_PATH = "/lock";
	public static final String REMOTE_CAPTURE_PATH = "/capture";
	public static final String REMOTE_LOOP_PATH = "/";
	
	public static final String HTTP_URL = "cgi-bin/Config.cgi";
	
	//public static final String VIDEO_PATH = "/storage/sdcard1/record";
	
	public static final String RESPONSE_OK = "0\nOK\n";
	public static final String RESPONSE_FAIL = "0\nFAIL\n";
	
	public static final String ACTION_GET = "get";
	public static final String ACTION_SET = "set";
	public static final String ACTION_DIR = "dir";
	public static final String ACTION_DOWNLOAD = "download";
	public static final String ACTION_DELETE = "delete"; 
	public static final String ACTION_UPGRADE = "upgrade";
	public static final String ACTION_NAVI = "navi";
	public static final String ACTION_SYNC_FILE = "sync_file";
	public static final String ACTION_THUMBNAIL = "thumbnail";
	
	public static final String PROPERTY_CARDVR_DIR_PATH = "path";
	
	public static final String PROPERTY_CARDVR_STATUS_ALL = "CarDvr.Status.*";
	public static final String PROPERTY_CARDVR_STATUS_DEVICE = "CarDvr.Status.Device";
	public static final String PROPERTY_CARDVR_STATUS_DEVICE_VALUE = "CarDvr.Status.Device=CarDvr\n";
	public static final String PROPERTY_CARDVR_STATUS_SERIALNO = "CarDvr.Status.Serialno";
	public static final String PROPERTY_CARDVR_STATUS_ABILITY = "CarDvr.Status.Ability";
	public static final String PROPERTY_CARDVR_STATUS_ABILITY_VALUE1 = "CarDvr.Status.Ability= volume,brightness\n";
	public static final String PROPERTY_CARDVR_STATUS_ABILITY_VALUE2 = "CarDvr.Status.Ability= volume,brightness,,voice\n";
	public static final String PROPERTY_DVRSDCARD_STATUS_MOUNT = "Dvr.Sdcard.Status.Mount";
	public static final String PROPERTY_CARCONTROL_STATUS_VERSION = "Carcontrol.Status.Version";
	
	public static final String PROPERTY_SETTING_STATUS_ALL = "Setting.Status.*";
	public static final String PROPERTY_SETTING_STATUS_VOLUME = "Setting.Status.Volume";
	public static final String PROPERTY_SETTING_STATUS_BRIGHTNESS = "Setting.Status.Brightness";
	public static final String PROPERTY_SETTING_STATUS_WAKE_UP = "Setting.Status.Wake.Up";
	public static final String PROPERTY_SETTING_STATUS_VOICE_PROMPT = "Setting.Status.Voice.Prompt";
	
	public static final String PROPERTY_CAMERA_RECORDING_STATUS = "Camera.Recording.Status";
	public static final String PROPERTY_CAMERA_RECORDING_STATUS_TRUE = "Camera.Recording.Status=true\n";
	public static final String PROPERTY_CAMERA_RECORDING_STATUS_FALSE = "Camera.Recording.Status=false\n";
	public static final String PROPERTY_CAMERA_RECORDING_START = "Camera.Recording.Start";
	public static final String PROPERTY_CAMERA_RECORDING_STOP = "Camera.Recording.Stop";
	public static final String PROPERTY_CAMERA_TAKE_PHOTO = "Camera.Take.Photo";
	
	public static final String PROPERTY_NAVI_LATITUDE = "latitude";
	public static final String PROPERTY_NAVI_LONGITUDE = "longitude";

}
