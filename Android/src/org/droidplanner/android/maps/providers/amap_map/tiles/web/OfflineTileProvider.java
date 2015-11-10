package org.droidplanner.android.maps.providers.amap_map.tiles.web;

import android.content.Context;

import com.amap.api.maps.model.Tile;
import com.amap.api.maps.model.TileProvider;

import org.droidplanner.android.data.DatabaseState;
import org.droidplanner.android.maps.providers.amap_map.tiles.web.offline.AMapDownloader;

/**
 * Created by clover on 15/11/4.
 */
public class OfflineTileProvider implements TileProvider{

    private final Context context;
    private final int minZoomLevel;
    private final int maxZoomLevel;

    public OfflineTileProvider(Context context, int minZoomLevel, int maxZoomLevel){
        this.context = context;
        this.minZoomLevel = minZoomLevel;
        this.maxZoomLevel = maxZoomLevel;
    }

    @Override
    public Tile getTile(int x, int y, int zoom){
        if (zoom > maxZoomLevel){
            return TileProvider.NO_TILE;
        }

        final String tileUri = AMapDownloader.getMapURLParam(x, y, zoom);
        byte[] data = DatabaseState.getOfflineDatabaseHandlerForMapId(context, "amap.web").dataForURL(tileUri);
        if (data == null || data.length == 0)
            return TileProvider.NO_TILE;

        return new Tile(256, 256, data);
    }

    @Override
    public int getTileWidth(){
        return 256;
    }

    @Override
    public int getTileHeight(){
        return 256;
    }
}
