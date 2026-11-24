package com.washlog.washlog2;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginPhone;
    private View dotPin1, dotPin2, dotPin3, dotPin4;
    private TextView tvPinStatus;
    private Button btnLogin;
    private ProgressBar pbLogin;

    private String pinInput = "";
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etLoginPhone = findViewById(R.id.etLoginPhone);
        dotPin1 = findViewById(R.id.dotPin1);
        dotPin2 = findViewById(R.id.dotPin2);
        dotPin3 = findViewById(R.id.dotPin3);
        dotPin4 = findViewById(R.id.dotPin4);
        tvPinStatus = findViewById(R.id.tvPinStatus);
        btnLogin = findViewById(R.id.btnLogin);
        pbLogin = findViewById(R.id.pbLogin);
        TextView tvGoToRegister = findViewById(R.id.tvGoToRegister);

        String prefilledPhone = getIntent().getStringExtra("extra_phone");
        if (prefilledPhone != null && !prefilledPhone.isEmpty()) {
            etLoginPhone.setText(prefilledPhone);
        }

        setupKeypad();

        btnLogin.setOnClickListener(v -> prosesLogin());

        tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void setupKeypad() {
        int[] keyIds = {
                R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
                R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9
        };

        for (int id : keyIds) {
            TextView keyView = findViewById(id);
            keyView.setOnClickListener(v -> {
                if (pinInput.length() < 4) {
                    pinInput += keyView.getText().toString();
                    updatePinDots(null);
                }
            });
        }

        TextView btnBackspace = findViewById(R.id.btnKeyBackspace);
        btnBackspace.setOnClickListener(v -> {
            if (!pinInput.isEmpty()) {
                pinInput = pinInput.substring(0, pinInput.length() - 1);
                updatePinDots(null);
            }
        });
    }

    private void updatePinDots(String errorMessage) {
        View[] dots = {dotPin1, dotPin2, dotPin3, dotPin4};
        for (int i = 0; i < 4; i++) {
            dots[i].setBackgroundResource(i < pinInput.length()
                    ? R.drawable.bg_dot_filled
                    : R.drawable.bg_dot_empty);
        }

        if (errorMessage != null) {
            tvPinStatus.setText("⚠ " + errorMessage);
            tvPinStatus.setTextColor(Color.parseColor("#DC2626"));
        } else {
            String hintText = (pinInput.length() == 3)
                    ? "Masukkan 1 digit terakhir"
                    : "Masukkan 4-digit PIN kasir";
            tvPinStatus.setText("ⓘ  " + hintText);
            tvPinStatus.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
        }
    }

    private String normalizePhone(String raw) {
        String clean = raw.replaceAll("[^0-9]", "");
        if (clean.startsWith("62")) {
            clean = "0" + clean.substring(2);
        } else if (clean.startsWith("8")) {
            clean = "0" + clean;
        }
        return clean;
    }

    private String getStringOrDefault(JsonObject obj, String key, String defaultVal) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultVal;
    }

    private int getIntOrDefault(JsonObject obj, String key, int defaultVal) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return defaultVal;
    }

    private void prosesLogin() {
        String rawPhone = etLoginPhone.getText().toString().trim();
        if (rawPhone.isEmpty()) {
            Toast.makeText(this, "Masukkan nomor WhatsApp terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pinInput.length() < 4) {
            Toast.makeText(this, "Masukkan 4-digit PIN terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneInput = normalizePhone(rawPhone);
        setLoading(true);

        String endpoint = "outlets?owner_phone=eq." + phoneInput + "&kasir_pin=eq." + pinInput + "&select=*";

        SupabaseClient.get(endpoint, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    updatePinDots("Gagal koneksi: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                runOnUiThread(() -> {
                    setLoading(false);
                    if (!response.isSuccessful()) {
                        updatePinDots("Server error (" + response.code() + ")");
                        return;
                    }

                    JsonArray results = JsonParser.parseString(body).getAsJsonArray();
                    if (results.size() > 0) {
                        JsonObject outlet = results.get(0).getAsJsonObject();
                        String id = getStringOrDefault(outlet, "id", "");
                        String name = getStringOrDefault(outlet, "outlet_name", "Outlet WashLog");
                        String phone = getStringOrDefault(outlet, "owner_phone", phoneInput);

                        String ownerPin = getStringOrDefault(outlet, "owner_pin", "");
                        int commissionPct = getIntOrDefault(outlet, "worker_commission_pct", 40);
                        int priceSmall = getIntOrDefault(outlet, "price_small", 15000);
                        int priceMedium = getIntOrDefault(outlet, "price_medium", 20000);
                        int priceLarge = getIntOrDefault(outlet, "price_large", 25000);
                        String avatarBase64 = getStringOrDefault(outlet, "avatar_base64", "");

                        sessionManager.saveOutletSession(id, name, phone);
                        sessionManager.saveOutletConfig(ownerPin, commissionPct, priceSmall, priceMedium, priceLarge, avatarBase64);

                        Toast.makeText(LoginActivity.this, "Selamat datang, " + name + "!", Toast.LENGTH_SHORT).show();

                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        pinInput = "";
                        updatePinDots("Nomor WA atau PIN Kasir tidak cocok");
                    }
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        pbLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}