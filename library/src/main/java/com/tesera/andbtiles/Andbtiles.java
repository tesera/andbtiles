package com.tesera.andbtiles;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Patterns;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tesera.andbtiles.callbacks.AndbtilesCallback;
import com.tesera.andbtiles.databases.MBTilesDatabase;
import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.exceptions.AndbtilesException;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.services.HarvesterService;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Andbtiles {

    private final Context mContext;
    private MapsDatabase mMapsDatabase;
    private SQLiteDatabase mDatabase;
    private ExecutorService mExecutorService;
    private Gson mGson;
    private HashMap<String, MapItem> mRecentMaps;

    /**
     * Class constructor.
     *
     * @param context sets the application context
     */
    public Andbtiles(Context context) {
        this.mContext = context;
        mMapsDatabase = new MapsDatabase(context);
        mGson = new Gson();
        mExecutorService = Executors.newSingleThreadExecutor();
        mRecentMaps = new HashMap<>();
    }

    /**
     * Returns a tile data as bytes array from the database uniquely identified by the mapID with specified zoom and coordinates
     * <p/>
     * This method always returns immediately or throws an exception if something went wrong
     * saved in the database or null if there is none.
     *
     * @param mapId unique database identifier
     * @param z     the zoom level
     * @param x     the x tile coordinate
     * @param y     the y tile coordinate
     * @return bytes array representing the requested tile
     * @see com.tesera.andbtiles.exceptions.AndbtilesException
     */
    public byte[] getTile(String mapId, int z, int x, int y) throws AndbtilesException {
        System.out.println(mapId);
        MapItem mapItem;
        if (mRecentMaps.containsKey(mapId))
            mapItem = mRecentMaps.get(mapId);
        else {
            mMapsDatabase.open();
            mapItem = mMapsDatabase.findMapById(mapId);
            if (mapItem == null) {
                // try a local file instead since
                // the remote files have ids like <user>.<mapname>
                // the remote files have ids like <mapname>.mbtiles
                mapId = mapId.split("\\.")[1] + ".mbtiles";
                mapItem = mMapsDatabase.findMapById(mapId);
                if (mapItem == null)
                    throw new AndbtilesException("Map <" + mapId + "> with not found in maps.");
            }
            mMapsDatabase.close();
            mRecentMaps.put(mapId, mapItem);
        }

        // open the database by id
        if (mDatabase == null)
            mDatabase = SQLiteDatabase.openOrCreateDatabase(mapItem.getPath(), null);
        // make the database query with the TSM coordinates
        String[] projection = new String[]{TilesContract.COLUMN_TILE_DATA};
        String selection = TilesContract.COLUMN_ZOOM_LEVEL + " = ? AND "
                + TilesContract.COLUMN_TILE_COLUMN + "= ? AND "
                + TilesContract.COLUMN_TILE_ROW + "= ?";
        String[] selectionArgs = new String[]{String.valueOf(z), String.valueOf(x), String.valueOf((int) (Math.pow(2, z) - y - 1))};

        // handle tile request
        switch (mapItem.getCacheMode()) {
            case Consts.CACHE_NO:
                return null;
            case Consts.CACHE_ON_DEMAND:
                // try to find the tile in the database
                Cursor cursor = mDatabase.query(TilesContract.TABLE_TILES, projection, selection, selectionArgs, null, null, null);
                if (cursor.moveToFirst())
                    return cursor.getBlob(cursor.getColumnIndex(TilesContract.COLUMN_TILE_DATA));
                else {
                    // make final variables for accessing from worker scope
                    final TileJson tileJsonFinal = mGson.fromJson(mapItem.getJsonData(), TileJson.class);
                    final int zFinal = z;
                    final int xFinal = x;
                    final int yFinal = y;
                    // cache the on demand tiles
                    mExecutorService.submit(
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    // fetch tile from the web
                                    final byte[] tileDataFinal = getTileBytes(zFinal, xFinal, yFinal, tileJsonFinal);
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
                            }));
                    // return the cursor containing the tile_data
                    return null;
                }
            case Consts.CACHE_FULL:
            case Consts.CACHE_DATA:
                cursor = mDatabase.query(TilesContract.TABLE_TILES, projection, selection, selectionArgs, null, null, null);
                if (cursor.moveToFirst())
                    return cursor.getBlob(cursor.getColumnIndex(TilesContract.COLUMN_TILE_DATA));
            default:
                break;
        }
        return null;
    }

    /**
     * Returns all tha maps saved as providers.
     * <p/>
     * This method always returns immediately and returns a list of all MapItems
     * saved in the database or null if there is none.
     *
     * @return list of saved MapItem objects or null if no map providers are added
     * @see com.tesera.andbtiles.pojos.MapItem
     */
    public List<MapItem> getMaps() {
        MapsDatabase mapsDatabase = new MapsDatabase(mContext);
        try {
            mapsDatabase.open();
        } catch (Exception e) {
            return null;
        }
        List<MapItem> mapsList = mapsDatabase.getAllItems();
        // check if file is still on the specified path
        List<MapItem> itemsForDeletion = new ArrayList<>();
        for (MapItem item : mapsList) {
            // skip if no or empty path - no private tiles data web URL path - not a local file
            if (item.getPath() == null || item.getPath().isEmpty() || item.getPath().matches(Patterns.WEB_URL.pattern()))
                continue;

            // check if the local file is in storage
            File mbTileFile = new File(item.getPath());
            if (!mbTileFile.exists())
                itemsForDeletion.add(item);
        }
        // delete items that don't have valid path
        // get the new list of maps
        if (!itemsForDeletion.isEmpty()) {
            mapsDatabase.deleteItems(itemsForDeletion);
            mapsList = mapsDatabase.getAllItems();
        }
        mapsDatabase.close();
        return mapsList;
    }

    /**
     * Adds local .mbtiles file as map provider.
     * The pathToMbTilesFile argument must specify an absolute path to a
     * file on the external storage that has an .mbtiles file extension.
     * <p/>
     * This method always returns immediately or throws an exception if the file
     * is not found on the storage system or the storage system is not mounted.
     *
     * @param pathToMbTilesFile an absolute path to the .mbtiles file on the storage system
     * @throws com.tesera.andbtiles.exceptions.AndbtilesException
     */
    public void addLocalMbTilesProvider(String pathToMbTilesFile) throws AndbtilesException {
        // try to find the file with the specified path
        File mbTilesFile = new File(pathToMbTilesFile);
        if (!mbTilesFile.exists())
            throw new AndbtilesException(String.format(mContext.getString(R.string.exception_file_not_found), pathToMbTilesFile));

        // create new map item for insertion
        MapItem mapItem = new MapItem();
        mapItem.setId(mbTilesFile.getName());
        mapItem.setPath(mbTilesFile.getAbsolutePath());
        mapItem.setName(mbTilesFile.getName());
        mapItem.setCacheMode(Consts.CACHE_DATA);
        mapItem.setSize(mbTilesFile.length());

        insertMapItem(mapItem);
    }

    /**
     * Adds a remote .mbtiles file as map provider. The file is downloaded in the process.
     * The urlToMbTilesFile argument must specify a valid URL to a
     * file on the web that has an .mbtiles file extension.
     * <p/>
     * This method executes in a background thread and informs the main thread via the callback.
     *
     * @param urlToMbTilesFile an absolute URL to the .mbtiles file on the web
     * @param callback         a callback for returning the background thread status
     * @see com.tesera.andbtiles.callbacks.AndbtilesCallback
     */
    public void addRemoteMbilesProvider(String urlToMbTilesFile, AndbtilesCallback callback) {
        // do a URL and extension check
        if (!urlToMbTilesFile.matches(Patterns.WEB_URL.pattern()) || !urlToMbTilesFile.endsWith(Consts.EXTENSION_MBTILES))
            callback.onError(new AndbtilesException
                    (String.format(mContext.getString(R.string.exception_invalid_url_mbtiles), urlToMbTilesFile)));

        downloadMbTilesFile(mContext, urlToMbTilesFile, callback);
    }

    /**
     * Adds a remote map provider from a TileJSON endpoint.
     * The urlToJsonTileEndpoint argument must specify a valid URL to a
     * TileJSON endpoint on the web that has a map named like the mapName argument.
     * The cache method must be specified as one of the following:
     * <p/>
     * Consts.CACHE_NO - the provider acts as simple proxy
     * <br/>
     * Consts.CACHE_ON_DEMAND - the provider caches only the requested tiles
     * <br/>
     * Consts.CACHE_FULL - all tiles are harvested
     * <br/>
     * Consts.CACHE_DATA_ONLY - only the private data (.mbtiles) is downloaded
     * <p/>
     * This method executes in a background thread and informs the main thread via the callback.
     *
     * @param urlToJsonTileEndpoint an absolute URL to the TileJSON endpoint on the web
     * @param mapId                 the unique id of a map that can be found at the endpoint
     * @param cacheMethod           one of the available cache methods
     * @param callback              a callback for returning the background thread status
     * @see com.tesera.andbtiles.utils.Consts
     * @see com.tesera.andbtiles.callbacks.AndbtilesCallback
     */
    public void addRemoteJsonTileProvider(String urlToJsonTileEndpoint, String mapId, int cacheMethod, AndbtilesCallback callback) {
        // do a URL and extension check
        if (!urlToJsonTileEndpoint.matches(Patterns.WEB_URL.pattern()) || !urlToJsonTileEndpoint.endsWith(Consts.EXTENSION_JSON))
            callback.onError(new AndbtilesException(
                    String.format(mContext.getString(R.string.exception_invalid_url_jsontile), urlToJsonTileEndpoint)));

        ProcessTileJson task = new ProcessTileJson(callback);
        task.execute(urlToJsonTileEndpoint, mapId, "" + cacheMethod);
    }

    /**
     * Adds a remote map provider from a TileJSON endpoint.
     * The urlToJsonTileEndpoint argument must specify a valid URL to a
     * TileJSON endpoint on the web that has a map named like the mapName argument.
     * The cache method must be specified as one of the following:
     * <p/>
     * Consts.CACHE_NO - the provider acts as simple proxy
     * <br/>
     * Consts.CACHE_ON_DEMAND - the provider caches only the requested tiles
     * <br/>
     * Consts.CACHE_FULL - all tiles are harvested
     * <br/>
     * Consts.CACHE_DATA_ONLY - only the private data (.mbtiles) is downloaded
     * <p/>
     * The minZoom and maxZoom arguments are only taken into consideration when
     * CACHE_FULL is selected for caching.
     * This method executes in a background thread and informs the main thread via the callback.
     *
     * @param urlToJsonTileEndpoint an absolute URL to the TileJSON endpoint on the web
     * @param mapId                 the unique id of a map that can be found at the endpoint
     * @param cacheMethod           one of the available cache methods
     * @param minZoom               the minimum zoom as a harvest start point
     * @param maxZoom               the maximum zoom as a harvest endpoint
     * @param callback              a callback for returning the background thread status
     * @see com.tesera.andbtiles.utils.Consts
     * @see com.tesera.andbtiles.callbacks.AndbtilesCallback
     */
    public void addRemoteJsonTileProvider(String urlToJsonTileEndpoint, String mapId, int cacheMethod, int minZoom, int maxZoom, AndbtilesCallback callback) {
        // do a URL and extension check
        if (!urlToJsonTileEndpoint.matches(Patterns.WEB_URL.pattern()) || !urlToJsonTileEndpoint.endsWith(Consts.EXTENSION_JSON))
            callback.onError(new AndbtilesException(
                    String.format(mContext.getString(R.string.exception_invalid_url_jsontile), urlToJsonTileEndpoint)));

        ProcessTileJson task = new ProcessTileJson(callback);
        task.execute(urlToJsonTileEndpoint, mapId, "" + cacheMethod, "" + minZoom, "" + maxZoom);
    }

    /**
     * Deletes a map provider from the database.
     * The mapForDeletion argument must have a valid name as a map identifier.
     * <p/>
     * This method always returns immediately.
     *
     * @param mapForDeletion MapItem object with name specified for deletion
     * @return true if the deletion was successful, false otherwise
     */
    public boolean deleteMap(MapItem mapForDeletion) {
        MapsDatabase mapsDatabase = new MapsDatabase(mContext);
        try {
            mapsDatabase.open();
        } catch (Exception e) {
            return false;
        }
        return mapsDatabase.deleteItem(mapForDeletion);
    }

    /**
     * Updates a map provider from the database.
     * The mapForDeletion argument must have a valid name in order to update the other values.
     * <p/>
     * This method always returns immediately.
     *
     * @param mapForUpdate MapItem object with the parameters that need to be updated
     * @return true if the update was successful, false otherwise
     */
    public boolean updateMap(MapItem mapForUpdate) {
        MapsDatabase mapsDatabase = new MapsDatabase(mContext);
        try {
            mapsDatabase.open();
        } catch (Exception e) {
            return false;
        }
        return mapsDatabase.updateItem(mapForUpdate);
    }

    // helper function for inserting map items into the database
    private void insertMapItem(MapItem mapItem) {
        // insert the file in the database
        MapsDatabase mapsDatabase = new MapsDatabase(mContext);
        mapsDatabase.open();
        mapsDatabase.insertItems(mapItem);
        mapsDatabase.close();
    }

    // helper function for downloading a single mbtiles file from the internet
    private void downloadMbTilesFile(final Context context, final String urlToFile, final AndbtilesCallback callback) {
        final DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        // the request should follow the provided URL
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(urlToFile));
        // the download destination should be on the external SD card inside the app folder
        request.setDestinationInExternalPublicDir(Consts.FOLDER_ROOT, FilenameUtils.getName(urlToFile));
        final long enqueue = downloadManager.enqueue(request);
        // register a broadcast receiver to listen to download complete event
        final BroadcastReceiver receiver = new BroadcastReceiver() {
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
                                callback.onError(e);
                                return;
                            }

                            // create new map item
                            MapItem mapItem = new MapItem();
                            mapItem.setId(mbTilesFile.getName());
                            mapItem.setPath(mbTilesFile.getAbsolutePath());
                            mapItem.setName(mbTilesFile.getName());
                            mapItem.setCacheMode(Consts.CACHE_DATA);
                            mapItem.setSize(mbTilesFile.length());

                            // insert the file in the database
                            MapsDatabase mapsDatabase = new MapsDatabase(context);
                            try {
                                mapsDatabase.open();
                            } catch (Exception e) {
                                e.printStackTrace();
                                callback.onError(e);
                                return;
                            }
                            mapsDatabase.insertItems(mapItem);
                            mapsDatabase.close();

                            callback.onSuccess();
                        }

                    } else
                        callback.onError(new AndbtilesException(
                                String.format(mContext.getString(R.string.exception_download_error), urlToFile)));
                }
                // unregister the receiver since the download is done
                context.unregisterReceiver(this);
            }
        };
        // unregister the receiver once done
        context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
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

    // helper class for processing maps obtained from TileJSON endpoint
    private class ProcessTileJson extends AsyncTask<String, Void, String> {

        private final AndbtilesCallback mCallback;

        private ProcessTileJson(AndbtilesCallback callback) {
            this.mCallback = callback;
        }

        @Override
        protected String doInBackground(String... params) {
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
                    params[1] = tileJson.getId();
                    mTileJsonList.add(tileJson);
                }

                // find the map with the given name
                for (TileJson tileJson : mTileJsonList) {
                    if (tileJson.getId().equalsIgnoreCase(params[1])) {
                        MapItem mapItem = new MapItem();
                        mapItem.setId(tileJson.getId());
                        mapItem.setName(tileJson.getName());
                        mapItem.setCacheMode(Integer.parseInt(params[2]));
                        mapItem.setPath(tileJson.getDownload());
                        mapItem.setSize(tileJson.getFilesize() == null ? 0 : tileJson.getFilesize().longValue());
                        mapItem.setJsonData(gson.toJson(tileJson, TileJson.class));

                        // create a local path for database
                        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                                + Consts.FOLDER_ROOT + File.separator + mapItem.getId();

                        switch (Integer.parseInt(params[2])) {
                            case Consts.CACHE_NO:
                                // insert the file in the database
                                insertMapItem(mapItem);
                                break;
                            case Consts.CACHE_ON_DEMAND:
                                // insert metadata
                                mapItem.setPath(path);
                                insertMetadata(mapItem);
                                // insert the file in the database
                                insertMapItem(mapItem);
                                break;
                            case Consts.CACHE_FULL:
                                // set the min and max zoom if specified
                                if (params[3] != null) {
                                    tileJson.setMinzoom(Integer.valueOf(params[3]));
                                    tileJson.setMaxzoom(Integer.valueOf(params[4]));
                                    mapItem.setJsonData(gson.toJson(tileJson, TileJson.class));
                                }
                                // insert metadata
                                mapItem.setPath(path);
                                insertMetadata(mapItem);
                                // start harvesting
                                Intent harvesterService = new Intent(mContext, HarvesterService.class);
                                harvesterService.putExtra(Consts.EXTRA_JSON, new Gson().toJson(mapItem, MapItem.class));
                                mContext.startService(harvesterService);
                                break;
                            case Consts.CACHE_DATA:
                                if (mapItem.getPath() == null)
                                    return "Map doesn't have private data: " + params[1];
                                downloadMbTilesFile(mContext, mapItem.getPath(), mCallback);
                                break;
                        }
                        return null;
                    }
                }
                return "Map not found in TileJSON endpoint: " + params[1];
            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String error) {
            if (error == null)
                mCallback.onSuccess();
            else
                mCallback.onError(new AndbtilesException(error));
            super.onPostExecute(error);
        }

        private void insertMetadata(MapItem mapItem) {
            // delete previous database because conflicts occur
            File database = new File(mapItem.getPath());
            if (database.exists())
                database.delete();

            database = new File(mapItem.getPath() + "-journal");
            if (database.exists())
                database.delete();

            MBTilesDatabase mbTilesDatabase = new MBTilesDatabase(mContext, mapItem.getPath());
            mbTilesDatabase.open();

            // fill the metadata table
            TileJson tileJson = new Gson().fromJson(mapItem.getJsonData(), TileJson.class);
            Map<String, String> metadataMap = new HashMap<>();
            metadataMap.put("bounds", Arrays.toString(tileJson.getBounds().toArray()).replace("[", "").replace("]", "").replace(" ", ""));
            metadataMap.put("center", Arrays.toString(tileJson.getCenter().toArray()).replace("[", "").replace("]", "").replace(" ", ""));
            metadataMap.put("minzoom", tileJson.getMinzoom().toString());
            metadataMap.put("maxzoom", tileJson.getMaxzoom().toString());
            metadataMap.put("name", tileJson.getName());
            metadataMap.put("description", tileJson.getDescription());
            metadataMap.put("attribution", tileJson.getAttribution());
            metadataMap.put("template", "");
            metadataMap.put("version", tileJson.getVersion());
            try {
                mbTilesDatabase.insertMetadata(metadataMap);
            } catch (SQLiteConstraintException e) {
                // metadata already exists so skip this
            }
            mbTilesDatabase.close();
        }
    }
}
