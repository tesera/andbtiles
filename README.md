Andbtiles
=========

Andbtiles is an Android utility library that manages downloading, caching or harvesting of MBTiles and exposing them to external applications via a Content Provider.  
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
* [Quick Start](https://github.com/tesera/andbtiles/wiki/Quick-Start-Guide) for API v9 and above. 

## Sample application 
Check out the [sample application](https://github.com/tesera/andbtiles/tree/master/sample) that builds UI on top of the library providing means for local file selection, remote file download, TileJSON endpoint parsing, cache method selection and map preview.  

## Example Use Case: Web View
1. The web view loads a map and requests a tile via http.
2. The web view client intercepts the request and uses content resolver to query for tile data. 
3. The Andbtiles content provider serves the tile data to the web view.

## Limitations
A single content provider serves data only from a single database. Since the library can manage multiple maps, the connection to the previous content provider must be closed in order to use the new one.  
Android likes to keep connections alive, so in order to close it you need to end the process that uses it. Since this can be a major inconvenience when developing apps that show multiple maps or overlays, an alternative method for tile requests is provided.  
Check out the [Quick Start](https://github.com/tesera/andbtiles/wiki/Quick-Start-Guide) guide for more. 

## Roadmap:
* ver1: ability to add manual datasources
* ver2: ability to add datasources via IntentFilter.
