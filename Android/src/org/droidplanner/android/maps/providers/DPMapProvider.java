package org.droidplanner.android.maps.providers;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.droidplanner.android.maps.DPMap;
import org.droidplanner.android.maps.providers.amap_map.AMapMapFragment;
import org.droidplanner.android.maps.providers.amap_map.AMapPrefFragement;
import org.droidplanner.android.maps.providers.google_map.GoogleMapFragment;
import org.droidplanner.android.maps.providers.google_map.GoogleMapPrefFragment;
import org.droidplanner.android.utils.DroneHelper;

/**
 * Contains a listing of the various map providers supported, and implemented in
 * DroidPlanner.
 */
public enum DPMapProvider {
	/**
	 * Provide access to google map v2. Requires the google play services.
	 */
	GOOGLE_MAP {
		@Override
		public DPMap getMapFragment() {
			return new GoogleMapFragment();
		}

		@Override
		public MapProviderPreferences getMapProviderPreferences() {
			return new GoogleMapPrefFragment();
		}

        /**
         * If gms is valid and not need to upgrade, then google map is valid.
         * If gms is valid, but need to upgrade, then if location not in china, notify to upgrade.
         *
         * @param context
         * @return
         */
        @Override
        public boolean IsMapProviderValid(Context context){
            return isGooglePlayServicesValid(
                    GooglePlayServicesUtil.isGooglePlayServicesAvailable(context));
        }
	},

    AMAP_MAP {
        @Override
        public DPMap getMapFragment() {
            return new AMapMapFragment();
        }

        @Override
        public MapProviderPreferences getMapProviderPreferences() {
            return new AMapPrefFragement();
        }

        /**
         * AMap is always valid.
         *
         * @param context
         * @return
         */
        @Override
        public boolean IsMapProviderValid(Context context){
            return true;
        }
    };

	/**
	 * @return the fragment implementing the map.
	 */
	public abstract DPMap getMapFragment();

	/**
	 * @return the set of preferences supported by the map.
	 */
	public abstract MapProviderPreferences getMapProviderPreferences();

    /**
     * Make sure the map is valid.
     *
     * @param context
     * @return
     */
    public abstract boolean IsMapProviderValid(Context context);

	/**
	 * Returns the map type corresponding to the given map name.
	 * 
	 * @param mapName
	 *            name of the map type
	 * @return {@link DPMapProvider} object.
	 */
	public static DPMapProvider getMapProvider(String mapName) {
		if (mapName == null) {
			return null;
		}

		try {
			return DPMapProvider.valueOf(mapName);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

    public static DPMapProvider getValidMapProvider(Context context){
        final DPMapProvider[] providers = DPMapProvider.values();
        final int providersCount = providers.length;

        for (int i = 0; i < providersCount; i++) {
            if (providers[i].IsMapProviderValid(context)){
                return providers[i];
            }
        }

        return null;
    }

	/**
	 * By default, Google Map is the map provider.
	 */
	public static final DPMapProvider DEFAULT_MAP_PROVIDER = GOOGLE_MAP;

    /**
     *
     */
    public static boolean isGooglePlayServicesValid(int status){
        return status == ConnectionResult.SUCCESS;
    }

    /**
     * Context is application context.
     *
     * @param status
     * @param context
     * @return
     */
    public static boolean isGooglePlayServicesNeedShowError(int status, Context context){

        switch (status){
            case ConnectionResult.CANCELED:
            case ConnectionResult.INTERNAL_ERROR:
            case ConnectionResult.DEVELOPER_ERROR:
                return true;

            case ConnectionResult.SUCCESS:
                return false;

            // need check location, then may notify user the error
            case ConnectionResult.SERVICE_MISSING:
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
            case ConnectionResult.SERVICE_DISABLED:
            case ConnectionResult.SIGN_IN_REQUIRED:
            case ConnectionResult.INVALID_ACCOUNT:
            case ConnectionResult.RESOLUTION_REQUIRED:
            case ConnectionResult.NETWORK_ERROR:
            case ConnectionResult.SERVICE_INVALID:
            case ConnectionResult.LICENSE_CHECK_FAILED:
            case ConnectionResult.TIMEOUT:
            case ConnectionResult.INTERRUPTED:
            case ConnectionResult.API_UNAVAILABLE:
            case ConnectionResult.SIGN_IN_FAILED:
            case ConnectionResult.SERVICE_UPDATING:
            default:
                break;
        }

        return !DroneHelper.isLocationInChina(context);
    }
}
