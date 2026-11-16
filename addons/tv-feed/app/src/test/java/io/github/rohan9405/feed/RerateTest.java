package io.github.rohan9405.feed;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RerateTest {
    private static Item item(String title, String year, String tmdb, String rating) {
        Item i = new Item();
        i.title = title;
        i.year = year;
        i.tmdb = tmdb;
        i.rating = rating;
        return i;
    }

    @Test
    public void everyTileOfTheTitleTakesTheNewRating() {
        List<Item> items = new ArrayList<>(Arrays.asList(item("Stree", "2018", "movie:1", "7.5"),
                item("Stree", "2018", "", "7.5"), item("Stree", "1961", "movie:2", "7.5"), item("Other", "2018", "", "6.0")));
        assertEquals(2, Store.rerate(items, "movie:1", "Stree", "2018", "7.6"));
        assertEquals("7.6", items.get(0).rating);
        assertEquals("7.6", items.get(1).rating);
        assertEquals("7.5", items.get(2).rating);
        assertEquals("6.0", items.get(3).rating);
    }

    @Test
    public void noRatingChangesNothing() {
        List<Item> items = new ArrayList<>(Arrays.asList(item("Stree", "2018", "movie:1", "7.5")));
        assertEquals(0, Store.rerate(items, "movie:1", "Stree", "2018", ""));
        assertEquals(0, Store.rerate(items, "movie:1", "Stree", "2018", "7.5"));
    }
}
