package com.ArfGg57.modupdater.restart;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates a single forced crash execution across any ModUpdater components.
 * Ensures only one crash is thrown even if multiple listeners detect the condition.
 */
public final class CrashCoordinator {
    private static final AtomicBoolean CLAIMED = new AtomicBoolean(false);

    private CrashCoordinator() {}

    /** Attempt to claim crash execution; returns true if this caller should proceed. */
    public static boolean tryClaim() {
        return CLAIMED.compareAndSet(false, true);
    }

    /** Has a crash already been executed/claimed? */
    public static boolean isCrashExecuted() {
        return CLAIMED.get();
    }

    /** Reset (primarily for tests). */
    public static void resetForTests() {
        CLAIMED.set(false);
    }
}

