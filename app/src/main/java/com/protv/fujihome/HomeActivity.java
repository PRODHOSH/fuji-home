package com.protv.fujihome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fuji Home: the lock-screen look with a depth-effect clock, Keep watching, and your apps in a glass dock. */
public class HomeActivity extends Activity {
    /** The dock, in this order. Apps that are not installed are skipped; every other app is under ••• > All apps. */
    static final String[] DOCK = {
            "com.netflix.ninja",                   // Netflix
            "com.google.android.youtube.tv",       // YouTube
            "com.amazon.amazonvideo.livingroom",   // Prime Video
            "com.disney.disneyplus",               // Disney+
            "com.spotify.tv.android",              // Spotify
            "com.plexapp.android",                 // Plex
            "com.android.vending",                 // Google Play Store
    };
    static final String STOCK_HOME = "com.google.android.tvlauncher";
    private static final int KEEP_WATCHING_MAX = 3;

    private DepthClockView depth;
    private Weather.Now weatherNow;
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            refreshWeather(true);
            main.postDelayed(this, Weather.REFRESH_MS);
        }
    };
    private LinearLayout dock, cards;
    private View cardsBlock, night;
    private ImageView moon;
    private HorizontalScrollView dockScroll;
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Card pictures, three at a time, so one slow poster doesn't hold up the others. */
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private String cardsSignature = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private float dp;
    private int lastDockFocus;
    /** Long-press OK > Move: the tile being moved (◀ ▶ slide it, OK / Back place it); null otherwise. */
    private View moving;
    private TextView dockHint, nbaUsName, nbaThemName, nbaCenter, nbaCaption;
    private View nba, nbaPill;
    private TextView nbaPillText;
    private ImageView nbaPillLogo;
    private ImageView nbaLogoUs, nbaLogoThem;
    /** Home is on screen (between onResume and onPause): only then do the NBA checks run. */
    private boolean resumed;
    /** OK was held on a dock app and its menu opened: swallow that key's release. */
    private boolean holdHandled;
    private final Runnable nbaTick = new Runnable() {
        @Override public void run() { refreshNba(); }
    };
    private String builtDock = "";

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            depth.tick();
            applyFestival();                              // a new day may be (or stop being) a festival
        }
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildDock(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);
        // The Fuji picture covers the screen: no black window background drawn underneath it every frame.
        getWindow().setBackgroundDrawable(null);
        findViewById(R.id.root).setBackground(null);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        depth = findViewById(R.id.depth);
        dock = findViewById(R.id.dock);
        dockScroll = findViewById(R.id.dockScroll);
        dockHint = findViewById(R.id.dockHint);
        nba = findViewById(R.id.nba);
        nbaPill = findViewById(R.id.nbaPill);
        nbaPillText = findViewById(R.id.nbaPillText);
        nbaPillLogo = findViewById(R.id.nbaPillLogo);
        nbaPill.setOnClickListener(v -> {
            Intent app = launchIntent(getPackageManager(), "com.nbaimd.gametime.nba2011");
            if (app != null) start(app);
        });
        nbaUsName = findViewById(R.id.nbaUsName);
        nbaThemName = findViewById(R.id.nbaThemName);
        nbaCenter = findViewById(R.id.nbaCenter);
        nbaCaption = findViewById(R.id.nbaCaption);
        nbaCenter.setTypeface(DepthClockView.outfit(this, 300));
        nbaLogoUs = findViewById(R.id.nbaLogoUs);
        nbaLogoThem = findViewById(R.id.nbaLogoThem);
        nba.setOnClickListener(v -> {
            Intent app = launchIntent(getPackageManager(), "com.nbaimd.gametime.nba2011");
            if (app != null) start(app);
        });
        cards = findViewById(R.id.cards);
        cardsBlock = findViewById(R.id.cardsBlock);
        night = findViewById(R.id.night);
        moon = findViewById(R.id.moon);

        // Notifications: the TV's own notification panel (Google's TV recommendations service).
        findViewById(R.id.btnNotifications).setOnClickListener(v -> openFirst(
                new Intent("com.android.tv.action.OPEN_NOTIFICATIONS_PANEL"),
                new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.btnInputs).setOnClickListener(v -> openInputs());
        findViewById(R.id.btnWifi).setOnClickListener(v -> openFirst(
                new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.btnSettings).setOnClickListener(v -> openFirst(new Intent(Settings.ACTION_SETTINGS)));
        moon.setOnClickListener(v -> setNight(!prefs.getBoolean("night", false)));
        applyNight(prefs.getBoolean("night", false));
        buildDock();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(timeReceiver, tf);
        depth.tick();
        applyFestival();
        if (!Dock.key(this).equals(builtDock)) buildDock();
        resumed = true;
        showNba(Nba.saved(this));
        main.post(nbaTick);
        loadKeepWatching();
        showWeather(Weather.saved(this));
        refreshWeather(false);
        main.postDelayed(weatherTick, Weather.REFRESH_MS);
        // Home always lands on the dock (the last app used), as Apple TV does: not on the NBA pill or top bar.
        main.post(() -> {
            if (moving == null) focusDock(lastDockFocus);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timeReceiver);
        main.removeCallbacks(weatherTick);
        main.removeCallbacks(nbaTick);
        resumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
        io.shutdownNow();
        images.shutdownNow();
    }

    /** Home pressed while already home: back to the first dock app, as Apple TV does. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && hasWindowFocus()) {
            dockScroll.smoothScrollTo(0, 0);
            focusDock(0);
        }
    }

    /** Home is the bottom of the stack: Back has nowhere to go. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {}

    // ---------------------------------------------------------------- dock

    private void buildDock() {
        PackageManager pm = getPackageManager();
        dock.removeAllViews();
        builtDock = Dock.key(this);
        for (String pkg : Dock.load(this)) {
            Intent launch = launchIntent(pm, pkg);
            if (launch == null) continue;                    // not installed, or switched off
            ImageView tile = newTile();
            fillTile(tile, pm, pkg, launch);
            tile.setTag(pkg);                                 // app tiles carry their package; Settings and ••• don't
            tile.setContentDescription(label(pm, pkg));
            tile.setOnClickListener(v -> start(launch));
            // Hold OK: Android marks the repeated key as a long press; the release that follows must not open the app.
            tile.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
                    holdHandled = true;
                    showTileMenu(v);
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) return true;
                if (event.getAction() == KeyEvent.ACTION_UP && holdHandled) {
                    holdHandled = false;
                    return true;
                }
                return false;
            });
            addTile(tile);
        }
        ImageView settings = newTile();
        settings.setImageResource(R.drawable.ic_settings);
        glassIcon(settings);
        settings.setContentDescription(getString(R.string.settings));
        settings.setOnClickListener(v -> openFirst(new Intent(Settings.ACTION_SETTINGS)));
        addTile(settings);

        ImageView more = newTile();
        more.setImageResource(R.drawable.ic_more);
        glassIcon(more);
        more.setContentDescription(getString(R.string.more));
        more.setOnClickListener(v -> showMenu());
        addTile(more);
    }

    private ImageView newTile() {
        ImageView t = new ImageView(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setForeground(getDrawable(R.drawable.focus_ring_tile));
        t.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 12 * dp);
            }
        });
        t.setClipToOutline(true);
        return t;
    }

    private void addTile(ImageView tile) {
        int size = Math.round(50 * dp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        if (dock.getChildCount() > 0) lp.setMarginStart(Math.round(10 * dp));
        tile.setOnFocusChangeListener((v, has) -> {
            if (v == moving) return;                          // the moving tile keeps its lifted look
            // Scale only: a shadow under the focused tile costs a blur on every frame on a TV GPU.
            v.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f).setDuration(140).start();
            // (The gap between tiles is wider than the zoom, so the focused tile never overlaps a neighbour.)
            if (has) lastDockFocus = dock.indexOfChild(v);
        });
        dock.addView(tile, lp);
    }

    // ---------------------------------------------------------------- arranging the dock

    /** Long-press OK on a dock app: Move, or Remove from Dock. */
    private void showTileMenu(View tile) {
        String pkg = (String) tile.getTag();
        String[] items = {getString(R.string.dock_move), getString(R.string.dock_remove)};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(label(getPackageManager(), pkg))
                .setItems(items, (d, which) -> {
                    if (which == 0) startMove(tile);
                    else removeFromDock(tile);
                })
                .show();
    }

    private void startMove(View tile) {
        moving = tile;
        tile.requestFocus();
        tile.animate().scaleX(1.24f).scaleY(1.24f).translationY(-8 * dp).setDuration(160).start();
        dockHint.setVisibility(View.VISIBLE);
    }

    private void endMove() {
        View tile = moving;
        moving = null;
        dockHint.setVisibility(View.GONE);
        tile.animate().scaleX(1.14f).scaleY(1.14f).translationY(0).setDuration(160).start();
        saveDockOrder();
    }

    /** Slide the moving tile one place left or right, among the app tiles (Settings and ••• stay at the end). */
    private void moveBy(int step) {
        int from = dock.indexOfChild(moving);
        int to = from + step;
        if (to < 0 || to >= appTileCount()) return;
        dock.removeViewAt(from);
        dock.addView(moving, to);
        fixDockMargins();
        moving.requestFocus();
        lastDockFocus = to;
    }

    private void removeFromDock(View tile) {
        int at = dock.indexOfChild(tile);
        dock.removeView(tile);
        fixDockMargins();
        saveDockOrder();
        focusDock(Math.max(0, at - 1));
        Toast.makeText(this, R.string.dock_removed, Toast.LENGTH_SHORT).show();
    }

    private int appTileCount() {
        int n = 0;
        for (int i = 0; i < dock.getChildCount(); i++) if (dock.getChildAt(i).getTag() != null) n++;
        return n;
    }

    private void fixDockMargins() {
        for (int i = 0; i < dock.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dock.getChildAt(i).getLayoutParams();
            lp.setMarginStart(i == 0 ? 0 : Math.round(10 * dp));
            dock.getChildAt(i).setLayoutParams(lp);
        }
    }

    private void saveDockOrder() {
        List<String> apps = new ArrayList<>(Dock.load(this));
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < dock.getChildCount(); i++) {
            Object tag = dock.getChildAt(i).getTag();
            if (tag != null) shown.add((String) tag);
        }
        // Apps in the saved list but not shown (not installed right now) keep their place at the end.
        for (String p : apps) if (!shown.contains(p) && launchIntent(getPackageManager(), p) == null) shown.add(p);
        Dock.save(this, shown);
        builtDock = Dock.key(this);
    }

    /** While a tile is being moved, ◀ ▶ move it and OK / Back place it; nothing else reacts. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (moving == null) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getRepeatCount() == 0) endMove();
                    break;
                default: break;
            }
        }
        return true;
    }

    private void focusDock(int index) {
        if (dock.getChildCount() == 0) return;
        View v = dock.getChildAt(Math.min(index, dock.getChildCount() - 1));
        v.requestFocus();
    }

    /** The app's TV banner fitted into the square on its own edge colour (as Apple TV shows logos); else its icon. */
    private void fillTile(ImageView tile, PackageManager pm, String pkg, Intent launch) {
        Drawable banner = null, icon = null;
        ResolveInfo ri = pm.resolveActivity(launch, 0);
        if (ri != null) {
            banner = ri.activityInfo.loadBanner(pm);
            icon = sharpIcon(pm, pkg, ri.activityInfo.getIconResource());
            if (icon == null) icon = ri.loadIcon(pm);
        }
        if (banner == null) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                banner = ai.loadBanner(pm);
                if (icon == null) icon = ai.loadIcon(pm);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        // The app's own square icon, edge to edge (iPhone / Apple TV style). The wide TV banner only when an
        // app has no icon at all, fitted on its own edge colour.
        if (icon != null) {
            tile.setScaleType(ImageView.ScaleType.FIT_XY);
            tile.setImageDrawable(new TileIcon(icon));
        } else if (banner != null) {
            tile.setBackgroundColor(edgeColour(banner));
            tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.setImageDrawable(banner);
        } else {
            tile.setBackgroundResource(R.drawable.glass_tile);
        }
    }

    /** The app's icon from its highest-density resources, so a 100 px tile is not an upscaled 96 px icon. */
    static Drawable sharpIcon(PackageManager pm, String pkg, int iconRes) {
        if (iconRes == 0) return null;
        try {
            return pm.getResourcesForApplication(pkg)
                    .getDrawableForDensity(iconRes, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private void glassIcon(ImageView tile) {
        tile.setBackgroundResource(R.drawable.glass_tile);
        tile.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(13 * dp);
        tile.setPadding(pad, pad, pad, pad);
    }

    /** The colour along the banner's left and right edges, so the square around it continues the banner. */
    private static int edgeColour(Drawable banner) {
        Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Drawable d = banner.getConstantState() != null ? banner.getConstantState().newDrawable().mutate() : banner;
        d.setBounds(0, 0, 32, 18);
        d.draw(new Canvas(b));
        long r = 0, g = 0, bl = 0, a = 0;
        int n = 0;
        for (int y = 0; y < 18; y++) {
            for (int x : new int[]{0, 31}) {
                int p = b.getPixel(x, y);
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); a += Color.alpha(p);
                n++;
            }
        }
        b.recycle();
        if (a / n < 128) return 0xFF1C1F24;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    // ---------------------------------------------------------------- keep watching

    private void loadKeepWatching() {
        io.execute(() -> {
            List<WatchNext.Item> items = WatchNext.read(this, KEEP_WATCHING_MAX);
            main.post(() -> showCards(items));
        });
    }

    private void showCards(List<WatchNext.Item> items) {
        if (isFinishing() || isDestroyed()) return;
        // Same shows as last time: keep the cards (and their pictures) as they are.
        String signature = WatchNext.signature(items);
        if (signature.equals(cardsSignature) && cards.getChildCount() == items.size()) return;
        cardsSignature = signature;
        View focused = getCurrentFocus();
        boolean focusInCards = focused != null && focused.getParent() == cards;
        cards.removeAllViews();
        PackageManager pm = getPackageManager();
        for (WatchNext.Item it : items) cards.addView(card(pm, it));
        cardsBlock.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (focusInCards) {
            if (cards.getChildCount() > 0) cards.getChildAt(0).requestFocus(); else focusDock(lastDockFocus);
        }
    }

    private View card(PackageManager pm, WatchNext.Item it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.card);
        card.setForeground(getDrawable(R.drawable.focus_ring_card));
        int pad = Math.round(7 * dp);
        card.setPadding(pad, pad, Math.round(12 * dp), pad);
        card.setFocusable(true);
        card.setClickable(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Math.round(230 * dp), Math.round(62 * dp));
        if (cards.getChildCount() > 0) clp.setMarginStart(Math.round(12 * dp));
        card.setLayoutParams(clp);
        card.setOnFocusChangeListener((v, has) ->
                v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(140).start());

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundResource(R.drawable.glass_tile);
        thumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8 * dp);
            }
        });
        thumb.setClipToOutline(true);
        try {
            thumb.setImageDrawable(pm.getApplicationIcon(it.pkg));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        card.addView(thumb, new LinearLayout.LayoutParams(Math.round(84 * dp), Math.round(48 * dp)));
        String imageUri = it.imageUri;
        int tw = Math.round(84 * dp), th = Math.round(48 * dp);
        images.execute(() -> {
            Bitmap bmp = WatchNext.loadImage(this, imageUri, tw, th);
            if (bmp != null) main.post(() -> thumb.setImageBitmap(bmp));
        });

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Math.round(10 * dp));
        card.addView(text, tlp);

        text.addView(line(label(pm, it.pkg).toUpperCase(Locale.getDefault()), 9, 0xB3FFFFFF, 0.1f));
        text.addView(line(it.title, 13, 0xFFFFFFFF, 0f));
        String meta = meta(it);
        if (!meta.isEmpty()) text.addView(line(meta, 10, 0xB3FFFFFF, 0f));
        if (it.duration > 0 && it.position > 0) text.addView(progress((float) it.position / it.duration));

        card.setOnClickListener(v -> openWatchNext(pm, it));
        return card;
    }

    private TextView line(String s, int sp, int colour, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setLetterSpacing(spacing);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setPadding(0, 0, 0, Math.round(2 * dp));
        return t;
    }

    private View progress(float fraction) {
        fraction = Math.max(0.02f, Math.min(1f, fraction));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(3 * dp));
        lp.topMargin = Math.round(3 * dp);
        bar.setLayoutParams(lp);
        View done = new View(this);
        done.setBackgroundColor(0xF2FFFFFF);
        bar.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fraction));
        View rest = new View(this);
        rest.setBackgroundColor(0x40FFFFFF);
        bar.addView(rest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - fraction));
        return bar;
    }

    private static String meta(WatchNext.Item it) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(it.season) && !TextUtils.isEmpty(it.episode)) {
            parts.add("S" + it.season + " E" + it.episode);
        } else if (!TextUtils.isEmpty(it.episodeTitle)) {
            parts.add(it.episodeTitle);
        }
        if (it.duration > 0 && it.position > 0 && it.position < it.duration) {
            long left = Math.max(1, (it.duration - it.position) / 60000);
            parts.add(left + " min left");
        }
        return TextUtils.join(" · ", parts);
    }

    private void openWatchNext(PackageManager pm, WatchNext.Item it) {
        if (it.intentUri != null) {
            try {
                Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) {
                // fall through to opening the app itself
            }
        }
        Intent launch = launchIntent(pm, it.pkg);
        if (launch != null) start(launch); else toast();
    }

    // ---------------------------------------------------------------- top bar, night, menu

    private void setNight(boolean on) {
        prefs.edit().putBoolean("night", on).apply();
        applyNight(on);
    }

    private void applyNight(boolean on) {
        night.setVisibility(on ? View.VISIBLE : View.GONE);
        moon.setSelected(on);
        moon.setAlpha(on ? 1f : 0.85f);
    }

    /**
     * HDMI 1-3, AV and TV, from Android's TV input list, named as in the TV's own settings. An HDMI or AV input
     * opens through the standard passthrough channel link (the TV's Live TV app shows it); TV opens Live TV.
     */
    private void openInputs() {
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> list = tim == null ? new ArrayList<>() : new ArrayList<>(tim.getTvInputList());
        List<String> labels = new ArrayList<>();
        List<Intent> targets = new ArrayList<>();
        List<TvInputInfo> passthrough = new ArrayList<>();
        for (TvInputInfo info : list) {
            if (info.isPassthroughInput() && info.getParentId() == null && !info.isHidden(this)) passthrough.add(info);
        }
        // HDMI first, then AV and the rest; in port order within each.
        Collections.sort(passthrough, (a, b) -> {
            int ta = a.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1, tb = b.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1;
            if (ta != tb) return Integer.compare(ta, tb);
            return String.valueOf(a.loadLabel(this)).compareTo(String.valueOf(b.loadLabel(this)));
        });
        for (TvInputInfo info : passthrough) {
            CharSequence custom = info.loadCustomLabel(this);
            CharSequence name = !TextUtils.isEmpty(custom) ? custom : info.loadLabel(this);
            labels.add(String.valueOf(name));
            targets.add(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId())));
        }
        Intent liveTv = launchIntent(getPackageManager(), getString(R.string.live_tv_package));
        if (liveTv != null) {
            labels.add(getString(R.string.input_tv));
            targets.add(liveTv);
        }
        if (labels.isEmpty()) {
            openFirst(new Intent(Settings.ACTION_SETTINGS));
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.inputs)
                .setItems(labels.toArray(new String[0]), (d, which) -> openFirst(targets.get(which), liveTv))
                .show();
    }

    private void showMenu() {
        boolean nightOn = prefs.getBoolean("night", false);
        boolean tamil = getResources().getBoolean(R.bool.addon_tamil_calendar);
        String[] items = {
                getString(R.string.tamil_calendar),
                getString(R.string.wallpapers),
                getString(R.string.menu_inputs),
                getString(R.string.menu_all_apps),
                getString(nightOn ? R.string.menu_night_off : R.string.menu_night_on),
                getString(R.string.menu_settings)};
        int skip = tamil ? 0 : 1;                       // the Tamil calendar add-on is the first item
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(java.util.Arrays.copyOfRange(items, skip, items.length), (d, which) -> {
                    switch (which + skip) {
                        case 0: start(new Intent(this, TamilCalendarActivity.class)); break;
                        case 1: start(new Intent(this, WallpaperPickerActivity.class)); break;
                        case 2: openInputs(); break;
                        case 3: start(new Intent(this, AllAppsActivity.class)); break;
                        case 4: setNight(!nightOn); break;
                        default: openFirst(new Intent(Settings.ACTION_SETTINGS)); break;
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- festivals

    /** Today's festival next to the date, and on Pongal and Deepavali their wallpaper (else the chosen one). */
    private void applyFestival() {
        Festivals.Today today = Festivals.on(this, new java.util.Date());
        depth.setFestival(Festivals.line(today));
        if (today != null && today.wallpaper != null) depth.show(today.wallpaper);
        else depth.loadWallpaper();
    }

    // ---------------------------------------------------------------- NBA add-on

    /**
     * Your NBA team (config.xml, off when empty): during a live game the scoreboard under the clock ([GSW] 98 – 94 [LAL], "● LIVE · Q4 2:31");
     * otherwise a small pill in the top-left corner ("GSW vs BOS · Mon 7:00 AM", "Final · GSW 98–126 MEM").
     */
    private void showNba(Nba.Game g) {
        if (g == null) {
            nba.setVisibility(View.GONE);
            nbaPill.setVisibility(View.GONE);
            return;
        }
        String us = Nba.name(g.us), them = Nba.name(g.them);
        boolean live = "live".equals(g.kind);
        if (live) {
            nbaUsName.setText(us);
            nbaThemName.setText(them);
            nbaCenter.setText(g.usScore + "  \u2013  " + g.themScore);
            android.text.SpannableStringBuilder cap = new android.text.SpannableStringBuilder("\u25CF ");
            cap.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF453A), 0, 1, 0);
            cap.append("LIVE  \u00B7  ").append(g.detail.toUpperCase(Locale.ENGLISH));
            nbaCaption.setText(cap);
        } else if ("next".equals(g.kind)) {
            nbaPillText.setText(us + (g.win ? " vs " : " at ") + them + "  \u00B7  " + g.detail);
        } else {
            nbaPillText.setText("Final  \u00B7  " + us + " " + g.usScore + "\u2013" + g.themScore + " " + them);
        }
        // Only one of the two shows; Home never jumps focus onto it.
        nba.setVisibility(live ? View.VISIBLE : View.GONE);
        nbaPill.setVisibility(live ? View.GONE : View.VISIBLE);
        String a = g.us, b = g.them;
        images.execute(() -> {
            android.graphics.Bitmap la = Nba.logo(this, a), lb = live ? Nba.logo(this, b) : null;
            main.post(() -> {
                if (live) {
                    nbaLogoUs.setImageBitmap(la);
                    nbaLogoThem.setImageBitmap(lb);
                } else {
                    nbaPillLogo.setImageBitmap(la);
                }
            });
        });
    }

    /** Fetch the NBA line; again in 30 s while a game is live, else in 30 minutes (only while Home shows). */
    private void refreshNba() {
        io.execute(() -> {
            Nba.Game game = Nba.fetch(this);
            main.post(() -> {
                if (isFinishing()) return;
                if (game != null) showNba(game);
                main.removeCallbacks(nbaTick);
                if (resumed) {
                    main.postDelayed(nbaTick, game != null && "live".equals(game.kind) ? 30_000L : Weather.REFRESH_MS);
                }
            });
        });
    }

    // ---------------------------------------------------------------- weather

    /** Show the weather line next to the date. */
    private void showWeather(Weather.Now now) {
        weatherNow = now;
        depth.setWeather(now == null ? "" : now.line());
    }

    /** Ask for the weather if it is due (or now, when {@code force}); off the main thread. */
    private void refreshWeather(boolean force) {
        if (!force && !Weather.due(this)) return;
        io.execute(() -> {
            Weather.Now now = Weather.fetch(this);
            if (now != null) main.post(() -> { if (!isFinishing()) showWeather(now); });
        });
    }


    // ---------------------------------------------------------------- helpers

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void start(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            toast();
        }
    }

    /** Open the first intent this TV can handle. */
    private void openFirst(Intent... candidates) {
        for (Intent i : candidates) {
            if (i == null) continue;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                return;
            } catch (RuntimeException ignored) {
                // not on this TV; try the next one
            }
        }
        toast();
    }

    private void toast() {
        Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
    }
}
