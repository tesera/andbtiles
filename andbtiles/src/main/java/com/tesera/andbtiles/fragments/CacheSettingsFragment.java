package com.tesera.andbtiles.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.tesera.andbtiles.R;
import com.tesera.andbtiles.callbacks.ActivityCallback;
import com.tesera.andbtiles.databases.MBTilesDatabase;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.services.HarvesterService;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.Utils;

import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class CacheSettingsFragment extends Fragment {

    private MapItem mMapItem;
    private RadioGroup mCacheGroup;

    private ActivityCallback mCallback;

    @Override
    public void onAttach(Activity activity) {
        mCallback = (ActivityCallback) activity;
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.fragment_cache_settings, null);
        mCacheGroup = (RadioGroup) contentView.findViewById(R.id.radio_cache);
        if (mMapItem.getSize() == 0)
            mCacheGroup.findViewById(R.id.radio_cache_data).setEnabled(false);

        ((RadioButton) mCacheGroup.getChildAt(0)).setChecked(true);
        ((TextView) contentView.findViewById(R.id.txt_name)).setText(mMapItem.getName());
        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        selectFile();
    }

    @Override
    public void onPause() {
        super.onPause();
        unselectFile();
    }

    public void selectFile() {
        View actionBarButtons = getActivity().getLayoutInflater().inflate(R.layout.action_bar_custom_confirm, new LinearLayout(getActivity()), false);

        View cancelActionView = actionBarButtons.findViewById(R.id.action_cancel);
        cancelActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFragmentManager().popBackStack();
            }
        });

        View doneActionView = actionBarButtons.findViewById(R.id.action_done);
        doneActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // set the cache method
                mMapItem.setCacheMode(mCacheGroup.indexOfChild(mCacheGroup.findViewById(mCacheGroup.getCheckedRadioButtonId())));
                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                        + Consts.FOLDER_ROOT + File.separator + mMapItem.getName() + "." + Consts.EXTENSION_MBTILES;
                // if the cache modes are On Demand or Full Cache create a database on the SD card and save the path
                switch (mMapItem.getCacheMode()) {
                    case Consts.CACHE_FULL:
                        insertMetadata(path);
                        // TODO add options fragment
                        Intent harvesterService = new Intent(getActivity(), HarvesterService.class);
                        harvesterService.putExtra(Consts.EXTRA_JSON, new Gson().toJson(mMapItem, MapItem.class));
                        getActivity().startService(harvesterService);
                        Toast.makeText(getActivity(), getString(R.string.crouton_harvesting), Toast.LENGTH_SHORT).show();
                        getFragmentManager().popBackStack();
                        getFragmentManager().popBackStack();
                        return;
                    case Consts.CACHE_ON_DEMAND:
                        insertMetadata(path);
                        mMapItem.setSize(new File(path).length());
                        mMapItem.setPath(path);
                        break;
                    case Consts.CACHE_DATA:
                        // start download using the download manager
                        mCallback.downloadFile(mMapItem.getPath());
                        return;
                }

                // check if the map is already added
                if (Utils.isMapInDatabase(getActivity(), mMapItem)) {
                    Toast.makeText(getActivity(), getString(R.string.crouton_map_exsists), Toast.LENGTH_SHORT).show();
                    return;
                }
                // try to save it to database
                if (!Utils.saveMapToDatabase(getActivity(), mMapItem)) {
                    Toast.makeText(getActivity(), getString(R.string.crouton_database_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                // return to previous screen, notify dataSetChanged and inform the user
                Toast.makeText(getActivity(), getString(R.string.crouton_map_added), Toast.LENGTH_SHORT).show();
                mCallback.onDatabaseChanged();
                getFragmentManager().popBackStack();
                getFragmentManager().popBackStack();
            }
        });

        // prepare the action bar for custom view
        getActivity().getActionBar().setHomeButtonEnabled(false);
        getActivity().getActionBar().setDisplayShowHomeEnabled(false);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
        getActivity().getActionBar().setDisplayShowTitleEnabled(false);

        getActivity().getActionBar().setDisplayShowCustomEnabled(true);
        getActivity().getActionBar().setCustomView(actionBarButtons);
    }

    private void insertMetadata(String path) {
        MBTilesDatabase mbTilesDatabase = new MBTilesDatabase(getActivity(), path);
        try {
            mbTilesDatabase.open();
        } catch (SQLException e) {
            // cannot open database on file system
            Toast.makeText(getActivity(), getString(R.string.crouton_database_error), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return;
        }
        // fill the metadata table
        TileJson tileJson = new Gson().fromJson(mMapItem.getJsonData(), TileJson.class);
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

    private void unselectFile() {
        // return to normal action bar state
        getActivity().getActionBar().setHomeButtonEnabled(true);
        getActivity().getActionBar().setDisplayShowHomeEnabled(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        getActivity().getActionBar().setDisplayShowTitleEnabled(true);

        getActivity().getActionBar().setDisplayShowCustomEnabled(false);
        getActivity().getActionBar().setCustomView(null);
    }

    public void setmMapItem(MapItem mMapItem) {
        this.mMapItem = mMapItem;
    }
}
