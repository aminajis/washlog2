package com.washlog.washlog2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
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
import java.text.NumberFormat;
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
    private TextView tvPrefPriceSmall, tvPrefPriceMedium, tvPrefPriceLarge;
    private ProgressBar pbProfile;
    private LinearLayout cardEmptyWorkers, llWorkerList;

    private String currentOutletName = "Outlet WashLog";
    private String currentOwnerPhone = "-";
    private String currentAddress = "";
    private String currentKasirPin = "";
    private String currentOwnerPin = "";
    private int currentWorkerPct = 40;
    private int currentPriceSmall = 15000;
    private int currentPriceMedium = 20000;
    private int currentPriceLarge = 25000;

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

        tvPrefPriceSmall = findViewById(R.id.tvPrefPriceSmall);
        tvPrefPriceMedium = findViewById(R.id.tvPrefPriceMedium);
        tvPrefPriceLarge = findViewById(R.id.tvPrefPriceLarge);

        pbProfile = findViewById(R.id.pbProfile);
        cardEmptyWorkers = findViewById(R.id.cardEmptyWorkers);
        llWorkerList = findViewById(R.id.llWorkerList);

        currentOutletName = sessionManager.getOutletName().isEmpty() ? "Outlet WashLog" : sessionManager.getOutletName();
        currentOwnerPin = sessionManager.getOwnerPin();
        currentWorkerPct = sessionManager.getCommissionPct();
        currentPriceSmall = sessionManager.getPriceSmall();
        currentPriceMedium = sessionManager.getPriceMedium();
        currentPriceLarge = sessionManager.getPriceLarge();

        tvProfileOutletName.setText(currentOutletName);
        updatePreferencesDisplay();
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

        findViewById(R.id.btnOpenChangePin).setOnClickListener(v ->
                requireOwnerPin("Ubah PIN Kasir", this::showChangePinDialog));

        findViewById(R.id.btnOpenChangeOwnerPin).setOnClickListener(v ->
                requireOwnerPin("Ubah PIN Owner", this::showChangeOwnerPinDialog));

        findViewById(R.id.btnOpenEditProfile).setOnClickListener(v ->
                requireOwnerPin("Edit Profil Outlet", this::showEditProfileDialog));

        findViewById(R.id.cardProfileAvatar).setOnClickListener(v ->
                requireOwnerPin("Ubah Foto Outlet", this::showEditProfileDialog));

        findViewById(R.id.btnAddWorker).setOnClickListener(v ->
                requireOwnerPin("Tambah Pekerja", this::showAddWorkerDialog));

        findViewById(R.id.btnEditSystemPrefs).setOnClickListener(v ->
                requireOwnerPin("Ubah Komisi & Tarif", this::showEditPrefsDialog));

        findViewById(R.id.btnLogoutShift).setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadProfileAndWorkers();
    }

    private void styleDialogEditText(EditText et, float density) {
        int padH = (int) (14 * density);
        int padV = (int) (12 * density);
        et.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        et.setHintTextColor(Color.parseColor("#94A3B8"));
        et.setTextSize(14f);
        et.setBackgroundResource(R.drawable.bg_input_field);
        et.setPadding(padH, padV, padH, padV);
    }

    private LinearLayout createDialogCardContainer(float density) {
        int pad = (int) (20 * density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        container.setBackgroundResource(R.drawable.bg_dialog_white);
        return container;
    }

    private TextView createDialogTitle(String titleText) {
        TextView tvTitle = new TextView(this);
        tvTitle.setText(titleText);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        return tvTitle;
    }

    private TextView createFieldLabel(String labelText, float density, int topMarginDp) {
        TextView lbl = new TextView(this);
        lbl.setText(labelText);
        lbl.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        lbl.setTextSize(12f);
        lbl.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (topMarginDp * density);
        lbl.setLayoutParams(lp);
        return lbl;
    }

    private LinearLayout createDialogButtonsRow(float density, String positiveText, Runnable onCancel, Runnable onConfirm) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = (int) (18 * density);
        row.setLayoutParams(rowLp);

        int btnHeight = (int) (44 * density);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("Batal");
        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        btnCancel.setTextSize(13f);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setBackgroundResource(R.drawable.bg_input_field);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, btnHeight, 1f);
        cancelLp.setMarginEnd((int) (8 * density));
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setOnClickListener(v -> onCancel.run());

        TextView btnOk = new TextView(this);
        btnOk.setText(positiveText);
        btnOk.setTextColor(Color.WHITE);
        btnOk.setTextSize(13f);
        btnOk.setTypeface(null, Typeface.BOLD);
        btnOk.setGravity(Gravity.CENTER);
        btnOk.setBackgroundResource(R.drawable.bg_btn_navy);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, btnHeight, 1f);
        btnOk.setLayoutParams(okLp);
        btnOk.setOnClickListener(v -> onConfirm.run());

        row.addView(btnCancel);
        row.addView(btnOk);
        return row;
    }

    /**
     * Dialog verifikasi 4-digit PIN Owner dengan kartu putih solid (anti-Dark Mode transparan)
     */
    private void requireOwnerPin(String actionTitle, Runnable onAuthorized) {
        String expectedPin = !currentOwnerPin.isEmpty() ? currentOwnerPin : sessionManager.getOwnerPin();
        if (expectedPin.isEmpty()) {
            expectedPin = currentKasirPin;
        }
        if (expectedPin.isEmpty()) {
            onAuthorized.run();
            return;
        }

        final String targetPin = expectedPin;
        float density = getResources().getDisplayMetrics().density;

        LinearLayout container = createDialogCardContainer(density);
        container.addView(createDialogTitle("🔐 Verifikasi PIN Owner"));

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Masukkan 4-digit PIN Owner untuk melanjutkan aksi \"" + actionTitle + "\".");
        tvSubtitle.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
        tvSubtitle.setTextSize(12.5f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (6 * density);
        tvSubtitle.setLayoutParams(subLp);
        container.addView(tvSubtitle);

        EditText etPinInput = new EditText(this);
        etPinInput.setHint("• • • •");
        etPinInput.setGravity(Gravity.CENTER);
        etPinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        styleDialogEditText(etPinInput, density);
        etPinInput.setTextSize(22f);
        etPinInput.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (14 * density);
        etPinInput.setLayoutParams(lp);
        container.addView(etPinInput);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        container.addView(createDialogButtonsRow(density, "Verifikasi", dialog::dismiss, () -> {
            String entered = etPinInput.getText().toString().trim();
            if (entered.equals(targetPin)) {
                dialog.dismiss();
                onAuthorized.run();
            } else {
                Toast.makeText(this, "PIN Owner salah! Akses ditolak.", Toast.LENGTH_SHORT).show();
            }
        }));

        dialog.show();
    }

    private void updatePreferencesDisplay() {
        int ownerPct = 20;
        int opsPct = Math.max(0, 100 - currentWorkerPct - ownerPct);
        tvPrefWorkerPct.setText(currentWorkerPct + "%");
        tvPrefOpsPct.setText(opsPct + "%");
        tvPrefOwnerPct.setText(ownerPct + "%");

        tvPrefPriceSmall.setText(formatRupiah(currentPriceSmall));
        tvPrefPriceMedium.setText(formatRupiah(currentPriceMedium));
        tvPrefPriceLarge.setText(formatRupiah(currentPriceLarge));
    }

    private String formatRupiah(int amount) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("id", "ID"));
        return "Rp " + nf.format(amount);
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
                        currentKasirPin = getStr(obj, "kasir_pin", "");
                        currentOwnerPin = getStr(obj, "owner_pin", currentOwnerPin);

                        currentWorkerPct = getInt(obj, "worker_commission_pct", 40);
                        currentPriceSmall = getInt(obj, "price_small", 15000);
                        currentPriceMedium = getInt(obj, "price_medium", 20000);
                        currentPriceLarge = getInt(obj, "price_large", 25000);
                        String avatarBase64 = getStr(obj, "avatar_base64", "");

                        sessionManager.updateOutletInfo(currentOutletName, currentOwnerPhone);
                        sessionManager.saveOutletConfig(
                                currentOwnerPin,
                                currentWorkerPct,
                                currentPriceSmall,
                                currentPriceMedium,
                                currentPriceLarge,
                                avatarBase64
                        );

                        tvProfileOutletName.setText(currentOutletName);
                        tvProfileOwnerPhone.setText(currentOwnerPhone);
                        updatePreferencesDisplay();
                        loadProfileAvatar();
                    }
                });
            }
        });

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

            swWorkerActive.setOnClickListener(v -> {
                boolean desiredState = swWorkerActive.isChecked();
                swWorkerActive.setChecked(!desiredState);

                requireOwnerPin((desiredState ? "Aktifkan " : "Nonaktifkan ") + name, () -> {
                    swWorkerActive.setChecked(desiredState);
                    applyWorkerStatusBadge(tvWorkerStatusBadge, desiredState);
                    if (workerId.isEmpty()) return;

                    JsonObject patchBody = new JsonObject();
                    patchBody.addProperty("is_active", desiredState);
                    SupabaseClient.patch("workers?id=eq." + workerId, patchBody.toString(), new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {}

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                            runOnUiThread(() -> Toast.makeText(
                                    ProfileActivity.this,
                                    name + (desiredState ? " diaktifkan" : " dinonaktifkan"),
                                    Toast.LENGTH_SHORT
                            ).show());
                        }
                    });
                });
            });

            item.setOnLongClickListener(v -> {
                if (workerId.isEmpty()) return true;
                requireOwnerPin("Hapus Pekerja", () ->
                        new AlertDialog.Builder(this)
                                .setTitle("Hapus Pekerja")
                                .setMessage("Yakin ingin menghapus " + name + "?")
                                .setPositiveButton("Hapus", (d, which) -> deleteWorkerFromSupabase(workerId, name))
                                .setNegativeButton("Batal", null)
                                .show()
                );
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

    private void showEditPrefsDialog() {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout container = createDialogCardContainer(density);
        container.addView(createDialogTitle("Ubah Komisi & Tarif Motor"));

        // 1. Persentase Komisi Pekerja
        container.addView(createFieldLabel("Persentase Komisi Pekerja (%) — Pemilik tetap 20%", density, 14));
        EditText etPct = new EditText(this);
        etPct.setInputType(InputType.TYPE_CLASS_NUMBER);
        etPct.setText(String.valueOf(currentWorkerPct));
        styleDialogEditText(etPct, density);
        LinearLayout.LayoutParams lpPct = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpPct.topMargin = (int) (5 * density);
        etPct.setLayoutParams(lpPct);
        container.addView(etPct);

        // 2. Tarif Motor Kecil
        container.addView(createFieldLabel("Tarif Motor Kecil (Rp)", density, 12));
        EditText etSmall = new EditText(this);
        etSmall.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSmall.setText(String.valueOf(currentPriceSmall));
        styleDialogEditText(etSmall, density);
        LinearLayout.LayoutParams lpSmall = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSmall.topMargin = (int) (5 * density);
        etSmall.setLayoutParams(lpSmall);
        container.addView(etSmall);

        // 3. Tarif Motor Sedang
        container.addView(createFieldLabel("Tarif Motor Sedang (Rp)", density, 12));
        EditText etMedium = new EditText(this);
        etMedium.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMedium.setText(String.valueOf(currentPriceMedium));
        styleDialogEditText(etMedium, density);
        LinearLayout.LayoutParams lpMed = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMed.topMargin = (int) (5 * density);
        etMedium.setLayoutParams(lpMed);
        container.addView(etMedium);

        // 4. Tarif Motor Besar
        container.addView(createFieldLabel("Tarif Motor Besar (Rp)", density, 12));
        EditText etLarge = new EditText(this);
        etLarge.setInputType(InputType.TYPE_CLASS_NUMBER);
        etLarge.setText(String.valueOf(currentPriceLarge));
        styleDialogEditText(etLarge, density);
        LinearLayout.LayoutParams lpLarge = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpLarge.topMargin = (int) (5 * density);
        etLarge.setLayoutParams(lpLarge);
        container.addView(etLarge);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        container.addView(createDialogButtonsRow(density, "Simpan", dialog::dismiss, () -> {
            String strPct = etPct.getText().toString().trim();
            String strSmall = etSmall.getText().toString().trim();
            String strMedium = etMedium.getText().toString().trim();
            String strLarge = etLarge.getText().toString().trim();

            if (strPct.isEmpty() || strSmall.isEmpty() || strMedium.isEmpty() || strLarge.isEmpty()) {
                Toast.makeText(this, "Semua kolom komisi dan tarif wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            int newPct = Integer.parseInt(strPct);
            int newSmall = Integer.parseInt(strSmall);
            int newMedium = Integer.parseInt(strMedium);
            int newLarge = Integer.parseInt(strLarge);

            if (newPct < 0 || newPct > 80) {
                Toast.makeText(this, "Komisi pekerja harus antara 0% - 80%", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newSmall <= 0 || newMedium <= 0 || newLarge <= 0) {
                Toast.makeText(this, "Tarif motor harus lebih dari Rp 0", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("worker_commission_pct", newPct);
            patchBody.addProperty("price_small", newSmall);
            patchBody.addProperty("price_medium", newMedium);
            patchBody.addProperty("price_large", newLarge);

            pbProfile.setVisibility(View.VISIBLE);
            SupabaseClient.patch("outlets?id=eq." + sessionManager.getOutletId(), patchBody.toString(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        pbProfile.setVisibility(View.GONE);
                        Toast.makeText(ProfileActivity.this, "Gagal menyimpan perubahan", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        pbProfile.setVisibility(View.GONE);
                        if (response.isSuccessful()) {
                            currentWorkerPct = newPct;
                            currentPriceSmall = newSmall;
                            currentPriceMedium = newMedium;
                            currentPriceLarge = newLarge;

                            sessionManager.setCommissionPct(newPct);
                            sessionManager.setMotorPrices(newSmall, newMedium, newLarge);
                            updatePreferencesDisplay();
                            Toast.makeText(ProfileActivity.this, "Komisi & tarif motor diperbarui!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ProfileActivity.this, "Gagal update ke server (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }));

        dialog.show();
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

            if (!currentKasirPin.isEmpty() && !oldPin.equals(currentKasirPin)) {
                Toast.makeText(this, "PIN Kasir saat ini tidak sesuai", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPin.length() != 4) {
                Toast.makeText(this, "PIN Kasir baru harus 4 digit", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPin.equals(confirmPin)) {
                Toast.makeText(this, "Konfirmasi PIN baru tidak cocok", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPin.equals(currentOwnerPin)) {
                Toast.makeText(this, "PIN Kasir tidak boleh sama dengan PIN Owner", Toast.LENGTH_SHORT).show();
                return;
            }

            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("kasir_pin", newPin);

            SupabaseClient.patch("outlets?id=eq." + sessionManager.getOutletId(), patchBody.toString(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Gagal ubah PIN Kasir", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        currentKasirPin = newPin;
                        dialog.dismiss();
                        Toast.makeText(ProfileActivity.this, "PIN Kasir berhasil diperbarui!", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        dialog.show();
    }

    private void showChangeOwnerPinDialog() {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout container = createDialogCardContainer(density);
        container.addView(createDialogTitle("Ubah PIN Owner"));

        container.addView(createFieldLabel("PIN Owner Baru (4 digit)", density, 14));
        EditText etNewOwnerPin = new EditText(this);
        etNewOwnerPin.setHint("Masukkan 4 digit PIN baru");
        etNewOwnerPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etNewOwnerPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        styleDialogEditText(etNewOwnerPin, density);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.topMargin = (int) (5 * density);
        etNewOwnerPin.setLayoutParams(lp1);
        container.addView(etNewOwnerPin);

        container.addView(createFieldLabel("Konfirmasi PIN Owner Baru", density, 12));
        EditText etConfirmOwnerPin = new EditText(this);
        etConfirmOwnerPin.setHint("Ulangi 4 digit PIN baru");
        etConfirmOwnerPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etConfirmOwnerPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        styleDialogEditText(etConfirmOwnerPin, density);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = (int) (5 * density);
        etConfirmOwnerPin.setLayoutParams(lp2);
        container.addView(etConfirmOwnerPin);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        container.addView(createDialogButtonsRow(density, "Simpan PIN", dialog::dismiss, () -> {
            String newPin = etNewOwnerPin.getText().toString().trim();
            String confirmPin = etConfirmOwnerPin.getText().toString().trim();

            if (newPin.length() != 4) {
                Toast.makeText(this, "PIN Owner baru harus 4 digit", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPin.equals(confirmPin)) {
                Toast.makeText(this, "Konfirmasi PIN Owner tidak cocok", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPin.equals(currentKasirPin)) {
                Toast.makeText(this, "PIN Owner tidak boleh sama dengan PIN Kasir", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("owner_pin", newPin);

            SupabaseClient.patch("outlets?id=eq." + sessionManager.getOutletId(), patchBody.toString(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Gagal ubah PIN Owner", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            currentOwnerPin = newPin;
                            sessionManager.setOwnerPin(newPin);
                            Toast.makeText(ProfileActivity.this, "PIN Owner berhasil diperbarui!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ProfileActivity.this, "Gagal simpan PIN Owner (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }));

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

            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("outlet_name", newName);
            patchBody.addProperty("owner_phone", newPhone);
            patchBody.addProperty("address", newAddr);

            if (pendingOutletBitmap != null) {
                String encodedAvatar = sessionManager.encodeAndSaveOutletPhoto(pendingOutletBitmap);
                loadProfileAvatar();
                if (!encodedAvatar.isEmpty()) {
                    patchBody.addProperty("avatar_base64", encodedAvatar);
                }
            }

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
                        Toast.makeText(ProfileActivity.this, "Profil & foto outlet diperbarui!", Toast.LENGTH_SHORT).show();
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

    private int getInt(JsonObject obj, String key, int fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : fallback;
    }
}