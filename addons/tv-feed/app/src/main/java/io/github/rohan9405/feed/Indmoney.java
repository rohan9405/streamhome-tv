package io.github.rohan9405.feed;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * INDmoney's official MCP server, read-only (portfolio:read market:read), as its own client: the TV holds the sign-in
 * and renews it itself, so nothing else (no computer, no other app) has to be on. The sign-in comes from the computer
 * once (my-tv/indmoney-pair.sh); after that the refresh token is traded for a new one at every renewal, and the new one
 * is saved before it is used. When INDmoney refuses it, the sign-in is marked as needing a reconnect.
 */
final class Indmoney {
    private static final String PREFS = "indmoney";
    private static final String MCP = "https://mcp.indmoney.com/mcp";
    private static final String TOKEN = "https://mcp.indmoney.com/token";
    private static final String RESOURCE = "https://mcp.indmoney.com/mcp";
    private static final int TIMEOUT_MS = 15000;
    private static final String REGISTER = "https://mcp.indmoney.com/register";
    private static final String AUTHORIZE = "https://mcp.indmoney.com/authorize";
    private static final String SCOPE = "portfolio:read market:read";
    /**
     * Where INDmoney sends the browser after sign-in: the loopback address the computer's sign-in also uses. On the TV the
     * sign-in page's own web view catches it before anything loads.
     */
    static final String RETURN = "http://127.0.0.1:53682/callback";

    /** INDmoney refused the sign-in: someone has to sign in again. */
    static final class ReconnectNeeded extends Exception {
        ReconnectNeeded(String why) {
            super(why);
        }
    }

    private final SharedPreferences prefs;
    private String session;

    Indmoney(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Stores a sign-in made on the computer: the registered client and its refresh token. */
    static void pair(Context ctx, String clientId, String clientSecret, String refreshToken) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
                .putString("client_id", clientId.trim()).putString("client_secret", clientSecret.trim())
                .putString("refresh_token", refreshToken.trim()).commit();
    }

    /** A sign-in the TV started: the client, and the PKCE secret and state only the TV holds. */
    static final class SignIn {
        final String clientId, clientSecret, verifier, state, url;

