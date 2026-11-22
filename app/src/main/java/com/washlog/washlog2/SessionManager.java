package com.washlog.washlog2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class SessionManager {
    private static final String PREF_NAME = "WashlogSession";
    private static final String KEY_IS_LOGIN = "is_login";
    private static final String KEY_OUTLET_ID = "outlet_id";
    private static final String KEY_OUTLET_NAME = "outlet_name";
    private static final String KEY_OWNER_PHONE = "owner_phone";
    private static final String KEY_OUTLET_PHOTO_PREFIX = "outlet_photo_";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void saveOutletSession(String id, String name, String phone) {
        editor.putBoolean(KEY_IS_LOGIN, true);
        editor.putString(KEY_OUTLET_ID, id);
        editor.putString(KEY_OUTLET_NAME, name);
        editor.putString(KEY_OWNER_PHONE, phone);
        editor.apply();
    }

    public void updateOutletInfo(String name, String phone) {
        if (name != null && !name.trim().isEmpty()) {
            editor.putString(KEY_OUTLET_NAME, name.trim());
        }
        if (phone != null && !phone.trim().isEmpty() && !phone.trim().equals("-")) {
            editor.putString(KEY_OWNER_PHONE, phone.trim());
        }
        editor.apply();
    }

    public void saveOutletPhoto(Bitmap bitmap) {
        if (bitmap == null) return;
        int maxDim = 256;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min((float) maxDim / width, (float) maxDim / height);
        Bitmap resized = (scale < 1f)
                ? Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true)
                : bitmap;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 82, baos);
        String encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        String key = KEY_OUTLET_PHOTO_PREFIX + getOutletId();
        editor.putString(key, encoded);
        editor.apply();
    }

    public Bitmap getOutletPhoto() {
        String key = KEY_OUTLET_PHOTO_PREFIX + getOutletId();
        String encoded = prefs.getString(key, "");
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGIN, false);
    }

    public String getOutletId() {
        return prefs.getString(KEY_OUTLET_ID, "");
    }

    public String getOutletName() {
        return prefs.getString(KEY_OUTLET_NAME, "Outlet WashLog");
    }

    public String getOwnerPhone() {
        return prefs.getString(KEY_OWNER_PHONE, "-");
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}