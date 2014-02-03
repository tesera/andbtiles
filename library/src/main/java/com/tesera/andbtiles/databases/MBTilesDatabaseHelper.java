package com.tesera.andbtiles.databases;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class MBTilesDatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;

    private static final String METADATA_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT);";
    private static final String METADATA_INDEX_CREATE = "CREATE UNIQUE INDEX name ON metadata (name);";
    private static final String MAP_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS map (" +
            "zoom_level INTEGER, " +
            "tile_column INTEGER, " +
            "tile_row INTEGER, " +
            "tile_id VARCHAR(256));";
    private static final String MAP_INDEX_CREATE = "CREATE UNIQUE INDEX map_index ON map (zoom_level, tile_column, tile_row);";
    private static final String IMAGES_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS images (tile_data BLOB, tile_id VARCHAR(256));";
    private static final String IMAGES_INDEX_CREATE = "CREATE UNIQUE INDEX image_id ON images (tile_id);";
    private static final String TILES_VIEW_CREATE = "CREATE VIEW tiles as " +
            "SELECT map.zoom_level AS zoom_level, " +
            "map.tile_column as tile_column, " +
            "map.tile_row as tile_row, " +
            "images.tile_data as tile_data " +
            "FROM map " +
            "JOIN images " +
            "ON images.tile_id = map.tile_id;";

    public MBTilesDatabaseHelper(Context context, String path) {
        super(context, path, null, DATABASE_VERSION);

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(METADATA_TABLE_CREATE);
        db.execSQL(METADATA_INDEX_CREATE);
        db.execSQL(MAP_TABLE_CREATE);
        db.execSQL(MAP_INDEX_CREATE);
        db.execSQL(IMAGES_TABLE_CREATE);
        db.execSQL(IMAGES_INDEX_CREATE);
        db.execSQL(TILES_VIEW_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}