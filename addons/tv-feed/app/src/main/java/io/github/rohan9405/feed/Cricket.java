package io.github.rohan9405.feed;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cricket on Home's chip: live, upcoming and finished matches of the teams the owner follows, at most {@value #MAX}.
 * The scores come from ESPN's public scoreboard, which is not a published API and asks for no key, so it can change
 * without notice: anything but a clean answer keeps the last matches. Off until a team is chosen on the Feed screen, and
 * nothing is fetched while off. Asked when Home is on screen: every minute while a followed match is live, else every
 * 15 minutes.
 */
final class Cricket {
    private static final String URL = "https://site.api.espn.com/apis/personalized/v2/scoreboard/header?sport=cricket&region=in&lang=en";
    static final int MAX = 3;
    /** Seconds a match stays on the chip, as offered on the Feed screen. */
    static final int[] ROTATIONS = {5, 10, 15};
    static final String[] COUNTRIES = {"India", "Australia", "England", "Pakistan", "New Zealand", "South Africa",
            "Sri Lanka", "Bangladesh", "West Indies", "Afghanistan"};
    static final String[] IPL = {"Mumbai Indians", "Chennai Super Kings", "Royal Challengers Bengaluru",
            "Kolkata Knight Riders", "Sunrisers Hyderabad", "Delhi Capitals", "Rajasthan Royals", "Punjab Kings",
            "Gujarat Titans", "Lucknow Super Giants"};
    private static final long LIVE_MS = 60_000, QUIET_MS = 15 * 60_000, RESULT_MS = 2 * 24 * 60 * 60_000L;
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Cricket() {
    }

    static SharedPreferences settings(Context ctx) {
        return ctx.getSharedPreferences("cricket", Context.MODE_PRIVATE);
    }

    static boolean on(Context ctx) {
        return settings(ctx).getBoolean("on", false);
    }

    static int rotationSec(Context ctx) {
        return settings(ctx).getInt("rotation", 10);
    }

    /** On the chip, the matches one after another and then the finance views; off, one of each in turn. */
    static boolean together(Context ctx) {
        return settings(ctx).getBoolean("together", true);
    }

    static Set<String> teams(Context ctx) {
        return new HashSet<>(settings(ctx).getStringSet("teams", Collections.emptySet()));
    }

    static void toggleTeam(Context ctx, String team) {
        Set<String> t = teams(ctx);
        if (!t.remove(team)) t.add(team);
        settings(ctx).edit().putStringSet("teams", t).apply();
    }

