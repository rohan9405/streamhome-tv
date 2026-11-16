package io.github.rohan9405.feed;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The header's languages, chosen on the Feed screen: with any chosen, the header's new titles are only those whose
 * original language is one of them (TMDB says which; a title it cannot place is left out). None chosen: every language.
 * The other rows are not filtered.
 */
final class Languages {
    /** {TMDB's code, name}. */
    static final String[][] NAMES = {{"en", "English"}, {"hi", "Hindi"}, {"gu", "Gujarati"}, {"ta", "Tamil"},
            {"te", "Telugu"}, {"ml", "Malayalam"}};

    private Languages() {
    }

    static Set<String> chosen(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences("languages", Context.MODE_PRIVATE).getStringSet("chosen", Collections.emptySet()));
    }

    /** One more or one fewer; the header is redrawn with a refresh, which asks TMDB only for what it does not know. */
    static void toggle(Context ctx, String code) {
        Set<String> now = chosen(ctx);
        if (!now.remove(code)) now.add(code);
        ctx.getSharedPreferences("languages", Context.MODE_PRIVATE).edit().putStringSet("chosen", now).apply();
        Refresher.kick(ctx, null);
    }

    /** The items, with the header's new titles kept only in the chosen languages; looks up the ones not known yet. */
    static List<Item> keep(Context ctx, List<Item> items, Tmdb tmdb) {
        Set<String> chosen = chosen(ctx);
        if (chosen.isEmpty()) return items;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> jobs = new ArrayList<>();
        for (Item i : items) {
            if (Item.HERO.equals(i.section) && i.language.isEmpty() && !i.tmdb.isEmpty()) {
                jobs.add(pool.submit(() -> i.language = tmdb.language(i.tmdb)));
            }
        }
        for (Future<?> job : jobs) {
            try {
                job.get();
            } catch (Exception e) {
                // Not known: left out below.
            }
        }
        pool.shutdown();
        List<Item> kept = filter(items, chosen);
        java.util.Map<String, Integer> left = new java.util.LinkedHashMap<>();
        for (Item i : kept) if (Item.HERO.equals(i.section)) left.merge(i.tab, 1, Integer::sum);
        android.util.Log.i("Feed", "header languages " + chosen + ": " + left);
        return kept;
    }

    /** YouTube's videos are not titles with a language: they stay. */
    static List<Item> filter(List<Item> items, Set<String> chosen) {
        if (chosen.isEmpty()) return items;
        List<Item> out = new ArrayList<>();
        for (Item i : items) {
            boolean header = Item.HERO.equals(i.section) && !Refresher.YOUTUBE.equals(i.tab);
            if (!header || chosen.contains(i.language)) out.add(i);
        }
        return out;
    }
}
