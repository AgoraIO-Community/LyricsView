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

public class LogUtils {
    private static final List<Logger> LOGGERS = new ArrayList<>(3);

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
                LOGGERS.add(new FileLogger(logPath, Constants.LOG_FILE_NAME, 1024 * 1024, 2, new ArrayList<String>(1) {{
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

    public static void addLogger(Logger logger) {
        LogManager.instance().addLogger(logger);
        LOGGERS.add(logger);
    }

    public static void removeLogger(Logger logger) {
        LogManager.instance().removeLogger(logger);
        LOGGERS.remove(logger);
    }


    public static void destroy() {
        for (Logger logger : LOGGERS) {
            LogManager.instance().removeLogger(logger);
        }
        LOGGERS.clear();
    }


    public static void d(String msg) {
        LogManager.instance().debug(Constants.TAG, msg);
    }

    public static void i(String msg) {
        LogManager.instance().info(Constants.TAG, msg);
    }

    public static void e(String msg) {
        LogManager.instance().error(Constants.TAG, msg);
    }
}
