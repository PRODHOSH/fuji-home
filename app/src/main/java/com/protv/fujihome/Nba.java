package com.protv.fujihome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Your NBA team on Home (res/values/config.xml): the live score during a game, otherwise the next game (in
 * the TV's own timezone) or the last result. From ESPN's public scoreboard and team schedule (no account; results checked against
 * Basketball-Reference). Read off the main thread; the last answer is kept so Home shows it at once.
 */
final class Nba {
    /** The team, from res/values/config.xml (ESPN's abbreviation, upper case); empty means no pill. */
    private static String TEAM = "GS";
    private static final String SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard";
    private static final String SCHEDULE = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/%s/schedule";

    static boolean enabled(Context c) {
        TEAM = c.getString(R.string.nba_team).trim().toUpperCase(Locale.US);
        return !TEAM.isEmpty();
    }
    private static final String LOGO = "https://a.espncdn.com/i/teamlogos/nba/500/%s.png";

    /** One scoreboard line: kind is "live", "next" or "final". */
    static final class Game {
        String kind, us = "GS", them = "", usScore = "", themScore = "", detail = "";
        boolean win;

        String toJson() {
            try {
                return new JSONObject().put("kind", kind).put("us", us).put("them", them).put("usScore", usScore)
                        .put("themScore", themScore).put("detail", detail).put("win", win).toString();
            } catch (org.json.JSONException e) {
                return "";
            }
        }

        static Game fromJson(String s) {
            try {
                JSONObject o = new JSONObject(s);
                Game g = new Game();
                g.kind = o.getString("kind");
                g.us = o.getString("us");
                g.them = o.getString("them");
                g.usScore = o.optString("usScore");
                g.themScore = o.optString("themScore");
                g.detail = o.optString("detail");
                g.win = o.optBoolean("win");
                return g;
            } catch (org.json.JSONException e) {
                return null;
            }
        }
    }

    private Nba() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nba", Context.MODE_PRIVATE);
    }

    static Game saved(Context c) {
        if (!enabled(c)) return null;
        String s = prefs(c).getString("game", "");
        return s.isEmpty() ? null : Game.fromJson(s);
    }

    /** Team abbreviation as fans write it (ESPN uses a few short forms). */
    static String name(String espn) {
        switch (espn) {
            case "GS": return "GSW";
            case "NO": return "NOP";
            case "NY": return "NYK";
            case "SA": return "SAS";
            case "UTAH": return "UTA";
            case "WSH": return "WAS";
            default: return espn;
        }
    }

    /** The team's logo, downloaded once and kept on the TV; null if unavailable. Call off the main thread. */
    static Bitmap logo(Context c, String espn) {
        File dir = new File(c.getCacheDir(), "nba_logos");
        File f = new File(dir, espn.toLowerCase(Locale.US) + ".png");
        try {
            if (!f.isFile()) {
                byte[] data = download(String.format(Locale.US, LOGO, espn.toLowerCase(Locale.US)));
                if (data == null || (!dir.isDirectory() && !dir.mkdirs())) return null;
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;                                   // 500 px source; the badge is about 26 dp
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Ask now (off the main thread). Null if offline or the answer can't be read. */
    static Game fetch(Context c) {
        if (!enabled(c)) return null;
        try {
            JSONObject today = find(get(SCOREBOARD).optJSONArray("events"));
            Game game = null;
            if (today != null && "in".equals(state(today))) game = make(today, "live");
            if (game == null) {
                JSONArray schedule = get(String.format(Locale.US, SCHEDULE, TEAM.toLowerCase(Locale.US))).optJSONArray("events");
                JSONObject last = null, next = null;
                for (int i = 0; schedule != null && i < schedule.length(); i++) {
                    JSONObject e = schedule.getJSONObject(i);
                    String st = state(e);
                    if ("post".equals(st)) last = e;
                    else if ("pre".equals(st) && next == null) next = e;
                    else if ("in".equals(st)) game = make(e, "live");
                }
                if (game == null) {
                    // The next game when it is within 3 days, else the last result.
                    if (next != null && start(next) - System.currentTimeMillis() < 3 * 86_400_000L) game = make(next, "next");
                    else if (last != null) game = make(last, "final");
                    else if (next != null) game = make(next, "next");
                }
            }
            if (game != null) prefs(c).edit().putString("game", game.toJson()).apply();
            return game;
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            return null;
        }
    }

    private static Game make(JSONObject e, String kind) throws org.json.JSONException {
        JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject us = team(comp, true), them = team(comp, false);
        Game g = new Game();
        g.kind = kind;
        g.us = us.getJSONObject("team").optString("abbreviation", TEAM);
        g.them = them.getJSONObject("team").optString("abbreviation", "");
        if ("next".equals(kind)) {
            SimpleDateFormat f = new SimpleDateFormat("EEE h:mm a", Locale.ENGLISH);
            f.setTimeZone(TimeZone.getDefault());
            g.detail = f.format(new Date(start(e)));
            g.win = "home".equals(us.optString("homeAway"));           // for "vs" (home) or "at" (away)
        } else {
            g.usScore = points(us);
            g.themScore = points(them);
            JSONObject status = comp.optJSONObject("status");
            if (status == null) status = e.getJSONObject("status");
            g.detail = "live".equals(kind) ? status.getJSONObject("type").optString("shortDetail", "") : "";
            g.win = us.optBoolean("winner");
        }
        return g;
    }

    private static JSONObject team(JSONObject comp, boolean us) throws org.json.JSONException {
        JSONArray cs = comp.getJSONArray("competitors");
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.getJSONObject(i);
            if (TEAM.equals(c.getJSONObject("team").optString("abbreviation")) == us) return c;
        }
        return cs.getJSONObject(0);
    }

    /** Score as text: the scoreboard gives a string, the schedule an object. */
    private static String points(JSONObject competitor) {
        JSONObject s = competitor.optJSONObject("score");
        if (s != null) return s.optString("displayValue", "0");
        return competitor.optString("score", "0");
    }

    private static JSONObject find(JSONArray events) throws org.json.JSONException {
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            JSONArray cs = e.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors");
            for (int j = 0; j < cs.length(); j++) {
                if (TEAM.equals(cs.getJSONObject(j).getJSONObject("team").optString("abbreviation"))) return e;
            }
        }
        return null;
    }

    private static String state(JSONObject e) throws org.json.JSONException {
        JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject status = comp.optJSONObject("status");
        if (status == null) status = e.optJSONObject("status");
        return status == null ? "" : status.getJSONObject("type").optString("state");
    }

    private static long start(JSONObject e) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(e.getString("date")).getTime();
        } catch (Exception ex) {
            return Long.MAX_VALUE;
        }
    }

    private static JSONObject get(String address) throws IOException, org.json.JSONException {
        byte[] data = download(address);
        if (data == null) throw new IOException("no answer");
        return new JSONObject(new String(data, "UTF-8"));
    }

    private static byte[] download(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 4 * 1024 * 1024) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }
}
