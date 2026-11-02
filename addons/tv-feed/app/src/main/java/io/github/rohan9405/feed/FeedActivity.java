package io.github.rohan9405.feed;


import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Feed screen, and the module card's target: what each app on this TV publishes (the probe), the rows Home
 * gets, the OMDb key and a Refresh button.
 *
 * <p>Set up from a computer without the remote (my-tv/api-keys.sh): {@code --es omdb_key <key>}, {@code --es tmdb_token <token>} or
 * {@code --ez demo true|false} stores the setting, starts a refresh and closes at once, with nothing shown.
 */
public final class FeedActivity extends Activity {
    private TextView status;
    private FeedSettingsUi settingsUi;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (applySetup(getIntent())) {
            finish();
            return;
        }

        settingsUi = new FeedSettingsUi(this, this::refresh);
        status = settingsUi.diagnostics;
        setContentView(settingsUi.root);
        settingsUi.focusDefault();
        show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) recreate();
    }

    private void refresh() {
        status.setText("Refreshing…");
        settingsUi.refreshing();
        Refresher.kick(this, () -> runOnUiThread(this::show));
    }

    private void show() {
        if (isDestroyed()) return;
        Store.Snapshot s = Store.get(this);
        settingsUi.update(s);
        StringBuilder b = new StringBuilder();
        b.append("Reading TV rows: ").append(checkSelfPermission(Refresher.READ_TV)
                == PackageManager.PERMISSION_GRANTED ? "allowed" : "not allowed yet").append('\n');
        // Each connection on its own: a missing or refused key turns off only what needs it.
        String omdb = Omdb.refused(this);
        b.append("IMDb ratings (OMDb): ").append(!Omdb.hasKey(this)
                ? "needs an OMDb key (free at omdbapi.com; my-tv/api-keys.sh save omdb, then send). Until then, only new TMDB titles get IMDb's rating."
                : !omdb.isEmpty() ? "OMDb refused the key (" + omdb + "): save a new one with my-tv/api-keys.sh"
                : "key set").append('\n');
        b.append("New releases, genres, Read more (TMDB): ").append(!Tmdb.hasToken(this)
                ? "needs a TMDB read token (free at themoviedb.org; my-tv/api-keys.sh save tmdb, then send). Until then the header uses JustWatch, and genres and Read more are off."
                : Tmdb.refused(this) ? "TMDB refused the token: save a new one with my-tv/api-keys.sh"
                : "token set").append('\n');
        Indmoney im = new Indmoney(this);
        b.append("Insight chip (INDmoney): ").append(!im.paired()
                ? "not connected, so the chip stays hidden (optional: my-tv/indmoney-signin.py, then my-tv/indmoney-pair.sh)"
                : im.reconnectNeeded() ? "INDmoney asked to sign in again: my-tv/indmoney-signin.py, then my-tv/indmoney-pair.sh"
                : "connected").append('\n');
        b.append("Last refresh: ").append(s.refreshedMs == 0 ? "never"
                : DateUtils.getRelativeTimeSpanString(s.refreshedMs)).append('\n');
        if (!s.error.isEmpty()) b.append("Problem: ").append(s.error).append('\n');

        // Per section and app: tiles, and how many have a rating.
        PackageManager pm = getPackageManager();
        Map<String, int[]> rows = new LinkedHashMap<>();
        for (Item i : s.items) {
            String name = Item.CONTINUE.equals(i.section) ? "Continue watching"
                    : (Item.HERO.equals(i.section) ? "Header, new in 14 days: "
                    : Item.POOL.equals(i.section) ? "Genre pool: " : "Top 10 candidates: ") + label(pm, i.tab, false);
            int[] n = rows.get(name);
            if (n == null) rows.put(name, n = new int[2]);
            n[0]++;
            if (!i.rating.isEmpty()) n[1]++;
        }
        b.append("\nOn Home\n");
        if (rows.isEmpty()) b.append("  none yet\n");
        for (Map.Entry<String, int[]> e : rows.entrySet()) {
            b.append("  ").append(e.getKey()).append(": ").append(e.getValue()[0]).append(" tiles");
            if (e.getValue()[1] > 0) b.append(", ").append(e.getValue()[1]).append(" rated");
            b.append('\n');
        }

        b.append("\nWhat each app publishes (Continue watching / recommendations / channels)\n");
        if (s.probe.isEmpty()) b.append("  nothing visible\n");
        for (Map.Entry<String, int[]> e : s.probe.entrySet()) {
            int[] n = e.getValue();
            b.append("  ").append(label(pm, e.getKey(), true)).append(": ").append(n[0]).append(" / ").append(n[1])
                    .append(" / ").append(n[2]).append('\n');
        }
        b.append("\nApps not listed publish nothing this app can see.");
        status.setText(b);
    }

    private static String label(PackageManager pm, String pkg, boolean withPackage) {
        try {
            String l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            return withPackage ? l + " (" + pkg + ")" : l;
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private TextView text(int sp, int colour) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setFocusable(false);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return t;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (applySetup(intent)) show();
    }

    /** A setup intent from a computer: store what it carries and refresh. False for an ordinary start. */
    private boolean applySetup(Intent intent) {
        boolean setup = false;
        String k = intent.getStringExtra("omdb_key");
        if (k != null) {
            Omdb.setKey(this, k);
            setup = true;
        }
        String cache = intent.getStringExtra("rating_cache");
        if (cache != null) {
            Omdb.setCacheUrl(this, cache);
            setup = true;
        }
        String t = intent.getStringExtra("tmdb_token");
        if (t != null) {
            Tmdb.setToken(this, t);
            setup = true;
        }
        String client = intent.getStringExtra("indmoney_client_id");
        if (client != null) {
            // A sign-in made on the computer (my-tv/indmoney-pair.sh); the TV renews it from here on.
            Indmoney.pair(this, client, String.valueOf(intent.getStringExtra("indmoney_client_secret")),
                    String.valueOf(intent.getStringExtra("indmoney_refresh_token")));
            Insights.kick(this);
            setup = true;
        }
        if (intent.hasExtra("demo")) {
            Demo.set(this, intent.getBooleanExtra("demo", false));
            setup = true;
        }
        if (setup) Refresher.kick(this, null);
        return setup;
    }
}
