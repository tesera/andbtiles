andbtiles
=========

andbtiles is an Android utility application that manages local MBTiles caching and exposes MBTiles via a Content Provider. i.e. content://mbtiles/mymapid

####Features:

1. Add a local map source: ...
2. Add a remote map source: ...
3. Preview the map: ...
4. Exposes MBTiles data as a ContentProvider

####Initial Datasources:

Sources:

1. local MBTiles
2. remote MBTiles or TileJSON (has additional cache mode setting)

Caching Modes:

1. no-cache: app simply acts as a proxy.
2. on-deman-cache: app only caches tiles that have been requested.
3. full-cache: app will harvest tiles one by one and cache them locally.

#### Example Use Case: Cordova Hybrid.
1. Leaflet.js/Mapbox request tiles via http.
2. Angular custom http backend intercepts requests and uses custom angular service to request tile from Cordova Plugin.
3. Cordova plugin queries MBTilesContentProvider for tile.

####Roadmap:
* ver 1: ability to add manual datasources
* ver 2: ability to add datasources via IntentFilter.


