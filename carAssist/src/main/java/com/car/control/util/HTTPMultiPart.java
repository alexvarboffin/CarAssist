package com.car.control.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import android.util.Log;

public class HTTPMultiPart {
	private static final String TAG = "HTTPMultiPart";
	
	private final String boundary;
	private static final String LINE_FEED = "\r\n";
	
	private static final class Part {
		String name;
		String value;
		File file;
	}
	
	private final ArrayList<Part> parts = new ArrayList<Part>();

	/**
	 * 
	 */
	public HTTPMultiPart() {
		// creates a unique boundary based on time stamp
		boundary = "----" + "ddixaczo3A9BallsUx" + System.currentTimeMillis();
	}
	
	/**
	 * Adds a form field to the request
	 */
	public void addFormField(String name, String value) {
		Part p = new Part();
		p.name = name;
		p.value = value;
		parts.add(p);
	}

	/**
	 * Adds a  file section to the request
	 */
	public void addFilePart(String name, File uploadFile)  {
		Part p = new Part();
		p.name = name;
		p.file = uploadFile;
		parts.add(p);
	}
	
	public interface ProgressCallback {
		//percent from 0 - 100, -1 if error
		public void postProgress(int percent);
	}
	
	/**
	 * start the http post request. must call it in work thread.
	 * @param requestURL
	 * @return null if error, else the response data.
	 */
	public String post(String requestURL, ProgressCallback cb) {
		try {
			//first ge the post data 
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(4 * 1024 * 1024);
			if(!buildOutputData(outputStream)){
				cb.postProgress(-1);
				return null;
			}
					
			byte[] data = outputStream.toByteArray();

			URL url = new URL(requestURL);
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			httpConn.setUseCaches(false);
			httpConn.setDoOutput(true); // indicates POST method
			httpConn.setDoInput(true);
			httpConn.setRequestMethod("POST");    
			httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			httpConn.setFixedLengthStreamingMode(data.length);

			OutputStream out = httpConn.getOutputStream();
			
			int lastProgress = -1;
			//write 4k each time so we can know the progress
			for (int i = 0; i < data.length;) {
				int n = 4096;
				if( i + n > data.length)
					n = data.length - i;
				
				out.write(data, i, n);
				i += n;
				double d = 1.0 * i / (data.length + 1024);	//assume we can get 1024 response
			//	Log.i(TAG, "postProgress:" + d + ",i=" + i + ",len=" + (data.length + 1024));
				
				int progress = (int) (d * 100);
				if (progress != lastProgress){
					cb.postProgress(progress);
					lastProgress = progress;
				}
				out.flush();
			}
				
			// checks server's status code first
			StringBuffer response = new StringBuffer(1024);
			int status = httpConn.getResponseCode();
			// Map<String, List<String>> s = httpConn.getHeaderFields();
			// Log.d(TAG, s.toString());
			
			InputStream ins = null;
			if (status == HttpURLConnection.HTTP_OK) 
				 ins = httpConn.getInputStream();
			else 
				ins = httpConn.getErrorStream();
			
            BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
            String line = null;
	        while ((line = reader.readLine()) != null) {
	        	response.append(line);
	        }
	        reader.close();
	        
			
			ins.close();
			httpConn.disconnect();
			if (status == HttpURLConnection.HTTP_OK) {
				return response.toString();
			} else {
				Log.w(TAG, "Server returned non-OK status: " + status);
			}
		} catch (Exception e) {
			Log.w(TAG, "post Error", e);
		}
		cb.postProgress(-1);
		return null;
	}


	
	private boolean buildOutputData(OutputStream outputStream) {
		PrintWriter writer = new PrintWriter(outputStream, true);
		
		for(Part p : parts) {
			if (p.file == null) {
				writer.append("--" + boundary).append(LINE_FEED);
				writer.append("Content-Disposition: form-data; name=\"" + p.name + "\"").append(LINE_FEED);
				writer.append(LINE_FEED);
				writer.append(p.value).append(LINE_FEED);
		        writer.flush();
			}
			else {
				String fileName = p.file.getName();
				writer.append("--" + boundary).append(LINE_FEED);
				writer.append("Content-Disposition: form-data; name=\"" + p.name + "\"; filename=\"" + fileName + "\"")
						.append(LINE_FEED);
				writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(fileName)).append(LINE_FEED);
		        writer.append(LINE_FEED);
				writer.flush();
				FileInputStream inputStream = null;
				try {
					inputStream = new FileInputStream(p.file);
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffer)) > 0) {
						outputStream.write(buffer, 0, bytesRead);
					}
					outputStream.flush();
				} catch (Exception e) {
					Log.w(TAG, "read file error" + e.toString());
					return false;
				}
				finally {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (IOException e) {
						}	
					}
				}
		        writer.append(LINE_FEED);
		        writer.flush();
			}
		}
        writer.append(LINE_FEED).flush();
        writer.append("--" + boundary + "--").append(LINE_FEED);
        writer.close();
		return true;
	}
}
