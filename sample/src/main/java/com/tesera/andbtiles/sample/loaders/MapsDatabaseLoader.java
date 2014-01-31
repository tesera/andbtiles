package com.tesera.andbtiles.sample.loaders;


import android.content.AsyncTaskLoader;
import android.content.Context;

import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.pojos.MapItem;

import java.util.List;

public class MapsDatabaseLoader extends AsyncTaskLoader<List<MapItem>> {

    private Andbtiles andbtiles;
    private List<MapItem> mapsList;

    public MapsDatabaseLoader(Context context) {
        super(context);
        andbtiles = new Andbtiles(context);
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
        mapsList = andbtiles.getMaps();
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
