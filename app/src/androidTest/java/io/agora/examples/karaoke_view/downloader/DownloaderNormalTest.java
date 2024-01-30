package io.agora.examples.karaoke_view.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.constants.DownloadError;
import io.agora.karaoke_view.v11.downloader.LyricsFileDownloader;
import io.agora.karaoke_view.v11.downloader.LyricsFileDownloaderCallback;
import io.agora.logging.ConsoleLogger;
import io.agora.logging.LogManager;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class DownloaderNormalTest {

    private static final String TAG = "karaoke-DownloaderInstrumentedTest";
    private final ConsoleLogger mConsoleLogger = new ConsoleLogger();

    @Test
    public void testDownloadNormal() {
        LogManager.instance().addLogger(mConsoleLogger);

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String[] urls = new String[]{"https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/1.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/2.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/3.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/4.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/5.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/6.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/7.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/8.lrc",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/9.lrc",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/10.lrc"};
        List<Integer> requestIdList = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch = new CountDownLatch(1);
        LyricsFileDownloader.getInstance(appContext).cleanAll();
        LyricsFileDownloader.getInstance(appContext).setMaxFileNum(5);
        LyricsFileDownloader.getInstance(appContext).setLyricsFileDownloaderCallback(new LyricsFileDownloaderCallback() {
            @Override
            public void onLyricsFileDownloadProgress(int requestId, float progress) {
                Log.d(TAG, "onLyricsFileDownloadProgress: requestId: " + requestId + ", requestIdList: " + requestIdList);
                assertTrue(requestIdList.contains(requestId));
            }

            @Override
            public void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error) {
                Log.d(TAG, "onLyricsFileDownloadCompleted: requestId: " + requestId + ", requestIdList: " + requestIdList);

                assertNotNull(fileData);
                assertNull(error);
                assertTrue(requestIdList.contains(requestId));

                requestIdList.remove(Integer.valueOf(requestId));
                if (requestIdList.isEmpty()) {
                    latch.countDown();
                }
            }
        });

        for (String url : urls) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int requestId = LyricsFileDownloader.getInstance(appContext).download(url);
                    requestIdList.add(requestId);
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }).start();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(requestIdList.size(), 0);
        File dirs = new File(appContext.getExternalCacheDir().getPath() + "/" + Constants.LYRICS_FILE_DOWNLOAD_DIR);
        File[] files = dirs.listFiles();
        int fileCount = 0;
        if (null != files) {
            for (File file : files) {
                if (file.isFile()) {
                    fileCount++;
                }
            }
        }
        assertEquals(5, fileCount);
        Log.d(TAG, "testDownloadNormal done");
    }
}
