package com.washlog.washlog2;

import java.util.concurrent.TimeUnit;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class SupabaseClient {
    public static final String SUPABASE_URL = "https://ktcpyqyhcxninubxxppz.supabase.co/rest/v1/";
    public static final String SUPABASE_KEY = "sb_publishable_-BBVTi-mLdsMynJIA4ZJWQ_XRaQYMhs";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Timeout diset 45 detik agar aman saat jaringan seluler lambat
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build();

    // Ambil data (SELECT)
    public static void get(String endpoint, Callback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + endpoint)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .get()
                .build();
        client.newCall(request).enqueue(callback);
    }

    // Kirim data baru (INSERT)
    public static void post(String endpoint, String jsonBody, Callback callback) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(SUPABASE_URL + endpoint)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(body)
                .build();
        client.newCall(request).enqueue(callback);
    }

    // Ubah data (UPDATE)
    public static void patch(String endpoint, String jsonBody, Callback callback) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(SUPABASE_URL + endpoint)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(body)
                .build();
        client.newCall(request).enqueue(callback);
    }
}