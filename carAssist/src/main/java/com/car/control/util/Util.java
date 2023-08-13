/*
 * Copyright 2012 WonderMedia Technologies, Inc. All Rights Reserved. 
 *  
 * This PROPRIETARY SOFTWARE is the property of WonderMedia Technologies, Inc. 
 * and may contain trade secrets and/or other confidential information of 
 * WonderMedia Technologies, Inc. This file shall not be disclosed to any third party, 
 * in whole or in part, without prior written consent of WonderMedia. 
 *  
 * THIS PROPRIETARY SOFTWARE AND ANY RELATED DOCUMENTATION ARE PROVIDED AS IS, 
 * WITH ALL FAULTS, AND WITHOUT WARRANTY OF ANY KIND EITHER EXPRESS OR IMPLIED, 
 * AND WonderMedia TECHNOLOGIES, INC. DISCLAIMS ALL EXPRESS OR IMPLIED WARRANTIES 
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.  
 */

package com.car.control.util;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

public final class Util {
	private static final String TAG = "CarSvc_Util";
	private static final boolean DEBUG = false;
	
	static final String AUTHORITY = "com.car.provider.carsettingprovider";
	public static final Uri URI = Uri.parse("content://" + AUTHORITY);
	public static final Uri URI_MUTE = Uri.parse("content://" + AUTHORITY + "/ttsmute/");
	public static final Uri URI_WAKEUP = Uri.parse("content://" + AUTHORITY + "/sensity/");
	public static final Uri URI_AUTOCLEAR = Uri.parse("content://" + AUTHORITY + "/autoclear/");

	private static Context sAppContext;

	public static void initContext(Context ctx) {
		if (sAppContext == null)
			sAppContext = ctx.getApplicationContext();
	}

