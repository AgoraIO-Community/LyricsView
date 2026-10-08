package io.agora.logging;
/** Host-only logging sink. No scoring or model behavior is replaced. */
public final class LogManager {
    private static final LogManager INSTANCE = new LogManager();
    public static LogManager instance() { return INSTANCE; }
    public void debug(String tag, String message) {}
}
