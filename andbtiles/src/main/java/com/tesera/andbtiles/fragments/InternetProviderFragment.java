package com.tesera.andbtiles.fragments;


import android.app.Activity;
import android.app.DownloadManager;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tesera.andbtiles.MainActivity;
import com.tesera.andbtiles.R;
import com.tesera.andbtiles.adapters.MBTilesAdapter;
import com.tesera.andbtiles.adapters.MapsAdapter;
import com.tesera.andbtiles.callbacks.DatabaseChangeCallback;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.Utils;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class InternetProviderFragment extends Fragment {

    private Button mFetch;
    private EditText mUrl;
    private ListView mMBTilesList;
    private Menu mMenu;

    private String mDownloadPath;

    private DatabaseChangeCallback mCallback;

    @Override
    public void onAttach(Activity activity) {
        mCallback = (DatabaseChangeCallback) activity;
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // this fragment has it's own menu
        setHasOptionsMenu(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.fragment_internet, null);

        mMBTilesList = (ListView) contentView.findViewById(android.R.id.list);
        mMBTilesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MapItem mapItem = (MapItem) parent.getAdapter().getItem(position);
                if (mapItem.getJsonData() == null) {
                    // get the download path
                    mDownloadPath = mapItem.getPath();
                    // check if the file already exists on the sd
                    File mbTilesFile = new File(Environment.getExternalStorageDirectory() + File.separator
                            + Consts.FOLDER_ROOT + File.separator + FilenameUtils.getName(mDownloadPath));

                    if (mbTilesFile.exists()) {
                        // create the map item file
                        mapItem = new MapItem();
                        mapItem.setPath(mbTilesFile.getAbsolutePath());
                        mapItem.setName(mbTilesFile.getName());
                        mapItem.setCacheMode(Consts.CACHE_FULL);
                        mapItem.setSize(mbTilesFile.length());
                        // check if it is already added
                        if (Utils.isMapInDatabase(getActivity(), mapItem)) {
                            Crouton.makeText(getActivity(), getString(R.string.crouton_map_exsists), Style.INFO).show();
                            return;
                        }

                        Crouton.makeText(getActivity(), getString(R.string.crouton_file_exists), Style.INFO).show();
                        selectFile(mapItem);
                        return;
                    }
                    selectFile(null);
                } else {
                    // this is a map from TileJSON so advance to cache options screen
                    CacheSettingsFragment fragment = new CacheSettingsFragment();
                    fragment.setmMapItem(mapItem);
                    getFragmentManager().beginTransaction()
                            .replace(R.id.container, fragment)
                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                            .addToBackStack(null)
                            .commit();
                }
            }
        });
        mFetch = (Button) contentView.findViewById(R.id.btn_fetch);
        mFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // validate the url
                if (!validate()) {
                    mUrl.setError(getString(R.string.hint_url_error));
                }

                // hide the keyboard and execute the fetch
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mUrl.getWindowToken(), 0);

                FetchURL task = new FetchURL();
                task.execute(mUrl.getText().toString());
            }
        });

        mUrl = (EditText) contentView.findViewById(R.id.edit_url);

        return contentView;
    }

    @Override
    public void onResume() {
        getActivity().getActionBar().setTitle(getString(R.string.title_internet_provider));
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);
        // save the menu as a class variable since we toggle the menu visibility often
        mMenu = menu;
        // setup the search view
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setQueryHint(getString(R.string.action_filter));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mMenu.findItem(R.id.action_search).collapseActionView();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // filter results only if we have an adapter
                if (mMBTilesList != null
                        || mMBTilesList.getAdapter() != null
                        || ((MBTilesAdapter) mMBTilesList.getAdapter()).getFilter() != null)
                    ((MapsAdapter) mMBTilesList.getAdapter()).getFilter().filter(newText);
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean validate() {
        String url = mUrl.getText().toString();
        return (url.endsWith(Consts.EXTENSION_MBTILES) || url.endsWith(Consts.EXTENSION_JSON)) && url.matches(Patterns.WEB_URL.pattern());
    }

    private void selectFile(final MapItem mapItem) {

        View actionBarButtons;
        if (mapItem == null)
            actionBarButtons = getActivity().getLayoutInflater().inflate(R.layout.action_bar_custom_download, new LinearLayout(getActivity()), false);
        else
            actionBarButtons = getActivity().getLayoutInflater().inflate(R.layout.action_bar_custom_confirm, new LinearLayout(getActivity()), false);

        View cancelActionView = actionBarButtons.findViewById(R.id.action_cancel);
        cancelActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unselectFile();
            }
        });

        View doneActionView = actionBarButtons.findViewById(R.id.action_done);
        doneActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unselectFile();

                // save local file or download remote one
                if (mapItem != null) {
                    // try to save it to database
                    if (!Utils.saveMapToDatabase(getActivity(), mapItem)) {
                        Crouton.makeText(getActivity(), getString(R.string.crouton_database_error), Style.ALERT).show();
                        return;
                    }
                    // return to previous screen, notify dataSetChanged and inform the user
                    Crouton.makeText(getActivity(), getString(R.string.crouton_map_added), Style.INFO).show();
                    mCallback.onDatabaseChanged();
                    getFragmentManager().popBackStack();
                    return;
                }

                // start download using the download manager
                final DownloadManager downloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
                // the request should follow the provided URL
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mDownloadPath));
                // the download destination should be on the external SD card inside the app folder
                request.setDestinationInExternalPublicDir(Consts.FOLDER_ROOT, FilenameUtils.getName(mDownloadPath));
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
                                    mapItem.setCacheMode(Consts.CACHE_FULL);
                                    mapItem.setSize(mbTilesFile.length());

                                    // try to save it to database
                                    if (!Utils.saveMapToDatabase(getActivity(), mapItem)) {
                                        // since this is a long running operation the activity/fragment may not be visible upon completion
                                        if (!isVisible())
                                            return;
                                        Crouton.makeText(getActivity(), getString(R.string.crouton_database_error), Style.ALERT).show();
                                        return;
                                    }

                                    // return to previous screen, notify dataSetChanged and inform the user
                                    try {
                                        Crouton.makeText(getActivity(), getString(R.string.crouton_map_added), Style.INFO).show();
                                        mCallback.onDatabaseChanged();
                                        getFragmentManager().popBackStack();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        mCallback.onDatabaseChanged();
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

                getActivity().registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        });

        // prepare the action bar for custom view
        getActivity().getActionBar().setHomeButtonEnabled(false);
        getActivity().getActionBar().setDisplayShowHomeEnabled(false);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
        getActivity().getActionBar().setDisplayShowTitleEnabled(false);

        getActivity().getActionBar().setDisplayShowCustomEnabled(true);
        getActivity().getActionBar().setCustomView(actionBarButtons);
        // hide the filter icon
        mMenu.setGroupVisible(R.id.group_search, false);
    }

    private void unselectFile() {
        // return to normal action bar state
        getActivity().getActionBar().setHomeButtonEnabled(true);
        getActivity().getActionBar().setDisplayShowHomeEnabled(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        getActivity().getActionBar().setDisplayShowTitleEnabled(true);

        getActivity().getActionBar().setDisplayShowCustomEnabled(false);
        getActivity().getActionBar().setCustomView(null);

        // un-select any selected files
        mMenu.setGroupVisible(R.id.group_search, true);
        mMBTilesList.setItemChecked(mMBTilesList.getCheckedItemPosition(), false);
    }

    private class FetchURL extends AsyncTask<String, Void, List<MapItem>> {

        @Override
        protected void onPreExecute() {
            mFetch.setEnabled(false);
            mFetch.setText(getString(R.string.btn_fetching));
            super.onPreExecute();
        }

        @Override
        protected List<MapItem> doInBackground(String... params) {
            if (params[0].endsWith(Consts.EXTENSION_MBTILES)) {
                // set the name and the path of the file
                MapItem mapItem = new MapItem();
                mapItem.setName(FilenameUtils.getName(params[0]));
                mapItem.setPath(params[0]);
                mapItem.setSize(getFileSize(params[0]));
                List<MapItem> adapterList = new ArrayList<>();
                adapterList.add(mapItem);
                return mapItem.getSize() == 0 ? null : adapterList;
            }
            // display the list of maps from the TileJSON otherwise
            try {
                HttpClient client = new DefaultHttpClient();
                // execute GET method
                HttpGet request = new HttpGet(params[0]);
                HttpResponse response = client.execute(request);

                // get the response
                HttpEntity responseEntity = response.getEntity();
                String jsonResponse = EntityUtils.toString(responseEntity);

                // parse the response
                List<TileJson> mTileJsonList = new ArrayList<>();
                Gson gson = new Gson();
                if (jsonResponse.startsWith("[")) {
                    // this is a JSON array
                    Type listOfDays = new TypeToken<List<TileJson>>() {
                    }.getType();
                    mTileJsonList = gson.fromJson(jsonResponse, listOfDays);
                } else {
                    // this is a JSON object
                    TileJson tileJson = gson.fromJson(jsonResponse, TileJson.class);
                    mTileJsonList.add(tileJson);
                }

                // construct adapter items from the parsed JSON
                List<MapItem> adapterList = new ArrayList<>();
                for (TileJson tileJson : mTileJsonList) {
                    MapItem mapItem = new MapItem();
                    mapItem.setName(tileJson.getName());
                    mapItem.setPath(tileJson.getDownload());
                    mapItem.setJsonData(gson.toJson(tileJson, TileJson.class));
                    if (tileJson.getFilesize() != null)
                        mapItem.setSize(tileJson.getFilesize().longValue());
                    adapterList.add(mapItem);
                }
                return adapterList;
            } catch (Exception e) {
                // an internet connection error
                e.printStackTrace();
                return null;
            }
        }

        private long getFileSize(String path) {
            if (path == null)
                return 0;
            try {
                // try go get the file size
                URL url = new URL(path);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();
                String locationUrl = urlConnection.getHeaderField("location");
                // the trick is that MapBox hosts its files on Amazon servers
                // to get the file size we need to find the location first
                url = new URL(locationUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();
                // get as string since the file can exceed the Integer 32bit limit
                String contentLengthStr = urlConnection.getHeaderField("content-length");
                return Long.valueOf(contentLengthStr);
            } catch (Exception e) {
                // file size unknown
                e.printStackTrace();
                return 0;
            }
        }

        @Override
        protected void onPostExecute(List<MapItem> result) {
            if (!isAdded())
                return;

            // enable the fetch button again
            mFetch.setEnabled(true);
            mFetch.setText(getString(R.string.btn_fetch));
            if (result == null || result.size() == 0) {
                Crouton.makeText(getActivity(), getString(R.string.crouton_fetch_error), Style.ALERT).show();
                return;
            }
            // display the results
            MapsAdapter mapsAdapter = new MapsAdapter(getActivity(), result);
            mMBTilesList.setAdapter(mapsAdapter);

            super.onPostExecute(result);
        }
    }
}