	public static Object[] getLocalInet4AddrAndInterface() {
		Object[] ret = new Object[2];

		try {
			Inet4Address foundAddr = null;
			NetworkInterface foundInf = null;

			if (Build.VERSION.SDK_INT >= 9) {
				// below api level is 9
				for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
						.hasMoreElements();) {
					NetworkInterface intf = en.nextElement();
					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
							.hasMoreElements();) {
						InetAddress inetAddress = enumIpAddr.nextElement();
						if (!inetAddress.isLoopbackAddress()) {
							if (inetAddress instanceof Inet4Address) {
								if (DEBUG)
									Log.d(TAG, "Found InetAddr:" + inetAddress + " on " + intf);

								if (null == foundAddr) {
									foundAddr = (Inet4Address) inetAddress;
									foundInf = intf;
								} else {
									Log.d(TAG, "getLocalInet4AddrAndInterface Previous InetAddr:"
											+ foundAddr + " on " + foundInf);
									Log.d(TAG, "getLocalInet4AddrAndInterface Another InetAddr:"
											+ inetAddress + " on " + intf);
									String foundName = foundInf.getName();
									String anotherName = intf.getName();
									// wireless network in high priority
									// otherwise the first founded in high priority
									if (foundName != null && !foundName.contains("p2p")
											&& anotherName != null && (anotherName.contains("p2p"))) {
										foundAddr = (Inet4Address) inetAddress;
										foundInf = intf;
										Log.d(TAG,
												"getLocalInet4AddrAndInterface change found address to Another");
									} else if (foundName != null && (!foundName.contains("wlan") && !foundName.contains("p2p"))
											&& anotherName != null
											&& ((anotherName.contains("wlan") || anotherName.contains("eth")))) {
										foundAddr = (Inet4Address) inetAddress;
										foundInf = intf;
										Log.d(TAG,
												"getLocalInet4AddrAndInterface change found address to Another");
									}
								}
							} else {
								if (DEBUG)
									Log.d(TAG, "Ignore InetAddr:" + inetAddress);
							}
						} else {
							if (DEBUG)
								Log.d(TAG, "Ignore loopback InetAddr:" + inetAddress);
						}
					}
				}
			} else {
				for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
						.hasMoreElements();) {
					NetworkInterface intf = en.nextElement();
					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
							.hasMoreElements();) {
						InetAddress inetAddress = enumIpAddr.nextElement();

						if (inetAddress instanceof Inet4Address) {
							if ((inetAddress.getAddress()[0] & 0xFF) != 127) {
								if (DEBUG)
									Log.d(TAG, "Found InetAddr:" + inetAddress + " on " + intf);

								if (null == foundAddr) {
									foundAddr = (Inet4Address) inetAddress;
									foundInf = intf;
								} else {
									Log.d(TAG, "getLocalInet4AddrAndInterface Previous InetAddr:"
											+ foundAddr);
									Log.d(TAG, "getLocalInet4AddrAndInterface Another InetAddr:"
											+ inetAddress);
									String foundName = foundInf.getName();
									String anotherName = intf.getName();
									// wireless network in high priority
									if (foundName != null && !foundName.contains("wlan")
											&& anotherName != null
											&& ((anotherName.contains("wlan") || anotherName.contains("eth")))) {
										foundAddr = (Inet4Address) inetAddress;
										foundInf = intf;
										Log.d(TAG,
												"getLocalInet4AddrAndInterface change found address to Another");
									}

								}
							} else {
								if (DEBUG)
									Log.d(TAG, "Ignore loopback InetAddr:" + inetAddress);
							}
						} else {
							if (DEBUG)
								Log.d(TAG, "Ignore InetAddr:" + inetAddress);
						}
					}
				}
			}

			if (foundAddr != null) {
				Log.d(TAG, "getLocalInet addr : " + foundAddr);
				ret[0] = foundAddr;
				ret[1] = foundInf; // maybe null
				return ret;
			}
		} catch (SocketException e) {
			Log.w(TAG, "getLocalInetAddr Error:" + e.toString());
		} catch (Exception e) {
			Log.w(TAG, "getLocalInetAddr error:" + e.toString());
		}
		return null;
	}

	/**
	 * return local Inet4Address address. Ignore loopback or IPv6.
	 * 
	 * @return null if no Inet4Address.
	 */
	public static Inet4Address getLocalInet4Addr() {
		Object[] addrInf = getLocalInet4AddrAndInterface();
		if (addrInf != null)
			return (Inet4Address) addrInf[0];
		return null;
	}

	public static Inet4Address getBroadcastHostAddress() {
		if (sBroadcastHostAddress == null || !(sBroadcastHostAddress instanceof Inet4Address)){
			return getLocalInet4Addr();
		}
		
		return (Inet4Address) sBroadcastHostAddress;
	}

	private static InetAddress sBroadcastHostAddress = null;

	public static Object[] getBroadcast() {
		InetAddress bcastAddr = null;
		NetworkInterface bcastInf = null;
		sBroadcastHostAddress = null;

		try {
			System.setProperty("java.net.preferIPv4Stack", "true");
			if (Build.VERSION.SDK_INT >= 9) {
				Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();
				while (niEnum.hasMoreElements()) {
					NetworkInterface ni = niEnum.nextElement();
					if (!ni.isLoopback()) {
						for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses()) {
							InetAddress bc = interfaceAddress.getBroadcast();
							
							if (bc != null) {
								if (bc instanceof Inet4Address) {
									if (bcastAddr == null) {
										bcastAddr = bc;
										bcastInf = ni;
										sBroadcastHostAddress = interfaceAddress.getAddress();
									} else {
										Log.d(TAG, "getBroadcast Previous InetAddr:" + bcastAddr);
										Log.d(TAG, "getBroadcast Another InetAddr:" + ni);
										String foundName = bcastInf.getName();
										String anotherName = ni.getName();
										// wireless network in high priority
										// highest --> p2p
										// then --> wlan, lowest --> other
										if (foundName != null && !foundName.contains("p2p")
												&& anotherName != null
												&& (anotherName.contains("p2p"))) {
											bcastAddr = bc;
											bcastInf = ni;
											sBroadcastHostAddress = interfaceAddress.getAddress();
											Log.d(TAG,
													"getBroadcast change found address to Another");

										} else if (foundName != null && (!foundName.contains("wlan") && !foundName.contains("p2p"))
												&& anotherName != null
												&& ((anotherName.contains("wlan") || anotherName.contains("eth")))) {
											bcastAddr = bc;
											bcastInf = ni;
											sBroadcastHostAddress = interfaceAddress.getAddress();
											Log.d(TAG,
													"getBroadcast change found address to Another");
										}
									}
								} else {
									Log.d(TAG, "Ignore bcast address:" + bc.toString());
								}
							} else {
								Log.d(TAG, "No bcast address:" + interfaceAddress);
							}
						}
					}
				}
			} else {
				if (sAppContext == null) {
					// Call Util.initContext() firstly
					Log.d(TAG, "No app context!");
					return null;
				}

				WifiManager wm = (WifiManager) sAppContext.getSystemService(Context.WIFI_SERVICE);
				if (wm == null) {
					Log.d(TAG, "Could not get wifi manager!");
				} else {
					DhcpInfo di = wm.getDhcpInfo();
					if (di != null) {
						// the high order is the low 8 bits
						int broadcastValue = (di.ipAddress & di.netmask) | ~di.netmask;
						byte[] section = new byte[4];
						for (int i = 0; i < 4; i++) {
							section[i] = (byte) ((broadcastValue >> i * 8) & 0xFF);
						}
						bcastAddr = (Inet4Address) InetAddress.getByAddress(section);
					} else {
						Log.d(TAG, "Could not get dhcp info!");
					}
				}

				if (bcastAddr == null) {
					Inet4Address local = getLocalInet4Addr();
					if (local != null) {
						byte[] b = local.getAddress();
						if (b != null && b.length >= 4) {
							b[3] = (byte) 0xFF; // just change 192.168.0.x to 192.168.0.255
							bcastAddr = (Inet4Address) InetAddress.getByAddress(b);
							Log.d(TAG, "Guess from " + local);
						}
					}
				}
			}
		} catch (SocketException e) {
			Log.d(TAG, "Find BCast socket error:", e);
		} catch (Exception e) {
			Log.d(TAG, "Find BCast error:", e);
		}

		if (bcastAddr != null) {
			Log.d(TAG, "getBroadcast addr : " + bcastAddr);
			Object[] ret = new Object[2];
			ret[0] = bcastAddr;
			ret[1] = bcastInf;
			return ret;
		}
		
		sBroadcastHostAddress = null;
		return null;
	}

	static void safeSleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	public static void assertResult(boolean bool) {
		if (bool)
			return;
		Exception e = new Exception("Assert Error");
		e.fillInStackTrace();
		Log.e("Assert", "AssertError:", e);
	}

	public static void dumpStack() {
		Exception e = new Exception("Dump");
		e.fillInStackTrace();
		Log.e("Debug", "", e);
	}

	// A helper function to split the text name1:value1\nname2:value2 to map
	public static HashMap<String, String> splitTextParams(String text) {

		HashMap<String, String> params = new HashMap<String, String>();

		int start = 0;
		int nameEnd, valueEnd;

		while ((nameEnd = text.indexOf(":", start)) != -1) {
			String name = text.substring(start, nameEnd);

			valueEnd = -1;
			for (int i = nameEnd + 1; i < text.length(); i++) {
				if (text.charAt(i) == '\n') {
					valueEnd = i;
					break;
				} else
					valueEnd = i + 1;
			}

			if (valueEnd == -1)
				break;

			String value = text.substring(nameEnd + 1, valueEnd);
			start = valueEnd;
			params.put(name.trim(), value.trim());
		}
		return (params.size() == 0 ? null : params);
	}

	public static String getPostfix(String fName) {
		int postfixPos = fName.lastIndexOf(".");
		String end = "*";
		if (postfixPos > 0) {
			end = fName.substring(postfixPos + 1, fName.length()).toLowerCase(Locale.ENGLISH);
		}
		return end;
	}
	
	public static int getVoumleMin(Context context){
		return 0;
	}
	
	public static int getVoumleMax(Context context){
		AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		return am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
	}
	
	public static int getVoumleCurrent(Context context){
		AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		return am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
	}
	
	public static void setVoumle(Context context, int voumle){
		AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, voumle, AudioManager.FLAG_SHOW_UI);
	}
	
	public static int getBrightnessMin(Context context){
		return 0;
	}
	
	public static int getBrightnessMax(Context context){
		return 255;
	}
	
	public static int getBrightnessCurrent(Context context){
		try {
			return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
		} catch (SettingNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return 0;
		}
		
	}
	
	public static void setBrightness(Context context, int brightness){
		Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);
	}
	
	public static void setAutoClear(Context ctx, boolean auto) {
		ContentValues values = new ContentValues();
		values.put("autoclear", auto? 1: 0);
		ctx.getContentResolver().update(URI_AUTOCLEAR, values, null, null);
	}	
	
	public static int getAutoClear(Context ctx) {
	    Cursor ret = ctx.getContentResolver().query(URI_AUTOCLEAR, new String[]{"autoclear"}, null, null, null);
		if (ret == null) return 0;
		ret.moveToFirst();
		if (ret.getCount() <= 0) {
			ret.close();
			return 0;
		}
		int auto = ret.getInt(ret.getColumnIndex("autoclear"));	
		ret.close();
		return auto;
	}	
	
	public static int getTTSMute(Context ctx) {
	    Cursor ret = ctx.getContentResolver().query(URI_MUTE, new String[]{"ttsmute"}, null, null, null);
		if (ret == null) return 0;
		ret.moveToFirst();
		if (ret.getCount() <= 0) {
			ret.close();
			return 0;
		}
		int mute = ret.getInt(ret.getColumnIndex("ttsmute"));	
		ret.close();
		return mute;
	}
	
	public static void setTTSMute(Context ctx, boolean mute) {
		ContentValues values = new ContentValues();
		values.put("ttsmute", mute? 1: 0);
		ctx.getContentResolver().update(URI_MUTE, values, null, null);
	}		
	
	public static int getWakeupSensity(Context ctx) {
	    Cursor ret = ctx.getContentResolver().query(URI_WAKEUP, new String[]{"sensity"}, null, null, null);
		if (ret == null) return 0;
		ret.moveToFirst();
		if (ret.getCount() <= 0) {
			ret.close();
			return 0;
		}
		int level = ret.getInt(ret.getColumnIndex("sensity"));	
		ret.close();
		return level;
	}	
	
	public static void setWakeupSensity(Context ctx, int level) {
		ContentValues values = new ContentValues();
		values.put("sensity", level);
		ctx.getContentResolver().update(URI_WAKEUP, values, null, null);
	}
	
	public static int getCarControlVersion(Context ctx, String filepath){
        PackageManager pm = ctx.getPackageManager();    
        PackageInfo info = pm.getPackageArchiveInfo(filepath, 
        		PackageManager.GET_ACTIVITIES);    
        if(info != null){      
            return info.versionCode;    
        }
        return 0;
	}
	
	/**
     * 去掉url中的路径，留下请求参数部分
     * @param strURL url地址
     * @return url请求参数部分
     */
    private static String TruncateUrlPage(String strURL){
	    String strAllParam=null;
	      String[] arrSplit=null;
	     
	      //strURL=strURL.trim().toLowerCase();
	      strURL=strURL.trim();
	     
	      arrSplit=strURL.split("[?]");
	      if(strURL.length()>1){
	          if(arrSplit.length>1){
	              if(arrSplit[1]!=null){
	                  strAllParam=arrSplit[1];
	              }
	          }
	      }
	     
	    return strAllParam;   
    }
	
    /**
     * 解析出url参数中的键值对
     * @param URL  url地址
     * @return  url请求参数部分
     */
    public static Map<String, String> URLRequest(String URL){
	    Map<String, String> mapRequest = new HashMap<String, String>();
	   
	      String[] arrSplit=null;
	     
	    String strUrlParam=TruncateUrlPage(URL);
	    if(strUrlParam==null){
	        return mapRequest;
	    }
	    arrSplit=strUrlParam.split("[&]");
	    for(String strSplit:arrSplit){
	          String[] arrSplitEqual=null;         
	          arrSplitEqual= strSplit.split("[=]");
	         
	          //解析出键值
	          if(arrSplitEqual.length>1){
	              //正确解析
	              mapRequest.put(arrSplitEqual[0], arrSplitEqual[1]);	             
	          }
	          else{
	              if(arrSplitEqual[0]!=""){
	              //只有参数没有值，不加入
	              mapRequest.put(arrSplitEqual[0], "");       
	              }
	          }
	    }   
	    return mapRequest;   
    }
    
    public static void renameDirectory(String fromDir, String toDir) {

        File from = new File(fromDir);

        if (!from.exists() || !from.isDirectory()) {
          System.out.println("CarDVR directory does not exist: " + fromDir);
          return;
        }

        File to = new File(toDir);

        //Rename
        if (from.renameTo(to))
          System.out.println("rename CarDVR to CarAssist Success!");
        else
          System.out.println("rename CarDVR to CarAssist Error");
      }

	
}
