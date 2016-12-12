package com.owncloud.android.device;

import com.owncloud.android.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.owncloud.android.device.async.WhisperAsyncProxy;

public class ClientPairActivity extends Activity implements OnClickListener {
	private WhisperDeviceManager cameraManager = WhisperDeviceManager
			.getInstance();
	private RelativeLayout rlBackToResult;
	private TextView tvDeviceId;
	private ImageView ivDevicePre;
	private EditText etPassword;
	private Button btnPair;

	private String deviceId;
	private String mPassword;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("ClientPairActivity","onCreate");
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_pair_richscan_result);
		initView();
		listen();
		ivDevicePre.setImageResource(R.drawable.device_pre);
		tvDeviceId.setText(deviceId);
//		cameraManager.getDeviceName(deviceId, new WhisperAsyncProxy<String>() {
//			@Override
//			public void onSuccess(String result) {
//				if (result != null) {
//					tvDeviceId.setText(result);
//				}
//			}
//		});
	}

	private void initView() {
		rlBackToResult = (RelativeLayout) findViewById(R.id.rl_back_to_result);
		// deviceId
		tvDeviceId = (TextView) findViewById(R.id.tv_device_id);
		// 二维码扫描到的图像
		ivDevicePre = (ImageView) findViewById(R.id.iv_device_pre);
		// 匹配的密码
		etPassword = (EditText) findViewById(R.id.et_pair_password);

		btnPair = (Button) findViewById(R.id.btn_pair);

		Intent intent = getIntent();
		deviceId = intent.getExtras().getString("deviceId");
	}

	private void listen() {
		rlBackToResult.setOnClickListener(this);
		btnPair.setOnClickListener(this);
	}

	// 点击开始匹配
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.rl_back_to_result:
			finish();
			break;
		case R.id.btn_pair:
			mPassword = UiUtils.getText(etPassword);
			pairDeviceByHost();
			/*myExit();
			this.finish();*/
			break;
		default:
			break;
		}
	}

	private void myExit() {
		Intent intent = new Intent();
		intent.setAction("CloseActivity");
		this.sendBroadcast(intent);
	}

	private void pairDeviceByHost() {
		cameraManager.pair(deviceId, mPassword,
				new WhisperAsyncProxy<Boolean>() {

					@Override
					public void onSuccess(Boolean alreadyPaired) {
						if (alreadyPaired) {
							UiUtils.showToast("配对设备已存在");
						} else {
							UiUtils.showToast("配对成功");
						}

						myExit();
						ClientPairActivity.this.finish();
					}

					@Override
					public void onError(Exception exception) {
						UiUtils.showToast("配对失败");
					}
				});
	};
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
}