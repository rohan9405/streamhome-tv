package io.github.rohan9405.feed;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of Home's top bar shortcuts are taken off it. Home takes one off when the owner holds OK on it; the Feed screen's
 * Top bar puts it back. Kept here, where both reach it, and sent to Home with the insights.
 */
final class TopBar {
    /** {Home's name for the shortcut, what the Feed screen calls it, what it does}. Settings always stays. */
    static final String[][] NAMES = {
            {"btnSearch", "Search", "Search films and shows"},
            {"btnWatchlist", "Watchlist", "Your saved films and series"},
            {"btnInputs", "Inputs", "HDMI and other sources"},
            {"btnWifi", "Wi-Fi", "Network settings"},
            {"btnBluetooth", "Bluetooth", "Remotes and accessories"}};

    private TopBar() {
    }

    static List<String> hidden(Context ctx) {
        List<String> out = new ArrayList<>(prefs(ctx));
        Collections.sort(out);
        return out;
    }

    static boolean shown(Context ctx, String name) {
        return !prefs(ctx).contains(name);
    }

    /** Puts a shortcut on the bar or takes it off; Home hears it at once. Unknown names and Settings are ignored. */
    static void set(Context ctx, String name, boolean shown) {
        boolean known = false;
        for (String[] n : NAMES) known |= n[0].equals(name);
        if (!known) return;
        Set<String> now = new HashSet<>(prefs(ctx));
        if (shown) now.remove(name);
        else now.add(name);
        ctx.getSharedPreferences("topbar", Context.MODE_PRIVATE).edit().putStringSet("hidden", now).apply();
        ctx.getContentResolver().notifyChange(FeedProvider.insightsUri(), null);
    }

    private static Set<String> prefs(Context ctx) {
        return ctx.getSharedPreferences("topbar", Context.MODE_PRIVATE).getStringSet("hidden", Collections.emptySet());
    }
}
