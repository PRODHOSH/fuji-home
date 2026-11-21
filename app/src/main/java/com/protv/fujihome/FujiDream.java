package com.protv.fujihome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.dreams.DreamService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The screensaver (lock screen): the same look as Home (wallpaper, date, big clock, scenery in front of it) with
 * no buttons. It moves to another wallpaper every 10 minutes with a soft fade, going through all of them in a
 * shuffled order, and shifts a few pixels every minute so nothing sits still on the panel for hours. Home keeps
 * the wallpaper picked in ••• > Wallpapers.
 */
public class FujiDream extends DreamService {
    private static final int CHANGE_EVERY_MINUTES = 10;
    private DepthClockView clock;
    private final List<String> order = new ArrayList<>();
    private int step, minutes, next;
    /** Pongal / Deepavali: the festival wallpaper stays all day, no rotation. */
    private boolean festivalWallpaper;
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            clock.tick();
            // A slow 3x3 drift of up to 6 px, a different spot each minute.
            step = (step + 1) % 9;
            float d = getResources().getDisplayMetrics().density;
            clock.setTranslationX((step % 3 - 1) * 3 * d);
            clock.setTranslationY((step / 3 - 1) * 3 * d);
            if (Intent.ACTION_TIME_TICK.equals(i.getAction()) && ++minutes % CHANGE_EVERY_MINUTES == 0) {
                nextWallpaper();
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        clock = new DepthClockView(this, null);
        clock.setScaleX(1.01f);           // room for the drift without showing an edge
        clock.setScaleY(1.01f);
        Weather.Now now = Weather.saved(this);
        clock.setWeather(now == null ? "" : now.line());
        Festivals.Today fest = Festivals.on(this, new java.util.Date());
        clock.setFestival(Festivals.line(fest));
        festivalWallpaper = fest != null && fest.wallpaper != null;
        if (festivalWallpaper) clock.show(fest.wallpaper);
        setContentView(clock);
        for (Wallpapers.Entry e : Wallpapers.list(this)) order.add(e.id);
        Collections.shuffle(order);
        // Start on the wallpaper Home shows, then move through the others.
        order.remove(clock.shownWallpaper());
        if (clock.shownWallpaper() != null) order.add(0, clock.shownWallpaper());
        next = 1;
    }

    /** Fade out, swap to the next wallpaper in the shuffled order, fade back in (once every 10 minutes). */
    private void nextWallpaper() {
        if (order.size() < 2 || festivalWallpaper) return;
        String id = order.get(next % order.size());
        next++;
        clock.animate().alpha(0f).setDuration(700).withEndAction(() -> {
            clock.show(id);
            clock.animate().alpha(1f).setDuration(900).start();
        }).start();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        clock.tick();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        unregisterReceiver(tick);
    }
}
