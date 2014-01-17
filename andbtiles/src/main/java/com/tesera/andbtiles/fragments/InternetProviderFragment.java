package com.tesera.andbtiles.fragments;


import android.app.Fragment;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tesera.andbtiles.R;
import com.tesera.andbtiles.adapters.MBTilesAdapter;
import com.tesera.andbtiles.adapters.MapsAdapter;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.pojos.TileJson;
import com.tesera.andbtiles.utils.Consts;

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

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public class InternetProviderFragment extends Fragment {

    private Button mFetch;
    private EditText mUrl;
    private ListView mMBTilesList;
    private Menu mMenu;

    private ArrayList<TileJson> mTileJsonList;

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
        mMBTilesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(mTileJsonList == null || mTileJsonList.size() == 0) {
                    // TODO this is an .mbtiles file so prompt user for download confirmation
                }
                else {
                    // TODO this is a map from TileJSON so advance to cache options screen
                }
            }
        });
        mFetch = (Button) contentView.findViewById(R.id.btn_fetch);
        mFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // validate the url
                if (!validate()) {
                    mUrl.setError(getString(R.string.hint_url_error));
                }

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
                if (mMBTilesList != null
                        || mMBTilesList.getAdapter() != null
                        || ((MBTilesAdapter) mMBTilesList.getAdapter()).getFilter() != null)
                    ((MapsAdapter) mMBTilesList.getAdapter()).getFilter().filter(newText);
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

    private boolean validate() {
        String url = mUrl.getText().toString();
        return (url.endsWith(Consts.EXTENSION_MBTILES) || url.endsWith(Consts.EXTENSION_JSON)) && url.matches(Patterns.WEB_URL.pattern());
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
                String[] urlSegments = params[0].split("/");
                MapItem mapItem = new MapItem();
                mapItem.setName(urlSegments[urlSegments.length - 1].replace("." + Consts.EXTENSION_MBTILES, ""));
                mapItem.setPath(params[0]);
                mapItem.setSize(getFileSize(params[0]));
                List<MapItem> adapterList = new ArrayList<>();
                adapterList.add(mapItem);
                return adapterList;
            }

            // display the list of maps from the TileJSON otherwise

            try {
                HttpClient client = new DefaultHttpClient();
                // execute GET method
                HttpGet request = new HttpGet(params[0]);
                HttpResponse response = client.execute(request);

                // get the response
                HttpEntity responseEntity = response.getEntity();
                String jsonResponse = EntityUtils.toString(responseEntity);

                // parse the response
                Gson gson = new Gson();
                if (jsonResponse.startsWith("[")) {
                    // this is a JSON array
                    Type listOfDays = new TypeToken<List<TileJson>>() {
                    }.getType();
                    mTileJsonList = gson.fromJson(jsonResponse, listOfDays);
                } else {
                    // this is a JSON object
                    TileJson tileJson = gson.fromJson(jsonResponse, TileJson.class);
                    mTileJsonList = new ArrayList<>();
                    mTileJsonList.add(tileJson);
                }

                // construct adapter items from the parsed JSON
                List<MapItem> adapterList = new ArrayList<>();
                for (TileJson tileJson : mTileJsonList) {
                    MapItem mapItem = new MapItem();
                    mapItem.setName(tileJson.getName());
                    mapItem.setPath(tileJson.getDownload());
                    mapItem.setSize(getFileSize(tileJson.getDownload()));
                    adapterList.add(mapItem);
                }
                return adapterList;
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

        @Override
        protected void onPostExecute(List<MapItem> result) {
            if (!isAdded())
                return;

            // enable the fetch button again
            mFetch.setEnabled(true);
            mFetch.setText(getString(R.string.btn_fetch));
            if (result == null || result.size() == 0) {
                Crouton.makeText(getActivity(), getString(R.string.crouton_fetch_error), Style.ALERT).show();
                return;
            }
            // display the results
            MapsAdapter mapsAdapter = new MapsAdapter(getActivity(), result);
            mMBTilesList.setAdapter(mapsAdapter);

            super.onPostExecute(result);
        }
    }
}
