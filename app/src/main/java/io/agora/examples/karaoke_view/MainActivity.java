package io.agora.examples.karaoke_view;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.agora.examples.utils.ResourceHelper;
import io.agora.karaoke_view.DownloadManager;

import io.agora.karaoke_view.v11.KaraokeEvent;
import io.agora.karaoke_view.v11.KaraokeView;
import io.agora.karaoke_view.v11.LyricsView;
import io.agora.karaoke_view.v11.ScoringView;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    private KaraokeView mKaraokeView;

    private int mCurrentIndex = 0;

    private LyricsModel mLyricsModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.clear_cache).setOnClickListener(this);
        findViewById(R.id.play).setOnClickListener(this);
        LyricsView lyricsView = findViewById(R.id.lrc_view);
        ScoringView scoringView = findViewById(R.id.scoring_view);
        mKaraokeView = new KaraokeView(lyricsView, scoringView);

        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches) {
                mKaraokeView.setPitch(refPitch);
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulatedScore, int index, int total) {

            }
        });

        loadTheLyrics(LyricsResourcePool.LRC_SAMPLE_1);
    }

    private void loadTheLyrics(String lrcSample) {
        mKaraokeView.reset();
        if (lrcSample.startsWith("https://") || lrcSample.startsWith("http://")) {
            DownloadManager.getInstance().download(this, lrcSample, file -> {
                file = extractFromZipFileIfPossible(file);
                mLyricsModel = KaraokeView.parseLyricsData(file);
                mKaraokeView.setLyricsData(mLyricsModel);
            }, Throwable::printStackTrace);
        } else {
            File file = ResourceHelper.copyAssetsToCreateNewFile(getApplicationContext(), lrcSample);
            file = extractFromZipFileIfPossible(file);
            mLyricsModel = KaraokeView.parseLyricsData(file);
            mKaraokeView.setLyricsData(mLyricsModel);
        }
    }

    private static File extractFromZipFileIfPossible(File file) {
        if (!(file.isFile() && file.getName().endsWith(".zip"))) {
            return file;
        }

        if (file.length() >= 1 * 1024 * 1024) { // Too large file, we do not support
            throw new AndroidRuntimeException("Too large lyrics file, we do not support so far " + file.length());
        }

        ByteArrayOutputStream byteArrayOutputStream = null;
        FileInputStream fis;
        // buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(file);
            ZipInputStream zis = new ZipInputStream(fis);
            byteArrayOutputStream = new ByteArrayOutputStream();
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                byteArrayOutputStream.flush();
                // close this ZipEntry
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
            byteArrayOutputStream.close();
            // close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        file.delete();
        String path = file.getAbsolutePath().replaceAll(".zip", ".xml");

        File realFile = new File(path);
        try {
            FileOutputStream fos = new FileOutputStream(realFile);
            fos.write(byteArrayOutputStream.toByteArray());
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return realFile;
    }

    private void doClearCacheAndLoadTheLyrics() {
        DownloadManager.getInstance().clearCache(this);

        loadTheLyrics(LyricsResourcePool.asList().get(mCurrentIndex));

        mCurrentIndex++;

        if (mCurrentIndex >= LyricsResourcePool.asList().size()) {
            mCurrentIndex = 0;
        }
    }

    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private long mCurrentPosition = 0;
    private ScheduledFuture mFuture;

    private void doMockPlay() {
        final long DURATION_OF_SONG = mLyricsModel.lines.get(mLyricsModel.lines.size() - 1).getEndTime();
        mCurrentPosition = 0;
        final String PLAYER_TAG = TAG + "_MockPlayer";
        Log.d(PLAYER_TAG, "duration: " + DURATION_OF_SONG + ", position: " + mCurrentPosition);
        if (mFuture != null) {
            mFuture.cancel(true);
        }

        mFuture = mExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (mCurrentPosition >= 0 && mCurrentPosition < DURATION_OF_SONG) {
                    mKaraokeView.setProgress(mCurrentPosition);
                    Log.d(PLAYER_TAG, "timer mCurrentPosition: " + mCurrentPosition);
                } else if (mCurrentPosition >= DURATION_OF_SONG && mCurrentPosition < (DURATION_OF_SONG + 1000)) {
                    long lastPosition = mCurrentPosition;
                    mKaraokeView.setProgress(0);
                    mKaraokeView.setPitch(0);
                    Log.d(PLAYER_TAG, "put the pivot back in space");
                    // Put the pivot back in space
                } else if (mCurrentPosition >= (DURATION_OF_SONG + 1000)) {
                    if (mFuture != null) {
                        mFuture.cancel(true);
                    }
                    mCurrentPosition = 0;
                    Log.d(PLAYER_TAG, "quit");
                    return;
                }
                mCurrentPosition += 20;
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.clear_cache:
                mCurrentPosition = 0; // Replay if already playing
                doClearCacheAndLoadTheLyrics();
                break;
            case R.id.play:
                doMockPlay();
                break;
            default:
                break;
        }
    }
}