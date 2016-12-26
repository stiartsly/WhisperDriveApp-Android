package com.owncloud.android.device;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.telephony.TelephonyManager;
import android.util.Log;
import io.whisper.managed.core.*;
import io.whisper.managed.exceptions.WhisperException;
import io.whisper.managed.exceptions.InvalidArgumentException;
import io.whisper.managed.portforwarding.WhisperPortForwarding;

import com.owncloud.android.device.async.WhisperAsyncProxy;
import com.owncloud.android.device.async.WhisperAsyncTask;
import com.owncloud.android.MainApp;

public class WhisperDeviceManager implements WhisperHandler {

	private static final String appId  = "7sRQjDsniyuHdZ9zsQU9DZbMLtQGLBWZ78yHWgjPpTKm";
	private static final String appKey = "6tzPPAgSACJdScX79wuzMNPQTWkRLZ4qEdhLcZU6q4B9";
	private static final String apiServerUrl  = "https://192.168.3.182:8443/web/api";
	private static final String mqttServerUri = "ssl://192.168.3.182:8883";
//	private static final String apiServerUrl  = "http://192.168.3.182:8080/web/api";
//	private static final String mqttServerUri = "tcp://192.168.3.182:1883";

	private static WhisperDeviceManager deviceManager = null;
	private Whisper whisperInstance;
	private PairTask pairingTask;
	private DBOpenHelper moh;
	private SQLiteDatabase db;
	private static final String TAG = "WhisperDeviceManager";
	private ArrayList<WhisperDevice> devices = new ArrayList<WhisperDevice>();
	public WhisperDevice currentDevice;
	public String username;
	
	public WhisperDeviceManager() {
		super();
	}

	public static WhisperDeviceManager getInstance() {

		if (deviceManager == null) {
			deviceManager = new WhisperDeviceManager();
		}
		return deviceManager;
	}
	
	public List<WhisperDevice> getDevices()
	{
		return devices;
	}

	public boolean initialize() {
		if (db == null) {
			moh = new DBOpenHelper(MainApp.getAppContext(), "whisper.db", null, 1);
			db = moh.getWritableDatabase();

			String currentDeviceId;
			Cursor cursor = db.rawQuery("select * from current_device", null);
			if (cursor.getCount() == 0) {
                //db.insert("current_device", "device_id", null);
                ContentValues contentValues = new ContentValues();
                contentValues.put("device_id", "");
                db.insert("current_device", null, contentValues);
				currentDeviceId = "";
            } else {
                cursor.moveToFirst();
				username = cursor.getString(cursor.getColumnIndex("username"));
				currentDeviceId = cursor.getString(cursor.getColumnIndex("device_id"));
			}
            cursor.close();

			Cursor devicesCursor = db.rawQuery("select * from device_message", null);
			while (devicesCursor.moveToNext()) {
				WhisperDevice device = new WhisperDevice();
				device.deviceId = devicesCursor.getString(devicesCursor.getColumnIndex("device_id"));
				device.deviceName = devicesCursor.getString(devicesCursor.getColumnIndex("device_name"));
				device.localPort = devicesCursor.getInt(devicesCursor.getColumnIndex("local_port"));
				device.online = false;
				devices.add(device);

				if (device.deviceId.equals(currentDeviceId)) {
					currentDevice = device;
				}
			}
			devicesCursor.close();

			if (devices.size() > 0) {
				try {
					whisperInstance = start(username, null);
				}
				catch (WhisperException e) {
					Log.e(TAG, "Get Whisper instance error : " + e.getErrorCode());
					return false;
				}
			}
		}

		return true;
	}

	public void start(String login, String password, WhisperAsyncProxy<Void> proxy) {
		new LoginTask(proxy).execute(login, password);
	}

	private class LoginTask extends WhisperAsyncTask<String, String, Void> {

		public LoginTask(WhisperAsyncProxy<Void> proxy) {
			super(proxy);
		}

		protected void onPreExecute() {
			File file = new File(MainApp.getAppContext().getFilesDir().getAbsolutePath() + "/.whisper");
			file.delete();
		}

