//MainActivity.java
package com.example.secureworldlocation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private LocationManager locationManager;
    private Location currentLocation;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sendLocationRunnable;

    private TextView tvLocation;
    private Button btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLocation = findViewById(R.id.tvLocation);
        btnRefresh = findViewById(R.id.btnRefresh);

        btnRefresh.setEnabled(false);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Ask for location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }

        // Manual refresh button
        btnRefresh.setOnClickListener(v -> {
            if (currentLocation != null) {
                double lat = currentLocation.getLatitude();
                double lon = currentLocation.getLongitude();

                // Show in UI
                runOnUiThread(() -> {
                    tvLocation.setText(String.format(Locale.getDefault(),
                            "Latitude: %.6f\nLongitude: %.6f", lat, lon));
                });

                // Send to API
                Log.d("ManualRefresh", "User tapped refresh button → Sending location now");
                sendLocationToAPI(lat, lon);
            } else {
                Toast.makeText(MainActivity.this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show();
                tvLocation.setText("Location permission denied");
            }
        }
    }

    // Start location updates and schedule 5-second API sending
    private void startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, locationListener);

            // Repeat task every 5 seconds
            sendLocationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (currentLocation != null) {
                        double lat = currentLocation.getLatitude();
                        double lon = currentLocation.getLongitude();

                        // Update UI
                        runOnUiThread(() -> {
                            tvLocation.setText(String.format(Locale.getDefault(),
                                    "Latitude: %.6f\nLongitude: %.6f", lat, lon));
                            btnRefresh.setEnabled(true);
                        });

                        // Log and send location
                        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                        Log.d("AutoSend", "Sending location every 5 sec → " +
                                "Lat=" + lat + ", Lon=" + lon + " at " + timestamp);

                        sendLocationToAPI(lat, lon);
                    } else {
                        Log.d("AutoSend", "No GPS fix yet, retrying in 5 sec");
                        runOnUiThread(() -> {
                            tvLocation.setText("Waiting for GPS location...");
                            btnRefresh.setEnabled(false);
                        });
                    }

                    handler.postDelayed(this, 5000);  // Schedule again
                }
            };

            handler.post(sendLocationRunnable);  // Start immediately

        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    // Updates when GPS detects new location
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            currentLocation = location;
            Log.d("GPSUpdate", "New location from GPS → Lat=" +
                    location.getLatitude() + ", Lon=" + location.getLongitude());
            runOnUiThread(() -> btnRefresh.setEnabled(true));
        }
    };

    // Send location to Secure World API
    private void sendLocationToAPI(double latitude, double longitude) {
        new Thread(() -> {
            try {
                URL url = new URL("https://adsapi.secureworldme.com/api/bike/CreateBikeLocation");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                // Build JSON payload
                JSONObject json = new JSONObject();
                json.put("BikeId", "BIKEODC001");
                json.put("device_code", "DEVODC123");
                json.put("Latitude", Double.toString(latitude));
                json.put("Longitude", Double.toString(longitude));
                json.put("URL", "");
                json.put("filename", "");
                json.put("total_duration", "");

                // Format time
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getDefault());
                String isoDate = sdf.format(new Date());
                String formattedDate = isoDate.substring(0, isoDate.length()-2) + ":" + isoDate.substring(isoDate.length()-2);
                json.put("CreatedDate", formattedDate);

                Log.d("APIRequest", "POST → " + json.toString());

                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    Log.d("APIResponse", "✅ Location sent successfully. Code: " + responseCode);
                } else {
                    Log.e("APIResponse", "❌ Failed to send location. Code: " + responseCode);
                }
                conn.disconnect();

            } catch (Exception e) {
                Log.e("APIError", "Error sending location", e);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        handler.removeCallbacks(sendLocationRunnable);
    }
}
