package com.tesera.andbtiles.utils;


import android.content.Context;
import android.os.Environment;

import com.tesera.andbtiles.databases.MapsDatabase;
import com.tesera.andbtiles.pojos.MapItem;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Utils {

    public static List<File> listFilesByExtension(String extension) {
        if (extension == null)
            return null;

        String[] extensions = new String[1];
        extensions[0] = extension;
        Collection<File> files;
        try {
            // find all files on the external SD card with certain extension
            files = FileUtils.listFiles(Environment.getExternalStorageDirectory(), extensions, true);
            return new ArrayList<>(files);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean saveMapToDatabase(Context context, MapItem mapItem) {
        MapsDatabase mapsDatabase = new MapsDatabase(context);
        try {
            mapsDatabase.open();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        mapsDatabase.insertItems(mapItem);
        mapsDatabase.close();
        return true;
    }

    public static boolean isMapInDatabase(Context context, MapItem mapItem) {
        MapsDatabase mapsDatabase = new MapsDatabase(context);
        try {
            mapsDatabase.open();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        boolean result = mapsDatabase.isMapAdded(mapItem);
        mapsDatabase.close();
        return result;
    }

    public static String getStringFromPrefs(Context context, String key) {
        return context.getSharedPreferences(Consts.PREF_NAME, Context.MODE_PRIVATE).getString(key, null);
    }

    public static void setStringToPrefs(Context context, String key, String value) {
        context.getSharedPreferences(Consts.PREF_NAME, Context.MODE_PRIVATE).edit().putString(key, value).commit();
    }
}
