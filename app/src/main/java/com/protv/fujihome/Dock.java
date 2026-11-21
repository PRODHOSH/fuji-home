package com.protv.fujihome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The dock's apps and their order, as the family arranges them (long-press OK on a dock app: Move / Remove; in All
 * apps: Add to Dock). Kept in the app's preferences; until changed it is HomeActivity.DOCK.
 */
final class Dock {
    private static final String KEY = "dock_apps";

    private Dock() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    static List<String> load(Context c) {
        String saved = prefs(c).getString(KEY, null);
        if (saved == null) return new ArrayList<>(Arrays.asList(HomeActivity.DOCK));
        List<String> out = new ArrayList<>();
        for (String p : saved.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    /** Changes whenever the dock changes (Home rebuilds its row when it differs). */
    static String key(Context c) {
        return TextUtils.join(",", load(c));
    }

    static void save(Context c, List<String> apps) {
        prefs(c).edit().putString(KEY, TextUtils.join(",", apps)).apply();
    }

    static boolean contains(Context c, String pkg) {
        return load(c).contains(pkg);
    }

    static void add(Context c, String pkg) {
        List<String> apps = load(c);
        if (!apps.contains(pkg)) {
            apps.add(pkg);
            save(c, apps);
        }
    }

    static void remove(Context c, String pkg) {
        List<String> apps = load(c);
        if (apps.remove(pkg)) save(c, apps);
    }
}
