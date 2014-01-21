package com.tesera.andbtiles.providers;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.google.gson.Gson;
import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;

public class TilesContentProvider extends ContentProvider {

    private MapsDatabase mMapsDatabase;
    private Gson mGson;

    @Override
    public boolean onCreate() {
        mGson = new Gson();
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
        if (mapItem == null)
            throw new IllegalArgumentException("Map <" + uri.getLastPathSegment() + "> not found in maps.");

        switch (mapItem.getCacheMode()) {
            case Consts.CACHE_NO:
                // since there is no caching there is no metadata
                // parse the TileJSON response instead
                if (tableName.equalsIgnoreCase(TilesContract.TABLE_METADATA)) {
                    // match the database center value
                    // the center values are in format lon,lat,zoom
                    // ex. -63.1275,45.1936,9
                    TileJson tileJson = mGson.fromJson(mapItem.getJsonData(), TileJson.class);
                    String centerValue = "";
                    for (Number number : tileJson.getCenter())
                        centerValue += number.toString() + ",";
                    centerValue = centerValue.substring(0, centerValue.lastIndexOf(","));

                    String[] columnNames = {TilesContract.COLUMN_NAME, TilesContract.COLUMN_VALUE};
                    String[] columnData = {"center", centerValue};

                    MatrixCursor cursor = new MatrixCursor(columnNames);
                    cursor.addRow(columnData);
                    return cursor;
                }
                // fetch tile from the web
                return getCursorWithTile(selectionArgs, mapItem);
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

    private Cursor getCursorWithTile(String[] selectionArgs, MapItem mapItem) {
        int z = Integer.parseInt(selectionArgs[0]);
        int x = Integer.parseInt(selectionArgs[1]);
        // we need to switch back from TSM to OSM coordinates since web fetching works in that format
        int y = (int) (Math.pow(2, z) - Double.parseDouble(selectionArgs[2]) - 1);
        // get the tile url scheme and replace it with actual values
        TileJson tileJson = mGson.fromJson(mapItem.getJsonData(), TileJson.class);
        String tilesUrl = tileJson.getTiles().get(0).toString();
        tilesUrl = tilesUrl.replace("{z}", "" + z).replace("{x}", "" + x).replace("{y}", "" + y);
        // this already is an worker thread so we can fetch network data here
        try {
            // download the tile from the web url
            URL url = new URL(tilesUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            // convert the primitive input stream to the wrapper class
            byte[] tileData = IOUtils.toByteArray(input);

            // create an artificial cursor and return it as content provider query result
            String[] columnNames = new String[]{TilesContract.COLUMN_TILE_DATA};
            MatrixCursor cursor = new MatrixCursor(columnNames);
            cursor.addRow(new Object[]{tileData});
            return cursor;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
