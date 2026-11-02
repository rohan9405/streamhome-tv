package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Available finance views in rotation order; visibility never changes how the total is calculated. */
final class InsightRotation {
    static boolean visible(JSONObject data) {
        if (!data.optBoolean("chip", true)) return false;
        JSONObject enabled = data.optJSONObject("views");
        boolean anyEnabled = enabled == null;
        for (String key : new String[] {"portfolio", "mf", "in", "us", "nifty", "sensex"})
            anyEnabled |= enabled != null && enabled.optBoolean(key, true);
        return anyEnabled && (!views(data).isEmpty() || "reconnect".equals(data.optString("status")));
    }

    /** Enabled, available views only; absent asset classes never appear as zero. */
    static java.util.List<JSONObject> views(JSONObject data) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        JSONObject enabled = data.optJSONObject("views");
        JSONObject p = data.optJSONObject("portfolio");
        try {
            if (p != null) {
                if (enabled == null || enabled.optBoolean("portfolio", true))
                    out.add(new JSONObject(p.toString()).put("chip_name", "Portfolio"));
                JSONArray classes = p.optJSONArray("classes");
                for (int i = 0; classes != null && i < classes.length(); i++) {
                    JSONObject c = classes.optJSONObject(i);
                    if (c == null) continue;
                    String key = c.optString("key");
                    if (enabled == null || enabled.optBoolean(key, true))
                        out.add(new JSONObject(c.toString()).put("chip_name", className(key, c.optString("name"))));
                }
            }
            JSONArray markets = data.optJSONArray("markets");
            for (int i = 0; markets != null && i < markets.length(); i++) {
                JSONObject m = markets.optJSONObject(i);
                if (m == null) continue;
                String name = m.optString("name").toUpperCase(java.util.Locale.ROOT).replace("NIFTY 50", "NIFTY");
                String key = name.startsWith("NIFTY") ? "nifty" : "sensex";
                if (enabled == null || enabled.optBoolean(key, true))
                    out.add(new JSONObject(m.toString()).put("chip_name", name));
            }
        } catch (JSONException ignored) {
            // Keep the valid views already assembled.
        }
        return out;
    }

    private static String className(String key, String fallback) {
        switch (key) {
            case "mf": return "Mutual Funds";
            case "in": return "Equity";
            case "us": return "US Stocks";
            default: return fallback;
        }
    }
}
