package io.github.rohan9405.feed;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class LanguagesAndArtTest {
    private static Item item(String section, String tab, String language) {
        Item i = new Item();
        i.section = section;
        i.tab = tab;
        i.language = language;
        return i;
    }

    @Test
    public void onlyTheHeadersNewTitlesAreFiltered() {
        List<Item> items = Arrays.asList(item(Item.HERO, "in.startv.hotstar", "hi"), item(Item.HERO, "in.startv.hotstar", "ko"),
                item(Item.HERO, "in.startv.hotstar", ""), item(Item.APP, "in.startv.hotstar", "ko"),
                item(Item.HERO, Refresher.YOUTUBE, ""));
        List<Item> kept = Languages.filter(items, new HashSet<>(Arrays.asList("hi", "en")));
        assertEquals(3, kept.size());
        assertEquals("hi", kept.get(0).language);
        assertEquals(Item.APP, kept.get(1).section);
    }

    @Test
    public void noChoiceKeepsEverything() {
        List<Item> items = Arrays.asList(item(Item.HERO, "x", "ko"));
        assertEquals(1, Languages.filter(items, Collections.emptySet()).size());
    }

    @Test
    public void backdropWithoutTextIsPreferred() throws Exception {
        assertEquals(Tmdb.IMAGES + "w1280/clean.jpg", Tmdb.pickBackdrop(new JSONObject(
                "{backdrops: [{iso_639_1: 'en', file_path: '/text.jpg'}, {iso_639_1: null, file_path: '/clean.jpg'}]}")));
        assertEquals("", Tmdb.pickBackdrop(new JSONObject("{backdrops: [{iso_639_1: 'en', file_path: '/text.jpg'}]}")));
    }
}
