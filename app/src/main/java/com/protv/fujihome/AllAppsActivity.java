package com.protv.fujihome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every TV app on the set in a grid, icon and name, on the darkened Fuji picture. Opened from ••• > All apps. */
public class AllAppsActivity extends Activity {
    private static final class App {
        String label, pkg;
        Drawable tile;
    }

    private final List<App> apps = new ArrayList<>();
    private float dp;
    private View lastSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        loadApps();

        FrameLayout root = new FrameLayout(this);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImageBitmap(Wallpapers.small(this, Wallpapers.current(this)));
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0xC20A0C10);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(56), px(34), px(56), 0);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText(R.string.menu_all_apps);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setTypeface(DepthClockView.outfit(this, 300));
        title.setPadding(px(6), 0, 0, px(18));
        column.addView(title);

        GridView grid = new GridView(this);
        grid.setNumColumns(8);
        grid.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
        grid.setColumnWidth(px(96));
        grid.setVerticalSpacing(px(20));
        grid.setSelector(android.R.color.transparent);
        grid.setDrawSelectorOnTop(false);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(px(8), px(10), px(8), px(40));
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(new Adapter());
        grid.setOnItemClickListener((parent, view, position, id) -> open(apps.get(position)));
        this.grid = grid;
        // Long-press OK: add the app to the dock, or take it off (see dispatchKeyEvent).
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showDockMenu(position);
            return true;
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
        grid.requestFocus();
    }

    private GridView grid;
    private boolean holdHandled;

    /** Hold OK on an app: Android marks the repeated key as a long press; its release must not open the app. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        if (grid != null && (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER)) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.isLongPress()) {
                holdHandled = true;
                int pos = grid.getSelectedItemPosition();
                if (pos >= 0) showDockMenu(pos);
                return true;
            }
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) return true;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP && holdHandled) {
                holdHandled = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showDockMenu(int position) {
        App app = apps.get(position);
        boolean inDock = Dock.contains(this, app.pkg);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(new String[]{getString(inDock ? R.string.dock_remove : R.string.dock_add)}, (d, w) -> {
                    if (inDock) Dock.remove(this, app.pkg); else Dock.add(this, app.pkg);
                    Toast.makeText(this, inDock ? R.string.dock_removed : R.string.dock_added, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            Drawable icon = HomeActivity.sharpIcon(pm, pkg, ri.activityInfo.getIconResource());
            if (icon == null) icon = ri.loadIcon(pm);
            a.tile = new TileIcon(icon);
            apps.add(a);
        }
        Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
    }

    private void open(App app) {
        Intent launch = HomeActivity.launchIntent(getPackageManager(), app.pkg);
        try {
            if (launch != null) { startActivity(launch); return; }
        } catch (RuntimeException ignored) {
        }
        Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
    }

    private void zoom(View item, boolean on) {
        View tile = item.findViewWithTag("tile");
        if (tile == null) return;
        tile.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(140).start();
        item.findViewWithTag("label").animate().alpha(on ? 1f : 0.75f).setDuration(140).start();
    }

    private int px(float value) {
        return Math.round(value * dp);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout item = (LinearLayout) convert;
            if (item == null) {
                item = new LinearLayout(AllAppsActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setClipChildren(false);
                ImageView tile = new ImageView(AllAppsActivity.this);
                tile.setTag("tile");
                tile.setScaleType(ImageView.ScaleType.FIT_XY);
                tile.setForeground(getDrawable(R.drawable.focus_ring_tile));
                tile.setDuplicateParentStateEnabled(true);
                tile.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 14 * dp);
                    }
                });
                tile.setClipToOutline(true);
                item.addView(tile, new LinearLayout.LayoutParams(px(64), px(64)));
                TextView label = new TextView(AllAppsActivity.this);
                label.setTag("label");
                label.setTextColor(0xFFFFFFFF);
                label.setAlpha(0.75f);
                label.setTextSize(11);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, px(8), 0, 0);
                item.addView(label, new LinearLayout.LayoutParams(px(96), -2));
            }
            App app = apps.get(position);
            ((ImageView) item.findViewWithTag("tile")).setImageDrawable(app.tile);
            ((TextView) item.findViewWithTag("label")).setText(app.label);
            return item;
        }
    }
}
