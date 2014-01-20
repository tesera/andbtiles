package com.tesera.andbtiles.databases;


import android.database.sqlite.SQLiteDatabase;

public class MBTilesDatabase {

    private SQLiteDatabase database;

    public MBTilesDatabase(String path) {
        database = SQLiteDatabase.openOrCreateDatabase(path, null);
    }

    public void close() {
        database.close();
    }
}
