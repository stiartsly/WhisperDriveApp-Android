package com.owncloud.android.device;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.owncloud.android.R;
import com.owncloud.android.device.async.WhisperAsyncProxy;

import java.util.List;

public class DeviceListActivity extends Activity implements OnClickListener {
    ListView listView;
    DeviceListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        findViewById(R.id.rl_back).setOnClickListener(this);
//        findViewById(R.id.iv_add_device).setOnClickListener(this);

        listView = (ListView) findViewById(R.id.list);
        adapter = new DeviceListAdapter(this);
        listView.setAdapter(adapter);
        //listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                WhisperDeviceManager deviceManager = WhisperDeviceManager.getInstance();
                List<WhisperDevice> devices = deviceManager.getDevices();
                WhisperDevice device = devices.get(position);
                deviceManager.setCurrentDevice(device);
                adapter.notifyDataSetChanged();
//                listView.setItemChecked(position, true);
            }
        });

//        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
//
//            @Override
//            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
//                final WhisperDeviceManager deviceManager = WhisperDeviceManager.getInstance();
//                final List<WhisperDevice> devices = deviceManager.getDevices();
//                final WhisperDevice device = devices.get(position);
//
//                AlertDialog.Builder builder = new AlertDialog.Builder(DeviceListActivity.this);
//                builder.setTitle("确定要删除此设备？");
//                builder.setPositiveButton(R.string.common_remove, new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        deviceManager.unPair(device, new WhisperAsyncProxy<Void>() {
//                            @Override
//                            public void onSuccess(Void result) {
//                                adapter.notifyDataSetChanged();
//                            }
//
//                            @Override
//                            public void onError(Exception exception) {
//                                UiUtils.showToast("删除失败");
//                            }
//                        });
//                    }
//                });
//                builder.setNegativeButton(R.string.common_cancel, null);
//                builder.show();
//                return true;
//            }
//        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rl_back:
                finish();
                break;

//            case R.id.iv_add_device:
//                Intent intent = new Intent(DeviceListActivity.this, MipcaActivityCapture.class);
//                startActivity(intent);
//                break;

            default:
                break;
        }
    }

    private class DeviceListAdapter extends BaseAdapter {
        List<WhisperDevice> devices = WhisperDeviceManager.getInstance().getDevices();
        private LayoutInflater mInflater;

        public DeviceListAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            WhisperDevice device = devices.get(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.simple_list_item_2_single_choice, null);
            }

            TextView deviceNameView = (TextView) convertView.findViewById(R.id.text1);
            TextView deviceIdView = (TextView) convertView.findViewById(R.id.text2);
            RadioButton selectedButton = (RadioButton) convertView.findViewById(R.id.radio);

            deviceNameView.setText(device.deviceName);
            deviceIdView.setText(device.deviceId);
            selectedButton.setChecked(device.equals(WhisperDeviceManager.getInstance().currentDevice));

            return convertView;
        }
    }
}
