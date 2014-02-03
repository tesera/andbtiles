Andbtiles
=========

Andbtiles is an Android utility library that manages downloading, caching or harvesting of MBTiles and exposes them to external applications via a Content Provider.  
_content://com.tesera.andbtiles.provider/{map_name}/tiles

## Features

* Add a local map provider
* Add a remote map provider
* Select a cache method 
* Expose MBTiles data via ContentProvider

## Datasources
* Local MBTiles file
* Remote MBTiles file 
* Map from TileJSON endpoint

## Cache Modes

* No cache: acts like a simple proxy, tiles are downloaded and exposed via the content provider
* On-Demand cache: requested tiles are downloaded and cached locally 
* Full cache: tiles are harvest one by one and cached locally
* Data-Only cache: only the private data (.mbtiles file) is downloaded and saved locally

## Usage and Integration
See the Quick Start guides for more information on how to achieve a simple integration:
* [Quick Start](https://github.com/tesera/andbtiles/wiki/Quick-Start-Guide) for API v8 and above. 

## Sample application 
Check out the [sample application](https://github.com/tesera/andbtiles/tree/master/sample) that builds UI on top of the library providing means for local file selection, remote file download, TileJSON endpoint parsing, cache method selection and map preview.  

## Example Use Case: Cordova Hybrid
1. Leaflet.js/Mapbox request tiles via http.
2. Angular custom http backend intercepts requests and uses custom angular service to request tile from Cordova Plugin.
3. Cordova plugin queries MBTilesContentProvider for tile.

## Roadmap:
* ver1: ability to add manual datasources
* ver2: ability to add datasources via IntentFilter.
