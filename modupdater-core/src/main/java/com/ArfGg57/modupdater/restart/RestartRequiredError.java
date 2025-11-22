package com.ArfGg57.modupdater.restart;

/** Signals that a restart is required to finish applying updates (locked files handled externally). */
public class RestartRequiredError extends Error {
    public RestartRequiredError(String message) { super(message); }
    public RestartRequiredError(String message, Throwable cause) { super(message, cause); }
}

