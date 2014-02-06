package com.tesera.andbtiles.pojos;


public class MapItem {

    private String id;
    private String path;
    private String name;
    private int cacheMode;
    private long size;
    private String jsonData;

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

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }
}