        SignIn(String clientId, String clientSecret, java.security.SecureRandom random) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            verifier = token(random, 48);
            state = token(random, 16);
            String challenge;
            try {
                challenge = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)), android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            url = AUTHORIZE + "?response_type=code&client_id=" + enc(clientId) + "&redirect_uri=" + enc(RETURN)
                    + "&scope=" + enc(SCOPE).replace("+", "%20") + "&state=" + enc(state) + "&code_challenge=" + enc(challenge)
                    + "&code_challenge_method=S256&resource=" + enc(RESOURCE);
        }

        private static String token(java.security.SecureRandom random, int bytes) {
            byte[] b = new byte[bytes];
            random.nextBytes(b);
            return android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        }
    }

    /**
     * Starts a sign-in on the TV: the client this TV already has (every sign-in so far registered one for {@link #RETURN}),
     * or a new read-only one registered now.
     */
    SignIn begin() throws IOException {
        String id = prefs.getString("client_id", ""), secret = prefs.getString("client_secret", "");
        if (id.isEmpty() || secret.isEmpty()) {
            HttpURLConnection c = open(REGISTER);
            c.setRequestProperty("Content-Type", "application/json");
            try {
                write(c, new JSONObject(map("client_name", "StreamHome", "redirect_uris", new JSONArray().put(RETURN),
                        "grant_types", new JSONArray().put("authorization_code").put("refresh_token"),
                        "response_types", new JSONArray().put("code"), "token_endpoint_auth_method", "client_secret_post",
                        "scope", SCOPE)).toString());
                if (c.getResponseCode() / 100 != 2) throw new IOException("register " + c.getResponseCode());
                JSONObject client = new JSONObject(read(c.getInputStream()));
                id = client.getString("client_id");
                secret = client.getString("client_secret");
            } catch (JSONException e) {
                throw new IOException("register: not JSON", e);
            } finally {
                c.disconnect();
            }
        }
        return new SignIn(id, secret, new java.security.SecureRandom());
    }

    /**
     * Ends a sign-in with the address INDmoney sent the web view to: its code, traded with the secret only this TV has,
     * becomes the TV's sign-in. Nothing is changed unless INDmoney accepts it.
     */
    static void finish(Context ctx, SignIn s, String returned) throws IOException {
        String[] codeState = codeAndState(returned);
        if (codeState == null) throw new IOException("INDmoney did not send a sign-in back.");
        if (!s.state.equals(codeState[1])) throw new IOException("That answer belongs to another sign-in.");
        String form = "grant_type=authorization_code&code=" + enc(codeState[0]) + "&redirect_uri=" + enc(RETURN)
                + "&client_id=" + enc(s.clientId) + "&client_secret=" + enc(s.clientSecret) + "&code_verifier=" + enc(s.verifier)
                + "&resource=" + enc(RESOURCE);
        HttpURLConnection c = open(TOKEN);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            write(c, form);
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("INDmoney did not accept the sign-in (" + code + ").");
            JSONObject t = new JSONObject(read(c.getInputStream()));
            pair(ctx, s.clientId, s.clientSecret, t.getString("refresh_token"));
        } catch (JSONException e) {
            throw new IOException("INDmoney's answer was not what was expected.", e);
        } finally {
            c.disconnect();
        }
    }

    /** {code, state} from the address INDmoney sent the browser to, or only its query; null without them. */
    static String[] codeAndState(String returned) {
        if (returned == null) return null;
        String s = returned.trim();
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(q + 1);
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        String code = null, state = null;
        for (String pair : s.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String value;
            try {
                value = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
            } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) {
                continue;
            }
            if ("code".equals(pair.substring(0, eq))) code = value;
            if ("state".equals(pair.substring(0, eq))) state = value;
        }
        return code == null || state == null || code.isEmpty() || !code.matches("[A-Za-z0-9._~-]{1,512}") ? null : new String[] {code, state};
    }

    boolean paired() {
        return !prefs.getString("refresh_token", "").isEmpty();
    }

    boolean reconnectNeeded() {
        return prefs.getBoolean("reconnect", false);
    }

    /** One tool's answer: the JSON inside the tool result's text. */
    JSONObject tool(String name, JSONObject args) throws IOException, ReconnectNeeded {
        if (session == null) {
            call(new JSONObject(map("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", new JSONObject(map(
                    "protocolVersion", "2025-06-18", "capabilities", new JSONObject(),
                    "clientInfo", new JSONObject(map("name", "streamhome", "version", "1")))))));
            call(new JSONObject(map("jsonrpc", "2.0", "method", "notifications/initialized")));
        }
        JSONObject res = call(new JSONObject(map("jsonrpc", "2.0", "id", 2, "method", "tools/call",
                "params", new JSONObject(map("name", name, "arguments", args)))));
        try {
            JSONObject result = res.getJSONObject("result");
            JSONArray content = result.getJSONArray("content");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < content.length(); i++) text.append(content.getJSONObject(i).optString("text"));
            if (result.optBoolean("isError")) throw new IOException(name + ": " + text);
            JSONObject outer = new JSONObject(text.toString());
            Object inner = outer.opt("result");
            return inner instanceof String ? new JSONObject((String) inner) : outer;
        } catch (JSONException e) {
            throw new IOException(name + ": unexpected answer", e);
        }
    }

    private JSONObject call(JSONObject body) throws IOException, ReconnectNeeded {
        String token = accessToken();
        HttpURLConnection c = open(MCP);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json, text/event-stream");
        c.setRequestProperty("Authorization", "Bearer " + token);
        if (session != null) c.setRequestProperty("Mcp-Session-Id", session);
        try {
            write(c, body.toString());
            int code = c.getResponseCode();
            if (code == 401) {
                prefs.edit().remove("access_token").apply();   // renewed on the next try
                throw new IOException("MCP 401");
            }
            if (code / 100 != 2) throw new IOException("MCP " + code);
            String sid = c.getHeaderField("mcp-session-id");
            if (sid != null) session = sid;
            String raw = read(c.getInputStream());
            if (raw.trim().isEmpty()) return new JSONObject();
            if (raw.startsWith("event:") || raw.startsWith("data:")) {
                StringBuilder data = new StringBuilder();
                for (String line : raw.split("\n")) if (line.startsWith("data:")) data.append(line.substring(5));
                raw = data.toString();
            }
            return new JSONObject(raw);
        } catch (JSONException e) {
            throw new IOException("MCP: not JSON", e);
        } finally {
            c.disconnect();
        }
    }

    /** A token good for at least five more minutes: the saved one, or a new one for the refresh token. */
    private synchronized String accessToken() throws IOException, ReconnectNeeded {
        if (!paired()) throw new ReconnectNeeded("not signed in");
        String access = prefs.getString("access_token", "");
        if (!access.isEmpty() && prefs.getLong("expires_at", 0) - System.currentTimeMillis() > 5 * 60_000) return access;
        String form = "grant_type=refresh_token&refresh_token=" + enc(prefs.getString("refresh_token", ""))
                + "&client_id=" + enc(prefs.getString("client_id", "")) + "&client_secret=" + enc(prefs.getString("client_secret", ""))
                + "&resource=" + enc(RESOURCE);
        HttpURLConnection c = open(TOKEN);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            write(c, form);
            int code = c.getResponseCode();
            if (code == 400 || code == 401) {
                String why = read(c.getErrorStream());
                prefs.edit().putBoolean("reconnect", true).apply();
                throw new ReconnectNeeded("refresh refused: " + code + " " + why);
            }
            if (code / 100 != 2) throw new IOException("token " + code);
            JSONObject t = new JSONObject(read(c.getInputStream()));
            access = t.getString("access_token");
            SharedPreferences.Editor e = prefs.edit().putString("access_token", access)
                    .putLong("expires_at", System.currentTimeMillis() + 1000L * t.optLong("expires_in", 3600))
                    .putBoolean("reconnect", false);
            // INDmoney swaps the refresh token at every renewal: the new one has to be on disk before anything else.
            if (t.has("refresh_token")) e.putString("refresh_token", t.getString("refresh_token"));
            e.commit();
            return access;
        } catch (JSONException e) {
            throw new IOException("token: not JSON", e);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        // Cloudflare in front of INDmoney turns away Java's default agent.
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; BRAVIA) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        return c;
    }

    private static void write(HttpURLConnection c, String body) throws IOException {
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = s.read(buf)) > 0 && out.size() < (4 << 20)) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.util.Map<String, Object> map(Object... kv) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
