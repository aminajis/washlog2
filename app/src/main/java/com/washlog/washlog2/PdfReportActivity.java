package com.washlog.washlog2;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

public class PdfReportActivity extends AppCompatActivity {

    public static final String EXTRA_PERIOD_TYPE = "extra_period_type";
    public static final String EXTRA_DATE_LABEL = "extra_date_label";
    public static final String EXTRA_FILTER_PREFIX = "extra_filter_prefix";
    public static final String EXTRA_OMSET = "extra_omset";
    public static final String EXTRA_EXPENSE = "extra_expense";
    public static final String EXTRA_COMMISSION = "extra_commission";
    public static final String EXTRA_NET_PROFIT = "extra_net_profit";

    private SessionManager sessionManager;
    private String periodType = "Harian";
    private String dateLabel = "";
    private String filterPrefix = "";
    private String omsetText = "Rp 0";
    private String expenseText = "Rp 0";
    private String commissionText = "Rp 0";
    private String netProfitText = "Rp 0";
    private String ownerPhone = "";
    private int commissionPct = 40;

    private TextView tvPdfFileName, tvPdfOutletHeader;
    private TextView tvPdfOmset, tvPdfExpense, tvPdfCommission, tvPdfNetProfit;
    private TextView tvPdfTotalUnit, tvPdfKruBadge, tvPdfTotalExpenseBottom;
    private TextView tvSignOwnerOutlet, tvPdfDocHash;
    private LinearLayout llPdfCategoryRows, llPdfWorkerRows, llPdfExpenseRows;

    private JsonArray allWorkers = new JsonArray();
    private JsonArray allTransactions = new JsonArray();
    private JsonArray allExpenses = new JsonArray();

    private static class CategoryStat {
        String label;
        int unitCount = 0;
        double unitPrice = 0;
        double subtotal = 0;

        CategoryStat(String label, double unitPrice) {
            this.label = label;
            this.unitPrice = unitPrice;
        }
    }

    private static class WorkerDetail {
        String name;
        int count = 0;
        double omset = 0;

        WorkerDetail(String name) {
            this.name = name;
        }
    }

    private static class ExpenseDetail {
        String title;
        String category;
        double amount;

        ExpenseDetail(String title, String category, double amount) {
            this.title = title;
            this.category = category;
            this.amount = amount;
        }
    }

    private static class TxDetail {
        String time;
        String motorAndPlat;
        String washer;
        String payMethod;
        double price;

        TxDetail(String time, String motorAndPlat, String washer, String payMethod, double price) {
            this.time = time;
            this.motorAndPlat = motorAndPlat;
            this.washer = washer;
            this.payMethod = payMethod;
            this.price = price;
        }
    }

    private static class ReportSnapshot {
        double totalOmset = 0;
        double totalTunai = 0;
        double totalQris = 0;
        int totalMotor = 0;
        double totalExpense = 0;
        double totalCommission = 0;
        double netProfit = 0;
        List<CategoryStat> categoryDetails = new ArrayList<>();
        List<WorkerDetail> workerDetails = new ArrayList<>();
        List<ExpenseDetail> expenseDetails = new ArrayList<>();
        List<TxDetail> txDetails = new ArrayList<>();
    }

    private static class PdfBuilderState {
        PdfDocument document;
        PdfDocument.Page currentPage;
        Canvas canvas;
        int pageNum = 1;
        int y = 60;
        final String printedAt;
        final Paint footerPaint;

        PdfBuilderState(PdfDocument doc, String printedAt, Paint footerPaint) {
            this.document = doc;
            this.printedAt = printedAt;
            this.footerPaint = footerPaint;
            startNewPage();
        }

        void startNewPage() {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, pageNum).create();
            currentPage = document.startPage(info);
            canvas = currentPage.getCanvas();
            y = 55;
        }

        void ensureSpace(int neededHeight) {
            if (y + neededHeight > 775) {
                drawPageFooter();
                document.finishPage(currentPage);
                pageNum++;
                startNewPage();
            }
        }

        void drawPageFooter() {
            canvas.drawText(
                    "Dicetak otomatis melalui aplikasi WashLog pada " + printedAt + "  •  Hal. " + pageNum,
                    595f / 2f,
                    812f,
                    footerPaint
            );
        }

        void finish() {
            drawPageFooter();
            document.finishPage(currentPage);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_report);