		@Override
		protected Void doInBackground(String... params) {
			Whisper whisper = null;
			try {
				String login = params[0];
				String password = params[1];
				whisper = start(login, password);

				synchronized (WhisperDeviceManager.this) {
					WhisperDeviceManager.this.wait(30 * 1000);
					if (whisperInstance == whisper) {
						username = login;
						ContentValues contentValues = new ContentValues();
						contentValues.put("username", login);
						db.update("current_device", contentValues, null, null);
					}
					else {
						_exceptionToBeThrown = new WhisperException(0, "Timeout");
						whisper.kill();
					}
				}
			} catch (WhisperException e) {
				Log.e(TAG, "Get Whisper instance error : " + e.getErrorCode());
				_exceptionToBeThrown = e;
			} catch (InterruptedException e) {
				Log.e(TAG, "wait interrupted");
				_exceptionToBeThrown = e;
				whisper.kill();
			}

			return null;
		}
	}

	private Whisper start(String login, String password) throws WhisperException {
		java.net.URL abpath = getClass().getResource("/assets/whisper.pem");
		Log.d(TAG, "whisper.pem url : " + abpath);
		File certFile = new File(MainApp.getAppContext().getFilesDir().getAbsolutePath(), "whisper.pem");
		if (!certFile.exists()) {
			try {
				InputStream is = MainApp.getAppContext().getAssets().open("whisper.pem");
				FileOutputStream fos = new FileOutputStream(certFile);
				byte[] buffer = new byte[1024*1024];
				int count;
				while ((count = is.read(buffer)) > 0) {
					fos.write(buffer, 0, count);
				}
				fos.close();
				is.close();
			} catch (IOException e) {
				Log.e(TAG, "Error copying whisper.pem to " + certFile, e);
				e.printStackTrace();
			}
		}

		String deviceId = ((TelephonyManager)MainApp.getAppContext().getSystemService(
				MainApp.getAppContext().TELEPHONY_SERVICE)).getDeviceId();

		WhisperOptions options = new WhisperOptions();
		options.setAppId(appId);
		options.setAppKey(appKey);
		options.setApiServerUrl(apiServerUrl);
		options.setMqttServerUri(mqttServerUri);
		options.setTrustStore(certFile.getAbsolutePath());
		options.setPersistentLocation(MainApp.getAppContext().getFilesDir().getAbsolutePath());
		options.setDeviceId(deviceId);
		options.setLogin(login);
		options.setPassword(password);
		options.setConnectTimeout(5);
		options.setRetryInterval(1);

		Log.i(TAG, "Ready to get whisper instance");
		Whisper whisper = Whisper.getInstance(options, WhisperDeviceManager.this, null);
		Log.i(TAG, "Whisper instance created");

		whisper.start(1000);
		Log.i(TAG, "Whisper is running now");

		return whisper;
	}

	private class SetClientNameTask extends WhisperAsyncTask<String, Void, Boolean> {

		public SetClientNameTask(WhisperAsyncProxy<Boolean> proxy) {
			super(proxy);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			try {
				if (params[0] == null) {
					throw new InvalidArgumentException();
				}

				WhisperNodeInfo nodeInfo = whisperInstance.getNodeInfo();
				nodeInfo.setName(params[0]);
				whisperInstance.setNodeInfo(nodeInfo);
				return true;
			} catch (WhisperException e) {
				_exceptionToBeThrown = e;
				e.printStackTrace();
				return false;
			}
		}
	}
	
	public void setClientName(String clientName, WhisperAsyncProxy<Boolean> proxy) {
		new SetClientNameTask(proxy).execute(clientName);
	}
	
	private class PairTask extends WhisperAsyncTask<Object, Void, Boolean> {
		public String deviceId;
		public String password;
		public WhisperException pairResult;

		public PairTask(WhisperAsyncProxy<Boolean> proxy) {
			super(proxy);
		}

