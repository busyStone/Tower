package org.droidplanner.android.maps.providers.amap_map.tiles.web.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import org.droidplanner.android.maps.DPMap;
import org.droidplanner.android.maps.providers.google_map.tiles.mapbox.offline.MapDownloader;
import org.droidplanner.android.utils.NetworkUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import timber.log.Timber;

/**
 * Created by clover on 15/11/4.
 */
public class AMapDownloader extends MapDownloader {

    public AMapDownloader(Context context) {
        super(context);
    }

    @Override
    public void beginDownloadingMapID(final String mapId, final String accessToken, DPMap.VisibleMapArea mapRegion, int
            minimumZ, int maximumZ, boolean includeMetadata, boolean includeMarkers){

        MBXOfflineMapDownloaderState state = getState();
        if (state != MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateAvailable) {
            Timber.w("state doesn't equal MBXOfflineMapDownloaderStateAvailable so return.  state = " + state);
            return;
        }

        // Start a download job to retrieve all the resources needed for using the specified map offline
        setState(MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateRunning);
        notifyDelegateOfStateChange();

        final ArrayList<String> urls = new ArrayList<String>();

        // Loop through the zoom levels and lat/lon bounds to generate a list of urls which should be included in the offline map
        //
        double minLat = Math.min(
                Math.min(mapRegion.farLeft.getLatitude(), mapRegion.nearLeft.getLatitude()),
                Math.min(mapRegion.farRight.getLatitude(), mapRegion.nearRight.getLatitude()));
        double maxLat = Math.max(
                Math.max(mapRegion.farLeft.getLatitude(), mapRegion.nearLeft.getLatitude()),
                Math.max(mapRegion.farRight.getLatitude(), mapRegion.nearRight.getLatitude()));

        double minLon = Math.min(
                Math.min(mapRegion.farLeft.getLongitude(), mapRegion.nearLeft.getLongitude()),
                Math.min(mapRegion.farRight.getLongitude(), mapRegion.nearRight.getLongitude()));
        double maxLon = Math.max(
                Math.max(mapRegion.farLeft.getLongitude(), mapRegion.nearLeft.getLongitude()),
                Math.max(mapRegion.farRight.getLongitude(), mapRegion.nearRight.getLongitude()));

        int minX;
        int maxX;
        int minY;
        int maxY;
        int tilesPerSide;

        Timber.d("Generating urls for tiles from zoom " + minimumZ + " to zoom " + maximumZ);

        for (int zoom = minimumZ; zoom <= maximumZ; zoom++) {
            tilesPerSide = Double.valueOf(Math.pow(2.0, zoom)).intValue();
            minX = Double.valueOf(Math.floor(((minLon + 180.0) / 360.0) * tilesPerSide)).intValue();
            maxX = Double.valueOf(Math.floor(((maxLon + 180.0) / 360.0) * tilesPerSide)).intValue();
            minY = Double.valueOf(Math.floor((1.0 - (Math.log(Math.tan(maxLat * Math.PI / 180.0) + 1.0 / Math.cos(maxLat * Math.PI / 180.0)) / Math.PI)) / 2.0 * tilesPerSide)).intValue();
            maxY = Double.valueOf(Math.floor((1.0 - (Math.log(Math.tan(minLat * Math.PI / 180.0) + 1.0 / Math.cos(minLat * Math.PI / 180.0)) / Math.PI)) / 2.0 * tilesPerSide)).intValue();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    urls.add(getMapURLParam(x,y,zoom));
                }
            }
        }

        Timber.d(urls.size() + " urls generated.");

        startDownloadProcess(mapId, urls);
    }

    @Override
    public void startDownloading(final String mapId) {

        // Get the actual URLs
        ArrayList<String> urls = sqliteReadArrayOfOfflineMapURLsToBeDownloadLimit(mapId, -1);
        this.totalFilesExpectedToWrite.set(urls.size());
        this.totalFilesWritten.set(0);

        notifyDelegateOfInitialCount(totalFilesExpectedToWrite.get());

        Timber.d(String.format(Locale.US, "number of urls to download = %d", urls.size()));
        if (this.totalFilesExpectedToWrite.get() == 0) {
            finishUpDownloadProcess();
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            Timber.e("Network is not available.");
            notifyDelegateOfNetworkConnectivityError(new IllegalStateException("Network is not available"));
            return;
        }

        final CountDownLatch downloadsTracker = new CountDownLatch(this.totalFilesExpectedToWrite.get());
        for (final String url : urls) {
            downloadsScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection conn = null;
                    try {
                        // satellite
                        conn = NetworkUtils.getHttpURLConnection(new URL(getMapSatelliteTileURL(url)));
                        Timber.d("URL to download = " + conn.getURL().toString());

                        byte[] satellite = executeDownload(conn);

                        // norm
                        conn = NetworkUtils.getHttpURLConnection(new URL(getMapNormalTileURL(url)));
                        Timber.d("URL to download = " + conn.getURL().toString());

                        byte[] normal = executeDownload(conn);

                        // draw overlay
                        if (normal.length > 0 && satellite.length > 0) {
                            Bitmap bSatellite = BitmapFactory.decodeByteArray(satellite, 0, satellite.length);
                            Bitmap bNormal = BitmapFactory.decodeByteArray(normal, 0, normal.length);

                            Bitmap newb = Bitmap.createBitmap(bSatellite.getWidth(),
                                    bSatellite.getHeight(), Bitmap.Config.ARGB_8888);
                            Canvas cv = new Canvas(newb);
                            cv.drawBitmap(bSatellite, 0, 0, null);
                            cv.drawBitmap(bNormal, 0, 0, null);
                            cv.save(Canvas.ALL_SAVE_FLAG);
                            cv.restore();

                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            newb.compress(Bitmap.CompressFormat.WEBP, 100, stream);
                            byte[] byteArray = stream.toByteArray();

                            sqliteSaveDownloadedData(mapId, byteArray, url);
                        }
                    } catch (IOException e) {
                        Timber.e(e, "Error occurred while retrieving map data.");
                    } finally {
                        downloadsTracker.countDown();

                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                }
            });
        }

        downloadsScheduler.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadsTracker.await();
                } catch (InterruptedException e) {
                    Timber.e(e, "Error while waiting for downloads to complete.");
                } finally {
                    finishUpDownloadProcess();
                }
            }
        });
    }

    private byte[] executeDownload(HttpURLConnection conn) throws IOException{

        byte[] data = null;

        conn.setConnectTimeout(60000);
        conn.connect();
        int rc = conn.getResponseCode();
        if (rc != HttpURLConnection.HTTP_OK) {
            String msg = String.format(Locale.US, "HTTP Error connection.  Response Code = %d for url = %s", rc, conn.getURL().toString());
            Timber.w(msg);
            notifyDelegateOfHTTPStatusError(rc, conn.getURL().toString());
            throw new IOException(msg);
        }

        ByteArrayOutputStream bais = new ByteArrayOutputStream();
        InputStream is = null;
        try {
            is = conn.getInputStream();
            // Read 4K at a time
            byte[] byteChunk = new byte[4096];
            int n;

            while ((n = is.read(byteChunk)) > 0) {
                bais.write(byteChunk, 0, n);
            }

            data = bais.toByteArray();
        } catch (IOException e) {
            Timber.e(e, String.format(Locale.US, "Failed while reading bytes from %s: %s", conn
                    .getURL().toString(), e.getMessage()));
        } finally {
            if (is != null) {
                is.close();
            }
            conn.disconnect();

            return data;
        }
    }

    private String getMapSatelliteTileURL(String param){
        return String.format(Locale.US,
                "http://webst0%d.is.autonavi.com/appmaptile?%s&style=6",
                (int) (Math.random() * 4 + 1), param);
    }

    private String getMapNormalTileURL(String param){
        return String.format(Locale.US,
                "http://webst0%d.is.autonavi.com/appmaptile?%s&style=8",
                (int) (Math.random() * 4 + 1), param);
    }

    public static String getMapURLParam(int x, int y, int zoom){
        return String.format(Locale.US, "x=%d&y=%d&z=%d", x, y, zoom);
    }
}
