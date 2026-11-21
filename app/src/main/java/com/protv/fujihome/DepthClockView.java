package com.protv.fujihome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The lock-screen look: wallpaper, then the date and a large thin clock, then the mountain cut out of the
 * same wallpaper drawn over the clock, so the peak stands in front of the numbers (the iPhone depth effect).
 * Everything is drawn in the wallpaper's own 1920x1080 frame with one scale, so the cut-out always lines up.
 * It redraws once a minute and nothing else moves: no blur, no video, no animation.
 */
public class DepthClockView extends View {
    private static final float FRAME_W = 1920f, FRAME_H = 1080f;
    /** Top of the clock's digits, and the date's baseline, in the 1920x1080 frame. The peak is at row 357. */
    private static final float CLOCK_TOP = 150f, DATE_BASELINE = 112f;

    private Bitmap background, mountain;
    private String wallpaperId;
    /** "☁️ 29° Cloudy", shown after the date; empty until the weather is known. */
    private String weather = "";
    /** "🪔 தீபாவளி" on a festival day, else empty. */
    private String festival = "";
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect bounds = new Rect();
    private String time = "", date = "";

    public DepthClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadWallpaper();

        clockPaint.setTypeface(outfit(context, 150));
        clockPaint.setTextAlign(Paint.Align.CENTER);
        clockPaint.setTextSize(470f);
        clockPaint.setLetterSpacing(-0.02f);
        clockPaint.setColor(0xDEFFFFFF);
        clockPaint.setShadowLayer(28f, 0f, 4f, 0x38000000);

        datePaint.setTypeface(outfit(context, 450));
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTextSize(34f);
        datePaint.setLetterSpacing(0.02f);
        datePaint.setColor(0xF2FFFFFF);
        datePaint.setShadowLayer(8f, 0f, 1f, 0x40000000);
        // Drawn once into a GPU layer and reused for every frame of focus movement: the wallpaper, text and
        // mountain are only drawn again when the minute changes, not each time a tile moves.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        tick();
    }

    /** Load the chosen wallpaper (and its scenery cut-out) if it changed. True if it did. */
    public boolean loadWallpaper() {
        return show(Wallpapers.current(getContext()));
    }

    /** The id of the wallpaper on screen. */
    public String shownWallpaper() {
        return wallpaperId;
    }

    /** Show this wallpaper (the screensaver rotates through them without changing Home's choice). */
    public boolean show(String id) {
        if (id.equals(wallpaperId)) return false;
        // The old pictures are left to Android's memory cleanup rather than freed here: the screen may still be
        // drawing them for one more frame.
        background = Wallpapers.background(getContext(), id);
        mountain = Wallpapers.foreground(getContext(), id);
        wallpaperId = id;
        invalidate();
        return true;
    }

    /** The festival after the date ("" hides it). */
    public void setFestival(String line) {
        String next = line == null ? "" : line;
        if (next.equals(festival)) return;
        festival = next;
        invalidate();
    }

    /** The weather line after the date ("" hides it). */
    public void setWeather(String line) {
        String next = line == null ? "" : line;
        if (next.equals(weather)) return;
        weather = next;
        invalidate();
    }

    /** The view covers the whole screen with an opaque picture, so nothing behind it needs drawing. */
    @Override
    public boolean isOpaque() {
        return background != null;
    }

    /** Outfit (OFL font, from the CleanYourTV launcher) at the given weight; Android's thin sans if it fails. */
    static Typeface outfit(Context context, int weight) {
        try {
            return new Typeface.Builder(context.getAssets(), "outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .setFallback("sans-serif")                  // Tamil letters and emoji come from the system fonts
                    .build();
        } catch (RuntimeException e) {
            return Typeface.create(weight < 300 ? "sans-serif-thin" : "sans-serif", Typeface.NORMAL);
        }
    }

    /** Read the time again (called every minute and when the time or time zone changes). */
    public void tick() {
        Date now = new Date();
        String timeFormat = DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm";
        time = new SimpleDateFormat(timeFormat, Locale.getDefault()).format(now);
        date = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.max(getWidth() / FRAME_W, getHeight() / FRAME_H);
        canvas.save();
        canvas.translate((getWidth() - FRAME_W * scale) / 2f, (getHeight() - FRAME_H * scale) / 2f);
        canvas.scale(scale, scale);
        if (background != null) canvas.drawBitmap(background, 0f, 0f, bitmapPaint);
        StringBuilder line = new StringBuilder(date);
        if (!festival.isEmpty()) line.append("   \u00B7   ").append(festival);
        if (!weather.isEmpty()) line.append("   \u00B7   ").append(weather);
        canvas.drawText(line.toString(), FRAME_W / 2f, DATE_BASELINE, datePaint);
        clockPaint.getTextBounds(time, 0, time.length(), bounds);
        canvas.drawText(time, FRAME_W / 2f, CLOCK_TOP - bounds.top, clockPaint);
        if (mountain != null) canvas.drawBitmap(mountain, 0f, 0f, bitmapPaint);
        canvas.restore();
    }
}
