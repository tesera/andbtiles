package com.tesera.andbtiles.services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.v4.app.NotificationCompat;

import com.google.gson.Gson;
import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HarvesterService extends IntentService {

    private SQLiteDatabase mDatabase;
    private TileJson mTileJson;

    public HarvesterService(String name) {
        super(name);
    }

    public HarvesterService() {
        super("HarvesterService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // get the tile json data
        final MapItem mapItem = new Gson().fromJson(intent.getStringExtra(Consts.EXTRA_JSON), MapItem.class);
        mTileJson = new Gson().fromJson(mapItem.getTileJsonString(), TileJson.class);
        ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

        // notify the user
        int maxProgress = calculateMaxProgress();
        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyManager.cancelAll();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Harvesting tiles…")
                .setProgress(maxProgress, 0, false);
        mNotifyManager.notify(123, builder.build());

        // go trough all zoom levels
        int numberOfTiles = 0;
        for (int z = mTileJson.getMinzoom().intValue(); z <= mTileJson.getMaxzoom().intValue(); z++) {
            // find the OSM coordinates of the bounding box
            int[] topLeftCoordinates = getTileNumber(mTileJson.getBounds().get(3).doubleValue(), mTileJson.getBounds().get(0).doubleValue(), z);
            int[] bottomRightCoordinates = getTileNumber(mTileJson.getBounds().get(1).doubleValue(), mTileJson.getBounds().get(2).doubleValue(), z);

            int startX = topLeftCoordinates[1];
            int endX = bottomRightCoordinates[1];
            int startY = topLeftCoordinates[2];
            int endY = bottomRightCoordinates[2];
            // harvest individual files for specific zoom level
            for (int x = startX; x <= endX; x++)
                for (int y = startY; y <= endY; y++) {
                    // change the notification text
                    builder.setContentTitle(++numberOfTiles + " tiles harvested");
                    builder.setContentText("../" + z + "/" + x + "/" + y + ".png");
                    builder.setProgress(maxProgress, numberOfTiles, false);
                    mNotifyManager.notify(123, builder.build());

                    // check if a tile exists
                    byte[] tileData = getTileBytes(z, x, y, mTileJson);
                    if (tileData == null)
                        continue;

                    final byte[] tileDataFinal = tileData;
                    final int zFinal = z;
                    final int xFinal = x;
                    final int yFinal = y;
                    mExecutorService.submit(
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mDatabase == null)
                                        mDatabase = SQLiteDatabase.openOrCreateDatabase(mapItem.getPath(), null);
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

                }
        }

        if (mDatabase != null)
            mDatabase.close();
        // try to save it to database
        mapItem.setSize(new File(mapItem.getPath()).length());
        // insert the file in the database
        MapsDatabase mapsDatabase = new MapsDatabase(this);
        mapsDatabase.open();
        mapsDatabase.insertItems(mapItem);
        mapsDatabase.close();

        // inform the user for completed harvest
        builder = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(mapItem.getPath())
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);

        mNotifyManager.cancelAll();
        mNotifyManager.notify(0, builder.build());
    }

    @Override
    public void onDestroy() {
        if (mDatabase != null)
            mDatabase.close();
        super.onDestroy();
    }

    private int calculateMaxProgress() {
        int numberOfTiles = 0;
        for (int z = mTileJson.getMinzoom().intValue(); z <= mTileJson.getMaxzoom().intValue(); z++) {
            // find the OSM coordinates of the bounding box
            int[] topLeftCoordinates = getTileNumber(mTileJson.getBounds().get(3).doubleValue(), mTileJson.getBounds().get(0).doubleValue(), z);
            int[] bottomRightCoordinates = getTileNumber(mTileJson.getBounds().get(1).doubleValue(), mTileJson.getBounds().get(2).doubleValue(), z);

            int startX = topLeftCoordinates[1];
            int endX = bottomRightCoordinates[1];
            int startY = topLeftCoordinates[2];
            int endY = bottomRightCoordinates[2];
            // harvest individual files for specific zoom level
            for (int x = startX; x <= endX; x++)
                for (int y = startY; y <= endY; y++)
                    numberOfTiles++;
        }
        return numberOfTiles;
    }

    private int[] getTileNumber(double lat, double lon, int zoom) {
        int xTile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int yTile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (xTile < 0)
            xTile = 0;
        if (xTile >= (1 << zoom))
            xTile = ((1 << zoom) - 1);
        if (yTile < 0)
            yTile = 0;
        if (yTile >= (1 << zoom))
            yTile = ((1 << zoom) - 1);

        int[] tileCoordinates = new int[3];
        tileCoordinates[0] = zoom;
        tileCoordinates[1] = xTile;
        tileCoordinates[2] = yTile;

        return tileCoordinates;
    }

    private byte[] getTileBytes(int z, int x, int y, TileJson tileJson) {
        String tilesUrl = tileJson.getTiles().get(new Random().nextInt(tileJson.getTiles().size())).toString();
        tilesUrl = tilesUrl.replace("{z}", "" + z).replace("{x}", "" + x).replace("{y}", "" + y);
        // this already is an worker thread so we can fetch network data here
        try {
            // download the tile from the web url
            URL url = new URL(tilesUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setReadTimeout(1000);
            connection.setConnectTimeout(1000);
            connection.connect();
            InputStream input = connection.getInputStream();
            // convert the primitive input stream to the wrapper class
            return IOUtils.toByteArray(input);
        } catch (Exception e) {
            // tile cannot be obtained
            return null;
        }
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
}
