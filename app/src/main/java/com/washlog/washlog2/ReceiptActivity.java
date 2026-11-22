package com.washlog.washlog2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ReceiptActivity extends AppCompatActivity {

    public static final String EXTRA_MOTOR_NAME = "extra_motor_name";
    public static final String EXTRA_PLAT_NO = "extra_plat_no";
    public static final String EXTRA_WASHER_NAME = "extra_washer_name";
    public static final String EXTRA_PRICE = "extra_price";
    public static final String EXTRA_PAY_METHOD = "extra_pay_method";
    public static final String EXTRA_CUSTOMER_PHONE = "extra_customer_phone";

    private SessionManager sessionManager;
    private TextView tvReceiptHeader, tvReceiptNumber, tvReceiptTime;
    private ImageView ivReceiptMotorIcon;
    private TextView tvReceiptMotorName, tvReceiptPlatBox, tvReceiptWasher, tvReceiptPayMethod;
    private TextView tvReceiptTotalPrice, tvReceiptCommLabel, tvReceiptCommAmount;
    private EditText etTargetPhone;

    private String outletName = "Outlet WashLog";
    private String receiptNumber = "";
    private String formattedEntryTime = "";
    private String motorName = "Motor Kecil";
    private String platNo = "-";
    private String washerName = "Kasir";
    private String payMethod = "Tunai";
    private double price = 15000;
    private int commissionPct = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);

        View topBar = findViewById(R.id.topBarReceipt);
        View rootReceipt = findViewById(R.id.rootReceipt);
        float density = getResources().getDisplayMetrics().density;
        int topPadH = (int) (16 * density);
        int topPadV = (int) (12 * density);

        ViewCompat.setOnApplyWindowInsetsListener(rootReceipt, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(topPadH, topPadV + sysBars.top, topPadH, topPadV);
            v.setPadding(0, 0, 0, sysBars.bottom);
            return insets;
        });

        sessionManager = new SessionManager(this);

        tvReceiptHeader = findViewById(R.id.tvReceiptHeader);
        tvReceiptNumber = findViewById(R.id.tvReceiptNumber);
        tvReceiptTime = findViewById(R.id.tvReceiptTime);
        ivReceiptMotorIcon = findViewById(R.id.ivReceiptMotorIcon);
        tvReceiptMotorName = findViewById(R.id.tvReceiptMotorName);
        tvReceiptPlatBox = findViewById(R.id.tvReceiptPlatBox);
        tvReceiptWasher = findViewById(R.id.tvReceiptWasher);
        tvReceiptPayMethod = findViewById(R.id.tvReceiptPayMethod);
        tvReceiptTotalPrice = findViewById(R.id.tvReceiptTotalPrice);
        tvReceiptCommLabel = findViewById(R.id.tvReceiptCommLabel);
        tvReceiptCommAmount = findViewById(R.id.tvReceiptCommAmount);
        etTargetPhone = findViewById(R.id.etTargetPhone);

        if (!sessionManager.getOutletName().isEmpty()) {
            outletName = sessionManager.getOutletName();
        }
        tvReceiptHeader.setText("WASHLOG • " + outletName.toUpperCase(Locale.ROOT));

        Date now = new Date();
        String datePart = new SimpleDateFormat("yyyyMMdd", Locale.US).format(now);
        long seqPart = System.currentTimeMillis() % 1000;
        receiptNumber = String.format(Locale.US, "#WNL-%s-%03d", datePart, seqPart);
        formattedEntryTime = new SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", new Locale("id", "ID")).format(now);

        tvReceiptNumber.setText(receiptNumber);
        tvReceiptTime.setText(formattedEntryTime);

        Intent intent = getIntent();
        boolean hasTxExtra = false;
        if (intent != null) {
            if (intent.hasExtra(EXTRA_MOTOR_NAME)) {
                motorName = intent.getStringExtra(EXTRA_MOTOR_NAME);
                hasTxExtra = true;
            }
            if (intent.hasExtra(EXTRA_PLAT_NO)) {
                platNo = intent.getStringExtra(EXTRA_PLAT_NO);
            }
            if (intent.hasExtra(EXTRA_WASHER_NAME)) {
                washerName = intent.getStringExtra(EXTRA_WASHER_NAME);
            }
            if (intent.hasExtra(EXTRA_PRICE)) {
                price = intent.getDoubleExtra(EXTRA_PRICE, 15000);
            }
            if (intent.hasExtra(EXTRA_PAY_METHOD)) {
                payMethod = intent.getStringExtra(EXTRA_PAY_METHOD);
            }
            if (intent.hasExtra(EXTRA_CUSTOMER_PHONE)) {
                String phone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE);
                if (phone != null && !phone.isEmpty()) {
                    etTargetPhone.setText(phone);
                }
            }
        }

        renderReceiptData();
        fetchCommissionSetting();

        if (!hasTxExtra) {
            fetchLatestTransaction();
        }

        findViewById(R.id.btnBackReceipt).setOnClickListener(v -> finish());
        findViewById(R.id.btnSendReceiptWa).setOnClickListener(v -> sendReceiptViaWhatsApp());

        findViewById(R.id.btnPrintThermal).setOnClickListener(v ->
                Toast.makeText(this, "Mencetak Nota via Mini Thermal Bluetooth...", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btnDoneReceipt).setOnClickListener(v -> {
            Intent homeIntent = new Intent(ReceiptActivity.this, MainActivity.class);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(homeIntent);
            finish();
        });
    }

    private void renderReceiptData() {
        String safeMotor = (motorName == null || motorName.isEmpty()) ? "Motor Kecil" : motorName;
        String safePlat = (platNo == null || platNo.isEmpty()) ? "-" : platNo;
        String safeWasher = (washerName == null || washerName.isEmpty()) ? "Kasir" : washerName;
        String safePay = (payMethod == null || payMethod.isEmpty()) ? "Tunai" : payMethod;

        String lowerMotor = safeMotor.toLowerCase(Locale.ROOT);
        if (lowerMotor.contains("besar") || lowerMotor.contains("sport") || lowerMotor.contains("moge")) {
            ivReceiptMotorIcon.setBackgroundResource(R.drawable.bg_btn_navy);
            ivReceiptMotorIcon.setImageResource(R.drawable.ic_motor_besar);
        } else if (lowerMotor.contains("sedang") || lowerMotor.contains("nmax") || lowerMotor.contains("pcx") || lowerMotor.contains("vario")) {
            ivReceiptMotorIcon.setBackgroundResource(R.drawable.bg_banner_blue);
            ivReceiptMotorIcon.setImageResource(R.drawable.ic_motor_sedang);
        } else {
            ivReceiptMotorIcon.setBackgroundResource(R.drawable.bg_banner_blue);
            ivReceiptMotorIcon.setImageResource(R.drawable.ic_motor_kecil);
        }

        tvReceiptMotorName.setText(safeMotor);
        tvReceiptPlatBox.setText(safePlat);
        tvReceiptWasher.setText(safeWasher);
        tvReceiptPayMethod.setText(safePay);
        tvReceiptTotalPrice.setText(formatRupiah(price));

        double workerComm = price * (commissionPct / 100.0);
        tvReceiptCommLabel.setText("Komisi Worker (" + commissionPct + "% Otomatis)");
        tvReceiptCommAmount.setText(formatRupiah(workerComm));
    }

    private void fetchCommissionSetting() {
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
                        JsonObject obj = arr.get(0).getAsJsonObject();
                        if (obj.has("worker_commission_pct") && !obj.get("worker_commission_pct").isJsonNull()) {
                            commissionPct = obj.get("worker_commission_pct").getAsInt();
                            renderReceiptData();
                        }
                    }
                });
            }
        });
    }

    private void fetchLatestTransaction() {
        String outletId = sessionManager.getOutletId();
        if (outletId.isEmpty()) return;

        SupabaseClient.get("transactions?outlet_id=eq." + outletId + "&order=created_at.desc&limit=1&select=*", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                runOnUiThread(() -> {
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    if (!arr.isEmpty()) {
                        JsonObject tx = arr.get(0).getAsJsonObject();
                        motorName = getStr(tx, "motor_name", getStr(tx, "category", motorName));
                        platNo = getStr(tx, "plate_number", getStr(tx, "plat_no", platNo));
                        washerName = getStr(tx, "worker_name", getStr(tx, "washer_name", washerName));
                        payMethod = getStr(tx, "payment_method", getStr(tx, "pay_method", payMethod));
                        if (tx.has("price") && !tx.get("price").isJsonNull()) {
                            price = tx.get("price").getAsDouble();
                        }
                        renderReceiptData();
                    }
                });
            }
        });
    }

    private void sendReceiptViaWhatsApp() {
        String rawPhone = etTargetPhone.getText().toString().trim();

        String message = "*STRUK DIGITAL — " + outletName.toUpperCase(Locale.ROOT) + "*\n"
                + "────────────────────\n"
                + "No. Struk : *" + receiptNumber + "*\n"
                + "Waktu     : " + formattedEntryTime + "\n\n"
                + "*Detail Layanan:*\n"
                + "• Kendaraan  : " + motorName + " (" + platNo + ")\n"
                + "• Pencuci    : " + washerName + "\n"
                + "• Pembayaran : " + payMethod + " (LUNAS)\n\n"
                + "*Total Bayar : " + formatRupiah(price) + "*\n"
                + "────────────────────\n"
                + "Terima kasih sudah mempercayakan motornya di *" + outletName + "*. Hati-hati di jalan & ditunggu kedatangan berikutnya!";

        try {
            String url;
            if (!rawPhone.isEmpty()) {
                String cleanPhone = rawPhone.replaceAll("[^0-9]", "");
                if (cleanPhone.startsWith("0")) {
                    cleanPhone = "62" + cleanPhone.substring(1);
                }
                url = "https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(message);
            } else {
                url = "https://api.whatsapp.com/send?text=" + Uri.encode(message);
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp tidak ditemukan di perangkat", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatRupiah(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        return "Rp " + formatter.format((long) amount);
    }

    private String getStr(JsonObject obj, String key, String fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : fallback;
    }
}