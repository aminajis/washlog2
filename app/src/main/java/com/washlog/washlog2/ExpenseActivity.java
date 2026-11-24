package com.washlog.washlog2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ExpenseActivity extends AppCompatActivity {

    private static final String PREF_RECEIPTS = "WashlogExpenseReceipts";

    private SessionManager sessionManager;
    private SharedPreferences receiptPrefs;

    private EditText etExpenseName, etExpenseNominal;
    private ImageView ivInputCatIcon;
    private LinearLayout chipCatSabun, chipCatListrik, chipCatMesin, chipCatMakan;
    private ImageView ivChipSabun, ivChipListrik, ivChipMesin, ivChipMakan;
    private TextView tvChipSabun, tvChipListrik, tvChipMesin, tvChipMakan;

    private ImageView ivReceiptPreview, ivReceiptThumbFallback;
    private TextView tvReceiptFileName, tvReceiptStatus, tvChangeReceiptLabel;
    private TextView tvExpenseDate, tvTotalDailyExpense;
    private LinearLayout llExpenseList;
    private ProgressBar pbExpense;
    private Button btnSubmitExpense;

    private String selectedCategory = "Bahan Kimia/Sabun";
    private String currentOwnerPin = "";
    private String fallbackKasirPin = "";
    private Bitmap pendingReceiptBitmap = null;
    private final OkHttpClient deleteClient = new OkHttpClient();

    private final ActivityResultLauncher<String> pickReceiptLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Bitmap decoded = decodeSampledBitmapFromUri(uri, 480, 480);
                    if (decoded != null) {
                        pendingReceiptBitmap = decoded;
                        ivReceiptPreview.setImageBitmap(decoded);
                        ivReceiptPreview.setVisibility(View.VISIBLE);
                        ivReceiptThumbFallback.setVisibility(View.GONE);

                        String fileName = resolveFileName(uri);
                        int sizeKb = estimateCompressedKb(decoded);
                        tvReceiptFileName.setText(fileName);
                        tvReceiptStatus.setText("Terlampir (" + sizeKb + " KB)");
                        tvReceiptStatus.setTextColor(ContextCompat.getColor(this, R.color.emerald_green));
                        tvChangeReceiptLabel.setText("Ubah");
                    } else {
                        Toast.makeText(this, "Gagal memuat foto nota", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense);

        sessionManager = new SessionManager(this);
        receiptPrefs = getSharedPreferences(PREF_RECEIPTS, MODE_PRIVATE);
        currentOwnerPin = sessionManager.getOwnerPin();

        View topBar = findViewById(R.id.topBarExpense);
        View bottomNav = findViewById(R.id.bottomNavExpense);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);
        int botPadH = (int) (8 * density);
        int botPadV = (int) (8 * density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootExpense), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            bottomNav.setPadding(botPadH, botPadV, botPadH, botPadV + sysBars.bottom);
            return insets;
        });

        etExpenseName = findViewById(R.id.etExpenseName);
        etExpenseNominal = findViewById(R.id.etExpenseNominal);
        ivInputCatIcon = findViewById(R.id.ivInputCatIcon);

        chipCatSabun = findViewById(R.id.chipCatSabun);
        chipCatListrik = findViewById(R.id.chipCatListrik);
        chipCatMesin = findViewById(R.id.chipCatMesin);
        chipCatMakan = findViewById(R.id.chipCatMakan);

        ivChipSabun = findViewById(R.id.ivChipSabun);
        ivChipListrik = findViewById(R.id.ivChipListrik);
        ivChipMesin = findViewById(R.id.ivChipMesin);
        ivChipMakan = findViewById(R.id.ivChipMakan);

        tvChipSabun = findViewById(R.id.tvChipSabun);
        tvChipListrik = findViewById(R.id.tvChipListrik);
        tvChipMesin = findViewById(R.id.tvChipMesin);
        tvChipMakan = findViewById(R.id.tvChipMakan);

        ivReceiptPreview = findViewById(R.id.ivReceiptPreview);
        ivReceiptThumbFallback = findViewById(R.id.ivReceiptThumbFallback);
        tvReceiptFileName = findViewById(R.id.tvReceiptFileName);
        tvReceiptStatus = findViewById(R.id.tvReceiptStatus);
        tvChangeReceiptLabel = findViewById(R.id.tvChangeReceiptLabel);

        tvExpenseDate = findViewById(R.id.tvExpenseDate);
        tvTotalDailyExpense = findViewById(R.id.tvTotalDailyExpense);
        llExpenseList = findViewById(R.id.llExpenseList);
        pbExpense = findViewById(R.id.pbExpense);
        btnSubmitExpense = findViewById(R.id.btnSubmitExpense);

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, dd MMM yyyy", new Locale("id", "ID"));
        tvExpenseDate.setText(dateFmt.format(new Date()));

        resetReceiptAttachment();

        findViewById(R.id.btnBackExpense).setOnClickListener(v -> finish());
        findViewById(R.id.navHomeExp).setOnClickListener(v -> finish());

        findViewById(R.id.navReportExp).setOnClickListener(v -> {
            startActivity(new Intent(ExpenseActivity.this, ReportActivity.class));
            finish();
        });

        View.OnClickListener goProfile = v -> {
            startActivity(new Intent(ExpenseActivity.this, ProfileActivity.class));
            finish();
        };
        findViewById(R.id.navProfileExp).setOnClickListener(goProfile);
        findViewById(R.id.btnTopProfileExp).setOnClickListener(goProfile);

        chipCatSabun.setOnClickListener(v -> selectCategory("Bahan Kimia/Sabun"));
        chipCatListrik.setOnClickListener(v -> selectCategory("Listrik/Air"));
        chipCatMesin.setOnClickListener(v -> selectCategory("Perawatan Mesin"));
        chipCatMakan.setOnClickListener(v -> selectCategory("Makanan/Minuman Tim"));

        findViewById(R.id.btnChangeReceipt).setOnClickListener(v -> pickReceiptLauncher.launch("image/*"));
        ivReceiptPreview.setOnClickListener(v -> {
            if (pendingReceiptBitmap != null) {
                showReceiptPreviewDialog(pendingReceiptBitmap, tvReceiptFileName.getText().toString());
            }
        });

        btnSubmitExpense.setOnClickListener(v -> addNewExpense());

        fetchOwnerPin();
        loadExpensesFromSupabase();
    }

    private void fetchOwnerPin() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        SupabaseClient.get("outlets?id=eq." + outletId + "&select=owner_pin,kasir_pin", new Callback() {
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
                        String fetchedOwnerPin = getStr(obj, "owner_pin", "");
                        fallbackKasirPin = getStr(obj, "kasir_pin", "");
                        if (!fetchedOwnerPin.isEmpty()) {
                            currentOwnerPin = fetchedOwnerPin;
                            sessionManager.setOwnerPin(fetchedOwnerPin);
                        }
                    }
                });
            }
        });
    }

    private void selectCategory(String category) {
        selectedCategory = category;
        ivInputCatIcon.setImageResource(getCategoryIconRes(category));

        updateChipStyle(chipCatSabun, ivChipSabun, tvChipSabun, category.equals("Bahan Kimia/Sabun"));
        updateChipStyle(chipCatListrik, ivChipListrik, tvChipListrik, category.equals("Listrik/Air"));
        updateChipStyle(chipCatMesin, ivChipMesin, tvChipMesin, category.equals("Perawatan Mesin"));
        updateChipStyle(chipCatMakan, ivChipMakan, tvChipMakan, category.equals("Makanan/Minuman Tim"));
    }

    private void updateChipStyle(LinearLayout chip, ImageView icon, TextView label, boolean isSelected) {
        chip.setBackgroundResource(isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        int textColor = ContextCompat.getColor(this, isSelected ? R.color.white : R.color.deep_slate_navy);
        int iconColor = ContextCompat.getColor(this, isSelected ? R.color.white : R.color.royal_blue);
        label.setTextColor(textColor);
        label.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        icon.setColorFilter(iconColor);
    }

    private int getCategoryIconRes(String category) {
        if (category.contains("Sabun")) return R.drawable.ic_exp_sabun;
        if (category.contains("Listrik")) return R.drawable.ic_exp_listrik;
        if (category.contains("Mesin")) return R.drawable.ic_exp_mesin;
        return R.drawable.ic_exp_makan;
    }

    private void resetReceiptAttachment() {
        pendingReceiptBitmap = null;
        ivReceiptPreview.setImageDrawable(null);
        ivReceiptPreview.setVisibility(View.GONE);
        ivReceiptThumbFallback.setVisibility(View.VISIBLE);
        tvReceiptFileName.setText("Belum ada foto nota");
        tvReceiptStatus.setText("Wajib • Lampirkan foto bukti nota");
        tvReceiptStatus.setTextColor(Color.parseColor("#DC2626"));
        tvChangeReceiptLabel.setText("Pilih");
    }

    private void loadExpensesFromSupabase() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        pbExpense.setVisibility(View.VISIBLE);

        SupabaseClient.get("expenses?outlet_id=eq." + outletId + "&order=created_at.desc&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    pbExpense.setVisibility(View.GONE);
                    Toast.makeText(ExpenseActivity.this, "Gagal memuat pengeluaran: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                runOnUiThread(() -> {
                    pbExpense.setVisibility(View.GONE);
                    if (!response.isSuccessful()) return;
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    renderExpenseList(arr);
                });
            }
        });
    }

    private void addNewExpense() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) {
            Toast.makeText(this, "Sesi outlet tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = etExpenseName.getText().toString().trim();
        String rawNominal = etExpenseNominal.getText().toString().trim();
        long amount = 0L;
        try {
            if (!rawNominal.isEmpty()) amount = Long.parseLong(rawNominal);
        } catch (NumberFormatException ignored) {}

        if (title.isEmpty()) {
            Toast.makeText(this, "Nama/keterangan pengeluaran wajib diisi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0L) {
            Toast.makeText(this, "Nominal pengeluaran wajib diisi", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingReceiptBitmap == null) {
            Toast.makeText(this, "Foto bukti nota wajib dilampirkan!", Toast.LENGTH_SHORT).show();
            return;
        }

        final Bitmap receiptToSave = pendingReceiptBitmap;
        final String encodedReceipt = encodeBitmapToBase64(receiptToSave);
        final long finalAmount = amount;

        JsonObject payload = new JsonObject();
        payload.addProperty("outlet_id", outletId);
        payload.addProperty("title", title);
        payload.addProperty("amount", amount);
        payload.addProperty("category", selectedCategory);
        if (!encodedReceipt.isEmpty()) {
            payload.addProperty("receipt_url", encodedReceipt);
        }

        pbExpense.setVisibility(View.VISIBLE);
        btnSubmitExpense.setEnabled(false);

        SupabaseClient.post("expenses", payload.toString(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    pbExpense.setVisibility(View.GONE);
                    btnSubmitExpense.setEnabled(true);
                    Toast.makeText(ExpenseActivity.this, "Gagal koneksi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String errBody = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    pbExpense.setVisibility(View.GONE);
                    btnSubmitExpense.setEnabled(true);
                    if (response.isSuccessful()) {
                        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                        saveReceiptBitmap(buildReceiptLookupKey(outletId, todayKey, title, finalAmount), receiptToSave);

                        etExpenseName.setText("");
                        etExpenseNominal.setText("");
                        resetReceiptAttachment();
                        Toast.makeText(ExpenseActivity.this, "Pengeluaran & bukti nota tersimpan!", Toast.LENGTH_SHORT).show();
                        loadExpensesFromSupabase();
                    } else {
                        new AlertDialog.Builder(ExpenseActivity.this)
                                .setTitle("Gagal Simpan (" + response.code() + ")")
                                .setMessage(errBody)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            }
        });
    }

    private void verifyOwnerPinAndRun(String actionTitle, Runnable onAuthorized) {
        String expectedPin = !currentOwnerPin.isEmpty() ? currentOwnerPin : sessionManager.getOwnerPin();
        if (expectedPin.isEmpty()) {
            expectedPin = fallbackKasirPin;
        }

        if (expectedPin == null || expectedPin.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(actionTitle)
                    .setMessage("Lanjutkan perubahan data pengeluaran ini?")
                    .setPositiveButton("Ya, Lanjutkan", (d, w) -> onAuthorized.run())
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        final String targetPin = expectedPin;
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (18 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        container.setBackgroundResource(R.drawable.bg_dialog_white);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🔐 Verifikasi PIN Owner");
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        container.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Masukkan 4-digit PIN Owner untuk " + actionTitle.toLowerCase(Locale.ROOT) + " catatan pengeluaran.");
        tvDesc.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
        tvDesc.setTextSize(11.5f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = (int) (4 * density);
        tvDesc.setLayoutParams(descLp);
        container.addView(tvDesc);

        EditText etPin = new EditText(this);
        etPin.setHint("••••");
        etPin.setGravity(Gravity.CENTER);
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        etPin.setTextSize(18f);
        etPin.setTypeface(null, Typeface.BOLD);
        etPin.setBackgroundResource(R.drawable.bg_input_field);
        etPin.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
        pinLp.topMargin = (int) (12 * density);
        etPin.setLayoutParams(pinLp);
        container.addView(etPin);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView btnVerify = new TextView(this);
        btnVerify.setText("Verifikasi PIN Owner");
        btnVerify.setTextColor(Color.WHITE);
        btnVerify.setTextSize(13f);
        btnVerify.setTypeface(null, Typeface.BOLD);
        btnVerify.setGravity(Gravity.CENTER);
        btnVerify.setBackgroundResource(R.drawable.bg_btn_navy);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * density));
        btnLp.topMargin = (int) (14 * density);
        btnVerify.setLayoutParams(btnLp);
        btnVerify.setOnClickListener(v -> {
            String entered = etPin.getText().toString().trim();
            if (entered.equals(targetPin)) {
                dialog.dismiss();
                onAuthorized.run();
            } else {
                Toast.makeText(this, "PIN Owner salah! Akses ditolak.", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(btnVerify);

        dialog.show();
    }

    private void showEditExpenseDialog(String expenseId, String oldLookupKey, String currentTitle, long currentAmount, String currentCat, String dateKey) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (18 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        container.setBackgroundResource(R.drawable.bg_dialog_white);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Edit Pengeluaran");
        tvHeader.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        tvHeader.setTextSize(16f);
        tvHeader.setTypeface(null, Typeface.BOLD);
        container.addView(tvHeader);

        EditText etTitle = new EditText(this);
        etTitle.setText(currentTitle);
        etTitle.setHint("Nama pengeluaran");
        etTitle.setTextSize(13f);
        etTitle.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        etTitle.setBackgroundResource(R.drawable.bg_input_field);
        int fieldPad = (int) (12 * density);
        etTitle.setPadding(fieldPad, fieldPad, fieldPad, fieldPad);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = (int) (12 * density);
        etTitle.setLayoutParams(titleLp);
        container.addView(etTitle);

        EditText etAmount = new EditText(this);
        etAmount.setText(String.valueOf(currentAmount));
        etAmount.setHint("Nominal (Rp)");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAmount.setTextSize(13f);
        etAmount.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        etAmount.setBackgroundResource(R.drawable.bg_input_field);
        etAmount.setPadding(fieldPad, fieldPad, fieldPad, fieldPad);
        LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        amtLp.topMargin = (int) (8 * density);
        etAmount.setLayoutParams(amtLp);
        container.addView(etAmount);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView btnSave = new TextView(this);
        btnSave.setText("Simpan Perubahan");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(13f);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackgroundResource(R.drawable.bg_btn_navy);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * density));
        saveLp.topMargin = (int) (14 * density);
        btnSave.setLayoutParams(saveLp);

        btnSave.setOnClickListener(v -> {
            String newTitle = etTitle.getText().toString().trim();
            long newAmt = 0L;
            try {
                newAmt = Long.parseLong(etAmount.getText().toString().trim());
            } catch (NumberFormatException ignored) {}

            if (newTitle.isEmpty() || newAmt <= 0L) {
                Toast.makeText(this, "Nama & nominal tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }

            JsonObject patchBody = new JsonObject();
            patchBody.addProperty("title", newTitle);
            patchBody.addProperty("amount", newAmt);

            final long finalNewAmt = newAmt;
            pbExpense.setVisibility(View.VISIBLE);
            SupabaseClient.patch("expenses?id=eq." + expenseId, patchBody.toString(), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        pbExpense.setVisibility(View.GONE);
                        Toast.makeText(ExpenseActivity.this, "Gagal memperbarui pengeluaran", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        pbExpense.setVisibility(View.GONE);
                        Bitmap existingReceipt = getReceiptBitmap(oldLookupKey);
                        if (existingReceipt != null) {
                            receiptPrefs.edit().remove(oldLookupKey).apply();
                            String newKey = buildReceiptLookupKey(sessionManager.getOutletId(), dateKey, newTitle, finalNewAmt);
                            saveReceiptBitmap(newKey, existingReceipt);
                        }
                        dialog.dismiss();
                        Toast.makeText(ExpenseActivity.this, "Pengeluaran diperbarui!", Toast.LENGTH_SHORT).show();
                        loadExpensesFromSupabase();
                    });
                }
            });
        });

        container.addView(btnSave);
        dialog.show();
    }

    private void deleteExpenseFromSupabase(String expenseId, String lookupKey, String title) {
        if (expenseId.isEmpty()) return;

        Request request = new Request.Builder()
                .url(SupabaseClient.SUPABASE_URL + "expenses?id=eq." + expenseId)
                .addHeader("apikey", SupabaseClient.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SupabaseClient.SUPABASE_KEY)
                .delete()
                .build();

        deleteClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(ExpenseActivity.this, "Gagal menghapus data", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    receiptPrefs.edit().remove(lookupKey).apply();
                    Toast.makeText(ExpenseActivity.this, title + " dihapus", Toast.LENGTH_SHORT).show();
                    loadExpensesFromSupabase();
                });
            }
        });
    }

    private void renderExpenseList(JsonArray expenseArray) {
        llExpenseList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        String outletId = sessionManager.getOutletId();
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        long totalDaily = 0L;

        for (int i = 0; i < expenseArray.size(); i++) {
            JsonObject item = expenseArray.get(i).getAsJsonObject();
            String itemDateKey = extractLocalDateKey(item);
            if (!itemDateKey.equals(todayKey)) continue;

            String id = getStr(item, "id", "");
            String title = getStr(item, "title", getStr(item, "name", "Pengeluaran"));
            long amount = getLong(item, "amount", getLong(item, "nominal", 0L));
            String category = getStr(item, "category", "Bahan Kimia/Sabun");
            String time = extractLocalTime(item);
            String picName = getStr(item, "pic_name", "Kasir");
            String remoteReceiptUrl = getStr(item, "receipt_url", "");

            totalDaily += amount;

            View card = inflater.inflate(R.layout.item_expense, llExpenseList, false);
            ImageView ivExpIcon = card.findViewById(R.id.ivExpIcon);
            TextView tvExpTitle = card.findViewById(R.id.tvExpTitle);
            TextView tvExpAmount = card.findViewById(R.id.tvExpAmount);
            TextView tvExpMeta = card.findViewById(R.id.tvExpMeta);
            TextView tvExpCategory = card.findViewById(R.id.tvExpCategory);
            TextView tvExpViewReceipt = card.findViewById(R.id.tvExpViewReceipt);
            ImageView btnEditExp = card.findViewById(R.id.btnEditExp);
            ImageView btnDeleteExp = card.findViewById(R.id.btnDeleteExp);

            ivExpIcon.setImageResource(getCategoryIconRes(category));
            tvExpTitle.setText(title);
            tvExpAmount.setText(formatRupiahId(amount));
            tvExpMeta.setText(" • " + time + " WIB • " + picName);
            tvExpCategory.setText(category);

            String lookupKey = buildReceiptLookupKey(outletId, itemDateKey, title, amount);
            Bitmap savedReceipt = decodeBase64ToBitmap(remoteReceiptUrl);
            if (savedReceipt == null) {
                savedReceipt = getReceiptBitmap(lookupKey);
            }

            if (savedReceipt != null) {
                final Bitmap finalReceipt = savedReceipt;
                tvExpViewReceipt.setVisibility(View.VISIBLE);
                tvExpViewReceipt.setOnClickListener(v -> showReceiptPreviewDialog(finalReceipt, title));
            } else {
                tvExpViewReceipt.setVisibility(View.GONE);
            }

            btnEditExp.setOnClickListener(v ->
                    verifyOwnerPinAndRun("Edit Pengeluaran", () ->
                            showEditExpenseDialog(id, lookupKey, title, amount, category, itemDateKey)));

            btnDeleteExp.setOnClickListener(v ->
                    verifyOwnerPinAndRun("Hapus Pengeluaran", () ->
                            deleteExpenseFromSupabase(id, lookupKey, title)));

            llExpenseList.addView(card);
        }

        if (llExpenseList.getChildCount() == 0) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Belum ada catatan pengeluaran hari ini.");
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.slate_gray));
            tvEmpty.setTextSize(12f);
            tvEmpty.setGravity(Gravity.CENTER);
            int padV = (int) (16 * getResources().getDisplayMetrics().density);
            tvEmpty.setPadding(0, padV, 0, padV);
            llExpenseList.addView(tvEmpty);
        }

        tvTotalDailyExpense.setText(formatRupiahId(totalDaily));
    }

    private void showReceiptPreviewDialog(Bitmap bitmap, String title) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        container.setBackgroundResource(R.drawable.bg_dialog_white);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Bukti Nota: " + title);
        tvHeader.setTextColor(ContextCompat.getColor(this, R.color.deep_slate_navy));
        tvHeader.setTextSize(15f);
        tvHeader.setTypeface(null, Typeface.BOLD);
        container.addView(tvHeader);

        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (340 * density)
        );
        imgParams.topMargin = (int) (12 * density);
        imageView.setLayoutParams(imgParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(bitmap);
        container.addView(imageView);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView btnClose = new TextView(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (42 * density)
        );
        btnParams.topMargin = (int) (14 * density);
        btnClose.setLayoutParams(btnParams);
        btnClose.setBackgroundResource(R.drawable.bg_btn_navy);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setText("Tutup");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(13f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnClose);

        dialog.show();
    }

    private String buildReceiptLookupKey(String outletId, String dateKey, String title, long amount) {
        return outletId + "_" + dateKey + "_" + title.trim().toLowerCase(Locale.ROOT) + "_" + amount;
    }

    private String encodeBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private Bitmap decodeBase64ToBitmap(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return null;
        try {
            String clean = encoded.contains(",") ? encoded.substring(encoded.indexOf(",") + 1) : encoded;
            byte[] bytes = Base64.decode(clean.trim(), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveReceiptBitmap(String key, Bitmap bitmap) {
        if (bitmap == null) return;
        String encoded = encodeBitmapToBase64(bitmap);
        if (!encoded.isEmpty()) {
            receiptPrefs.edit().putString(key, encoded).apply();
        }
    }

    private Bitmap getReceiptBitmap(String key) {
        String encoded = receiptPrefs.getString(key, "");
        return decodeBase64ToBitmap(encoded);
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

    private String resolveFileName(Uri uri) {
        String result = "bukti_nota.jpg";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private int estimateCompressedKb(Bitmap bitmap) {
        if (bitmap == null) return 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, baos);
        return Math.max(1, baos.size() / 1024);
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

    private String extractLocalTime(JsonObject obj) {
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            String createdAt = obj.get("created_at").getAsString();
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
            if (createdAt.length() >= 16) return createdAt.substring(11, 16);
        }
        return "00:00";
    }

    private String formatRupiahId(long amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        return "Rp " + formatter.format(amount);
    }

    private String getStr(JsonObject obj, String key, String fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : fallback;
    }

    private long getLong(JsonObject obj, String key, long fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsLong() : fallback;
    }
}