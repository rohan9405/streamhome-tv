package io.github.rohan9405.feed;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * The phone page's TV screen: a QR code and a PIN. Its web server runs only while this screen is showing, and for ten
 * minutes at most. {@code mode=search} has one box, whose words Home's search takes; otherwise the page takes API keys.
 */
public final class PhoneActivity extends Activity {
    static final String MODE = "mode", SEARCH = "search";
    private static final long OPEN_MS = 10 * 60_000;
    /** What the key forms accept: long enough to be a key, and only what keys are made of. */
    private static final String KEY = "[A-Za-z0-9._-]{8,2048}";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private PhoneServer server;
    private boolean search;
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        search = SEARCH.equals(getIntent().getStringExtra(MODE));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF10151F);
        setContentView(page);
    }

    @Override
    protected void onStart() {
        super.onStart();
        page.removeAllViews();
        try {
            server = new PhoneServer(search ? searchPage() : keysPage(), new PhoneServer.Listener() {
                @Override
                public String onForm(Map<String, String> fields) {
                    return search ? words(fields) : keys(fields);
                }

                @Override
                public void onLocked() {
                    main.post(PhoneActivity.this::finish);
                }
            });
            show(server);
            main.postDelayed(close, OPEN_MS);
        } catch (IOException | WriterException e) {
            page.addView(text("This TV is not on a local network, so the phone page cannot open.", 18, Color.WHITE, Typeface.DEFAULT));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        main.removeCallbacks(close);
        if (server != null) server.stop();
        server = null;
        // Not coming back to a server that has stopped.
        finish();
    }

    private String words(Map<String, String> fields) {
        String w = fields.containsKey("words") ? fields.get("words").trim() : "";
        if (w.isEmpty()) return "Type a title first.";
        if (w.length() > 200) w = w.substring(0, 200);
        FeedProvider.setPhoneWords(w);
        main.post(this::finish);
        return "Searching on the TV.";
    }

    private String keys(Map<String, String> fields) {
        StringBuilder said = new StringBuilder();
        String tmdb = fields.getOrDefault("tmdb", "").trim(), omdb = fields.getOrDefault("omdb", "").trim();
        boolean saved = false;
        if (tmdb.matches(KEY)) {
            Tmdb.setToken(this, tmdb);
            said.append("TMDB token saved. ");
            saved = true;
        } else if (!tmdb.isEmpty()) {
            said.append("That TMDB token does not look right. ");
        }
        if (omdb.matches(KEY)) {
            Omdb.setKey(this, omdb);
            said.append("OMDb key saved. ");
            saved = true;
        } else if (!omdb.isEmpty()) {
            said.append("That OMDb key does not look right. ");
        }
        if (saved) Refresher.kick(this, null);
        return said.length() == 0 ? "Nothing to save." : said.toString().trim();
    }

    private void show(PhoneServer s) throws WriterException {
        float d = getResources().getDisplayMetrics().density;
        page.addView(text(search ? "Search from your phone" : "Add keys from your phone", 30, Color.WHITE, Typeface.create("sans-serif-light", Typeface.NORMAL)));
        ImageView code = new ImageView(this);
        code.setImageBitmap(qr(s.url(), Math.round(240 * d)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(240 * d), Math.round(240 * d));
        lp.topMargin = lp.bottomMargin = Math.round(18 * d);
        page.addView(code, lp);
        page.addView(text("PIN  " + s.pin, 34, Color.WHITE, Typeface.DEFAULT_BOLD));
        page.addView(text("Scan with a phone on this Wi-Fi, or open " + s.url(), 15, 0xFFB5C0D2, Typeface.DEFAULT));
        page.addView(text("Open only while this screen shows. Sent unencrypted over your home network, so use it on Wi-Fi you trust.", 13, 0xFF8998AF, Typeface.DEFAULT));
    }

    private TextView text(String s, int sp, int colour, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 6, 0, 6);
        return t;
    }

    private static Bitmap qr(String text, int size) throws WriterException {
        BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                Collections.singletonMap(EncodeHintType.MARGIN, 2));
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) pixels[y * size + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565);
    }

    private static String shell(String body) {
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>StreamHome</title>"
                + "<style>body{background:#10151f;color:#fff;font:17px sans-serif;padding:20px;max-width:480px;margin:auto}"
                + "label{display:block;margin:16px 0 6px;color:#b5c0d2}input{width:100%;box-sizing:border-box;padding:12px;font-size:17px;"
                + "border-radius:10px;border:1px solid #354053;background:#1c2432;color:#fff}"
                + "button{margin-top:20px;width:100%;padding:14px;font-size:17px;border:0;border-radius:12px;background:#f0f5ff;color:#111722}</style>"
                + "<h2>StreamHome</h2><form method=post autocomplete=off>" + body
                + "<label>PIN on the TV</label><input name=pin inputmode=numeric maxlength=6 required><button>Send to TV</button></form>";
    }

    static String searchPage() {
        return shell("<label>Film or show</label><input name=words maxlength=200 required autofocus>");
    }

    static String keysPage() {
        return shell("<p>Leave a box empty to keep what is on the TV.</p>"
                + "<label>TMDB read token</label><input name=tmdb type=password>"
                + "<label>OMDb key</label><input name=omdb type=password>");
    }
}
