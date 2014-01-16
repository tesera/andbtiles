package com.tesera.andbtiles.databases;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;
import com.tesera.andbtiles.pojos.MapMetadata;
import com.tesera.andbtiles.utils.Consts;

public class MBTilesDatabase {

    private SQLiteDatabase database;

    public MBTilesDatabase(String path) {
        database = SQLiteDatabase.openOrCreateDatabase(path, null);
    }

    public void close() {
        database.close();
    }

    public Tile getTile(int x, int y, int z) {
        String[] cols = new String[]{"tile_data"};
        String selection = "zoom_level = ? AND tile_column = ? AND tile_row = ?";

        // .mbtiles database use TSM coordinates
        // we need to switch to Google compatible coordinates
        // https://gist.github.com/tmcw/4954720
        String[] params = new String[]{String.valueOf(z), String.valueOf(x), String.valueOf(Math.pow(2, z) - y - 1)};
        Cursor cursor = database.query("tiles", cols, selection, params, null, null, null);

        // if tile is not present in the database use NO_TILE
        // the next attempt will be performed with exponential back-off
        Tile tile = TileProvider.NO_TILE;
        if (cursor.moveToFirst())
            tile = cursorToMapItem(cursor);
        // make sure to close the cursor
        cursor.close();
        return tile;
    }

    public MapMetadata getCenterAndZoom() {
        String[] cols = new String[]{"value"};
        String selection = "name like 'center'";
        Cursor cursor = database.query("metadata", cols, selection, null, null, null, null);
        MapMetadata mapMetadata = null;
        if (cursor.moveToFirst())
            mapMetadata = cursorToMapMetadata(cursor);

        cursor.close();
        return mapMetadata;
    }

    private Tile cursorToMapItem(Cursor cursor) {
        // get the blob data i.e. the .png image
        return new Tile(Consts.TILE_SIZE, Consts.TILE_SIZE, cursor.getBlob(cursor.getColumnIndex("tile_data")));
    }

    private MapMetadata cursorToMapMetadata(Cursor cursor) {
        String center;
        try {
            center = cursor.getString(0);
        } catch (Exception e) {
            return null;
        }
        if (center == null)
            return null;

        // the center values are in format lon,lat,zoom
        // ex. -63.1275,45.1936,9
        String[] splitCenterValues = center.split(",");
        MapMetadata mapMetadata = new MapMetadata();
        mapMetadata.setLon(Double.valueOf(splitCenterValues[0]));
        mapMetadata.setLat(Double.valueOf(splitCenterValues[1]));
        mapMetadata.setZoom(Integer.valueOf(splitCenterValues[2]));
        return mapMetadata;
    }
}
