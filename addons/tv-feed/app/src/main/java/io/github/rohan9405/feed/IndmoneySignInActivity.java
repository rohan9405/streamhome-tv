package io.github.rohan9405.feed;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * INDmoney's own sign-in page on the TV. Type into it with the remote, or from a phone: the QR code in the corner opens
 * a page, behind this screen's PIN, whose text is typed into the box selected on the TV. INDmoney sends the page back to
 * a loopback address, which this screen catches, so the TV finishes the sign-in itself. The phone page's server runs only
 * while this screen is open, and for fifteen minutes at most.
 */
public final class IndmoneySignInActivity extends Activity {
    private static final long OPEN_MS = 15 * 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Runnable close = this::finish;
    private WebView web;
    private LinearLayout corner;
    private TextView message;
    private PhoneServer server;
    private volatile Indmoney.SignIn signIn;
    private boolean done;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF10151F);
        web = new WebView(this) {
            /** The remote's Up and Down move between the page's boxes and buttons, as Tab does on a keyboard. */
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                int key = e.getKeyCode();
                if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP) {
                    int meta = key == KeyEvent.KEYCODE_DPAD_UP ? KeyEvent.META_SHIFT_ON : 0;
                    return super.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), KeyEvent.KEYCODE_TAB,
                            e.getRepeatCount(), meta));
                }
                return super.dispatchKeyEvent(e);
            }
        };
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return caught(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                caught(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Ready to type: the first empty box takes the focus, for the remote and the phone alike.
                view.evaluateJavascript(FOCUS_FIRST, null);
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        message = text("Opening INDmoney…", 22, Color.WHITE, Typeface.DEFAULT);
        message.setBackground(panel());
        message.setPadding(dp(28), dp(20), dp(28), dp(20));
        root.addView(message, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        corner.setGravity(Gravity.CENTER_HORIZONTAL);
        corner.setBackground(panel());
        corner.setPadding(dp(14), dp(12), dp(14), dp(12));
        // Bottom-left: INDmoney's page keeps its sign-in form on the right on a wide screen.
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        clp.setMargins(dp(24), 0, 0, dp(24));
        root.addView(corner, clp);
        setContentView(root);
        net.execute(() -> {
            try {
                Indmoney.SignIn s = new Indmoney(this).begin();
                signIn = s;
                main.post(() -> {
                    message.setVisibility(View.GONE);
                    web.loadUrl(s.url);
                    web.requestFocus();
                });
            } catch (IOException e) {
                main.post(() -> say("INDmoney could not be reached. Check the TV's connection, then press Back and try again."));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        corner.removeAllViews();
        try {
            server = new PhoneServer(keyboardPage(), new PhoneServer.Listener() {
                @Override
                public String onForm(Map<String, String> fields) {
                    return type(fields);
                }

                @Override
                public void onLocked() {
                    main.post(() -> corner.setVisibility(View.GONE));
                }
            });
            ImageView code = new ImageView(this);
            code.setImageBitmap(PhoneActivity.qr(server.url(), dp(132)));
            corner.addView(text("Type from your phone", 13, 0xFFDBE4F3, Typeface.DEFAULT_BOLD));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(132));
            lp.topMargin = lp.bottomMargin = dp(8);
            corner.addView(code, lp);
            corner.addView(text("PIN  " + server.pin, 18, Color.WHITE, Typeface.DEFAULT_BOLD));
            corner.addView(text("Or type with the remote", 11, 0xFF8998AF, Typeface.DEFAULT));
        } catch (IOException | com.google.zxing.WriterException e) {
            // No local network for the phone: the remote still types.
            corner.setVisibility(View.GONE);
        }
        main.postDelayed(close, OPEN_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        main.removeCallbacks(close);
        if (server != null) server.stop();
        server = null;
        if (!isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        net.shutdownNow();
        web.destroy();
    }

    /** The phone's text, typed into the box selected on the TV; "enter" also presses Enter, "clear" empties the box. */
    private String type(Map<String, String> fields) {
        String text = fields.containsKey("text") ? fields.get("text") : "";
        String action = fields.containsKey("action") ? fields.get("action") : "type";
        if (text.length() > 100) return "That is too long.";
        if (done) return "The TV has finished signing in.";
        main.post(() -> {
            if ("clear".equals(action)) {
                web.evaluateJavascript(CLEAR, null);
                return;
            }
            web.evaluateJavascript("(" + TYPE + ")(" + JSONObject.quote(text) + ")", result -> {
                if ("enter".equals(action)) {
                    long now = android.os.SystemClock.uptimeMillis();
                    web.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0));
                    web.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0));
                }
            });
        });
        return "clear".equals(action) ? "Cleared the box on the TV." : "Typed on the TV.";
    }

    /** INDmoney sent the page back: the sign-in is finished here, and nothing loads at that address. */
    private boolean caught(String url) {
        if (url == null || !url.startsWith(Indmoney.RETURN)) return false;
        web.stopLoading();
        if (done) return true;
        done = true;
        Indmoney.SignIn s = signIn;
        web.setVisibility(View.INVISIBLE);
        say("Signing in…");
        net.execute(() -> {
            try {
                if (s == null) throw new IOException("This sign-in was not started here.");
                Indmoney.finish(this, s, url);
                Insights.kick(this);
                main.post(() -> {
                    say("Signed in to INDmoney. Your portfolio shows on Home in a minute.");
                    corner.setVisibility(View.GONE);
                    main.removeCallbacks(close);
                    main.postDelayed(close, 4000);
                });
            } catch (IOException e) {
                main.post(() -> say(e.getMessage() + " Press Back and try again."));
            }
        });
        return true;
    }

    private void say(String s) {
        message.setText(s);
        message.setVisibility(View.VISIBLE);
    }

    private GradientDrawable panel() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF2171E2B);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), 0xFF2B3546);
        return g;
    }

    private TextView text(String s, int sp, int colour, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** The first visible, empty box on the page, or the first visible one; null when the page has none. */
    private static final String PICK = "function(){var all=[].slice.call(document.querySelectorAll('input:not([type=hidden]):not([type=checkbox]):not([type=radio]),textarea'))"
            + ".filter(function(x){return x.offsetParent!==null&&!x.disabled&&!x.readOnly});"
            + "return all.filter(function(x){return !x.value})[0]||all[0]||null;}";
    private static final String FOCUS_FIRST = "(function(){var a=document.activeElement;if(a&&(a.tagName=='INPUT'||a.tagName=='TEXTAREA'))return;"
            + "var e=(" + PICK + ")();if(e)e.focus();})()";
    /** Types at the selected box (or the first empty one) as a keyboard would, so the page's own scripts see it. */
    private static final String TYPE = "function(t){var e=document.activeElement;"
            + "if(!e||(e.tagName!='INPUT'&&e.tagName!='TEXTAREA')){e=(" + PICK + ")();if(!e)return 'none';e.focus();}"
            + "document.execCommand('insertText',false,t);return 'ok';}";
    private static final String CLEAR = "(function(){var e=document.activeElement;if(!e||(e.tagName!='INPUT'&&e.tagName!='TEXTAREA'))return;"
            + "e.select();document.execCommand('delete');})()";

    /** One box, typed into the TV's selected box; the page stays, so the PIN is entered once. */
    static String keyboardPage() {
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>StreamHome</title>"
                + "<style>body{background:#10151f;color:#fff;font:17px sans-serif;padding:20px;max-width:480px;margin:auto}"
                + "label{display:block;margin:16px 0 6px;color:#b5c0d2}input{width:100%;box-sizing:border-box;padding:12px;font-size:20px;"
                + "border-radius:10px;border:1px solid #354053;background:#1c2432;color:#fff}"
                + "button{margin-top:12px;width:100%;padding:14px;font-size:17px;border:0;border-radius:12px;background:#f0f5ff;color:#111722}"
                + "button.quiet{background:#1c2432;color:#dbe4f3;border:1px solid #354053}#said{margin-top:14px;color:#a5f3cc;min-height:1.4em}</style>"
                + "<h2>Type on the TV</h2><p style='color:#c8d0de'>What you type goes into the box selected on the TV's INDmoney page: "
                + "your mobile number first, then the code INDmoney texts you.</p>"
                + "<form id=f autocomplete=off><label>PIN on the TV</label><input name=pin inputmode=numeric maxlength=6 required>"
                + "<label>Text</label><input name=text id=t inputmode=numeric autocomplete=one-time-code>"
                + "<button type=button data-a=enter>Type and press Enter</button><button type=button class=quiet data-a=type>Type only</button>"
                + "<button type=button class=quiet data-a=clear>Clear the box on the TV</button></form><div id=said></div>"
                + "<script>document.querySelectorAll('button').forEach(function(b){b.onclick=function(){var f=document.getElementById('f');"
                + "if(!f.pin.value){document.getElementById('said').textContent='Enter the PIN on the TV first.';return;}"
                + "var d=new URLSearchParams(new FormData(f));d.set('action',b.dataset.a);"
                + "fetch('/',{method:'POST',body:d}).then(function(r){return r.text()}).then(function(h){"
                + "var s=new DOMParser().parseFromString(h,'text/html').body.textContent.replace('StreamHome','').trim();"
                + "document.getElementById('said').textContent=s;if(s.indexOf('Typed')==0)document.getElementById('t').value='';})"
                + ".catch(function(){document.getElementById('said').textContent='The TV did not answer. Is its sign-in screen still open?';});};});</script>";
    }
}
