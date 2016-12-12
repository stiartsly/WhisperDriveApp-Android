package com.owncloud.android.device.async;

import android.os.AsyncTask;
/**
 * 寮傛鎿嶄綔浠诲姟
 */
public abstract class WhisperAsyncTask<Params, Progress, Result> extends
		AsyncTask<Params, Progress, Result>  {
	
	private WhisperAsyncProxy<Result> _proxy = null;
	protected Exception _exceptionToBeThrown = null;
	
	/**
	 * 寮傛鎿嶄綔浠诲姟
	 * @param proxy 寮傛妯″紡瀵硅薄
	 */
	public WhisperAsyncTask(WhisperAsyncProxy<Result> proxy) {
		_proxy = proxy;
	}

	protected WhisperAsyncProxy<Result> getProxy() {
		return _proxy;
	}

	/**
	 * 寮傛鎿嶄綔鎵ц鍚庤繑鍥炵粨鏋滐紝杩斿洖閿欒鍘熷洜鎴栬�呮垚鍔熺殑缁撴灉
	 * @param result 寮傛浠诲姟鐨勮繑鍥炲��
	 */
	@Override
	protected void onPostExecute(Result result) {
		if (_proxy == null) {
			return;
		}
		
		if (_exceptionToBeThrown != null) {
			_proxy.onError(_exceptionToBeThrown);
			return;
		}
		_proxy.onSuccess(result);
	}

}
