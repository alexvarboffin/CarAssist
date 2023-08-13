package com.car.control.util;


// Copied from WifiManager to get some @hide API definition.
public class WifiHideAPI {
	public static final String EXTRA_WIFI_AP_STATE = "wifi_state";

	public static final String WIFI_AP_STATE_CHANGED_ACTION =
    "android.net.wifi.WIFI_AP_STATE_CHANGED";
	
	
	public static final int WIFI_AP_STATE_DISABLING = 10;
    public static final int WIFI_AP_STATE_DISABLED = 11;
    public static final int WIFI_AP_STATE_ENABLING = 12;
    public static final int WIFI_AP_STATE_ENABLED = 13;
	public static final int WIFI_AP_STATE_FAILED = 14;
}
