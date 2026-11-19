package dev.tls1.refocus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class FocusReceiver extends BroadcastReceiver {
    private static final String K_LAST_POWER = "last_power_elapsed";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        Log.i(Focus.TAG, "onReceive " + a);
        if (Focus.ACTION_TICK.equals(a)) {
            Focus.onTick(c);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            remember(c);
            Focus.onPowerOn(c);
            return;
        }
        // Vendor power/screen events (wake from standby). Debounce so repeated
        // events don't keep restarting the warm-up sequence.
        long now = SystemClock.elapsedRealtime();
        long last = Focus.prefs(c).getLong(K_LAST_POWER, -1);
        if (last < 0 || last > now || now - last > 5 * 60_000L) {
            remember(c);
            Focus.onPowerOn(c);
        }
    }

    private static void remember(Context c) {
        Focus.prefs(c).edit().putLong(K_LAST_POWER, SystemClock.elapsedRealtime()).apply();
    }
}
