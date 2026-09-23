package com.nerva.earthwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

final class AssetData {
    private AssetData() {}
    static Bitmap decode(String[] parts) {
        int len = 0; for (String p : parts) len += p.length();
        StringBuilder s = new StringBuilder(len); for (String p : parts) s.append(p);
        byte[] bytes = Base64.decode(s.toString(), Base64.NO_WRAP);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
