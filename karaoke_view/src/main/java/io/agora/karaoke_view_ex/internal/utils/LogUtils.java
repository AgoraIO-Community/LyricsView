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
    private static boolean mEnableLog = false;
    private static boolean mSaveLogFile = false;

    private static String mLogPath;
    private static final List<Logger> LOGGERS = new ArrayList<>(3);

    public static void enableLog(Context context, boolean enableLog, boolean saveLogFile, String logFilePath) {
        mEnableLog = enableLog;
        mSaveLogFile = saveLogFile;
        if (mEnableLog) {
            try {
                initLog(context, logFilePath);
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(Constants.TAG, "initLog error:" + e.getMessage());
            }
        }
    }

    public static boolean isEnableLog() {
        return mEnableLog;
    }

    public static String getLogPath() {
        return mLogPath;
    }


    private static void initLog(Context context, String logFilePath) {
        mLogPath = context.getExternalFilesDir(null).getPath();
        if (!TextUtils.isEmpty(logFilePath)) {
            mLogPath = logFilePath;
        }

        LogManager.instance().removeAllLogger();

        LOGGERS.add(new ConsoleLogger());
        if (mSaveLogFile) {
            LOGGERS.add(new FileLogger(mLogPath, Constants.LOG_FILE_NAME, 1024 * 1024, 2, new ArrayList<String>(1) {{
                add(Constants.TAG);
            }}));
        }
        for (Logger logger : LOGGERS) {
            LogManager.instance().addLogger(logger);
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
        if (mEnableLog) {
            LogManager.instance().debug(Constants.TAG, msg);
        }
    }

    public static void i(String msg) {
        if (mEnableLog) {
            LogManager.instance().info(Constants.TAG, msg);
        }
    }

    public static void e(String msg) {
        if (mEnableLog) {
            LogManager.instance().error(Constants.TAG, msg);
        }
    }
}
