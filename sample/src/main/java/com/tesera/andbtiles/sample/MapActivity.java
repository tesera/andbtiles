package com.tesera.andbtiles.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.MapMetadata;
import com.tesera.andbtiles.sample.providers.MBTilesProvider;
import com.tesera.andbtiles.sample.utils.Const;
import com.tesera.andbtiles.utils.TilesContract;

import java.io.File;


public class MapActivity extends Activity {

    private String mMapName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // get the database path
        mMapName = getIntent().getStringExtra(Const.EXTRA_NAME);
        if (mMapName == null) {
            Toast.makeText(this, getString(R.string.crouton_invalid_file), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // set the action bar title
        getActionBar().setTitle(mMapName);

        GoogleMap map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        map.setMapType(GoogleMap.MAP_TYPE_NONE);

        // display tiles
        MBTilesProvider mbTilesProvider = new MBTilesProvider(this, TilesContract.CONTENT_URI + mMapName + File.separator + TilesContract.TABLE_TILES);
        // create new TileOverlayOptions instance.
        TileOverlayOptions tileOverlayOptions = new TileOverlayOptions();
        // set the tile provider to your custom implementation.
        tileOverlayOptions.tileProvider(mbTilesProvider);
        map.addTileOverlay(tileOverlayOptions);

        // at this point the map will not be focused
        // use the metadata to get the map center and default zoom level
        MapMetadata mapMetadata = mbTilesProvider.getCenterAndZoom(TilesContract.CONTENT_URI + mMapName + File.separator + TilesContract.TABLE_METADATA);
        // if no metadata is present don't display the map
        // TODO maybe find a random tile and center it on certain zoom level
        if (mapMetadata == null)
            return;

        // center and zoom to map
        CameraUpdate center = CameraUpdateFactory.newLatLng(new LatLng(mapMetadata.getLat(), mapMetadata.getLon()));
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(mapMetadata.getZoom());
        map.moveCamera(center);
        map.animateCamera(zoom);
    }

    @Override
    protected void onPause() {
        // FIXME
        // we kill the process so the content provider connection is released
        // needed for opening a new map from different database
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.map, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_detele:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.hint_map_delete_title)
                        .setMessage(R.string.hint_map_delete_message)
                        .setNegativeButton(R.string.btn_cancel, null)
                        .setPositiveButton(R.string.btn_confirm, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Andbtiles andbtiles = new Andbtiles(MapActivity.this);
                                MapItem mapForDeletion = new MapItem();
                                mapForDeletion.setName(mMapName);
                                if (andbtiles.deleteMap(mapForDeletion)) {
                                    Toast.makeText(MapActivity.this, R.string.crouton_map_deleted, Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            }
                        }
                        ).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
