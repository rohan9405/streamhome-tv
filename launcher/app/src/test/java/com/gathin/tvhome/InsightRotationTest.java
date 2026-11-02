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
}
