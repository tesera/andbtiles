package com.tesera.andbtiles.sample.utils;


import android.content.Context;
import android.os.Environment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static String getStringFromPrefs(Context context, String key) {
        return context.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE).getString(key, null);
    }

    public static void setStringToPrefs(Context context, String key, String value) {
        context.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE).edit().putString(key, value).commit();
    }

    public static Set<String> getStringSetFromPrefs(Context context, String key) {
        return context.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE).getStringSet(key, null);
    }

    public static void setStringSetToPrefs(Context context, String key, String value) {
        Set<String> cachedSet = getStringSetFromPrefs(context, key);
        if (cachedSet == null)
            cachedSet = new HashSet<>();
        cachedSet.add(value);
        context.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE).edit().putStringSet(key, cachedSet).commit();
    }
}
