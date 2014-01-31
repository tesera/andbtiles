package com.tesera.andbtiles.databases;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.tesera.andbtiles.utils.TilesContract;

import java.sql.SQLException;
import java.util.Map;


public class MBTilesDatabase {

    private SQLiteDatabase database;
    private MBTilesDatabaseHelper dbHelper;
    private String[] allColumns = {
            MapsDatabaseHelper.COLUMN_ID,
            MapsDatabaseHelper.COLUMN_NAME,
            MapsDatabaseHelper.COLUMN_PATH,
            MapsDatabaseHelper.COLUMN_CACHE_MODE,
            MapsDatabaseHelper.COLUMN_SIZE,
            MapsDatabaseHelper.COLUMN_JSON_DATA
    };

    public MBTilesDatabase(Context context, String path) {
        dbHelper = new MBTilesDatabaseHelper(context, path);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void insertMetadata(Map<String, String> metadataMap) {
        String sql = "INSERT INTO " + TilesContract.TABLE_METADATA + " VALUES (?,?);";
        SQLiteStatement statement = database.compileStatement(sql);
        database.beginTransaction();
        for (String key : metadataMap.keySet()) {
            statement.clearBindings();
            statement.bindString(1, key);
            statement.bindString(2, metadataMap.get(key) == null ? "" : metadataMap.get(key));
            statement.execute();
        }
        database.setTransactionSuccessful();
        database.endTransaction();
    }
}
