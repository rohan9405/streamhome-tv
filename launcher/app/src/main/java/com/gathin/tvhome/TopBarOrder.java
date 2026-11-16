package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Remote-editable Home shortcuts: moved with Move, taken off the bar with Remove from top bar, and put back from the Feed
 * screen (Top bar), which keeps the list. IDs are resource names, so order survives rebuilding the APK.
 */
final class TopBarOrder {
    private final Activity act;
    private final ViewGroup bar;
    private final SharedPreferences prefs;
    /** Every shortcut, on the bar or taken off it. */
    private final List<View> all;
    private java.util.Set<String> hidden = new java.util.HashSet<>();
    private final java.util.function.Consumer<String> onRemove;
    private View moving;
    private List<View> before;
    private boolean eatConfirmUp;

    /** {@code onRemove}: a shortcut's name ("btnWifi") when the owner takes it off the bar. */
    TopBarOrder(Activity act, ViewGroup bar, java.util.function.Consumer<Runnable> settingsMenu, java.util.function.Consumer<String> onRemove) {
        this.act = act;
        this.bar = bar;
        this.onRemove = onRemove;
        prefs = act.getSharedPreferences("home_shortcuts", Context.MODE_PRIVATE);
        all = buttons();
        List<View> ordered = new ArrayList<>();
        for (String name : prefs.getString("order", "").split(",")) {
            for (View v : buttons()) if (name.equals(name(v)) && !ordered.contains(v)) ordered.add(v);
        }
        for (View v : buttons()) if (!ordered.contains(v)) ordered.add(v);
        apply(ordered);
        for (View v : buttons()) v.setOnLongClickListener(button -> {
            if (moving != null) return true;
            String title = button.getContentDescription() == null ? name(button) : button.getContentDescription().toString();
            // Settings stays: holding OK on it is the way into Launcher settings.
            boolean removable = button.getId() != R.id.btnSettings;
            String[] options = removable ? new String[] {"Move", "Remove"} : new String[] {"Move"};
            Runnable remove = () -> {
                String gone = name(button);
                java.util.Set<String> now = new java.util.HashSet<>(hidden);
                now.add(gone);
                setHidden(now);
                onRemove.accept(gone);
                Toast.makeText(act, "Removed. Turn it back on in Apps → Feed → Top bar", Toast.LENGTH_LONG).show();
            };
            Runnable move = () -> {
                moving = button;
                before = buttons();
                // Lifted like a drawer tile being moved.
                button.setSelected(true);
                button.animate().scaleX(1.16f).scaleY(1.16f).setDuration(120).start();
                button.requestFocusFromTouch();
                Toast.makeText(act, "Left / Right to move · OK to save · Back to cancel", Toast.LENGTH_LONG).show();
            };
            if (button.getId() == R.id.btnSettings) settingsMenu.accept(move);
            else HomeOptionsMenu.show(act, button, title, options, removable ? new Runnable[] {move, remove} : new Runnable[] {move},
                    () -> { if (button.getParent() != null) button.requestFocusFromTouch(); else first().requestFocus(); });
            return true;
        });
    }

