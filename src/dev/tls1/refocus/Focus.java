package dev.tls1.refocus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/** Core logic: trigger projector autofocus and manage the schedule. */
public final class Focus {
    static final String TAG = "Refocus";

    static final String ACTION_TICK = "dev.tls1.refocus.TICK";

    // Minutes after power-on for warm-up refocus (lens heats up and focus drifts).
    static final int[] WARMUP_MIN = {15, 35};

    static final String PREFS = "refocus";
    static final String K_INTERVAL = "interval_min";   // 0 = off
    static final String K_WARMUP = "warmup";           // boolean
    static final String K_STAGE = "stage";             // index into WARMUP_MIN, or -1 for periodic
    static final String K_LAST = "last_ts";            // wall-clock of last trigger
    static final String K_COUNT = "count";

    static final int DEFAULT_INTERVAL = 60;
    static final String K_KEY = "key_code";         // assigned remote key, 0 = none
    static final String K_LEARN = "learning";       // true while assigning a key

    private Focus() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Sends the explicit broadcast handled by com.fengmi.projector.imaging BootReceiver. */
    static void trigger(Context c) {
        Intent i = new Intent("fengos.intent.action.START_FOCUS");
        i.setComponent(new ComponentName("com.fengmi.projector.imaging",
                "com.fengmi.projector.imaging.persist.BootReceiver"));
        // fromIot forces autofocus even if "autofocus on boot" is disabled in settings.
        i.putExtra("fromIot", true);
        c.sendBroadcast(i);
        SharedPreferences p = prefs(c);
        p.edit().putLong(K_LAST, System.currentTimeMillis())
                .putInt(K_COUNT, p.getInt(K_COUNT, 0) + 1).apply();
        Log.i(TAG, "autofocus triggered");
    }

    static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    /** Called on boot / wake from standby: restart warm-up sequence. */
    static void onPowerOn(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean(K_WARMUP, true)) {
            p.edit().putInt(K_STAGE, 0).apply();
            scheduleIn(c, WARMUP_MIN[0]);
        } else {
            p.edit().putInt(K_STAGE, -1).apply();
            schedulePeriodic(c);
        }
    }

    /** Alarm fired. */
    static void onTick(Context c) {
        if (screenOn(c)) {
            trigger(c);
        } else {
            Log.i(TAG, "screen off, skip");
        }
        SharedPreferences p = prefs(c);
        int stage = p.getInt(K_STAGE, -1);
        if (stage >= 0 && stage + 1 < WARMUP_MIN.length) {
            p.edit().putInt(K_STAGE, stage + 1).apply();
            scheduleIn(c, WARMUP_MIN[stage + 1] - WARMUP_MIN[stage]);
        } else {
            p.edit().putInt(K_STAGE, -1).apply();
            schedulePeriodic(c);
        }
    }

    static void schedulePeriodic(Context c) {
        int interval = prefs(c).getInt(K_INTERVAL, DEFAULT_INTERVAL);
        if (interval > 0) {
            scheduleIn(c, interval);
        } else {
            cancel(c);
        }
    }

    static void scheduleIn(Context c, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = SystemClock.elapsedRealtime() + minutes * 60_000L;
        // Non-wakeup: if the projector is in standby, the alarm fires right after it wakes up.
        am.setExact(AlarmManager.ELAPSED_REALTIME, at, pending(c));
        Log.i(TAG, "next autofocus in " + minutes + " min");
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, FocusReceiver.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
