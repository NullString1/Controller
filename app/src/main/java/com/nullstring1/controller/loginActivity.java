package com.nullstring1.controller;

import static android.content.ContentValues.TAG;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.Callable;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class loginActivity extends AppCompatActivity {

    SharedPreferences pref;
    OkHttpClient client;
    Button pasteButton;
    Toast toast;
    ClipboardManager clipboardManager;
    EditText authTokenInput;
    ImageButton refreshButton;
    controlActivity.TaskRunner taskRunner = new controlActivity.TaskRunner();
    TextView serverStatus, controllerTV;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = this.getSharedPreferences("com.nullstring1.controller.pref", Context.MODE_PRIVATE);
        if (!pref.getString("auth_token", "").equals("")){
            Intent intent = new Intent(this, controlActivity.class);
            startActivity(intent);
        }
        setContentView(R.layout.activity_login);
        client = new OkHttpClient();
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        pasteButton = findViewById(R.id.pasteButton);
        authTokenInput = findViewById(R.id.authTokenInput);
        serverStatus = findViewById(R.id.LserverStatus);
        refreshButton = findViewById(R.id.LrefreshButton);
        controllerTV = findViewById(R.id.controllerTV2);
        refreshButton.setOnClickListener((l) -> {
            taskRunner.executeAsync(new checkSConn(), result -> {
                serverStatus.setText(result ? R.string.connected : R.string.not_connected);
            });
        });

        controllerTV.setOnClickListener((l) -> {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Open controller setup page?");
            builder.setMessage("Have you connected to the controller network?");

            // add the buttons
            builder.setPositiveButton("Yes", (DialogInterface.OnClickListener) (dialogInterface, i) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://controller.setup"));
                startActivity(browserIntent);
            });
            builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialogInterface, i) ->
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

            // create and show the alert dialog
            AlertDialog dialog = builder.create();
            dialog.show();

        });
        pasteButton.setOnClickListener((l) -> {
            if (clipboardManager.hasPrimaryClip() && (clipboardManager.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) || clipboardManager.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))){
                ClipData.Item item = clipboardManager.getPrimaryClip().getItemAt(0);
                authTokenInput.setText(item.getText().toString());
            }
            Log.d(TAG, "onCreate: " + clipboardManager.getPrimaryClipDescription().getMimeType(0));
        });
        taskRunner.executeAsync(new checkSConn(), result -> {
            serverStatus.setText(result ? R.string.connected : R.string.not_connected);
        });
    }

    public void showToast(Context context, String message, int duration){
        if (toast != null){
            toast.cancel();
        }
        toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    public class checkSConn implements Callable<Boolean> {
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

    public void login(View view){
        String token = authTokenInput.getText().toString();

        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://blynk.cloud/external/api/isHardwareConnected")).newBuilder();
        urlBuilder.addQueryParameter("token", token);
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response){
                if (response.isSuccessful()){
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString("auth_token", token);
                    editor.apply();
                    Intent intent = new Intent(getApplicationContext(), controlActivity.class);
                    startActivity(intent);
                } else{
                    loginActivity.this.runOnUiThread(() -> {
                        Toast toast = Toast.makeText(getApplicationContext(), "Auth Token Incorrect", Toast.LENGTH_SHORT);
                        toast.show();
                    });
                }
            }
        });

    }
}