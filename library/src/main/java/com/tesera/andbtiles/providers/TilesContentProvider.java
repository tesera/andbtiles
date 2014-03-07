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
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TilesContentProvider extends ContentProvider {

    private SQLiteDatabase mDatabase;
    private MapsDatabase mMapsDatabase;
    private ExecutorService mExecutorService;
    private Gson mGson;

    @Override
    public boolean onCreate() {
        mGson = new Gson();
        mExecutorService = Executors.newSingleThreadExecutor();

        mDatabase = null;
        mMapsDatabase = new MapsDatabase(getContext());
        mMapsDatabase.open();

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        if (!uri.toString().startsWith(TilesContract.CONTENT_URI))
            throw new UnsupportedOperationException("Content URI not recognized.");

        String databaseId = uri.getPathSegments().get(uri.getPathSegments().size() - 2);
        String tableName = uri.getLastPathSegment();

        MapItem mapItem = mMapsDatabase.findMapById(databaseId);
        if (mapItem == null) {
            // try a local file instead since
            // the remote files have ids like <user>.<mapname>
            // the remote files have ids like <mapname>.mbtiles
            databaseId = databaseId.split("/.")[1] + ".mbtiles";
            mapItem = mMapsDatabase.findMapById(databaseId);
            if (mapItem == null)
                throw new IllegalArgumentException("Map <" + databaseId + "> with not found in maps.");
        }

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
                    TileJson tileJson = mGson.fromJson(mapItem.getTileJsonString(), TileJson.class);

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
                TileJson tileJson = mGson.fromJson(mapItem.getTileJsonString(), TileJson.class);
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
                tileJson = mGson.fromJson(mapItem.getTileJsonString(), TileJson.class);
                tileData = getTileBytes(z, x, y, tileJson);

                final byte[] tileDataFinal = tileData;
                final int zFinal = z;
                final int xFinal = x;
                final int yFinal = y;

                mExecutorService.submit(
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // save to database in worker thread
                                // the operation can be quite lengthily because
                                // we cannot make batch insert since the tiles are requested asynchronously
                                String tile_id = UUID.nameUUIDFromBytes(tileDataFinal).toString();

                                // insert into separate tables
                                ContentValues values = new ContentValues();
                                values.put(TilesContract.COLUMN_TILE_ID, tile_id);
                                values.put(TilesContract.COLUMN_TILE_DATA, tileDataFinal);
                                insertImages(values);

                                values = new ContentValues();
                                values.put(TilesContract.COLUMN_ZOOM_LEVEL, zFinal);
                                values.put(TilesContract.COLUMN_TILE_COLUMN, xFinal);
                                values.put(TilesContract.COLUMN_TILE_ROW, String.valueOf((int) (Math.pow(2, zFinal) - yFinal - 1)));
                                values.put(TilesContract.COLUMN_TILE_ID, tile_id);
                                insertMap(values);
                            }
                        })
                );

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

    private void insertImages(ContentValues cValue) {
        String sql = "INSERT INTO " + TilesContract.TABLE_IMAGES + " VALUES (?,?);";
        SQLiteStatement statement = mDatabase.compileStatement(sql);
        mDatabase.beginTransaction();
        statement.clearBindings();
        statement.bindBlob(1, cValue.getAsByteArray(TilesContract.COLUMN_TILE_DATA));
        statement.bindString(2, cValue.getAsString(TilesContract.COLUMN_TILE_ID));
        try {
            statement.execute();
        } catch (Exception e) {
            // this is a non-unique tile_id
        }
        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
    }

    private void insertMap(ContentValues cValue) {
        String sql = "INSERT INTO " + TilesContract.TABLE_MAP + " VALUES (?,?,?,?);";
        SQLiteStatement statement = mDatabase.compileStatement(sql);
        mDatabase.beginTransaction();
        statement.clearBindings();
        statement.bindLong(1, cValue.getAsLong(TilesContract.COLUMN_ZOOM_LEVEL));
        statement.bindLong(2, cValue.getAsLong(TilesContract.COLUMN_TILE_COLUMN));
        statement.bindLong(3, cValue.getAsLong(TilesContract.COLUMN_TILE_ROW));
        statement.bindString(4, cValue.getAsString(TilesContract.COLUMN_TILE_ID));
        try {
            statement.execute();
        } catch (Exception e) {
            // this is a non-unique tile_id
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
