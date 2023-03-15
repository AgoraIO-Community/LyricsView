package io.agora.karaoke_view.v11.logging;

import android.util.Log;

public class DefaultConsoleLogger implements Logger {
    @Override
    public void onLog(int level, String tag, String message) {
        switch (level) {
            case Log.DEBUG:
                Log.d(tag, message);
                break;
            case Log.ERROR:
                Log.e(tag, message);
                break;
            case Log.INFO:
                Log.i(tag, message);
                break;
            case Log.WARN:
                Log.w(tag, message);
                break;
        }
    }
}
