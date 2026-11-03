package io.github.rohan9405.feed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The phone page's small web server: on while its TV screen is open, for devices on this TV's own network only. It serves
 * one page, and acts on a form only with the PIN shown on the TV; {@value #MAX_WRONG} wrong PINs stop it. Plain HTTP, as
 * a TV has no certificate a phone would trust: what is typed crosses the home network unencrypted, which the screen
 * says. Nothing typed is ever sent back.
 */
final class PhoneServer {
    interface Listener {
        /** A checked form's fields, on the server's thread; the text returned goes back to the phone. */
        String onForm(Map<String, String> fields);

        /** Too many wrong PINs: the server has stopped. */
        void onLocked();
    }

    static final int MAX_WRONG = 5;
    private static final int MAX_BODY = 16 * 1024, MAX_HEAD = 8 * 1024, TIMEOUT_MS = 10_000;

    final String pin;
    private final String page;
    private final Listener listener;
    private final String host;
    private final ServerSocket socket;
    private int wrong;

    /** @throws IOException when this TV has no address on a local network, or no port to listen on */
    PhoneServer(String page, Listener listener) throws IOException {
        String address = localAddress();
        if (address == null) throw new IOException("no local network");
        this.page = page;
        this.listener = listener;
        pin = newPin(new SecureRandom());
        socket = new ServerSocket(0, 4);
        host = address + ":" + socket.getLocalPort();
        Thread t = new Thread(this::serve, "phone-page");
        t.setDaemon(true);
        t.start();
    }

    String url() {
        return "http://" + host + "/";
    }

    void stop() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private void serve() {
        while (!socket.isClosed()) {
            try (Socket s = socket.accept()) {
                s.setSoTimeout(TIMEOUT_MS);
                handle(s);
            } catch (IOException | RuntimeException e) {
                // A dropped connection, or the server stopping.
            }
        }
    }

    private void handle(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        if (!ours(s.getInetAddress())) {
            respond(out, "403 Forbidden", "Only this TV's own network.");
            return;
        }
        String head = readHead(in);
        String[] lines = head.split("\r\n");
        String[] request = lines[0].split(" ");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        // A web page elsewhere cannot reach this server by a name of its own.
        if (request.length < 2 || !host.equals(headers.get("host"))) {
            respond(out, "403 Forbidden", "Open the address shown on the TV.");
        } else if ("GET".equals(request[0]) && "/".equals(request[1])) {
            respond(out, "200 OK", page);
        } else if ("POST".equals(request[0]) && "/".equals(request[1])) {
            int length;
            try {
                length = Integer.parseInt(String.valueOf(headers.get("content-length")));
            } catch (NumberFormatException e) {
                length = -1;
            }
            if (length < 0 || length > MAX_BODY) {
                respond(out, "413 Too Large", "That is too long.");
                return;
            }
            post(out, parseForm(new String(readBody(in, length), StandardCharsets.UTF_8)));
        } else {
            respond(out, "404 Not Found", "Not here.");
        }
    }

    private void post(OutputStream out, Map<String, String> form) throws IOException {
        if (!pinMatches(pin, form.get("pin"))) {
            wrong++;
            if (wrong >= MAX_WRONG) {
                respond(out, "403 Forbidden", result("Too many wrong PINs. Open the phone page on the TV again."));
                stop();
                listener.onLocked();
                return;
            }
            respond(out, "403 Forbidden", result("That PIN is wrong. Check the TV and go back."));
            return;
        }
        form.remove("pin");
        respond(out, "200 OK", result(listener.onForm(form)));
    }

    static String result(String message) {
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>StreamHome</title>"
                + "<body style='background:#10151f;color:#fff;font:18px sans-serif;padding:24px'>" + escape(message);
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void respond(OutputStream out, String status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\n"
                + "Referrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c, matched = 0;
        byte[] end = {'\r', '\n', '\r', '\n'};
        while (matched < 4 && b.size() < MAX_HEAD && (c = in.read()) >= 0) {
            b.write(c);
            matched = c == end[matched] ? matched + 1 : c == '\r' ? 1 : 0;
        }
        return b.toString("ISO-8859-1");
    }

    private static byte[] readBody(InputStream in, int length) throws IOException {
        byte[] b = new byte[length];
        int at = 0, n;
        while (at < length && (n = in.read(b, at, length - at)) >= 0) at += n;
        return at == length ? b : java.util.Arrays.copyOf(b, at);
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> out = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (java.io.UnsupportedEncodingException | IllegalArgumentException ignored) {
                // A malformed field counts as missing.
            }
        }
        return out;
    }

    static boolean pinMatches(String pin, String typed) {
        return typed != null && MessageDigest.isEqual(pin.getBytes(StandardCharsets.UTF_8), typed.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** Six digits, a new one each time the page opens. */
    static String newPin(SecureRandom random) {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    private static boolean ours(InetAddress peer) {
        return peer.isSiteLocalAddress() || peer.isLoopbackAddress() || peer.isLinkLocalAddress();
    }

    /** This TV's address on its local network; null when it has none. */
    private static String localAddress() throws IOException {
        for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!n.isUp() || n.isLoopback()) continue;
            for (InetAddress a : Collections.list(n.getInetAddresses())) {
                if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        }
        return null;
    }
}
