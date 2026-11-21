package com.protv.fujihome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ••• > Tamil Calendar: the day's Tamil daily calendar sheet from tamildailycalendar.com (the calendar the family
 * uses), shown as published, full size in a glass frame over the wallpaper. Left/Right change the day, OK goes back
 * to today. Each sheet is kept after its first download, so it opens at once and still shows without internet.
 */
public class TamilCalendarActivity extends Activity {
    private static final String SITE = "https://www.tamildailycalendar.com/";
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/120.0 Safari/537.36";
    private static final int KEEP = 40;                          // sheets kept on the TV

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Calendar day = Calendar.getInstance();
    private ImageView sheet;
    private TextView title, status;
    private float dp;
    private int request;                                         // ignore answers for days already left

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImageBitmap(Wallpapers.small(this, Wallpapers.current(this)));
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0xCC0A0C10);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(0, px(22), 0, px(14));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        title = new TextView(this);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(DepthClockView.outfit(this, 350));
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        // The sheet: 800 x 605 as published, as large as the screen allows, on a white card with round corners.
        FrameLayout card = new FrameLayout(this);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * dp);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(12 * dp);
        int h = px(405), w = Math.round(h * 800f / 605f);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(w + px(16), h + px(16));
        cardLp.topMargin = px(14);
        column.addView(card, cardLp);

        sheet = new ImageView(this);
        sheet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        card.addView(sheet, sheetLp);

        status = new TextView(this);
        status.setTextColor(0xFF3A3F47);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        card.addView(status, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        TextView hint = new TextView(this);
        hint.setText(R.string.tamil_calendar_hint);
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = px(14);
        column.addView(hint, hintLp);

        setContentView(root);
        show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                day.add(Calendar.DAY_OF_MONTH, -1);
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                day.add(Calendar.DAY_OF_MONTH, 1);
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                day.setTimeInMillis(System.currentTimeMillis());
                show();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void show() {
        boolean today = sameDay(day, Calendar.getInstance());
        String date = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ENGLISH).format(day.getTime());
        title.setText(getString(R.string.tamil_calendar) + "   ·   " + date + (today ? "   ·   " + getString(R.string.today) : ""));
        sheet.setImageDrawable(null);
        status.setText(R.string.loading);
        status.setVisibility(View.VISIBLE);
        final int id = ++request;
        final Calendar which = (Calendar) day.clone();
        io.execute(() -> {
            Bitmap bmp = load(which);
            main.post(() -> {
                if (id != request || isFinishing()) return;
                if (bmp != null) {
                    sheet.setImageBitmap(bmp);
                    status.setVisibility(View.GONE);
                } else {
                    status.setText(R.string.sheet_unavailable);
                }
            });
            // Fetch the next day too, so Right is instant.
            Calendar next = (Calendar) which.clone();
            next.add(Calendar.DAY_OF_MONTH, 1);
            load(next);
        });
    }

    /** The sheet for that day: from the TV's copy, else downloaded from the site and kept. Null if unavailable. */
    private Bitmap load(Calendar c) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        String name = d + String.format(Locale.US, "%02d", m) + y;            // as the site names them: 21112026
        File dir = new File(getCacheDir(), "tamil_sheets");
        File file = new File(dir, name + ".jpg");
        if (file.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(file.getPath());
            if (b != null) return b;
        }
        byte[] data = download(SITE + y + "/" + name + ".jpg");
        if (data == null) return null;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (b == null || b.getWidth() < 300) return null;                     // not a calendar sheet
        if (dir.isDirectory() || dir.mkdirs()) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            } catch (IOException ignored) {
            }
            trim(dir);
        }
        return b;
    }

    private static byte[] download(String address) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", AGENT);
            conn.setRequestProperty("Referer", SITE);
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 3 * 1024 * 1024) return null;
                }
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= KEEP) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = KEEP; i < files.length; i++) files[i].delete();
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
