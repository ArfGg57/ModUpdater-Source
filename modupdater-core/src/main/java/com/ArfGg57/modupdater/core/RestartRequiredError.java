package com.ArfGg57.modupdater.core;

/**
 * Error thrown to force a game crash when mod updates require a restart.
 * This extends Error (not Exception) so it bypasses normal exception handlers
 * and properly crashes the game, which is necessary because:
 * 1. FMLSecurityManager blocks System.exit() calls
 * 2. RuntimeException is caught by FML exception handlers
 */
public class RestartRequiredError extends Error {
    
    public RestartRequiredError(String message) {
        super(message);
    }
    
    public RestartRequiredError(String message, Throwable cause) {
        super(message, cause);
    }
}
