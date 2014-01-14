package com.tesera.andbtiles.databases;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.tesera.andbtiles.pojos.MapItem;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MapsDatabase {

    private SQLiteDatabase database;
    private MapsDatabaseHelper dbHelper;
    private String[] allColumns = {
            MapsDatabaseHelper.COLUMN_ID,
            MapsDatabaseHelper.COLUMN_NAME,
            MapsDatabaseHelper.COLUMN_PATH,
            MapsDatabaseHelper.COLUMN_CACHE_MODE
    };

    public MapsDatabase(Context context) {
        dbHelper = new MapsDatabaseHelper(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void deleteItems(List<MapItem> items) {
        for(MapItem item : items)
            database.delete(MapsDatabaseHelper.TABLE_MAPS, MapsDatabaseHelper.COLUMN_ID + "=" + item.getId(), null);
    }

    public void insertItems(List<MapItem> items) {
        String sql = "INSERT INTO " + MapsDatabaseHelper.TABLE_MAPS + " VALUES (?,?,?,?);";
        SQLiteStatement statement = database.compileStatement(sql);
        database.beginTransaction();
        for (MapItem item : items) {
            statement.clearBindings();
            statement.bindString(1, item.getId());
            statement.bindString(2, item.getName());
            statement.bindString(3, item.getPath());
            statement.bindLong(4, item.getCacheMode());
            statement.execute();
        }
        database.setTransactionSuccessful();
        database.endTransaction();
    }

    public void updateItem(MapItem item) {
        ContentValues args = new ContentValues();
        args.put(MapsDatabaseHelper.COLUMN_ID, item.getId());
        args.put(MapsDatabaseHelper.COLUMN_NAME, item.getName());
        args.put(MapsDatabaseHelper.COLUMN_PATH, item.getPath());
        args.put(MapsDatabaseHelper.COLUMN_CACHE_MODE, item.getCacheMode());
        database.update(MapsDatabaseHelper.TABLE_MAPS, args, MapsDatabaseHelper.COLUMN_ID + "=" + item.getId(), null);
    }

    public List<MapItem> getAllItems() {
        List<MapItem> comments = new ArrayList<>();
        Cursor cursor = database.query(MapsDatabaseHelper.TABLE_MAPS, allColumns, null, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            MapItem comment = cursorToMapItem(cursor);
            comments.add(comment);
            cursor.moveToNext();
        }
        // make sure to close the cursor
        cursor.close();
        return comments;
    }

    private MapItem cursorToMapItem(Cursor cursor) {
        MapItem mapItem = new MapItem();
        mapItem.setId(cursor.getString(0));
        mapItem.setName(cursor.getString(1));
        mapItem.setPath(cursor.getString(2));
        mapItem.setCacheMode(cursor.getInt(4));
        return mapItem;
    }
}
