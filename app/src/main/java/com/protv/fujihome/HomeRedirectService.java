package com.protv.fujihome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Opens Fuji Home whenever the TV's original home screen comes to the front (Home button, Back out of an app,
 * power-on). Nothing on the TV is switched off: the original home screen stays Android's Home, and this only
 * covers it.
 *
 * Adapted from CleanYourTV's HomeKeyService (stock_home_redirect). Fuji Home is opened like an app, not with a
 * Home intent, so the original launcher stays the home process and can still open YouTube and Netflix for the
 * remote's own buttons on Android 10 (CleanYourTV #1274). It reads window changes only: no key presses (so the
 * remote behaves exactly as before) and no screen text.
 *
 * Switch off: Settings > Device Preferences > Accessibility > Fuji Home, uninstall Fuji Home, or
 * the restore script from your backup. Any of these puts the TV back as it was.
 */
public class HomeRedirectService extends AccessibilityService {
    /** The original launcher's home screen activities. Its panels (Inputs, Notifications, Account) are not. */
    private final Set<String> stockHome = new HashSet<>();
    /** How long after boot the first start of this service still counts as part of the boot. */
    private static final long BOOT_WINDOW_MS = 3 * 60 * 1000L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(HomeActivity.STOCK_HOME);
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(home,
                PackageManager.MATCH_DISABLED_COMPONENTS);
        for (ResolveInfo ri : found) stockHome.add(ri.activityInfo.name);
        if (stockHome.isEmpty()) stockHome.add(HomeActivity.STOCK_HOME + ".MainActivity");
        probeAtBoot();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || cls == null || !HomeActivity.STOCK_HOME.contentEquals(pkg)) return;
        if (stockHome.contains(cls.toString())) openFujiHome();
    }

    private void openFujiHome() {
        // CATEGORY_HOME without ACTION_MAIN is not a Home intent (see the class comment).
        Intent open = new Intent(this, HomeActivity.class).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(open);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    /**
     * At power-on the original home screen is often up before Android starts this service, so no window event
     * for it arrives. Once per boot, open an invisible screen that closes at once: whatever was behind it comes
     * back and sends a window event, which opens Fuji Home only if it is the original home screen (an HDMI input
     * or an app the TV resumed is left alone).
     */
    private void probeAtBoot() {
        if (SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) return;
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = getSharedPreferences("boot", MODE_PRIVATE);
        if (boot < 0 || boot == prefs.getInt("probe_boot", -1)) return;
        prefs.edit().putInt("probe_boot", boot).apply();
        try {
            startActivity(new Intent(this, ProbeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
