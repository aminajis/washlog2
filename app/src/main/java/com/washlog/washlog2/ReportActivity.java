package com.washlog.washlog2;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import java.util.Calendar;
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

public class ReportActivity extends AppCompatActivity {

    private static final int INITIAL_TX_DISPLAY = 5;
    private static final int TX_PAGE_STEP = 10;

    private SessionManager sessionManager;

    private TextView tabHarian, tabBulanan, tvActiveDateLabel;
    private TextView tvReportOmset, tvReportMotorCount;
    private TextView tvReportExpense, tvReportReceiptCount;
    private TextView tvReportNetProfit;
    private TextView tvReportPetugasBadge, tvReportTotalCommission, tvReportCommissionFormula;
    private TextView tvTeamCommHeaderTitle, tvTeamWorkerCount;
    private LinearLayout llWorkerCommissionList;

    private TextView tvAllTxHeaderTitle, tvAllTxCountBadge;
    private LinearLayout llReportTransactionList, llTxPaginationBar;
    private TextView btnLoadMoreTx, btnCollapseTx;

    private boolean isDaily = true;
    private int dateOffset = 0;
    private int commissionPct = 40;
    private int visibleTxLimit = INITIAL_TX_DISPLAY;

    private JsonArray allTransactions = new JsonArray();
    private JsonArray allExpenses = new JsonArray();
    private JsonArray allWorkers = new JsonArray();

    private String lastActiveDateLabel = "";
    private String lastOmsetText = "Rp 0";
    private String lastMotorCountText = "0 motor selesai";
    private String lastExpenseText = "Rp 0";
    private String lastTotalCommissionText = "Rp 0";
    private String lastNetProfitText = "Rp 0";
    private int lastWorkerCount = 0;