        View topBar = findViewById(R.id.topBarPdfReport);
        View bottomBar = findViewById(R.id.bottomBarPdfReport);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);
        int botPadH = (int) (16 * density);
        int botPadTop = (int) (10 * density);
        int botPadBot = (int) (12 * density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootPdfReport), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            bottomBar.setPadding(botPadH, botPadTop, botPadH, botPadBot + sysBars.bottom);
            return insets;
        });

        sessionManager = new SessionManager(this);

        tvPdfFileName = findViewById(R.id.tvPdfFileName);
        tvPdfOutletHeader = findViewById(R.id.tvPdfOutletHeader);
        TextView tvPdfReportSubtitle = findViewById(R.id.tvPdfReportSubtitle);
        TextView tvPdfReportDate = findViewById(R.id.tvPdfReportDate);

        tvPdfOmset = findViewById(R.id.tvPdfOmset);
        tvPdfExpense = findViewById(R.id.tvPdfExpense);
        tvPdfCommission = findViewById(R.id.tvPdfCommission);
        tvPdfNetProfit = findViewById(R.id.tvPdfNetProfit);

        tvPdfTotalUnit = findViewById(R.id.tvPdfTotalUnit);
        tvPdfKruBadge = findViewById(R.id.tvPdfKruBadge);
        tvPdfTotalExpenseBottom = findViewById(R.id.tvPdfTotalExpenseBottom);
        tvSignOwnerOutlet = findViewById(R.id.tvSignOwnerOutlet);
        tvPdfDocHash = findViewById(R.id.tvPdfDocHash);

        llPdfCategoryRows = findViewById(R.id.llPdfCategoryRows);
        llPdfWorkerRows = findViewById(R.id.llPdfWorkerRows);
        llPdfExpenseRows = findViewById(R.id.llPdfExpenseRows);

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_PERIOD_TYPE)) periodType = intent.getStringExtra(EXTRA_PERIOD_TYPE);
            if (intent.hasExtra(EXTRA_DATE_LABEL)) dateLabel = intent.getStringExtra(EXTRA_DATE_LABEL);
            if (intent.hasExtra(EXTRA_FILTER_PREFIX)) filterPrefix = intent.getStringExtra(EXTRA_FILTER_PREFIX);
            if (intent.hasExtra(EXTRA_OMSET)) omsetText = intent.getStringExtra(EXTRA_OMSET);
            if (intent.hasExtra(EXTRA_EXPENSE)) expenseText = intent.getStringExtra(EXTRA_EXPENSE);
            if (intent.hasExtra(EXTRA_COMMISSION)) commissionText = intent.getStringExtra(EXTRA_COMMISSION);
            if (intent.hasExtra(EXTRA_NET_PROFIT)) netProfitText = intent.getStringExtra(EXTRA_NET_PROFIT);
        }

        if (dateLabel == null || dateLabel.isEmpty()) {
            dateLabel = new SimpleDateFormat("EEEE, dd MMM yyyy", new Locale("id", "ID")).format(new Date());
        }
        if (filterPrefix == null || filterPrefix.isEmpty()) {
            boolean isDaily = !"Bulanan".equalsIgnoreCase(periodType);
            filterPrefix = new SimpleDateFormat(isDaily ? "yyyy-MM-dd" : "yyyy-MM", Locale.US).format(new Date());
        }

        String outletName = sessionManager.getOutletName().isEmpty() ? "WashLog" : sessionManager.getOutletName();
        String compactDate = new SimpleDateFormat("ddMMMyyyy", Locale.US).format(new Date());
        tvPdfFileName.setText("Preview: Laporan_" + periodType + "_" + compactDate + ".pdf");
        tvPdfOutletHeader.setText("Outlet: " + outletName + " • Kasir Shift: Admin");
        tvSignOwnerOutlet.setText("Owner " + outletName);
        tvPdfDocHash.setText("HASH: " + Integer.toHexString((outletName + filterPrefix).hashCode()).toUpperCase(Locale.ROOT) + "-WL");

        tvPdfReportSubtitle.setText("REKAPITULASI " + periodType.toUpperCase(Locale.ROOT) + " STEAM MOTOR");
        tvPdfReportDate.setText("Tanggal: " + dateLabel);
        tvPdfOmset.setText(omsetText);
        tvPdfExpense.setText("- " + expenseText);
        tvPdfCommission.setText("- " + commissionText);
        tvPdfNetProfit.setText(netProfitText);
        tvPdfTotalExpenseBottom.setText(expenseText);

        findViewById(R.id.btnBackPdfReport).setOnClickListener(v -> finish());
        findViewById(R.id.btnDownloadPdf).setOnClickListener(v -> generateAndSavePdf());
        findViewById(R.id.btnSendPdfWa).setOnClickListener(v -> sendPdfViaWhatsApp());

        loadDetailedData();
    }

    private void loadDetailedData() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        SupabaseClient.get("outlets?id=eq." + outletId + "&select=outlet_name,owner_phone,worker_commission_pct", new Callback() {
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
                        String freshName = getStr(obj, "outlet_name", "");
                        ownerPhone = getStr(obj, "owner_phone", "").trim();
                        if (!freshName.isEmpty()) {
                            sessionManager.updateOutletInfo(freshName, ownerPhone);
                            tvPdfOutletHeader.setText("Outlet: " + freshName + " • Kasir Shift: Admin");
                            tvSignOwnerOutlet.setText("Owner " + freshName);
                        }
                        commissionPct = getInt(obj, "worker_commission_pct", 40);
                        refreshPreviewNumbers();
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
                    refreshPreviewNumbers();
                });
            }
        });

        SupabaseClient.get("transactions?outlet_id=eq." + outletId + "&order=created_at.asc&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    allTransactions = JsonParser.parseString(body).getAsJsonArray();
                    refreshPreviewNumbers();
                });
            }
        });

        SupabaseClient.get("expenses?outlet_id=eq." + outletId + "&order=created_at.asc&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    allExpenses = JsonParser.parseString(body).getAsJsonArray();
                    refreshPreviewNumbers();
                });
            }
        });
    }

    private void refreshPreviewNumbers() {
        ReportSnapshot snap = buildReportSnapshot();
        if (!allTransactions.isEmpty() || !allExpenses.isEmpty()) {
            omsetText = formatRupiah(snap.totalOmset);
            expenseText = formatRupiah(snap.totalExpense);
            commissionText = formatRupiah(snap.totalCommission);
            netProfitText = formatRupiah(snap.netProfit);

            tvPdfOmset.setText(omsetText);
            tvPdfExpense.setText("- " + expenseText);
            tvPdfCommission.setText("- " + commissionText);
            tvPdfNetProfit.setText(netProfitText);
            tvPdfTotalExpenseBottom.setText(expenseText);
        }

        renderPreviewSections(snap);
    }

    private void renderPreviewSections(ReportSnapshot snap) {
        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (8 * density);
        int padV = (int) (6 * density);

        // II. Rincian Unit Motor Per Kategori
        llPdfCategoryRows.removeAllViews();
        tvPdfTotalUnit.setText(snap.totalMotor + " Unit");

        if (snap.categoryDetails.isEmpty()) {
            llPdfCategoryRows.addView(createEmptyPreviewRow("Belum ada unit motor tercatat.", padH, padV));
        } else {
            for (CategoryStat cat : snap.categoryDetails) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(padH, padV, padH, padV);

                LinearLayout leftCol = new LinearLayout(this);
                leftCol.setOrientation(LinearLayout.VERTICAL);
                leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvCatTitle = new TextView(this);
                tvCatTitle.setText(cat.label);
                tvCatTitle.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
                tvCatTitle.setTextSize(11f);
                tvCatTitle.setTypeface(null, Typeface.BOLD);

                TextView tvCatSub = new TextView(this);
                tvCatSub.setText(cat.unitCount + " unit x " + formatRupiah(cat.unitPrice));
                tvCatSub.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
                tvCatSub.setTextSize(9.5f);

                leftCol.addView(tvCatTitle);
                leftCol.addView(tvCatSub);

                TextView tvCatTotal = new TextView(this);
                tvCatTotal.setText(formatRupiah(cat.subtotal));
                tvCatTotal.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
                tvCatTotal.setTextSize(11f);
                tvCatTotal.setTypeface(null, Typeface.BOLD);

                row.addView(leftCol);
                row.addView(tvCatTotal);
                llPdfCategoryRows.addView(row);
            }
        }

        // III. Rincian Komisi Pencuci / Worker
        llPdfWorkerRows.removeAllViews();
        tvPdfKruBadge.setText(snap.workerDetails.size() + " Kru");

        if (snap.workerDetails.isEmpty()) {
            llPdfWorkerRows.addView(createEmptyPreviewRow("Belum ada pencuci bertugas.", padH, padV));
        } else {
            int idx = 1;
            for (WorkerDetail wd : snap.workerDetails) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(padH, padV, padH, padV);

                TextView tvNum = new TextView(this);
                int badgeSize = (int) (22 * density);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
                badgeLp.setMarginEnd((int) (8 * density));
                tvNum.setLayoutParams(badgeLp);
                tvNum.setBackgroundResource(R.drawable.bg_pill_blue);
                tvNum.setGravity(Gravity.CENTER);
                tvNum.setText(String.valueOf(idx++));
                tvNum.setTextColor(ContextCompat.getColor(this, R.color.royal_blue));
                tvNum.setTextSize(10f);
                tvNum.setTypeface(null, Typeface.BOLD);

                LinearLayout midCol = new LinearLayout(this);
                midCol.setOrientation(LinearLayout.VERTICAL);
                midCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvWName = new TextView(this);
                tvWName.setText(wd.name);
                tvWName.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
                tvWName.setTextSize(11f);
                tvWName.setTypeface(null, Typeface.BOLD);

                TextView tvWCount = new TextView(this);
                tvWCount.setText(wd.count + " unit selesai");
                tvWCount.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
                tvWCount.setTextSize(9.5f);

                midCol.addView(tvWName);
                midCol.addView(tvWCount);

                double workerComm = wd.omset * (commissionPct / 100.0);
                TextView tvWComm = new TextView(this);
                tvWComm.setText(formatRupiah(workerComm));
                tvWComm.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
                tvWComm.setTextSize(11f);
                tvWComm.setTypeface(null, Typeface.BOLD);

                row.addView(tvNum);
                row.addView(midCol);
                row.addView(tvWComm);
                llPdfWorkerRows.addView(row);
            }
        }

        // IV. Rekapitulasi Pengeluaran
        llPdfExpenseRows.removeAllViews();
        if (snap.expenseDetails.isEmpty()) {
            llPdfExpenseRows.addView(createEmptyPreviewRow("Tidak ada pengeluaran operasional tercatat.", padH, padV));
        } else {
            for (ExpenseDetail exp : snap.expenseDetails) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(padH, padV, padH, padV);

                LinearLayout leftCol = new LinearLayout(this);
                leftCol.setOrientation(LinearLayout.VERTICAL);
                leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvExpTitle = new TextView(this);
                tvExpTitle.setText(exp.title);
                tvExpTitle.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
                tvExpTitle.setTextSize(11f);
                tvExpTitle.setTypeface(null, Typeface.BOLD);

                TextView tvExpCat = new TextView(this);
                tvExpCat.setText(exp.category);
                tvExpCat.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
                tvExpCat.setTextSize(9.5f);

                leftCol.addView(tvExpTitle);
                leftCol.addView(tvExpCat);

                TextView tvExpAmt = new TextView(this);
                tvExpAmt.setText(formatRupiah(exp.amount));
                tvExpAmt.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
                tvExpAmt.setTextSize(11f);
                tvExpAmt.setTypeface(null, Typeface.BOLD);

                row.addView(leftCol);
                row.addView(tvExpAmt);
                llPdfExpenseRows.addView(row);
            }
        }
    }

    private TextView createEmptyPreviewRow(String text, int padH, int padV) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
        tv.setTextSize(10.5f);
        tv.setPadding(padH, padV, padH, padV);
        return tv;
    }

    private String formatCategoryDisplayLabel(String rawCat) {
        String lower = rawCat.toLowerCase(Locale.ROOT);
        if (lower.contains("kecil") || lower.contains("bebek")) {
            return "Bebek / Matic Kecil";
        } else if (lower.contains("sedang") || lower.contains("nmax") || lower.contains("pcx") || lower.contains("vario")) {
            return "Matic Sedang / Sport 150cc";
        } else if (lower.contains("besar") || lower.contains("moge") || lower.contains("sport")) {
            return "Big Bike 250cc+ / Moge";
        }
        return rawCat;
    }

    private ReportSnapshot buildReportSnapshot() {
        ReportSnapshot snap = new ReportSnapshot();

        Map<String, String> workerIdToName = new HashMap<>();
        Map<String, WorkerDetail> workerMap = new LinkedHashMap<>();
        Map<String, CategoryStat> categoryMap = new LinkedHashMap<>();

        for (JsonElement el : allWorkers) {
            JsonObject w = el.getAsJsonObject();
            String wId = getStr(w, "id", "");
            String wName = getStr(w, "name", getStr(w, "worker_name", ""));
            if (!wId.isEmpty() && !wName.isEmpty()) {
                workerIdToName.put(wId, wName);
            }
            if (!wName.isEmpty() && !workerMap.containsKey(wName)) {
                workerMap.put(wName, new WorkerDetail(wName));
            }
        }

        for (JsonElement el : allTransactions) {
            JsonObject tx = el.getAsJsonObject();
            String dateKey = extractLocalDateKey(tx);
            if (!dateKey.startsWith(filterPrefix)) continue;

            double price = getDouble(tx, "price", 0);
            String motorName = getStr(tx, "motor_name", getStr(tx, "category", "Motor"));
            String rawCategory = getStr(tx, "category", motorName);
            String displayCat = formatCategoryDisplayLabel(rawCategory);
            String platNo = getStr(tx, "plate_number", getStr(tx, "plat_no", "-"));
            String payMethod = getStr(tx, "payment_method", getStr(tx, "pay_method", "Tunai"));
            String washerName = resolveWasherName(tx, workerIdToName, platNo);
            String timeStr = extractLocalTime(tx);

            snap.totalOmset += price;
            snap.totalMotor++;
            if (payMethod.toLowerCase(Locale.ROOT).contains("qris")) {
                snap.totalQris += price;
            } else {
                snap.totalTunai += price;
            }

            CategoryStat cs = categoryMap.get(displayCat);
            if (cs == null) {
                cs = new CategoryStat(displayCat, price);
                categoryMap.put(displayCat, cs);
            }
            cs.unitCount++;
            cs.subtotal += price;

            WorkerDetail wd = workerMap.get(washerName);
            if (wd == null) {
                wd = new WorkerDetail(washerName);
                workerMap.put(washerName, wd);
            }
            wd.count++;
            wd.omset += price;

            String cleanPlat = platNo.contains("•") ? platNo.split("•")[0].trim() : platNo;
            snap.txDetails.add(new TxDetail(
                    timeStr,
                    cleanPlat + " (" + motorName + ")",
                    washerName,
                    payMethod,
                    price
            ));
        }

        for (JsonElement el : allExpenses) {
            JsonObject exp = el.getAsJsonObject();
            String dateKey = extractLocalDateKey(exp);
            if (!dateKey.startsWith(filterPrefix)) continue;

            double amount = getDouble(exp, "amount", getDouble(exp, "price", getDouble(exp, "nominal", 0)));
            String title = getStr(exp, "title",
                    getStr(exp, "name",
                            getStr(exp, "description",
                                    getStr(exp, "item_name", "Pengeluaran Operasional"))));
            String category = getStr(exp, "category", "Operasional");

            snap.totalExpense += amount;
            snap.expenseDetails.add(new ExpenseDetail(title, category, amount));
        }

        snap.totalCommission = snap.totalOmset * (commissionPct / 100.0);
        snap.netProfit = snap.totalOmset - snap.totalExpense - snap.totalCommission;
        snap.categoryDetails.addAll(categoryMap.values());
        snap.workerDetails.addAll(workerMap.values());

        return snap;
    }

    private PdfDocument buildPdfDocument() {
        ReportSnapshot snap = buildReportSnapshot();
        if (allTransactions.isEmpty() && allExpenses.isEmpty()) {
            snap.totalOmset = parseRupiah(omsetText);
            snap.totalExpense = parseRupiah(expenseText);
            snap.totalCommission = parseRupiah(commissionText);
            snap.netProfit = parseRupiah(netProfitText);
        }

        PdfDocument document = new PdfDocument();

        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.parseColor("#2563EB"));
        titlePaint.setTextSize(17f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        Paint subtitlePaint = new Paint();
        subtitlePaint.setColor(Color.parseColor("#0F172A"));
        subtitlePaint.setTextSize(13f);
        subtitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        subtitlePaint.setTextAlign(Paint.Align.CENTER);

        Paint datePaint = new Paint();
        datePaint.setColor(Color.parseColor("#64748B"));
        datePaint.setTextSize(11f);
        datePaint.setTextAlign(Paint.Align.CENTER);

        Paint sectionPaint = new Paint();
        sectionPaint.setColor(Color.parseColor("#1E3A8A"));
        sectionPaint.setTextSize(11.5f);
        sectionPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        sectionPaint.setTextAlign(Paint.Align.LEFT);

        Paint tableHeaderBg = new Paint();
        tableHeaderBg.setColor(Color.parseColor("#F1F5F9"));

        Paint tableHeaderPaint = new Paint();
        tableHeaderPaint.setColor(Color.parseColor("#334155"));
        tableHeaderPaint.setTextSize(9.5f);
        tableHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint cellPaint = new Paint();
        cellPaint.setColor(Color.parseColor("#1E293B"));
        cellPaint.setTextSize(9.5f);

        Paint cellMutedPaint = new Paint();
        cellMutedPaint.setColor(Color.parseColor("#64748B"));
        cellMutedPaint.setTextSize(9f);

        Paint cellBoldRight = new Paint();
        cellBoldRight.setColor(Color.parseColor("#0F172A"));
        cellBoldRight.setTextSize(9.5f);
        cellBoldRight.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        cellBoldRight.setTextAlign(Paint.Align.RIGHT);

        Paint summaryLabelPaint = new Paint();
        summaryLabelPaint.setColor(Color.parseColor("#475569"));
        summaryLabelPaint.setTextSize(11f);
        summaryLabelPaint.setTextAlign(Paint.Align.LEFT);

        Paint summaryDarkRight = new Paint();
        summaryDarkRight.setColor(Color.parseColor("#0F172A"));
        summaryDarkRight.setTextSize(11f);
        summaryDarkRight.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        summaryDarkRight.setTextAlign(Paint.Align.RIGHT);

        Paint summaryRedRight = new Paint();
        summaryRedRight.setColor(Color.parseColor("#DC2626"));
        summaryRedRight.setTextSize(11f);
        summaryRedRight.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        summaryRedRight.setTextAlign(Paint.Align.RIGHT);

        boolean isLoss = snap.netProfit < 0;
        Paint profitLabelPaint = new Paint();
        profitLabelPaint.setColor(isLoss ? Color.parseColor("#DC2626") : Color.parseColor("#059669"));
        profitLabelPaint.setTextSize(13f);
        profitLabelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        profitLabelPaint.setTextAlign(Paint.Align.LEFT);

        Paint profitValuePaint = new Paint();
        profitValuePaint.setColor(isLoss ? Color.parseColor("#DC2626") : Color.parseColor("#059669"));
        profitValuePaint.setTextSize(14f);
        profitValuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        profitValuePaint.setTextAlign(Paint.Align.RIGHT);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#CBD5E1"));
        linePaint.setStrokeWidth(1f);

        Paint rowDividerPaint = new Paint();
        rowDividerPaint.setColor(Color.parseColor("#E2E8F0"));
        rowDividerPaint.setStrokeWidth(0.7f);

        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.parseColor("#94A3B8"));
        footerPaint.setTextSize(9f);
        footerPaint.setTextAlign(Paint.Align.CENTER);

        String printedAt = new SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", new Locale("id", "ID")).format(new Date());
        PdfBuilderState pdf = new PdfBuilderState(document, printedAt, footerPaint);

        int centerX = 595 / 2;
        int leftX = 45;
        int rightX = 550;

        String outletName = sessionManager.getOutletName().isEmpty()
                ? "WASHLOG SYSTEM"
                : "WASHLOG - " + sessionManager.getOutletName().toUpperCase(Locale.ROOT);

        // === HEADER LAPORAN ===
        pdf.canvas.drawText(outletName, centerX, pdf.y, titlePaint);
        pdf.y += 20;
        pdf.canvas.drawText("LAPORAN KEUANGAN RINCI (" + periodType.toUpperCase(Locale.ROOT) + ")", centerX, pdf.y, subtitlePaint);
        pdf.y += 16;
        pdf.canvas.drawText("Periode: " + dateLabel, centerX, pdf.y, datePaint);
        pdf.y += 16;
        pdf.canvas.drawLine(leftX, pdf.y, rightX, pdf.y, linePaint);
        pdf.y += 22;

        // === 1. RINGKASAN ARUS KAS ===
        pdf.canvas.drawText("I. RINGKASAN ARUS KAS", leftX, pdf.y, sectionPaint);
        pdf.y += 18;

        pdf.canvas.drawText("Omset Kotor (" + snap.totalMotor + " Motor)", leftX, pdf.y, summaryLabelPaint);
        pdf.canvas.drawText(formatRupiah(snap.totalOmset), rightX, pdf.y, summaryDarkRight);
        pdf.y += 14;

        cellMutedPaint.setTextAlign(Paint.Align.LEFT);
        pdf.canvas.drawText("    • Pembayaran Tunai: " + formatRupiah(snap.totalTunai) + "   |   QRIS: " + formatRupiah(snap.totalQris), leftX, pdf.y, cellMutedPaint);
        pdf.y += 18;

        pdf.canvas.drawText("Total Biaya Operasional (" + snap.expenseDetails.size() + " Nota)", leftX, pdf.y, summaryLabelPaint);
        pdf.canvas.drawText("- " + formatRupiah(snap.totalExpense), rightX, pdf.y, summaryRedRight);
        pdf.y += 18;

        pdf.canvas.drawText("Total Komisi Pekerja (" + commissionPct + "%)", leftX, pdf.y, summaryLabelPaint);
        pdf.canvas.drawText("- " + formatRupiah(snap.totalCommission), rightX, pdf.y, summaryRedRight);
        pdf.y += 14;

        pdf.canvas.drawLine(leftX, pdf.y, rightX, pdf.y, linePaint);
        pdf.y += 20;

        pdf.canvas.drawText("LABA BERSIH PEMILIK", leftX, pdf.y, profitLabelPaint);
        pdf.canvas.drawText(formatRupiah(snap.netProfit), rightX, pdf.y, profitValuePaint);
        pdf.y += 26;

        // === 2. RINCIAN UNIT MOTOR PER KATEGORI ===
        pdf.ensureSpace(75);
        pdf.canvas.drawText("II. RINCIAN UNIT MOTOR PER KATEGORI", leftX, pdf.y, sectionPaint);
        pdf.y += 12;

        pdf.canvas.drawRect(leftX, pdf.y, rightX, pdf.y + 20, tableHeaderBg);
        pdf.canvas.drawText("Kategori Motor", leftX + 8, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Volume & Tarif", leftX + 270, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.RIGHT);
        pdf.canvas.drawText("Subtotal", rightX - 8, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.LEFT);
        pdf.y += 22;

        if (snap.categoryDetails.isEmpty()) {
            pdf.canvas.drawText("Belum ada data kategori motor pada periode ini.", leftX + 8, pdf.y + 13, cellMutedPaint);
            pdf.y += 22;
        } else {
            for (CategoryStat cs : snap.categoryDetails) {
                pdf.ensureSpace(24);
                pdf.canvas.drawText(cs.label, leftX + 8, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(cs.unitCount + " unit x " + formatRupiah(cs.unitPrice), leftX + 270, pdf.y + 14, cellMutedPaint);
                pdf.canvas.drawText(formatRupiah(cs.subtotal), rightX - 8, pdf.y + 14, cellBoldRight);
                pdf.y += 20;
                pdf.canvas.drawLine(leftX, pdf.y, rightX, pdf.y, rowDividerPaint);
            }
        }
        pdf.y += 18;

        // === 3. RINCIAN KOMISI PEKERJA ===
        pdf.ensureSpace(75);
        pdf.canvas.drawText("III. RINCIAN KOMISI PEKERJA (" + commissionPct + "%)", leftX, pdf.y, sectionPaint);
        pdf.y += 12;

        pdf.canvas.drawRect(leftX, pdf.y, rightX, pdf.y + 20, tableHeaderBg);
        pdf.canvas.drawText("Nama Pekerja", leftX + 8, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Unit Dicuci", leftX + 230, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Kontribusi Omset", leftX + 320, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.RIGHT);
        pdf.canvas.drawText("Komisi Diterima", rightX - 8, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.LEFT);
        pdf.y += 22;

        if (snap.workerDetails.isEmpty()) {
            pdf.canvas.drawText("Belum ada data pekerja pada periode ini.", leftX + 8, pdf.y + 13, cellMutedPaint);
            pdf.y += 22;
        } else {
            for (WorkerDetail wd : snap.workerDetails) {
                pdf.ensureSpace(24);
                double wComm = wd.omset * (commissionPct / 100.0);
                pdf.canvas.drawText(wd.name, leftX + 8, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(wd.count + " motor", leftX + 230, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(formatRupiah(wd.omset), leftX + 320, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(formatRupiah(wComm), rightX - 8, pdf.y + 14, cellBoldRight);
                pdf.y += 20;
                pdf.canvas.drawLine(leftX, pdf.y, rightX, pdf.y, rowDividerPaint);
            }
        }
        pdf.y += 18;

        // === 4. REKAPITULASI PENGELUARAN ===
        pdf.ensureSpace(75);
        pdf.canvas.drawText("IV. REKAPITULASI PENGELUARAN OPERASIONAL", leftX, pdf.y, sectionPaint);
        pdf.y += 12;

        pdf.canvas.drawRect(leftX, pdf.y, rightX, pdf.y + 20, tableHeaderBg);
        pdf.canvas.drawText("Keterangan / Item", leftX + 8, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Kategori", leftX + 300, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.RIGHT);
        pdf.canvas.drawText("Nominal", rightX - 8, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.LEFT);
        pdf.y += 22;

        if (snap.expenseDetails.isEmpty()) {
            pdf.canvas.drawText("Tidak ada catatan pengeluaran pada periode ini.", leftX + 8, pdf.y + 13, cellMutedPaint);
            pdf.y += 22;
        } else {
            for (ExpenseDetail exp : snap.expenseDetails) {
                pdf.ensureSpace(24);
                String safeTitle = exp.title.length() > 42 ? exp.title.substring(0, 39) + "..." : exp.title;
                pdf.canvas.drawText(safeTitle, leftX + 8, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(exp.category, leftX + 300, pdf.y + 14, cellMutedPaint);
                pdf.canvas.drawText(formatRupiah(exp.amount), rightX - 8, pdf.y + 14, cellBoldRight);
                pdf.y += 20;
                pdf.canvas.drawLine(leftX, pdf.y, rightX, pdf.y, rowDividerPaint);
            }
        }
        pdf.y += 18;

        // === 5. LOG TRANSAKSI CUCIAN ===
        pdf.ensureSpace(75);
        pdf.canvas.drawText("V. LOG TRANSAKSI CUCIAN (" + snap.txDetails.size() + " UNIT)", leftX, pdf.y, sectionPaint);
        pdf.y += 12;

        pdf.canvas.drawRect(leftX, pdf.y, rightX, pdf.y + 20, tableHeaderBg);
        pdf.canvas.drawText("Waktu", leftX + 8, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Plat & Motor", leftX + 95, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Washer", leftX + 305, pdf.y + 14, tableHeaderPaint);
        pdf.canvas.drawText("Metode", leftX + 390, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.RIGHT);
        pdf.canvas.drawText("Tarif", rightX - 8, pdf.y + 14, tableHeaderPaint);
        tableHeaderPaint.setTextAlign(Paint.Align.LEFT);
        pdf.y += 22;

        if (snap.txDetails.isEmpty()) {
            pdf.canvas.drawText("Tidak ada transaksi cucian pada periode ini.", leftX + 8, pdf.y + 13, cellMutedPaint);
            pdf.y += 22;
        } else {
            for (TxDetail tx : snap.txDetails) {
                pdf.ensureSpace(24);
                String safeMotor = tx.motorAndPlat.length() > 32 ? tx.motorAndPlat.substring(0, 29) + "..." : tx.motorAndPlat;
                pdf.canvas.drawText(tx.time, leftX + 8, pdf.y + 14, cellMutedPaint);
                pdf.canvas.drawText(safeMotor, leftX + 95, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(tx.washer, leftX + 305, pdf.y + 14, cellPaint);
                pdf.canvas.drawText(tx.payMethod, leftX + 390, pdf.y + 14, cellMutedPaint);
                pdf.canvas.drawText(formatRupiah(tx.price), rightX - 8, pdf.y + 14, cellBoldRight);
                pdf.y += 20;
                pdf.canvas.drawLine(leftX, pdf.y, rightX, pdf.y, rowDividerPaint);
            }
        }

        pdf.finish();
        return document;
    }

    private Uri savePdfToCacheAndDownloads(PdfDocument document, String fileName) throws IOException {
        File cacheDir = new File(getCacheDir(), "reports");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File cacheFile = new File(cacheDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            document.writeTo(fos);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri dlUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (dlUri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(dlUri)) {
                    if (os != null) document.writeTo(os);
                }
            }
        }

        try {
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cacheFile);
        } catch (Exception e) {
            return Uri.fromFile(cacheFile);
        }
    }

    private void generateAndSavePdf() {
        PdfDocument document = buildPdfDocument();
        String fileName = "Laporan_WashLog_" + periodType + "_" + System.currentTimeMillis() + ".pdf";
        try {
            Uri fileUri = savePdfToCacheAndDownloads(document, fileName);
            Toast.makeText(this, "PDF tersimpan di folder Download!", Toast.LENGTH_SHORT).show();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Bagikan / Buka Dokumen PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuat PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }

    private void sendPdfViaWhatsApp() {
        PdfDocument document = buildPdfDocument();
        String fileName = "Laporan_WashLog_" + periodType + "_" + System.currentTimeMillis() + ".pdf";
        String outletName = sessionManager.getOutletName().isEmpty() ? "Outlet WashLog" : sessionManager.getOutletName();
        String caption = "*LAPORAN " + periodType.toUpperCase(Locale.ROOT) + " WASHLOG*\n"
                + "*" + outletName + "* (" + dateLabel + ")\n"
                + "• Omset Kotor : " + omsetText + "\n"
                + "• Pengeluaran : - " + expenseText + "\n"
                + "• Komisi Tim  : - " + commissionText + "\n"
                + "• *Laba Bersih : " + netProfitText + "*";

        try {
            Uri fileUri = savePdfToCacheAndDownloads(document, fileName);
            Intent waShare = new Intent(Intent.ACTION_SEND);
            waShare.setType("application/pdf");
            waShare.setPackage("com.whatsapp");
            waShare.putExtra(Intent.EXTRA_STREAM, fileUri);
            waShare.putExtra(Intent.EXTRA_TEXT, caption);
            waShare.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(waShare);
        } catch (Exception e) {
            try {
                String cleanPhone = ownerPhone.replaceAll("[^0-9]", "");
                if (cleanPhone.startsWith("0")) cleanPhone = "62" + cleanPhone.substring(1);
                String url = cleanPhone.isEmpty()
                        ? "https://api.whatsapp.com/send?text=" + Uri.encode(caption)
                        : "https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(caption);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ex) {
                Toast.makeText(this, "WhatsApp tidak tersedia", Toast.LENGTH_SHORT).show();
            }
        } finally {
            document.close();
        }
    }

    private String resolveWasherName(JsonObject tx, Map<String, String> workerIdToName, String platNo) {
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

    private String extractLocalDateKey(JsonObject obj) {
        String dateCol = getStr(obj, "date", "");
        if (dateCol.length() >= 10) return dateCol.substring(0, 10);
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
        return !timeCol.isEmpty() ? timeCol : "-";
    }

    private double parseRupiah(String rp) {
        if (rp == null) return 0;
        String digits = rp.replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || digits.equals("-")) return 0;
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
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