		@Override
		protected Boolean doInBackground(Object... params) {
			Boolean alreadyPaired = false;
			try {
				if (params[0] == null || params[1] == null
						|| !(params[0] instanceof String)
						|| !(params[1] instanceof String)) {
					throw new InvalidArgumentException();
				}
				deviceId = (String) params[0];
				password = (String) params[1];

				alreadyPaired = whisperInstance.isFriend(deviceId);
				if (!alreadyPaired) {
					whisperInstance.friendRequest(deviceId, password);

					synchronized (this) {
						wait(10 * 1000);
						if (pairResult == null) {
							_exceptionToBeThrown = new WhisperException(0, "Timeout");
						}
						else if (pairResult.getErrorCode() != 0) {
							_exceptionToBeThrown = pairResult;
						}
					}
				}
			} catch (WhisperException e) {
				_exceptionToBeThrown = e;
				e.printStackTrace();
			} catch (InterruptedException e) {
				_exceptionToBeThrown = e;
				Log.e(TAG, "wait interrupted");
			}

			return alreadyPaired;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			pairingTask = null;

			if (_exceptionToBeThrown == null && (currentDevice == null || !currentDevice.deviceId.equals(deviceId))) {
				for (WhisperDevice device : devices) {
					if (device.deviceId.equals(deviceId)) {
						device.connect(true);
						setCurrentDevice(device);
						break;
					}
				}
			}

			super.onPostExecute(result);
		}
	}

	/**
	 * 客户端匹配设备
	 * 
	 * @param deviceId
	 *            设备id
	 * @param password
	 *            匹配设备所需要的密码
	 */
	public void pair(String deviceId, String password, WhisperAsyncProxy<Boolean> proxy) {
		pairingTask = new PairTask(proxy);
		pairingTask.execute(deviceId, password);
	}

	private class UnPairTask extends WhisperAsyncTask<String, Void, Void> {
		private String deviceId;

		public UnPairTask(WhisperAsyncProxy<Void> proxy) {
			super(proxy);
		}

		@Override
		protected Void doInBackground(String... params) {
			try {
				if (params[0] == null || !(params[0] instanceof String)) {
					throw new InvalidArgumentException();
				}
				deviceId = params[0];
				whisperInstance.friendRemove(deviceId);
			} catch (WhisperException e) {
				_exceptionToBeThrown = e;
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			if (_exceptionToBeThrown == null) {
				for (int i = 0; i < devices.size(); i++) {
					WhisperDevice device = devices.get(i);
					if (device.deviceId.equals(deviceId)) {
						device.disconnect();
						devices.remove(i);
						db.delete("device_message", "device_id=?", new String[] { deviceId });
						
						if (device == currentDevice) {
							if (devices.isEmpty()) {
								setCurrentDevice(null);
							}
							else {
								setCurrentDevice(devices.get(0));
							}
						}
						break;
					}
				}
			}
			
			super.onPostExecute(result);
		}
	}
	public void unPair(WhisperDevice device, WhisperAsyncProxy<Void> proxy) {
		new UnPairTask(proxy).execute(device.deviceId);
	}
	
	public void getDevices(WhisperAsyncProxy<List<WhisperDevice>> proxy) {
		try {
			List<WhisperFriendInfo> list  = whisperInstance.getFriends();
			ArrayList<WhisperDevice> temp = new ArrayList<WhisperDevice>(devices);
			for (WhisperFriendInfo deviceInfo : list) {
				String deviceId = deviceInfo.getUserInfo().getUserId();
				String deviceName = deviceInfo.getLabel();
				if (deviceName == null || deviceName.length() == 0) {
					deviceName = deviceInfo.getUserInfo().getName();
				}

				WhisperDevice device = null;
				for (int i = 0; i < temp.size(); i++) {
					WhisperDevice oldDevice = temp.get(i);
					if (oldDevice.deviceId.equals(deviceId)) {
						device = oldDevice;
						temp.remove(i);
						break;
					}
				}

				if (device == null) {
					device = new WhisperDevice();
					device.deviceId = deviceId;
					devices.add(device);

					ContentValues contentValues = new ContentValues();
					contentValues.put("device_id", deviceId);
					contentValues.put("device_name", deviceName);
					contentValues.put("local_port", 0);
					db.insert("device_message", null, contentValues);
				}

				device.deviceName = deviceName;
				device.online = deviceInfo.getPresence().equals("online");

				if (device.online) {
					device.connect();
				}
			}

			if (temp.size() > 0) {
				devices.removeAll(temp);

				for (WhisperDevice device : temp) {
					device.disconnect();
					db.delete("device_message", "device_id=?", new String[] { device.deviceId });
				}

				if (temp.contains(currentDevice)) {
					setCurrentDevice(null);
				}
			}

			if (currentDevice == null && devices.size() > 0) {
				setCurrentDevice(devices.get(0));
			}

			if (proxy != null) {
				proxy.onSuccess(devices);
			}

		} catch (WhisperException e) {
			e.printStackTrace();
			if (proxy != null) {
				proxy.onError(e);
			}
		}
	}

