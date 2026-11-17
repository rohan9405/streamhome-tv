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
 * newest first. A title is known by its TMDB id, or by its app and name when it has none. Its picture is kept here
 * too, the first time it is shown: the Feed add-on clears its own pictures as its rows change, and need not be asked
 * again for a title already on the list.
 */
final class Watchlist {
    /** The watchlist's tile among the apps: not a package on the TV, so Home opens the watchlist itself. */
    static final String PKG = "streamhome.watchlist";
    private static final String ITEMS = "items";

    private final SharedPreferences prefs;
    private final java.io.File pictures;

    Watchlist(Context ctx) {
        prefs = ctx.getSharedPreferences("watchlist", Context.MODE_PRIVATE);
        pictures = new java.io.File(ctx.getFilesDir(), "watchlist");
    }

    /** Where this title's card picture is kept (it may not be yet). */
    java.io.File picture(Feed.Entry e) {
        return new java.io.File(pictures, pictureName(key(e)));
    }

    /** At most this much of the TV's storage goes to watchlist pictures (some 250 cards). */
    static final long PICTURES_MAX_BYTES = 10L * 1024 * 1024;

    /**
     * Deletes the pictures of titles no longer on the list (and any half-written file), then, while the rest come to
     * more than {@link #PICTURES_MAX_BYTES}, those of the titles added longest ago: such a card fetches its picture again
     * when shown. Off the main thread: it lists files.
     */
    void tidy() {
        java.io.File[] files = pictures.listFiles();
        if (files == null) return;
        JSONArray items = items();
        // Newest first: the oldest are the last kept.
        List<JSONObject> byAdded = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i) != null) byAdded.add(items.optJSONObject(i));
        byAdded.sort((a, b) -> Long.compare(b.optLong("added"), a.optLong("added")));
        List<String> order = new ArrayList<>();
        for (JSONObject o : byAdded) order.add(pictureName(o.optString("key")));
        java.util.Map<String, java.io.File> found = new java.util.HashMap<>();
        for (java.io.File f : files) {
            if (order.contains(f.getName())) found.put(f.getName(), f);
            else //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        for (String name : keepWithin(order, found, PICTURES_MAX_BYTES)) {
            //noinspection ResultOfMethodCallIgnored
            found.get(name).delete();
        }
    }

    /** The pictures to delete so the rest fit in max bytes: the newest titles' are kept first. */
    static List<String> keepWithin(List<String> newestFirst, java.util.Map<String, java.io.File> files, long max) {
        List<String> drop = new ArrayList<>();
        long used = 0;
        for (String name : newestFirst) {
            java.io.File f = files.get(name);
            if (f == null) continue;
            used += f.length();
            if (used > max) drop.add(name);
        }
        return drop;
    }

    /** A file name for a key: TMDB ids and app names hold characters a file name should not. */
    static String pictureName(String key) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(String.format(Locale.ROOT, "%02x", d[i]));
            return b.append(".jpg").toString();
        } catch (java.security.NoSuchAlgorithmException never) {
            throw new IllegalStateException(never);
        }
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
        //noinspection ResultOfMethodCallIgnored
        picture(e).delete();
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
