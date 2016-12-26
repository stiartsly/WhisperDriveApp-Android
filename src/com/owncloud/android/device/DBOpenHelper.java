package com.owncloud.android.device;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class DBOpenHelper extends SQLiteOpenHelper {

	public DBOpenHelper(Context context, String name, CursorFactory factory,
			int version) {
		super(context, name, factory, version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table current_device(_id integer primary key autoincrement, username char(50), device_id char(50))");
		db.execSQL("create table device_message(_id integer primary key autoincrement, device_id char(50), device_name char(50), local_port integer)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int oldVersion, int newVersion) {
		System.out.println("数据库升级了");

	}

}
