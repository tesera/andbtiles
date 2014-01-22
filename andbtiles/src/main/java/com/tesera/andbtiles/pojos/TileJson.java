
package com.tesera.andbtiles.pojos;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TileJson {

    private String attribution;
    private boolean autoscale;
    private List<Number> bounds;
    private List<Number> center;
    private List<String> data;
    private String geocoder;
    private String id;
    private Number maxzoom;
    private Number minzoom;
    private String name;
    @SerializedName("private")
    private boolean _private;
    private String scheme;
    private String tilejson;
    private List tiles;
    private String webpage;
    private String download;
    private Number filesize;
    private String version;
    private String description;

    public String getAttribution() {
        return attribution;
    }

    public void setAttribution(String attribution) {
        this.attribution = attribution;
    }

    public boolean isAutoscale() {
        return autoscale;
    }

    public void setAutoscale(boolean autoscale) {
        this.autoscale = autoscale;
    }

    public List<Number> getBounds() {
        return bounds;
    }

    public void setBounds(List<Number> bounds) {
        this.bounds = bounds;
    }

    public List<Number> getCenter() {
        return center;
    }

    public void setCenter(List<Number> center) {
        this.center = center;
    }

    public List<String> getData() {
        return data;
    }

    public void setData(List<String> data) {
        this.data = data;
    }

    public String getGeocoder() {
        return geocoder;
    }

    public void setGeocoder(String geocoder) {
        this.geocoder = geocoder;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Number getMaxzoom() {
        return maxzoom;
    }

    public void setMaxzoom(Number maxzoom) {
        this.maxzoom = maxzoom;
    }

    public Number getMinzoom() {
        return minzoom;
    }

    public void setMinzoom(Number minzoom) {
        this.minzoom = minzoom;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean is_private() {
        return _private;
    }

    public void set_private(boolean _private) {
        this._private = _private;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getTilejson() {
        return tilejson;
    }

    public void setTilejson(String tilejson) {
        this.tilejson = tilejson;
    }

    public List getTiles() {
        return tiles;
    }

    public void setTiles(List tiles) {
        this.tiles = tiles;
    }

    public String getWebpage() {
        return webpage;
    }

    public void setWebpage(String webpage) {
        this.webpage = webpage;
    }

    public String getDownload() {
        return download;
    }

    public void setDownload(String download) {
        this.download = download;
    }

    public Number getFilesize() {
        return filesize;
    }

    public void setFilesize(Number filesize) {
        this.filesize = filesize;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
