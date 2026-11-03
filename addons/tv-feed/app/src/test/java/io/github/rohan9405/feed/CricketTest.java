package io.github.rohan9405.feed;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

public class CricketTest {
    private static final long NOW = 1_800_000_000_000L, HOUR = 3_600_000L;

    private static JSONObject json(String s) {
        try {
            return new JSONObject(s);
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }
    }

    private static JSONObject match(String state, long at, String a, String b) {
        return json("{state: '" + state + "', at: " + at + ", a_name: '" + a + "', b_name: '" + b + "'}");
    }

    private static JSONArray parse(String event) {
        try {
            return Cricket.parse(json("{sports: [{leagues: [{name: 'Tour', events: [" + event + "]}]}]}"));
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void readsAMatchFromTheBoard() {
        JSONObject m = parse("{date: '2026-11-04T02:00:00Z', status: 'in', summary: 'Stumps', fullStatus: {summary: 'India lead'},"
                + " competitors: [{displayName: 'India', abbreviation: 'IND', score: '187/4 (16.2 ov)'}, {displayName: 'Australia', abbreviation: 'AUS', score: ''}]}").optJSONObject(0);
        assertEquals("live", m.optString("state"));
        assertEquals("IND", m.optString("a_abbr"));
        assertEquals("187/4 (16.2 ov)", m.optString("a_score"));
        assertEquals("India lead", m.optString("summary"));
    }

    @Test
    public void inProgressWithNoScoreHasNotBegun() {
        assertEquals("next", parse("{date: '2026-11-04T02:00:00Z', status: 'in', competitors: [{displayName: 'A', score: ''}, {displayName: 'B', score: ''}]}")
                .optJSONObject(0).optString("state"));
    }

    @Test
    public void aBadDateOrMissingSideIsSkipped() {
        assertEquals(0, parse("{date: 'soon', status: 'pre', competitors: [{displayName: 'A'}, {displayName: 'B'}]}").length());
        assertEquals(0, parse("{date: '2026-11-04T02:00:00Z', status: 'pre', competitors: [{displayName: 'A'}]}").length());
    }

    @Test
    public void teamNamesMatchWhole() {
        JSONArray all = new JSONArray().put(match("next", NOW, "Mumbai Indians", "India A")).put(match("next", NOW, "India Women", "Kenya"));
        assertEquals(0, Cricket.select(all, Collections.singleton("India"), NOW).length());
        assertEquals(1, Cricket.select(new JSONArray().put(match("next", NOW, "New Zealand", "India")), Collections.singleton("India"), NOW).length());
    }

    @Test
    public void leagueTagAndOldCitySpellingStillMatch() {
        assertEquals("delhi capitals", Cricket.normal("Delhi Capitals (IPL)"));
        assertEquals(Cricket.normal("Royal Challengers Bengaluru"), Cricket.normal("Royal Challengers Bangalore"));
    }

    @Test
    public void liveThenSoonestThenLatestResultAtMostThree() {
        JSONArray all = new JSONArray()
                .put(match("result", NOW - 2 * HOUR, "India", "X1"))
                .put(match("next", NOW + 9 * HOUR, "India", "X2"))
                .put(match("next", NOW + 1 * HOUR, "India", "X3"))
                .put(match("live", NOW - HOUR, "India", "X4"));
        JSONArray picked = Cricket.select(all, Collections.singleton("India"), NOW);
        assertEquals(3, picked.length());
        assertEquals("X4", picked.optJSONObject(0).optString("b_name"));
        assertEquals("X3", picked.optJSONObject(1).optString("b_name"));
        assertEquals("X2", picked.optJSONObject(2).optString("b_name"));
    }

    @Test
    public void oldResultsDropOut() {
        assertEquals(0, Cricket.select(new JSONArray().put(match("result", NOW - 72 * HOUR, "India", "X")), Collections.singleton("India"), NOW).length());
    }
}
