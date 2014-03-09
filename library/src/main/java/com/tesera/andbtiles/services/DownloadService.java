package com.tesera.andbtiles.services;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;

import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.utils.Consts;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class DownloadService extends Service {

    private DownloadManager downloadManager;
    private long enqueue;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // check for download complete action
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(enqueue);
                Cursor cursor = downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                        // find the file and save the map item to the database
                        String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        File mbTilesFile;
                        try {
                            mbTilesFile = new File(new URI(uriString).getPath());
                        } catch (URISyntaxException e) {
                            e.printStackTrace();
                            return;
                        }

                        // create new map item
                        MapItem mapItem = new MapItem();
                        mapItem.setId(mbTilesFile.getName());
                        mapItem.setPath(mbTilesFile.getAbsolutePath());
                        mapItem.setName(mbTilesFile.getName());
                        mapItem.setCacheMode(Consts.CACHE_DATA);
                        mapItem.setSize(mbTilesFile.length());
                        insertMapItem(mapItem);
                    }
                }
                // unregister the receiver since the download is done
                context.unregisterReceiver(this);
                stopSelf();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // get the tile json data
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        // the request should follow the provided URL
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(intent.getStringExtra(Consts.EXTRA_JSON)));
        // the download destination should be on the external SD card inside the app folder
        request.setDestinationInExternalPublicDir(Consts.FOLDER_ROOT, FilenameUtils.getName(intent.getStringExtra(Consts.EXTRA_JSON)));
        enqueue = downloadManager.enqueue(request);

        // register a broadcast receiver to listen to download complete event
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        return super.onStartCommand(intent, flags, startId);
    }

    // helper function for inserting map items into the database
    private void insertMapItem(MapItem mapItem) {
        // insert the file in the database
        MapsDatabase mapsDatabase = new MapsDatabase(getApplicationContext());
        mapsDatabase.open();
        mapsDatabase.insertItems(mapItem);
        mapsDatabase.close();
    }
}
