package io.agora.examples.karaoke_view.downloader;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
public class DownloaderRepeatTest {

    private static final String TAG = "karaoke-DownloaderInstrumentedTest";
    private final ConsoleLogger mConsoleLogger = new ConsoleLogger();

    @Test
    public void testDownloadRepeat() {
        LogManager.instance().addLogger(mConsoleLogger);

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String[] urls = new String[]{"https://solutions-apaas.agora.io/rte-ktv/0609f0627e114a669008d26e312f7613.zip"};
        List<Integer> requestIdList = new ArrayList<>(urls.length);
        final CountDownLatch latch = new CountDownLatch(1);
        LyricsFileDownloader.getInstance(appContext).cleanAll();
        LyricsFileDownloader.getInstance(appContext).setMaxFileNum(5);
        LyricsFileDownloader.getInstance(appContext).setLyricsFileDownloaderCallback(new LyricsFileDownloaderCallback() {
            @Override
            public void onLyricsFileDownloadProgress(int requestId, float progress) {
                assertTrue(requestIdList.contains(requestId));
            }

            @Override
            public void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error) {
                Log.d(TAG, "onLyricsFileDownloadCompleted: requestId: " + requestId + ", error: " + error);
                if (DownloadError.REPEAT_DOWNLOADING == error) {
                    requestIdList.remove(Integer.valueOf(requestId));
                    latch.countDown();
                }
            }
        });

        requestIdList.add(LyricsFileDownloader.getInstance(appContext).download(urls[0]));
        requestIdList.add(LyricsFileDownloader.getInstance(appContext).download(urls[0]));


        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        LyricsFileDownloader.getInstance(appContext).cancelDownload(requestIdList.get(0));

        Log.d(TAG, "testDownloadRepeat done");
    }
}
