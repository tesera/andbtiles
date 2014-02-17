package com.tesera.andbtiles.pojos;


import com.google.gson.Gson;

public class MapItem {

    private String id;
    private String path;
    private String name;
    private int cacheMode;
    private long size;
    private String tileJsonString;
    private String geoJsonString;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCacheMode() {
        return cacheMode;
    }

    public void setCacheMode(int cacheMode) {
        this.cacheMode = cacheMode;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getTileJsonString() {
        return tileJsonString;
    }

    public void setTileJsonString(String tileJsonString) {
        this.tileJsonString = tileJsonString;
    }

    public String getGeoJsonString() {
        return geoJsonString;
    }

    public void setGeoJsonString(String geoJsonString) {
        this.geoJsonString = geoJsonString;
    }

    public TileJson getTileJson() {
        if (tileJsonString == null || tileJsonString.length() == 0)
            return null;

        try {
            return new Gson().fromJson(tileJsonString, TileJson.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public GeoJson getGeoJson() {
        if (geoJsonString == null || geoJsonString.length() == 0)
            return null;

        try {
            return new Gson().fromJson(geoJsonString, GeoJson.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
