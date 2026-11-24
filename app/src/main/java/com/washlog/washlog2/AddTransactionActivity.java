package com.washlog.washlog2;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import androidx.core.widget.ImageViewCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class AddTransactionActivity extends AppCompatActivity {

    private LinearLayout cardMotorKecil, cardMotorSedang, cardMotorBesar;
    private ImageView ivIconMotorKecil, ivIconMotorSedang, ivIconMotorBesar;
    private TextView tvPriceMotorKecil, tvPriceMotorSedang, tvPriceMotorBesar;

    private LinearLayout llWasherChips;
    private EditText etPlatNo;
    private LinearLayout btnPayTunai, btnPayQris;
    private ImageView ivPayTunai, ivPayQris;
    private TextView tvPayTunai, tvPayQris;

    private TextView tvSplitOpsLabel, tvSplitOpsValue;
    private TextView tvSplitOwnerLabel, tvSplitOwnerValue;
    private TextView tvSplitWorkerLabel, tvSplitWorkerValue;
    private TextView tvSplitTotalCustomer;

    private Button btnSaveTransaction;
    private ProgressBar pbSaveTx;

    private SessionManager sessionManager;
    private double priceKecil = 15000.0;
    private double priceSedang = 20000.0;
    private double priceBesar = 25000.0;

    private String selectedCategory = "Motor Kecil";
    private double categoryPrice = 15000.0;
    private String selectedWasher = "Kasir";
    private String payMethod = "Tunai";
    private int commissionPct = 40;

    private final List<String> washerNames = new ArrayList<>();
    private final Map<String, String> washerNameToId = new LinkedHashMap<>();
    private final Map<String, Integer> washerTodayCount = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        View topBar = findViewById(R.id.topBarAddTx);
        View bottomBar = findViewById(R.id.bottomBarAddTx);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);
        int botPadH = (int) (16 * density);
        int botPadV = (int) (10 * density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootAddTx), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            bottomBar.setPadding(botPadH, botPadV, botPadH, botPadV + sysBars.bottom);
            return insets;
        });

        sessionManager = new SessionManager(this);

        // Muat langsung konfigurasi tarif & komisi terakhir dari SessionManager
        commissionPct = sessionManager.getCommissionPct();
        priceKecil = sessionManager.getPriceSmall();
        priceSedang = sessionManager.getPriceMedium();
        priceBesar = sessionManager.getPriceLarge();

        cardMotorKecil = findViewById(R.id.cardMotorKecil);
        cardMotorSedang = findViewById(R.id.cardMotorSedang);
        cardMotorBesar = findViewById(R.id.cardMotorBesar);

        ivIconMotorKecil = findViewById(R.id.ivIconMotorKecil);
        ivIconMotorSedang = findViewById(R.id.ivIconMotorSedang);
        ivIconMotorBesar = findViewById(R.id.ivIconMotorBesar);

        tvPriceMotorKecil = findViewById(R.id.tvPriceMotorKecil);
        tvPriceMotorSedang = findViewById(R.id.tvPriceMotorSedang);
        tvPriceMotorBesar = findViewById(R.id.tvPriceMotorBesar);

        llWasherChips = findViewById(R.id.llWasherChips);
        etPlatNo = findViewById(R.id.etPlatNo);
        btnPayTunai = findViewById(R.id.btnPayTunai);
        btnPayQris = findViewById(R.id.btnPayQris);
        ivPayTunai = findViewById(R.id.ivPayTunai);
        ivPayQris = findViewById(R.id.ivPayQris);
        tvPayTunai = findViewById(R.id.tvPayTunai);
        tvPayQris = findViewById(R.id.tvPayQris);

        tvSplitOpsLabel = findViewById(R.id.tvSplitOpsLabel);
        tvSplitOpsValue = findViewById(R.id.tvSplitOpsValue);
        tvSplitOwnerLabel = findViewById(R.id.tvSplitOwnerLabel);
        tvSplitOwnerValue = findViewById(R.id.tvSplitOwnerValue);
        tvSplitWorkerLabel = findViewById(R.id.tvSplitWorkerLabel);
        tvSplitWorkerValue = findViewById(R.id.tvSplitWorkerValue);
        tvSplitTotalCustomer = findViewById(R.id.tvSplitTotalCustomer);

        btnSaveTransaction = findViewById(R.id.btnSaveTransaction);
        pbSaveTx = findViewById(R.id.pbSaveTx);

        etPlatNo.setFilters(new InputFilter[]{new InputFilter.AllCaps()});

        findViewById(R.id.btnBackAddTx).setOnClickListener(v -> finish());

        cardMotorKecil.setOnClickListener(v -> selectCategory("Motor Kecil", priceKecil));
        cardMotorSedang.setOnClickListener(v -> selectCategory("Motor Sedang", priceSedang));
        cardMotorBesar.setOnClickListener(v -> selectCategory("Motor Besar", priceBesar));

        btnPayTunai.setOnClickListener(v -> selectPayMethod("Tunai"));
        btnPayQris.setOnClickListener(v -> selectPayMethod("QRIS"));

        btnSaveTransaction.setOnClickListener(v -> simpanTransaksi());

        refreshPriceLabels();
        selectCategory("Motor Kecil", priceKecil);
        washerNames.add("Memuat...");
        renderWasherChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOutletConfig();
        loadActiveWashersAndCounts();
    }

    private void refreshPriceLabels() {
        tvPriceMotorKecil.setText(formatRupiah(priceKecil));
        tvPriceMotorSedang.setText(formatRupiah(priceSedang));
        tvPriceMotorBesar.setText(formatRupiah(priceBesar));
    }

    private double getDoubleFromJson(JsonObject obj, String primaryKey, String fallbackKey, double currentVal) {
        if (obj.has(primaryKey) && !obj.get(primaryKey).isJsonNull() && obj.get(primaryKey).getAsDouble() > 0) {
            return obj.get(primaryKey).getAsDouble();
        }
        if (obj.has(fallbackKey) && !obj.get(fallbackKey).isJsonNull() && obj.get(fallbackKey).getAsDouble() > 0) {
            return obj.get(fallbackKey).getAsDouble();
        }
        return currentVal;
    }

    private void loadOutletConfig() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

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
                        if (obj.has("worker_commission_pct") && !obj.get("worker_commission_pct").isJsonNull()) {
                            commissionPct = obj.get("worker_commission_pct").getAsInt();
                        }
                        priceKecil = getDoubleFromJson(obj, "price_small", "price_kecil", priceKecil);
                        priceSedang = getDoubleFromJson(obj, "price_medium", "price_sedang", priceSedang);
                        priceBesar = getDoubleFromJson(obj, "price_large", "price_besar", priceBesar);

                        sessionManager.setCommissionPct(commissionPct);
                        sessionManager.setMotorPrices((int) priceKecil, (int) priceSedang, (int) priceBesar);

                        refreshPriceLabels();

                        if ("Motor Kecil".equals(selectedCategory)) {
                            selectCategory("Motor Kecil", priceKecil);
                        } else if ("Motor Sedang".equals(selectedCategory)) {
                            selectCategory("Motor Sedang", priceSedang);
                        } else {
                            selectCategory("Motor Besar", priceBesar);
                        }
                    }
                });
            }
        });
    }

    private void selectCategory(String category, double price) {
        selectedCategory = category;
        categoryPrice = price;

        boolean isKecil = "Motor Kecil".equals(category);
        boolean isSedang = "Motor Sedang".equals(category);
        boolean isBesar = "Motor Besar".equals(category);

        styleCategoryCard(cardMotorKecil, ivIconMotorKecil, tvPriceMotorKecil, isKecil);
        styleCategoryCard(cardMotorSedang, ivIconMotorSedang, tvPriceMotorSedang, isSedang);
        styleCategoryCard(cardMotorBesar, ivIconMotorBesar, tvPriceMotorBesar, isBesar);

        updateCalculationCard();
    }

    private void styleCategoryCard(LinearLayout card, ImageView icon, TextView priceView, boolean selected) {
        card.setBackgroundResource(selected ? R.drawable.bg_option_selected : R.drawable.bg_option_unselected);
        icon.setBackgroundResource(R.drawable.bg_pill_blue);
        icon.setBackgroundTintList(selected
                ? ColorStateList.valueOf(ContextCompat.getColor(this, R.color.royal_blue))
                : null);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
                ContextCompat.getColor(this, selected ? R.color.white : R.color.deep_slate_navy)));
        priceView.setTextColor(ContextCompat.getColor(this, selected ? R.color.royal_blue : R.color.deep_slate_navy));
    }

    private void updateCalculationCard() {
        int workerPct = Math.max(0, Math.min(100, commissionPct));
        int remPct = Math.max(0, 100 - workerPct);
        int ownerPct = Math.min(20, remPct);
        int opsPct = Math.max(0, remPct - ownerPct);

        double workerAmt = categoryPrice * (workerPct / 100.0);
        double ownerAmt = categoryPrice * (ownerPct / 100.0);
        double opsAmt = categoryPrice - workerAmt - ownerAmt;

        tvSplitOpsLabel.setText("Biaya Operasional Steam (" + opsPct + "%)");
        tvSplitOpsValue.setText(formatRupiah(opsAmt));

        tvSplitOwnerLabel.setText("Hak Pemilik Usaha (" + ownerPct + "%)");
        tvSplitOwnerValue.setText(formatRupiah(ownerAmt));

        tvSplitWorkerLabel.setText("Komisi Worker (" + selectedWasher + " • " + workerPct + "%)");
        tvSplitWorkerValue.setText(formatRupiah(workerAmt));

        String formattedTotal = formatRupiah(categoryPrice);
        tvSplitTotalCustomer.setText(formattedTotal);
        btnSaveTransaction.setText("✓  Simpan Transaksi (" + formattedTotal + ")");
    }

    private void selectPayMethod(String method) {
        payMethod = method;
        boolean isTunai = "Tunai".equals(method);

        btnPayTunai.setBackgroundResource(isTunai ? R.drawable.bg_pay_selected : R.drawable.bg_pay_unselected);
        tvPayTunai.setTextColor(ContextCompat.getColor(this, isTunai ? R.color.white : R.color.deep_slate_navy));
        ImageViewCompat.setImageTintList(ivPayTunai, ColorStateList.valueOf(
                ContextCompat.getColor(this, isTunai ? R.color.white : R.color.deep_slate_navy)));

        btnPayQris.setBackgroundResource(!isTunai ? R.drawable.bg_pay_selected : R.drawable.bg_pay_unselected);
        tvPayQris.setTextColor(ContextCompat.getColor(this, !isTunai ? R.color.white : R.color.deep_slate_navy));
        ImageViewCompat.setImageTintList(ivPayQris, ColorStateList.valueOf(
                ContextCompat.getColor(this, !isTunai ? R.color.white : R.color.deep_slate_navy)));
    }

    private void loadActiveWashersAndCounts() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        SupabaseClient.get("workers?outlet_id=eq." + outletId + "&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    washerNames.clear();
                    washerNames.add("Kasir");
                    selectedWasher = "Kasir";
                    updateCalculationCard();
                    renderWasherChips();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                runOnUiThread(() -> {
                    if (!response.isSuccessful()) {
                        washerNames.clear();
                        washerNames.add("Kasir");
                        selectedWasher = "Kasir";
                        updateCalculationCard();
                        renderWasherChips();
                        return;
                    }

                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    List<String> fetched = new ArrayList<>();
                    washerNameToId.clear();

                    for (JsonElement el : arr) {
                        JsonObject w = el.getAsJsonObject();
                        boolean isActive = !w.has("is_active") || w.get("is_active").isJsonNull() || w.get("is_active").getAsBoolean();
                        String wId = (w.has("id") && !w.get("id").isJsonNull()) ? w.get("id").getAsString() : "";
                        String wName = "";
                        if (w.has("name") && !w.get("name").isJsonNull()) {
                            wName = w.get("name").getAsString().trim();
                        } else if (w.has("worker_name") && !w.get("worker_name").isJsonNull()) {
                            wName = w.get("worker_name").getAsString().trim();
                        }
                        if (isActive && !wName.isEmpty()) {
                            fetched.add(wName);
                            if (!wId.isEmpty()) {
                                washerNameToId.put(wName, wId);
                            }
                        }
                    }

                    washerNames.clear();
                    if (!fetched.isEmpty()) {
                        washerNames.addAll(fetched);
                        if (!washerNames.contains(selectedWasher)) {
                            selectedWasher = washerNames.get(0);
                        }
                    } else {
                        washerNames.add("Kasir");
                        selectedWasher = "Kasir";
                    }
                    updateCalculationCard();
                    renderWasherChips();
                    loadTodayWorkerStats(outletId);
                });
            }
        });
    }

    private void loadTodayWorkerStats(String outletId) {
        String todayPrefix = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SupabaseClient.get("transactions?outlet_id=eq." + outletId + "&select=worker_name,washer_name,created_at,date", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    washerTodayCount.clear();
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject tx = el.getAsJsonObject();
                        String dKey = extractLocalDateKey(tx);
                        if (!dKey.startsWith(todayPrefix)) continue;

                        String wName = "";
                        if (tx.has("worker_name") && !tx.get("worker_name").isJsonNull()) {
                            wName = tx.get("worker_name").getAsString().trim();
                        } else if (tx.has("washer_name") && !tx.get("washer_name").isJsonNull()) {
                            wName = tx.get("washer_name").getAsString().trim();
                        }
                        if (!wName.isEmpty()) {
                            washerTodayCount.put(wName, washerTodayCount.getOrDefault(wName, 0) + 1);
                        }
                    }
                    renderWasherChips();
                });
            }
        });
    }

    private void renderWasherChips() {
        llWasherChips.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int cardWidth = (int) (104 * density);
        int padH = (int) (10 * density);
        int padV = (int) (12 * density);
        int marginEnd = (int) (10 * density);
        int avatarSize = (int) (42 * density);
        int checkSize = (int) (16 * density);

        for (String name : washerNames) {
            boolean isSelected = name.equals(selectedWasher);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(padH, padV, padH, padV);
            card.setBackgroundResource(isSelected ? R.drawable.bg_option_selected : R.drawable.bg_card_white);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    cardWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.setMarginEnd(marginEnd);
            card.setLayoutParams(cardLp);

            FrameLayout avatarFrame = new FrameLayout(this);
            LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
            avatarFrame.setLayoutParams(frameLp);

            TextView tvInitial = new TextView(this);
            tvInitial.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            tvInitial.setBackgroundResource(R.drawable.bg_pill_blue);
            if (isSelected) {
                tvInitial.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.royal_blue)));
            }
            tvInitial.setGravity(Gravity.CENTER);
            String initial = name.isEmpty() ? "W" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            tvInitial.setText(initial);
            tvInitial.setTextColor(ContextCompat.getColor(this, isSelected ? R.color.white : R.color.deep_slate_navy));
            tvInitial.setTextSize(15f);
            tvInitial.setTypeface(null, Typeface.BOLD);
            avatarFrame.addView(tvInitial);

            if (isSelected) {
                TextView tvCheck = new TextView(this);
                FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(checkSize, checkSize, Gravity.BOTTOM | Gravity.END);
                tvCheck.setLayoutParams(checkLp);
                tvCheck.setBackgroundResource(R.drawable.bg_pill_green);
                tvCheck.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.emerald_green)));
                tvCheck.setGravity(Gravity.CENTER);
                tvCheck.setText("✓");
                tvCheck.setTextColor(ContextCompat.getColor(this, R.color.white));
                tvCheck.setTextSize(9f);
                tvCheck.setTypeface(null, Typeface.BOLD);
                avatarFrame.addView(tvCheck);
            }

            TextView tvName = new TextView(this);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            nameLp.topMargin = (int) (8 * density);
            tvName.setLayoutParams(nameLp);
            tvName.setText(name);
            tvName.setMaxLines(1);
            tvName.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
            tvName.setTextSize(12.5f);
            tvName.setTypeface(null, Typeface.BOLD);

            int count = washerTodayCount.getOrDefault(name, 0);
            TextView tvCount = new TextView(this);
            tvCount.setText(count + " motor");
            tvCount.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            tvCount.setTextSize(10f);

            card.addView(avatarFrame);
            card.addView(tvName);
            card.addView(tvCount);

            card.setOnClickListener(v -> {
                selectedWasher = name;
                updateCalculationCard();
                renderWasherChips();
            });

            llWasherChips.addView(card);
        }
    }

    private void simpanTransaksi() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) {
            Toast.makeText(this, "Sesi outlet tidak ditemukan, silakan login ulang", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawPlat = etPlatNo.getText().toString().trim().toUpperCase(Locale.ROOT);
        String motorPlat = rawPlat.isEmpty() ? "Motor Non-Plat" : rawPlat;

        JsonObject payload = new JsonObject();
        payload.addProperty("outlet_id", outletId);
        payload.addProperty("motor_name", selectedCategory);
        payload.addProperty("category", selectedCategory);
        payload.addProperty("plate_number", motorPlat);
        payload.addProperty("plat_no", motorPlat);
        payload.addProperty("worker_name", selectedWasher);
        payload.addProperty("washer_name", selectedWasher);
        payload.addProperty("price", (long) categoryPrice);
        payload.addProperty("payment_method", payMethod);
        payload.addProperty("pay_method", payMethod);

        setLoading(true);
        postTransactionWithFallback(payload, motorPlat, 0);
    }

    private void postTransactionWithFallback(JsonObject payload, String motorPlat, int attempt) {
        SupabaseClient.post("transactions", payload.toString(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    new AlertDialog.Builder(AddTransactionActivity.this)
                            .setTitle("Gagal Koneksi")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String errBody = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        setLoading(false);
                        Intent receiptIntent = new Intent(AddTransactionActivity.this, ReceiptActivity.class);
                        receiptIntent.putExtra(ReceiptActivity.EXTRA_MOTOR_NAME, selectedCategory);
                        receiptIntent.putExtra(ReceiptActivity.EXTRA_PLAT_NO, motorPlat);
                        receiptIntent.putExtra(ReceiptActivity.EXTRA_WASHER_NAME, selectedWasher);
                        receiptIntent.putExtra(ReceiptActivity.EXTRA_PRICE, categoryPrice);
                        receiptIntent.putExtra(ReceiptActivity.EXTRA_PAY_METHOD, payMethod);
                        startActivity(receiptIntent);
                        finish();
                        return;
                    }

                    if (attempt < 10) {
                        JsonObject retry = payload.deepCopy();

                        if (errBody.contains("PGRST204")) {
                            String marker = "Could not find the '";
                            int startIdx = errBody.indexOf(marker);
                            if (startIdx != -1) {
                                int colStart = startIdx + marker.length();
                                int colEnd = errBody.indexOf("'", colStart);
                                if (colEnd > colStart) {
                                    String unknownCol = errBody.substring(colStart, colEnd);
                                    if (retry.has(unknownCol)) {
                                        retry.remove(unknownCol);
                                        postTransactionWithFallback(retry, motorPlat, attempt + 1);
                                        return;
                                    }
                                }
                            }
                        }

                        if (errBody.contains("23502")) {
                            String missingCol = extractNotNullColumn(errBody);
                            if (!missingCol.isEmpty() && !retry.has(missingCol)) {
                                fillMissingRequiredColumn(retry, missingCol, motorPlat);
                                postTransactionWithFallback(retry, motorPlat, attempt + 1);
                                return;
                            }
                        }
                    }

                    setLoading(false);
                    new AlertDialog.Builder(AddTransactionActivity.this)
                            .setTitle("Error Simpan Transaksi (" + response.code() + ")")
                            .setMessage(errBody)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private String extractNotNullColumn(String errBody) {
        String escapedMarker = "null value in column \\\"";
        int startIdx = errBody.indexOf(escapedMarker);
        if (startIdx != -1) {
            int colStart = startIdx + escapedMarker.length();
            int colEnd = errBody.indexOf("\\\"", colStart);
            if (colEnd > colStart) {
                return errBody.substring(colStart, colEnd);
            }
        }
        String plainMarker = "null value in column \"";
        startIdx = errBody.indexOf(plainMarker);
        if (startIdx != -1) {
            int colStart = startIdx + plainMarker.length();
            int colEnd = errBody.indexOf("\"", colStart);
            if (colEnd > colStart) {
                return errBody.substring(colStart, colEnd);
            }
        }
        return "";
    }

    private void fillMissingRequiredColumn(JsonObject retry, String col, String motorPlat) {
        String lower = col.toLowerCase(Locale.ROOT);
        if (lower.contains("worker_id") || lower.contains("washer_id")) {
            String wId = washerNameToId.get(selectedWasher);
            if (wId != null && !wId.isEmpty()) {
                retry.addProperty(col, wId);
            }
        } else if (lower.contains("worker") || lower.contains("washer")) {
            retry.addProperty(col, selectedWasher);
        } else if (lower.contains("pay")) {
            retry.addProperty(col, payMethod);
        } else if (lower.contains("plat")) {
            retry.addProperty(col, motorPlat);
        } else if (lower.contains("price") || lower.contains("amount") || lower.contains("total")) {
            retry.addProperty(col, (long) categoryPrice);
        } else if (lower.contains("time")) {
            String nowTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            retry.addProperty(col, nowTime);
        } else if (lower.contains("date")) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            retry.addProperty(col, today);
        } else {
            retry.addProperty(col, selectedCategory);
        }
    }

    private String extractLocalDateKey(JsonObject obj) {
        if (obj.has("date") && !obj.get("date").isJsonNull()) {
            String d = obj.get("date").getAsString();
            if (d.length() >= 10) return d.substring(0, 10);
        }
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            String createdAt = obj.get("created_at").getAsString();
            if (createdAt.length() >= 19) {
                try {
                    SimpleDateFormat isoUtc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    isoUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date parsed = isoUtc.parse(createdAt.substring(0, 19));
                    if (parsed != null) {
                        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed);
                    }
                } catch (Exception ignored) {}
            }
            if (createdAt.length() >= 10) return createdAt.substring(0, 10);
        }
        return "";
    }

    private String formatRupiah(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        return "Rp " + formatter.format((long) amount);
    }

    private void setLoading(boolean loading) {
        pbSaveTx.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveTransaction.setEnabled(!loading);
    }
}