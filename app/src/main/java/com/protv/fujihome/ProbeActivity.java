package com.protv.fujihome;

import android.app.Activity;
import android.os.Bundle;

/** Invisible and closes at once: lets HomeRedirectService see what is on screen after power-on (probeAtBoot). */
public class ProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().post(this::close);
    }

    @SuppressWarnings("deprecation")
    private void close() {
        finish();
        overridePendingTransition(0, 0);
    }
}
