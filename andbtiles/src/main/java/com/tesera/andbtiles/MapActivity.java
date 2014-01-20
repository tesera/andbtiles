package com.tesera.andbtiles;

import android.app.Activity;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.tesera.andbtiles.pojos.MapMetadata;
import com.tesera.andbtiles.providers.MBTilesProvider;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.TilesContract;

import java.io.File;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class MapActivity extends Activity {

    GoogleMap mMap;
    MBTilesProvider mMBTilesProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // get the database path
        String name = getIntent().getStringExtra(Consts.EXTRA_NAME);
        if (name == null) {
            Crouton.makeText(this, getString(R.string.crouton_invalid_file), Style.ALERT).show();
            finish();
            return;
        }

        // set the action bar title
        getActionBar().setTitle(name);

        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        mMap.setMapType(GoogleMap.MAP_TYPE_NONE);

        // display tiles
        mMBTilesProvider = new MBTilesProvider(this, TilesContract.CONTENT_URI + name + File.separator + TilesContract.TABLE_TILES);
        // create new TileOverlayOptions instance.
        TileOverlayOptions tileOverlayOptions = new TileOverlayOptions();
        // set the tile provider to your custom implementation.
        tileOverlayOptions.tileProvider(mMBTilesProvider);
        mMap.addTileOverlay(tileOverlayOptions);

        // at this point the map will not be focused
        // use the metadata to get the map center and default zoom level
        MapMetadata mapMetadata = mMBTilesProvider.getCenterAndZoom(TilesContract.CONTENT_URI + name + File.separator + TilesContract.TABLE_METADATA);

        // if no metadata is present don't display the map
        // TODO maybe find a random tile and center it on certain zoom level
        if (mapMetadata == null)
            return;

        // center and zoom to map
        CameraUpdate center = CameraUpdateFactory.newLatLng(new LatLng(mapMetadata.getLat(), mapMetadata.getLon()));
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(mapMetadata.getZoom());
        mMap.moveCamera(center);
        mMap.animateCamera(zoom);
    }
}
