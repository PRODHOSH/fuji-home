package com.protv.fujihome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The wallpapers bundled in assets/wallpapers (made by tools/make_wallpapers.py): each is <id>.webp, a preview
 * <id>_thumb.webp, and for depth wallpapers <id>_fg.webp, the scenery drawn over the clock. Only the chosen one is
 * ever loaded.
 */
final class Wallpapers {
    static final String PREF = "wallpaper";
    static final String DEFAULT = "fuji";

    static final class Entry {
        String id, name;
        boolean depth;
    }

    private Wallpapers() {}

    static List<Entry> list(Context context) {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = context.getAssets().open("wallpapers/index.json")) {
            JSONArray arr = new JSONArray(new String(readAll(in), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optBoolean("festival")) continue;        // only on their festival days
                Entry e = new Entry();
                e.id = o.getString("id");
                e.name = o.getString("name");
                e.depth = o.optBoolean("depth");
                out.add(e);
            }
        } catch (IOException | org.json.JSONException ignored) {
        }
        return out;
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    static String current(Context context) {
        return prefs(context).getString(PREF, DEFAULT);
    }

    static void choose(Context context, String id) {
        prefs(context).edit().putString(PREF, id).apply();
    }

    /** The chosen wallpaper (falling back to Mount Fuji if it is missing). */
    static Bitmap background(Context context, String id) {
        Bitmap b = decode(context, "wallpapers/" + id + ".webp", 1);
        return b != null ? b : decode(context, "wallpapers/" + DEFAULT + ".webp", 1);
    }

    /** The scenery cut-out for a depth wallpaper; null for the others. */
    static Bitmap foreground(Context context, String id) {
        return decode(context, "wallpapers/" + id + "_fg.webp", 1);
    }

    static Bitmap thumbnail(Context context, String id) {
        return decode(context, "wallpapers/" + id + "_thumb.webp", 1);
    }

    /** A small copy for a blurred-looking backdrop (All apps): a quarter of the size, stretched back up. */
    static Bitmap small(Context context, String id) {
        return decode(context, "wallpapers/" + id + ".webp", 4);
    }

    private static Bitmap decode(Context context, String asset, int sample) {
        try (InputStream in = context.getAssets().open(asset)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
