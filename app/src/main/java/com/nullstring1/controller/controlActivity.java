package com.nullstring1.controller;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class controlActivity extends AppCompatActivity {

    SharedPreferences pref;
    SharedPreferences.Editor editor;
    OkHttpClient client;
    SwitchMaterial hSwitch, wSwitch;
    TextView statusLabel, outsideTemp, serverStatus, controllerTV, insideTemp;
    ImageButton refreshButton;
    ImageView weatherIcon;
    FusedLocationProviderClient flpC;
    ConstraintLayout weatherCL;
    TaskRunner taskRunner = new TaskRunner();
    Toast toast;
    boolean v0, v1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        flpC = LocationServices.getFusedLocationProviderClient(this);
        pref = this.getSharedPreferences("com.nullstring1.controller.pref", MODE_PRIVATE);
        editor = pref.edit();
        client = new OkHttpClient();
        hSwitch = findViewById(R.id.heatingSwitch);
        wSwitch = findViewById(R.id.waterSwitch);
        insideTemp = findViewById(R.id.insideTemp);
        refreshButton = findViewById(R.id.refreshButton);
        statusLabel = findViewById(R.id.controllerStatusLabel);
        weatherIcon = findViewById(R.id.weatherIcon);
        outsideTemp = findViewById(R.id.outsideTemp);
        weatherCL = findViewById(R.id.weatherCL);
        serverStatus = findViewById(R.id.serverStatus);
        controllerTV = findViewById(R.id.controllerTV);
        insideTemp.setOnClickListener((l) -> {
            if(insideTemp.getText().toString().equals(getString(R.string.n_a))) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = getLayoutInflater();
                View v = inflater.inflate(R.layout.alertdialog, null);
                builder.setView(v)
                        .setPositiveButton("Yes", (dialogInterface, i) -> {
                            editor.putString("sensAuthToken", ((EditText) v.findViewById(R.id.sensToken)).getText().toString());
                            editor.apply();
                            getState();
                        })
                        .setNegativeButton("No", (dialogInterface, i) -> dialogInterface.cancel());
                builder.show();
            }
        });
        controllerTV.setOnClickListener((l) -> {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Open controller setup page?");
            builder.setMessage("Have you connected to the controller network?");

            // add the buttons
            builder.setPositiveButton("Yes", (dialogInterface, i) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://controller.setup"));
                startActivity(browserIntent);
            });
            builder.setNegativeButton("No", (dialogInterface, i) ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

            // create and show the alert dialog
            AlertDialog dialog = builder.create();
            dialog.show();

        });
        refreshButton.setOnClickListener((l) -> checkConn());
        hSwitch.setActivated(true);
        wSwitch.setActivated(true);
        hSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(hSwitch.isActivated()) {
                updatePin("V0", isChecked);
                setButtonColour(hSwitch, isChecked ? "#FC9B4C" : "#8B4EFC");
            } else{
                hSwitch.setChecked(!isChecked);
                showToast(getApplicationContext(), "No internet connection", Toast.LENGTH_SHORT);
            }
        });
        wSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(wSwitch.isActivated()) {
                updatePin("V1", isChecked);
                setButtonColour(wSwitch, isChecked ? "#FC9B4C" : "#8B4EFC");
            } else{
                wSwitch.setChecked(!isChecked);
                showToast(getApplicationContext(), "No internet connection", Toast.LENGTH_SHORT);
            }
        });
        checkConn();
    }

    public void updatePin(String pin, boolean state){
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://blynk.cloud/external/api/update")).newBuilder();
        urlBuilder.addQueryParameter("token", pref.getString("auth_token", ""));
        urlBuilder.addQueryParameter(pin, state ? "1":"0");
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()){
                    controlActivity.this.runOnUiThread(() -> {
                        try {
                            showToast(getApplicationContext(), Objects.requireNonNull(response.body()).string(), Toast.LENGTH_SHORT);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    });
                }
            }
        });
    }
    public void showToast(Context context, String message, int duration){
        if (toast != null){
            toast.cancel();
        }
        toast = Toast.makeText(context, message, duration);
        toast.show();
    }
    public void checkConn(){
        taskRunner.executeAsync(new checkSConn(), result -> {
            if (result) {
                getControllerStatus();
                getState();
                getLastLocation();
                //hSwitch.setEnabled(true);
                hSwitch.setActivated(true);
                //wSwitch.setEnabled(true);
                wSwitch.setActivated(true);
                serverStatus.setText(R.string.connected);
            } else {
                //hSwitch.setEnabled(false);
                hSwitch.setActivated(false);
                //wSwitch.setEnabled(false);
                wSwitch.setActivated(false);
                serverStatus.setText(R.string.not_connected);
            }
        });
    }
    public class checkSConn implements Callable<Boolean>{
        @Override
        public Boolean call() {
            try {
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress("lon1.blynk.cloud", 443), 1000);
                sock.close();
                Log.d(TAG, "checkServerConn: Connected to lon1.blynk.cloud:80");
                return true;
            } catch (IOException e) {
                if (e.getClass() == SocketTimeoutException.class){
                    Log.d(TAG, "checkServerConn: Could not reach lon1.blynk.cloud:80, due to timeout");
                    runOnUiThread(() -> showToast(getApplicationContext(), "Timeout. Cannot reach server. Check your internet connection", Toast.LENGTH_LONG));
                } else {
                    Log.d(TAG, "checkServerConn: Could not reach lon1.blynk.cloud:80, due to other error");
                    e.printStackTrace();
                    runOnUiThread(() -> showToast(getApplicationContext(), "Cannot reach server. Check your internet connection", Toast.LENGTH_LONG));
                }

                return false;
            }
        }
    }

    public void updateWeather(double lat, double lon){
        Log.d(TAG, "updateWeather: Called");
        Log.d(TAG, "updateWeather: lat:" + lat + " long: " + lon);
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://api.openweathermap.org/data/2.5/weather")).newBuilder();
        urlBuilder.addQueryParameter("appid", "dcf37bca09dd97e159163e43d83718c3");
        urlBuilder.addQueryParameter("lat", String.valueOf(lat));
        urlBuilder.addQueryParameter("lon", String.valueOf(lon));
        urlBuilder.addQueryParameter("units", "metric");
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "onFailure: ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(Objects.requireNonNull(response.body()).string());
                    String temperature = json.getJSONObject("main").getInt("temp") + "°C";
                    String iconId = json.getJSONArray("weather").getJSONObject(0).getString("icon");
                    String windSpeed = json.getJSONObject("wind").getString("speed") + "m/s";
                    Log.d(TAG, "onResponse: Temperature: " + temperature + " iconId: " + iconId + " windSpeed: " + windSpeed);
                    weatherIcon.setImageResource(getResources().getIdentifier("_" + iconId, "drawable", getPackageName()));
                    outsideTemp.setText(temperature);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    public void setButtonColour(SwitchMaterial button, String colour){
        Drawable buttonDrawable = button.getBackground();
        buttonDrawable = DrawableCompat.wrap(buttonDrawable);
        DrawableCompat.setTint(buttonDrawable, Color.parseColor(colour));
        button.setBackground(buttonDrawable);
    }

    public void getControllerStatus(){
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://blynk.cloud/external/api/isHardwareConnected")).newBuilder();
        urlBuilder.addQueryParameter("token", pref.getString("auth_token", ""));
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String resStr = Objects.requireNonNull(response.body()).string();
                controlActivity.this.runOnUiThread(() -> {
                    Log.d(TAG, "onResponse: " + resStr);
                    statusLabel.setText(getString(resStr.equals("true") ? R.string.online:R.string.offline));
                    //statusLabel.setText("das");
                });
            }
        });
    }
    public void getState() {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://lon1.blynk.cloud/external/api/get")).newBuilder();
        urlBuilder.addQueryParameter("token", pref.getString("auth_token", ""));
        urlBuilder.addQueryParameter("v0", null);
        urlBuilder.addQueryParameter("v1", null);
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull final Response response) throws IOException {
                String resStr = Objects.requireNonNull(response.body()).string();
                Log.d(TAG, resStr);
                try {
                    JSONObject json = new JSONObject(resStr);
                    v0 = json.getInt("v0") == 1;
                    v1 = json.getInt("v1") == 1;
                    editor.putBoolean("v0", v0);
                    editor.putBoolean("v1", v1);
                    //Log.d(TAG, "onResponse: " + v0 + v1);
                    controlActivity.this.runOnUiThread(() -> {
                        //Log.d(TAG, "onResponse: " + v0 + v1);
                        setButtonColour(hSwitch, v0 ? "#FC9B4C":"#8B4EFC");
                        hSwitch.setChecked(v0);
                        setButtonColour(wSwitch, v1 ? "#FC9B4C":"#8B4EFC");
                        wSwitch.setChecked(v1);
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        if (!pref.getString("sensAuthToken", "").equals("")) {
            urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://lon1.blynk.cloud/external/api/get")).newBuilder();
            urlBuilder.addQueryParameter("token", pref.getString("sensAuthToken", ""));
            urlBuilder.addQueryParameter("v0", null);
            url = urlBuilder.build().toString();
            request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull final Response response) throws IOException {
                    String resStr = Objects.requireNonNull(response.body()).string();
                    if (resStr.length() > 5){
                        controlActivity.this.runOnUiThread(() -> insideTemp.setText(R.string.n_a));
                        return;
                    }
                    Log.d(TAG, resStr);
                    String temperature = resStr + "°C";
                    Log.d(TAG, "temperature: " + temperature);
                    controlActivity.this.runOnUiThread(() -> insideTemp.setText(temperature));
                }
            });
        }
    }

    private final LocationCallback mLocationCallback = new LocationCallback() {

        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location mLastLocation = locationResult.getLastLocation();
            updateWeather(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        }
    };

    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        //Log.d(TAG, "getLastLocation: Run");
        if (checkPermissions()) {
            Log.d(TAG, "getLastLocation: Perms correct");
            if (isLocationEnabled()) {
                Log.d(TAG, "getLastLocation: Location enabled");
                flpC.getLastLocation().addOnCompleteListener(task -> {
                    Location location = task.getResult();
                    if (location == null) {
                        Log.d(TAG, "getLastLocation: Location null");
                        requestNewLocationData();
                    } else {
                        Log.d(TAG, "getLastLocation: got location");
                        updateWeather(location.getLatitude(), location.getLongitude());
                    }
                });
            } else {
                showToast(this, "Please turn on" + " your location...", Toast.LENGTH_LONG);
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        } else {
            // if permissions aren't available,
            // request for permissions
            Log.d(TAG, "getLastLocation: Perms wrong");
            requestPermissions();
        }
    }
    @SuppressLint("MissingPermission")
    private void requestNewLocationData() {
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
        mLocationRequest.setInterval(5);
        mLocationRequest.setFastestInterval(0);
        mLocationRequest.setNumUpdates(1);

        flpC = LocationServices.getFusedLocationProviderClient(this);
        flpC.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION}, 44);
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 44) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkConn();
    }

    public static class TaskRunner {
        private final Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
        private final Handler handler = new Handler(Looper.getMainLooper());

        public interface Callback<R> {
            void onComplete(R result);
        }

        public <A> void executeAsync(Callable<A> callable, Callback<A> callback) {
            executor.execute(() -> {
                final A result;
                try {
                    result = callable.call();
                    handler.post(() -> callback.onComplete(result));
                } catch (Exception e) {
                    e.printStackTrace();
                }

            });
        }
    }
}
