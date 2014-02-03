package com.tesera.andbtiles.sample.providers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;
import com.tesera.andbtiles.pojos.MapMetadata;
import com.tesera.andbtiles.sample.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;

public class MBTilesProvider implements TileProvider {

    private final Context mContext;
    private final String mContentProviderPath;

    public MBTilesProvider(Context context, String path) {
        mContext = context;
        mContentProviderPath = path;
    }

    @Override
    public Tile getTile(int x, int y, int z) {
        // query the provider for a tile specified by x, y and z coordinates
        String[] projection = new String[]{TilesContract.COLUMN_TILE_DATA};
        String selection = TilesContract.COLUMN_ZOOM_LEVEL + " = ? AND "
                + TilesContract.COLUMN_TILE_COLUMN + "= ? AND "
                + TilesContract.COLUMN_TILE_ROW + "= ?";
        String[] selectionArgs = new String[]{String.valueOf(z), String.valueOf(x), String.valueOf((int) (Math.pow(2, z) - y - 1))};
        // .mbtiles database use TSM coordinates
        // we need to switch to Google compatible coordinates
        // https://gist.github.com/tmcw/4954720
        Cursor cursor = mContext.getContentResolver().query(Uri.parse(mContentProviderPath), projection, selection, selectionArgs, null);
        Tile tile = TileProvider.NO_TILE;
        if (cursor.moveToFirst())
            tile = cursorToMapItem(cursor);
        // make sure to close the cursor
        cursor.close();
        return tile;
    }

    public MapMetadata getCenterAndZoom(String path) {
        // query the provider for the center coordinates and default zoom level
        String[] projection = new String[]{TilesContract.COLUMN_VALUE};
        String selection = TilesContract.COLUMN_NAME + " like ?";
        String[] selectionArgs = new String[]{"center"};

        Cursor cursor = mContext.getContentResolver().query(Uri.parse(path), projection, selection, selectionArgs, null);
        // no metadata table is present
        if (cursor == null)
            return null;

        // return the metadata
        MapMetadata mapMetadata = null;
        if (cursor.moveToFirst())
            mapMetadata = cursorToMapMetadata(cursor);

        cursor.close();
        return mapMetadata;
    }

    private Tile cursorToMapItem(Cursor cursor) {
        // get the blob data i.e. the .png image
        return new Tile(Consts.TILE_SIZE, Consts.TILE_SIZE, cursor.getBlob(cursor.getColumnIndex(TilesContract.COLUMN_TILE_DATA)));
    }

    private MapMetadata cursorToMapMetadata(Cursor cursor) {
        String center;
        try {
            center = cursor.getString(cursor.getColumnIndex(TilesContract.COLUMN_VALUE));
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
