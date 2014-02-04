package com.tesera.andbtiles.databases;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class MapsDatabaseHelper extends SQLiteOpenHelper {

    // Database
    private static final String DATABASE_NAME = "maps.db";
    private static final int DATABASE_VERSION = 1;
    // Tables
    public static final String TABLE_MAPS = "maps";
    // Columns
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_PATH = "path";
    public static final String COLUMN_CACHE_MODE = "cache_mode";
    public static final String COLUMN_SIZE = "size";
    public static final String COLUMN_JSON_DATA = "json_data";

    // Database creation statement
    private static final String DATABASE_CREATE = "create table " + TABLE_MAPS + "(" +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_NAME + " text unique, " +
            COLUMN_PATH + " text, " +
            COLUMN_CACHE_MODE + " integer, " +
            COLUMN_SIZE + " integer, " +
            COLUMN_JSON_DATA + " text);";

    public MapsDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MAPS);
        onCreate(db);
    }
}
