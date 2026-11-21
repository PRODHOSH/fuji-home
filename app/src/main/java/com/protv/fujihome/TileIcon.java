package com.protv.fujihome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * An app icon drawn edge to edge in a square tile, the way iPhone and Apple TV show icons: an adaptive icon's
 * background and foreground layers fill the whole tile (no circle mask); an older flat icon is enlarged a
 * little over the colour of its own edges, so its padding and rounded corners don't show as a frame.
 */
final class TileIcon extends Drawable {
    private static final int WHITE = 0xFFF5F5F7;
    private final Drawable background, foreground, flat;
    private final int flatBackdrop;

    TileIcon(Drawable icon) {
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            background = a.getBackground();
            foreground = a.getForeground();
            flat = null;
            flatBackdrop = 0;
        } else {
            background = null;
            foreground = null;
            flat = icon;
            flatBackdrop = backdrop(icon);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int w = b.width(), h = b.height();
        if (flat == null) {
            // Adaptive layers are 108 units with the visible part the middle 72: grow them by half to fill.
            int dx = w / 4, dy = h / 4;
            Rect layer = new Rect(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy);
            // Under a see-through background layer (a logo on nothing): a white tile, as iOS does.
            canvas.drawColor(WHITE);
            if (background != null) { background.setBounds(layer); background.draw(canvas); }
            if (foreground != null) { foreground.setBounds(layer); foreground.draw(canvas); }
        } else if (flatBackdrop == WHITE) {
            // A logo with no plate of its own (Play Store): the whole logo, centred on white with a margin,
            // as iOS shows such icons.
            canvas.drawColor(flatBackdrop);
            int dx = Math.round(w * 0.14f), dy = Math.round(h * 0.14f);
            flat.setBounds(b.left + dx, b.top + dy, b.right - dx, b.bottom - dy);
            flat.draw(canvas);
        } else {
            canvas.drawColor(flatBackdrop);
            // Icons with their own plate carry about 8% transparent padding on each side; enlarge past it.
            int dx = Math.round(w * 0.09f), dy = Math.round(h * 0.09f);
            flat.setBounds(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy);
            flat.draw(canvas);
        }
    }

    /** The colour of the icon's opaque pixels along its middle row and column edges. */
    private static int backdrop(Drawable icon) {
        int s = 48;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Drawable d = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        d.setBounds(0, 0, s, s);
        d.draw(new Canvas(bmp));
        long r = 0, g = 0, bl = 0;
        int n = 0;
        int[] lo = {255, 255, 255}, hi = {0, 0, 0}; // per-channel spread of the edge samples: a plate is one colour
        // Walk in from each side at a few rows/columns and take the first opaque pixel: the plate's colour.
        int[] lines = {s / 3, s / 2, 2 * s / 3};
        for (int line : lines) {
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = dir[0] == 1 ? 0 : dir[0] == -1 ? s - 1 : line;
                int y = dir[1] == 1 ? 0 : dir[1] == -1 ? s - 1 : line;
                while (x >= 0 && y >= 0 && x < s && y < s) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) > 200) {
                        r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
                        int[] ch = {Color.red(p), Color.green(p), Color.blue(p)};
                        for (int k = 0; k < 3; k++) {
                            lo[k] = Math.min(lo[k], ch[k]); hi[k] = Math.max(hi[k], ch[k]);
                        }
                        break;
                    }
                    x += dir[0]; y += dir[1];
                }
            }
        }
        bmp.recycle();
        // A logo on nothing, or edges that are the logo itself in several colours (Play Store): a clean white
        // tile, as iOS gives icons without a plate. Otherwise continue the icon's own plate colour.
        int opaqueEdges = lines.length * 4;
        int spread = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
        if (n < opaqueEdges - 2 || spread > 90) return WHITE;
        return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
