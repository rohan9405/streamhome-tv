package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** The primary drawer's glass popup, anchored to the pressed shortcut or recommendation. */
final class HomeOptionsMenu {
    private HomeOptionsMenu() { }

    static void show(Activity act, View anchor, String title, String[] labels, Runnable[] actions, Runnable onDismiss) {
        if (anchor == null || !anchor.isAttachedToWindow()) return;
        float density = act.getResources().getDisplayMetrics().density;
        View panel = act.getLayoutInflater().inflate(R.layout.popup_inputs, null, false);
        panel.setContentDescription(title);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = new PopupWindow(panel, Math.round(240 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (anchor.isAttachedToWindow()) anchor.requestFocusFromTouch();
            if (onDismiss != null) onDismiss.run();
        });
        for (int i = 0; i < labels.length; i++) {
            View row = act.getLayoutInflater().inflate(R.layout.item_input, rows, false);
            for (int id : new int[] {R.id.icon, R.id.dot, R.id.status}) row.findViewById(id).setVisibility(View.GONE);
            ((TextView) row.findViewById(R.id.title)).setText(labels[i]);
            Runnable action = actions[i];
            row.setOnClickListener(v -> { popup.dismiss(); action.run(); });
            // A fresh OK press is required; releasing the long press that opened the popup does nothing.
            final boolean[] pressed = {false};
            row.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) pressed[0] = true;
                } else {
                    boolean activate = pressed[0];
                    pressed[0] = false;
                    if (activate) { popup.dismiss(); action.run(); }
                }
                return true;
            });
            rows.addView(row);
        }
        // Sized to its rows, like the drawer's and Settings' menus.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(Math.round(150 * density), panel.getMeasuredWidth()));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        Rect screen = new Rect();
        anchor.getWindowVisibleDisplayFrame(screen);
        int gap = Math.round(10 * density), height = panel.getMeasuredHeight();
        boolean below = location[1] + anchor.getHeight() + gap + height <= screen.bottom;
        panel.setAlpha(0f);
        panel.setPivotY(below ? 0f : height);
        panel.setScaleY(0.92f);
        panel.setTranslationY((below ? -8 : 8) * density);
        if (below) {
            boolean topBar = anchor.getParent() instanceof View && ((View) anchor.getParent()).getId() == R.id.topButtons;
            popup.showAsDropDown(anchor, 0, gap, topBar ? Gravity.END : Gravity.START);
        } else {
            int x = Math.max(screen.left, Math.min(location[0], screen.right - popup.getWidth()));
            popup.showAtLocation(anchor.getRootView(), Gravity.NO_GRAVITY, x, Math.max(screen.top, location[1] - gap - height));
        }
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocusFromTouch();
    }
}
