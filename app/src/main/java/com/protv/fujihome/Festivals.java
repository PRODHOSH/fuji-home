package com.protv.fujihome;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Optional festival and vratham days on Home, from assets/tamil/days.json. It is not shipped in this repo: make it
 * for yourself with tools/fetch_tamil_calendar.py (it reads the public lists on tamildailycalendar.com), or write
 * your own as {"2026-11-08": {"fest": ["தீபாவளி"], "days": ["அமாவாசை"], "wp": "fest_diya"}}. Without it nothing shows.
 */
final class Festivals {
    static final class Today {
        String text;        // "திருக்கார்த்திகை · பௌர்ணமி"
        String wallpaper;   // "fest_diya" / "fest_pongal" on Pongal and Deepavali days, else null
        boolean festival;
    }

    private static JSONObject all;

    private Festivals() {}

    /** What the calendar lists for this date, or null for an ordinary day. */
    static Today on(Context context, Date date) {
        if (!context.getResources().getBoolean(R.bool.addon_tamil_calendar)) return null;
        try {
            if (all == null) {
                try (InputStream in = context.getAssets().open("tamil/days.json")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    all = new JSONObject(new String(out.toByteArray(), "UTF-8"));
                }
            }
            JSONObject day = all.optJSONObject(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date));
            if (day == null) return null;
            // Short: the first festival and the first vratham day at most, as the calendar names them.
            List<String> parts = new ArrayList<>();
            JSONArray fest = day.optJSONArray("fest");
            JSONArray days = day.optJSONArray("days");
            if (fest != null && fest.length() > 0) parts.add(fest.getString(0));
            if (days != null && days.length() > 0) parts.add(days.getString(0));
            if (parts.isEmpty()) return null;
            Today t = new Today();
            t.text = TextUtils.join(" · ", parts);
            t.festival = fest != null && fest.length() > 0;
            String wp = day.optString("wp", "");
            t.wallpaper = wp.isEmpty() ? null : wp;
            return t;
        } catch (IOException | org.json.JSONException e) {
            return null;
        }
    }

    /** For the Home date line: "🪔 தீபாவளி பண்டிகை · அமாவாசை", "ஏகாதசி". */
    static String line(Today t) {
        if (t == null) return "";
        String icon = "fest_diya".equals(t.wallpaper) ? "🪔 "
                : "fest_pongal".equals(t.wallpaper) ? "🌾 "
                : t.festival ? "✨ " : "";
        return icon + t.text;
    }
}
