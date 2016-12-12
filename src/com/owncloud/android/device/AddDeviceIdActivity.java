package com.owncloud.android.device;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.owncloud.android.R;

@SuppressLint("NewApi")
public class AddDeviceIdActivity extends Activity implements OnClickListener {
	private RelativeLayout rlBackToDevice;
	private Button btnOk;
	private EditText etDeviceId;
	private String deviceId;
	private boolean isClickable = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_pair_manual_input);
		initView();
		listen();
		etDeviceId.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (!getText(etDeviceId).isEmpty()) {
					btnOk.setBackground(getResources().getDrawable(
							R.drawable.shape_red_corners_button_pressed));
					isClickable = true;
				} else {
					btnOk.setBackground(getResources().getDrawable(
							R.drawable.shape_red_corners_button));
					isClickable = false;
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});
	}

	private String getText(EditText editText) {
		return editText.getText().toString();
	}

	public void initView() {
		rlBackToDevice = (RelativeLayout) findViewById(R.id.rl_back_to_device);
		etDeviceId = (EditText) findViewById(R.id.et_device_id);
		//etDeviceId.setText("d41145fc02a43852b7da0992bdb11277");
		btnOk = (Button) findViewById(R.id.btn_ok);
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			AddDeviceIdActivity.this.finish();
		};
	};

	@Override
	protected void onResume() {
		IntentFilter filter = new IntentFilter();
		filter.addAction("CloseActivity");
		this.registerReceiver(receiver, filter);
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		this.unregisterReceiver(receiver);
		super.onDestroy();
	}

	private void listen() {
		rlBackToDevice.setOnClickListener(this);
		btnOk.setOnClickListener(this);
	}

	public void showToast(String str) {
		Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.rl_back_to_device:
			finish();
			break;
		case R.id.btn_ok:
			if (isClickable) {
				deviceId = getText(etDeviceId);
				if (deviceId.length() != 32) {
					Log.e("deviceId", deviceId.length() + "");
					showToast("序列号不合法");
				} else {
					Intent intent = new Intent(this, ClientPairActivity.class);
					intent.putExtra("deviceId", deviceId);
					startActivity(intent);
				}
			}
			break;
		default:
			break;
		}
	}
}
