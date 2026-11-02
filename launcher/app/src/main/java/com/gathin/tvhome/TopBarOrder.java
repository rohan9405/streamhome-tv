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

/** Remote-editable Home shortcuts. IDs are resource names, so order survives rebuilding the APK. */
final class TopBarOrder {
    private final Activity act;
    private final ViewGroup bar;
    private final SharedPreferences prefs;
    private View moving;
    private List<View> before;
    private boolean eatConfirmUp;

    TopBarOrder(Activity act, ViewGroup bar, java.util.function.Consumer<Runnable> settingsMenu) {
        this.act = act;
        this.bar = bar;
        prefs = act.getSharedPreferences("home_shortcuts", Context.MODE_PRIVATE);
        List<View> ordered = new ArrayList<>();
        for (String name : prefs.getString("order", "").split(",")) {
            for (View v : buttons()) if (name.equals(name(v)) && !ordered.contains(v)) ordered.add(v);
        }
        for (View v : buttons()) if (!ordered.contains(v)) ordered.add(v);
        apply(ordered);
        for (View v : buttons()) v.setOnLongClickListener(button -> {
            if (moving != null) return true;
            String title = button.getContentDescription() == null ? name(button) : button.getContentDescription().toString();
            String[] options = {"Move"};
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
            else HomeOptionsMenu.show(act, button, title, options, new Runnable[] {move}, button::requestFocusFromTouch);
            return true;
        });
    }

    private List<View> buttons() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View v = bar.getChildAt(i);
            int id = v.getId();
            if (id == R.id.btnInputs || id == R.id.btnWifi || id == R.id.btnBluetooth || id == R.id.btnSettings) out.add(v);
        }
        return out;
    }

    private String name(View v) { return act.getResources().getResourceEntryName(v.getId()); }

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
            List<String> names = new ArrayList<>();
            for (View v : buttons()) names.add(name(v));
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
