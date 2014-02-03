package com.tesera.andbtiles.sample;

import android.app.Activity;
import android.os.Bundle;

import com.tesera.andbtiles.sample.callbacks.ActivityCallback;
import com.tesera.andbtiles.sample.fragments.MapsFragment;

public class MainActivity extends Activity implements ActivityCallback {

    private boolean isDatabaseChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new MapsFragment())
                    .commit();
        }
    }

    @Override
    public void onDatabaseChanged() {
        isDatabaseChanged = true;
    }

    public boolean isDatabaseChanged() {
        return isDatabaseChanged;
    }

    public void setDatabaseChanged() {
        this.isDatabaseChanged = false;
    }
}
