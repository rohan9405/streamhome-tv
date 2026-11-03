package io.github.rohan9405.feed;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * What Home reads: {@code content://io.github.rohan9405.feed/items}, one row per tile, and
 * {@code .../image/<name>} for the cached art. Exported, but it answers only apps signed with this app's own
 * key (the launcher built on the same computer), because the rows show what you watch.
 *
 * <p>A query also starts a refresh when the last one is older than {@link Refresher#STALE_MS}; the launcher hears
 * about the new rows through notifyChange.
 */
public final class FeedProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.rohan9405.feed";
    static final String[] COLUMNS = {"row", "row_order", "title", "subtitle", "rating", "progress",
            "poster", "backdrop", "intent", "package", "hero", "section", "tab", "overview", "kind", "year", "genre", "plain_art", "engaged", "released", "tmdb", "logo"};

    static Uri itemsUri() {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).appendPath("items").build();
    }

    /** The running refresh's progress (-1 when none runs) and when the last one finished, for Home's top bar. */
    static Uri statusUri() {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).appendPath("status").build();
    }

    /** Read more: one title's cast, crew and facts from TMDB, asked for when Home opens the popup. */
    static final String[] DETAILS = {"overview", "tagline", "released", "runtime", "episode_runtime", "seasons", "episodes",
            "certification", "cast", "directors", "creators", "language", "status", "imdb_rating"};

    /** A row of {@link #DETAILS} for "movie:123" or "tv:456", fetched now (kept a week); null when TMDB has nothing. */
    private Cursor details(String tmdb, String title, String year) {
        Cursor missing = tmdbMissing();
        if (missing != null) return missing;
        Tmdb.Details d = tmdb == null ? null : new Tmdb(getContext()).details(tmdb);
        if (d == null) return null;
        MatrixCursor c = new MatrixCursor(DETAILS);
        c.addRow(new Object[] {d.overview, d.tagline, d.released, d.runtime, d.episodeRuntime, d.seasons, d.episodes,
                d.certification, d.cast, d.directors, d.creators, d.language, d.status, rating(tmdb, title, year)});
        return c;
    }

    /** Home says what is missing instead of an empty popup or an empty search; null when TMDB can be asked. */
    private Cursor tmdbMissing() {
        if (Tmdb.hasToken(getContext()) && !Tmdb.refused(getContext())) return null;
        MatrixCursor c = new MatrixCursor(new String[] {"error"});
        c.addRow(new Object[] {Tmdb.hasToken(getContext()) ? "tmdb_refused" : "tmdb_missing"});
        return c;
    }

    /**
     * IMDb's rating for a title Home has none for (one from search): OMDb, then IMDb itself for a new title OMDb has not
     * rated. Only asked when Home sends the title, so a title that already has its rating spends nothing.
     */
    private String rating(String tmdb, String title, String year) {
        if (title == null || title.isEmpty()) return "";
        Omdb.Entry e = new Omdb(getContext(), 1).lookup(title, year == null ? "" : year);
        if (e != null && !e.rating.isEmpty()) return e.rating;
        String id = e != null && !e.imdbId.isEmpty() ? e.imdbId : new Tmdb(getContext()).imdbId(tmdb);
        if (id.isEmpty()) return "";
        String r = new Imdb(getContext()).ratings(java.util.Collections.singletonList(id)).get(id);
        return r == null ? "" : r;
    }

    /** Search: one row per title, or an "error" row like Read more's; null when TMDB did not answer. */
    private Cursor search(String words) {
        Cursor missing = tmdbMissing();
        if (missing != null) return missing;
        List<Tmdb.Title> found = words == null || words.trim().isEmpty() ? null : new Tmdb(getContext()).search(words.trim());
        if (found == null) return null;
        MatrixCursor c = new MatrixCursor(new String[] {"title", "overview", "kind", "year", "genre", "tmdb", "art", "services"});
        for (Tmdb.Title t : found) {
            c.addRow(new Object[] {t.name, t.overview, t.kind, t.year, t.genres, t.kind + ":" + t.id, t.backdrop,
                    String.join(",", t.services)});
        }
        return c;
    }

    /** A search card's picture: fetched when the card appears, and only from TMDB's image host. */
    private Cursor art(String source) {
        if (source == null || !source.startsWith(Tmdb.IMAGES)) return null;
        String name = Images.cache(getContext(), source, false);
        MatrixCursor c = new MatrixCursor(new String[] {"image"});
        c.addRow(new Object[] {name.isEmpty() ? "" : imageUri(name).toString()});
        return c;
    }

    /** Home's insight card: the INDmoney portfolio's day and the indices, as one JSON row. */
    static Uri insightsUri() {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).appendPath("insights").build();
    }

    private static final java.util.concurrent.atomic.AtomicReference<String> phoneWords = new java.util.concurrent.atomic.AtomicReference<>("");

    static void setPhoneWords(String words) {
        phoneWords.set(words);
    }

    /** The Feed screen's setting: the header's titles as their logos, or written as text. */
    static boolean logosOn(android.content.Context ctx) {
        return ctx.getSharedPreferences("header", android.content.Context.MODE_PRIVATE).getBoolean("logos", true);
    }

    static void setLogos(android.content.Context ctx, boolean on) {
        ctx.getSharedPreferences("header", android.content.Context.MODE_PRIVATE).edit().putBoolean("logos", on).apply();
        ctx.getContentResolver().notifyChange(itemsUri(), null);
    }

    static Uri imageUri(String name) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY)
                .appendPath("image").appendPath(name).build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!allowed()) return null;
        if ("status".equals(uri.getLastPathSegment())) {
            MatrixCursor c = new MatrixCursor(new String[] {"progress", "refreshed"});
            c.addRow(new Object[] {Refresher.progress(), Store.get(getContext()).refreshedMs});
            c.setNotificationUri(getContext().getContentResolver(), statusUri());
            return c;
        }
        if ("details".equals(uri.getLastPathSegment())) {
            return details(uri.getQueryParameter("tmdb"), uri.getQueryParameter("title"), uri.getQueryParameter("year"));
        }
        if ("search".equals(uri.getLastPathSegment())) return search(uri.getQueryParameter("q"));
        if ("phone_words".equals(uri.getLastPathSegment())) {
            // Taken once: the words a phone sent to Home's search.
            MatrixCursor c = new MatrixCursor(new String[] {"words"});
            c.addRow(new Object[] {phoneWords.getAndSet("")});
            return c;
        }
        if ("art".equals(uri.getLastPathSegment())) return art(uri.getQueryParameter("source"));
        if ("link".equals(uri.getLastPathSegment())) {
            // A title's own page in its app, looked up when Home opens it (JustWatch), for a title the app did not publish.
            MatrixCursor c = new MatrixCursor(new String[] {"intent"});
            c.addRow(new Object[] {new JustWatch(getContext()).link(uri.getQueryParameter("pkg"), uri.getQueryParameter("tmdb"),
                    uri.getQueryParameter("title"))});
            return c;
        }
        if ("insights".equals(uri.getLastPathSegment())) {
            // Home's once-a-minute check is for live scores: INDmoney is only asked when Home opens or was told of a change.
            if (uri.getQueryParameter("poll") == null) Insights.refreshIfStale(getContext());
            Cricket.refreshIfStale(getContext());
            MatrixCursor c = new MatrixCursor(new String[] {"json"});
            c.addRow(new Object[] {Insights.json(getContext())});
            c.setNotificationUri(getContext().getContentResolver(), insightsUri());
            return c;
        }
        if (!"items".equals(uri.getLastPathSegment())) return null;
        Store.Snapshot s = Store.get(getContext());
        // Home sends its drawer apps: TMDB is asked about those only, and straight away for one just added.
        boolean added = args != null && Refresher.setDrawer(getContext(), args);
        if (added) Refresher.kickSoon(getContext());
        else if (System.currentTimeMillis() - s.refreshedMs > Refresher.STALE_MS) Refresher.kick(getContext(), null);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        List<Item> items = s.items;
        boolean logos = logosOn(getContext());
        for (Item i : items) {
            c.addRow(new Object[] {i.row, i.rowOrder, i.title, i.subtitle, i.rating, i.progress,
                    i.poster.isEmpty() ? "" : imageUri(i.poster).toString(),
                    i.backdrop.isEmpty() ? "" : imageUri(i.backdrop).toString(),
                    i.intent, i.pkg, i.hero ? 1 : 0, i.section, i.tab, i.overview, i.kind, i.year, i.genre, i.plainArt ? 1 : 0, i.engaged, i.released, i.tmdb,
                    i.logo.isEmpty() || !logos ? "" : imageUri(i.logo).toString()});
        }
        c.setNotificationUri(getContext().getContentResolver(), itemsUri());
        // When these rows were made: Home draws the header's picks from it, a new draw for each refresh.
        android.os.Bundle extras = new android.os.Bundle();
        extras.putLong("refreshed", s.refreshedMs);
        c.setExtras(extras);
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> path = uri.getPathSegments();
        if (!allowed() || path.size() != 2 || !"image".equals(path.get(0)) || !Images.NAME.matcher(path.get(1)).matches()) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(new File(Images.dir(getContext()), path.get(1)), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** This app, or an app signed with the same key. */
    private boolean allowed() {
        String caller;
        try {
            caller = getCallingPackage();
        } catch (SecurityException e) {
            return false;
        }
        if (caller == null) return false;
        String self = getContext().getPackageName();
        return caller.equals(self)
                || getContext().getPackageManager().checkSignatures(self, caller) == PackageManager.SIGNATURE_MATCH;
    }

    @Override
    public String getType(Uri uri) {
        return "image".equals(uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0)) ? "image/jpeg" : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