    /** A setting changed: Home hears it at once, and what it needs is fetched. */
    static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(FeedProvider.insightsUri(), null);
        if (on(ctx) && !teams(ctx).isEmpty()) kick(ctx);
    }

    /** The matches for Home's chip, as saved; empty while off. */
    static JSONArray forHome(Context ctx) {
        if (!on(ctx)) return new JSONArray();
        return select(saved(ctx).optJSONArray("matches"), teams(ctx), System.currentTimeMillis());
    }

    static void refreshIfStale(Context ctx) {
        if (!on(ctx) || teams(ctx).isEmpty()) return;
        JSONObject s = saved(ctx);
        boolean live = false;
        JSONArray mine = select(s.optJSONArray("matches"), teams(ctx), System.currentTimeMillis());
        for (int i = 0; i < mine.length(); i++) live |= "live".equals(mine.optJSONObject(i).optString("state"));
        if (System.currentTimeMillis() - s.optLong("tried") > (live ? LIVE_MS : QUIET_MS)) kick(ctx);
    }

    private static void kick(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) return;
        ONE.execute(() -> {
            try {
                JSONObject s = saved(app);
                try {
                    Net.Response r = Net.request(URL, "Accept", "application/json", 1 << 21);
                    if (r.code / 100 == 2) s.put("matches", parse(new JSONObject(r.body)));
                } catch (IOException | JSONException | RuntimeException e) {
                    android.util.Log.w("Feed", "cricket: " + e);
                }
                s.put("tried", System.currentTimeMillis());
                store(app, s);
                app.getContentResolver().notifyChange(FeedProvider.insightsUri(), null);
            } catch (JSONException ignored) {
                // Asked again next time.
            } finally {
                RUNNING.set(false);
            }
        });
    }

    /** Every match on ESPN's board, each as a small object. A match "in" progress with no score has not begun. */
    static JSONArray parse(JSONObject board) throws JSONException {
        JSONArray out = new JSONArray();
        JSONArray sports = board.optJSONArray("sports");
        for (int s = 0; sports != null && s < sports.length(); s++) {
            JSONArray leagues = sports.getJSONObject(s).optJSONArray("leagues");
            for (int l = 0; leagues != null && l < leagues.length(); l++) {
                JSONObject league = leagues.getJSONObject(l);
                JSONArray events = league.optJSONArray("events");
                for (int e = 0; events != null && e < events.length(); e++) {
                    JSONObject m = match(events.getJSONObject(e), league.optString("name"));
                    if (m != null) out.put(m);
                }
            }
        }
        return out;
    }

    private static JSONObject match(JSONObject e, String league) throws JSONException {
        JSONArray teams = e.optJSONArray("competitors");
        if (teams == null || teams.length() < 2) return null;
        JSONObject a = teams.getJSONObject(0), b = teams.getJSONObject(1);
        long at;
        try {
            at = Instant.parse(e.optString("date")).toEpochMilli();
        } catch (RuntimeException bad) {
            return null;
        }
        JSONObject full = e.optJSONObject("fullStatus");
        String status = e.optString("status");
        boolean scored = !a.optString("score").isEmpty() || !b.optString("score").isEmpty();
        String state = "post".equals(status) ? "result" : "in".equals(status) && scored ? "live" : "next";
        return new JSONObject().put("state", state).put("at", at).put("league", league)
                .put("status", e.optString("summary"))
                .put("summary", full == null ? "" : full.optString("summary"))
                .put("a_name", a.optString("displayName")).put("a_abbr", a.optString("abbreviation")).put("a_score", a.optString("score"))
                .put("b_name", b.optString("displayName")).put("b_abbr", b.optString("abbreviation")).put("b_score", b.optString("score"));
    }

    /** The followed teams' matches: live ones, then the next to start, then the latest results; at most {@value #MAX}. */
    static JSONArray select(JSONArray matches, Set<String> teams, long nowMs) {
        Set<String> wanted = new HashSet<>();
        for (String t : teams) wanted.add(normal(t));
        List<JSONObject> live = new ArrayList<>(), next = new ArrayList<>(), done = new ArrayList<>();
        for (int i = 0; matches != null && i < matches.length(); i++) {
            JSONObject m = matches.optJSONObject(i);
            if (m == null || !(wanted.contains(normal(m.optString("a_name"))) || wanted.contains(normal(m.optString("b_name"))))) continue;
            String state = m.optString("state");
            if ("live".equals(state)) live.add(m);
            else if ("next".equals(state)) next.add(m);
            else if (nowMs - m.optLong("at") < RESULT_MS) done.add(m);
        }
        Collections.sort(next, (x, y) -> Long.compare(x.optLong("at"), y.optLong("at")));
        Collections.sort(done, (x, y) -> Long.compare(y.optLong("at"), x.optLong("at")));
        JSONArray out = new JSONArray();
        for (List<JSONObject> group : java.util.Arrays.asList(live, next, done)) {
            for (JSONObject m : group) if (out.length() < MAX) out.put(m);
        }
        return out;
    }

    /**
     * Team names compare whole, so "India" is not "India A", "India Women" or "Mumbai Indians". ESPN adds a league in
     * brackets to some, and Bengaluru's IPL side has gone by both spellings.
     */
    static String normal(String name) {
        return name.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim().toLowerCase(Locale.ROOT).replace("bangalore", "bengaluru");
    }

    private static JSONObject saved(Context ctx) {
        try (InputStream in = new FileInputStream(file(ctx))) {
            return new JSONObject(new String(Net.read(in, 1 << 21), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return new JSONObject();
        }
    }

    private static void store(Context ctx, JSONObject o) {
        File f = file(ctx), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "cricket.json");
    }
}
