package io.github.rohan9405.feed;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Remote-first settings in Home's typography and dark glass palette. */
final class FeedSettingsUi {
    final LinearLayout root;
    final TextView diagnostics;
    private final Activity act;
    private final LinearLayout page;
    private final TextView heading, subtitle, updated, refreshButton;
    private final List<TextView> navigation = new ArrayList<>();
    private final Typeface regular, medium, light;
    private final float density;
    private int selected;

    FeedSettingsUi(Activity act, Runnable refresh) {
        this.act = act;
        density = act.getResources().getDisplayMetrics().density;
        regular = act.getResources().getFont(R.font.outfit_regular);
        medium = act.getResources().getFont(R.font.outfit_medium);
        light = act.getResources().getFont(R.font.outfit_light);
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(36), dp(30), dp(36), dp(28));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF10151F, 0xFF090C13}));
        LinearLayout rail = column();
        root.addView(rail, new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView brand = text("StreamHome", 22, Color.WHITE, medium);
        rail.addView(brand);
        TextView label = text("SETTINGS", 10, 0xFF7D879B, medium);
        label.setLetterSpacing(0.18f);
        label.setPadding(0, dp(9), 0, dp(30));
        rail.addView(label);
        String[] names = {"Finance chip", "Connections", "Diagnostics"};
        for (int i = 0; i < names.length; i++) {
            int at = i;
            TextView item = text(names[i], 15, Color.WHITE, regular);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), 0, dp(10), 0);
            item.setFocusable(true);
            item.setOnFocusChangeListener((v, focused) -> {
                if (focused && selected != at) select(at);
                paintNavigation();
            });
            item.setOnClickListener(v -> select(at));
            navigation.add(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            lp.bottomMargin = dp(6);
            rail.addView(item, lp);
        }
        rail.addView(new View(act), new LinearLayout.LayoutParams(1, 0, 1));
        TextView back = text("‹  Back to Home", 13, 0xFFABB4C5, regular);
        back.setFocusable(true);
        back.setPadding(dp(12), dp(12), dp(10), dp(12));
        back.setOnFocusChangeListener((v, has) -> v.setBackground(shape(has ? 0xFF283347 : Color.TRANSPARENT, 12, has ? 0xFFB1BED2 : Color.TRANSPARENT)));
        back.setOnClickListener(v -> act.finish());
        rail.addView(back);

        LinearLayout content = column();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        cp.leftMargin = dp(30);
        root.addView(content, cp);
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        heading = text("", 30, Color.WHITE, light);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        refreshButton = text("Refresh feed", 14, 0xFFDBE4F3, medium);
        refreshButton.setFocusable(true);
        refreshButton.setGravity(Gravity.CENTER);
        refreshButton.setPadding(dp(17), dp(10), dp(17), dp(10));
        refreshButton.setBackground(shape(0xFF1C2432, 18, 0xFF354053));
        refreshButton.setOnFocusChangeListener((v, has) -> {
            v.setBackground(shape(has ? 0xFFF0F5FF : 0xFF1C2432, 18, has ? Color.WHITE : 0xFF354053));
            refreshButton.setTextColor(has ? 0xFF111722 : 0xFFDBE4F3);
        });
        refreshButton.setOnClickListener(v -> refresh.run());
        header.addView(refreshButton);
        content.addView(header);
        subtitle = text("", 13, 0xFF98A4B8, regular);
        subtitle.setPadding(0, dp(6), 0, dp(5));
        content.addView(subtitle);
        updated = text("", 11, 0xFF6F7D95, regular);
        content.addView(updated);
        View line = new View(act);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        rule.setMargins(0, dp(17), 0, dp(17));
        content.addView(line, rule);
        ScrollView scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        page = column();
        page.setPadding(dp(3), dp(3), dp(3), dp(8));
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        diagnostics = text("", 12, 0xFFB5C0D2, regular);
        diagnostics.setLineSpacing(dp(5), 1f);
        diagnostics.setPadding(dp(18), dp(16), dp(18), dp(16));
        diagnostics.setBackground(shape(0xFF141B28, 16, 0xFF273245));
        // Focusable, so Right from the menu enters it and Up / Down scroll a list longer than the screen.
        diagnostics.setFocusable(true);
        diagnostics.setOnFocusChangeListener((v, has) -> v.setBackground(shape(0xFF141B28, 16, has ? 0xFFC8D6EA : 0xFF273245)));
        select(0);
    }

    void focusDefault() { navigation.get(0).requestFocusFromTouch(); }
    void refreshing() { refreshButton.setText("Refreshing…"); }
    void update(Store.Snapshot snapshot) {
        refreshButton.setText("Refresh feed");
        updated.setText(snapshot.refreshedMs == 0 ? "Feed has not refreshed yet" : "Updated " + DateUtils.getRelativeTimeSpanString(snapshot.refreshedMs));
        if (selected == 1) connections();
    }

    private void select(int at) {
        selected = at;
        if (diagnostics != null && diagnostics.getParent() instanceof ViewGroup) ((ViewGroup) diagnostics.getParent()).removeView(diagnostics);
        page.removeAllViews();
        if (at == 0) finance();
        else if (at == 1) connections();
        else {
            heading.setText("Diagnostics");
            subtitle.setText("Source and refresh details for troubleshooting.");
            page.addView(diagnostics);
        }
        paintNavigation();
    }

    private void paintNavigation() {
        for (int i = 0; i < navigation.size(); i++) {
            TextView item = navigation.get(i);
            boolean focused = item.hasFocus(), active = selected == i;
            item.setBackground(shape(focused ? 0xFF263447 : active ? 0xFF182A28 : Color.TRANSPARENT, 12,
                    focused ? 0xFF9EB0CD : active ? 0xFF2D4D46 : Color.TRANSPARENT));
            item.setTextColor(active ? 0xFFA8F3CF : focused ? Color.WHITE : 0xFF9EAAC0);
        }
    }

    private void finance() {
        heading.setText("Finance chip");
        subtitle.setText("Choose what rotates beside the clock on Home.");
        LinearLayout controls = row();
        page.addView(controls);
        addCard(controls, "Show on Home", "Finance chip", () -> Insights.chipOn(act) ? "On" : "Off", () -> {
            Insights.settings(act).edit().putBoolean("chip", !Insights.chipOn(act)).apply();
            Insights.changed(act);
        }, 58, false);
        addCard(controls, "Rotation", "Time between views", () -> Insights.rotationSec(act) + " sec", () -> {
            int at = 0;
            while (at < Insights.ROTATIONS.length && Insights.ROTATIONS[at] != Insights.rotationSec(act)) at++;
            Insights.settings(act).edit().putInt("rotation", Insights.ROTATIONS[(at + 1) % Insights.ROTATIONS.length]).apply();
            Insights.changed(act);
        }, 58, true);
        TextView group = text("VISIBLE VIEWS", 10, 0xFF8D9BB1, medium);
        group.setLetterSpacing(0.15f);
        group.setPadding(dp(2), dp(19), 0, dp(10));
        page.addView(group);
        String[] names = {"Portfolio", "Mutual Funds", "Equity", "US Stocks", "Nifty", "Sensex"};
        String[] descriptions = {"Combined daily movement", "Indian mutual funds", "Indian shares", "US holdings, shown in INR", "NSE 50 index", "BSE benchmark"};
        for (int i = 0; i < names.length; i += 2) {
            LinearLayout pair = row();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            page.addView(pair, lp);
            for (int j = i; j < i + 2; j++) {
                String key = Insights.VIEWS[j];
                addCard(pair, names[j], descriptions[j], () -> Insights.viewOn(act, key) ? "On" : "Off", () -> {
                    android.content.SharedPreferences.Editor change = Insights.settings(act).edit();
                    for (String k : Insights.VIEWS) change.putBoolean(k, Insights.viewOn(act, k));
                    change.putBoolean(key, !Insights.viewOn(act, key)).apply();
                    Insights.changed(act);
                }, 68, j > i);
            }
        }
        TextView note = text("Saved automatically · Missing asset classes are skipped", 11, 0xFF74839B, regular);
        note.setPadding(dp(2), dp(3), 0, 0);
        page.addView(note);
    }

    private interface Value { String get(); }
    private void addCard(LinearLayout parent, String title, String description, Value value, Runnable action, int height, boolean gap) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), 0, dp(14), 0);
        card.setFocusable(true);
        card.setBackground(shape(0xFF171E2B, 14, 0xFF2B3546));
        LinearLayout words = column();
        TextView name = text(title, 15, 0xFFEAF0FA, medium);
        TextView detail = text(description, 11, 0xFF8998AF, regular);
        detail.setPadding(0, dp(4), 0, 0);
        words.addView(name);words.addView(detail);
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = text("", 12, 0xFFA5F3CC, medium);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        card.addView(badge);
        Runnable paint = () -> {
            String state = value.get();
            badge.setText(state);
            boolean off = "Off".equals(state);
            badge.setTextColor(off ? 0xFF8D9AB1 : 0xFFA5F3CC);
            badge.setBackground(shape(off ? 0xFF242D3D : 0xFF203C34, 10, Color.TRANSPARENT));
            card.setContentDescription(title + ": " + state);
        };
        paint.run();
        card.setOnClickListener(v -> { action.run();paint.run(); });
        card.setOnFocusChangeListener((v, has) -> {
            v.setBackground(shape(has ? 0xFF27354A : 0xFF171E2B, 14, has ? 0xFFC8D6EA : 0xFF2B3546));
            v.animate().scaleX(has ? 1.012f : 1f).scaleY(has ? 1.012f : 1f).setDuration(130).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(height), 1);
        if (gap) lp.leftMargin = dp(12);
        parent.addView(card, lp);
    }

    private void connections() {
        page.removeAllViews();
        heading.setText("Connections");
        subtitle.setText("Services that bring recommendations and finance to Home.");
        connection("TV recommendations", "Continue watching and app-published rows", act.checkSelfPermission(Refresher.READ_TV) == PackageManager.PERMISSION_GRANTED,
                () -> act.requestPermissions(new String[] {Refresher.READ_TV}, 1));
        connection("Streaming catalogue", "TMDB · New releases, genres and title details", Tmdb.hasToken(act) && !Tmdb.refused(act),
                () -> info("Streaming catalogue", "Add or renew your TMDB read token from your computer with my-tv/api-keys.sh, then send it to the TV."));
        connection("IMDb ratings", "OMDb · Ratings on title cards", Omdb.hasKey(act) && Omdb.refused(act).isEmpty(),
                () -> info("IMDb ratings", "Add or renew your OMDb key from your computer with my-tv/api-keys.sh, then send it to the TV."));
        Indmoney im = new Indmoney(act);
        connection("Portfolio & markets", "INDmoney · Read-only finance connection", im.paired() && !im.reconnectNeeded(),
                () -> info("Portfolio & markets", "Sign in from your computer with my-tv/indmoney-signin.py, then pair with my-tv/indmoney-pair.sh. Your existing connection stays in place."));
    }

    private void connection(String title, String description, boolean ready, Runnable fix) {
        LinearLayout pair = row();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);page.addView(pair, lp);
        addCard(pair, title, description, () -> ready ? "Ready" : "Set up", () -> {
            if (ready) info(title, description + "\n\nConnected and ready to use."); else fix.run();
        }, 70, false);
    }
    private void info(String title, String message) {
        new android.app.AlertDialog.Builder(act).setTitle(title).setMessage(message).setPositiveButton("Done", null).show();
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(act);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(act);v.setOrientation(LinearLayout.HORIZONTAL);return v; }
    private TextView text(String text, int size, int color, Typeface font) {
        TextView v = new TextView(act);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setTypeface(font);return v;
    }
    private GradientDrawable shape(int color, int radius, int edge) {
        GradientDrawable bg = new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));bg.setStroke(dp(1), edge);return bg;
    }
    private int dp(int value) { return Math.round(value * density); }
}
