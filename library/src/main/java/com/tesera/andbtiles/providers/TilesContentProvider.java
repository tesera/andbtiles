package com.tesera.andbtiles.providers;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.ConnectivityManager;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TilesContentProvider extends ContentProvider {

    private SQLiteDatabase mDatabase;
    private MapsDatabase mMapsDatabase;

    private Gson mGson;

    private List<ContentValues> mMapValues;
    private List<ContentValues> mImageValues;

    @Override
    public boolean onCreate() {

        mMapValues = new ArrayList<>();
        mImageValues = new ArrayList<>();
        mGson = new Gson();

        mDatabase = null;
        mMapsDatabase = new MapsDatabase(getContext());
        mMapsDatabase.open();

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

        if (mDatabase == null)
            mDatabase = SQLiteDatabase.openOrCreateDatabase(mapItem.getPath(), null);

        // handle tile request
        switch (mapItem.getCacheMode()) {
            case Consts.CACHE_NO:
                if (!isNetworkConnected())
                    return null;
                // since there is no caching there is no metadata
                // parse the TileJSON response instead
                if (tableName.equalsIgnoreCase(TilesContract.TABLE_METADATA)) {
                    // FIXME needs more complex algorithm if requesting values other than center
                    // match the database center value
                    // the center values are in format lon,lat,zoom
                    // ex. -63.1275,45.1936,9
                    TileJson tileJson = mGson.fromJson(mapItem.getJsonData(), TileJson.class);

                    String[] columnNames = {TilesContract.COLUMN_NAME, TilesContract.COLUMN_VALUE};
                    String[] columnData = {"center", Arrays.toString(tileJson.getCenter().toArray()).replace("[", "").replace("]", "").replace(" ", "")};

                    MatrixCursor cursor = new MatrixCursor(columnNames);
                    cursor.addRow(columnData);
                    return cursor;
                }
                // fetch tile from the web
                int z = Integer.parseInt(selectionArgs[0]);
                int x = Integer.parseInt(selectionArgs[1]);
                // we need to switch back from TSM to OSM coordinates since web fetching works in that format
                int y = (int) (Math.pow(2, z) - Double.parseDouble(selectionArgs[2]) - 1);
                TileJson tileJson = mGson.fromJson(mapItem.getJsonData(), TileJson.class);
                byte[] tileData = getTileBytes(z, x, y, tileJson);
                // return the cursor containing the tile_data
                return getCursorWithTile(tileData);
            case Consts.CACHE_ON_DEMAND:
                // try to find the tile in the database
                Cursor cursor = mDatabase.query(tableName, projection, selection, selectionArgs, null, null, sortOrder);
                if (cursor != null && cursor.getCount() > 0)
                    return cursor;

                if (!isNetworkConnected())
                    return null;
                // fetch tile from the web
                z = Integer.parseInt(selectionArgs[0]);
                x = Integer.parseInt(selectionArgs[1]);
                // we need to switch back from TSM to OSM coordinates since web fetching works in that format
                y = (int) (Math.pow(2, z) - Double.parseDouble(selectionArgs[2]) - 1);
                tileJson = mGson.fromJson(mapItem.getJsonData(), TileJson.class);
                tileData = getTileBytes(z, x, y, tileJson);

                final byte[] tileDataFinal = tileData;
                final int zFinal = z;
                final int xFinal = x;
                final int yFinal = y;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // save to database in worker thread
                        // the operation can be quite lengthily because
                        // we cannot make batch insert since the tiles are requested asynchronously
                        String tile_id = UUID.nameUUIDFromBytes(tileDataFinal).toString();

                        String[] projection = new String[]{TilesContract.COLUMN_TILE_ID};
                        String selection = TilesContract.COLUMN_TILE_ID + " = ?";
                        String[] selectionParams = new String[]{tile_id};

                        // only insert if we don't have the tile
                        // this will prevent adding redundant tiles like blue ocean tile
                        Cursor cursor = mDatabase.query(TilesContract.TABLE_IMAGES, projection, selection, selectionParams, null, null, null);
                        if (cursor.getCount() == 0) {
                            // insert into separate tables
                            ContentValues values = new ContentValues();
                            values.put(TilesContract.COLUMN_TILE_ID, tile_id);
                            values.put(TilesContract.COLUMN_TILE_DATA, tileDataFinal);
                            mImageValues.add(values);
                            // make batch insert for every 20 tiles
                            if (mImageValues.size() > 20) {
                                batchInsertImages(mImageValues);
                                mImageValues.clear();
                            }
                        }

                        selection = TilesContract.COLUMN_ZOOM_LEVEL + " = ? AND "
                                + TilesContract.COLUMN_TILE_COLUMN + "= ? AND "
                                + TilesContract.COLUMN_TILE_ROW + "= ?";
                        selectionParams = new String[]{String.valueOf(zFinal), String.valueOf(xFinal), String.valueOf((int) (Math.pow(2, zFinal) - yFinal - 1))};

                        // only insert if we don't have the tile
                        cursor = mDatabase.query(TilesContract.TABLE_MAP, projection, selection, selectionParams, null, null, null);
                        if (cursor.getCount() == 0) {
                            ContentValues values = new ContentValues();
                            values.put(TilesContract.COLUMN_ZOOM_LEVEL, zFinal);
                            values.put(TilesContract.COLUMN_TILE_COLUMN, xFinal);
                            values.put(TilesContract.COLUMN_TILE_ROW, String.valueOf((int) (Math.pow(2, zFinal) - yFinal - 1)));
                            values.put(TilesContract.COLUMN_TILE_ID, tile_id);
                            mMapValues.add(values);
                            // make batch insert for every 20 tiles
                            if (mMapValues.size() > 20) {
                                batchInsertMap(mMapValues);
                                mMapValues.clear();
                            }
                        }
                    }
                }).start();

                // return the cursor containing the tile_data
                return getCursorWithTile(tileData);
            case Consts.CACHE_FULL:
            case Consts.CACHE_DATA:
                return mDatabase.query(tableName, projection, selection, selectionArgs, null, null, sortOrder);
            default:
                break;
        }

        return null;
    }

    private void batchInsertImages(List<ContentValues> cValues) {
        String sql = "INSERT INTO " + TilesContract.TABLE_IMAGES + " VALUES (?,?);";
        SQLiteStatement statement = mDatabase.compileStatement(sql);
        mDatabase.beginTransaction();
        for (ContentValues cValue : cValues) {
            statement.clearBindings();
            statement.bindBlob(1, cValue.getAsByteArray(TilesContract.COLUMN_TILE_DATA));
            statement.bindString(2, cValue.getAsString(TilesContract.COLUMN_TILE_ID));
            statement.execute();
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
    }

    private void batchInsertMap(List<ContentValues> cValues) {
        String sql = "INSERT INTO " + TilesContract.TABLE_MAP + " VALUES (?,?,?,?);";
        SQLiteStatement statement = mDatabase.compileStatement(sql);
        mDatabase.beginTransaction();
        for (ContentValues cValue : cValues) {
            statement.clearBindings();
            statement.bindLong(1, cValue.getAsLong(TilesContract.COLUMN_ZOOM_LEVEL));
            statement.bindLong(2, cValue.getAsLong(TilesContract.COLUMN_TILE_COLUMN));
            statement.bindLong(3, cValue.getAsLong(TilesContract.COLUMN_TILE_ROW));
            statement.bindString(4, cValue.getAsString(TilesContract.COLUMN_TILE_ID));
            statement.execute();
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
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

    private Cursor getCursorWithTile(byte[] tileData) {
        // convert the primitive input stream to the wrapper class
        if (tileData == null)
            return null;

        // create an artificial cursor and return it as content provider query result
        String[] columnNames = new String[]{TilesContract.COLUMN_TILE_DATA};
        MatrixCursor cursor = new MatrixCursor(columnNames);
        cursor.addRow(new Object[]{tileData});
        return cursor;
    }

    private byte[] getTileBytes(int z, int x, int y, TileJson tileJson) {
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
            return IOUtils.toByteArray(input);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm.getActiveNetworkInfo() != null);
    }
}
