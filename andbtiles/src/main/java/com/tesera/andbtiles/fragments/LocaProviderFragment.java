package com.tesera.andbtiles.fragments;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
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
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tesera.andbtiles.R;
import com.tesera.andbtiles.adapters.MBTilesAdapter;
import com.tesera.andbtiles.utils.Consts;
import com.tesera.andbtiles.utils.Utils;

import java.io.File;
import java.lang.reflect.Type;
import java.util.List;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class LocaProviderFragment extends Fragment {

    private ListView mMBTilesList;
    private TextView mEmptyView;
    private Menu mMenu;

    private String mMBTilesFilePath;

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
                mMBTilesFilePath = ((File) parent.getAdapter().getItem(position)).getAbsolutePath();
                selectFile();
            }
        });
        mEmptyView = (TextView) contentView.findViewById(android.R.id.empty);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);
        mMenu = menu;
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
                if (mMBTilesList != null)
                    ((MBTilesAdapter) mMBTilesList.getAdapter()).getFilter().filter(newText);
                return false;
            }
        });
        menu.findItem(R.id.action_search).setVisible(false);

        // list all .mbtiles file on SD
        // we call this here, because the task toggles the menu visibility
        ListFilesByExtensionTask task = new ListFilesByExtensionTask();
        task.execute(Consts.EXTENSION_MBTILES);
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

        mMBTilesFilePath = data.getDataString();
        selectFile();
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void selectFile() {
        if (mMBTilesFilePath == null)
            return;
        if (!mMBTilesFilePath.endsWith(Consts.EXTENSION_MBTILES)) {
            Crouton.makeText(getActivity(), getString(R.string.crouton_invalid_file), Style.ALERT).show();
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.dialog_title_local_provider))
                .setMessage(String.format(getString(R.string.dialog_message_local_provider), mMBTilesFilePath))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .setPositiveButton(getString(R.string.btn_confirm), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO select cache method
                    }
                })
                .show();
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
            // enable results filtering
            mMenu.findItem(R.id.action_search).setVisible(true);
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
            // enable results filtering
            mMenu.findItem(R.id.action_search).setVisible(true);
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
