package com.tesera.andbtiles;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;

import com.tesera.andbtiles.callbacks.DatabaseChangeCallback;
import com.tesera.andbtiles.fragments.MapsFragment;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.Utils;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends Activity implements DatabaseChangeCallback {

    private boolean isDatabaseChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new MapsFragment())
                    .commit();
        }
    }

    @Override
    public void onDatabaseChanged() {
        isDatabaseChanged = true;
    }

    @Override
    public void downloadFile(String path) {
        Toast.makeText(this, getString(R.string.crouton_downloading), Toast.LENGTH_SHORT).show();
        // empty back stack
        getFragmentManager().popBackStack();
        getFragmentManager().popBackStack();

        final DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        // the request should follow the provided URL
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(path));
        // the download destination should be on the external SD card inside the app folder
        request.setDestinationInExternalPublicDir(Consts.FOLDER_ROOT, FilenameUtils.getName(path));
        final long enqueue = downloadManager.enqueue(request);

        // register a broadcast receiver to listen to download complete event
        BroadcastReceiver receiver = new BroadcastReceiver() {
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
                            mapItem.setPath(mbTilesFile.getAbsolutePath());
                            mapItem.setName(mbTilesFile.getName());
                            mapItem.setCacheMode(Consts.CACHE_DATA);
                            mapItem.setSize(mbTilesFile.length());

                            // try to save it to database
                            if (!Utils.saveMapToDatabase(MainActivity.this, mapItem)) {
                                // since this is a long running operation the activity/fragment may not be visible upon completion
                                Toast.makeText(MainActivity.this, getString(R.string.crouton_database_error), Toast.LENGTH_SHORT).show();
                                return;
                            }

                            try {
                                Toast.makeText(MainActivity.this, getString(R.string.crouton_map_added), Toast.LENGTH_SHORT).show();
                                isDatabaseChanged = true;
                                // replace fragment with new data
                                getFragmentManager().beginTransaction()
                                        .replace(R.id.container, new MapsFragment())
                                        .commit();
                            } catch (Exception e) {
                                e.printStackTrace();
                                isDatabaseChanged = true;
                                // since this is a long running operation the activity/fragment may not be visible upon completion
                                // add notification for success
                                NotificationCompat.Builder builder =
                                        new NotificationCompat.Builder(context)
                                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                                .setContentTitle(context.getString(R.string.crouton_download_complete))
                                                .setContentText(mbTilesFile.getAbsolutePath())
                                                .setAutoCancel(true)
                                                .setDefaults(Notification.DEFAULT_ALL);
                                // Creates an explicit intent for an Activity in your app
                                Intent resultIntent = new Intent(context, MainActivity.class);
                                // The stack builder object will contain an artificial back stack for the
                                // started Activity.
                                // This ensures that navigating backward from the Activity leads out of
                                // your application to the Home screen.
                                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                                // Adds the back stack for the Intent (but not the Intent itself)
                                stackBuilder.addParentStack(MainActivity.class);
                                // Adds the Intent that starts the Activity to the top of the stack
                                stackBuilder.addNextIntent(resultIntent);
                                PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                                builder.setContentIntent(resultPendingIntent);
                                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                                // mId allows you to update the notification later on.
                                notificationManager.notify(0, builder.build());
                            }
                        }
                    }
                }
                // unregister the receiver since the download is done
                context.unregisterReceiver(this);
            }
        };

        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public boolean isDatabaseChanged() {
        return isDatabaseChanged;
    }

    public void setDatabaseChanged(boolean isDatabaseChanged) {
        this.isDatabaseChanged = isDatabaseChanged;
    }
}
