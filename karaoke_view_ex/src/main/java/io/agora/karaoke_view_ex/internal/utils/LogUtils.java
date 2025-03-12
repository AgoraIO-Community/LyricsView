package io.agora.karaoke_view_ex.internal.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.logging.ConsoleLogger;
import io.agora.logging.FileLogger;
import io.agora.logging.LogManager;
import io.agora.logging.Logger;

/**
 * Utility class for logging in the karaoke view module.
 * Provides methods for logging at different levels and managing log output destinations.
 */
public class LogUtils {
    /**
     * List of registered loggers
     */
    private static final List<Logger> LOGGERS = new ArrayList<>(3);

    /**
     * Initializes and configures the logging system
     *
     * @param context     The application context
     * @param enableLog   Whether to enable console logging
     * @param saveLogFile Whether to save logs to a file
     * @param logFilePath Custom path for log files, or null to use default
     */
    public static void enableLog(Context context, boolean enableLog, boolean saveLogFile, String logFilePath) {
        try {
            String logPath = logFilePath;
            if (TextUtils.isEmpty(logPath)) {
                logPath = context.getExternalFilesDir(null).getPath();
            }

            destroy();

            if (enableLog) {
                LOGGERS.add(new ConsoleLogger());
            }

            if (saveLogFile) {
                LOGGERS.add(new FileLogger(logPath, Constants.LOG_FILE_NAME, 1024 * 1024, 4, new ArrayList<String>(1) {{
                    add(Constants.TAG);
                }}));
            }
            for (Logger logger : LOGGERS) {
                LogManager.instance().addLogger(logger);
            }
        } catch (Exception e) {
            Log.i(Constants.TAG, "initLog error:" + e.getMessage());
        }
    }

    /**
     * Adds a custom logger to the logging system
     *
     * @param logger The logger to add
     */
    public static void addLogger(Logger logger) {
        LogManager.instance().addLogger(logger);
        LOGGERS.add(logger);
    }

    /**
     * Removes a logger from the logging system
     *
     * @param logger The logger to remove
     */
    public static void removeLogger(Logger logger) {
        LogManager.instance().removeLogger(logger);
        LOGGERS.remove(logger);
    }

    /**
     * Destroys all loggers and cleans up resources
     */
    public static void destroy() {
        for (Logger logger : LOGGERS) {
            LogManager.instance().removeLogger(logger);
        }
        LOGGERS.clear();
    }

    /**
     * Logs a debug message
     *
     * @param msg The message to log
     */
    public static void d(String msg) {
        LogManager.instance().debug(Constants.TAG, msg);
    }

    /**
     * Logs an info message
     *
     * @param msg The message to log
     */
    public static void i(String msg) {
        LogManager.instance().info(Constants.TAG, msg);
    }

    /**
     * Logs an error message
     *
     * @param msg The message to log
     */
    public static void e(String msg) {
        LogManager.instance().error(Constants.TAG, msg);
    }
}
