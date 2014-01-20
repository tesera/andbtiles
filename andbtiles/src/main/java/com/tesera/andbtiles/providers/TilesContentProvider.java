package com.tesera.andbtiles.providers;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;

import java.sql.SQLException;

public class TilesContentProvider extends ContentProvider {

    private MapsDatabase mMapsDatabase;

    @Override
    public boolean onCreate() {
        mMapsDatabase = new MapsDatabase(getContext());
        try {
            mMapsDatabase.open();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        if (!uri.toString().startsWith(TilesContract.CONTENT_URI))
            throw new UnsupportedOperationException("Content URI not recognized.");

        String databaseName = uri.getPathSegments().get(uri.getPathSegments().size() - 2);
        String tableName = uri.getLastPathSegment();

        MapItem mapItem = mMapsDatabase.findMapByDatabaseName(databaseName);
        if (mapItem == null) {
            throw new IllegalArgumentException("Map <" + uri.getLastPathSegment() + "> not found in maps.");
        }

        switch (mapItem.getCacheMode()) {
            case Consts.CACHE_FULL:
                return SQLiteDatabase.openOrCreateDatabase(mapItem.getPath(), null).query(tableName, projection, selection, selectionArgs, null, null, sortOrder);
            // TODO implement all cache modes
            default:
                break;
        }

        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("You are not allowed to insert data.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("You are not allowed to delete data.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("You are not allowed to update data.");
    }
}
