package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class InsightRotationTest {
    private JSONObject data() throws Exception {
        return new JSONObject("{portfolio:{change:15,pct:1,classes:[{key:'mf',change:10},{key:'in',change:3},{key:'us',change:2}]},"
                + "markets:[{name:'NIFTY 50',price:24000},{name:'SENSEX',price:80000}]}");
    }
    @Test public void rotatesAllAvailableClassesBeforeTheMarkets() throws Exception {
        List<JSONObject> views = InsightRotation.views(data());
        assertEquals(6, views.size());
        String[] names = {"Portfolio", "Mutual Funds", "Equity", "US Stocks", "NIFTY", "SENSEX"};
        for (int i = 0; i < names.length; i++) assertEquals(names[i], views.get(i).getString("chip_name"));
    }
    @Test public void totalAndClassesHaveIndependentVisibilityWithoutChangingTotals() throws Exception {
        JSONObject input = data().put("views", new JSONObject("{portfolio:false,mf:true,in:false,us:false,nifty:false,sensex:true}"));
        List<JSONObject> views = InsightRotation.views(input);
        assertEquals(2, views.size());
        assertEquals("Mutual Funds", views.get(0).getString("chip_name"));
        assertEquals("SENSEX", views.get(1).getString("chip_name"));
        assertEquals(15, input.getJSONObject("portfolio").getDouble("change"), 0);
        assertFalse(input.getJSONObject("portfolio").has("chip_name"));
    }
    @Test public void allOffOrUnavailableGivesNoFakeZeroViews() throws Exception {
        JSONObject input = data().put("views", new JSONObject("{portfolio:false,mf:false,in:false,us:false,nifty:false,sensex:false}"));
        assertTrue(InsightRotation.views(input).isEmpty());
        assertFalse(InsightRotation.visible(input.put("status", "reconnect")));
        assertTrue(InsightRotation.visible(new JSONObject("{status:reconnect}")));
        assertFalse(InsightRotation.visible(data().put("chip", false)));
        assertTrue(InsightRotation.views(new JSONObject()).isEmpty());
        assertEquals(2, InsightRotation.views(data().put("portfolio", JSONObject.NULL)).size());
    }

    @org.junit.Test
    public void matchesComeTogetherThenFinanceByDefault() throws Exception {
        org.json.JSONObject data = new org.json.JSONObject("{markets: [{name: 'NIFTY 50', price: 1}, {name: 'SENSEX', price: 2}],"
                + " cricket: [{a_abbr: 'IND', state: 'live'}, {a_abbr: 'MI', state: 'next'}]}");
        java.util.List<org.json.JSONObject> views = InsightRotation.views(data);
        org.junit.Assert.assertEquals(4, views.size());
        org.junit.Assert.assertTrue(InsightRotation.isCricket(views.get(0)));
        org.junit.Assert.assertTrue(InsightRotation.isCricket(views.get(1)));
        org.junit.Assert.assertFalse(InsightRotation.isCricket(views.get(2)));
    }

    @org.junit.Test
    public void withTogetherOffTheyTakeTurns() throws Exception {
        org.json.JSONObject data = new org.json.JSONObject("{cricket_together: false, markets: [{name: 'NIFTY 50', price: 1}, {name: 'SENSEX', price: 2}],"
                + " cricket: [{a_abbr: 'IND', state: 'live'}]}");
        java.util.List<org.json.JSONObject> views = InsightRotation.views(data);
        org.junit.Assert.assertFalse(InsightRotation.isCricket(views.get(0)));
        org.junit.Assert.assertTrue(InsightRotation.isCricket(views.get(1)));
        org.junit.Assert.assertFalse(InsightRotation.isCricket(views.get(2)));
    }

    @org.junit.Test
    public void cricketShowsWithTheFinanceChipOff() throws Exception {
        org.json.JSONObject data = new org.json.JSONObject("{chip: false, markets: [{name: 'SENSEX', price: 2}], cricket: [{a_abbr: 'IND', state: 'next'}]}");
        org.junit.Assert.assertTrue(InsightRotation.visible(data));
        org.junit.Assert.assertEquals(1, InsightRotation.views(data).size());
    }

    @org.junit.Test
    public void cricketValueReadsAsAScoreOrResult() throws Exception {
        org.junit.Assert.assertEquals("IND 187/4 (16.2 ov) v AUS", InsightRotation.cricketValue(new org.json.JSONObject(
                "{state: 'live', a_abbr: 'IND', a_score: '187/4 (16.2 ov)', b_abbr: 'AUS', b_score: ''}")));
        org.junit.Assert.assertEquals("India won by 6 wkts", InsightRotation.cricketValue(new org.json.JSONObject(
                "{state: 'result', a_abbr: 'IND', b_abbr: 'ENG', summary: 'India won by 6 wkts'}")));
    }
}
