package com.car.control.dvr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.car.control.R;
import com.car.control.browser.VideoActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class OSSLivingActivity extends Activity implements View.OnClickListener {
//    private String mOSSAccessKey = "xxx";   //ex: "ACSDg19dijfj5Ngs"
//    private String mOSSSecretKey = "xxx";   //ex: "ab2HjdkhaHKusdhj1829jkjhagskJ7s"
//    private String mEndPoint = "xxx";       //ex: "http://oss-cn-shenzhen.aliyuncs.com"
//    private String mBucket = "xxx";         //ex: "liveshenzhen"
//    private String mStreamingFile = "xxx";  //ex: "TWNFC66DRWAAHU9D_1234567890"
//    private String mSerialNum = "xxx";  //ex: "TWNFC66DRWAAHU9D"

    private String mOSSAccessKey = "ACSDg19dijfj5Ngs";   //ex: "ACSDg19dijfj5Ngs"
    private String mOSSSecretKey = "ab2HjdkhaHKusdhj1829jkjhagskJ7s";   //ex: "ab2HjdkhaHKusdhj1829jkjhagskJ7s"
    private String mEndPoint = "http://oss-cn-shenzhen.aliyuncs.com";       //ex: "http://oss-cn-shenzhen.aliyuncs.com"
    private String mBucket = "liveshenzhen";         //ex: "liveshenzhen"
    private String mStreamingFile = "TWNFC66DRWAAHU9D_1234567890";  //ex: "TWNFC66DRWAAHU9D_1234567890"
    private String mSerialNum = android.os.Build.SERIAL;  //ex: "TWNFC66DRWAAHU9D"

    private EditText mOSSAccessKeyEdit;
    private EditText mOSSSecretKeyEdit;
    private EditText mEndPointEdit;
    private EditText mBucketEdit;
    private EditText mStreamingFileEdit;
    private EditText mSerialNumEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_living);

        initView();
    }

    private void initView() {
        Button mStartButton = (Button) findViewById(R.id.start_living);
        mStartButton.setOnClickListener(this);

        mOSSAccessKeyEdit = (EditText) findViewById(R.id.Living_oss_accesskey);
        mOSSSecretKeyEdit = (EditText) findViewById(R.id.Living_oss_secretkey);
        mEndPointEdit = (EditText) findViewById(R.id.Living_oss_endpoint);
        mBucketEdit = (EditText) findViewById(R.id.Living_oss_bucket);
        mStreamingFileEdit = (EditText) findViewById(R.id.Living_oss_streamingfile);
        mSerialNumEdit = (EditText) findViewById(R.id.Living_oss_serialnum);

        mOSSAccessKeyEdit.setText(mOSSAccessKey);
        mOSSSecretKeyEdit.setText(mOSSSecretKey);
        mEndPointEdit.setText(mEndPoint);
        mBucketEdit.setText(mBucket);
        mStreamingFileEdit.setText(mStreamingFile);
        mSerialNumEdit.setText(mSerialNum);
    }

    @Override
    public void onClick(View v) {
        mOSSAccessKey = mOSSAccessKeyEdit.getText().toString();
        mOSSSecretKey = mOSSSecretKeyEdit.getText().toString();
        mEndPoint = mEndPointEdit.getText().toString();
        mBucket = mBucketEdit.getText().toString();
        mStreamingFile = mStreamingFileEdit.getText().toString();
        mSerialNum = mSerialNumEdit.getText().toString();

        {
            Intent intent = new Intent(this, VideoActivity.class);
            JSONObject jsonObject = new JSONObject();

            try {
                jsonObject.put("ret", 0);
                jsonObject.put("access", mOSSAccessKey);
                jsonObject.put("secret", mOSSSecretKey);
                jsonObject.put("ep", mEndPoint);
                jsonObject.put("bk", mBucket);
                jsonObject.put("sf", mStreamingFile);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            intent.putExtra(VideoActivity.KEY_LIVING_FLAG, true);
            intent.putExtra(VideoActivity.KEY_LIVING_SN, mSerialNum);//FIXME:
            String url = "p2p://living";
            intent.setDataAndType(Uri.parse(url), "video/*");
            intent.putExtra(VideoActivity.KEY_LIVING_JSON, jsonObject.toString());

            startActivity(intent);
        }
    }
}