package io.agora.karaoke_view.v11.logging;

import android.util.Log;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LogManager {
    private static LogManager mLogManager;
    private final ExecutorService mThreadPool;
    private final ConcurrentHashMap<Logger, Integer> mLoggers;

    private LogManager() {
        mLoggers = new ConcurrentHashMap<>(3);
        mThreadPool = Executors.newSingleThreadExecutor();
    }

    public static LogManager instance() {
        if (mLogManager != null) {
            return mLogManager;
        }
        synchronized (Logger.class) {
            if (mLogManager == null) {
                mLogManager = new LogManager();
            }
        }
        return mLogManager;
    }

    public void warn(String tag, String message) {
        log(Log.WARN, tag, message);
    }

    public void info(String tag, String message) {
        log(Log.INFO, tag, message);
    }

    public void debug(String tag, String message) {
        log(Log.DEBUG, tag, message);
    }

    public void error(String tag, String message) {
        log(Log.ERROR, tag, message);
    }

    private void log(int level, String tag, String message) {
        Thread thread = Thread.currentThread();
        String from = "*" + thread.getName() + " " + thread.getPriority() + " " + System.currentTimeMillis() + "*";
        final String msg = from + " " + message;

        mThreadPool.submit(() -> {
            Iterator<Logger> it = mLoggers.keySet().iterator();
            while (it.hasNext()) {
                Logger logger = it.next();
                logger.onLog(level, tag, msg);
            }
        });
    }

    public final void addLogger(Logger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger must not be null");
        }
        mLoggers.put(logger, Objects.hash(logger));
    }

    public final void removeLogger(Logger logger) {
        mLoggers.remove(logger);
    }
}

