package com.tesera.andbtiles.loaders;


import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Patterns;

import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapsDatabaseLoader extends AsyncTaskLoader<List<MapItem>> {
    private MapsDatabase mapsDatabase;
    private List<MapItem> mapsList;

    public MapsDatabaseLoader(Context context) {
        super(context);
        mapsDatabase = new MapsDatabase(context);
    }

    @Override
    protected void onStartLoading() {
        if (mapsList != null)
            // use cached results
            deliverResult(mapsList);
        else
            // reload data
            forceLoad();
    }

    @Override
    protected void onStopLoading() {
        // cancel the ongoing load if any
        cancelLoad();
    }

    @Override
    public List<MapItem> loadInBackground() {
        // return all entries
        try {
            mapsDatabase.open();
        } catch (Exception e) {
            return null;
        }
        mapsList = mapsDatabase.getAllItems();
        // check if file is still on the specified path
        List<MapItem> itemsForDeletion = new ArrayList<>();
        for (MapItem item : mapsList) {
            // skip if
            // no or empty path - no private tiles data
            // web URL path - not a local file
            if(item.getPath() == null || item.getPath().isEmpty() || item.getPath().matches(Patterns.WEB_URL.pattern()))
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

    @Override
    public void deliverResult(List<MapItem> data) {
        // cache data
        mapsList = data;
        super.deliverResult(data);
    }

    @Override
    protected void onReset() {
        super.onReset();
        // stop loader if running
        onStopLoading();
        // invalidate cache
        mapsList = null;
    }

    @Override
    public void onCanceled(List<MapItem> data) {
        // try to cancel the current load and invalidate cache
        super.onCanceled(data);
        mapsList = null;
    }
}