    private static class WorkerStat {
        String name;
        int motorCount = 0;
        double omset = 0;
        final Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        WorkerStat(String name) {
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        sessionManager = new SessionManager(this);

        View topBar = findViewById(R.id.topBarReport);
        View bottomNav = findViewById(R.id.bottomNavReport);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);
        int botPadH = (int) (8 * density);
        int botPadV = (int) (8 * density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootReport), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            bottomNav.setPadding(botPadH, botPadV, botPadH, botPadV + sysBars.bottom);
            return insets;
        });

        tabHarian = findViewById(R.id.tabHarian);
        tabBulanan = findViewById(R.id.tabBulanan);
        tvActiveDateLabel = findViewById(R.id.tvActiveDateLabel);

        tvReportOmset = findViewById(R.id.tvReportOmset);
        tvReportMotorCount = findViewById(R.id.tvReportMotorCount);
        tvReportExpense = findViewById(R.id.tvReportExpense);
        tvReportReceiptCount = findViewById(R.id.tvReportReceiptCount);
        tvReportNetProfit = findViewById(R.id.tvReportNetProfit);
        tvReportPetugasBadge = findViewById(R.id.tvReportPetugasBadge);
        tvReportTotalCommission = findViewById(R.id.tvReportTotalCommission);
        tvReportCommissionFormula = findViewById(R.id.tvReportCommissionFormula);
        tvTeamCommHeaderTitle = findViewById(R.id.tvTeamCommHeaderTitle);
        tvTeamWorkerCount = findViewById(R.id.tvTeamWorkerCount);
        llWorkerCommissionList = findViewById(R.id.llWorkerCommissionList);

        tvAllTxHeaderTitle = findViewById(R.id.tvAllTxHeaderTitle);
        tvAllTxCountBadge = findViewById(R.id.tvAllTxCountBadge);
        llReportTransactionList = findViewById(R.id.llReportTransactionList);
        llTxPaginationBar = findViewById(R.id.llTxPaginationBar);
        btnLoadMoreTx = findViewById(R.id.btnLoadMoreTx);
        btnCollapseTx = findViewById(R.id.btnCollapseTx);

        findViewById(R.id.btnBackReport).setOnClickListener(v -> finish());
        findViewById(R.id.navHomeRep).setOnClickListener(v -> finish());

        findViewById(R.id.navExpenseRep).setOnClickListener(v -> {
            startActivity(new Intent(ReportActivity.this, ExpenseActivity.class));
            finish();
        });

        View.OnClickListener goProfile = v -> {
            startActivity(new Intent(ReportActivity.this, ProfileActivity.class));
            finish();
        };
        findViewById(R.id.btnTopProfileRep).setOnClickListener(goProfile);
        findViewById(R.id.navProfileRep).setOnClickListener(goProfile);

        tabHarian.setOnClickListener(v -> {
            if (!isDaily) {
                isDaily = true;
                dateOffset = 0;
                visibleTxLimit = INITIAL_TX_DISPLAY;
                updatePeriodTabsUI();
                recalculateAndRender();
            }
        });

        tabBulanan.setOnClickListener(v -> {
            if (isDaily) {
                isDaily = false;
                dateOffset = 0;
                visibleTxLimit = INITIAL_TX_DISPLAY;
                updatePeriodTabsUI();
                recalculateAndRender();
            }
        });

        findViewById(R.id.btnPrevDate).setOnClickListener(v -> {
            dateOffset += 1;
            visibleTxLimit = INITIAL_TX_DISPLAY;
            recalculateAndRender();
        });

        findViewById(R.id.btnNextDate).setOnClickListener(v -> {
            if (dateOffset > 0) {
                dateOffset -= 1;
                visibleTxLimit = INITIAL_TX_DISPLAY;
                recalculateAndRender();
            }
        });

        btnLoadMoreTx.setOnClickListener(v -> {
            visibleTxLimit += TX_PAGE_STEP;
            recalculateAndRender();
        });

        btnCollapseTx.setOnClickListener(v -> {
            visibleTxLimit = INITIAL_TX_DISPLAY;
            recalculateAndRender();
        });

        findViewById(R.id.btnExportPdfReport).setOnClickListener(v -> {
            Intent intent = new Intent(ReportActivity.this, PdfReportActivity.class);
            intent.putExtra(PdfReportActivity.EXTRA_PERIOD_TYPE, isDaily ? "Harian" : "Bulanan");
            intent.putExtra(PdfReportActivity.EXTRA_DATE_LABEL, lastActiveDateLabel);
            intent.putExtra(PdfReportActivity.EXTRA_OMSET, lastOmsetText);
            intent.putExtra(PdfReportActivity.EXTRA_EXPENSE, lastExpenseText);
            intent.putExtra(PdfReportActivity.EXTRA_COMMISSION, lastTotalCommissionText);
            intent.putExtra(PdfReportActivity.EXTRA_NET_PROFIT, lastNetProfitText);
            startActivity(intent);
        });

        findViewById(R.id.btnShareWaReport).setOnClickListener(v -> shareReportToWhatsApp());

        updatePeriodTabsUI();
        recalculateAndRender();
        loadReportData();
    }

    private void updatePeriodTabsUI() {
        if (isDaily) {
            tabHarian.setBackgroundResource(R.drawable.bg_tab_selected);
            tabHarian.setText("✓  Harian");
            tabHarian.setTextColor(ContextCompat.getColor(this, R.color.royal_blue));
            tabHarian.setTypeface(null, Typeface.BOLD);

            tabBulanan.setBackground(null);
            tabBulanan.setText("Bulanan");
            tabBulanan.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            tabBulanan.setTypeface(null, Typeface.NORMAL);
        } else {
            tabBulanan.setBackgroundResource(R.drawable.bg_tab_selected);
            tabBulanan.setText("✓  Bulanan");
            tabBulanan.setTextColor(ContextCompat.getColor(this, R.color.royal_blue));
            tabBulanan.setTypeface(null, Typeface.BOLD);

            tabHarian.setBackground(null);
            tabHarian.setText("Harian");
            tabHarian.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            tabHarian.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void loadReportData() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        SupabaseClient.get("outlets?id=eq." + outletId + "&select=worker_commission_pct", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    if (!arr.isEmpty()) {
                        commissionPct = getInt(arr.get(0).getAsJsonObject(), "worker_commission_pct", 40);
                        recalculateAndRender();
                    }
                });
            }
        });

        SupabaseClient.get("workers?outlet_id=eq." + outletId + "&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    allWorkers = JsonParser.parseString(body).getAsJsonArray();
                    recalculateAndRender();
                });
            }
        });

        SupabaseClient.get("transactions?outlet_id=eq." + outletId + "&order=created_at.desc&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    allTransactions = JsonParser.parseString(body).getAsJsonArray();
                    recalculateAndRender();
                });
            }
        });

        SupabaseClient.get("expenses?outlet_id=eq." + outletId + "&order=created_at.desc&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    allExpenses = JsonParser.parseString(body).getAsJsonArray();
                    recalculateAndRender();
                });
            }
        });
    }

    private String resolveWasherName(JsonObject tx, Map<String, String> workerIdToName) {
        String w = getStr(tx, "washer_name", getStr(tx, "worker_name", ""));
        if (!w.isEmpty()) return w;
        String wId = getStr(tx, "worker_id", "");
        if (!wId.isEmpty() && workerIdToName.containsKey(wId)) {
            return workerIdToName.get(wId);
        }
        String plat = getStr(tx, "plat_no", getStr(tx, "plate_number", ""));
        if (plat.contains("•")) {
            String[] parts = plat.split("•");
            if (parts.length > 1) return parts[1].trim();
        }
        return "Kasir";
    }

    private void recalculateAndRender() {
        Locale idLocale = new Locale("id", "ID");
        Calendar cal = Calendar.getInstance(idLocale);
        if (isDaily) {
            cal.add(Calendar.DAY_OF_MONTH, -dateOffset);
        } else {
            cal.add(Calendar.MONTH, -dateOffset);
        }

        SimpleDateFormat displayFmt = new SimpleDateFormat(
                isDaily ? "EEEE, dd MMM yyyy" : "MMMM yyyy", idLocale
        );
        SimpleDateFormat filterFmt = new SimpleDateFormat(
                isDaily ? "yyyy-MM-dd" : "yyyy-MM", Locale.US
        );

        String activeDateLabel = displayFmt.format(cal.getTime());
        String filterPrefix = filterFmt.format(cal.getTime());
        tvActiveDateLabel.setText(activeDateLabel);

        Map<String, WorkerStat> workerStatMap = new LinkedHashMap<>();
        Map<String, String> workerIdToName = new HashMap<>();
        for (JsonElement el : allWorkers) {
            JsonObject w = el.getAsJsonObject();
            String wId = getStr(w, "id", "");
            String wName = getStr(w, "name", getStr(w, "worker_name", ""));
            if (!wId.isEmpty() && !wName.isEmpty()) {
                workerIdToName.put(wId, wName);
            }
            if (!wName.isEmpty() && !workerStatMap.containsKey(wName)) {
                workerStatMap.put(wName, new WorkerStat(wName));
            }
        }

        double totalOmset = 0;
        int motorCount = 0;
        List<JsonObject> filteredTxList = new ArrayList<>();

        for (JsonElement el : allTransactions) {
            JsonObject tx = el.getAsJsonObject();
            String dateKey = extractLocalDateKey(tx);
            if (!dateKey.startsWith(filterPrefix)) continue;

            filteredTxList.add(tx);

            double price = getDouble(tx, "price", 0);
            String washerName = resolveWasherName(tx, workerIdToName);
            String motorName = getStr(tx, "motor_name", "Motor Matic");
            String category = getStr(tx, "category", motorName);

            totalOmset += price;
            motorCount++;

            WorkerStat stat = workerStatMap.get(washerName);
            if (stat == null) {
                stat = new WorkerStat(washerName);
                workerStatMap.put(washerName, stat);
            }
            stat.motorCount += 1;
            stat.omset += price;
            Integer prevCat = stat.categoryCounts.get(category);
            stat.categoryCounts.put(category, (prevCat == null ? 0 : prevCat) + 1);
        }

        double totalExpense = 0;
        int receiptCount = 0;

        for (JsonElement el : allExpenses) {
            JsonObject exp = el.getAsJsonObject();
            String dateKey = extractLocalDateKey(exp);
            if (!dateKey.startsWith(filterPrefix)) continue;

            totalExpense += getDouble(exp, "amount", 0);
            receiptCount++;
        }

        double totalCommission = totalOmset * (commissionPct / 100.0);
        double netProfit = totalOmset - totalExpense - totalCommission;

        String omsetText = formatRupiah(totalOmset);
        String motorCountText = motorCount + " motor selesai";
        String expenseText = formatRupiah(totalExpense);
        String receiptCountText = receiptCount + " nota operasional";
        String netProfitText = formatRupiah(netProfit);
        String totalCommissionText = formatRupiah(totalCommission);

        tvReportOmset.setText(omsetText);
        tvReportMotorCount.setText("• " + motorCountText);
        tvReportExpense.setText(expenseText);
        tvReportReceiptCount.setText("• " + receiptCountText);
        tvReportNetProfit.setText(netProfitText);
        tvReportTotalCommission.setText(totalCommissionText);
        tvReportCommissionFormula.setText(commissionPct + "% x " + omsetText + " (Omset)");

        List<WorkerStat> workerList = new ArrayList<>(workerStatMap.values());
        int workerCount = workerList.size();
        tvReportPetugasBadge.setText(workerCount + " Petugas");
        tvTeamCommHeaderTitle.setText(isDaily ? "Rincian Komisi Tim Hari Ini" : "Rincian Komisi Tim Bulanan");
        tvTeamWorkerCount.setText(workerCount + " Pekerja");

        llWorkerCommissionList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (WorkerStat stat : workerList) {
            View item = inflater.inflate(R.layout.item_worker_commission, llWorkerCommissionList, false);
            TextView tvInitial = item.findViewById(R.id.tvWorkerCommInitial);
            TextView tvName = item.findViewById(R.id.tvWorkerCommName);
            TextView tvDetail = item.findViewById(R.id.tvWorkerCommDetail);
            TextView tvAmount = item.findViewById(R.id.tvWorkerCommAmount);

            String initial = stat.name.isEmpty() ? "W" : stat.name.substring(0, 1).toUpperCase(Locale.ROOT);
            double workerComm = stat.omset * (commissionPct / 100.0);

            tvInitial.setText(initial);
            tvName.setText(stat.name);
            tvDetail.setText(buildWorkerDetailText(stat));
            tvAmount.setText(formatRupiah(workerComm));

            llWorkerCommissionList.addView(item);
        }

        // Render Log Buku Kas Cucian dengan Pagination (otomatis ikut filter Harian / Bulanan)
        tvAllTxHeaderTitle.setText(isDaily ? "Semua Transaksi Hari Ini" : "Semua Transaksi Bulan Ini");
        tvAllTxCountBadge.setText(motorCount + " Unit");
        llReportTransactionList.removeAllViews();

        if (filteredTxList.isEmpty()) {
            llTxPaginationBar.setVisibility(View.GONE);
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Belum ada transaksi cucian pada periode ini.");
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            tvEmpty.setTextSize(12f);
            tvEmpty.setGravity(Gravity.CENTER);
            int padV = (int) (16 * getResources().getDisplayMetrics().density);
            tvEmpty.setPadding(0, padV, 0, padV);
            llReportTransactionList.addView(tvEmpty);
        } else {
            int displayCount = Math.min(filteredTxList.size(), visibleTxLimit);
            for (int i = 0; i < displayCount; i++) {
                JsonObject tx = filteredTxList.get(i);
                String motorName = getStr(tx, "motor_name", getStr(tx, "category", "Motor"));
                String category = getStr(tx, "category", motorName);
                String platNo = getStr(tx, "plat_no", getStr(tx, "plate_number", "-"));
                String washerName = resolveWasherName(tx, workerIdToName);
                double price = getDouble(tx, "price", 0);
                String payMethod = getStr(tx, "pay_method", getStr(tx, "payment_method", "Tunai"));
                String timeLabel = isDaily ? extractLocalTime(tx) : (extractLocalDateKey(tx) + " • " + extractLocalTime(tx));

                View row = inflater.inflate(R.layout.item_report_transaction, llReportTransactionList, false);
                ImageView ivRepTxIcon = row.findViewById(R.id.ivRepTxIcon);
                TextView tvRepTxTitle = row.findViewById(R.id.tvRepTxTitle);
                TextView tvRepTxSub = row.findViewById(R.id.tvRepTxSub);
                TextView tvRepTxPrice = row.findViewById(R.id.tvRepTxPrice);
                TextView tvRepTxPay = row.findViewById(R.id.tvRepTxPay);
                View dividerRepTx = row.findViewById(R.id.dividerRepTx);

                String catLower = category.toLowerCase(Locale.ROOT);
                if (catLower.contains("besar") || catLower.contains("sport") || catLower.contains("moge")) {
                    ivRepTxIcon.setBackgroundResource(R.drawable.bg_btn_navy);
                    ivRepTxIcon.setImageResource(R.drawable.ic_motor_besar);
                } else if (catLower.contains("kecil") || catLower.contains("bebek")) {
                    ivRepTxIcon.setBackgroundResource(R.drawable.bg_banner_blue);
                    ivRepTxIcon.setImageResource(R.drawable.ic_motor_kecil);
                } else {
                    ivRepTxIcon.setBackgroundResource(R.drawable.bg_banner_blue);
                    ivRepTxIcon.setImageResource(R.drawable.ic_motor_sedang);
                }

                String cleanPlat = platNo.contains("•") ? platNo.split("•")[0].trim() : platNo;
                tvRepTxTitle.setText("#" + (i + 1) + "  " + motorName + " • " + cleanPlat);
                tvRepTxSub.setText(timeLabel + " • Washer: " + washerName);
                tvRepTxPrice.setText(formatRupiah(price));
                tvRepTxPay.setText(payMethod + " • Struk ›");

                dividerRepTx.setVisibility(i == displayCount - 1 ? View.GONE : View.VISIBLE);

                row.setOnClickListener(v -> {
                    Intent receiptIntent = new Intent(ReportActivity.this, ReceiptActivity.class);
                    receiptIntent.putExtra(ReceiptActivity.EXTRA_MOTOR_NAME, motorName);
                    receiptIntent.putExtra(ReceiptActivity.EXTRA_PLAT_NO, cleanPlat);
                    receiptIntent.putExtra(ReceiptActivity.EXTRA_WASHER_NAME, washerName);
                    receiptIntent.putExtra(ReceiptActivity.EXTRA_PRICE, price);
                    receiptIntent.putExtra(ReceiptActivity.EXTRA_PAY_METHOD, payMethod);
                    startActivity(receiptIntent);
                });

                llReportTransactionList.addView(row);
            }

            // Atur visibilitas tombol Muat Lagi & Ringkas
            int remaining = filteredTxList.size() - displayCount;
            boolean canCollapse = visibleTxLimit > INITIAL_TX_DISPLAY && filteredTxList.size() > INITIAL_TX_DISPLAY;

            if (remaining > 0 || canCollapse) {
                llTxPaginationBar.setVisibility(View.VISIBLE);
                if (remaining > 0) {
                    int nextBatch = Math.min(remaining, TX_PAGE_STEP);
                    btnLoadMoreTx.setVisibility(View.VISIBLE);
                    btnLoadMoreTx.setText("Tampilkan " + nextBatch + " Lagi (" + remaining + " tersisa) ▾");
                } else {
                    btnLoadMoreTx.setVisibility(View.GONE);
                }
                btnCollapseTx.setVisibility(canCollapse ? View.VISIBLE : View.GONE);
            } else {
                llTxPaginationBar.setVisibility(View.GONE);
            }
        }

        lastActiveDateLabel = activeDateLabel;
        lastOmsetText = omsetText;
        lastMotorCountText = motorCountText;
        lastExpenseText = expenseText;
        lastTotalCommissionText = totalCommissionText;
        lastNetProfitText = netProfitText;
        lastWorkerCount = workerCount;
    }

    private String buildWorkerDetailText(WorkerStat stat) {
        if (!isDaily || stat.motorCount == 0) {
            return stat.motorCount + " Motor Selesai";
        }
        if (stat.categoryCounts.size() == 1) {
            String singleCat = stat.categoryCounts.keySet().iterator().next();
            return stat.motorCount + " " + singleCat;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(stat.motorCount).append(" Motor (");
        int idx = 0;
        for (Map.Entry<String, Integer> entry : stat.categoryCounts.entrySet()) {
            if (idx > 0) sb.append(", ");
            String shortCat = entry.getKey().replace("Motor ", "");
            sb.append(entry.getValue()).append(" ").append(shortCat);
            idx++;
        }
        sb.append(")");
        return sb.toString();
    }

    private void shareReportToWhatsApp() {
        String outletName = sessionManager.getOutletName().isEmpty() ? "Outlet WashLog" : sessionManager.getOutletName();
        String periodTitle = isDaily ? "HARIAN" : "BULANAN";
        String message = "*LAPORAN " + periodTitle + " WASHLOG*\n"
                + "*" + outletName + "*\n"
                + "Periode: " + lastActiveDateLabel + "\n"
                + "────────────────────\n"
                + "• *Omset Kotor* : " + lastOmsetText + " (" + lastMotorCountText + ")\n"
                + "• *Operasional* : - " + lastExpenseText + "\n"
                + "• *Komisi Tim (" + lastWorkerCount + ")* : - " + lastTotalCommissionText + "\n"
                + "────────────────────\n"
                + "*LABA BERSIH PEMILIK : " + lastNetProfitText + "*\n\n"
                + "_Dicatat otomatis melalui aplikasi WashLog._";

        try {
            Intent waIntent = new Intent(Intent.ACTION_VIEW);
            waIntent.setData(Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(message)));
            startActivity(waIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Laporan siap dibagikan ke WhatsApp", Toast.LENGTH_SHORT).show();
        }
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
                    SimpleDateFormat localFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    return localFmt.format(parsed);
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
                    return new SimpleDateFormat("HH:mm 'WIB'", Locale.getDefault()).format(parsed);
                }
            } catch (Exception ignored) {}
        }
        String timeCol = getStr(obj, "time", "");
        return !timeCol.isEmpty() ? timeCol : "Hari ini";
    }

    private String formatRupiah(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        return "Rp " + formatter.format((long) amount);
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