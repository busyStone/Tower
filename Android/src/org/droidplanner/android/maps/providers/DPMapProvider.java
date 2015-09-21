package org.droidplanner.android.maps.providers;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.droidplanner.android.maps.DPMap;
import org.droidplanner.android.maps.providers.amap_map.AMapMapFragment;
import org.droidplanner.android.maps.providers.amap_map.AMapPrefFragement;
import org.droidplanner.android.maps.providers.google_map.GoogleMapFragment;
import org.droidplanner.android.maps.providers.google_map.GoogleMapPrefFragment;

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

        @Override
        public boolean IsMapProviderValid(Context context){
            // Check for the google play services is available
            final int playStatus = GooglePlayServicesUtil
                    .isGooglePlayServicesAvailable(context);
            return playStatus == ConnectionResult.SUCCESS;
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
}
