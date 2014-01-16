package com.tesera.andbtiles.providers;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;
import com.tesera.andbtiles.databases.MBTilesDatabase;
import com.tesera.andbtiles.pojos.MapMetadata;

public class MBTilesProvider implements TileProvider {

    private MBTilesDatabase mDatabase;

    public MBTilesProvider(String path) {
        mDatabase = new MBTilesDatabase(path);
    }

    public void closeDatabase() {
        mDatabase.close();
    }

    public MapMetadata getCenterAndZoom() {
        return mDatabase.getCenterAndZoom();
    }

    @Override
    public Tile getTile(int x, int y, int z) {
        // query database with coordinates and zoom level
        return mDatabase.getTile(x, y, z);
    }
}
