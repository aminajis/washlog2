package com.washlog.washlog2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProfileActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private ImageView ivProfileOutletAvatar, ivProfileAvatarFallback;
    private TextView tvProfileOutletName, tvProfileOwnerPhone, tvWorkerCountHeader;
    private TextView tvPrefWorkerPct, tvPrefOpsPct, tvPrefOwnerPct;
    private ProgressBar pbProfile;
    private LinearLayout cardEmptyWorkers, llWorkerList;

    private String currentOutletName = "Outlet WashLog";
    private String currentOwnerPhone = "-";
    private String currentAddress = "";
    private String currentPin = "";

    private Bitmap pendingOutletBitmap = null;
    private ImageView activeDialogAvatarView = null;
    private TextView activeDialogFallbackView = null;

    private final ActivityResultLauncher<String> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Bitmap decoded = decodeSampledBitmapFromUri(uri, 400, 400);
                    if (decoded != null) {
                        pendingOutletBitmap = decoded;
                        if (activeDialogAvatarView != null && activeDialogFallbackView != null) {
                            activeDialogAvatarView.setImageBitmap(decoded);
                            activeDialogAvatarView.setVisibility(View.VISIBLE);
                            activeDialogFallbackView.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this, "Gagal membaca gambar dari galeri", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final Map<String, Integer> washerTxCountMap = new HashMap<>();
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);

        View topBar = findViewById(R.id.topBarProfile);
        View bottomNav = findViewById(R.id.bottomNavProfile);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);
        int botPadH = (int) (8 * density);
        int botPadV = (int) (8 * density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootProfile), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            bottomNav.setPadding(botPadH, botPadV, botPadH, botPadV + sysBars.bottom);
            return insets;
        });

        ivProfileOutletAvatar = findViewById(R.id.ivProfileOutletAvatar);
        ivProfileAvatarFallback = findViewById(R.id.ivProfileAvatarFallback);
        tvProfileOutletName = findViewById(R.id.tvProfileOutletName);
        tvProfileOwnerPhone = findViewById(R.id.tvProfileOwnerPhone);
        tvWorkerCountHeader = findViewById(R.id.tvWorkerCountHeader);
        tvPrefWorkerPct = findViewById(R.id.tvPrefWorkerPct);
        tvPrefOpsPct = findViewById(R.id.tvPrefOpsPct);
        tvPrefOwnerPct = findViewById(R.id.tvPrefOwnerPct);
        pbProfile = findViewById(R.id.pbProfile);
        cardEmptyWorkers = findViewById(R.id.cardEmptyWorkers);
        llWorkerList = findViewById(R.id.llWorkerList);

        currentOutletName = sessionManager.getOutletName().isEmpty() ? "Outlet WashLog" : sessionManager.getOutletName();
        tvProfileOutletName.setText(currentOutletName);
        loadProfileAvatar();

        findViewById(R.id.btnBackProfile).setOnClickListener(v -> finish());
        findViewById(R.id.navHomeProf).setOnClickListener(v -> finish());
        findViewById(R.id.navExpenseProf).setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ExpenseActivity.class));
            finish();
        });
        findViewById(R.id.navReportProf).setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ReportActivity.class));
            finish();
        });

        findViewById(R.id.btnCopyOwnerPhone).setOnClickListener(v -> {
            if (currentOwnerPhone.isEmpty() || currentOwnerPhone.equals("-")) {
                Toast.makeText(this, "Nomor WhatsApp pemilik belum diatur", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp Pemilik", currentOwnerPhone));
                Toast.makeText(this, "Nomor WhatsApp disalin: " + currentOwnerPhone, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnOpenChangePin).setOnClickListener(v -> showChangePinDialog());
        findViewById(R.id.btnOpenEditProfile).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.cardProfileAvatar).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.btnAddWorker).setOnClickListener(v -> showAddWorkerDialog());

        findViewById(R.id.btnLogoutShift).setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadProfileAndWorkers();
    }

    private void loadProfileAvatar() {
        Bitmap photo = sessionManager.getOutletPhoto();
        if (photo != null) {
            ivProfileOutletAvatar.setImageBitmap(photo);
            ivProfileOutletAvatar.setVisibility(View.VISIBLE);
            ivProfileAvatarFallback.setVisibility(View.GONE);
        } else {
            ivProfileOutletAvatar.setVisibility(View.GONE);
            ivProfileAvatarFallback.setVisibility(View.VISIBLE);
        }
    }

    private void loadProfileAndWorkers() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        pbProfile.setVisibility(View.VISIBLE);

        // 1. Ambil profil outlet & sinkronkan ke SessionManager
        SupabaseClient.get("outlets?id=eq." + outletId + "&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    if (!arr.isEmpty()) {
                        JsonObject obj = arr.get(0).getAsJsonObject();
                        currentOutletName = getStr(obj, "outlet_name", currentOutletName);
                        currentOwnerPhone = getStr(obj, "owner_phone", "-");
                        currentAddress = getStr(obj, "address", "");
                        currentPin = getStr(obj, "kasir_pin", "");

                        int workerPct = (obj.has("worker_commission_pct") && !obj.get("worker_commission_pct").isJsonNull())
                                ? obj.get("worker_commission_pct").getAsInt() : 40;
                        int ownerPct = 20;
                        int opsPct = Math.max(0, 100 - workerPct - ownerPct);
                        tvPrefWorkerPct.setText(workerPct + "%");
                        tvPrefOpsPct.setText(opsPct + "%");
                        tvPrefOwnerPct.setText(ownerPct + "%");

                        sessionManager.updateOutletInfo(currentOutletName, currentOwnerPhone);
                        tvProfileOutletName.setText(currentOutletName);
                        tvProfileOwnerPhone.setText(currentOwnerPhone);
                    }
                });
            }
        });

        // 2. Hitung total motor cuci per washer dari transaksi
        SupabaseClient.get("transactions?outlet_id=eq." + outletId + "&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                fetchWorkersList(outletId);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                washerTxCountMap.clear();
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray txArr = JsonParser.parseString(response.body().string()).getAsJsonArray();
                    for (JsonElement el : txArr) {
                        JsonObject tx = el.getAsJsonObject();
                        String key = getStr(tx, "worker_id",
                                getStr(tx, "washer_name",
                                        getStr(tx, "worker_name", "")));
                        if (key.isEmpty()) {
                            String plat = getStr(tx, "plat_no", getStr(tx, "plate_number", ""));
                            if (plat.contains("•")) {
                                String[] parts = plat.split("•");
                                if (parts.length > 1) key = parts[1].trim();
                            }
                        }
                        if (!key.isEmpty()) {
                            Integer prev = washerTxCountMap.get(key);
                            washerTxCountMap.put(key, (prev == null ? 0 : prev) + 1);
                        }
                    }
                }
                fetchWorkersList(outletId);
            }
        });
    }

    private void fetchWorkersList(String outletId) {
        SupabaseClient.get("workers?outlet_id=eq." + outletId + "&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    pbProfile.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, "Gagal memuat pekerja: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                runOnUiThread(() -> {
                    pbProfile.setVisibility(View.GONE);
                    if (!response.isSuccessful()) {
                        new AlertDialog.Builder(ProfileActivity.this)
                                .setTitle("Error Muat Workers (" + response.code() + ")")
                                .setMessage(body)
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    JsonArray workersArr = JsonParser.parseString(body).getAsJsonArray();
                    renderWorkers(workersArr);
                });
            }
        });
    }

    private void renderWorkers(JsonArray workersArr) {
        int count = workersArr.size();
        tvWorkerCountHeader.setText("Total " + count + " Pekerja Terdaftar");
        cardEmptyWorkers.setVisibility(count == 0 ? View.VISIBLE : View.GONE);

        llWorkerList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < count; i++) {
            JsonObject w = workersArr.get(i).getAsJsonObject();
            String workerId = getStr(w, "id", "");
            String name = getStr(w, "name", getStr(w, "worker_name", "Washer"));
            String shift = getStr(w, "shift", "Pagi");
            boolean isActive = !w.has("is_active") || w.get("is_active").isJsonNull() || w.get("is_active").getAsBoolean();

            int byId = washerTxCountMap.containsKey(workerId) ? washerTxCountMap.get(workerId) : 0;
            int byName = washerTxCountMap.containsKey(name) ? washerTxCountMap.get(name) : 0;
            int washedCount = Math.max(byId, byName);

            View item = inflater.inflate(R.layout.item_worker, llWorkerList, false);
            TextView tvWorkerInitial = item.findViewById(R.id.tvWorkerInitial);
            TextView tvWorkerName = item.findViewById(R.id.tvWorkerName);
            TextView tvWorkerStatusBadge = item.findViewById(R.id.tvWorkerStatusBadge);
            TextView tvWorkerMeta = item.findViewById(R.id.tvWorkerMeta);
            SwitchCompat swWorkerActive = item.findViewById(R.id.swWorkerActive);

            String initial = name.isEmpty() ? "W" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            tvWorkerInitial.setText(initial);
            tvWorkerName.setText(name);
            tvWorkerMeta.setText("Shift " + shift + " • " + washedCount + " motor dicuci");
            swWorkerActive.setChecked(isActive);
            applyWorkerStatusBadge(tvWorkerStatusBadge, isActive);

            swWorkerActive.setOnCheckedChangeListener((buttonView, checked) -> {
                applyWorkerStatusBadge(tvWorkerStatusBadge, checked);
                if (workerId.isEmpty()) return;
                JsonObject patchBody = new JsonObject();
                patchBody.addProperty("is_active", checked);
                SupabaseClient.patch("workers?id=eq." + workerId, patchBody.toString(), new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {}

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {}
                });
            });

            item.setOnLongClickListener(v -> {
                if (workerId.isEmpty()) return true;
                new AlertDialog.Builder(this)
                        .setTitle("Hapus Pekerja")
                        .setMessage("Yakin ingin menghapus " + name + "?")
                        .setPositiveButton("Hapus", (d, which) -> deleteWorkerFromSupabase(workerId, name))
                        .setNegativeButton("Batal", null)
                        .show();
                return true;
            });

            llWorkerList.addView(item);
        }
    }

    private void applyWorkerStatusBadge(TextView badge, boolean isActive) {
        if (isActive) {
            badge.setText("Aktif");
            badge.setBackgroundResource(R.drawable.bg_pill_green);
            badge.setTextColor(ContextCompat.getColor(this, R.color.emerald_green));
        } else {
            badge.setText("Non-Aktif");
            badge.setBackgroundResource(R.drawable.bg_card_white);
            badge.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
        }
    }

    private void deleteWorkerFromSupabase(String workerId, String name) {
        pbProfile.setVisibility(View.VISIBLE);
        Request request = new Request.Builder()
                .url(SupabaseClient.SUPABASE_URL + "workers?id=eq." + workerId)
                .addHeader("apikey", SupabaseClient.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SupabaseClient.SUPABASE_KEY)
                .delete()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    pbProfile.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, "Gagal menghapus pekerja", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, name + " dihapus", Toast.LENGTH_SHORT).show();
                    loadProfileAndWorkers();
                });
            }
        });
    }

    private void showAddWorkerDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_worker, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText etWorkerName = view.findViewById(R.id.etWorkerName);
        EditText etWorkerPhone = view.findViewById(R.id.etWorkerPhone);
        TextView chipPagi = view.findViewById(R.id.chipShiftPagi);
        TextView chipSore = view.findViewById(R.id.chipShiftSore);
        TextView chipFull = view.findViewById(R.id.chipShiftFull);

        final String[] selectedShift = {"Pagi"};

        View.OnClickListener shiftListener = v -> {
            if (v.getId() == R.id.chipShiftPagi) selectedShift[0] = "Pagi";
            else if (v.getId() == R.id.chipShiftSore) selectedShift[0] = "Sore";
            else selectedShift[0] = "Full Time";

            styleShiftChip(chipPagi, selectedShift[0].equals("Pagi"));
            styleShiftChip(chipSore, selectedShift[0].equals("Sore"));
            styleShiftChip(chipFull, selectedShift[0].equals("Full Time"));
        };

        chipPagi.setOnClickListener(shiftListener);
        chipSore.setOnClickListener(shiftListener);
        chipFull.setOnClickListener(shiftListener);

        view.findViewById(R.id.btnCloseAddWorker).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnSaveWorkerModal).setOnClickListener(v -> {
            String name = etWorkerName.getText().toString().trim();
            String phone = etWorkerPhone.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Nama lengkap pekerja wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            String outletId = sessionManager.getOutletId();
            if (outletId.isEmpty()) {
                Toast.makeText(this, "Sesi outlet kosong, silakan login ulang", Toast.LENGTH_SHORT).show();
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("outlet_id", outletId);
            payload.addProperty("name", name);
            if (!phone.isEmpty()) {
                payload.addProperty("phone", phone);
            }
            payload.addProperty("shift", selectedShift[0]);
            payload.addProperty("is_active", true);

            dialog.dismiss();
            pbProfile.setVisibility(View.VISIBLE);
            postWorkerWithFallback(payload, name);
        });

        dialog.show();
    }

    private void postWorkerWithFallback(JsonObject payload, String workerName) {
        SupabaseClient.post("workers", payload.toString(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    pbProfile.setVisibility(View.GONE);
                    new AlertDialog.Builder(ProfileActivity.this)
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
                    if (response.isSuccessful()) {
                        Toast.makeText(ProfileActivity.this, "Pekerja " + workerName + " tersimpan!", Toast.LENGTH_SHORT).show();
                        loadProfileAndWorkers();
                        return;
                    }

                    if (body.contains("PGRST204")) {
                        JsonObject retry = payload.deepCopy();
                        if (body.contains("'phone'") && retry.has("phone")) {
                            retry.remove("phone");
                            postWorkerWithFallback(retry, workerName);
                            return;
                        }
                        if (body.contains("'shift'") && retry.has("shift")) {
                            retry.remove("shift");
                            postWorkerWithFallback(retry, workerName);
                            return;
                        }
                        if (body.contains("'is_active'") && retry.has("is_active")) {
                            retry.remove("is_active");
                            postWorkerWithFallback(retry, workerName);
                            return;
                        }
                        if (body.contains("'name'") && retry.has("name")) {
                            String val = retry.get("name").getAsString();
                            retry.remove("name");
                            retry.addProperty("worker_name", val);
                            postWorkerWithFallback(retry, workerName);
                            return;
                        }
                    }

                    pbProfile.setVisibility(View.GONE);
                    new AlertDialog.Builder(ProfileActivity.this)
                            .setTitle("Gagal Simpan Pekerja (" + response.code() + ")")
                            .setMessage(body)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void styleShiftChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_option_selected : R.drawable.bg_option_unselected);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.royal_blue : R.color.deep_slate_navy));
    }

    private void showChangePinDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_pin, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btnCloseChangePin).setOnClickListener(v -> dialog.dismiss());

        EditText etOldPin = view.findViewById(R.id.etOldPin);
        EditText etNewPin = view.findViewById(R.id.etNewPin);
        EditText etConfirmPin = view.findViewById(R.id.etConfirmPin);

        view.findViewById(R.id.btnSaveNewPin).setOnClickListener(v -> {
            String oldPin = etOldPin.getText().toString().trim();
            String newPin = etNewPin.getText().toString().trim();
            String confirmPin = etConfirmPin.getText().toString().trim();

            if (!currentPin.isEmpty() && !oldPin.equals(currentPin)) {
                Toast.makeText(this, "PIN saat ini tidak sesuai", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPin.length() != 4) {
                Toast.makeText(this, "PIN baru harus 4 digit", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPin.equals(confirmPin)) {
                Toast.makeText(this, "Konfirmasi PIN baru tidak cocok", Toast.LENGTH_SHORT).show();
                return;
            }

            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("kasir_pin", newPin);

            SupabaseClient.patch("outlets?id=eq." + sessionManager.getOutletId(), patchBody.toString(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Gagal ubah PIN", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        currentPin = newPin;
                        dialog.dismiss();
                        Toast.makeText(ProfileActivity.this, "PIN Kasir berhasil diperbarui!", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        dialog.show();
    }

    private void showEditProfileDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btnCloseEditProfile).setOnClickListener(v -> dialog.dismiss());

        pendingOutletBitmap = null;
        activeDialogAvatarView = view.findViewById(R.id.ivEditOutletAvatar);
        activeDialogFallbackView = view.findViewById(R.id.tvEditAvatarFallback);

        Bitmap existingPhoto = sessionManager.getOutletPhoto();
        if (existingPhoto != null) {
            activeDialogAvatarView.setImageBitmap(existingPhoto);
            activeDialogAvatarView.setVisibility(View.VISIBLE);
            activeDialogFallbackView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.btnPickOutletPhoto).setOnClickListener(v ->
                pickPhotoLauncher.launch("image/*"));

        dialog.setOnDismissListener(d -> {
            activeDialogAvatarView = null;
            activeDialogFallbackView = null;
        });

        EditText etEditOutletName = view.findViewById(R.id.etEditOutletName);
        EditText etEditOwnerPhone = view.findViewById(R.id.etEditOwnerPhone);
        EditText etEditAddress = view.findViewById(R.id.etEditAddress);

        etEditOutletName.setText(currentOutletName);
        etEditOwnerPhone.setText(currentOwnerPhone.equals("-") ? "" : currentOwnerPhone);
        etEditAddress.setText(currentAddress);

        view.findViewById(R.id.btnSaveProfileEdit).setOnClickListener(v -> {
            String newName = etEditOutletName.getText().toString().trim();
            String newPhone = etEditOwnerPhone.getText().toString().trim();
            String newAddr = etEditAddress.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Nama tempat steam wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            if (pendingOutletBitmap != null) {
                sessionManager.saveOutletPhoto(pendingOutletBitmap);
                loadProfileAvatar();
            }

            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("outlet_name", newName);
            patchBody.addProperty("owner_phone", newPhone);
            patchBody.addProperty("address", newAddr);

            SupabaseClient.patch("outlets?id=eq." + sessionManager.getOutletId(), patchBody.toString(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Gagal simpan profil", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        currentOutletName = newName;
                        currentOwnerPhone = newPhone.isEmpty() ? "-" : newPhone;
                        currentAddress = newAddr;
                        sessionManager.updateOutletInfo(currentOutletName, currentOwnerPhone);
                        tvProfileOutletName.setText(currentOutletName);
                        tvProfileOwnerPhone.setText(currentOwnerPhone);
                        dialog.dismiss();
                        Toast.makeText(ProfileActivity.this, "Profil outlet diperbarui!", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        dialog.show();
    }

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, options);
            }

            int inSampleSize = 1;
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                int halfHeight = options.outHeight / 2;
                int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is2, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String getStr(JsonObject obj, String key, String fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : fallback;
    }
}