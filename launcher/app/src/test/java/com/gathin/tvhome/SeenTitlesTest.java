package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class SeenTitlesTest {
    private Feed.Entry e(String pkg, String title, String year, String tmdb) {
        Feed.Entry e = new Feed.Entry(Feed.APP, pkg, 0, title, "", "", 0, "", "", "", pkg, "", "movie", year, "Drama");
        e.tmdb = tmdb;
        return e;
    }
    @Test public void survivesRestartAcrossServicesAndKeepsRemakesSeparate() {
        SeenTitles first = new SeenTitles(Collections.emptySet());
        first.mark(e("netflix", "Dune", "2021", "movie:438631"));
        SeenTitles restarted = new SeenTitles(first.saved());
        assertTrue(restarted.contains(e("prime", "Dune", "2021", "movie:438631")));
        assertTrue(restarted.contains(e("prime", " DUNE ", "2021", "")));
        assertFalse(restarted.contains(e("prime", "Dune", "1984", "movie:841")));
        assertFalse(new SeenTitles(Collections.emptySet()).contains(e("netflix", "Dune", "2021", "movie:438631")));
    }
    @Test public void replacementSkipsSeenAndAlreadyVisiblePicksAndReportsExhaustion() {
        SeenTitles seen = new SeenTitles(Collections.emptySet());
        Feed.Entry a = e("n", "A", "2026", "movie:1"), b = e("n", "B", "2026", "movie:2"),
                c = e("n", "C", "2026", "movie:3"), copy = e("p", "B", "2026", "movie:2");
        seen.mark(a);
        assertSame(c, seen.replacement(Arrays.asList(a, copy, c), Arrays.asList(a, b)));
        assertNull(seen.replacement(Arrays.asList(a, copy), Arrays.asList(a, b)));
        assertEquals(Arrays.asList(b, c), seen.available(Arrays.asList(a, b, copy, e("p", "B", "2026", ""), c), 10));
        assertTrue(seen.available(Arrays.asList(b, c), 0).isEmpty());
    }
    @Test public void hidingDoesNotModifyTheSavedSetOrOtherProfiles() {
        java.util.Set<String> saved = new java.util.HashSet<>();
        SeenTitles owner = new SeenTitles(saved), guest = new SeenTitles(saved);
        Feed.Entry title = e("n", "A", "2026", "movie:1");
        owner.mark(title);
        assertTrue(saved.isEmpty());
        assertFalse(guest.contains(title));
        owner.unmark(title);
        assertFalse(owner.contains(title));
    }
}
