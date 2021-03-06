package com.sequencing.weather.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.JsonSyntaxException;
import com.sequencing.video.customview.CVideoView;
import com.sequencing.video.helper.AppUIHelper;
import com.sequencing.weather.R;
import com.sequencing.weather.entity.LocationEntity;
import com.sequencing.weather.exceptions.WUndergroundException;
import com.sequencing.weather.helper.ConnectionHelper;
import com.sequencing.weather.helper.HttpHelper;
import com.sequencing.weather.helper.InstancesContainer;
import com.sequencing.weather.helper.MediaTypeUtil;
import com.sequencing.weather.helper.VideoGeneratorHelper;
import com.sequencing.weather.helper.WeatherHelper;
import com.sequencing.weather.logging.events.Interaction;
import com.sequencing.weather.logging.events.Request;
import com.sequencing.weather.logging.events.UpdateInteraction;
import com.sequencing.weather.logging.listeners.InteractionListener;
import com.sequencing.weather.logging.listeners.RequestEventListener;
import com.sequencing.weather.preference.GeneticFilePreference;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class LocationActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static String location = null;
    private double latitude;
    private double longitude;
    private Button btnClear;
    private EditText etLocation;
    private ListView lvCities;

    private GoogleApiClient mGoogleApiClient;

    private CVideoView videoView;

    public static final String TAG = "LocationActivity";
    private static int countRetryAttempts;

    @Inject
    InteractionListener interactionListener;

    @Inject
    RequestEventListener requestEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        RTPApplication application = RTPApplication.create(getApplicationContext());
        application.getDaggerComponent().inject(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_location);

        videoView = (CVideoView) findViewById(R.id.video_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        btnClear = (Button) findViewById(R.id.btnClear);
        etLocation = (EditText) findViewById(R.id.etLocation);
        etLocation.addTextChangedListener(new CityChangeListener());
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etLocation.setText("");
            }
        });

        lvCities = (ListView) findViewById(R.id.lvCities);
        lvCities.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etLocation.getWindowToken(), 0);
                return false;
            }
        });

        etLocation.setSelected(false);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (location != null) {
            etLocation.setText(location);
        }
        playVideo();

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (SP.getString("location", null) != null)
            etLocation.setText(SP.getString("location", null));

    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
        if(!requestEventListener.isRegistered()){
            EventBus.getDefault().register(requestEventListener);
            requestEventListener.setIsRequestListenerRegistered(true);
        }

        if(!interactionListener.isRegistered()){
            EventBus.getDefault().register(interactionListener);
            interactionListener.setIsInteractionListenerRegistered(true);
        }
    }

    protected void onStop() {
        stopVideo();
        mGoogleApiClient.disconnect();
        EventBus.getDefault().unregister(interactionListener);
        requestEventListener.setIsRequestListenerRegistered(false);
        EventBus.getDefault().unregister(requestEventListener);
        interactionListener.setIsInteractionListenerRegistered(false);
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void init() {
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
                videoView.setOnCorrectVideoDimensions(getCorrectVideoDimensions());
                mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                        videoView.onVideoSizeChanged(mp);
                    }
                });
            }
        });
    }

    private void playVideo() {
        int orientation = getResources().getConfiguration().orientation;
        if(orientation == Configuration.ORIENTATION_PORTRAIT){
            videoView.setAlignment(CVideoView.ALIGN_WIDTH);
        } else {
            videoView.setAlignment(CVideoView.ALIGN_NONE);
        }
        videoView.setVisibility(View.VISIBLE);
        String videoName = VideoGeneratorHelper.getVideo(this);
        String videoNameWithoutExtension = videoName.split("\\.")[0];
        int raw_id = getResources().getIdentifier(videoNameWithoutExtension, "raw", getPackageName());
        String path = "android.resource://" + getPackageName() + "/" + raw_id;
        videoView.setVideoURI(Uri.parse(path));
        videoView.start();
    }

    private void stopVideo() {
        videoView.stopPlayback();
        videoView.setVisibility(View.GONE);
    }

    /**
     * Adjust video layout according to video dimensions
     */
    private CVideoView.OnCorrectVideoDimensions getCorrectVideoDimensions() {
        return new CVideoView.OnCorrectVideoDimensions() {
            @Override
            public void correctDimensions(int width, int height) {
                AppUIHelper.resizeLayout(videoView, height, -1, 300, null);
            }
        };
    }

    public String autoDetectLocation(){
        String detectedLocation = getCurrentLocation(this, latitude, longitude);
        if (detectedLocation == null) {
            Toast.makeText(this, "Failure to auto detect location, try to detect via Map", Toast.LENGTH_LONG).show();
            return null;
        }
        etLocation.setText(detectedLocation);
        Toast.makeText(getBaseContext(), "Location has been changed to " + detectedLocation, Toast.LENGTH_SHORT).show();
        return  detectedLocation;
    }

    public String getCurrentLocation(Context context, double latitude, double longitude) {
        Geocoder gcd = new Geocoder(context, Locale.US);
        List<Address> addresses = null;

        try {
            addresses = gcd.getFromLocation(latitude, longitude, 1);
        } catch (IOException e) {
            Log.w(TAG, "Unable to get user location");
            return null;
        }

        if (addresses == null || addresses.size() == 0)
            return null;

        String result = addresses.get(0).getAdminArea() != null ? addresses.get(0).getLocality() + ", " + addresses.get(0).getAdminArea() :
                addresses.get(0).getLocality() + ", " + addresses.get(0).getCountryName();

//        logEventChangeInteraction(context, result, latitude, longitude);

        updateLocation(context, result, latitude, longitude, null);

        return result;
    }

    private void logEventChangeInteraction(Context context, String location, double latitude, double longitude) {
        if(!requestEventListener.isRegistered()){
            EventBus.getDefault().register(requestEventListener);
            requestEventListener.setIsRequestListenerRegistered(true);
        }

        if(!interactionListener.isRegistered()){
            EventBus.getDefault().register(interactionListener);
            interactionListener.setIsInteractionListenerRegistered(true);
        }

        endLastInteraction(context);

        Interaction interaction = new Interaction();
        interaction.lat = latitude;
        interaction.lng = longitude;
        interaction.media = MediaTypeUtil.getNetworkClass(this);
        interaction.place = location;
        interaction.ts = System.currentTimeMillis();
        EventBus.getDefault().post(interaction);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong("timeStampInteraction", interaction.ts).commit();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            latitude = mLastLocation.getLatitude();
            longitude = mLastLocation.getLongitude();
        }

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if(SP.getBoolean("should_auto_detect", true)) {
            autoDetectLocation();
        } else {
            SP.edit().putBoolean("should_auto_detect", true).commit();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public static void updateLocation(Context context, String location, double latitude, double longitude, @Nullable String cityId) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();
        try {
            cityId = cityId != null ? cityId : WeatherHelper.getCityIdByGeoData(latitude, longitude).get("code");
        } catch (WUndergroundException | JsonSyntaxException e) {
            Toast.makeText(context, "Unable to get city location", Toast.LENGTH_SHORT).show();
            return;
        }

        MainActivity.chosenLocation = location;
        editor.putString("location", location);
        editor.putString("latitude", String.valueOf(latitude));
        editor.putString("longitude", String.valueOf(longitude));
        editor.putBoolean("should_refresh", true);
        editor.putString("city_id", cityId);
        editor.commit();

        LocationActivity.location = location;

        sendLocationToServer(cityId);

        Log.i(TAG, "Location has been changed to " + location + ". With latitude " + latitude +
                " and longitude " + longitude);

        if (settings.getString("genetic_data_file", null) == null) {
            Toast.makeText(context, "Please select genetic data file", Toast.LENGTH_LONG).show();

            GeneticFilePreference.runFileSelector(context, false);
        }
    }

    public void endLastInteraction(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        long currentTimestampInteraction = settings.getLong("timeStampInteraction", 0);
        int sessionDuration = (int) (System.currentTimeMillis() - currentTimestampInteraction);
        if(currentTimestampInteraction != 0){
            UpdateInteraction updateInteraction = new UpdateInteraction();
            updateInteraction.ts = currentTimestampInteraction;
            updateInteraction.duration = sessionDuration;
            EventBus.getDefault().post(updateInteraction);
        }
    }

    private static void sendRequestEvent(int requestTime, int background, int service, long interactionTimestamp, long failureTimestamp, String failureReason) {
        Request request = new Request();
        request.requestTime = requestTime;
        request.requestBackground = background;
        request.requestService = service;
        request.failureTimestamp = failureTimestamp;
        request.failureReason = failureReason;
        request.interactionTimestamp = interactionTimestamp;
        EventBus.getDefault().post(request);
    }

    private String[] getCityNames(List<LocationEntity.City> cities) {
        String[] cityNames = new String[cities.size()];

        for (int i = 0; i < cities.size(); i++)
            cityNames[i] = cities.get(i).getName();

        return cityNames;
    }

    private class CityChangeListener implements TextWatcher {
        private static final long DELAY = 500;
        private boolean firstLoad = true;

        private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, int start, int before, int count) {
            if (s.equals(location) || firstLoad) {
                firstLoad = false;
                return;
            }

            if (!ConnectionHelper.isConnectionAvailable(getApplicationContext())) {
                Toast.makeText(getApplicationContext(), "Check your internet connection", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Text has been changed");

            service.schedule(new Runnable() {

                @Override
                public void run() {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);

                    final List<LocationEntity.City> cities = WeatherHelper.getCities(s.toString());

                    if (cities.size() == 0)
                        return;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                    changeLocationFromList(cities);
                        }
                    });
                }
            }, DELAY, TimeUnit.MILLISECONDS);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }

        private void changeLocationFromList(final List<LocationEntity.City> cities) {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getBaseContext(),
                    R.layout.list_item, getCityNames(cities));

            lvCities.setAdapter(adapter);
            lvCities.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String cityName = cities.get(position).getName();
                    logEventChangeInteraction(getBaseContext(), cityName, cities.get(position).getLat(), cities.get(position).getLon());
                    updateLocation(getBaseContext(), cityName, cities.get(position).getLat(), cities.get(position).getLon(), cities.get(position).getL());
                    etLocation.setText(cityName);

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etLocation.getWindowToken(), 0);

                    Toast.makeText(getBaseContext(), "Location has been changed to " + cityName, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static void sendLocationToServer(final String location) {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

        Runnable run = new Runnable() {
            @Override
            public void run() {
                String url = "https://weathermyway.rocks/ExternalSettings/SaveLocation";
                Map<String, String> params = new HashMap<>(2);
                params.put("city", location);
                params.put("token", InstancesContainer.getoAuth2Client().getToken().getAccessToken());
                String response = HttpHelper.doPost(url, null, params);
            }
        };

        service.submit(run);
        Log.i(TAG, "Location has been sent to server");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_location, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_continue) {
            Intent intent = new Intent(this, MainActivity_.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

        @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
