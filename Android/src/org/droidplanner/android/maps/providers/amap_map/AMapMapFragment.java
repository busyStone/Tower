package org.droidplanner.android.maps.providers.amap_map;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;
import com.amap.api.location.LocationProviderProxy;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapException;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.Projection;
import com.amap.api.maps.SupportMapFragment;

import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polygon;
import com.amap.api.maps.model.PolygonOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.VisibleRegion;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;

import org.droidplanner.android.R;
import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.fragments.SettingsFragment;
import org.droidplanner.android.maps.DPMap;
import org.droidplanner.android.maps.MarkerInfo;
import org.droidplanner.android.maps.providers.DPMapProvider;
import org.droidplanner.android.utils.DroneHelper;
import org.droidplanner.android.utils.collection.HashBiMap;
import org.droidplanner.android.utils.prefs.AutoPanMode;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicReference;

import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.FootPrint;
import com.o3dr.services.android.lib.drone.property.Gps;

import timber.log.Timber;

public class AMapMapFragment extends SupportMapFragment implements DPMap,
        AMap.OnMapLoadedListener,AMapLocationListener,LocationSource {

    private final AtomicReference<AutoPanMode> mPanMode = new AtomicReference<AutoPanMode>(
            AutoPanMode.DISABLED);

    private DroidPlannerPrefs mAppPrefs;

    private static final IntentFilter eventFilter = new IntentFilter();

    static {
        eventFilter.addAction(AttributeEvent.GPS_POSITION);
        eventFilter.addAction(SettingsFragment.ACTION_MAP_ROTATION_PREFERENCE_UPDATED);
    }

    private Drone getDroneApi() {
        return ((DroidPlannerApp) getActivity().getApplication()).getDrone();
    }

    private void updateCamera(final LatLong coord) {
        if (coord != null) {
            getMap().animateCamera(CameraUpdateFactory.newCameraPosition(
                    new CameraPosition(DroneHelper.CoordToAMapLatLang(coord), 18, 0, 30)
            ));
        }
    }

    private void setupMap(){
        AMap map = getMap();
        setupMapUI(map);
        setupMapListeners(map);

        map.setMyLocationEnabled(true);
        map.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
        map.setLocationSource(this);

        map.setMapType(AMapPrefFragement.getMapType(getActivity().getApplicationContext()));

        initLocation();
    }

    private void setupMapUI(AMap map){
        UiSettings mUiSettings = map.getUiSettings();
        mUiSettings.setMyLocationButtonEnabled(false);
        mUiSettings.setCompassEnabled(false);
        mUiSettings.setTiltGesturesEnabled(false);
        mUiSettings.setZoomControlsEnabled(false);
        mUiSettings.setRotateGesturesEnabled(mAppPrefs.isMapRotationEnabled());
    }

    private void setupMapListeners(AMap map){
        final AMap.OnMapClickListener onMapClickListener = new AMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (mMapClickListener != null){
                    mMapClickListener.onMapClick(DroneHelper.AMapLatLngToCoord(latLng));
                }
            }
        };
        map.setOnMapClickListener(onMapClickListener);

        map.setOnMapLongClickListener(new AMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                if (mMapLongClickListener != null) {
                    mMapLongClickListener.onMapLongClick(DroneHelper.AMapLatLngToCoord(latLng));
                }
            }
        });

        map.setOnMarkerDragListener(new AMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                if (mMarkerDragListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    markerInfo.setPosition(DroneHelper.AMapLatLngToCoord(marker.getPosition()));
                    mMarkerDragListener.onMarkerDragStart(markerInfo);
                }
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                if (mMarkerDragListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    markerInfo.setPosition(DroneHelper.AMapLatLngToCoord(marker.getPosition()));
                    mMarkerDragListener.onMarkerDrag(markerInfo);
                }
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                if (mMarkerDragListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    markerInfo.setPosition(DroneHelper.AMapLatLngToCoord(marker.getPosition()));
                    mMarkerDragListener.onMarkerDragEnd(markerInfo);
                }
            }
        });

        map.setOnMarkerClickListener(new AMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (useMarkerClickAsMapClick) {
                    onMapClickListener.onMapClick(marker.getPosition());
                    return true;
                }

                if (mMarkerClickListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    if (markerInfo != null) {
                        return mMarkerClickListener.onMarkerClick(markerInfo);
                    }
                }

                return false;
            }
        });
    }

    private void setAutoPanMode(AutoPanMode currrent, AutoPanMode update){
        if (mPanMode.compareAndSet(currrent, update)){
            switch (currrent){
                case DRONE:
                    LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                            .unregisterReceiver(eventReceiver);
                    break;
                case DISABLED:
                default:
                    break;
            }

            switch (update){
                case DRONE:
                    LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                            .registerReceiver(eventReceiver, eventFilter);
                    break;
                case DISABLED:
                default:
                    break;
            }
        }
    }

    // TODO: 15/9/18 alpha infoWindowAnchor
    private void generateMarker(MarkerInfo markerInfo, LatLng position, boolean isDraggable){
        final MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .draggable(isDraggable)
                .anchor(markerInfo.getAnchorU(), markerInfo.getAnchorV())
                .snippet(markerInfo.getSnippet())
                .title(markerInfo.getTitle())
                .setFlat(markerInfo.isFlat())
                .visible(markerInfo.isVisible());

        final Bitmap markerIcon = markerInfo.getIcon(getResources());
        if (markerIcon != null){
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(markerIcon));
        }

        Marker marker = getMap().addMarker(markerOptions);
        mBiMarkersMap.put(markerInfo, marker);
    }

    // TODO: 15/9/18 setAlpha setInfoWindowAnchor
    private void updateMarker(Marker marker, MarkerInfo markerInfo, LatLng position,
                              boolean isDraggable) {
        final Bitmap markerIcon = markerInfo.getIcon(getResources());
        if (markerIcon != null) {
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(markerIcon));
        }

        marker.setAnchor(markerInfo.getAnchorU(), markerInfo.getAnchorV());
        marker.setPosition(position);
        marker.setRotateAngle(markerInfo.getRotation());
        marker.setSnippet(markerInfo.getSnippet());
        marker.setTitle(markerInfo.getTitle());
        marker.setDraggable(isDraggable);
        marker.setFlat(markerInfo.isFlat());
        marker.setVisible(markerInfo.isVisible());
    }

    private LatLngBounds getBounds(List<LatLng> pointsList) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : pointsList) {
            builder.include(point);
        }
        return builder.build();
    }

    private AMapLocation getLastLocation(){
        return LocationManagerProxy.getInstance(getActivity())
                .getLastKnownLocation(LocationProviderProxy.AMapNetwork);
    }

    private void requestLastLoaction(){
        AMapLocation aMapLocation = getLastLocation();
        if (aMapLocation != null && mLocationListener != null){
            mLocationListener.onLocationChanged(aMapLocation);
        }
    }

    private static final float GO_TO_MY_LOCATION_ZOOM = 17f;

    private void requestGoToMyLocation(){
        AMapLocation aMapLocation = getLastLocation();
        if (aMapLocation != null){
            updateCamera(DroneHelper.LocationToCoord(aMapLocation), GO_TO_MY_LOCATION_ZOOM);

            if (mLocationListener != null){
                mLocationListener.onLocationChanged(aMapLocation);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Broadcast Receiver
    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action){
                case AttributeEvent.GPS_POSITION:
                    if (mPanMode.get() == AutoPanMode.DRONE){
                        final Drone drone = getDroneApi();
                        if (!drone.isConnected()){
                            return;
                        }

                        final Gps droneGps = drone.getAttribute(AttributeType.GPS);
                        if (droneGps != null && droneGps.isValid()){
                            final LatLong droneLocation = droneGps.getPosition();
                            updateCamera(droneLocation);
                        }
                    }
                    break;
                case SettingsFragment.ACTION_MAP_ROTATION_PREFERENCE_UPDATED:
                    break;
            }
        }
    };


    // ---------------------------------------------------------------------------------------------
    // LocationSource
    private OnLocationChangedListener mLocationChangedListener;
    private LocationManagerProxy mAMapLocationManager;

    private void initLocation(){
        mAMapLocationManager = LocationManagerProxy.getInstance(getActivity());
        //此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
        //注意设置合适的定位时间的间隔，并且在合适时间调用removeUpdates()方法来取消定位请求
        //在定位结束后，在合适的生命周期调用destroy()方法
        //其中如果间隔时间为-1，则定位只定一次
        mAMapLocationManager.requestLocationData(
                LocationProviderProxy.AMapNetwork, -1, 15, this);
        mAMapLocationManager.setGpsEnable(true);
    }

    @Override
    public void activate(OnLocationChangedListener listener){
        mLocationChangedListener = listener;
        if (mAMapLocationManager == null){
            mAMapLocationManager = LocationManagerProxy.getInstance(getActivity());
            mAMapLocationManager.requestLocationData(LocationProviderProxy.AMapNetwork,
                    20*1000, // minTime
                    10,      // minDistance
                    this);
        }
    }

    @Override
    public void deactivate(){
        mLocationChangedListener = null;

        if (mAMapLocationManager != null) {
            mAMapLocationManager.removeUpdates(this);
            mAMapLocationManager.destroy();
        }
        mAMapLocationManager = null;
    }

    // ---------------------------------------------------------------------------------------------
    // AMapLocationListener

    private Marker userMarker;

    @Override
    public void onLocationChanged(AMapLocation aMapLocation){

        if (aMapLocation == null){
            return;
        }

        if (userMarker == null){
            final MarkerOptions options = new MarkerOptions()
                    .position(new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()))
                    .draggable(false)
                    .setFlat(true)
                    .visible(true)
                    .anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.user_location));
            userMarker = getMap().addMarker(options);
        }else{
            userMarker.setPosition(new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
        }

        if (mPanMode.get() == AutoPanMode.USER) {
            Timber.d("User location changed.");
            updateCamera(DroneHelper.LocationToCoord(aMapLocation),
                    (int) getMap().getCameraPosition().zoom);
        }

        if (mLocationListener != null){
            mLocationListener.onLocationChanged(aMapLocation);
        }

        if (mLocationChangedListener != null){
            mLocationChangedListener.onLocationChanged(aMapLocation);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // LocationListener
    @Override
    public void onLocationChanged(Location location) {
    }
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }
    @Override
    public void onProviderDisabled(String provider) {
    }

    // ---------------------------------------------------------------------------------------------
    // AMap.OnMapLoadedListener
    // TODO: 15/9/18 setupMapOverlay
    @Override
    public void onMapLoaded(){
        // 没有调用
    }

    // ---------------------------------------------------------------------------------------------
    // DPMap
    private Polyline flightPath;
    private Polyline missionPath;
    private Polyline mDroneLeashPath;
    private int maxFlightPathSize;
    private final HashBiMap<MarkerInfo, Marker> mBiMarkersMap = new HashBiMap<MarkerInfo, Marker>();

    private DPMap.OnMapClickListener mMapClickListener;
    private DPMap.OnMapLongClickListener mMapLongClickListener;
    private DPMap.OnMarkerClickListener mMarkerClickListener;
    private DPMap.OnMarkerDragListener mMarkerDragListener;
    private android.location.LocationListener mLocationListener;

    protected boolean useMarkerClickAsMapClick = false;

    private List<Polygon> polygonsPaths = new ArrayList<Polygon>();

    private Polygon footprintPoly;

    @Override
    public void clearFlightPath(){
        if (flightPath != null){
            List<LatLng>oldFlightPath = flightPath.getPoints();
            oldFlightPath.clear();
            flightPath.setPoints(oldFlightPath);
        }
    }

    @Override
    public LatLong getMapCenter(){
        return DroneHelper.AMapLatLngToCoord(getMap().getCameraPosition().target);
    }

    @Override
    public float getMapZoomLevel(){
        return getMap().getCameraPosition().zoom;
    }

    @Override
    public float getMaxZoomLevel(){
        return getMap().getMaxZoomLevel();
    }

    @Override
    public float getMinZoomLevel(){
        return getMap().getMinZoomLevel();
    }

    @Override
    public void selectAutoPanMode(AutoPanMode target){
        final AutoPanMode currentMode = mPanMode.get();

        if (currentMode == target){
            return;
        }

        setAutoPanMode(currentMode, target);
    }

    @Override
    public DPMapProvider getProvider(){
        return DPMapProvider.AMAP_MAP;
    }

    @Override
    public void addFlightPathPoint(LatLong coord){
        final LatLng position = DroneHelper.CoordToAMapLatLang(coord);

        if (maxFlightPathSize > 0){
            if (flightPath == null){
                PolylineOptions flightPathOptions = new PolylineOptions();
                flightPathOptions.color(FLIGHT_PATH_DEFAULT_COLOR)
                        .width(FLIGHT_PATH_DEFAULT_WIDTH).zIndex(1);
                flightPath = getMap().addPolyline(flightPathOptions);
            }

            List<LatLng> oldFlightPath = flightPath.getPoints();
            if (oldFlightPath.size() > maxFlightPathSize){
                oldFlightPath.remove(0);
            }
            oldFlightPath.add(position);
            flightPath.setPoints(oldFlightPath);
        }
    }

    @Override
    public void clearMarkers(){
        for (Marker marker : mBiMarkersMap.valueSet()){
            marker.remove();
        }

        mBiMarkersMap.clear();
    }

    @Override
    public void updateMarker(MarkerInfo markerInfo){
        updateMarker(markerInfo, markerInfo.isDraggable());
    }

    @Override
    public void updateMarker(MarkerInfo markerInfo, boolean isDraggable){
        final LatLong coord = markerInfo.getPosition();
        if (coord == null){
            return;
        }

        final LatLng position = DroneHelper.CoordToAMapLatLang(coord);
        Marker marker = mBiMarkersMap.getValue(markerInfo);
        if (marker == null){
            generateMarker(markerInfo, position, isDraggable);
        }else{
            updateMarker(marker, markerInfo, position, isDraggable);
        }
    }

    @Override
    public void updateMarkers(List<MarkerInfo> markerInfos){
        for (MarkerInfo info : markerInfos){
            updateMarker(info);
        }
    }

    @Override
    public void updateMarkers(List<MarkerInfo> markersInfos, boolean isDraggable) {
        for (MarkerInfo info : markersInfos) {
            updateMarker(info, isDraggable);
        }
    }

    @Override
    public Set<MarkerInfo> getMarkerInfoList() {
        return new HashSet<MarkerInfo>(mBiMarkersMap.keySet());
    }

    @Override
    public List<LatLong> projectPathIntoMap(List<LatLong> path) {
        List<LatLong> coords = new ArrayList<LatLong>();
        Projection projection = getMap().getProjection();

        for (LatLong point : path) {
            LatLng coord = projection.fromScreenLocation(new Point((int) point
                    .getLatitude(), (int) point.getLongitude()));
            coords.add(DroneHelper.AMapLatLngToCoord(coord));
        }

        return coords;
    }

    @Override
    public void removeMarkers(Collection<MarkerInfo> markerInfoList) {
        if (markerInfoList == null || markerInfoList.isEmpty()) {
            return;
        }

        for (MarkerInfo markerInfo : markerInfoList) {
            Marker marker = mBiMarkersMap.getValue(markerInfo);
            if (marker != null) {
                marker.remove();
                mBiMarkersMap.removeKey(markerInfo);
            }
        }
    }

    // TODO: 15/9/18 setPadding
    @Override
    public void setMapPadding(int left, int top, int right, int bottom) {
    }

    @Override
    public void setOnMapClickListener(OnMapClickListener listener) {
        mMapClickListener = listener;
    }

    @Override
    public void setOnMapLongClickListener(OnMapLongClickListener listener) {
        mMapLongClickListener = listener;
    }

    @Override
    public void setOnMarkerDragListener(OnMarkerDragListener listener) {
        mMarkerDragListener = listener;
    }

    @Override
    public void setOnMarkerClickListener(OnMarkerClickListener listener) {
        mMarkerClickListener = listener;
    }

    @Override
    public void setLocationListener(android.location.LocationListener receiver) {
        mLocationListener = receiver;

        //Update the listener with the last received location
        if (mLocationListener != null) {
            requestLastLoaction();
        }
    }

    @Override
    public void updateCamera(final LatLong coord, final float zoomLevel) {
        if (coord != null) {
            getMap().animateCamera(CameraUpdateFactory.newLatLngZoom(
                    DroneHelper.CoordToAMapLatLang(coord), zoomLevel));
        }
    }

    @Override
    public void updateCameraBearing(float bearing) {
        final CameraPosition cameraPosition = new CameraPosition(DroneHelper.CoordToAMapLatLang
                (getMapCenter()), getMapZoomLevel(), 0, bearing);
        getMap().animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public void updateDroneLeashPath(PathSource pathSource) {
        List<LatLong> pathCoords = pathSource.getPathPoints();
        final List<LatLng> pathPoints = new ArrayList<LatLng>(pathCoords.size());
        for (LatLong coord : pathCoords) {
            pathPoints.add(DroneHelper.CoordToAMapLatLang(coord));
        }

        if (mDroneLeashPath == null) {
            PolylineOptions flightPath = new PolylineOptions();
            flightPath.color(DRONE_LEASH_DEFAULT_COLOR).width(
                    DroneHelper.scaleDpToPixels(DRONE_LEASH_DEFAULT_WIDTH,
                            getResources()));
            mDroneLeashPath = getMap().addPolyline(flightPath);
        }

        mDroneLeashPath.setPoints(pathPoints);
    }

    @Override
    public void updateMissionPath(PathSource pathSource) {
        List<LatLong> pathCoords = pathSource.getPathPoints();
        final List<LatLng> pathPoints = new ArrayList<LatLng>(pathCoords.size());
        for (LatLong coord : pathCoords) {
            pathPoints.add(DroneHelper.CoordToAMapLatLang(coord));
        }

        if (missionPath == null) {
            PolylineOptions pathOptions = new PolylineOptions();
            pathOptions.color(MISSION_PATH_DEFAULT_COLOR).width(
                    MISSION_PATH_DEFAULT_WIDTH);
            missionPath = getMap().addPolyline(pathOptions);
        }

        missionPath.setPoints(pathPoints);
    }

    @Override
    public void updatePolygonsPaths(List<List<LatLong>> paths) {
        for (Polygon poly : polygonsPaths) {
            poly.remove();
        }

        for (List<LatLong> contour : paths) {
            PolygonOptions pathOptions = new PolygonOptions();
            pathOptions.strokeColor(POLYGONS_PATH_DEFAULT_COLOR).strokeWidth(
                    POLYGONS_PATH_DEFAULT_WIDTH);
            final List<LatLng> pathPoints = new ArrayList<LatLng>(contour.size());
            for (LatLong coord : contour) {
                pathPoints.add(DroneHelper.CoordToAMapLatLang(coord));
            }
            pathOptions.addAll(pathPoints);
            polygonsPaths.add(getMap().addPolygon(pathOptions));
        }
    }

    @Override
    public void addCameraFootprint(FootPrint footprintToBeDraw) {
        PolygonOptions pathOptions = new PolygonOptions();
        pathOptions.strokeColor(FOOTPRINT_DEFAULT_COLOR).strokeWidth(FOOTPRINT_DEFAULT_WIDTH);
        pathOptions.fillColor(FOOTPRINT_FILL_COLOR);

        for (LatLong vertex : footprintToBeDraw.getVertexInGlobalFrame()) {
            pathOptions.add(DroneHelper.CoordToAMapLatLang(vertex));
        }
        getMap().addPolygon(pathOptions);

    }

    /**
     * Save the map camera state on a preference file
     * http://stackoverflow.com/questions
     * /16697891/google-maps-android-api-v2-restoring
     * -map-state/16698624#16698624
     */
    @Override
    public void saveCameraPosition() {
        CameraPosition camera = getMap().getCameraPosition();
        mAppPrefs.prefs.edit()
                .putFloat(PREF_LAT, (float) camera.target.latitude)
                .putFloat(PREF_LNG, (float) camera.target.longitude)
                .putFloat(PREF_BEA, camera.bearing)
                .putFloat(PREF_TILT, camera.tilt)
                .putFloat(PREF_ZOOM, camera.zoom).apply();
    }

    @Override
    public void loadCameraPosition() {
        final SharedPreferences settings = mAppPrefs.prefs;

        final CameraPosition.Builder camera = new CameraPosition.Builder();
        camera.bearing(settings.getFloat(PREF_BEA, DEFAULT_BEARING));
        camera.tilt(settings.getFloat(PREF_TILT, DEFAULT_TILT));
        camera.zoom(settings.getFloat(PREF_ZOOM, DEFAULT_ZOOM_LEVEL));
        camera.target(new LatLng(settings.getFloat(PREF_LAT, DEFAULT_LATITUDE),
                settings.getFloat(PREF_LNG, DEFAULT_LONGITUDE)));

        getMap().moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()));
    }

    @Override
    public void zoomToFit(List<LatLong> coords){
        if (!coords.isEmpty()){
            final List<LatLng> points = new ArrayList<LatLng>();
            for (LatLong corrd : coords){
                points.add(DroneHelper.CoordToAMapLatLang(corrd));
            }

            final LatLngBounds bounds = getBounds(points);
            final Activity activity = getActivity();
            if (activity == null){
                return;
            }

            final View rootView = ((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            if (rootView == null){
                return;
            }

            final int height = rootView.getHeight();
            final int width = rootView.getWidth();
            Timber.d("Screen W %d, H %d", width, height);
            if (height > 0 && width > 0){
                getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,width,height,100));
            }
        }
    }

    @Override
    public void zoomToFitMyLocation(final List<LatLong> coords){
        final Location myLocation = getLastLocation();
        if (myLocation != null) {
            final List<LatLong> updatedCoords = new ArrayList<LatLong>(coords);
            updatedCoords.add(DroneHelper.LocationToCoord(myLocation));
            zoomToFit(updatedCoords);
        } else {
            zoomToFit(coords);
        }
    }

    @Override
    public void goToMyLocation(){
        requestGoToMyLocation();
    }

    @Override
    public void goToDroneLocation(){
        Drone dpApi = getDroneApi();
        if (!dpApi.isConnected()){
            return;
        }

        Gps gps = dpApi.getAttribute(AttributeType.GPS);
        if (!gps.isValid()){
            Toast.makeText(getActivity().getApplicationContext(),
                    R.string.drone_no_location,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final float currentZoomLevel = getMap().getCameraPosition().zoom;
        final LatLong droneLocation = gps.getPosition();
        updateCamera(droneLocation, (int) currentZoomLevel);
    }

    @Override
    public void skipMarkerClickEvents(boolean skip){
        useMarkerClickAsMapClick = skip;
    }

    @Override
    public void updateRealTimeFootprint(FootPrint footPrint){
        List<LatLong> pathPoints = footPrint == null
                ? Collections.<LatLong>emptyList()
                : footPrint.getVertexInGlobalFrame();

        if (pathPoints.isEmpty()){
            if (footprintPoly != null){
                footprintPoly.remove();
                footprintPoly = null;
            }
        }else{
            if (footprintPoly == null){
                PolygonOptions pathOptions = new PolygonOptions()
                        .strokeColor(FOOTPRINT_DEFAULT_COLOR)
                        .strokeWidth(FOOTPRINT_DEFAULT_WIDTH)
                        .fillColor(FOOTPRINT_FILL_COLOR);

                for (LatLong vertex : pathPoints){
                    pathOptions.add(DroneHelper.CoordToAMapLatLang(vertex));
                }
                footprintPoly = getMap().addPolygon(pathOptions);
            }else{
                List<LatLng> list = new ArrayList<LatLng>();
                for (LatLong vertex : pathPoints){
                    list.add(DroneHelper.CoordToAMapLatLang(vertex));
                }
                footprintPoly.setPoints(list);
            }
        }
    }

    public VisibleMapArea getVisibleMapArea(){
        final AMap map = getMap();
        if(map == null)
            return null;

        final VisibleRegion mapRegion = map.getProjection().getVisibleRegion();
        return new VisibleMapArea(DroneHelper.AMapLatLngToCoord(mapRegion.farLeft),
                DroneHelper.AMapLatLngToCoord(mapRegion.nearLeft),
                DroneHelper.AMapLatLngToCoord(mapRegion.nearRight),
                DroneHelper.AMapLatLngToCoord(mapRegion.farRight));
    }

    // ---------------------------------------------------------------------------------------------
    // SupportMapFragment

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle){
        setHasOptionsMenu(true);

        final View view = super.onCreateView(inflater,viewGroup,bundle);

        mAppPrefs = new DroidPlannerPrefs(getActivity().getApplicationContext());

        final Bundle args = getArguments();
        if (args != null){
            maxFlightPathSize = args.getInt(EXTRA_MAX_FLIGHT_PATH_SIZE);
        }

        return view;
    }

    @Override
    public void onStart(){
        super.onStart();

        LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                .registerReceiver(eventReceiver, eventFilter);
    }

    @Override
    public void onResume(){
        super.onResume();

        setupMap();
    }

    @Override
    public void onPause(){
        super.onPause();

        deactivate();
    }

    @Override
    public void onStop(){
        super.onStop();

        LocalBroadcastManager.getInstance(getActivity().getApplicationContext())
                .unregisterReceiver(eventReceiver);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_amap_map, menu);
    }

    // TODO: 15/9/18 call download
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.menu_download_amap_map:
                return super.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // TODO: 15/9/18  check download menu
    @Override
    public void onPrepareOptionsMenu(Menu menu){

    }


}
