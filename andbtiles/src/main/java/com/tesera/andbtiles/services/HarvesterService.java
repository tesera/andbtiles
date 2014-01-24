package com.tesera.andbtiles.services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.gson.Gson;
import com.tesera.andbtiles.MainActivity;
import com.tesera.andbtiles.R;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;
import com.tesera.andbtiles.utils.Utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class HarvesterService extends IntentService {

    private SQLiteDatabase mDatabase;

    public HarvesterService(String name) {
        super(name);
    }

    public HarvesterService() {
        super("HarvesterService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // get the tile json data
        MapItem mapItem = new Gson().fromJson(intent.getStringExtra(Consts.EXTRA_JSON), MapItem.class);
        TileJson tileJson = new Gson().fromJson(mapItem.getJsonData(), TileJson.class);

        // open the database for inserting
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                + Consts.FOLDER_ROOT + File.separator + tileJson.getName() + "." + Consts.EXTENSION_MBTILES;
        mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);
        List<ContentValues> mMapValues = new ArrayList<>();
        List<ContentValues> mImageValues = new ArrayList<>();

        // notify the user
        int maxProgress = tileJson.getMaxzoom().intValue() - tileJson.getMinzoom().intValue();
        int progress = 0;
        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle(getString(R.string.crouton_harvesting))
                        .setProgress(maxProgress, progress, false);
        mNotifyManager.notify(123, builder.build());

        // go trough all zoom levels
        for (int z = tileJson.getMinzoom().intValue(); z <= tileJson.getMaxzoom().intValue(); z++) {
            // find the OSM coordinates of the bounding box
            int[] topLeftCoordinates = getTileNumber(tileJson.getBounds().get(3).doubleValue(), tileJson.getBounds().get(0).doubleValue(), z);
            int[] bottomRightCoordinates = getTileNumber(tileJson.getBounds().get(1).doubleValue(), tileJson.getBounds().get(2).doubleValue(), z);

            int startX = topLeftCoordinates[1];
            int endX = bottomRightCoordinates[1];
            int startY = topLeftCoordinates[2];
            int endY = bottomRightCoordinates[2];
            // harvest individual files for specific zoom level
            for (int x = startX; x <= endX; x++)
                for (int y = startY; y <= endY; y++) {
                    // get the tile url
                    String tilesUrl = tileJson.getTiles().get(new Random().nextInt(tileJson.getTiles().size())).toString();
                    tilesUrl = tilesUrl.replace("{z}", "" + z).replace("{x}", "" + x).replace("{y}", "" + y);

                    // check if a tile exists
                    byte[] tileData = getTileBytes(z, x, y, tileJson);
                    if (tileData == null)
                        continue;

                    // change the notification text
                    builder.setContentText("../" + z + "/" + x + "/" + y + ".png");
                    mNotifyManager.notify(123, builder.build());

                    // add for batch insert
                    String tile_id = UUID.nameUUIDFromBytes(tileData).toString();

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
                        values.put(TilesContract.COLUMN_TILE_DATA, tileData);
                        mImageValues.add(values);
                        // make batch insert for every 50 tiles
                        if (mImageValues.size() > 50) {
                            batchInsertImages(mImageValues);
                            mImageValues.clear();
                        }
                    }

                    selection = TilesContract.COLUMN_ZOOM_LEVEL + " = ? AND "
                            + TilesContract.COLUMN_TILE_COLUMN + "= ? AND "
                            + TilesContract.COLUMN_TILE_ROW + "= ?";
                    selectionParams = new String[]{String.valueOf(z), String.valueOf(x), String.valueOf((int) (Math.pow(2, z) - y - 1))};

                    // only insert if we don't have the tile
                    cursor = mDatabase.query(TilesContract.TABLE_MAP, projection, selection, selectionParams, null, null, null);
                    if (cursor.getCount() == 0) {
                        ContentValues values = new ContentValues();
                        values.put(TilesContract.COLUMN_ZOOM_LEVEL, z);
                        values.put(TilesContract.COLUMN_TILE_COLUMN, x);
                        values.put(TilesContract.COLUMN_TILE_ROW, String.valueOf((int) (Math.pow(2, z) - y - 1)));
                        values.put(TilesContract.COLUMN_TILE_ID, tile_id);
                        mMapValues.add(values);
                        // make batch insert for every 50 tiles
                        if (mMapValues.size() > 50) {
                            batchInsertMap(mMapValues);
                            mMapValues.clear();
                        }
                    }
                }

            builder.setProgress(maxProgress, ++progress, false);
            mNotifyManager.notify(123, builder.build());
        }
        // insert the rest of the tiles
        batchInsertImages(mImageValues);
        batchInsertMap(mMapValues);
        mDatabase.close();

        // check if the map is already added
        if (Utils.isMapInDatabase(this, mapItem))
            return;
        // try to save it to database
        mapItem.setSize(new File(path).length());
        mapItem.setPath(path);
        Utils.saveMapToDatabase(this, mapItem);

        // inform the user for completed harvest
        builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(getString(R.string.crouton_download_complete))
                        .setContentText(mapItem.getPath())
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);
        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        mNotifyManager.cancelAll();
        mNotifyManager.notify(0, builder.build());
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
            connection.connect();
            InputStream input = connection.getInputStream();
            // convert the primitive input stream to the wrapper class
            return IOUtils.toByteArray(input);
        } catch (Exception e) {
            // tile cannot be obtained
            return null;
        }
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
}
