package com.owncloud.android.device;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.owncloud.android.R;
import com.owncloud.android.device.async.WhisperAsyncProxy;

import io.whisper.managed.exceptions.WhisperException;

public class WhisperLoginActivity extends AppCompatActivity {

    private EditText mUsernameInput;
    private EditText mPasswordInput;
    private View mOkButton;

    private TextView mAuthStatusView;
    private int mAuthStatusText = 0, mAuthStatusIcon = 0;

    private TextWatcher mTextInputWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();

            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        setContentView(R.layout.activity_whisper_login);

        mUsernameInput = (EditText) findViewById(R.id.account_username);
        mPasswordInput = (EditText) findViewById(R.id.account_password);
        mAuthStatusView = (TextView) findViewById(R.id.auth_status_text);
        mOkButton = findViewById(R.id.buttonOK);
        mOkButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onOkClick();
            }
        });

        String username = WhisperDeviceManager.getInstance().username;
        if (username != null) {
            mUsernameInput.setText(username);
            mPasswordInput.requestFocus();
        }

        mPasswordInput.setText("");
        showAuthStatus();

        mTextInputWatcher = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                boolean inputed = mUsernameInput.getText().toString().length() > 0 &&
                        mPasswordInput.getText().toString().length() > 0;
                mOkButton.setEnabled(inputed);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAuthStatusIcon != 0) {
                    mAuthStatusIcon = 0;
                    mAuthStatusText = 0;
                    showAuthStatus();
                }
            }
        };
    }
    @Override
    protected void onResume() {
        super.onResume();

        mUsernameInput.addTextChangedListener(mTextInputWatcher);
        mPasswordInput.addTextChangedListener(mTextInputWatcher);
    }

    @Override
    protected void onPause() {
        mUsernameInput.removeTextChangedListener(mTextInputWatcher);
        mPasswordInput.removeTextChangedListener(mTextInputWatcher);

        super.onPause();
    }

    @Override
    public void onBackPressed() {
    }

    public void onOkClick() {
        mAuthStatusIcon = R.drawable.progress_small;
        mAuthStatusText = R.string.auth_trying_to_login;
        showAuthStatus();
        mOkButton.setEnabled(false);

        WhisperDeviceManager.getInstance().start(
                mUsernameInput.getText().toString(),
                mPasswordInput.getText().toString(),
                new WhisperAsyncProxy<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                        if (WhisperDeviceManager.getInstance().getDevices().size() > 0) {
                            WhisperLoginActivity.this.finish();
                        }
                        else {
                            WhisperDeviceManager.getInstance().stop();
                            mAuthStatusIcon = R.drawable.common_error;
                            mAuthStatusText = 0;
                            showAuthStatus();
                            mOkButton.setEnabled(true);
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        mAuthStatusIcon = R.drawable.common_error;
                        mAuthStatusText = R.string.auth_oauth_error;
//                        if (exception instanceof WhisperException &&
//                                ((WhisperException)exception).getErrorCode() == -10029) {
//                            mAuthStatusText = R.string.auth_unauthorized;
//                        }
//                        else {
//                            mAuthStatusText = R.string.auth_can_not_auth_against_server;
//                        }
                        showAuthStatus();
                        mOkButton.setEnabled(true);
                    }
                });
    }

    private void showAuthStatus() {
        if (mAuthStatusIcon == 0 && mAuthStatusText == 0) {
            mAuthStatusView.setVisibility(View.INVISIBLE);
        } else {
            if (mAuthStatusText == 0) {
                mAuthStatusView.setText("此账号没有绑定owncloud服务器设备");
            }
            else {
                mAuthStatusView.setText(mAuthStatusText);
            }
            mAuthStatusView.setCompoundDrawablesWithIntrinsicBounds(mAuthStatusIcon, 0, 0, 0);
            mAuthStatusView.setVisibility(View.VISIBLE);
        }
    }
}
