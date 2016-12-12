package com.owncloud.android.device;

import com.owncloud.android.MainApp;

import io.whisper.managed.core.Whisper;
import io.whisper.managed.exceptions.WhisperException;
import io.whisper.managed.portforwarding.WhisperPortForwarding;
import io.whisper.managed.portforwarding.WhisperPortForwardingOpenCompleteHandler;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WhisperDevice {
	static private DBOpenHelper moh;
	static private SQLiteDatabase db;

	public String deviceId;
	public String deviceName;
	public boolean online;
	public int localPort;
	
	private ConnectTask connectTask;
//	private int sessionId;
	private int portFordwordId;

	public boolean connect()
	{
		return connect(false);
	}

	public boolean connect(boolean allowUpdatePort)
	{
//		if (sessionId > 0 && portFordwordId > 0)
		if (portFordwordId > 0)
			return true;

		if (!online)
			return false;

		if (connectTask == null) {
			connectTask = new ConnectTask();
			connectTask.allowUpdatePort = allowUpdatePort;
			connectTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

		return false;
	}
	
	public void disconnect()
	{
//		if (sessionId > 0) {
//			new DisconnectTask()..executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, sessionId, portFordwordId);
//		}
		if (portFordwordId > 0) {
			new DisconnectTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, portFordwordId);
		}

//		sessionId = 0;
		portFordwordId = 0;
	}
	
//	private Handler mainHandler = new MainHandler();
//	private class MainHandler extends Handler {
//
//		public MainHandler(){
//			super(Looper.getMainLooper());
//		}
//
//        @Override
//        public void handleMessage(Message msg) {
//			super.handleMessage(msg);
//        	switch (msg.what) {
//            case 1:
//            	onSessionReceivedData(msg.arg1, msg.arg2, (byte[]) msg.obj);
//                break;
//            case 2:
//            	onSessionClosed(msg.arg1, msg.arg2);
//                break;
//            default:
//                break;
//            }
//        }
//	};
	
	private class ConnectTask extends AsyncTask<Void, Void, Boolean> {
		boolean allowUpdatePort = false;
		private ReentrantLock lock;
		private Condition condition;

		@Override
		protected Boolean doInBackground(Void... params) {
//			if (sessionId == 0) {vv
//				int result = SessionManager.getInstance().connect(deviceId, "", 3, new PeerSessionHandler() {
//					@Override
//					public void onSessionReceivedData(int sessionId, int channelId, byte[] data)
//					{
//						Message msg = new Message();
//						msg.what = 1;
//						msg.arg1 = sessionId;
//						msg.arg2 = channelId;
//						msg.obj = data;
//						mainHandler.sendMessage(msg);
//					}
//
//					@Override
//					public void onSessionClosed(int sessionId, int status)
//					{
//						Message msg = new Message();
//						msg.what = 2;
//						msg.arg1 = sessionId;
//						msg.arg2 = status;
//						mainHandler.sendMessage(msg);
//					}
//				});
//
//				if (result <= 0) {
//					Log.e("ConnectTask", "connect failed");
//					return null;
//				} else {
//					sessionId = result;
//					portFordwordId = 0;
//					Log.d("ConnectTask", "Session mode: " + SessionManager.getInstance().getSessionInfo(sessionId).getMode());
//				}
//			}

			if (portFordwordId == 0) {
				final int port;
				if (localPort > 0) {
					if (isPortAvailable(localPort)) {
						port = localPort;
					}
					else if (allowUpdatePort) {
						port = findFreePort();
						if (port <= 0) {
							return false;
						}
					}
					else {
						return false;
					}
				} else {
					port = findFreePort();
					if (port <= 0) {
						return false;
					}
				}

				try {
					lock = new ReentrantLock();
					condition = lock.newCondition();
					lock.lock();

					WhisperPortForwarding pfInstance = WhisperPortForwarding.getInstance(Whisper.getInstance());
					Log.d("ConnectTask", "port forwarding instance created");

					pfInstance.requestOpen(deviceId, "owncloud", "127.0.0.1", String.valueOf(port), new WhisperPortForwardingOpenCompleteHandler() {
						@Override
						public void onSuccess(int pfId, Object context) {
							Log.d("ConnectTask", "Open port forwarding success, local port : " + port);
							if (localPort != port) {
								localPort = port;

//								ContentValues contentValues = new ContentValues();
//								contentValues.put("local_port", localPort);
//								db.update("device_message", contentValues, "device_id=?",
//										new String[] { deviceId });
								db.execSQL("update device_message set local_port=? where device_id=?",
										new Object[] { localPort, deviceId });
							}
							portFordwordId = pfId;

							lock.lock();
							condition.signal();
							lock.unlock();
						}

						@Override
						public void onError(String reason, Object context) {
							Log.e("ConnectTask", "Open port forwarding error : " + reason);
							lock.lock();
							condition.signal();
							lock.unlock();
						}
					}, this);

					condition.await(10, TimeUnit.SECONDS);
				} catch (WhisperException e) {
					Log.e("ConnectTask", "open port forwarding failed");
					e.printStackTrace();
				} catch (InterruptedException e) {
					Log.e("ConnectTask", "Condition interrupted, should waiting until connected event received");
				} finally {
					lock.unlock();
				}
			}

			return portFordwordId > 0;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			connectTask = null;
			if (result) {
				Intent intent = new Intent();
				intent.setAction("WhisperDeviceConnected");
				intent.putExtra("deviceId", WhisperDevice.this.deviceId);
				MainApp.getAppContext().sendBroadcast(intent);
			}
			else {
			}
		}
	}
	
	private class DisconnectTask extends AsyncTask<Integer, Void, Void> {

		@Override
		protected Void doInBackground(Integer... params) {
			if (params[0] > 0) {
				try {
					WhisperPortForwarding.getInstance().close(params[0]);
				} catch (WhisperException e) {
					e.printStackTrace();
				}
			}
//			SessionManager.getInstance().closeSession(params[0]);
			return null;
		}
	}

//	public void onSessionReceivedData(int sessionId, int channelId, byte[] data)
//	{
//		if (sessionId != this.sessionId) {
//			return;
//		}
//	}
//
//	public void onSessionClosed(int sessionId, int status)
//	{
//		if (sessionId != this.sessionId) {
//			return;
//		}
//
//		this.sessionId = 0;
//	}

	static private boolean isPortAvailable(int port) {
		try {
			new ServerSocket(port).close();
			return true;
		}
		catch (Exception e) {
			Log.i("ConnectTask", "Port" + port + "is not available");
			return false;
		}
	}

	static private int findFreePort() {
		if (db == null) {
			moh = new DBOpenHelper(MainApp.getAppContext(), "whisper.db", null, 1);
			db = moh.getWritableDatabase();
		}

		int port;
		Cursor cursor = db.rawQuery("SELECT MAX(local_port) FROM device_message", null);
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			port = cursor.getInt(0);
			if (port <= 0) {
				port = 40000;
			}
			else {
				port++;
			}
		}
		else {
			port = 40000;
		}
		cursor.close();

		while (port < 65536) {
			if (isPortAvailable(port)) {
				return port;
			}
			port++;
		}

		return 0;
	}
}
