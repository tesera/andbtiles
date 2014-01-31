package com.tesera.andbtiles.sample.fragments;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupMenu;

import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.sample.MainActivity;
import com.tesera.andbtiles.sample.MapActivity;
import com.tesera.andbtiles.sample.R;
import com.tesera.andbtiles.sample.adapters.MapsAdapter;
import com.tesera.andbtiles.sample.loaders.MapsDatabaseLoader;
import com.tesera.andbtiles.sample.utils.Consts;

import java.util.List;

public class MapsFragment extends Fragment implements LoaderManager.LoaderCallbacks<List<MapItem>> {

    private ListView mMapsList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // this fragment has it's own menu
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.fragment_maps, null);
        // find the list view and the empty view
        mMapsList = (ListView) contentView.findViewById(android.R.id.list);
        mMapsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MapItem mapItem = (MapItem) parent.getAdapter().getItem(position);

                Intent mapIntent = new Intent(getActivity(), MapActivity.class);
                mapIntent.putExtra(Consts.EXTRA_PATH, mapItem.getPath());
                mapIntent.putExtra(Consts.EXTRA_NAME, mapItem.getName());
                startActivity(mapIntent);
            }
        });
        mMapsList.setEmptyView(contentView.findViewById(android.R.id.empty));
        return contentView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // init the database loader
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        // set action bar title
        getActivity().getActionBar().setTitle(getString(R.string.app_name));
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);

        // reload data if database has changed
        MainActivity activity = (MainActivity) getActivity();
        if (activity.isDatabaseChanged()) {
            getLoaderManager().restartLoader(0, null, this);
            activity.setDatabaseChanged(false);
        }
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_add:
                // we must find the anchor view this way
                // http://stackoverflow.com/questions/14729592/show-popup-menu-on-actionbar-item-click
                View menuItem = getActivity().findViewById(R.id.action_add);
                PopupMenu popupMenu = new PopupMenu(getActivity(), menuItem);
                popupMenu.inflate(R.menu.add);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        Fragment fragment = null;
                        // choose provider
                        switch (item.getItemId()) {
                            case R.id.action_local:
                                fragment = new LocaProviderFragment();
                                break;
                            case R.id.action_internet:
                                fragment = new InternetProviderFragment();
                                break;
                        }
                        if (fragment != null)
                            getActivity().getFragmentManager().beginTransaction()
                                    .replace(R.id.container, fragment)
                                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                    .addToBackStack(null)
                                    .commit();
                        return true;
                    }
                });
                // show the dropdown popup menu
                popupMenu.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<List<MapItem>> onCreateLoader(int id, Bundle args) {
        return new MapsDatabaseLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<MapItem>> loader, List<MapItem> data) {
        if (mMapsList == null)
            return;
        // set empty view if there are no saved maps
        if (data == null || data.isEmpty())
            return;
        mMapsList.setAdapter(new MapsAdapter(getActivity(), data));
    }

    @Override
    public void onLoaderReset(Loader<List<MapItem>> loader) {
        if (mMapsList == null)
            return;
        mMapsList.setAdapter(null);
    }
}
