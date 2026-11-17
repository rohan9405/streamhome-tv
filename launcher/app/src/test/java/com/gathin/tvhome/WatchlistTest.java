package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.List;

public class WatchlistTest {
    private static Feed.Entry entry(String title, String kind, String tmdb) {
        Feed.Entry e = new Feed.Entry("hero", "com.netflix.ninja", 0, title, "", "7.1", -1, "", "", "intent", "com.netflix.ninja", "", kind, "2026", "");
        e.tmdb = tmdb;
        return e;
    }

    @Test
    public void latestAddedFirstAndSplitByKind() {
        JSONArray items = new JSONArray();
        items = Watchlist.put(items, Watchlist.toJson(entry("Old film", "movie", "movie:1"), "", 100));
        items = Watchlist.put(items, Watchlist.toJson(entry("A series", "", "tv:2"), "", 200));
        items = Watchlist.put(items, Watchlist.toJson(entry("New film", "movie", "movie:3"), "", 300));
        List<Feed.Entry> films = Watchlist.pick(items, false);
        assertEquals(2, films.size());
        assertEquals("New film", films.get(0).title);
        assertEquals("Old film", films.get(1).title);
        assertEquals("A series", Watchlist.pick(items, true).get(0).title);
    }

    @Test
    public void addingAgainKeepsOneCopyAsTheLatest() {
        JSONArray items = new JSONArray();
        items = Watchlist.put(items, Watchlist.toJson(entry("Film", "movie", "movie:1"), "", 100));
        items = Watchlist.put(items, Watchlist.toJson(entry("Other", "movie", "movie:2"), "", 200));
        items = Watchlist.put(items, Watchlist.toJson(entry("Film", "movie", "movie:1"), "", 300));
        assertEquals(2, items.length());
        assertEquals("Film", Watchlist.pick(items, false).get(0).title);
    }

    @Test
    public void removalAndRoundTrip() {
        Feed.Entry e = entry("Film", "movie", "movie:1");
        e.artSource = "https://image.tmdb.org/t/p/w780/a.jpg";
        JSONArray items = Watchlist.put(new JSONArray(), Watchlist.toJson(e, "", 1));
        Feed.Entry back = Watchlist.pick(items, false).get(0);
        assertEquals("movie:1", back.tmdb);
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", back.artSource);
        assertEquals(0, Watchlist.without(items, "movie:1").length());
    }

    @Test
    public void titlesWithoutTmdbAreKnownByAppAndName() {
        Feed.Entry e = entry("Kota Factory", "", "");
        assertEquals("com.netflix.ninja|kota factory", Watchlist.key(e));
        assertFalse(Watchlist.series(Watchlist.toJson(e, "", 1)));
        assertTrue(Watchlist.series(Watchlist.toJson(entry("X", "tv", ""), "", 1)));
    }

    @Test
    public void ratingCountsReadShort() {
        assertEquals("", Feed.votes(""));
        assertEquals("999", Feed.votes("999"));
        assertEquals("1k", Feed.votes("1000"));
        assertEquals("1.2k", Feed.votes("1234"));
        assertEquals("9.9k", Feed.votes("9999"));
        assertEquals("34k", Feed.votes("34567"));
        assertEquals("1.2M", Feed.votes("1234567"));
    }

    @org.junit.Test
    public void pictureNamesAreSafeAndStable() {
        String a = Watchlist.pictureName("tv:12345"), b = Watchlist.pictureName("com.netflix.ninja|the crown");
        org.junit.Assert.assertTrue(a.matches("[0-9a-f]{20}\\.jpg"));
        org.junit.Assert.assertTrue(b.matches("[0-9a-f]{20}\\.jpg"));
        org.junit.Assert.assertEquals(a, Watchlist.pictureName("tv:12345"));
        org.junit.Assert.assertNotEquals(a, b);
    }

    @org.junit.Test
    public void theOldestPicturesGoFirstPastTheCap() throws java.io.IOException {
        java.io.File dir = java.nio.file.Files.createTempDirectory("wl").toFile();
        java.util.Map<String, java.io.File> files = new java.util.HashMap<>();
        for (String n : new String[] {"new.jpg", "mid.jpg", "old.jpg"}) {
            java.io.File f = new java.io.File(dir, n);
            java.nio.file.Files.write(f.toPath(), new byte[40]);
            files.put(n, f);
        }
        // "gone.jpg" has no file: it takes no room.
        assertEquals(java.util.Arrays.asList("old.jpg"),
                Watchlist.keepWithin(java.util.Arrays.asList("new.jpg", "gone.jpg", "mid.jpg", "old.jpg"), files, 100));
        assertEquals(java.util.Collections.emptyList(),
                Watchlist.keepWithin(java.util.Arrays.asList("new.jpg", "mid.jpg", "old.jpg"), files, 120));
    }
}
