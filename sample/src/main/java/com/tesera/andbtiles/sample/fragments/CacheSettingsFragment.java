package com.tesera.andbtiles.sample.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.tesera.andbtiles.Andbtiles;
import com.tesera.andbtiles.callbacks.AndbtilesCallback;
import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.sample.R;
import com.tesera.andbtiles.sample.callbacks.ActivityCallback;
import com.tesera.andbtiles.sample.utils.Consts;


class CacheSettingsFragment extends Fragment {

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
        getActivity().getActionBar().setTitle(R.string.title_cache_settings);
        selectFile();
    }

    @Override
    public void onPause() {
        super.onPause();
        unselectFile();
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
                // set the cache method
                mMapItem.setCacheMode(mCacheGroup.indexOfChild(mCacheGroup.findViewById(mCacheGroup.getCheckedRadioButtonId())));
                switch (mMapItem.getCacheMode()) {
                    case Consts.CACHE_FULL:
                        Bundle args = new Bundle();
                        args.putString(Consts.EXTRA_JSON, getArguments().getString(Consts.EXTRA_JSON));

                        HarvestSettingsFragment fragment = new HarvestSettingsFragment();
                        fragment.setmMapItem(mMapItem);
                        fragment.setArguments(args);
                        getFragmentManager().beginTransaction()
                                .replace(R.id.container, fragment)
                                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                .addToBackStack(null)
                                .commit();
                        return;
                    case Consts.CACHE_NO:
                    case Consts.CACHE_ON_DEMAND:
                        andbtiles.addRemoteJsonTileProvider(getArguments().getString(Consts.EXTRA_JSON), mMapItem.getName(),
                                mMapItem.getCacheMode(), new AndbtilesCallback() {
                            @Override
                            public void onSuccess() {
                                if (!isAdded())
                                    return;
                                mCallback.onDatabaseChanged();
                                getFragmentManager().popBackStack();
                                getFragmentManager().popBackStack();
                            }

                            @Override
                            public void onError(Exception e) {
                                if (!isAdded())
                                    return;
                                Toast.makeText(getActivity(), getString(R.string.crouton_database_error), Toast.LENGTH_SHORT).show();
                            }
                        });
                        break;
                    case Consts.CACHE_DATA:
                        andbtiles.addRemoteMbilesProvider(mMapItem.getPath(), new AndbtilesCallback() {
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
                        getFragmentManager().popBackStack();
                        getFragmentManager().popBackStack();
                        break;
                }
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
