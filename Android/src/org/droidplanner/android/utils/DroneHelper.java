package org.droidplanner.android.utils;

import android.content.res.Resources;
import android.location.Location;

import com.google.android.gms.maps.model.LatLng;
import com.o3dr.services.android.lib.coordinate.LatLong;

public class DroneHelper {

    // ---------------------------------------------------------------------------------------------
    // google map
	static public LatLng CoordToLatLang(LatLong coord) {
		return new LatLng(coord.getLatitude(), coord.getLongitude());
	}

    public static LatLong LatLngToCoord(LatLng point) {
        return new LatLong((float)point.latitude, (float) point.longitude);
    }

    // ---------------------------------------------------------------------------------------------
    // AMap
    public static com.amap.api.maps.model.LatLng CoordToAMapLatLang(LatLong coord){
        return new com.amap.api.maps.model.LatLng(coord.getLatitude(),coord.getLongitude());
    }

    public static LatLong AMapLatLngToCoord(com.amap.api.maps.model.LatLng point){
        return new LatLong((float)point.latitude,(float)point.longitude);
    }

    // ---------------------------------------------------------------------------------------------
    // common
	public static LatLong LocationToCoord(Location location) {
		return new LatLong((float) location.getLatitude(), (float) location.getLongitude());
	}

	public static int scaleDpToPixels(double value, Resources res) {
		final float scale = res.getDisplayMetrics().density;
		return (int) Math.round(value * scale);
	}
}
