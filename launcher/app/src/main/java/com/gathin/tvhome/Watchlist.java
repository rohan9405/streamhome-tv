package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The owner's watchlist, kept on the TV by the launcher (which stays offline): films and series added from Read more,
 * newest first. A title is known by its TMDB id, or by its app and name when it has none. Its picture is the TMDB
 * address the Feed add-on fetches again when needed, so the list outlives the add-on clearing its picture cache.
 */
final class Watchlist {
    /** The watchlist's tile among the apps: not a package on the TV, so Home opens the watchlist itself. */
    static final String PKG = "streamhome.watchlist";
    private static final String ITEMS = "items";

    private final SharedPreferences prefs;

    Watchlist(Context ctx) {
        prefs = ctx.getSharedPreferences("watchlist", Context.MODE_PRIVATE);
    }

    static String key(Feed.Entry e) {
        return !e.tmdb.isEmpty() ? e.tmdb : e.pkg + "|" + e.title.toLowerCase(Locale.ROOT);
    }

    boolean contains(Feed.Entry e) {
        String k = key(e);
        JSONArray items = items();
        for (int i = 0; i < items.length(); i++) if (k.equals(items.optJSONObject(i).optString("key"))) return true;
        return false;
    }

    /** {@code art}: a TMDB picture address for the card, "" when there is none; {@code rating}: as Read more showed it. */
    void add(Feed.Entry e, String art, String rating) {
        JSONObject item = toJson(e, art, System.currentTimeMillis());
        try {
            if (rating != null && !rating.isEmpty()) item.put("rating", rating);
        } catch (JSONException x) {
            throw new IllegalStateException(x);
        }
        save(put(items(), item));
    }

    /** A rating Read more just refreshed: the saved card shows it too. */
    void rerate(Feed.Entry e, String rating) {
        if (rating == null || rating.isEmpty()) return;
        String k = key(e);
        JSONArray items = items();
        try {
            for (int i = 0; i < items.length(); i++) if (k.equals(items.optJSONObject(i).optString("key"))) items.optJSONObject(i).put("rating", rating);
        } catch (JSONException x) {
            return;
        }
        save(items);
    }

    void remove(Feed.Entry e) {
        save(without(items(), key(e)));
    }

    /** Series or films, the latest added first. */
    List<Feed.Entry> list(boolean series) {
        return pick(items(), series);
    }

    static List<Feed.Entry> pick(JSONArray items, boolean series) {
        List<JSONObject> chosen = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o != null && series(o) == series) chosen.add(o);
        }
        chosen.sort((a, b) -> Long.compare(b.optLong("added"), a.optLong("added")));
        List<Feed.Entry> out = new ArrayList<>();
        for (JSONObject o : chosen) out.add(fromJson(o));
        return out;
    }

    /** A series by its kind or its TMDB id; anything else (a film, or a title nothing is known about) is a film. */
    static boolean series(JSONObject o) {
        return "tv".equals(o.optString("kind")) || o.optString("tmdb").startsWith("tv:");
    }

    /** The list with this title in it once, as just added. */
    static JSONArray put(JSONArray items, JSONObject item) {
        JSONArray out = without(items, item.optString("key"));
        out.put(item);
        return out;
    }

    static JSONArray without(JSONArray items, String key) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o != null && !key.equals(o.optString("key"))) out.put(o);
        }
        return out;
    }

    static JSONObject toJson(Feed.Entry e, String art, long added) {
        try {
            return new JSONObject().put("key", key(e)).put("added", added).put("title", e.title).put("subtitle", e.subtitle)
                    .put("rating", e.rating).put("poster", e.poster).put("backdrop", e.backdrop).put("intent", e.intent)
                    .put("pkg", e.pkg).put("overview", e.overview).put("kind", !e.kind.isEmpty() ? e.kind
                            : e.tmdb.startsWith("tv:") ? "tv" : e.tmdb.startsWith("movie:") ? "movie" : "")
                    .put("year", e.year).put("genre", e.genre).put("tmdb", e.tmdb).put("released", e.released)
                    .put("art", art == null || art.isEmpty() ? e.artSource : art).put("plain_art", e.plainArt);
        } catch (JSONException x) {
            throw new IllegalStateException(x);
        }
    }

    static Feed.Entry fromJson(JSONObject o) {
        Feed.Entry e = new Feed.Entry("watchlist", o.optString("pkg"), 0, o.optString("title"), o.optString("subtitle"),
                o.optString("rating"), -1, o.optString("poster"), o.optString("backdrop"), o.optString("intent"),
                o.optString("pkg"), o.optString("overview"), o.optString("kind"), o.optString("year"), o.optString("genre"));
        e.tmdb = o.optString("tmdb");
        e.released = o.optString("released");
        e.artSource = o.optString("art");
        e.plainArt = o.optBoolean("plain_art", true);
        return e;
    }

    private JSONArray items() {
        try {
            return new JSONArray(prefs.getString(ITEMS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void save(JSONArray items) {
        prefs.edit().putString(ITEMS, items.toString()).apply();
    }
}
