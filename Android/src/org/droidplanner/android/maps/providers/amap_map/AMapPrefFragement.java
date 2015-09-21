package org.droidplanner.android.maps.providers.amap_map;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.amap.api.maps.AMap;

import org.droidplanner.android.R;
import org.droidplanner.android.maps.providers.DPMapProvider;
import org.droidplanner.android.maps.providers.MapProviderPreferences;

/**
 * A simple {@link Fragment} subclass.
 */
public class AMapPrefFragement extends MapProviderPreferences {


   @Override
   public DPMapProvider getMapProvider(){
       return DPMapProvider.AMAP_MAP;
   };

   @Override
    public void onCreate(Bundle savedInstanceState){
       super.onCreate(savedInstanceState);

       addPreferencesFromResource(R.xml.preferences_amap_maps);

       setupAMapMapTypePref();
       setupAMapDownloadPref();
   }

    private static final String AMAP_TYPE_SATELLITE = "satellite";
    private static final String AMAP_TYPE_NORMAL = "normal";
    private static final String AMAP_TYPE_NIGHT = "night";
    private static final String PREF_AMAP_TYPE = "pref_amap_type";
    private static final String PREF_AMAP_DOWNLOAD = "pref_amap_map_download";
    private static final String DEFAULT_AMAP_TYPE = AMAP_TYPE_SATELLITE;

    private void setupAMapMapTypePref(){
        String mapTypeKey = PREF_AMAP_TYPE;
        Preference mapTypePref = findPreference(mapTypeKey);
        if (mapTypePref != null){
            SharedPreferences sharedPref = PreferenceManager
                    .getDefaultSharedPreferences(getActivity().getApplicationContext());
            mapTypePref.setSummary(sharedPref.getString(mapTypeKey, DEFAULT_AMAP_TYPE));
            mapTypePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });
        }
    }

    private void setupAMapDownloadPref(){
        Preference preference = findPreference(PREF_AMAP_DOWNLOAD);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(getActivity().getApplicationContext(),AMapOfflineMapActivity.class));
                return true;
            }
        });
    }

    public static int getMapType(Context context){
        int mapType = AMap.MAP_TYPE_SATELLITE;

        if (context != null){
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            String selectedType = sharedPref.getString(PREF_AMAP_TYPE, DEFAULT_AMAP_TYPE);

            switch (selectedType){
                case AMAP_TYPE_NORMAL:
                    mapType = AMap.MAP_TYPE_NORMAL;
                    break;
                case AMAP_TYPE_NIGHT:
                    mapType = AMap.MAP_TYPE_NIGHT;
                    break;
                case AMAP_TYPE_SATELLITE:
                    mapType = AMap.MAP_TYPE_SATELLITE;
                    break;
                default:
                    break;
            }
        }

        return mapType;
    }
}
