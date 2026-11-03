package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class FeedSearchTest {
    private static Feed.Entry entry(String title, String pkg) {
        return new Feed.Entry("search", pkg, 0, title, "", "", -1, "", "", pkg.isEmpty() ? "" : "intent", pkg, "", "movie", "", "");
    }

    @Test
    public void servicesKeepToInstalledAppsDrawerFirst() {
        assertEquals(Arrays.asList("b", "a", "c"), Feed.rankServices("a,c,b,gone",
                Collections.singletonList("b"), new HashSet<>(Arrays.asList("a", "b", "c"))));
    }

    @Test
    public void noServicesLeavesNothing() {
        assertEquals(Collections.emptyList(), Feed.rankServices("", Collections.singletonList("b"), new HashSet<>(Arrays.asList("b"))));
    }

    @Test
    public void streamableTitlesComeFirstInTheirOwnOrder() {
        List<Feed.Entry> found = new ArrayList<>(Arrays.asList(entry("Nowhere", ""), entry("One", "a"), entry("Two", "b")));
        List<String> titles = new ArrayList<>();
        for (Feed.Entry e : Feed.streamableFirst(found)) titles.add(e.title);
        assertEquals(Arrays.asList("One", "Two", "Nowhere"), titles);
    }

    @Test
    public void anotherAppKeepsTheTitleAndOpensThere() {
        Feed.Entry e = entry("One", "a");
        e.tmdb = "movie:1";
        Feed.Entry other = Feed.on(e, "b");
        assertEquals("b", other.pkg);
        assertEquals("movie:1", other.tmdb);
        assertEquals(true, other.intent.contains("LEANBACK_LAUNCHER") && other.intent.contains("package=b;"));
    }
}
