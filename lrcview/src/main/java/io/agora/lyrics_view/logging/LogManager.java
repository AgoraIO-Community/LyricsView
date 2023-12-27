package io.agora.lyrics_view.logging;

import android.util.Log;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogManager {
    private static LogManager mLogManager;

    private final ConcurrentHashMap<Logger, Integer> mLoggers;

    private LogManager() {
        mLoggers = new ConcurrentHashMap<>(3);
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
        Iterator<Logger> it = mLoggers.keySet().iterator();
        while (it.hasNext()) {
            Logger logger = it.next();
            logger.log(Log.WARN, tag, message);
        }
    }

    public void info(String tag, String message) {
        Iterator<Logger> it = mLoggers.keySet().iterator();
        while (it.hasNext()) {
            Logger logger = it.next();
            logger.log(Log.INFO, tag, message);
        }
    }

    public void debug(String tag, String message) {
        Iterator<Logger> it = mLoggers.keySet().iterator();
        while (it.hasNext()) {
            Logger logger = it.next();
            logger.log(Log.DEBUG, tag, message);
        }
    }

    public void error(String tag, String message) {
        Iterator<Logger> it = mLoggers.keySet().iterator();
        while (it.hasNext()) {
            Logger logger = it.next();
            logger.log(Log.ERROR, tag, message);
        }
    }

    private void addLogger(Logger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger must not be null");
        }
        mLoggers.put(logger, Objects.hash(logger));
    }

    private Logger mDefaultLogger;

    public void addHandler(Handler handler) {
        if (mDefaultLogger == null) {
            mDefaultLogger = new DefaultLogger();
        }
        mDefaultLogger.addHandler(handler);
        addLogger(mDefaultLogger);
    }

    private static class DefaultLogger implements Logger {
        private final ExecutorService mThreadPool;

        private final ConcurrentHashMap<Handler, Integer> mHandlers;

        public DefaultLogger() {
            mHandlers = new ConcurrentHashMap<>(3);
            mThreadPool = Executors.newSingleThreadExecutor();
        }

        @Override
        public void addHandler(Handler handler) {
            this.mHandlers.put(handler, Objects.hash(handler));
        }

        @Override
        public void log(int level, String tag, String message) {
            Thread thread = Thread.currentThread();
            String from = "*" + thread.getName() + " " + thread.getPriority() + " " + System.currentTimeMillis() + "*";
            final String msg = from + " " + message;

            mThreadPool.submit(() -> {
                Iterator<Handler> it = mHandlers.keySet().iterator();
                while (it.hasNext()) {
                    Handler logger = it.next();
                    switch (level) {
                        case Log.DEBUG:
                            logger.onDebug(tag, msg);
                            break;
                        case Log.INFO:
                            logger.onInfo(tag, msg);
                            break;
                        case Log.ERROR:
                            logger.onError(tag, msg);
                            break;
                        case Log.WARN:
                            logger.onWarn(tag, msg);
                            break;
                        default:
                            break;
                    }

                }

            });
        }
    }
}
