package org.droidplanner.android.utils;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;

import com.amap.api.location.CoordinateConverter;
import com.amap.api.location.DPoint;
import com.google.android.gms.maps.model.LatLng;
import com.o3dr.services.android.lib.coordinate.LatLong;

import timber.log.Timber;

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
        return new com.amap.api.maps.model.LatLng(coord.getLatitude(), coord.getLongitude());
    }

    public static LatLong AMapLatLngToCoord(Context context, com.amap.api.maps.model.LatLng point){
        return ConvertGCJ2GPS(context, point.latitude, point.longitude);
    }

    public static com.amap.api.maps.model.LatLng CoordConvert2AMapLatLang(Context context, LatLong coord){
        return ConvertGPS2GCJ(context, coord.getLatitude(), coord.getLongitude());
    }

    public static LatLong AMapLatLngConvert2Coord(Context context, com.amap.api.maps.model.LatLng point){
        return ConvertGCJ2GPS(context, point.latitude, point.longitude);
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

	// ---------------------------------------------------------------------------------------------
	// converter
    public static com.amap.api.maps.model.LatLng ConvertGPS2GCJ(Context context, double lat, double lng){

        CoordinateConverter converter = new CoordinateConverter(context);
        converter.from(CoordinateConverter.CoordType.GPS);

        DPoint point = new DPoint(lat, lng);
        DPoint dest;
        com.amap.api.maps.model.LatLng latLng = new com.amap.api.maps.model.LatLng(lat,lng);

        try{
            converter.coord(point);
            dest = converter.convert();
            latLng = new com.amap.api.maps.model.LatLng(dest.getLatitude(),dest.getLongitude());
        }catch (Exception e){
            Timber.e(e.toString());
        }

        return latLng;
    }

    public static LatLong ConvertGCJ2GPS(Context context, double lat, double lng){
        com.amap.api.maps.model.LatLng latLng = ConvertGPS2GCJ(context, lat,lng);

        return new LatLong(
                2 * lat - latLng.latitude,
                2 * lng - latLng.longitude
        );
    }
}
