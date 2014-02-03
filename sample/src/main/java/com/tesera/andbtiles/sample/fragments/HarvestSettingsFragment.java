package com.tesera.andbtiles.sample.fragments;


import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.edmodo.rangebar.RangeBar;
import com.google.gson.Gson;
import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.callbacks.AndbtilesCallback;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.sample.R;
import com.tesera.andbtiles.sample.utils.Consts;

class HarvestSettingsFragment extends Fragment {

    private TextView mMinZoom;
    private TextView mMaxZoom;
    private TextView mTiles;

    private MapItem mMapItem;
    private TileJson mTileJson;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.fragment_harvest_settings, null);

        String tileJsonString = mMapItem.getJsonData();
        if (tileJsonString == null)
            getFragmentManager().popBackStack();
        mTileJson = new Gson().fromJson(tileJsonString, TileJson.class);

        // show info
        TextView infoText = (TextView) contentView.findViewById(R.id.txt_name);
        infoText.setText(mMapItem.getName());

        infoText = (TextView) contentView.findViewById(R.id.txt_info);
        infoText.setText(mTileJson.getBounds().toString());

        mMinZoom = (TextView) contentView.findViewById(R.id.txt_min_zoom);
        mMinZoom.setText(mTileJson.getMinzoom().toString());

        mMaxZoom = (TextView) contentView.findViewById(R.id.txt_max_zoom);
        mMaxZoom.setText(mTileJson.getMaxzoom().toString());

        mTiles = (TextView) contentView.findViewById(R.id.txt_tiles);

        RangeBar mRangeBar = (RangeBar) contentView.findViewById(R.id.rangebar);
        mRangeBar.setTickCount(mTileJson.getMaxzoom().intValue() - mTileJson.getMinzoom().intValue());
        mRangeBar.setOnRangeBarChangeListener(new RangeBar.OnRangeBarChangeListener() {
            @Override
            public void onIndexChangeListener(RangeBar rangeBar, int leftThumbIndex, int rightThumbIndex) {
                // update the text
                mMinZoom.setText(mTileJson.getMinzoom().intValue() + leftThumbIndex + "");
                mMaxZoom.setText(mTileJson.getMinzoom().intValue() + rightThumbIndex + 1 + "");

                TileCounter countTask = new TileCounter();
                countTask.execute(mTileJson.getMinzoom().intValue() + leftThumbIndex, mTileJson.getMinzoom().intValue() + rightThumbIndex + 1);
            }
        });

        TileCounter countTask = new TileCounter();
        countTask.execute(mTileJson.getMinzoom().intValue(), mTileJson.getMaxzoom().intValue());
        selectFile();
        return contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getActionBar().setTitle(R.string.title_harvest_settings);
    }

    void selectFile() {
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
                Andbtiles andbtiles = new Andbtiles(getActivity());
                andbtiles.addRemoteJsonTileProvider(getArguments().getString(Consts.EXTRA_JSON), mMapItem.getName(), mMapItem.getCacheMode(), Integer.valueOf(mMinZoom.getText().toString()),
                        Integer.valueOf(mMaxZoom.getText().toString()), new AndbtilesCallback() {
                    @Override
                    public void onSuccess() {
                        // long running operation
                        // no need to stay on this fragment
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isAdded())
                            return;
                        Toast.makeText(getActivity(), getString(R.string.crouton_database_error), Toast.LENGTH_SHORT).show();
                    }
                });

                Toast.makeText(getActivity(), getString(R.string.toast_harvesting), Toast.LENGTH_SHORT).show();
                getFragmentManager().popBackStack();
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

    private int[] getTileNumber(double lat, double lon, int zoom) {
        int xTile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int yTile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (xTile < 0)
            xTile = 0;
        if (xTile >= (1 << zoom))
            xTile = ((1 << zoom) - 1);
        if (yTile < 0)
            yTile = 0;
        if (yTile >= (1 << zoom))
            yTile = ((1 << zoom) - 1);

        int[] tileCoordinates = new int[3];
        tileCoordinates[0] = zoom;
        tileCoordinates[1] = xTile;
        tileCoordinates[2] = yTile;

        return tileCoordinates;
    }

    public void setmMapItem(MapItem mMapItem) {
        this.mMapItem = mMapItem;
    }

    private class TileCounter extends AsyncTask<Integer, Void, Long> {

        @Override
        protected Long doInBackground(Integer... params) {
            long numberOfTiles = 0;
            for (int z = params[0]; z <= params[1]; z++) {
                // find the OSM coordinates of the bounding box
                int[] topLeftCoordinates = getTileNumber(mTileJson.getBounds().get(3).doubleValue(), mTileJson.getBounds().get(0).doubleValue(), z);
                int[] bottomRightCoordinates = getTileNumber(mTileJson.getBounds().get(1).doubleValue(), mTileJson.getBounds().get(2).doubleValue(), z);

                int startX = topLeftCoordinates[1];
                int endX = bottomRightCoordinates[1];
                int startY = topLeftCoordinates[2];
                int endY = bottomRightCoordinates[2];
                // harvest individual files for specific zoom level
                for (int x = startX; x <= endX; x++)
                    for (int y = startY; y <= endY; y++)
                        numberOfTiles++;
            }
            return numberOfTiles;
        }

        @Override
        protected void onPostExecute(Long numberOfTiles) {
            if (isAdded() && numberOfTiles != 0)
                mTiles.setText(String.format(getString(R.string.hint_harvest_tiles_number), numberOfTiles));
            super.onPostExecute(numberOfTiles);
        }
    }
}