	public void setCurrentDevice(WhisperDevice device) {
		if (device == null && currentDevice != null) {
			currentDevice = null;

			ContentValues contentValues = new ContentValues();
			contentValues.put("device_id", "");
			db.update("current_device", contentValues, null, null);
		}
		else if (device != null && device != currentDevice && devices.contains(device)) {
			currentDevice = device;
			currentDevice.connect();

			ContentValues contentValues = new ContentValues();
			contentValues.put("device_id", device.deviceId);
			db.update("current_device", contentValues, null, null);
		}
	}
	
	private class SetDeviceNameTask extends WhisperAsyncTask<String, Void, Boolean> {
		private String deviceId;  
		private String deviceName;

		public SetDeviceNameTask(WhisperAsyncProxy<Boolean> proxy) {
			super(proxy);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			try {
				if (params[0] == null || params[1] == null
						|| !(params[0] instanceof String)
						|| !(params[1] instanceof String)) {
					throw new InvalidArgumentException();
				}
				deviceId = params[0];
				deviceName = params[1];
				whisperInstance.setFriendLabel(deviceId, deviceName);
				return true;
			} catch (WhisperException e) {
				_exceptionToBeThrown = e;
				e.printStackTrace();
				return false;
			}
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				db.execSQL(
						"update device_message set device_name=? where device_id=?",
						new Object[] { deviceName, deviceId });
			}
			
			super.onPostExecute(result);
		}
	}
	
	public void setDeviceName(String deviceId, String deviceName, WhisperAsyncProxy<Boolean> proxy) {
		new SetDeviceNameTask(proxy).execute(deviceId, deviceName);
	}

	public void stop() {
		for (WhisperDevice device : devices) {
			device.disconnect();
		}
		db.delete("device_message", null, null);
		devices.clear();
		setCurrentDevice(null);

		WhisperPortForwarding pfInstance = WhisperPortForwarding.getInstance();
		if (pfInstance != null) {
			pfInstance.cleanup();
		}

		whisperInstance.kill();
		whisperInstance = null;

		File file = new File(MainApp.getAppContext().getFilesDir().getAbsolutePath() + "/.whisper");
		file.delete();
	}

	@Override
	public void onIdle(Whisper w, Object context) {
	}

	@Override
	public void onConnection(Whisper w, WhisperConnection status, Object context) {
		Log.i(TAG, "OnConnection:" + status);

//		if (status == WhisperConnection.Connected) {
//			getDevices(null);
//		} else if (status == WhisperConnection.Disconnected) {
//			for (WhisperDevice device : devices) {
//				device.online = false;
//				device.disconnect();
//			}
//		}
	}

	@Override
	public void onReady(Whisper w, Object context) {
		Log.i(TAG, "onReady emitted");
		synchronized (this) {
			whisperInstance = w;

			String manufacturer = android.os.Build.MANUFACTURER;
			String model = android.os.Build.MODEL;
			if (!model.startsWith(manufacturer)) {
				model = manufacturer + " " + model;
			}
			if (model.length() > 0) {
				if (model.length() > 32) {
					model = model.substring(0, 31);
				}
				setClientName(model, null);
			}

			getDevices(null);

			notify();
		}
	}