    private List<View> buttons() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View v = bar.getChildAt(i);
            int id = v.getId();
            if (id == R.id.btnSearch || id == R.id.btnWatchlist || id == R.id.btnInputs || id == R.id.btnWifi || id == R.id.btnBluetooth || id == R.id.btnSettings) out.add(v);
        }
        return out;
    }

    private String name(View v) { return act.getResources().getResourceEntryName(v.getId()); }

    /**
     * The shortcuts taken off the bar, as the Feed screen keeps them: those come off, the rest go back in their saved
     * order, where the shortcuts were. Settings never comes off.
     */
    void setHidden(java.util.Set<String> names) {
        java.util.Set<String> now = new java.util.HashSet<>(names);
        now.remove("btnSettings");
        if (now.equals(hidden) || moving != null) return;
        hidden = now;
        View focused = act.getCurrentFocus();
        List<View> on = buttons();
        int at = on.isEmpty() ? bar.getChildCount() : bar.indexOfChild(on.get(0));
        for (View v : on) bar.removeView(v);
        for (View v : saved()) {
            if (hidden.contains(name(v))) continue;
            bar.addView(v, Math.min(at++, bar.getChildCount()));
        }
        if (focused != null && focused.getParent() == null) first().requestFocus();
    }

    /** Every shortcut in the saved order, then any the order does not name yet. */
    private List<View> saved() {
        List<View> ordered = new ArrayList<>();
        for (String n : prefs.getString("order", "").split(",")) {
            for (View v : all) if (n.equals(name(v)) && !ordered.contains(v)) ordered.add(v);
        }
        for (View v : all) if (!ordered.contains(v)) ordered.add(v);
        return ordered;
    }

    private void apply(List<View> order) {
        List<Integer> slots = new ArrayList<>();
        for (View v : buttons()) slots.add(bar.indexOfChild(v));
        for (View v : buttons()) bar.removeView(v);
        for (int i = 0; i < order.size(); i++) bar.addView(order.get(i), Math.min(slots.get(i), bar.getChildCount()));
    }

    View first() {
        for (int i = 0; i < bar.getChildCount(); i++) if (bar.getChildAt(i).getVisibility() == View.VISIBLE) return bar.getChildAt(i);
        return act.findViewById(R.id.btnSettings);
    }

    void stop() { if (moving != null) finish(true); }

    boolean finishClick(View v) {
        if (moving == null) return false;
        finish(true);
        return true;
    }

    private void finish(boolean save) {
        View was = moving;
        moving = null;
        if (save) {
            // The bar's new order, with the shortcuts taken off it kept in their places among the rest.
            List<String> names = new ArrayList<>();
            for (View v : buttons()) names.add(name(v));
            List<View> earlier = saved();
            for (int i = 0; i < earlier.size(); i++) {
                String n = name(earlier.get(i));
                if (!names.contains(n)) names.add(Math.min(i, names.size()), n);
            }
            prefs.edit().putString("order", android.text.TextUtils.join(",", names)).apply();
        } else apply(before);
        was.setSelected(false);
        was.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        was.requestFocus();
    }

    boolean movingKey(KeyEvent event) {
        return (moving != null || eatConfirmUp) && key(moving != null ? moving : first(), event.getKeyCode(), event);
    }

    boolean key(View v, int key, KeyEvent event) {
        boolean confirm = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (eatConfirmUp && (confirm || key == KeyEvent.KEYCODE_BACK)) { eatConfirmUp = false; return true; }
            return moving != null;
        }
        if (moving != null) {
            if (confirm || key == KeyEvent.KEYCODE_BACK) {
                finish(confirm);
                eatConfirmUp = true;
            } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                List<View> order = buttons();
                int at = order.indexOf(moving), dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (bar.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) dir = -dir;
                int next = at + dir;
                while (next >= 0 && next < order.size() && order.get(next).getVisibility() != View.VISIBLE) next += dir;
                if (next >= 0 && next < order.size()) {
                    float x = moving.getX();
                    java.util.Collections.swap(order, at, next);
                    apply(order);
                    moving.requestFocus();
                    View tile = moving;
                    tile.post(() -> {
                        tile.setTranslationX(x - tile.getLeft());
                        tile.animate().translationX(0).setDuration(160).start();
                    });
                }
            }
            return true;
        }
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        int dir = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
        if (bar.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) dir = -dir;
        for (int i = bar.indexOfChild(v) + dir; i >= 0 && i < bar.getChildCount(); i += dir) {
            View next = bar.getChildAt(i);
            if (next.getVisibility() == View.VISIBLE && next.isFocusable()) { next.requestFocus(); return true; }
        }
        // Left at the edge is handled by Home, to reach the finance chip.
        return key == KeyEvent.KEYCODE_DPAD_RIGHT;
    }
}
