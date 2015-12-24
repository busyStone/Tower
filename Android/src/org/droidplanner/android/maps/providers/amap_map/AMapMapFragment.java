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
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.CoordinateConverter;
import com.amap.api.location.DPoint;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
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
import com.amap.api.maps.model.TileOverlay;
import com.amap.api.maps.model.TileOverlayOptions;
import com.amap.api.maps.model.TileProvider;
import com.amap.api.maps.model.VisibleRegion;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;

import org.droidplanner.android.R;
import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.fragments.SettingsFragment;
import org.droidplanner.android.graphic.map.GraphicHome;
import org.droidplanner.android.maps.DPMap;
import org.droidplanner.android.maps.MarkerInfo;
import org.droidplanner.android.maps.providers.DPMapProvider;
import org.droidplanner.android.maps.providers.amap_map.tiles.web.OfflineTileProvider;
import org.droidplanner.android.utils.DroneHelper;
import org.droidplanner.android.utils.collection.HashBiMap;
import org.droidplanner.android.utils.file.DirectoryPath;
import org.droidplanner.android.utils.prefs.AutoPanMode;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.FootPrint;
import com.o3dr.services.android.lib.drone.property.Gps;

import timber.log.Timber;

public class AMapMapFragment extends SupportMapFragment implements DPMap,
        AMap.OnMapLoadedListener,AMapLocationListener {

    private final AtomicReference<AutoPanMode> mPanMode = new AtomicReference<AutoPanMode>(
            AutoPanMode.DISABLED);

    private DroidPlannerPrefs mAppPrefs;

    private static final IntentFilter eventFilter = new IntentFilter();

    private AMap mAmap;
    private TileOverlay offlineTileProvider;

    private Context appContext;

    static {
        eventFilter.addAction(AttributeEvent.GPS_POSITION);
        eventFilter.addAction(SettingsFragment.ACTION_MAP_ROTATION_PREFERENCE_UPDATED);
    }

    private Drone getDroneApi() {
        return ((DroidPlannerApp) getActivity().getApplication()).getDrone();
    }

    private void updateCamera(final LatLong coord) {
        if (coord != null) {
            getAMap().animateCamera(CameraUpdateFactory.newLatLngZoom(
                    DroneHelper.CoordConvert2AMapLatLang(appContext, coord),
                    getAMap().getCameraPosition().zoom));
        }
    }

    private void setupMap(AMap map){

        setupMapUI(map);
        setupMapOverlay(map);
        setupMapListeners(map);

        map.setMyLocationEnabled(true);
        map.setMyLocationType(AMap.LOCATION_TYPE_MAP_FOLLOW);
    }

    private void setupMapUI(AMap map){
        UiSettings mUiSettings = map.getUiSettings();
        mUiSettings.setMyLocationButtonEnabled(false);
        mUiSettings.setCompassEnabled(false);
        mUiSettings.setTiltGesturesEnabled(false);
        mUiSettings.setZoomControlsEnabled(false);
        mUiSettings.setRotateGesturesEnabled(mAppPrefs.isMapRotationEnabled());
    }

    private void setupMapOverlay(AMap map){
        Context context = getContext();

        if (!AMapPrefFragement.isOfflineMapLayerEnabled(context)){
            if (offlineTileProvider != null){
                offlineTileProvider.remove();
                offlineTileProvider = null;
            }

            map.setMapType(AMapPrefFragement.getMapType(appContext));

            return;
        }else{
            map.setMapType(AMap.MAP_TYPE_NORMAL);
        }

        if (offlineTileProvider == null){
            final TileProvider tileProvider = new OfflineTileProvider(context,
                    (int)map.getMinZoomLevel(),
                    (int)map.getMaxZoomLevel());
            offlineTileProvider = map.addTileOverlay(new TileOverlayOptions()
                            .tileProvider(tileProvider)
                            .memCacheSize(1024)
                            .memoryCacheEnabled(true)
            );
        }
    }

    private void setupMapListeners(AMap map){
        final AMap.OnMapClickListener onMapClickListener = new AMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (mMapClickListener != null){
                    mMapClickListener.onMapClick(
                            DroneHelper.AMapLatLngConvert2Coord(appContext, latLng));
                }
            }
        };
        map.setOnMapClickListener(onMapClickListener);

        map.setOnMapLongClickListener(new AMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                if (mMapLongClickListener != null) {
                    mMapLongClickListener.onMapLongClick(
                            DroneHelper.AMapLatLngConvert2Coord(appContext, latLng));
                }
            }
        });

        map.setOnMarkerDragListener(new AMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                if (mMarkerDragListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    if(!(markerInfo instanceof GraphicHome)) {
                        markerInfo.setPosition(
                                DroneHelper.AMapLatLngConvert2Coord(appContext, marker.getPosition()));
                        mMarkerDragListener.onMarkerDragStart(markerInfo);
                    }
                }
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                if (mMarkerDragListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    if(!(markerInfo instanceof GraphicHome)) {
                        markerInfo.setPosition(DroneHelper.AMapLatLngConvert2Coord(
                                appContext, marker.getPosition()));
                        mMarkerDragListener.onMarkerDrag(markerInfo);
                    }

                }
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                if (mMarkerDragListener != null) {
                    final MarkerInfo markerInfo = mBiMarkersMap.getKey(marker);
                    markerInfo.setPosition(DroneHelper.AMapLatLngConvert2Coord(
                            appContext, marker.getPosition()));
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
        mPanMode.compareAndSet(currrent, update);
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

        Marker marker = getAMap().addMarker(markerOptions);
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
        AMapLocation aMapLocation = null;

        if (aMapLocationClient != null){
            aMapLocation = aMapLocationClient.getLastKnownLocation();
            if (aMapLocation == null || aMapLocation.getErrorCode() != 0){
                aMapLocation = null;
            }
        }

        return aMapLocation;
    }

    private void requestLastLocation(){
        AMapLocation aMapLocation = getLastLocation();
        if (aMapLocation != null && mLocationListener != null){
            mLocationListener.onLocationChanged(aMapLocation);
        }
    }

    private static final float GO_TO_MY_LOCATION_ZOOM = 17f;

    private void requestGoToMyLocation(){
        AMapLocation aMapLocation = getLastLocation();
        if (aMapLocation != null){
            updateMyLocationCamera(
                    new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()),
                    GO_TO_MY_LOCATION_ZOOM
            );

            if (mLocationListener != null){
                mLocationListener.onLocationChanged(aMapLocation);
            }
        }
    }

    public AMap getAMap(){
        if (mAmap == null){
            mAmap = getMap();
        }

        return mAmap;
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
                    if (mAmap != null){
                        setupMapUI(mAmap);
                    }

                    break;
            }
        }
    };


    // ---------------------------------------------------------------------------------------------
    // LocationSource
    private AMapLocationClient aMapLocationClient;
    private AMapLocationClientOption aMapLocationClientOption;

    private void initLocation(){

        if (aMapLocationClient != null) {
            aMapLocationClient.startLocation();

            return;
        }

        aMapLocationClientOption = new AMapLocationClientOption();
        aMapLocationClientOption.setLocationMode(
                AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        aMapLocationClientOption.setNeedAddress(false);

        aMapLocationClient = new AMapLocationClient(appContext);
        aMapLocationClient.setLocationListener(this);
        aMapLocationClient.setLocationOption(aMapLocationClientOption);
        aMapLocationClient.startLocation();
    }

    private void stopLocation(){

        if (aMapLocationClient != null){
            aMapLocationClient.stopLocation();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // AMapLocationListener

    private Marker userMarker;

    @Override
    public void onLocationChanged(AMapLocation aMapLocation){

        if (aMapLocation == null){
            return;
        }

        if (aMapLocation.getErrorCode() != 0){
            Timber.e("AmapError","location Error, ErrCode:"
                    + aMapLocation.getErrorCode() + ", errInfo:"
                    + aMapLocation.getErrorInfo());
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
            userMarker = getAMap().addMarker(options);
        }else{
            userMarker.setPosition(new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
        }

        if (mPanMode.get() == AutoPanMode.USER) {
            Timber.d("User location changed.");
            updateMyLocationCamera(
                    new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()),
                    (int) getAMap().getCameraPosition().zoom
            );
        }

        if (mLocationListener != null){
            mLocationListener.onLocationChanged(aMapLocation);
        }
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
        // not used
        return new LatLong(0,0);
    }

    public LatLng getAMapCenter(){
        return getAMap().getCameraPosition().target;
    }

    @Override
    public float getMapZoomLevel(){
        return getAMap().getCameraPosition().zoom;
    }

    @Override
    public float getMaxZoomLevel(){
        return getAMap().getMaxZoomLevel();
    }

    @Override
    public float getMinZoomLevel(){
        return getAMap().getMinZoomLevel();
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
        final LatLng position = DroneHelper.CoordConvert2AMapLatLang(appContext, coord);

        if (maxFlightPathSize > 0){
            if (flightPath == null){
                PolylineOptions flightPathOptions = new PolylineOptions();
                flightPathOptions.color(FLIGHT_PATH_DEFAULT_COLOR)
                        .width(FLIGHT_PATH_DEFAULT_WIDTH).zIndex(1);
                flightPath = getAMap().addPolyline(flightPathOptions);
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
    public void updateMarker(MarkerInfo markerInfo) {
        updateMarker(markerInfo, markerInfo.isDraggable());
    }

    @Override
    public void updateMarker(MarkerInfo markerInfo, boolean isDraggable){
        final LatLong coord = markerInfo.getPosition();
        if (coord == null){
            return;
        }

        final LatLng position = DroneHelper.CoordConvert2AMapLatLang(appContext, coord);
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
        Projection projection = getAMap().getProjection();

        for (LatLong point : path) {
            LatLng coord = projection.fromScreenLocation(new Point((int) point
                    .getLatitude(), (int) point.getLongitude()));
            coords.add(DroneHelper.AMapLatLngConvert2Coord(appContext, coord));
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
            requestLastLocation();
        }
    }

    @Override
    public void updateCamera(final LatLong coord, final float zoomLevel) {
        if (coord != null) {
            getAMap().animateCamera(CameraUpdateFactory.newLatLngZoom(
                    DroneHelper.CoordToAMapLatLang(coord), zoomLevel));
        }
    }

    public void updateMyLocationCamera(final LatLng latLng, final float zoomLevel){
        if (latLng == null){
            return;
        }

        getAMap().animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel));
    }

    @Override
    public void updateCameraBearing(float bearing) {
        final CameraPosition cameraPosition = new CameraPosition(
                getAMapCenter(), getMapZoomLevel(), 0, bearing);
        getAMap().animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public void updateDroneLeashPath(PathSource pathSource) {
        List<LatLong> pathCoords = pathSource.getPathPoints();
        final List<LatLng> pathPoints = new ArrayList<LatLng>(pathCoords.size());
        for (LatLong coord : pathCoords) {
            pathPoints.add(DroneHelper.CoordConvert2AMapLatLang(appContext, coord));
        }

        if (mDroneLeashPath == null) {
            final PolylineOptions flightPath = new PolylineOptions();
            flightPath.color(DRONE_LEASH_DEFAULT_COLOR).width(
                    DroneHelper.scaleDpToPixels(DRONE_LEASH_DEFAULT_WIDTH, getResources()));
            mDroneLeashPath = getAMap().addPolyline(flightPath);
        }

        mDroneLeashPath.setPoints(pathPoints);
    }

    @Override
    public void updateMissionPath(PathSource pathSource) {
        List<LatLong> pathCoords = pathSource.getPathPoints();
        final List<LatLng> pathPoints = new ArrayList<>(pathCoords.size());
        for (LatLong coord : pathCoords) {
            pathPoints.add(DroneHelper.CoordConvert2AMapLatLang(appContext, coord));
        }

        if (missionPath == null) {
            final PolylineOptions pathOptions = new PolylineOptions();
            pathOptions.color(MISSION_PATH_DEFAULT_COLOR).width(MISSION_PATH_DEFAULT_WIDTH);
            missionPath = getAMap().addPolyline(pathOptions);
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
                pathPoints.add(DroneHelper.CoordConvert2AMapLatLang(appContext, coord));
            }
            pathOptions.addAll(pathPoints);
            polygonsPaths.add(getAMap().addPolygon(pathOptions));
        }
    }

    @Override
    public void addCameraFootprint(FootPrint footprintToBeDraw) {
        PolygonOptions pathOptions = new PolygonOptions();
        pathOptions.strokeColor(FOOTPRINT_DEFAULT_COLOR).strokeWidth(FOOTPRINT_DEFAULT_WIDTH);
        pathOptions.fillColor(FOOTPRINT_FILL_COLOR);

        for (LatLong vertex : footprintToBeDraw.getVertexInGlobalFrame()) {
            pathOptions.add(DroneHelper.CoordConvert2AMapLatLang(appContext, vertex));
        }
        getAMap().addPolygon(pathOptions);

    }

    /**
     * Save the map camera state on a preference file
     * http://stackoverflow.com/questions
     * /16697891/google-maps-android-api-v2-restoring
     * -map-state/16698624#16698624
     */
    @Override
    public void saveCameraPosition() {
        CameraPosition camera = getAMap().getCameraPosition();

        LatLong latLong = DroneHelper.AMapLatLngConvert2Coord(appContext,
                new LatLng(camera.target.latitude, camera.target.longitude));

        mAppPrefs.prefs.edit()
                .putFloat(PREF_LAT, (float) latLong.getLatitude())
                .putFloat(PREF_LNG, (float) latLong.getLongitude())
                .putFloat(PREF_BEA, camera.bearing)
                .putFloat(PREF_TILT, camera.tilt)
                .putFloat(PREF_ZOOM, camera.zoom).apply();
    }

    @Override
    public void loadCameraPosition() {
        // delayed to do load camera at this.onResume
    }

    private void loadCameraPosition_() {
        final SharedPreferences settings = mAppPrefs.prefs;

        final CameraPosition.Builder camera = new CameraPosition.Builder();
        camera.bearing(settings.getFloat(PREF_BEA, DEFAULT_BEARING));
        camera.tilt(settings.getFloat(PREF_TILT, DEFAULT_TILT));
        camera.zoom(settings.getFloat(PREF_ZOOM, DEFAULT_ZOOM_LEVEL));
        camera.target(DroneHelper.CoordConvert2AMapLatLang(appContext,
                new LatLong(settings.getFloat(PREF_LAT, DEFAULT_LATITUDE),
                        settings.getFloat(PREF_LNG, DEFAULT_LONGITUDE))
        ));

        getAMap().moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()));
    }

    @Override
    public void zoomToFit(List<LatLong> coords){
        if (!coords.isEmpty()){
            final List<LatLng> points = new ArrayList<LatLng>();
            for (LatLong corrd : coords){
                points.add(DroneHelper.CoordConvert2AMapLatLang(appContext, corrd));
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
                getAMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,width,height,100));
            }
        }
    }

    @Override
    public void zoomToFitMyLocation(final List<LatLong> coords){
        final Location myLocation = getLastLocation();
        if (myLocation != null) {
            final List<LatLong> updatedCoords = new ArrayList<LatLong>(coords);
            updatedCoords.add(DroneHelper.AMapLatLngConvert2Coord(appContext,
                    new LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
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
            Toast.makeText(appContext, R.string.drone_no_location, Toast.LENGTH_SHORT).show();
            return;
        }

        final float currentZoomLevel = getAMap().getCameraPosition().zoom;
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
                    pathOptions.add(DroneHelper.CoordConvert2AMapLatLang(appContext, vertex));
                }
                footprintPoly = getAMap().addPolygon(pathOptions);
            }else{
                List<LatLng> list = new ArrayList<LatLng>();
                for (LatLong vertex : pathPoints){
                    list.add(DroneHelper.CoordConvert2AMapLatLang(appContext, vertex));
                }
                footprintPoly.setPoints(list);
            }
        }
    }

    public VisibleMapArea getVisibleMapArea(){
        final AMap map = getAMap();
        if(map == null)
            return null;

        final VisibleRegion mapRegion = map.getProjection().getVisibleRegion();
        return new VisibleMapArea(DroneHelper.AMapLatLngConvert2Coord(appContext, mapRegion.farLeft),
                DroneHelper.AMapLatLngConvert2Coord(appContext, mapRegion.nearLeft),
                DroneHelper.AMapLatLngConvert2Coord(appContext, mapRegion.nearRight),
                DroneHelper.AMapLatLngConvert2Coord(appContext, mapRegion.farRight));
    }

    // ---------------------------------------------------------------------------------------------
    // SupportMapFragment
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        appContext = getActivity().getApplicationContext();

        mAppPrefs = new DroidPlannerPrefs(appContext);

        final Bundle args = getArguments();
        if (args != null){
            maxFlightPathSize = args.getInt(EXTRA_MAX_FLIGHT_PATH_SIZE);
        }

        MapsInitializer.sdcardDir = DirectoryPath.getAMapPath();
        try{
            MapsInitializer.initialize(getActivity().getBaseContext());
        }catch (RemoteException e){}

        getAMap();
    }

    @Override
    public void onStart(){
        super.onStart();

        setupMap(mAmap);

        LocalBroadcastManager.getInstance(appContext)
                .registerReceiver(eventReceiver, eventFilter);
    }

    @Override
    public void onResume(){
        super.onResume();

        loadCameraPosition_();
        initLocation();
    }

    @Override
    public void onPause(){
        super.onPause();

        stopLocation();
    }

    @Override
    public void onStop(){
        super.onStop();

        LocalBroadcastManager.getInstance(appContext)
                .unregisterReceiver(eventReceiver);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        aMapLocationClient.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_google_map, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.menu_download_mapbox_map:
                startActivity(new Intent(getContext(), DownloadAMapMapActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu){
        final MenuItem item = menu.findItem(R.id.menu_download_mapbox_map);
        if(item != null) {
            final boolean isEnabled = AMapPrefFragement.isAddDownloadMenuOptionEnabled(getContext());
            item.setEnabled(isEnabled);
            item.setVisible(isEnabled);
        }

    }

    public static boolean isLocationInChina(Context context){

        DroidPlannerPrefs prefs = new DroidPlannerPrefs(context);
        final SharedPreferences settings = prefs.prefs;

        double lat = settings.getFloat(PREF_LAT, DEFAULT_LATITUDE);
        double lng = settings.getFloat(PREF_LNG, DEFAULT_LONGITUDE);

        CoordinateConverter converter = new CoordinateConverter(context);
        converter.from(CoordinateConverter.CoordType.GPS);

        LatLng latLong = DroneHelper.ConvertGPS2GCJ(context, lat, lng);

        return converter.isAMapDataAvailable(latLong.latitude, latLong.longitude);
    }

}
