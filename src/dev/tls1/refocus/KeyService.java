package dev.tls1.refocus;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Intercepts the assigned remote-control key and triggers autofocus. */
public class KeyService extends AccessibilityService {
    private long lastTrigger;

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        android.content.SharedPreferences p = Focus.prefs(this);
        if (p.getBoolean(Focus.K_LEARN, false)) return false; // let MainActivity capture it
        int key = p.getInt(Focus.K_KEY, 0);
        if (key == 0 || e.getKeyCode() != key) return false;

        if (e.getAction() == KeyEvent.ACTION_UP && e.getRepeatCount() == 0) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastTrigger > 3000) {
                lastTrigger = now;
                Log.i(Focus.TAG, "remote key " + key + " -> autofocus");
                Focus.trigger(this);
            }
        }
        return true; // consume down/up so the key does nothing else
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
