package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Profile-owned title identities, shared across services. Never writes into a streaming app's account. */
final class SeenTitles {
    private final Set<String> keys;
    SeenTitles(Set<String> saved) { keys = new HashSet<>(saved); }
    Set<String> saved() { return new HashSet<>(keys); }

    private static String fallback(Feed.Entry e) {
        String title = Normalizer.normalize(e.title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .trim().replaceAll("\\s+", " ");
        return "title:" + e.kind + "|" + title + "|" + e.year;
    }

    boolean contains(Feed.Entry e) {
        return !e.tmdb.isEmpty() && keys.contains("tmdb:" + e.tmdb) || keys.contains(fallback(e));
    }
    void mark(Feed.Entry e) {
        if (!e.tmdb.isEmpty()) keys.add("tmdb:" + e.tmdb);
        keys.add(fallback(e));
    }
    void unmark(Feed.Entry e) {
        if (!e.tmdb.isEmpty()) keys.remove("tmdb:" + e.tmdb);
        keys.remove(fallback(e));
    }

    List<Feed.Entry> available(List<Feed.Entry> candidates, int max) {
        List<Feed.Entry> out = new ArrayList<>();
        Set<String> added = new HashSet<>();
        if (max <= 0) return out;
        for (Feed.Entry e : candidates) {
            String id = e.tmdb.isEmpty() ? fallback(e) : "tmdb:" + e.tmdb;
            if (!contains(e) && !added.contains(id) && !added.contains(fallback(e))) {
                added.add(id);
                added.add(fallback(e));
                out.add(e);
            }
            if (out.size() >= max) break;
        }
        return out;
    }

    Feed.Entry replacement(List<Feed.Entry> candidates, List<Feed.Entry> showing) {
        Set<String> occupied = new HashSet<>();
        for (Feed.Entry e : showing) {
            occupied.add(fallback(e));
            if (!e.tmdb.isEmpty()) occupied.add("tmdb:" + e.tmdb);
        }
        for (Feed.Entry e : candidates) if (!contains(e) && !occupied.contains(fallback(e))
                && (e.tmdb.isEmpty() || !occupied.contains("tmdb:" + e.tmdb))) return e;
        return null;
    }
}
