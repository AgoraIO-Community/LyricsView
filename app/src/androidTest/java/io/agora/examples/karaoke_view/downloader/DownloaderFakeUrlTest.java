package io.agora.examples.karaoke_view.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
public class DownloaderFakeUrlTest {

    private static final String TAG = "karaoke-DownloaderInstrumentedTest";
    private final ConsoleLogger mConsoleLogger = new ConsoleLogger();

    @Test
    public void TestDownloadForFakeUrl() {
        LogManager.instance().addLogger(mConsoleLogger);

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String[] urls = new String[]{"https://127.0.0.1/lyricsMockDownload/1.zip",
                "https://agora.fake.domain.com/lyricsMockDownload/1.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/10000.zip",
                "https://8.141.208.82/lyricsMockDownload/1.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/11.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/11.txt"};

        Map<Integer, DownloadError> requestErrorMap = new ConcurrentHashMap<>(urls.length);

        final CountDownLatch latch = new CountDownLatch(1);
        LyricsFileDownloader.getInstance(appContext).cleanAll();
        LyricsFileDownloader.getInstance(appContext).setMaxFileNum(5);
        LyricsFileDownloader.getInstance(appContext).setLyricsFileDownloaderCallback(new LyricsFileDownloaderCallback() {
            @Override
            public void onLyricsFileDownloadProgress(int requestId, float progress) {
                Log.d(TAG, "onLyricsFileDownloadProgress: requestId: " + requestId + ", requestIdList: " + requestErrorMap);
                assertTrue(requestErrorMap.containsKey(requestId));
            }

            @Override
            public void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error) {
                Log.d(TAG, "onLyricsFileDownloadCompleted: requestId: " + requestId + ", error: " + error);
                assertEquals(requestErrorMap.get(requestId), error);
                assertEquals(error.getErrorCode(), error.getErrorCode());
                requestErrorMap.remove(requestId);
                if (requestErrorMap.size() == 0) {
                    latch.countDown();
                }

            }
        });

        for (int i = 0; i < urls.length; i++) {
            int requestId = LyricsFileDownloader.getInstance(appContext).download(urls[i]);
            DownloadError downloadError = DownloadError.GENERAL;
            switch (i) {
                case 0:
                    downloadError = DownloadError.HTTP_DOWNLOAD_ERROR;
                    downloadError.setErrorCode(Constants.ERROR_HTTP_NOT_CONNECT);
                    break;
                case 1:
                    downloadError = DownloadError.HTTP_DOWNLOAD_ERROR;
                    downloadError.setErrorCode(Constants.ERROR_HTTP_UNKNOWN_HOST);
                    break;
                case 2:
                    downloadError = DownloadError.HTTP_DOWNLOAD_ERROR_LOGIC;
                    downloadError.setErrorCode(404);
                    break;
                case 3:
                    downloadError = DownloadError.HTTP_DOWNLOAD_ERROR;
                    downloadError.setErrorCode(Constants.ERROR_HTTP_TIMEOUT);
                    break;
                case 4:
                    downloadError = DownloadError.UNZIP_FAIL;
                    downloadError.setErrorCode(Constants.ERROR_UNZIP_ERROR);
                    break;
                case 5:
                    downloadError = DownloadError.UNZIP_FAIL;
                    downloadError.setErrorCode(Constants.ERROR_UNZIP_ERROR);
                    break;
            }
            requestErrorMap.put(requestId, downloadError);
        }


        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        Log.d(TAG, "TestDownloadForFakeUrl done");
    }
}
