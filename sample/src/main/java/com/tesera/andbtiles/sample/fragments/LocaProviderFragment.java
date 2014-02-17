package com.tesera.andbtiles.sample.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.exceptions.AndbtilesException;
import com.tesera.andbtiles.sample.R;
import com.tesera.andbtiles.sample.adapters.MBTilesAdapter;
import com.tesera.andbtiles.sample.callbacks.ActivityCallback;
import com.tesera.andbtiles.sample.utils.Consts;
import com.tesera.andbtiles.sample.utils.Utils;

import java.io.File;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class LocaProviderFragment extends Fragment {

    private ListView mMBTilesList;
    private TextView mEmptyView;
    private TextView mName;
    private Menu mMenu;

    private File mMBTilesFile;
    private ActivityCallback mCallback;

    @Override
    public void onAttach(Activity activity) {
        mCallback = (ActivityCallback) activity;
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // this fragment has it's own menu
        setHasOptionsMenu(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.fragment_local, null);
        // find the list view and the empty view
        mMBTilesList = (ListView) contentView.findViewById(android.R.id.list);
        mMBTilesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mMBTilesFile = (File) parent.getAdapter().getItem(position);
                mName.setText(mMBTilesFile.getName());

                mMBTilesList.setItemChecked(position, true);
                selectFile();
            }
        });
        mEmptyView = (TextView) contentView.findViewById(android.R.id.empty);
        mName = (TextView) contentView.findViewById(R.id.txt_name);
        Button mBrowse = (Button) contentView.findViewById(R.id.btn_browse);
        mBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // launch a pick file intent
                Intent pickFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickFileIntent.setType("file/*");
                startActivityForResult(pickFileIntent, Consts.RESULT_PICK_FILE);
            }
        });
        return contentView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // search for .mbtiles files on the external storage
        ListFilesByExtensionTask task = new ListFilesByExtensionTask();
        task.execute(Consts.EXTENSION_MBTILES);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        getActivity().getActionBar().setTitle(getString(R.string.title_local_provider));
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);
        // save the menu as a class variable since we toggle the menu visibility often
        mMenu = menu;
        // setup the search view
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setQueryHint(getString(R.string.action_filter));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mMenu.findItem(R.id.action_search).collapseActionView();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // filter results only if we have an adapter
                if (mMBTilesList != null
                        || mMBTilesList.getAdapter() != null
                        || ((MBTilesAdapter) mMBTilesList.getAdapter()).getFilter() != null)
                    ((MBTilesAdapter) mMBTilesList.getAdapter()).getFilter().filter(newText);
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != Consts.RESULT_PICK_FILE || data == null || data.getDataString() == null)
            return;
        // get the file from data string URI and extract the file name from it
        try {
            mMBTilesFile = new File(new URI(data.getDataString()).getPath());
        } catch (URISyntaxException e) {
            Toast.makeText(getActivity(), getString(R.string.crouton_invalid_file), Toast.LENGTH_SHORT).show();
            return;
        }
        mName.setText(mMBTilesFile.getName());

        selectFile();
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void selectFile() {
        if (mMBTilesFile == null)
            return;
        if (!mMBTilesFile.getName().endsWith(Consts.EXTENSION_MBTILES)) {
            Toast.makeText(getActivity(), getString(R.string.crouton_invalid_file), Toast.LENGTH_SHORT).show();
            return;
        }

        View actionBarButtons = getActivity().getLayoutInflater().inflate(R.layout.action_bar_custom_confirm, new LinearLayout(getActivity()), false);

        View cancelActionView = actionBarButtons.findViewById(R.id.action_cancel);
        cancelActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unselectFile();
            }
        });

        View doneActionView = actionBarButtons.findViewById(R.id.action_done);
        doneActionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Andbtiles andbtiles = new Andbtiles(getActivity());
                try {
                    andbtiles.addLocalMbTilesProvider(mMBTilesFile.getAbsolutePath(), mMBTilesFile.getAbsolutePath().replace(Consts.EXTENSION_MBTILES, Consts.EXTENSION_GEO_JSON));
                } catch (AndbtilesException e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    unselectFile();
                    return;
                }
                // return to previous screen, notify dataSetChanged and inform the user
                unselectFile();
                Toast.makeText(getActivity(), getString(R.string.crouton_map_added), Toast.LENGTH_SHORT).show();
                mCallback.onDatabaseChanged();
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
        // hide the filter icon
        mMenu.setGroupVisible(R.id.group_search, false);
    }

    private void unselectFile() {
        // return to normal action bar state
        getActivity().getActionBar().setHomeButtonEnabled(true);
        getActivity().getActionBar().setDisplayShowHomeEnabled(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        getActivity().getActionBar().setDisplayShowTitleEnabled(true);

        getActivity().getActionBar().setDisplayShowCustomEnabled(false);
        getActivity().getActionBar().setCustomView(null);

        // un-select any selected files
        mMenu.setGroupVisible(R.id.group_search, true);
        mMBTilesList.setItemChecked(mMBTilesList.getCheckedItemPosition(), false);
        mName.setText(R.string.hint_no_file_selected);
    }

    private class ListFilesByExtensionTask extends AsyncTask<String, Void, List<File>> {

        @Override
        protected void onPreExecute() {
            mMBTilesList.setEmptyView(mEmptyView);
            // try the cache for immediate results display
            String jsonFileCache = Utils.getStringFromPrefs(getActivity(), Consts.PREF_KEY_LOCAL_FILES_CACHE);
            if (jsonFileCache == null)
                return;
            // de-serialize the results
            Gson gson = new Gson();
            Type listOfDays = new TypeToken<List<File>>() {
            }.getType();
            List<File> files = gson.fromJson(jsonFileCache, listOfDays);
            // set the adapter
            MBTilesAdapter adapter = new MBTilesAdapter(getActivity(), files);
            mMBTilesList.setAdapter(adapter);
            // we already have an adapter
            // empty list in this case would mean no filter results
            mEmptyView.setText(getString(R.string.hint_no_results));
            super.onPreExecute();
        }

        @Override
        protected List<File> doInBackground(String... params) {
            return Utils.listFilesByExtension(params[0]);
        }

        @Override
        protected void onPostExecute(List<File> files) {
            if (!isAdded())
                return;
            if (files == null || files.isEmpty()) {
                mEmptyView.setVisibility(View.GONE);
                return;
            }
            MBTilesAdapter adapter = new MBTilesAdapter(getActivity(), files);
            mMBTilesList.setAdapter(adapter);
            // we already have an adapter
            // empty list in this case would mean no filter results
            mEmptyView.setText(getString(R.string.hint_no_results));

            // cache the results since files tend not to move too often
            Gson gson = new Gson();
            Type listOfDays = new TypeToken<List<File>>() {
            }.getType();
            String jsonFilesCache = gson.toJson(files, listOfDays);
            Utils.setStringToPrefs(getActivity(), Consts.PREF_KEY_LOCAL_FILES_CACHE, jsonFilesCache);
            super.onPostExecute(files);
        }
    }
}
