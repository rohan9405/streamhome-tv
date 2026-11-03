package io.github.rohan9405.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SearchAndLogoTest {
    private static JSONObject json(String s) {
        try {
            return new JSONObject(s);
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void logoPrefersEnglishThenHindiThenNoLanguage() {
        assertEquals(Tmdb.IMAGES + "w500/en.png", Tmdb.pickLogo(json("{logos: [{iso_639_1: 'hi', file_path: '/hi.png'},"
                + " {iso_639_1: 'en', file_path: '/en.png'}]}")));
        assertEquals(Tmdb.IMAGES + "w500/hi.png", Tmdb.pickLogo(json("{logos: [{iso_639_1: null, file_path: '/none.png'},"
                + " {iso_639_1: 'hi', file_path: '/hi.png'}]}")));
        assertEquals(Tmdb.IMAGES + "w500/none.png", Tmdb.pickLogo(json("{logos: [{iso_639_1: null, file_path: '/none.png'}]}")));
    }

    @Test
    public void logoSkipsSvgsAndOtherLanguages() {
        assertEquals("", Tmdb.pickLogo(json("{logos: [{iso_639_1: 'en', file_path: '/a.svg'},"
                + " {iso_639_1: 'ta', file_path: '/ta.png'}]}")));
        assertEquals("", Tmdb.pickLogo(null));
    }

    @Test
    public void servicesAreIndiasIncludedOnesOnly() {
        JSONObject answer = json("{results: {US: {flatrate: [{provider_id: 350}]}, IN: {"
                + " rent: [{provider_id: 119}], flatrate: [{provider_id: 8}, {provider_id: 99999}], ads: [{provider_id: 232}]}}}");
        assertEquals(Arrays.asList("com.netflix.ninja", "com.graymatrix.did"), Tmdb.parseServices(answer));
    }

    @Test
    public void noServicesWhenNotOnOfferHere() {
        assertEquals(Collections.emptyList(), Tmdb.parseServices(json("{results: {US: {flatrate: [{provider_id: 8}]}}}")));
        assertEquals(Collections.emptyList(), Tmdb.parseServices(null));
    }

    @Test
    public void darkLogosAreLeftOut() {
        assertTrue(Images.tooDark(new int[] {0xFF000000, 0xFF101010}));
        assertTrue(Images.tooDark(new int[] {0x00FFFFFF}));   // nothing visible
        assertFalse(Images.tooDark(new int[] {0xFFFFFFFF, 0x00000000}));   // white on transparent
    }
}
