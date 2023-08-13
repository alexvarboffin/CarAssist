
package com.car.control.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.car.control.CarWebSocketClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.NotYetConnectedException;
import java.util.Map;
import java.util.Scanner;

public class HttpRequestManager {
	
	private static final String TAG = "CarSvc_HttpReqManager";
	
	private HandlerThread mWorkThread;
	private Handler mWorkHandler;
	private static HttpRequestManager sIns;
	
	private HttpRequestManager(){
		mWorkThread = new HandlerThread("http work");
		mWorkThread.start();
		mWorkHandler = new Handler(mWorkThread.getLooper());
	}
	
	public static void create(){
		sIns =  new HttpRequestManager();
	}
	
	public static void destory(){
		sIns.mWorkThread.quit();
	}
	
	public static HttpRequestManager instance(){
		return sIns;
	}
	
	public void requestHttp(final String str, final Map<String, String> headers, 
			final OnHttpResponseListener listener){
		mWorkHandler.post(() -> {
			HttpURLConnection urlConnection = null;
			Scanner scanner = null;
			try {
				URL url = new URL(str);
				urlConnection = (HttpURLConnection)url.openConnection();
				if(headers != null && headers.size() != 0){
						for (Map.Entry<String, String> entry : headers.entrySet()) {
							urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
						}
				}
				urlConnection.setConnectTimeout(2000);
				InputStream in = new BufferedInputStream(urlConnection.getInputStream());
				scanner = new Scanner(in);
				scanner.useDelimiter("\\A");
				String result =  scanner.hasNext() ? scanner.next() : "";
				listener.onHttpResponse(result);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				listener.onHttpResponse(null);
			} catch (IOException e) {
				e.printStackTrace();
				listener.onHttpResponse(null);
			} finally {
				if(urlConnection != null)
					urlConnection.disconnect();
				if(scanner != null)
					scanner.close();
			}
		});
	}
	
	public void requestHttp(final String str, final OnHttpResponseListener listener){
		requestHttp(str, null, listener);
	}
	
	public void requestWebSocket(final String send){
		mWorkHandler.post(new Runnable(){

			@Override
			public void run() {
				if(CarWebSocketClient.instance() == null || !CarWebSocketClient.instance().isOpen())
					return;
				try{
					CarWebSocketClient.instance().send(send);
				}catch(NotYetConnectedException e){
					Log.i(TAG,"NotYetConnectedException:" + e);
					CarWebSocketClient.instance().close();
				}
			}
			
		});
	}

	public interface OnHttpResponseListener{
		public void onHttpResponse(String result);
	}
}
