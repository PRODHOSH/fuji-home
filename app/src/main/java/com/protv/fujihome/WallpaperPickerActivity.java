package com.protv.fujihome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** ••• > Wallpapers: every bundled wallpaper as a preview with its name; OK picks it and goes back Home. */
public class WallpaperPickerActivity extends Activity {
    private List<Wallpapers.Entry> entries;
    private Bitmap[] thumbs;
    private float dp;
    private View lastSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        entries = Wallpapers.list(this);
        thumbs = new Bitmap[entries.size()];
        for (int i = 0; i < thumbs.length; i++) thumbs[i] = Wallpapers.thumbnail(this, entries.get(i).id);
        String current = Wallpapers.current(this);

        FrameLayout root = new FrameLayout(this);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImageBitmap(Wallpapers.small(this, current));
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0xC70A0C10);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(56), px(34), px(56), 0);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText(R.string.wallpapers);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setTypeface(DepthClockView.outfit(this, 300));
        title.setPadding(px(6), 0, 0, px(18));
        column.addView(title);

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
        grid.setColumnWidth(px(192));
        grid.setVerticalSpacing(px(22));
        grid.setSelector(android.R.color.transparent);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(px(8), px(10), px(8), px(40));
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(new Adapter(current));
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Wallpapers.choose(this, entries.get(position).id);
            finish();                                   // Home loads it when it comes back
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (lastSelected != null && lastSelected != view) zoom(lastSelected, false);
                if (view != null) zoom(view, true);
                lastSelected = view;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                if (lastSelected != null) zoom(lastSelected, false);
                lastSelected = null;
            }
        });
        column.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(current)) grid.setSelection(i);
        }
        grid.requestFocus();
    }

    private void zoom(View item, boolean on) {
        View thumb = item.findViewWithTag("thumb");
        if (thumb != null) thumb.animate().scaleX(on ? 1.07f : 1f).scaleY(on ? 1.07f : 1f).setDuration(140).start();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private final class Adapter extends BaseAdapter {
        private final String current;

        Adapter(String current) {
            this.current = current;
        }

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout item = (LinearLayout) convert;
            if (item == null) {
                item = new LinearLayout(WallpaperPickerActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setClipChildren(false);
                ImageView thumb = new ImageView(WallpaperPickerActivity.this);
                thumb.setTag("thumb");
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setForeground(getDrawable(R.drawable.focus_ring_card));
                thumb.setDuplicateParentStateEnabled(true);
                thumb.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 14 * dp);
                    }
                });
                thumb.setClipToOutline(true);
                item.addView(thumb, new LinearLayout.LayoutParams(px(192), px(108)));
                TextView label = new TextView(WallpaperPickerActivity.this);
                label.setTag("label");
                label.setTextColor(0xFFFFFFFF);
                label.setTextSize(12);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, px(8), 0, 0);
                item.addView(label, new LinearLayout.LayoutParams(px(192), -2));
            }
            Wallpapers.Entry e = entries.get(position);
            ((ImageView) item.findViewWithTag("thumb")).setImageBitmap(thumbs[position]);
            TextView label = item.findViewWithTag("label");
            label.setText(e.id.equals(current) ? "✓  " + e.name : e.name);
            label.setAlpha(e.id.equals(current) ? 1f : 0.8f);
            return item;
        }
    }
}
