package com.rama.txori_zero.activities;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;

import com.rama.txori_zero.R;
import com.rama.txori_zero.helpers.SystemBars;
import com.rama.txori_zero.managers.DatabaseHelper;
import com.rama.txori_zero.managers.FontManager;
import com.rama.txori_zero.managers.PrefsManager;
import com.rama.txori_zero.managers.SoundManager;
import com.rama.txori_zero.managers.ThemeManager;
import com.rama.txori_zero.managers.ZoomManager;
import com.rama.txori_zero.pages.HomePage;
import com.rama.txori_zero.pages.Page;
import com.rama.txori_zero.pages.RoulettePage;
import com.rama.txori_zero.pages.StopwatchPage;
import com.rama.txori_zero.pages.TimerPage;
import com.rama.txori_zero.widgets.WdNavbar;

public class Main extends BaseActivity {
    private static final String KEY_PAGE = "current_page";
    private static final long FLASH_MS = 200;

    private DatabaseHelper helper;
    private SQLiteDatabase db;
    private WdNavbar navbar;
    private View root;
    private View editButton;
    private View closeButton;
    private View flashOverlay;
    private final View[] pageViews = new View[4];
    private final Page[] pages = new Page[4];
    private HomePage home;
    private int currentPage = WdNavbar.HOME;
    private final Handler handler = new Handler();
    private final Runnable hideFlash = () -> flashOverlay.setVisibility(View.GONE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        FontManager.apply(root, FontManager.getJersey25(this));
        ThemeManager.rollIfRandom(this);
        SoundManager.init();

        helper = new DatabaseHelper(this);
        db = helper.getWritableDatabase();

        pageViews[WdNavbar.HOME] = findViewById(R.id.page_home);
        pageViews[WdNavbar.STOPWATCH] = findViewById(R.id.page_stopwatch);
        pageViews[WdNavbar.TIMER] = findViewById(R.id.page_timer);
        pageViews[WdNavbar.ROULETTE] = findViewById(R.id.page_roulette);
        home = new HomePage(this, pageViews[WdNavbar.HOME], helper, db);
        pages[WdNavbar.HOME] = home;
        pages[WdNavbar.STOPWATCH] = new StopwatchPage(this, pageViews[WdNavbar.STOPWATCH]);
        pages[WdNavbar.TIMER] = new TimerPage(this, pageViews[WdNavbar.TIMER]);
        pages[WdNavbar.ROULETTE] = new RoulettePage(this, pageViews[WdNavbar.ROULETTE], helper, db);

        flashOverlay = findViewById(R.id.flash_overlay);
        editButton = findViewById(R.id.edit_btn);
        closeButton = findViewById(R.id.close_btn);
        editButton.setOnClickListener(v -> pages[currentPage].onEdit());
        closeButton.setOnClickListener(v -> pages[currentPage].onClose());
        findViewById(R.id.open_settings).setOnClickListener(v -> startActivity(new Intent(Main.this, Settings.class)));

        navbar = (WdNavbar) findViewById(R.id.navbar);
        navbar.setOnNavigateListener(page -> navigateTo(page));

        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt(KEY_PAGE, WdNavbar.HOME);
        }
        navigateTo(currentPage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PrefsManager.getInstance(this).getBoolean(PrefsManager.KEEP_AWAKE, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        // The settings screen may have changed the theme or zoom.
        refreshTheme();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_PAGE, currentPage);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideFlash);
        for (Page page : pages) {
            if (page != null) page.destroy();
        }
        SoundManager.release();
        if (db != null) db.close();
        if (helper != null) helper.close();
        super.onDestroy();
    }

    private void navigateTo(int page) {
        currentPage = page;
        for (int i = 0; i < pageViews.length; i++) {
            pageViews[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
        }
        pages[page].onShow();
        refreshTheme();
    }

    /** Re-applies zoom, theme and the navbar highlight. */
    public void refreshTheme() {
        ZoomManager.apply(this, root);
        ThemeManager.applyTheme(this, root);
        navbar.setActive(currentPage, ThemeManager.currentPalette(this));
        home.refreshTheme();
    }

    public void setTopBar(boolean edit, boolean close) {
        editButton.setVisibility(edit ? View.VISIBLE : View.GONE);
        closeButton.setVisibility(close ? View.VISIBLE : View.GONE);
    }

    /** What the notification manager runs for the "flash screen" setting. */
    public Runnable flashAction() {
        return () -> {
            handler.removeCallbacks(hideFlash);
            flashOverlay.bringToFront();
            flashOverlay.setVisibility(View.VISIBLE);
            handler.postDelayed(hideFlash, FLASH_MS);
        };
    }
}
