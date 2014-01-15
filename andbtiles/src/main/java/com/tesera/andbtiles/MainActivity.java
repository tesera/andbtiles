package com.tesera.andbtiles;

import android.app.Activity;
import android.os.Bundle;

import com.tesera.andbtiles.callbacks.DatabaseChangeCallback;
import com.tesera.andbtiles.fragments.MapsFragment;

public class MainActivity extends Activity implements DatabaseChangeCallback {

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

    public void setDatabaseChanged(boolean isDatabaseChanged) {
        this.isDatabaseChanged = isDatabaseChanged;
    }
}
