package com.car.control.browser;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import android.content.Context;
import android.util.Log;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.HeadObjectRequest;
import com.alibaba.sdk.android.oss.model.HeadObjectResult;
import com.alibaba.sdk.android.oss.model.Range;
import com.media.tool.MediaPlayer;

public class AliyunOSSDataSource {
	private String mEndPoint = "xxx";
	private String mBucketName = "xxx";
	private String mAccessKeyID = "xxx";
	private String mAccessKeySecret = "xxx";
	private String mStreamFileString = "xxx";
	
	private long mReadOffset = 0;
    private long mFileSize = 0;
	
	private OSS mOSS = null;
	private MediaPlayer mDataCallback = null;
	private FileOutputStream mBufferStreaming = null;
	
	private Object mObject = new Object();
	private OSSAsyncTask mOSSAsyncTask = null;
	private boolean mStop = true;
	private String TAG = "CarSvc_DataSource";
    
    final private long MAX_BUFFER_SIZE = 0x100000;  //if the filesize bigger than this value, seek to latest SEEK_BUFFER_POINT data
    final private long SEEK_BUFFER_POINT = 0x80000;
	
	public AliyunOSSDataSource(Context context, String accessKey, String secretKey, String endpoint, String bucket, String streamingfile) {
        mAccessKeyID = accessKey;
        mAccessKeySecret = secretKey;
        OSSCredentialProvider credentialProvider = new OSSPlainTextAKSKCredentialProvider(mAccessKeyID, mAccessKeySecret);

        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求书，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次
        //OSSLog.enableLog();
        if(endpoint != null) {
        	mEndPoint = endpoint;
        }
        
        if(bucket != null) {
        	mBucketName = bucket;
        }

        if(streamingfile != null) {
        	mStreamFileString = streamingfile;
        }

        mOSS = new OSSClient(context, mEndPoint, credentialProvider, conf);
//        Log.d(TAG, "AliyunOSSDataSource mEndPoint = " + mEndPoint + " mBucketName = " + mBucketName + " mAccessKeyID = " + mAccessKeyID + " mAccessKeySecret = " + mAccessKeySecret + " mStreamFileString = " + mStreamFileString);
	}

    public void setMediaPlayer(MediaPlayer mediaplayer) {
		mDataCallback = mediaplayer;
	}

	public void start() {
		synchronized (mObject) {
			mStop = false;
			setOSSDownloadPoint();
		}
	}

	public void stop() {
		synchronized (mObject) {
			mStop = true;
			stopOSSDownload();
		}		
	}

	private void stopOSSDownload() {
		if(mOSSAsyncTask != null) {
			mOSSAsyncTask.cancel();
		}
		
		if(mBufferStreaming != null) {
			try {
				mBufferStreaming.flush();
				mBufferStreaming.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mBufferStreaming = null;
		}
	}

	public void setBufferFilename(String filename) {
		try {
			mBufferStreaming = new FileOutputStream(filename);
			Log.d(TAG, "setBufferFilename filename = " + filename + " mBufferStreaimg " + mBufferStreaming);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private int restartOSSDownload() {
	    //maybe reach end of file, restart the read again
	    while(mStop == false) {
	    	try {
	    		//wait a little moment to retry
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
            checkFileSize();
            Log.e(TAG, "restart download since no stop called mFileSize = " + mFileSize + " mReadOffset = " + mReadOffset);
            if(mFileSize > mReadOffset) {
                startOSSDownload();
                break;
            }
	    }
	    
	    return 0;
	}	
	
	//retry if failed happen until stop called
	private int startOSSDownload() {
        GetObjectRequest get = new GetObjectRequest(mBucketName, mStreamFileString);
        get.setRange(new Range(mReadOffset, Range.INFINITE)); // 下载0到99共100个字节，文件范围从0开始计算
        Log.d(TAG, "Start Download From offset " + mReadOffset);
        
        OSSAsyncTask task = mOSS.asyncGetObject(get, new OSSCompletedCallback<GetObjectRequest, GetObjectResult>() {
            @Override
            public void onSuccess(GetObjectRequest request, GetObjectResult result) {
                // 请求成功
                InputStream inputStream = result.getObjectContent();
                byte[] buffer = new byte[40960];
                int len = -1;
                try {
                    while ((len = inputStream.read(buffer)) != -1) {
                        // 处理下载的数据
                        mReadOffset += len;
//                        Log.d(TAG, "read length: " + len + " mReadOffset = " + mReadOffset);
                        if(mStop == true) {
                        	break;
                        }
                        
                        if(mDataCallback != null) {
                        	mDataCallback.writeRawData(ByteBuffer.wrap(buffer, 0, len));
                        }
                        
                        if(mBufferStreaming != null) {
                        	mBufferStreaming.write(buffer, 0, len);
                        }
                    }
                    //Log.d(TAG, "download success. EOF reach");
                    restartOSSDownload();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(GetObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                OSSonFailure(clientExcepion, serviceException);
                restartOSSDownload();
            }
        });
        
    	mOSSAsyncTask = task;
        
		return 0;
	}

    private long checkFileSize() {
        HeadObjectRequest head = new HeadObjectRequest(mBucketName, mStreamFileString);
        HeadObjectResult result = null;
		try {
			result = mOSS.headObject(head);
            mFileSize = result.getMetadata().getContentLength();
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        return mFileSize;
    }

    public long getFileSize() {
    	return mFileSize;
    }

    //when the start call, check the filesize in the server, if it's too big
    //it means maybe someone is living now, so skip some data at the beginning
    //let the native code to find the nearest time for living
    private void setOSSDownloadPoint() {
        HeadObjectRequest head = new HeadObjectRequest(mBucketName, mStreamFileString);

        OSSAsyncTask task = mOSS.asyncHeadObject(head, new OSSCompletedCallback<HeadObjectRequest, HeadObjectResult>() {
            @Override
            public void onSuccess(HeadObjectRequest request, HeadObjectResult result) {
                long size = result.getMetadata().getContentLength();
                Log.d(TAG, "object Size: " + size);
                Log.d(TAG, "object Content Type: " + result.getMetadata().getContentType());

                if(size > MAX_BUFFER_SIZE) {
                	mReadOffset = size - SEEK_BUFFER_POINT;
                }
                Log.d(TAG, "set DownloadPoint mReadOffset: " + mReadOffset);
                
                startOSSDownload();
            }

            @Override
            public void onFailure(HeadObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                OSSonFailure(clientExcepion, serviceException);

                //maybe the file has not been generated now, just start retry mechiszm
                startOSSDownload();
            }
        });
    }

	private void OSSonFailure(ClientException clientExcepion, ServiceException serviceException) {
        // 请求异常
        if (clientExcepion != null) {
            // 本地异常如网络异常等
            clientExcepion.printStackTrace();
        }
        if (serviceException != null) {
            // 服务异常
            Log.e(TAG, "ErrorCode " + serviceException.getErrorCode());
            Log.e(TAG, "RequestId " + serviceException.getRequestId());
            Log.e(TAG, "HostId " + serviceException.getHostId());
            Log.e(TAG, "RawMessage " +serviceException.getRawMessage());
        }

        Log.e(TAG, "onFailure. mStop = " + mStop);
        return;
    }
}
