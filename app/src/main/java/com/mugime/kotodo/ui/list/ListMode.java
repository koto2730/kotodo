package com.mugime.kotodo.ui.list;

/** Which set of todos a list screen starts from, before the user's own filters. */
public enum ListMode {
    /** 当日 (or whichever date the user has navigated to). */
    TODAY,
    /** Every todo in the database, including future ones. */
    ALL,
    /** Only 完了済み items. */
    COMPLETED;

    public static ListMode parse(String raw) {
        if (raw != null) {
            for (ListMode mode : values()) {
                if (mode.name().equalsIgnoreCase(raw.trim())) {
                    return mode;
                }
            }
        }
        return TODAY;
    }
}
