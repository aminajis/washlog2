package com.washlog.washlog2;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_HOME_TX_DISPLAY = 10;

    private SessionManager sessionManager;
    private ImageView ivOutletAvatar;
    private TextView tvOutletAvatarFallback;
    private TextView tvHaloOutlet, tvCurrentDateTime;
    private TextView tvTotalMotor, tvTargetBadge;
    private TextView tvTotalOmset, tvOmsetBreakdown, tvQrisBadge;
    private TextView tvTotalKomisi, tvActiveWasherSub, tvKomisiBadge;
    private TextView tvSeeAllTx;
    private ProgressBar pbHome;
    private LinearLayout cardEmptyTx, llTransactionList;

    private int commissionPct = 40;
    private double lastTotalOmset = 0;
    private final Map<String, String> workerIdToName = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        View topBar = findViewById(R.id.topBarMain);
        View bottomNav = findViewById(R.id.bottomNavMain);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);
        int botPadH = (int) (8 * density);
        int botPadV = (int) (8 * density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootMain), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            bottomNav.setPadding(botPadH, botPadV, botPadH, botPadV + sysBars.bottom);
            return insets;
        });

        ivOutletAvatar = findViewById(R.id.ivOutletAvatar);
        tvOutletAvatarFallback = findViewById(R.id.tvOutletAvatarFallback);
        tvHaloOutlet = findViewById(R.id.tvHaloOutlet);
        tvCurrentDateTime = findViewById(R.id.tvCurrentDateTime);
        tvTotalMotor = findViewById(R.id.tvTotalMotor);
        tvTargetBadge = findViewById(R.id.tvTargetBadge);
        tvTotalOmset = findViewById(R.id.tvTotalOmset);
        tvOmsetBreakdown = findViewById(R.id.tvOmsetBreakdown);
        tvQrisBadge = findViewById(R.id.tvQrisBadge);
        tvTotalKomisi = findViewById(R.id.tvTotalKomisi);
        tvActiveWasherSub = findViewById(R.id.tvActiveWasherSub);
        tvKomisiBadge = findViewById(R.id.tvKomisiBadge);
        tvSeeAllTx = findViewById(R.id.tvSeeAllTx);
        pbHome = findViewById(R.id.pbHome);
        cardEmptyTx = findViewById(R.id.cardEmptyTx);
        llTransactionList = findViewById(R.id.llTransactionList);

        String savedName = sessionManager.getOutletName();
        tvHaloOutlet.setText("Halo, " + (savedName.isEmpty() ? "Kasir WashLog" : savedName) + "!");

        findViewById(R.id.btnNewTransaction).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AddTransactionActivity.class)));

        View.OnClickListener goExpense = v ->
                startActivity(new Intent(MainActivity.this, ExpenseActivity.class));
        findViewById(R.id.btnAddExpense).setOnClickListener(goExpense);
        findViewById(R.id.navExpense).setOnClickListener(goExpense);

        View.OnClickListener goReport = v ->
                startActivity(new Intent(MainActivity.this, ReportActivity.class));
        tvSeeAllTx.setOnClickListener(goReport);
        findViewById(R.id.navReport).setOnClickListener(goReport);

        View.OnClickListener goProfile = v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        findViewById(R.id.navProfile).setOnClickListener(goProfile);
        findViewById(R.id.btnTopProfile).setOnClickListener(goProfile);
        findViewById(R.id.btnHeaderAvatar).setOnClickListener(goProfile);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String savedName = sessionManager.getOutletName();
        if (!savedName.isEmpty()) {
            tvHaloOutlet.setText("Halo, " + savedName + "!");
        }
        loadOutletAvatar();

        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm 'WIB'", new Locale("id", "ID"));
        tvCurrentDateTime.setText(sdf.format(new Date()));

        loadDashboardData();
    }

    private void loadOutletAvatar() {
        Bitmap photo = sessionManager.getOutletPhoto();
        if (photo != null) {
            ivOutletAvatar.setImageBitmap(photo);
            ivOutletAvatar.setVisibility(View.VISIBLE);
            tvOutletAvatarFallback.setVisibility(View.GONE);
        } else {
            ivOutletAvatar.setVisibility(View.GONE);
            tvOutletAvatarFallback.setVisibility(View.VISIBLE);
        }
    }

    private void loadDashboardData() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        pbHome.setVisibility(View.VISIBLE);

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
                        String name = getStr(obj, "outlet_name", sessionManager.getOutletName());
                        String phone = getStr(obj, "owner_phone", sessionManager.getOwnerPhone());
                        sessionManager.updateOutletInfo(name, phone);

                        commissionPct = getInt(obj, "worker_commission_pct", 40);
                        tvHaloOutlet.setText("Halo, " + name + "!");
                        tvKomisiBadge.setText(commissionPct + "% Alokasi Omset");
                        updateCommissionDisplay();
                    }
                });
            }
        });

        SupabaseClient.get("workers?outlet_id=eq." + outletId + "&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                fetchTransactions(outletId);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                        workerIdToName.clear();
                        int activeCount = 0;
                        for (JsonElement el : arr) {
                            JsonObject w = el.getAsJsonObject();
                            String wId = getStr(w, "id", "");
                            String wName = getStr(w, "name", getStr(w, "worker_name", ""));
                            if (!wId.isEmpty() && !wName.isEmpty()) {
                                workerIdToName.put(wId, wName);
                            }
                            if (!w.has("is_active") || w.get("is_active").isJsonNull() || w.get("is_active").getAsBoolean()) {
                                activeCount++;
                            }
                        }
                        tvActiveWasherSub.setText("Untuk " + activeCount + " Washer aktif");
                    });
                }
                fetchTransactions(outletId);
            }
        });
    }

    private void fetchTransactions(String outletId) {
        SupabaseClient.get("transactions?outlet_id=eq." + outletId + "&order=created_at.desc&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> pbHome.setVisibility(View.GONE));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                runOnUiThread(() -> {
                    pbHome.setVisibility(View.GONE);
                    if (!response.isSuccessful()) return;

                    JsonArray txArray = JsonParser.parseString(body).getAsJsonArray();
                    renderTransactionsAndMetrics(txArray);
                });
            }
        });
    }

    private String resolveWasherName(JsonObject tx, String platNo) {
        String w = getStr(tx, "washer_name", getStr(tx, "worker_name", ""));
        if (!w.isEmpty()) return w;
        String wId = getStr(tx, "worker_id", "");
        if (!wId.isEmpty() && workerIdToName.containsKey(wId)) {
            return workerIdToName.get(wId);
        }
        if (platNo.contains("•")) {
            String[] parts = platNo.split("•");
            if (parts.length > 1) return parts[1].trim();
        }
        return "Kasir";
    }

    private void renderTransactionsAndMetrics(JsonArray txArray) {
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<JsonObject> todayTxList = new ArrayList<>();

        double totalOmset = 0;
        double totalTunai = 0;
        double totalQris = 0;

        for (int i = 0; i < txArray.size(); i++) {
            JsonObject tx = txArray.get(i).getAsJsonObject();
            String txDateKey = extractLocalDateKey(tx);
            if (!txDateKey.equals(todayKey)) continue;

            todayTxList.add(tx);
            double price = getDouble(tx, "price", 0);
            String payMethod = getStr(tx, "pay_method", getStr(tx, "payment_method", "Tunai"));

            totalOmset += price;
            if (payMethod.toLowerCase(Locale.ROOT).contains("tunai")) {
                totalTunai += price;
            } else if (payMethod.toLowerCase(Locale.ROOT).contains("qris")) {
                totalQris += price;
            }
        }

        int totalMotor = todayTxList.size();
        int displayLimit = Math.min(totalMotor, MAX_HOME_TX_DISPLAY);

        llTransactionList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < displayLimit; i++) {
            JsonObject tx = todayTxList.get(i);
            String motorName = getStr(tx, "motor_name", getStr(tx, "category", "Motor"));
            String category = getStr(tx, "category", motorName);
            String platNo = getStr(tx, "plat_no", getStr(tx, "plate_number", "-"));
            String washerName = resolveWasherName(tx, platNo);
            double price = getDouble(tx, "price", 0);
            String payMethod = getStr(tx, "pay_method", getStr(tx, "payment_method", "Tunai"));
            String time = extractLocalTime(tx);

            View itemView = inflater.inflate(R.layout.item_home_transaction, llTransactionList, false);
            ImageView ivTxIcon = itemView.findViewById(R.id.ivTxIcon);
            TextView tvTxMotorName = itemView.findViewById(R.id.tvTxMotorName);
            TextView tvTxCategory = itemView.findViewById(R.id.tvTxCategory);
            TextView tvTxPlatWasher = itemView.findViewById(R.id.tvTxPlatWasher);
            TextView tvTxPrice = itemView.findViewById(R.id.tvTxPrice);
            TextView tvTxPayMethod = itemView.findViewById(R.id.tvTxPayMethod);
            TextView tvTxTime = itemView.findViewById(R.id.tvTxTime);

            String catLower = category.toLowerCase(Locale.ROOT);
            if (catLower.contains("besar") || catLower.contains("sport") || catLower.contains("moge")) {
                ivTxIcon.setBackgroundResource(R.drawable.bg_btn_navy);
                ivTxIcon.setImageResource(R.drawable.ic_motor_besar);
            } else if (catLower.contains("kecil") || catLower.contains("bebek")) {
                ivTxIcon.setBackgroundResource(R.drawable.bg_banner_blue);
                ivTxIcon.setImageResource(R.drawable.ic_motor_kecil);
            } else {
                ivTxIcon.setBackgroundResource(R.drawable.bg_banner_blue);
                ivTxIcon.setImageResource(R.drawable.ic_motor_sedang);
            }

            tvTxMotorName.setText(motorName);
            tvTxCategory.setText(category);
            String cleanPlat = platNo.contains("•") ? platNo.split("•")[0].trim() : platNo;
            tvTxPlatWasher.setText(cleanPlat + " • " + washerName);
            tvTxPrice.setText(formatRupiah(price));
            tvTxPayMethod.setText(payMethod);
            tvTxTime.setText("🕒 " + time);

            itemView.setOnClickListener(v -> {
                Intent receiptIntent = new Intent(MainActivity.this, ReceiptActivity.class);
                receiptIntent.putExtra(ReceiptActivity.EXTRA_MOTOR_NAME, motorName);
                receiptIntent.putExtra(ReceiptActivity.EXTRA_PLAT_NO, cleanPlat);
                receiptIntent.putExtra(ReceiptActivity.EXTRA_WASHER_NAME, washerName);
                receiptIntent.putExtra(ReceiptActivity.EXTRA_PRICE, price);
                receiptIntent.putExtra(ReceiptActivity.EXTRA_PAY_METHOD, payMethod);
                startActivity(receiptIntent);
            });

            llTransactionList.addView(itemView);
        }

        lastTotalOmset = totalOmset;
        int targetPercent = Math.min((totalMotor * 100) / 25, 100);

        tvTotalMotor.setText(totalMotor + " Unit");
        tvTargetBadge.setText(targetPercent + "% dr Target");
        tvTotalOmset.setText(formatRupiah(totalOmset));
        tvOmsetBreakdown.setText("Tunai: " + formatShortK(totalTunai) + " | QRIS: " + formatShortK(totalQris));
        tvQrisBadge.setText("QRIS: " + formatShortK(totalQris));
        tvSeeAllTx.setText("Semua (" + totalMotor + ") >");
        updateCommissionDisplay();

        cardEmptyTx.setVisibility(totalMotor == 0 ? View.VISIBLE : View.GONE);
    }

    private String extractLocalDateKey(JsonObject obj) {
        String dateCol = getStr(obj, "date", "");
        if (dateCol.length() >= 10) {
            return dateCol.substring(0, 10);
        }
        String createdAt = getStr(obj, "created_at", "");
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
        return createdAt.length() >= 10 ? createdAt.substring(0, 10) : "";
    }

    private String extractLocalTime(JsonObject obj) {
        String createdAt = getStr(obj, "created_at", "");
        if (createdAt.length() >= 19) {
            try {
                SimpleDateFormat isoUtc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                isoUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = isoUtc.parse(createdAt.substring(0, 19));
                if (parsed != null) {
                    return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed);
                }
            } catch (Exception ignored) {}
        }
        String timeCol = getStr(obj, "time", "");
        return !timeCol.isEmpty() ? timeCol : "Hari ini";
    }

    private void updateCommissionDisplay() {
        double totalKomisi = lastTotalOmset * (commissionPct / 100.0);
        tvTotalKomisi.setText(formatRupiah(totalKomisi));
    }

    private String formatRupiah(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        return "Rp " + formatter.format((long) amount);
    }

    private String formatShortK(double amount) {
        long val = (long) amount;
        return val >= 1000 ? ("Rp " + (val / 1000) + "k") : ("Rp " + val);
    }

    private String getStr(JsonObject obj, String key, String fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : fallback;
    }

    private double getDouble(JsonObject obj, String key, double fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsDouble() : fallback;
    }

    private int getInt(JsonObject obj, String key, int fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : fallback;
    }
}