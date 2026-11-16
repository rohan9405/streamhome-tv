package io.github.rohan9405.feed;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IMDb's own ratings, for titles OMDb knows but has no rating for yet: OMDb catches up weeks after a release, while
 * IMDb shows a rating as soon as a title has a few votes. Asked of the GraphQL service behind imdb.com, by IMDb id,
 * fifty titles a request, so a refresh makes one or two requests. Not a published API (IMDb allows limited personal,
 * non-commercial use): anything but a clean answer leaves the titles unrated. Each rating is kept for a day.
 */
final class Imdb {
    private static final String API = "https://caching.graphql.imdb.com/";
    private static final long KEEP_MS = 24L * 60 * 60 * 1000;
    private static final int PER_REQUEST = 50;

    private final SharedPreferences prefs;

    Imdb(Context ctx) {
        prefs = ctx.getSharedPreferences("imdb", Context.MODE_PRIVATE);
    }

    /** IMDb id ("tt123") -> rating ("7.4"), for the ids IMDb has a rating for. */
    Map<String, String> ratings(Collection<String> ids) {
        Map<String, String> out = new HashMap<>();
        List<String> ask = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String id : ids) {
            if (!id.matches("tt\\d+") || out.containsKey(id) || ask.contains(id)) continue;
            String saved = prefs.getString(id, "");
            int at = saved.indexOf('@');
            if (at >= 0 && now - Long.parseLong(saved.substring(at + 1)) < KEEP_MS) {
                if (at > 0) out.put(id, saved.substring(0, at));
            } else {
                ask.add(id);
            }
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (int from = 0; from < ask.size(); from += PER_REQUEST) {
            List<String> chunk = ask.subList(from, Math.min(ask.size(), from + PER_REQUEST));
            Map<String, String> got;
            try {
                got = parse(post(chunk, false));
            } catch (IOException | JSONException e) {
                android.util.Log.w("Feed", "IMDb: " + e);
                break;
            }
            for (String id : chunk) {
                String r = got.get(id);
                if (r != null) out.put(id, r);
                edit.putString(id, (r == null ? "" : r) + "@" + now);
            }
        }
        edit.apply();
        return out;
    }

    /**
     * One title's rating and how many people rated it, {"7.4", "123456"} ("" where IMDb has none), for Read more. Kept a
     * day; {@code fresh} asks IMDb again whatever was kept. Null when IMDb could not be asked.
     */
    String[] summary(String id, boolean fresh) {
        if (!id.matches("tt\\d+")) return null;
        String saved = prefs.getString("s:" + id, "");
        int at = saved.lastIndexOf('@');
        long now = System.currentTimeMillis();
        if (!fresh && at >= 0 && now - Long.parseLong(saved.substring(at + 1)) < KEEP_MS) {
            String[] kept = saved.substring(0, at).split("\\|", -1);
            if (kept.length == 2) return kept;
        }
        String[] got;
        try {
            got = parseSummary(post(java.util.Collections.singletonList(id), true), id);
        } catch (IOException | JSONException e) {
            android.util.Log.w("Feed", "IMDb: " + e);
            return null;
        }
        prefs.edit().putString("s:" + id, got[0] + "|" + got[1] + "@" + now).apply();
        if (!got[0].isEmpty()) prefs.edit().putString(id, got[0] + "@" + now).apply();
        return got;
    }

    /** {"data":{"titles":[{"id":"tt1","ratingsSummary":{"aggregateRating":7.4,"voteCount":1234}}]}} -> {"7.4", "1234"}. */
    static String[] parseSummary(String body, String id) throws JSONException {
        JSONObject data = new JSONObject(body).optJSONObject("data");
        JSONArray titles = data == null ? null : data.optJSONArray("titles");
        for (int i = 0; titles != null && i < titles.length(); i++) {
            JSONObject t = titles.optJSONObject(i);
            JSONObject s = t == null || !id.equals(t.optString("id")) ? null : t.optJSONObject("ratingsSummary");
            if (s == null) continue;
            double r = s.isNull("aggregateRating") ? 0 : s.optDouble("aggregateRating", 0);
            long votes = s.isNull("voteCount") ? 0 : s.optLong("voteCount", 0);
            return new String[] {r > 0 && r <= 10 ? String.format(java.util.Locale.ROOT, "%.1f", r) : "", votes > 0 ? String.valueOf(votes) : ""};
        }
        return new String[] {"", ""};
    }

    /**
     * The rating IMDb gave when Read more last asked for this title, if within a week; "" otherwise. A refresh prefers it to
     * OMDb's, which can lag IMDb by weeks, so a rating refreshed on purpose is not undone by the next refresh.
     */
    String kept(String id) {
        String saved = prefs.getString("s:" + id, "");
        int at = saved.lastIndexOf('@'), bar = saved.indexOf('|');
        if (at < 0 || bar < 0) return "";
        try {
            if (System.currentTimeMillis() - Long.parseLong(saved.substring(at + 1)) > 7 * KEEP_MS) return "";
        } catch (NumberFormatException e) {
            return "";
        }
        return saved.substring(0, bar);
    }

    /** {"data":{"titles":[{"id":"tt1","ratingsSummary":{"aggregateRating":7.4}}]}} -> tt1 -> "7.4". */
    static Map<String, String> parse(String body) throws JSONException {
        Map<String, String> out = new HashMap<>();
        JSONObject data = new JSONObject(body).optJSONObject("data");
        JSONArray titles = data == null ? null : data.optJSONArray("titles");
        for (int i = 0; titles != null && i < titles.length(); i++) {
            JSONObject t = titles.optJSONObject(i);
            JSONObject s = t == null ? null : t.optJSONObject("ratingsSummary");
            if (s == null || s.isNull("aggregateRating")) continue;
            double r = s.optDouble("aggregateRating", 0);
            if (r > 0 && r <= 10) out.put(t.optString("id"), String.format(java.util.Locale.ROOT, "%.1f", r));
        }
        return out;
    }

    private static String post(List<String> ids, boolean votes) throws IOException, JSONException {
        String body = new JSONObject().put("query", "query($ids: [ID!]!) { titles(ids: $ids) { id ratingsSummary { aggregateRating"
                + (votes ? " voteCount" : "") + " } } }")
                .put("variables", new JSONObject().put("ids", new JSONArray(ids))).toString();
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-imdb-client-name", "imdb-web-next");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; BRAVIA) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return new String(Net.read(in, 1 << 20), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
