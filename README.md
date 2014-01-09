andbtiles
=========

andbtiles is an Android application that manages local MBTiles caching and exposes MBTiles via a Content Provider. i.e. content://mbtiles/mymapid

####Features:

1. Add a data source:
2. Configure a data source:
3. Preview the map:

####Initial Datasources:

1. local database
2. MBTiles URL download
3. Mapbox Account
4. AWS S3

####Mapbox Integration
By adding you Mapbox credential the app will fetch you maps from the Mapbox API and allow you to control how the maps will be cached.

1. no-cache: app simply acts as a proxy.
2. on-deman-cache: app only caches tiles that have been requested.
3. full-cache: app will harvest tiles and cache them locally.

#### Example Use Case:
1. Leaflet.js/Mapbox request tiles via http.
2. Angular custom http backend intercepts requests and uses custom angular service to request tile from Cordova Plugin.
3. Cordova plugin queries MBTilesContentProvider for tile.

####Roadmap:
* ver 1: ability to add manual datasources
* ver 2: ability to add datasources via IntentFilter.


