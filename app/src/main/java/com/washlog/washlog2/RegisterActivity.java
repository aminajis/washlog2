package com.washlog.washlog2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private EditText etOutletName, etOwnerPhone;
    private EditText etPin1, etPin2, etPin3, etPin4;
    private LinearLayout cardWorker13, cardWorker46, cardWorkerMore6;
    private TextView tvWorker13Title, tvWorker46Title, tvWorkerMore6Title;
    private CheckBox cbTerms;
    private Button btnRegisterSubmit;
    private ProgressBar pbRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_register);

        View scrollView = findViewById(R.id.scrollRegister);
        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (12 * density);
        int padTopBase = (int) (14 * density);
        int padBottomBase = (int) (28 * density);

        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padH, padTopBase + sysBars.top, padH, padBottomBase + sysBars.bottom);
            return insets;
        });

        etOutletName = findViewById(R.id.etOutletName);
        etOwnerPhone = findViewById(R.id.etOwnerPhone);
        etPin1 = findViewById(R.id.etPin1);
        etPin2 = findViewById(R.id.etPin2);
        etPin3 = findViewById(R.id.etPin3);
        etPin4 = findViewById(R.id.etPin4);

        cardWorker13 = findViewById(R.id.cardWorker13);
        cardWorker46 = findViewById(R.id.cardWorker46);
        cardWorkerMore6 = findViewById(R.id.cardWorkerMore6);
        tvWorker13Title = findViewById(R.id.tvWorker13Title);
        tvWorker46Title = findViewById(R.id.tvWorker46Title);
        tvWorkerMore6Title = findViewById(R.id.tvWorkerMore6Title);

        cbTerms = findViewById(R.id.cbTerms);
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit);
        pbRegister = findViewById(R.id.pbRegister);

        setupPinAutoFocus(etPin1, etPin2);
        setupPinAutoFocus(etPin2, etPin3);
        setupPinAutoFocus(etPin3, etPin4);

        cardWorker13.setOnClickListener(v -> selectWorkerRange("1 - 3"));
        cardWorker46.setOnClickListener(v -> selectWorkerRange("4 - 6"));
        cardWorkerMore6.setOnClickListener(v -> selectWorkerRange("> 6"));

        btnRegisterSubmit.setOnClickListener(v -> submitRegistration());

        findViewById(R.id.tvGoLogin).setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void setupPinAutoFocus(EditText current, EditText next) {
        current.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) {
                    next.requestFocus();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void selectWorkerRange(String range) {
        boolean s13 = range.equals("1 - 3");
        boolean s46 = range.equals("4 - 6");
        boolean sMore = range.equals("> 6");

        cardWorker13.setBackgroundResource(s13 ? R.drawable.bg_option_selected : R.drawable.bg_option_unselected);
        cardWorker46.setBackgroundResource(s46 ? R.drawable.bg_option_selected : R.drawable.bg_option_unselected);
        cardWorkerMore6.setBackgroundResource(sMore ? R.drawable.bg_option_selected : R.drawable.bg_option_unselected);

        tvWorker13Title.setTextColor(ContextCompat.getColor(this, s13 ? R.color.royal_blue : R.color.deep_slate_navy));
        tvWorker46Title.setTextColor(ContextCompat.getColor(this, s46 ? R.color.royal_blue : R.color.deep_slate_navy));
        tvWorkerMore6Title.setTextColor(ContextCompat.getColor(this, sMore ? R.color.royal_blue : R.color.deep_slate_navy));
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

    private void submitRegistration() {
        String outletName = etOutletName.getText().toString().trim();
        String rawPhone = etOwnerPhone.getText().toString().trim();
        String pin = etPin1.getText().toString().trim()
                + etPin2.getText().toString().trim()
                + etPin3.getText().toString().trim()
                + etPin4.getText().toString().trim();

        if (outletName.isEmpty()) {
            Toast.makeText(this, "Nama tempat steam wajib diisi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rawPhone.isEmpty()) {
            Toast.makeText(this, "Nomor WhatsApp pemilik wajib diisi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pin.length() != 4) {
            Toast.makeText(this, "Lengkapi 4-digit PIN kasir", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Centang persetujuan Ketentuan Layanan dulu", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullPhone = normalizePhone(rawPhone);

        JsonObject payload = new JsonObject();
        payload.addProperty("outlet_name", outletName);
        payload.addProperty("owner_phone", fullPhone);
        payload.addProperty("kasir_pin", pin);
        payload.addProperty("worker_commission_pct", 40);

        setLoading(true);

        SupabaseClient.post("outlets", payload.toString(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    new AlertDialog.Builder(RegisterActivity.this)
                            .setTitle("Gagal Koneksi")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    setLoading(false);
                    if (response.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Outlet berhasil terdaftar! Silakan Login Kasir", Toast.LENGTH_SHORT).show();
                        Intent loginIntent = new Intent(RegisterActivity.this, LoginActivity.class);
                        loginIntent.putExtra("extra_phone", fullPhone);
                        startActivity(loginIntent);
                        finish();
                    } else {
                        new AlertDialog.Builder(RegisterActivity.this)
                                .setTitle("Error Supabase (" + response.code() + ")")
                                .setMessage(body)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        pbRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegisterSubmit.setEnabled(!loading);
    }
}