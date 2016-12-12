package com.owncloud.android.device;

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

import com.owncloud.android.device.async.WhisperAsyncProxy;
import com.owncloud.android.device.async.WhisperAsyncTask;
import com.owncloud.android.MainApp;

public class WhisperDeviceManager implements WhisperHandler {

	private static final String appId  = "7sRQjDsniyuHdZ9zsQU9DZbMLtQGLBWZ78yHWgjPpTKm";
	private static final String appKey = "6tzPPAgSACJdScX79wuzMNPQTWkRLZ4qEdhLcZU6q4B9";
	private static final String apiServerUrl  = "http://192.168.3.182:8080/web/api";
	private static final String mqttServerUri = "tcp://192.168.3.182:1883";

	private static WhisperDeviceManager deviceManager = null;
	private Whisper whisperInstance;
	private Thread whisperThread;
	private PairTask pairingTask;
	private DBOpenHelper moh;
	private SQLiteDatabase db;
	private static final String TAG = "WhisperDeviceManager";
	private ArrayList<WhisperDevice> devices = new ArrayList<WhisperDevice>();
	public WhisperDevice currentDevice;
	
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
		}

		try {
//			File file = new File(MainApp.getAppContext().getFilesDir().getAbsolutePath() + "/.whisper");
//			file.delete();

			String deviceId = ((TelephonyManager)MainApp.getAppContext().getSystemService(
					MainApp.getAppContext().TELEPHONY_SERVICE)).getDeviceId();

			WhisperOptions options = new WhisperOptions();
			options.setAppId(appId);
			options.setAppKey(appKey);
			options.setApiServerUrl(apiServerUrl);
			options.setMqttServerUri(mqttServerUri);
			options.setPersistentLocation(MainApp.getAppContext().getFilesDir().getAbsolutePath());
			options.setDeviceId(deviceId);
			options.setLogin("stiartsly@gmail.com");
			options.setPassword("password");

			Log.i(TAG, "Ready to get whisper instance");
			whisperInstance = Whisper.getInstance(options, this, null);
			Log.i(TAG, "Whisper instance created");

			whisperThread = new Thread(whisperInstance);
			whisperThread.start();
			Log.i(TAG, "Whisper is running now");
		} catch (WhisperException e) {
			Log.e(TAG, "Get Whisper instance error" + e.getErrorCode());
			return false;
		}
		
		return true;
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
						currentDevice = device;
						currentDevice.connect(true);

						ContentValues contentValues = new ContentValues();
						contentValues.put("device_id", deviceId);
						db.update("current_device", contentValues, "", null);

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

	private class UnPairTask extends WhisperAsyncTask<String, Void, Boolean> {
		private String deviceId;

		public UnPairTask(WhisperAsyncProxy<Boolean> proxy) {
			super(proxy);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			try {
				if (params[0] == null || !(params[0] instanceof String)) {
					throw new InvalidArgumentException();
				}
				deviceId = params[0];
				whisperInstance.friendRemove(deviceId);
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
				for (int i = 0; i < devices.size(); i++) {
					WhisperDevice device = devices.get(i);
					if (device.deviceId.equals(deviceId)) {
						devices.remove(i);
						db.delete("device_message", "device_id=?", new String[] { deviceId });
						
						if (device == currentDevice) {
							if (devices.isEmpty()) {
								currentDevice = null;

                                ContentValues contentValues = new ContentValues();
                                contentValues.put("device_id", "");
                                db.update("current_device", contentValues, "", null);
							}
							else {
								currentDevice = devices.get(0);
                                currentDevice.connect();

                                ContentValues contentValues = new ContentValues();
                                contentValues.put("device_id", currentDevice.deviceId);
                                db.update("current_device", contentValues, "", null);
							}
						}
						break;
					}
				}
			}
			
			super.onPostExecute(result);
		}
	}
	public void unPair(String deviceId, WhisperAsyncProxy<Boolean> proxy) {
		new UnPairTask(proxy).execute(deviceId);
	}
	
	private class GetDevicesTask extends WhisperAsyncTask<Void, Void, List<WhisperDevice>> {
		private List<WhisperFriendInfo> list = null;

		public GetDevicesTask(WhisperAsyncProxy<List<WhisperDevice>> proxy) {
			super(proxy);
		}

		@Override
		protected List<WhisperDevice> doInBackground(Void... params) {
			try {
				list  = whisperInstance.getFriends();
			} catch (WhisperException e) {
				_exceptionToBeThrown = e;
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(List<WhisperDevice> result) {
			if (_exceptionToBeThrown == null) {
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
						db.delete("device_message", "device_id=?", new String[] { device.deviceId });
					}

					if (temp.contains(currentDevice)) {
						currentDevice = null;

                        ContentValues contentValues = new ContentValues();
                        contentValues.put("device_id", "");
                        db.update("current_device", contentValues, "", null);
					}
				}

				if (currentDevice == null && devices.size() > 0) {
					currentDevice = devices.get(0);
                    currentDevice.connect();

                    ContentValues contentValues = new ContentValues();
                    contentValues.put("device_id", currentDevice.deviceId);
                    db.update("current_device", contentValues, "", null);
				}
			}
			
			super.onPostExecute(devices);
		}
	}
	
	public void getDevices(WhisperAsyncProxy<List<WhisperDevice>> proxy) {
		new GetDevicesTask(proxy).execute();
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

	public void stopClient() {
		try {
			whisperInstance.kill();
			whisperThread.join();
		}catch(Exception e) {
			Log.e(TAG, "whisperThread supposed to join, but interrupted");
		}
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
				devices.remove(i);
				db.delete("device_message", "device_id=?", new String[] { friendId });

				if (device == currentDevice) {
					if (devices.isEmpty()) {
						currentDevice = null;

						ContentValues contentValues = new ContentValues();
						contentValues.put("device_id", "");
						db.update("current_device", contentValues, "", null);
					}
					else {
						currentDevice = devices.get(0);
						currentDevice.connect();

						ContentValues contentValues = new ContentValues();
						contentValues.put("device_id", currentDevice.deviceId);
						db.update("current_device", contentValues, "", null);
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