	@Override
	public void onSelfInfoChanged(Whisper w, WhisperUserInfo userInfo, Object context) {
		Log.i(TAG, "onSelfInfoChanged:" + userInfo);
	}

	@Override
	public boolean onFriendsIterated(Whisper w, WhisperFriendInfo friendInfo, Object context) {
		Log.i(TAG, "onFriendsIterated:" + friendInfo);
		return true;
	}

	@Override
	public void onFriendInfoChanged(Whisper w, String friendId, WhisperFriendInfo friendInfo,
									Object context) {
		Log.i(TAG, "onFriendInfoChanged:" + friendInfo);
	}

	@Override
	public void onFriendPresenceChanged(Whisper w, String friendId, String presence,
										Object context) {
		Log.i(TAG, "onFriendPresenceChanged: friendId:" + friendId + ",Presence:" + presence);
		for (WhisperDevice device : devices) {
			if (device.deviceId.equals(friendId)) {
				device.online = presence.equals("online");
				if (device.online) {
					device.connect();
				} else {
					device.disconnect();
				}
			}
		}
	}

	@Override
	public void onFriendAdded(Whisper w, WhisperFriendInfo friendInfo, Object context) {
		Log.i(TAG, "onFriendAdded:" + friendInfo);
	}

	@Override
	public void onFriendRemoved(Whisper w, String friendId, Object context) {
		Log.i(TAG, "onFriendRemoved:" + friendId);

		for (int i = 0; i < devices.size(); i++) {
			WhisperDevice device = devices.get(i);
			if (device.deviceId.equals(friendId)) {
				device.disconnect();
				devices.remove(i);
				db.delete("device_message", "device_id=?", new String[] { friendId });

				if (device == currentDevice) {
					if (devices.isEmpty()) {
						setCurrentDevice(null);
					}
					else {
						setCurrentDevice(devices.get(0));
					}
				}
				break;
			}
		}
	}

	@Override
	public boolean onFriendRequest(Whisper w, String userId, WhisperUserInfo userInfo,
								String hello, Object context) {
		Log.i(TAG, "onFriendRequest:" + "from:" + userId +
				", with info:" + userInfo + "and mesaage:" + hello);
		return true;
	}

	@Override
	public boolean onFriendResponse(Whisper w, String userId, int status, String reason,
								 int entrusted, WhisperFriendInfo friendInfo, Object context) {
		Log.i(TAG, "onFriendResponse: from:" + userId);
		if (status == 0) {
			Log.i(TAG, "Friend request acknowleged with entrusted mode " + entrusted + ")");
			Log.i(TAG, "Remote friend information:" + friendInfo);

			WhisperDevice device = null;
			for (WhisperDevice oldDevice : devices) {
				if (oldDevice.deviceId.equals(userId)) {
					device = oldDevice;
				}
			}

			if (device == null) {
				device = new WhisperDevice();
				device.deviceId = userId;
				devices.add(device);

				ContentValues contentValues = new ContentValues();
				contentValues.put("device_id", userId);
				contentValues.put("device_name", friendInfo.getUserInfo().getName());
				contentValues.put("local_port", 0);
				db.insert("device_message", null, contentValues);
			}

			device.deviceName = friendInfo.getUserInfo().getName();
			device.online = true;
		} else {
			Log.i(TAG, "Friend request rejected with reason (" + reason + ")");
		}

		if (pairingTask != null && userId.equals(pairingTask.deviceId)) {
			synchronized (pairingTask) {
				pairingTask.pairResult = new WhisperException(status, reason);
				pairingTask.notify();
			}
		}

		return true;
	}

	@Override
	public boolean onFriendMessage(Whisper w, String friendId, String message, Object context) {
		Log.i(TAG, "onFriendMessage: from:" + friendId + " with message:" + message);
		return true;
	}

	@Override
	public boolean onFriendInviteRequest(Whisper w, String friendId, String hello, Object context) {
		Log.i(TAG, "OnFrieInvite request from (" + friendId + ") with hell message (" + hello + ")");
		return true;
	}
}
