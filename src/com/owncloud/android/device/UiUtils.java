package com.owncloud.android.device;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.owncloud.android.MainApp;

public class UiUtils {

	public static String stringFilter(String str) throws PatternSyntaxException {
		// ֻ������ĸ�����ֺͺ���
		String regEx = "[^a-zA-Z0-9\u4E00-\u9FA5]";
		Pattern p = Pattern.compile(regEx);
		Matcher m = p.matcher(str);
		return m.replaceAll("").trim();
	}

	public static void showToast(String str) {
		Toast.makeText(MainApp.getAppContext(), str, Toast.LENGTH_SHORT).show();
	}
	public static String getText(EditText editText) {
		return editText.getText().toString().trim().replace(" ", "");
	}
	/**
	 * ��ȡ��Ļ��
	 * 
	 * @return
	 */
	public static int measureViewWidth(View v) {
		int w = View.MeasureSpec.makeMeasureSpec(0,
				View.MeasureSpec.UNSPECIFIED);
		int h = View.MeasureSpec.makeMeasureSpec(0,
				View.MeasureSpec.UNSPECIFIED);
		v.measure(w, h);
		int width = v.getMeasuredWidth();
		return width;
	}

	/**
	 * ��ȡ��Ļ��
	 * 
	 * @return
	 */
	public static int measureViewHeight(View v) {
		int w = View.MeasureSpec.makeMeasureSpec(0,
				View.MeasureSpec.UNSPECIFIED);
		int h = View.MeasureSpec.makeMeasureSpec(0,
				View.MeasureSpec.UNSPECIFIED);
		v.measure(w, h);
		int height = v.getMeasuredHeight();
		return height;
	}

	/**
	 * ��ȡϵͳʱ��
	 */
	@SuppressLint("SimpleDateFormat")
	public static String getSystenTime() {
		long time = System.currentTimeMillis();
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date data = new Date(time);
		String timeString = format.format(data);
		return timeString;
	}

}
