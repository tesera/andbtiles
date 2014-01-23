package com.tesera.andbtiles.callbacks;

public interface DatabaseChangeCallback {

    public void onDatabaseChanged();
    public void downloadFile(String path);
}
