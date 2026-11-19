package dev.tls1.refocus;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int[] INTERVALS = {0, 15, 30, 60, 120};

    private TextView status;
    private Button intervalBtn;
    private Button warmupBtn;
    private Button keyBtn;
    private Button keystoneBtn;
    private static final String KEYSTONE_FLAG = "close_keystone_from_tvinput";
    private boolean learning;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(18, 18, 22));
        root.setPadding(dp(48), dp(32), dp(48), dp(32));

        TextView title = new TextView(this);
        title.setText("Refocus");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.rgb(170, 170, 180));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(24));
        root.addView(status);

        Button now = button("Focus now");
        now.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Focus.trigger(MainActivity.this);
                refresh();
            }
        });
        root.addView(now);

        intervalBtn = button("");
        intervalBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                SharedPreferences p = Focus.prefs(MainActivity.this);
                int cur = p.getInt(Focus.K_INTERVAL, Focus.DEFAULT_INTERVAL);
                int idx = 0;
                for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == cur) idx = i;
                int next = INTERVALS[(idx + 1) % INTERVALS.length];
                p.edit().putInt(Focus.K_INTERVAL, next).putInt(Focus.K_STAGE, -1).apply();
                Focus.schedulePeriodic(MainActivity.this);
                refresh();
            }
        });
        root.addView(intervalBtn);

        warmupBtn = button("");
        warmupBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                SharedPreferences p = Focus.prefs(MainActivity.this);
                p.edit().putBoolean(Focus.K_WARMUP, !p.getBoolean(Focus.K_WARMUP, true)).apply();
                refresh();
            }
        });
        root.addView(warmupBtn);

        keyBtn = button("");
        keyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setLearning(!learning);
            }
        });
        keyBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                Focus.prefs(MainActivity.this).edit().putInt(Focus.K_KEY, 0).apply();
                setLearning(false);
                return true;
            }
        });
        root.addView(keyBtn);

        keystoneBtn = button("");
        keystoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean blocked = keystoneBlocked();
                try {
                    android.provider.Settings.Global.putInt(getContentResolver(), KEYSTONE_FLAG, blocked ? 0 : 1);
                } catch (SecurityException ex) {
                    keystoneBtn.setText("No permission: adb shell pm grant dev.tls1.refocus android.permission.WRITE_SECURE_SETTINGS");
                    return;
                }
                refresh();
            }
        });
        root.addView(keystoneBtn);

        setContentView(root);
        now.requestFocus();

        // First launch / reinstall: make sure the periodic schedule is running.
        SharedPreferences p = Focus.prefs(this);
        if (p.getInt(Focus.K_STAGE, -1) < 0) Focus.schedulePeriodic(this);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void setLearning(boolean on) {
        learning = on;
        Focus.prefs(this).edit().putBoolean(Focus.K_LEARN, on).apply();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (learning) setLearning(false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (learning && e.getAction() == KeyEvent.ACTION_UP) {
            int k = e.getKeyCode();
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                    return super.dispatchKeyEvent(e); // navigation keys can't be assigned
                case KeyEvent.KEYCODE_BACK:
                    setLearning(false);
                    return true;
                default:
                    Focus.prefs(this).edit().putInt(Focus.K_KEY, k).apply();
                    setLearning(false);
                    return true;
            }
        }
        if (learning && e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (k != KeyEvent.KEYCODE_DPAD_UP && k != KeyEvent.KEYCODE_DPAD_DOWN
                    && k != KeyEvent.KEYCODE_DPAD_LEFT && k != KeyEvent.KEYCODE_DPAD_RIGHT
                    && k != KeyEvent.KEYCODE_DPAD_CENTER && k != KeyEvent.KEYCODE_ENTER) {
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean keystoneBlocked() {
        return android.provider.Settings.Global.getInt(getContentResolver(), KEYSTONE_FLAG, 0) == 1;
    }

    private boolean canWriteSecure() {
        return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean keyServiceEnabled() {
        String s = android.provider.Settings.Secure.getString(getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private void refresh() {
        SharedPreferences p = Focus.prefs(this);
        int interval = p.getInt(Focus.K_INTERVAL, Focus.DEFAULT_INTERVAL);
        boolean warm = p.getBoolean(Focus.K_WARMUP, true);
        long last = p.getLong(Focus.K_LAST, 0);
        String lastStr = last == 0 ? "never"
                : new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(new Date(last));
        status.setText("Last autofocus: " + lastStr + "  ·  total: " + p.getInt(Focus.K_COUNT, 0));
        intervalBtn.setText("Periodic: " + (interval == 0 ? "off" : "every " + interval + " min"));
        warmupBtn.setText("After power-on (15 and 35 min): " + (warm ? "on" : "off"));
        int key = p.getInt(Focus.K_KEY, 0);
        if (learning) {
            keyBtn.setText("Press a remote button…  (Back to cancel)");
        } else {
            String k = key == 0 ? "not assigned" : KeyEvent.keyCodeToString(key).replace("KEYCODE_", "") + " (" + key + ")";
            keyBtn.setText("Remote button: " + k + (keyServiceEnabled() ? "" : "  ·  service disabled"));
        }
        keystoneBtn.setText("Auto keystone on movement: " + (keystoneBlocked() ? "off" : "on")
                + (canWriteSecure() ? "" : "  ·  permission required (see README)"));
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(10));
        normal.setColor(Color.rgb(44, 44, 54));
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(10));
        focused.setColor(Color.rgb(40, 110, 220));
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{android.R.attr.state_pressed}, focused);
        sl.addState(new int[]{}, normal);
        b.setBackground(sl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(560), dp(64));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
