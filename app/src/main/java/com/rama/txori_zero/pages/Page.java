package com.rama.txori_zero.pages;

/** One screen of the main activity. Pages are plain views, so no Fragment (API 11) is needed. */
public interface Page {
    /** The page became visible. */
    void onShow();

    void onEdit();

    void onClose();

    /** Lets the page decide which top bar buttons (edit / close) show. */
    void syncTopBar();

    void destroy();
}
