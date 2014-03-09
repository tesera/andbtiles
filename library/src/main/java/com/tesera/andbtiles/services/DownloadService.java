package com.tesera.andbtiles.services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;

import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.utils.Consts;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class DownloadService extends IntentService {

    public DownloadService(String name) {
        super(name);
    }

    public DownloadService() {
        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading file…")
                .setContentText(intent.getStringExtra(Consts.EXTRA_JSON))
                .setProgress(100, 0, false);
        mNotifyManager.notify(124, builder.build());

        int count;
        try {
            URL url = new URL(intent.getStringExtra(Consts.EXTRA_JSON));
            URLConnection connection = url.openConnection();
            connection.connect();

            URL downloadUrl = new URL(connection.getHeaderField("Location"));
            URLConnection downloadConnection = downloadUrl.openConnection();
            int lenghtOfFile = downloadConnection.getContentLength();

            // input stream to read file - with 8k buffer
            InputStream input = new BufferedInputStream(downloadUrl.openStream(), 8192);

            // Output stream to write file
            File andbtilesFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + Consts.FOLDER_ROOT);
            if (!andbtilesFolder.exists())
                andbtilesFolder.mkdirs();

            OutputStream output = new FileOutputStream(andbtilesFolder.getAbsolutePath() + File.separator + FilenameUtils.getName(intent.getStringExtra(Consts.EXTRA_JSON)));
            byte data[] = new byte[1024];
            long total = 0;
            long progress;
            long oldProgress = -1;

            while ((count = input.read(data)) != -1) {
                total += count;
                progress = total * 100 / lenghtOfFile;
                if (progress != oldProgress) {
                    // set the progress
                    builder.setProgress(100, (int) progress, false);
                    mNotifyManager.notify(124, builder.build());
                    oldProgress = progress;
                }
                // writing data to file
                output.write(data, 0, count);
            }
            // flushing output
            output.flush();
            // closing streams
            output.close();
            input.close();

            // create new map item
            File mbTilesFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    + File.separator + Consts.FOLDER_ROOT + File.separator + FilenameUtils.getName(intent.getStringExtra(Consts.EXTRA_JSON)));
            MapItem mapItem = new MapItem();
            mapItem.setId(mbTilesFile.getName());
            mapItem.setPath(mbTilesFile.getAbsolutePath());
            mapItem.setName(mbTilesFile.getName());
            mapItem.setCacheMode(Consts.CACHE_DATA);
            mapItem.setSize(mbTilesFile.length());
            insertMapItem(mapItem);

            // inform the user for completed harvest
            builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download complete")
                    .setContentText(mapItem.getPath())
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL);

            mNotifyManager.cancelAll();
            mNotifyManager.notify(2, builder.build());
        } catch (Exception e) {
            builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download failed")
                    .setContentText(intent.getStringExtra(Consts.EXTRA_JSON))
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL);

            mNotifyManager.cancelAll();
            mNotifyManager.notify(2, builder.build());
            e.printStackTrace();
        }
    }

    // helper function for inserting map items into the database
    private void insertMapItem(MapItem mapItem) {
        // insert the file in the database
        MapsDatabase mapsDatabase = new MapsDatabase(getApplicationContext());
        mapsDatabase.open();
        mapsDatabase.insertItems(mapItem);
        mapsDatabase.close();
        System.out.println("inserted");
    }
}
