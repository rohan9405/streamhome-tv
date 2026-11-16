package io.github.rohan9405.feed;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class JustWatchScheduleTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static long at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, IST).toInstant().toEpochMilli();
    }

    @Test
    public void twiceADayAtNineAndTwentyOne() {
        assertEquals(at(16, 21, 0), JustWatch.slotStart(ZonedDateTime.of(2026, 11, 17, 8, 59, 0, 0, IST)));
        assertEquals(at(17, 9, 0), JustWatch.slotStart(ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, IST)));
        assertEquals(at(17, 9, 0), JustWatch.slotStart(ZonedDateTime.of(2026, 11, 17, 20, 59, 0, 0, IST)));
        assertEquals(at(17, 21, 0), JustWatch.slotStart(ZonedDateTime.of(2026, 11, 17, 23, 30, 0, 0, IST)));
    }

    private static Tmdb.Title title(String kind, long id, String name) {
        Tmdb.Title t = new Tmdb.Title();
        t.kind = kind;
        t.id = id;
        t.name = name;
        return t;
    }

    @Test
    public void tmdbAddsOnlyWhatJustWatchLacks() {
        List<Tmdb.Title> merged = Refresher.merge(Arrays.asList(title("tv", 1, "Tulsa King"), title("movie", 0, "Apsara")),
                Arrays.asList(title("tv", 1, "Tulsa King (2022)"), title("movie", 5, "apsara"), title("movie", 9, "New film")));
        assertEquals(3, merged.size());
        assertEquals("New film", merged.get(2).name);
    }

    @Test
    public void eitherSourceAloneIsEnough() {
        assertEquals(1, Refresher.merge(null, Arrays.asList(title("movie", 9, "New film"))).size());
        assertEquals(1, Refresher.merge(Arrays.asList(title("movie", 9, "New film")), java.util.Collections.emptyList()).size());
    }
}
