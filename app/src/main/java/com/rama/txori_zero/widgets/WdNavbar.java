package com.rama.txori_zero.widgets;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.rama.txori_zero.R;
import com.rama.txori_zero.objects.Themes;

public class WdNavbar extends LinearLayout {
    public static final int HOME = 0;
    public static final int STOPWATCH = 1;
    public static final int TIMER = 2;
    public static final int ROULETTE = 3;

    public interface OnNavigateListener {
        void onNavigate(int page);
    }

    private final View[] buttons = new View[4];
    private final ImageView[] icons = new ImageView[4];
    private OnNavigateListener listener;

    public WdNavbar(Context context) {
        this(context, null);
    }

    public WdNavbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.wd_navbar, this, true);
        buttons[HOME] = findViewById(R.id.nav_home);
        buttons[STOPWATCH] = findViewById(R.id.nav_stopwatch);
        buttons[TIMER] = findViewById(R.id.nav_timer);
        buttons[ROULETTE] = findViewById(R.id.nav_roulette);
        icons[HOME] = (ImageView) findViewById(R.id.nav_home_icon);
        icons[STOPWATCH] = (ImageView) findViewById(R.id.nav_stopwatch_icon);
        icons[TIMER] = (ImageView) findViewById(R.id.nav_timer_icon);
        icons[ROULETTE] = (ImageView) findViewById(R.id.nav_roulette_icon);
        for (int i = 0; i < buttons.length; i++) {
            final int page = i;
            buttons[i].setOnClickListener(v -> {
                if (listener != null) listener.onNavigate(page);
            });
        }
    }

    public void setOnNavigateListener(OnNavigateListener listener) {
        this.listener = listener;
    }

    /** The active page gets the accent background and an inverted icon. Call after the theme was applied. */
    public void setActive(int page, Themes.Palette palette) {
        for (int i = 0; i < buttons.length; i++) {
            boolean active = i == page;
            buttons[i].setBackgroundColor(active ? palette.accent : palette.surface_0);
            buttons[i].setEnabled(!active);
            icons[i].setTag(active ? "base" : "text");
            icons[i].setColorFilter(active ? palette.base : palette.text, PorterDuff.Mode.SRC_IN);
        }
    }
}
