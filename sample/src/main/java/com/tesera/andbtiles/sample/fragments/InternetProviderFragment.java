package com.tesera.andbtiles.sample.fragments;


import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.callbacks.AndbtilesCallback;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.sample.R;
import com.tesera.andbtiles.sample.adapters.MapsAdapter;
import com.tesera.andbtiles.sample.callbacks.ActivityCallback;
import com.tesera.andbtiles.sample.utils.Const;
import com.tesera.andbtiles.sample.utils.Utils;
import com.tesera.andbtiles.utils.Consts;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class InternetProviderFragment extends Fragment {

    private Button mFetch;
    private EditText mUrl;
    private ListView mMBTilesList;
    private Menu mMenu;

    private String mDownloadPath;

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
        View contentView = inflater.inflate(R.layout.fragment_internet, null);
        mMBTilesList = (ListView) contentView.findViewById(android.R.id.list);
        // check for endpoint data cache
        Set<String> cachedUrls = Utils.getStringSetFromPrefs(getActivity(), Const.PREF_KEY_CACHED_URLS);
        if (cachedUrls != null && !cachedUrls.isEmpty()) {
            ArrayAdapter<String> urlsAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, new ArrayList<>(cachedUrls));
            mMBTilesList.setAdapter(urlsAdapter);
            mMBTilesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    FetchURL task = new FetchURL();
                    task.execute((String) parent.getAdapter().getItem(position));
                    mUrl.setText((String) parent.getAdapter().getItem(position));
                }
            });
        }

        // setup the fetch button
        mFetch = (Button) contentView.findViewById(R.id.btn_fetch);
        mFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // validate the url
                if (!isValid())
                    mUrl.setError(getString(R.string.hint_url_error));

                // hide the keyboard and execute the fetch
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mUrl.getWindowToken(), 0);

                FetchURL task = new FetchURL();
                task.execute(mUrl.getText().toString());
            }
        });
        mUrl = (EditText) contentView.findViewById(R.id.edit_url);

        return contentView;
    }

    @Override
    public void onResume() {
        getActivity().getActionBar().setTitle(getString(R.string.title_internet_provider));
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
                try {
                    if (mMBTilesList != null
                            && mMBTilesList.getAdapter() != null
                            && ((MapsAdapter) mMBTilesList.getAdapter()).getFilter() != null)
                        ((MapsAdapter) mMBTilesList.getAdapter()).getFilter().filter(newText);
                } catch (Exception e) {
                    // wrong cast, ignore it
                }
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

    private boolean isValid() {
        mDownloadPath = mUrl.getText().toString();
        return (mDownloadPath.endsWith(Consts.EXTENSION_MBTILES) || mDownloadPath.endsWith(Consts.EXTENSION_JSON))
                && mDownloadPath.matches(Patterns.WEB_URL.pattern());
    }

    private void selectFile() {

        View actionBarButtons;
        actionBarButtons = getActivity().getLayoutInflater().inflate(R.layout.action_bar_custom_download, new LinearLayout(getActivity()), false);


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
                unselectFile();
                // download the remote mbtiles file
                Andbtiles andbtiles = new Andbtiles(getActivity());
                andbtiles.addRemoteMbilesProvider(mDownloadPath, new AndbtilesCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded())
                            return;
                        // return to previous screen, notify dataSetChanged and inform the user
                        Toast.makeText(getActivity(), getString(R.string.crouton_map_added), Toast.LENGTH_SHORT).show();
                        mCallback.onDatabaseChanged();
                        getFragmentManager().popBackStack();
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isAdded())
                            return;
                        Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
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
    }

    private class FetchURL extends AsyncTask<String, Void, List<MapItem>> {

        @Override
        protected void onPreExecute() {
            mFetch.setEnabled(false);
            mFetch.setText(getString(R.string.btn_fetching));
            super.onPreExecute();
        }

        @Override
        protected List<MapItem> doInBackground(String... params) {
            if (params[0].endsWith(Consts.EXTENSION_MBTILES)) {
                // set the name and the path of the file
                MapItem mapItem = new MapItem();
                mapItem.setId(FilenameUtils.getName(params[0]));
                mapItem.setName(FilenameUtils.getName(params[0]));
                mapItem.setPath(params[0]);
                mapItem.setSize(getFileSize(params[0]));
                List<MapItem> adapterList = new ArrayList<>();
                adapterList.add(mapItem);
                return mapItem.getSize() == 0 ? null : adapterList;
            }
            // display the list of maps from the TileJSON otherwise
            try {
                HttpClient client = new DefaultHttpClient();
                // execute GET method
                HttpGet request = new HttpGet(params[0]);
                HttpResponse response = client.execute(request);

                // get the response
                HttpEntity responseEntity = response.getEntity();

                // cache the entered url
                String jsonResponse = EntityUtils.toString(responseEntity);
                Utils.setStringSetToPrefs(getActivity(), Const.PREF_KEY_CACHED_URLS, params[0]);

                return parseJsonResponse(jsonResponse);
            } catch (Exception e) {
                // an internet connection error
                e.printStackTrace();
                return null;
            }
        }

        private long getFileSize(String path) {
            if (path == null)
                return 0;
            try {
                // try go get the file size
                URL url = new URL(path);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();
                String locationUrl = urlConnection.getHeaderField("location");
                // the trick is that MapBox hosts its files on Amazon servers
                // to get the file size we need to find the location first
                url = new URL(locationUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();
                // get as string since the file can exceed the Integer 32bit limit
                String contentLengthStr = urlConnection.getHeaderField("content-length");
                return Long.valueOf(contentLengthStr);
            } catch (Exception e) {
                // file size unknown
                e.printStackTrace();
                return 0;
            }
        }

        private List<MapItem> parseJsonResponse(String jsonResponse) {
            // parse the response
            List<TileJson> tileJsons = new ArrayList<>();
            Gson gson = new Gson();
            if (jsonResponse.startsWith("[")) {
                // this is a JSON array
                Type listOfDays = new TypeToken<List<TileJson>>() {
                }.getType();
                tileJsons = gson.fromJson(jsonResponse, listOfDays);
            } else {
                // this is a JSON object
                TileJson tileJson = gson.fromJson(jsonResponse, TileJson.class);
                tileJsons.add(tileJson);
            }

            // construct adapter items from the parsed JSON
            List<MapItem> adapterList = new ArrayList<>();
            for (TileJson tileJson : tileJsons) {
                MapItem mapItem = new MapItem();
                mapItem.setId(tileJson.getId());
                mapItem.setName(tileJson.getName());
                mapItem.setPath(tileJson.getDownload());
                mapItem.setTileJsonString(gson.toJson(tileJson, TileJson.class));
                if (tileJson.getFilesize() != null)
                    mapItem.setSize(tileJson.getFilesize().longValue());
                adapterList.add(mapItem);
            }
            return adapterList;
        }

        @Override
        protected void onPostExecute(List<MapItem> result) {
            if (!isAdded())
                return;
            // enable the fetch button again
            mFetch.setEnabled(true);
            mFetch.setText(getString(R.string.btn_fetch));
            if (result == null || result.size() == 0) {
                Toast.makeText(getActivity(), getString(R.string.crouton_fetch_error), Toast.LENGTH_SHORT).show();
                return;
            }
            // display the results and setup the click listener
            MapsAdapter mapsAdapter = new MapsAdapter(getActivity(), result);
            mMBTilesList.setAdapter(mapsAdapter);
            mMBTilesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    MapItem mapItem = (MapItem) parent.getAdapter().getItem(position);
                    // this is a url to a .mbtiles file
                    if (mapItem.getTileJsonString() == null)
                        selectFile();
                        // this is a map from TileJSON so advance to cache options screen
                    else {
                        CacheSettingsFragment fragment = new CacheSettingsFragment();
                        fragment.setmMapItem(mapItem);
                        getFragmentManager().beginTransaction()
                                .replace(R.id.container, fragment)
                                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                .addToBackStack(null)
                                .commit();
                    }
                }
            });

            super.onPostExecute(result);
        }
    }
}
