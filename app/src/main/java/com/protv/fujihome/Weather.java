package com.protv.fujihome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The weather for the city in res/values/config.xml, from Open-Meteo (free, no account; only the city's map
 * position is sent). Checked at most
 * every 30 minutes while Home is on screen; the last answer is kept so it shows at once after a restart.
 */
final class Weather {
    /** Your city, from res/values/config.xml. */
    private static String url(Context c) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + c.getString(R.string.weather_latitude)
                + "&longitude=" + c.getString(R.string.weather_longitude)
                + "&current=temperature_2m,weather_code,is_day&timezone="
                + c.getString(R.string.weather_timezone).replace("/", "%2F");
    }
    static final long REFRESH_MS = 30 * 60 * 1000L;
    /** Older than this, the saved weather is not shown at all. */
    private static final long STALE_MS = 3 * 60 * 60 * 1000L;

    static final class Now {
        int code, temp;
        boolean day;
        long fetched;

        String line() {
            return icon() + " " + temp + "° " + words();
        }

        private String icon() {
            if (code >= 95) return "⛈️";                     // thunder
            if (code >= 80 || (code >= 61 && code <= 67)) return "🌧️";   // rain
            if (code >= 51 && code <= 57) return "🌦️"; // drizzle
            if (code == 45 || code == 48) return "🌫️"; // fog
            if (code == 3) return "☁️";                      // overcast
            if (code == 1 || code == 2) return day ? "⛅" : "☁️";
            return day ? "☀️" : "🌙";               // clear: sun or moon
        }

        private String words() {
            if (code >= 95) return "Thunderstorm";
            if (code >= 80) return "Showers";
            if (code >= 61 && code <= 67) return code >= 65 ? "Heavy rain" : "Rain";
            if (code >= 51 && code <= 57) return "Drizzle";
            if (code == 45 || code == 48) return "Fog";
            if (code == 3) return "Cloudy";
            if (code == 1 || code == 2) return "Partly cloudy";
            return "Clear";
        }
    }

    private Weather() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    /** The last weather fetched, or null if there is none recent enough to show. */
    static Now saved(Context c) {
        SharedPreferences p = prefs(c);
        long at = p.getLong("fetched", 0);
        if (at == 0 || System.currentTimeMillis() - at > STALE_MS) return null;
        Now n = new Now();
        n.code = p.getInt("code", 0);
        n.temp = p.getInt("temp", 0);
        n.day = p.getBoolean("day", true);
        n.fetched = at;
        return n;
    }

    static boolean due(Context c) {
        return System.currentTimeMillis() - prefs(c).getLong("fetched", 0) > REFRESH_MS;
    }

    /** Ask Open-Meteo now (call off the main thread). Null if the TV is offline or the answer is unreadable. */
    static Now fetch(Context c) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url(c)).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;
            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
                body = out.toByteArray();
            }
            JSONObject cur = new JSONObject(new String(body, "UTF-8")).getJSONObject("current");
            Now now = new Now();
            now.code = cur.getInt("weather_code");
            now.temp = (int) Math.round(cur.getDouble("temperature_2m"));
            now.day = cur.optInt("is_day", 1) == 1;
            now.fetched = System.currentTimeMillis();
            prefs(c).edit().putInt("code", now.code).putInt("temp", now.temp).putBoolean("day", now.day)
                    .putLong("fetched", now.fetched).apply();
            return now;
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
