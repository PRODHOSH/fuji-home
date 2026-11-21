package com.protv.fujihome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The system's "Continue watching" list (TvContract.WatchNextPrograms) that streaming apps publish. Read-only:
 * this app never writes to it. Apps that publish nothing simply don't appear.
 */
final class WatchNext {
    static final class Item {
        String pkg, title, episodeTitle, imageUri, intentUri;
        long position, duration, lastEngaged;
        String season, episode;
    }

    private static final String[] COLUMNS = {
            "package_name", "title", "episode_title", "poster_art_uri", "thumbnail_uri", "intent_uri",
            "last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis",
            "season_display_number", "episode_display_number", "browsable"};

    private WatchNext() {}

    /** Newest first, at most {@code limit}; empty when nothing is published or the provider refuses. */
    static List<Item> read(Context context, int limit) {
        List<Item> items = new ArrayList<>();
        // No selection: a reader without ACCESS_ALL_EPG_DATA gets a SecurityException when it passes one.
        try (Cursor c = context.getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null)) {
            if (c == null) return items;
            while (c.moveToNext()) {
                if (!c.isNull(11) && c.getInt(11) == 0) continue;     // not browsable
                Item it = new Item();
                it.pkg = c.getString(0);
                it.title = c.getString(1);
                it.episodeTitle = c.getString(2);
                String thumb = c.getString(4);
                it.imageUri = thumb != null && !thumb.isEmpty() ? thumb : c.getString(3);
                it.intentUri = c.getString(5);
                it.position = c.isNull(6) ? 0 : c.getLong(6);
                it.duration = c.isNull(7) ? 0 : c.getLong(7);
                it.lastEngaged = c.isNull(8) ? 0 : c.getLong(8);
                it.season = c.getString(9);
                it.episode = c.getString(10);
                if (it.pkg == null || it.title == null || it.title.isEmpty()) continue;
                items.add(it);
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        Collections.sort(items, (a, b) -> Long.compare(b.lastEngaged, a.lastEngaged));
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    /**
     * A small copy of the card picture, about {@code targetW} wide; null if it cannot be loaded. Pictures are kept
     * in the app's cache folder, so after a restart the cards show at once instead of waiting for Wi-Fi.
     */
    static Bitmap loadImage(Context context, String uri, int targetW, int targetH) {
        if (uri == null || uri.isEmpty()) return null;
        File dir = new File(context.getCacheDir(), "posters");
        File cached = new File(dir, Integer.toHexString(uri.hashCode()) + "_" + uri.length() + ".img");
        byte[] data = null;
        try {
            if (cached.isFile()) data = readAll(new FileInputStream(cached));
            if (data == null) {
                data = uri.startsWith("http") ? fetch(uri) : readLocal(context.getContentResolver(), Uri.parse(uri));
                if (data != null && (dir.isDirectory() || dir.mkdirs())) {
                    try (FileOutputStream out = new FileOutputStream(cached)) {
                        out.write(data);
                    }
                    trim(dir, 40);
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= targetW && o.outHeight / (sample * 2) >= targetH) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private static byte[] fetch(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(6000);
        conn.setInstanceFollowRedirects(true);
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                return readAll(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readLocal(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            return in == null ? null : readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = stream.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 4 * 1024 * 1024) return null;        // not a card picture
            }
            return out.toByteArray();
        }
    }

    /** Keep only the newest {@code keep} cached pictures. */
    private static void trim(File dir, int keep) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= keep) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < files.length; i++) files[i].delete();
    }

    /** Changes when the cards would look different; equal lists need no rebuild. */
    static String signature(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            sb.append(it.pkg).append('|').append(it.title).append('|').append(it.season).append('|')
                    .append(it.episode).append('|').append(it.position / 60000).append('|').append(it.imageUri)
                    .append('\n');
        }
        return sb.toString();
    }
}
