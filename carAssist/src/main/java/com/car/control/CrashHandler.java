package com.car.control;  
  
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;  
import java.io.StringWriter;  
import java.io.Writer;  
import java.lang.Thread.UncaughtExceptionHandler;  
import java.lang.reflect.Field;  
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;  
import java.text.SimpleDateFormat;  
import java.util.Date;  
import java.util.HashMap;  
import java.util.Map;  
  
import android.content.Context;  
import android.content.pm.PackageInfo;  
import android.content.pm.PackageManager;  
import android.content.pm.PackageManager.NameNotFoundException;  
import android.os.Build;
import android.os.Handler;
import android.os.Looper;  
import android.util.Log;  
import android.widget.Toast;

public class CrashHandler implements UncaughtExceptionHandler {

	public static final String TAG = "CarSvc_CrashHandler";

	private static CrashHandler INSTANCE = new CrashHandler();

	private Context mContext;
	private Handler mHandler = new Handler();

	// 系统默认的 UncaughtException 处理类
	private Thread.UncaughtExceptionHandler mDefaultHandler;
	
	// 用于格式化日期,作为日志文件名的一部分
	private DateFormat mFormatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");

	private CrashHandler() {
		
	}

	public static CrashHandler getInstance() {
		return INSTANCE;
	}

	public void init(Context context) {
		mContext = context;
	
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		if (!handleException(ex) && mDefaultHandler != null) {
			// 如果用户没有处理则让系统默认的异常处理器来处理
			mDefaultHandler.uncaughtException(thread, ex);
		} else {
			//FIXME 不错任何事，主线程未捕获的异常会弹出强制关闭程序，其它线程的异常不会
			// 退出程序
			//android.os.Process.killProcess(android.os.Process.myPid());
			//System.exit(1);
		}
	}

	private boolean handleException(final Throwable ex) {
		if (ex == null) {
			return false;
		}
		
		mHandler.post(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				Toast.makeText(mContext, R.string.crash_tip, Toast.LENGTH_LONG).show();
			}
		});
		
		new Thread(){
			public void run(){
				Map<String, String> infos = collectDeviceInfo(mContext);
				uploadCrashInfo2Server(ex, infos);
			}
		}.start();
		
		return true;
	}

	private Map<String, String> collectDeviceInfo(Context ctx) {
		Map<String, String> infos = new HashMap<String, String>();
		try {
			PackageManager pm = ctx.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);

			if (pi != null) {
				String versionName = pi.versionName == null ? "null" : pi.versionName;
				String versionCode = pi.versionCode + "";
				infos.put("versionName", versionName);
				infos.put("versionCode", versionCode);
			}
		} catch (NameNotFoundException e) {
			Log.e(TAG, "an error occured when collect package info", e);
		}

		Field[] fields = Build.class.getDeclaredFields();
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				infos.put(field.getName(), field.get(null).toString());
				Log.d(TAG, field.getName() + " : " + field.get(null));
			} catch (Exception e) {
				Log.e(TAG, "an error occured when collect crash info", e);
			}
		}
		
		return infos;
	}

	private void uploadCrashInfo2Server(Throwable ex, Map<String, String> infos) {
		StringBuffer sb = new StringBuffer();
		for (Map.Entry<String, String> entry : infos.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			sb.append(key + "=" + value + "\n");
		}

		Writer writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		ex.printStackTrace(printWriter);
		Throwable cause = ex.getCause();
		while (cause != null) {
			cause.printStackTrace(printWriter);
			cause = cause.getCause();
		}
		printWriter.close();

		String result = writer.toString();
		sb.append(result);
		try {
			long timestamp = System.currentTimeMillis();
			String time = mFormatter.format(new Date());
			String fileName = "crash-" + time + "-" + timestamp + ".log";
			
			URL url = new URL("http://car.carassist.cn/uploadlog/carcontrol/" + fileName);  
	        HttpURLConnection conn = (HttpURLConnection)url.openConnection();  
	        conn.setRequestMethod("PUT");   
	        conn.setDoInput(true);  
	        conn.setDoOutput(true);
	        conn.setConnectTimeout(2000);
	        OutputStream os = conn.getOutputStream();       
	        os.write(sb.toString().getBytes());       
	        os.close();           
	          
	        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));  
	        String line ;  
	        String respond ="";  
	        while( (line = br.readLine()) != null ){  
	        	respond += "/n"+line;  
	        }  
	        Log.i(TAG, "respond = " + respond);  
	        br.close();

		} catch (Exception e) {
			Log.e(TAG, "an error occured while writing file...", e);
		}

	}
}
