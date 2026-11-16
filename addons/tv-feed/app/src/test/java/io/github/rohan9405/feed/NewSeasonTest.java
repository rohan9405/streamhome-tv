package io.github.rohan9405.feed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class NewSeasonTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static Tmdb.Title title(boolean season, String released) {
        Tmdb.Title t = new Tmdb.Title();
        t.listedSeason = season;
        t.released = released;
        return t;
    }

    @Test
    public void aRunningSeasonsNewEpisodeIsNotNew() {
        // Law & Order: SVU's season premiered on 8 October; JustWatch lists it again for each new episode.
        assertFalse(JustWatch.isNew(title(true, "2026-10-08"), TODAY, 14));
    }

    @Test
    public void aSeasonThatJustPremieredIsNew() {
        assertTrue(JustWatch.isNew(title(true, "2026-11-08"), TODAY, 14));
    }

    @Test
    public void filmsAndWholeSeriesJustOnTheServiceStillCount() {
        assertTrue(JustWatch.isNew(title(false, "2026-06-01"), TODAY, 14));
        assertFalse(JustWatch.isNew(title(false, "2024-06-01"), TODAY, 14));
    }

    @Test
    public void aSeasonFromJustWatchIsMarked() throws Exception {
        Tmdb.Title t = JustWatch.title(new org.json.JSONObject("{node: {objectType: 'SHOW_SEASON', content: {title: 'Season 26',"
                + " originalReleaseDate: '2026-10-08'}, show: {content: {title: 'Law & Order', externalIds: {tmdbId: '2734'}}}}}"));
        assertTrue(t.listedSeason);
        assertFalse(JustWatch.isNew(t, TODAY, 14));
    }
}
