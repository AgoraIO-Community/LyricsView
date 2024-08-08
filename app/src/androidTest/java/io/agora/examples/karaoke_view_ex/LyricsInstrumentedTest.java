package io.agora.examples.karaoke_view_ex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.agora.examples.utils.Utils;
import io.agora.karaoke_view_ex.KaraokeView;
import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.constants.DownloadError;
import io.agora.karaoke_view_ex.downloader.LyricsFileDownloader;
import io.agora.karaoke_view_ex.downloader.LyricsFileDownloaderCallback;
import io.agora.karaoke_view_ex.internal.ScoringMachine;
import io.agora.karaoke_view_ex.internal.ai.AIAlgorithmScoreNative;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.lyric.parse.LyricPitchParser;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.model.LyricModel;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class LyricsInstrumentedTest {

    private static final String TAG = "LyricsViewEx-LyricsInstrumentedTest";
    private KaraokeView mKaraokeView;

    private void enableLyricViewExLog() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LogUtils.enableLog(appContext, true, true, null);
    }

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("io.agora.examples.karaoke_view_ex", appContext.getPackageName());
        mKaraokeView = new KaraokeView(appContext);
        enableLyricViewExLog();
    }

    @Test
    public void parseOneAndOnlyOneLineXmlFile() {
        // specified to 810507.xml

        String fileNameOfSong = "810507.xml";
        String songArtist = "张学友";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String oneAndOnlyOneLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(oneAndOnlyOneLineXmlFileContent.contains(songArtist));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        // 810507.xml has 42 lines
        assertEquals(42, parsedLyrics.lines.size());

        // The 7th line contains '泪' '慢' '慢' '流' '慢' '慢' '收'
        LyricsLineModel the7thLine = parsedLyrics.lines.get(6);
        long startOf7thLine = parsedLyrics.lines.get(6).getStartTime();
        long endOf7thLine = parsedLyrics.lines.get(6).getEndTime();
        assertTrue(endOf7thLine - startOf7thLine > 0);
        assertTrue(TextUtils.equals("泪", the7thLine.tones.get(0).word) && the7thLine.tones.get(0).pitch == 176);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(1).word) && the7thLine.tones.get(1).pitch == 0);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(2).word) && the7thLine.tones.get(2).pitch == 176);
        assertTrue(TextUtils.equals("流", the7thLine.tones.get(3).word) && the7thLine.tones.get(3).pitch == 158);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(4).word) && the7thLine.tones.get(4).pitch == 125);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(5).word) && the7thLine.tones.get(5).pitch == 159);
        assertTrue(TextUtils.equals("收", the7thLine.tones.get(6).word) && the7thLine.tones.get(6).pitch == 150);

        // The 41th line contains '你' '何' '忍' '远' '走' '高' '飞'
        LyricsLineModel the41thLine = parsedLyrics.lines.get(40);
        long startOf41thLine = parsedLyrics.lines.get(40).getStartTime();
        long endOf41thLine = parsedLyrics.lines.get(40).getEndTime();
        assertTrue(endOf41thLine - startOf41thLine > 0);
        assertTrue(TextUtils.equals("你", the41thLine.tones.get(0).word) && the41thLine.tones.get(0).pitch == 0);
        assertTrue(TextUtils.equals("何", the41thLine.tones.get(1).word) && the41thLine.tones.get(1).pitch == 0);
        assertTrue(TextUtils.equals("忍", the41thLine.tones.get(2).word) && the41thLine.tones.get(2).pitch == 0);
        assertTrue(TextUtils.equals("远", the41thLine.tones.get(3).word) && the41thLine.tones.get(3).pitch == 0);
        assertTrue(TextUtils.equals("走", the41thLine.tones.get(4).word) && the41thLine.tones.get(4).pitch == 0);
        assertTrue(TextUtils.equals("高", the41thLine.tones.get(5).word) && the41thLine.tones.get(5).pitch == 0);
        assertTrue(TextUtils.equals("飞", the41thLine.tones.get(6).word) && the41thLine.tones.get(6).pitch == 0);
    }

    @Test
    public void parseMetadataForThisLyrics() {
        // specified to
        String fileNameOfSong = "c18228e223144247810ee511916e2207.xml";
        String songTitle = "路边的野花不要采";
        String songArtist = "邓丽君";
        int expectedNumberOfLines = 20;

        long expectedpreludeEndPosition = (long) (13.0600 * 1000);
        long expectedDuration = (long) (113.0414 * 1000);

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String oneAndOnlyOneLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(oneAndOnlyOneLineXmlFileContent.contains(songArtist));
        assertTrue(oneAndOnlyOneLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        assertEquals(songTitle, parsedLyrics.name);
        assertEquals(songArtist, parsedLyrics.singer);
        assertEquals(expectedpreludeEndPosition, parsedLyrics.preludeEndPosition);
        assertEquals(expectedDuration, parsedLyrics.duration);
        assertEquals(expectedNumberOfLines, parsedLyrics.lines.size());

        Log.d(TAG, "Metadata for this lyrics, numberOfLines: " + parsedLyrics.lines.size() + ", title: " + parsedLyrics.name + ", singer: " + parsedLyrics.singer + ", preludeEndPosition: " + parsedLyrics.preludeEndPosition + ", duration: " + parsedLyrics.duration);
    }

    @Test
    public void unexpectedContentCheckingForLyrics() {

        File target;
        LyricModel parsedLyrics;

        String fileNameOfSong;
        String songTitle;
        String songArtist;

        // specified to
        fileNameOfSong = "237732-empty-content.xml";
        songTitle = "不是因为寂寞才想你(Empty Content)";
        songArtist = "AI";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String lyricsContentInString = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        assertEquals(null, parsedLyrics);

        // specified to
        fileNameOfSong = "237732-empty-content-2.xml";
        songTitle = "不是因为寂寞才想你(Empty Content)";
        songArtist = "AI";

        lyricsContentInString = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        assertEquals(null, parsedLyrics);

        // specified to
        fileNameOfSong = "237732-invalid-content.xml";
        songTitle = "不是因为寂寞才想你(Invalid Content)";
        songArtist = "AI";

        lyricsContentInString = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        assertEquals(null, parsedLyrics);

        // specified to
        fileNameOfSong = "237732-invalid-content-2.xml";
        songTitle = "不是因为寂寞才想你(Invalid Content)";
        songArtist = "AI";

        lyricsContentInString = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        assertEquals(null, parsedLyrics);
    }

    @Test
    public void lineSeparating() {
        enableLyricViewExLog();
        String fileNameOfSong;
        String songTitle;
        int expectedNumberOfLines;

        fileNameOfSong = "825003.xml"; // May contains same timestamp for start and previous end
        songTitle = "净化空间";
        expectedNumberOfLines = 30;
        fullyPlayASong(fileNameOfSong, songTitle, expectedNumberOfLines, 20);

        fileNameOfSong = "237732.xml"; // Long blank between two lines(line 18 & line 19)
        songTitle = "不是因为寂寞才想你";
        expectedNumberOfLines = 50;
        fullyPlayASong(fileNameOfSong, songTitle, expectedNumberOfLines, 20);

        fileNameOfSong = "237732-modified-for-testing.xml"; // Empty/No/Invalid pitches/Prompt
        songTitle = "不是因为寂寞才想你(ModifiedForTesting)";
        expectedNumberOfLines = 18;
        fullyPlayASong(fileNameOfSong, songTitle, expectedNumberOfLines, 20);
    }

    private void fullyPlayASong(String fileNameOfSong, String songTitle, int expectedNumberOfLines, int interval) {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        assertTrue(parsedLyrics.preludeEndPosition >= 0);

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + index + " " + lineCount);
                mNumberOfScoringLines++;
                mLatestIndexOfScoringLines = index;

                assertEquals(parsedLyrics.lines.get(index).getStartTime(), line.getStartTime());
                assertEquals(parsedLyrics.lines.get(index).getEndTime(), line.getEndTime());
                assertTrue(mCurrentPosition - line.getEndTime() <= interval); // `onLineFinished` should immediately(in 50 milliseconds) come back when line finish

            }


            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }


            @Override
            public void onPitchAndScoreUpdate(float speakerPitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + speakerPitch + " " + scoreAfterNormalization + " " + progress);

            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });


        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics, true);
        mockPlay(parsedLyrics, scoringMachine, 20);
        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(expectedNumberOfLines, lineCount);

        // Check if `onLineFinished` working as expected
        assertEquals(lineCount, mNumberOfScoringLines);
        assertEquals(lineCount, mLatestIndexOfScoringLines + 1);
    }

    @Test
    public void testForScoring() {
        enableLyricViewExLog();
        String fileNameOfSong;
        String songTitle;
        int expectedNumberOfLines;

        fileNameOfSong = "151675-fake-for-line-scoring.xml";
        songTitle = "FakedForLineScoring";
        expectedNumberOfLines = 11;
        scoreASong(fileNameOfSong, songTitle, expectedNumberOfLines, 20);
        scoreASong(fileNameOfSong, songTitle, expectedNumberOfLines, 50);
        scoreASong(fileNameOfSong, songTitle, expectedNumberOfLines, 80);
    }

    private ScoringMachine mScoringMachineTestForScoring;

    private void scoreASong(String fileNameOfSong, String songTitle, int expectedNumberOfLines, int interval) {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        mScoringMachineTestForScoring = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                mNumberOfScoringLines++;
                mLatestIndexOfScoringLines = index;
                Log.d(TAG, "onLineFinished " + score + " " + cumulativeScore + ", index=" + index + ", lineCount=" + lineCount + ", mNumberOfScoringLines=" + mNumberOfScoringLines);

                assertEquals(parsedLyrics.lines.get(index).getStartTime(), line.getStartTime());
                assertEquals(parsedLyrics.lines.get(index).getEndTime(), line.getEndTime());
                assertTrue(mCurrentPosition - line.getEndTime() <= interval); // `onLineFinished` should immediately(such as in 50 milliseconds) come back when line finish
                assertTrue(score >= 0); // Output
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onPitchAndScoreUpdate(float speakerPitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + speakerPitch + " " + scoreAfterNormalization + " " + progress);
                assertTrue(scoreAfterNormalization >= 0); // Output
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        mScoringMachineTestForScoring.prepare(parsedLyrics, true);
        mockPlay(parsedLyrics, mScoringMachineTestForScoring, interval);
        Log.d(TAG, "Started at " + new Date(startTsOfTest) + " with interval " + interval + "ms , taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(expectedNumberOfLines, lineCount);

        // Check if `onLineFinished` working as expected
        assertEquals(lineCount, mNumberOfScoringLines);
        assertEquals(lineCount, mLatestIndexOfScoringLines + 1);
    }

    @Test
    public void testFirst5Lines() {
        // specified to 825003.xml
        // 825003.xml has 30 lines
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + index + " " + lineCount);
                mNumberOfScoringLines++;
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }


            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + progress);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics, true);

        // Only first 5 lines
        for (LyricsLineModel line : parsedLyrics.lines) {
            mLatestIndexOfScoringLines++;
            for (LyricsLineModel.Tone tone : line.tones) {
                scoringMachine.setPitch((float) (tone.pitch - 1), (int) (tone.begin + tone.getDuration() / 2));
            }

            if (mLatestIndexOfScoringLines >= 6) {
                break;
            }
        }

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);

        // Check if `onLineFinished` working as expected
        assertEquals(mNumberOfScoringLines, 5);
        assertEquals(mLatestIndexOfScoringLines, 6);
    }

    private float mPitchHit = 0;

    @Test
    public void testPitchHit() {
        // specified to 825003.xml
        // 825003.xml has 30 lines
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }


        ScoringMachine scoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + index + " " + lineCount);
            }


            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + progress);
                mPitchHit = pitch;
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics, true);

        mPitchHit = -1;
        scoringMachine.setPitch(0, 0);
        assertEquals(mPitchHit, -1, 0d);

        mPitchHit = -1;
        scoringMachine.setPitch(0, 28813);
        assertEquals(mPitchHit, -1, 0d);

        mPitchHit = -1;
        scoringMachine.setPitch(172, 28814);
        assertEquals(mPitchHit, 172, 0d);

        mPitchHit = -1;
        scoringMachine.setPitch(172, 29675);
        assertEquals(mPitchHit, 172, 0d);

        mPitchHit = -1;
        scoringMachine.setPitch(130, 185160);
        assertEquals(mPitchHit, 130, 0d);

        mPitchHit = -1;
        scoringMachine.setPitch(213, 185161);
        assertEquals(mPitchHit, 213, 0d);

        mPitchHit = -1;
        scoringMachine.setPitch(100, 187238);
        double processedPitch = AIAlgorithmScoreNative.handlePitch(213, 100, scoringMachine.getMaximumRefPitch());
        assertEquals(200.0, processedPitch, 0);
        assertEquals(200.0, mPitchHit, 0.01); // 120.33999633789062

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);
    }

    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private long mCurrentPosition = 0;
    private int mNumberOfScoringLines = 0;
    private int mLatestIndexOfScoringLines = 0;
    private int mFinalCumulativeScore = 0;
    private ScheduledFuture mFuture;

    private void mockPlay(final LyricModel model, final ScoringMachine scoringMachine, int interval) {
        final long DURATION_OF_SONG = model.lines.get(model.lines.size() - 1).getEndTime();
        mCurrentPosition = 0;
        final String PLAYER_TAG = TAG + "_MockPlayer";
        Log.d(PLAYER_TAG, "duration: " + DURATION_OF_SONG + ", position: " + mCurrentPosition + ", interval: " + interval);
        if (mFuture != null) {
            mFuture.cancel(true);
        }

        CountDownLatch latch = new CountDownLatch(1);

        mFuture = mExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (mCurrentPosition >= 0 && mCurrentPosition < DURATION_OF_SONG) {
                    float pitch = 0;
                    pitch = (float) Math.random() * 200;
                    scoringMachine.setPitch(pitch, (int) mCurrentPosition);
                    Log.d(PLAYER_TAG, "mCurrentPosition: " + mCurrentPosition + ", pitch: " + pitch);
                } else if (mCurrentPosition >= DURATION_OF_SONG && mCurrentPosition < (DURATION_OF_SONG + 1000)) {
                    long lastPosition = mCurrentPosition;
                    scoringMachine.setPitch((float) 0, (int) mCurrentPosition);
                    Log.d(PLAYER_TAG, "put the indicator back in space");
                    // Put the indicator back in space
                } else if (mCurrentPosition >= (DURATION_OF_SONG + 1000)) {
                    if (mFuture != null) {
                        mFuture.cancel(true);
                    }
                    mCurrentPosition = 0;
                    scoringMachine.reset();
                    latch.countDown();
                    Log.d(PLAYER_TAG, "quit");
                    return;
                }
                mCurrentPosition += interval;
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(PLAYER_TAG, "Song finished");
    }

    @Test
    public void findNonPerfectXML() {
        String fileNameOfSong;
        String songTitle;
        int expectedNumberOfLines;

        fileNameOfSong = "147383.xml";
        songTitle = "光辉岁月";
        expectedNumberOfLines = 48;
        showWarningsForLyricsFile(fileNameOfSong, songTitle, expectedNumberOfLines);

        fileNameOfSong = "660078.xml";
        songTitle = "遇见";
        expectedNumberOfLines = 25;
        showWarningsForLyricsFile(fileNameOfSong, songTitle, expectedNumberOfLines);

        fileNameOfSong = "826125.xml";
        songTitle = "恋歌";
        expectedNumberOfLines = 26;
        showWarningsForLyricsFile(fileNameOfSong, songTitle, expectedNumberOfLines);

        fileNameOfSong = "793566.xml";
        songTitle = "感谢你曾来过";
        expectedNumberOfLines = 86;
        showWarningsForLyricsFile(fileNameOfSong, songTitle, expectedNumberOfLines);
    }

    private void showWarningsForLyricsFile(String fileNameOfSong, String songTitle, int expectedNumberOfLines) {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        int indexOfLine = 0;
        for (LyricsLineModel line : parsedLyrics.lines) {
            for (int indexOfPitch = 0; indexOfPitch < line.tones.size(); indexOfPitch++) {
                LyricsLineModel.Tone pitch = line.tones.get(indexOfPitch);
                int indexOfPreviousPitch = indexOfPitch - 1;
                LyricsLineModel.Tone previousPitch;
                if (indexOfPreviousPitch >= 0) {
                    previousPitch = line.tones.get(indexOfPreviousPitch);
                    if (previousPitch.end >= pitch.begin) {
                        Log.w(TAG, "Wrong begin/end for pitch " + indexOfPitch + " and " + indexOfPreviousPitch + " at line " + indexOfLine);
                    }
                }
            }

            int indexOfPreviousLine = indexOfLine - 1;
            LyricsLineModel previousLine;
            if (indexOfPreviousLine >= 0) {
                previousLine = parsedLyrics.lines.get(indexOfPreviousLine);
                if (previousLine.getEndTime() >= line.getStartTime()) {
                    Log.w(TAG, "Wrong start/end for line " + indexOfLine + " and " + indexOfPreviousLine);
                }
            }
            Log.d(TAG, "Line(" + indexOfLine + ")" + "summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
            indexOfLine++;
        }

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);
    }

    @Test
    public void parseLrcAndPitchFile() {
        String fileNameOfSong;
        String fileNameOfPitch;
        String songTitle;
        int expectedNumberOfLines;

        fileNameOfSong = "6246262727282260.lrc";
        fileNameOfPitch = "6246262727282260.bin";
        expectedNumberOfLines = 60;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        File pitches = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfPitch);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(lyrics, pitches, true, 0);

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);
    }

    @Test
    public void testHandlePitchForAi() {
        assertEquals(0d, AIAlgorithmScoreNative.handlePitch(0, 0, 400), 0d);
        assertEquals(0d, AIAlgorithmScoreNative.handlePitch(1, 0, 400), 0d);
        assertEquals(1d, AIAlgorithmScoreNative.handlePitch(1, 1, 400), 0d);
        assertEquals(90d, AIAlgorithmScoreNative.handlePitch(100, 90, 400), 0d);
        assertEquals(80d, AIAlgorithmScoreNative.handlePitch(100, 80, 400), 0d);
        assertEquals(160d, AIAlgorithmScoreNative.handlePitch(200, 80, 400), 0d);
        assertEquals(320d, AIAlgorithmScoreNative.handlePitch(400, 80, 400), 0d);
        assertEquals(400d, AIAlgorithmScoreNative.handlePitch(400, 200, 400), 0d);
        assertEquals(400d, AIAlgorithmScoreNative.handlePitch(400, 400, 400), 0d);
        assertEquals(500d, AIAlgorithmScoreNative.handlePitch(400, 500, 400), 0d);
        assertEquals(300d, AIAlgorithmScoreNative.handlePitch(400, 600, 400), 0d);
        assertEquals(350d, AIAlgorithmScoreNative.handlePitch(400, 700, 400), 0d);
        assertEquals(400d, AIAlgorithmScoreNative.handlePitch(400, 800, 400), 0d);
    }

    @Test
    public void testCalculatedScoreForAi() {
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(201, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(200, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(190, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(180, 100, 10, 0), 0.01f);
        assertEquals(9.763043f, AIAlgorithmScoreNative.calculatedScore(170, 100, 10, 0), 0.01f);
        assertEquals(22.357689f, AIAlgorithmScoreNative.calculatedScore(160, 100, 10, 0), 0.01f);
        assertEquals(35.765438f, AIAlgorithmScoreNative.calculatedScore(150, 100, 10, 0), 0.01f);
        assertEquals(50.098568f, AIAlgorithmScoreNative.calculatedScore(140, 100, 10, 0), 0.01f);
        assertEquals(65.494354f, AIAlgorithmScoreNative.calculatedScore(130, 100, 10, 0), 0.01f);
        assertEquals(82.12307f, AIAlgorithmScoreNative.calculatedScore(120, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AIAlgorithmScoreNative.calculatedScore(110, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AIAlgorithmScoreNative.calculatedScore(101, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AIAlgorithmScoreNative.calculatedScore(100, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AIAlgorithmScoreNative.calculatedScore(99, 100, 10, 0), 0.01f);
        assertEquals(73.64238f, AIAlgorithmScoreNative.calculatedScore(80, 100, 10, 0), 0.01f);
        assertEquals(45.901512f, AIAlgorithmScoreNative.calculatedScore(70, 100, 10, 0), 0.01f);
        assertEquals(13.877029f, AIAlgorithmScoreNative.calculatedScore(60, 100, 10, 0), 0.01f);
        assertEquals(10.3853855f, AIAlgorithmScoreNative.calculatedScore(59, 100, 10, 0), 0.01f);
        assertEquals(6.834053f, AIAlgorithmScoreNative.calculatedScore(58, 100, 10, 0), 0.01f);
        assertEquals(3.2209551f, AIAlgorithmScoreNative.calculatedScore(57, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(56, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(55, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(50, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(40, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(30, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(20, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(10, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AIAlgorithmScoreNative.calculatedScore(1, 100, 10, 0), 0.01f);
    }

    @Test
    public void testCalculatedScoreForScoreCompensationOffset() {
        assertEquals(73.64238f, AIAlgorithmScoreNative.calculatedScore(80, 100, 10, 0), 0.01f);
        assertEquals(85.64239f, AIAlgorithmScoreNative.calculatedScore(80, 100, 10, 10), 0.01f);
        assertEquals(100.0f, AIAlgorithmScoreNative.calculatedScore(80, 100, 10, 50), 0.01f);
        assertEquals(100.0f, AIAlgorithmScoreNative.calculatedScore(80, 100, 10, 100), 0.01f);
    }

    @Test
    public void testMockScoring() {
        enableLyricViewExLog();
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        assertTrue(parsedLyrics.preludeEndPosition >= 0);

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        mFinalCumulativeScore = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished line:" + line + " score:" + score + " cumulativeScore:" + cumulativeScore + " index:" + index + " numberOfLines:" + numberOfLines);
                mNumberOfScoringLines++;
                mFinalCumulativeScore = cumulativeScore;
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }


            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate pitch:" + pitch + " scoreAfterNormalization:" + scoreAfterNormalization + " progress:" + progress);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics, true);

        // Only first 5 lines
        for (LyricsLineModel line : parsedLyrics.lines) {
            mLatestIndexOfScoringLines++;
            for (LyricsLineModel.Tone tone : line.tones) {
                scoringMachine.setPitch((float) (tone.pitch - 1), (int) (tone.begin + tone.getDuration() / 2));
            }

            if (mLatestIndexOfScoringLines >= 6) {
                break;
            }
        }

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);

        // Check if `onLineFinished` working as expected
        assertEquals(mNumberOfScoringLines, 5);
        assertEquals(mLatestIndexOfScoringLines, 6);
        assertEquals(mFinalCumulativeScore, 500);
    }

    @Test
    public void testMockScoring2() {
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = Utils.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel parsedLyrics = LyricPitchParser.parseFile(target, null, true, 0);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        assertTrue(parsedLyrics.preludeEndPosition >= 0);

        mFinalCumulativeScore = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished line:" + line + " score:" + score + " cumulativeScore:" + cumulativeScore + " index:" + index + " numberOfLines:" + numberOfLines);
                mFinalCumulativeScore = cumulativeScore;
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate pitch:" + pitch + " scoreAfterNormalization:" + scoreAfterNormalization + " progress:" + progress);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics, true);

        LyricsLineModel firstLine = parsedLyrics.lines.get(0);
        long time = firstLine.getStartTime();
        int gap = 0;
        while (time <= firstLine.getEndTime()) {
            if (gap == 40) {
                gap = 0;
                scoringMachine.setPitch(50F, (int) time);
            }
            gap += 20;
            time += 20;
        }
        scoringMachine.setPitch(50F, (int) time);

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        assertEquals(66, mFinalCumulativeScore);
    }


    @Test
    public void testDownload() {
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
        LyricsFileDownloader.getInstance(appContext).setMaxFileAge(8 * 60 * 60);
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
        LyricsFileDownloader.getInstance(appContext).cleanAll();


        //TestDownloadForFakeUrl
        urls = new String[]{"https://127.0.0.1/lyricsMockDownload/1.zip",
                "https://agora.fake.domain.com/lyricsMockDownload/1.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/10000.zip",
                "https://8.141.208.82/lyricsMockDownload/1.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/11.zip",
                "https://fullapp.oss-cn-beijing.aliyuncs.com/lyricsMockDownload/11.txt"};

        Map<Integer, DownloadError> requestErrorMap = new ConcurrentHashMap<>(urls.length);
        requestIdList.clear();
        final CountDownLatch latch2 = new CountDownLatch(1);
        LyricsFileDownloader.getInstance(appContext).cleanAll();
        LyricsFileDownloader.getInstance(appContext).setMaxFileNum(5);
        LyricsFileDownloader.getInstance(appContext).setMaxFileAge(8 * 60 * 60);
        LyricsFileDownloader.getInstance(appContext).setLyricsFileDownloaderCallback(new LyricsFileDownloaderCallback() {
            @Override
            public void onLyricsFileDownloadProgress(int requestId, float progress) {
                Log.d(TAG, "TestDownloadForFakeUrl onLyricsFileDownloadProgress: requestId: " + requestId + ", requestIdList: " + requestErrorMap);
                assertTrue(requestErrorMap.containsKey(requestId));
            }

            @Override
            public void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error) {
                Log.d(TAG, "TestDownloadForFakeUrl onLyricsFileDownloadCompleted: requestId: " + requestId + ", error: " + error);
                assertEquals(requestErrorMap.get(requestId), error);
                assertEquals(error.getErrorCode(), error.getErrorCode());
                requestErrorMap.remove(requestId);
                if (requestErrorMap.isEmpty()) {
                    latch2.countDown();
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
            latch2.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "TestDownloadForFakeUrl error: " + e.getMessage());
        }

        assertEquals(requestErrorMap.size(), 0);

        LyricsFileDownloader.getInstance(appContext).cleanAll();

        //testDownloadFileAge
        urls = new String[]{"https://d1n8x1oristvw.cloudfront.net/song_resource/20220705/7b95e6e99afb4d099bca10cc5e3f74a0.xml",
                "https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/meta/demo/fulldemoStatic/privacy/loadFil.xml"};
        requestIdList.clear();
        final CountDownLatch latch3 = new CountDownLatch(1);
        LyricsFileDownloader.getInstance(appContext).cleanAll();
        LyricsFileDownloader.getInstance(appContext).setMaxFileNum(5);
        LyricsFileDownloader.getInstance(appContext).setMaxFileAge(1);
        LyricsFileDownloader.getInstance(appContext).setLyricsFileDownloaderCallback(new LyricsFileDownloaderCallback() {
            @Override
            public void onLyricsFileDownloadProgress(int requestId, float progress) {
                Log.d(TAG, "testDownloadFileAge onLyricsFileDownloadProgress: requestId: " + requestId + ", requestIdList: " + requestIdList);
                assertTrue(requestIdList.contains(requestId));
            }

            @Override
            public void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error) {
                Log.d(TAG, "testDownloadFileAge onLyricsFileDownloadCompleted: requestId: " + requestId + ", requestIdList: " + requestIdList);

                assertNotNull(fileData);
                assertNull(error);
                assertTrue(requestIdList.contains(requestId));

                requestIdList.remove(Integer.valueOf(requestId));

                latch3.countDown();
            }
        });

        int requestId = LyricsFileDownloader.getInstance(appContext).download(urls[0]);
        requestIdList.add(requestId);


        try {
            latch3.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        try {
            Thread.sleep(3 * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestId = LyricsFileDownloader.getInstance(appContext).download(urls[1]);
        requestIdList.add(requestId);

        assertEquals(requestIdList.size(), 1);
        LyricsFileDownloader.getInstance(appContext).cancelDownload(requestIdList.get(0));

        dirs = new File(appContext.getExternalCacheDir().getPath() + "/" + Constants.LYRICS_FILE_DOWNLOAD_DIR);
        files = dirs.listFiles();
        fileCount = 0;
        if (null != files) {
            for (File file : files) {
                if (file.isFile()) {
                    fileCount++;
                }
            }
        }
        assertEquals(0, fileCount);
        assertEquals(requestErrorMap.size(), 0);

        LyricsFileDownloader.getInstance(appContext).cleanAll();


        //testDownloadRepeat
        urls = new String[]{"https://solutions-apaas.agora.io/rte-ktv/0609f0627e114a669008d26e312f7613.zip"};
        requestIdList.clear();
        final CountDownLatch latch4 = new CountDownLatch(1);
        LyricsFileDownloader.getInstance(appContext).cleanAll();
        LyricsFileDownloader.getInstance(appContext).setMaxFileNum(5);
        LyricsFileDownloader.getInstance(appContext).setMaxFileAge(8 * 60 * 60);
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
                } else {
                    assertNotNull(fileData);
                    assertNull(error);
                    assertTrue(requestIdList.contains(requestId));
                    requestIdList.remove(Integer.valueOf(requestId));
                    if (requestIdList.isEmpty()) {
                        latch4.countDown();
                    }

                }
            }
        });

        requestIdList.add(LyricsFileDownloader.getInstance(appContext).download(urls[0]));
        requestIdList.add(LyricsFileDownloader.getInstance(appContext).download(urls[0]));


        try {
            latch4.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        LyricsFileDownloader.getInstance(appContext).cleanAll();

        Log.d(TAG, "testDownload done");
    }

    @Test
    public void testParseKRCFile() {
        String fileNameOfSong = "4875936889260991133.krc";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel model = LyricPitchParser.parseFile(lyrics, null, true, 0);

        assertFalse(model.lines.isEmpty());
        assertEquals(0, model.lines.get(0).getStartTime());
        assertEquals("十年 (《明年今日》国语版|《隐婚男女》电影插曲|《摆渡人》电影插曲)", model.name);
        assertEquals("陈奕迅", model.singer);
        assertEquals(LyricType.KRC, model.type);
        assertEquals(182736, model.duration); //[179088,3648]
        assertEquals(0, model.preludeEndPosition);
        assertNull(model.pitchDataList);
    }

    @Test
    public void testParseLyricDataFormat() {
        enableLyricViewExLog();
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        String fileNameOfSong = "8141335308133421388.krc";
        LyricModel model = parseLyricData(appContext, fileNameOfSong);
        assertNotNull(model);
        assertEquals(LyricType.KRC, model.type);

        fileNameOfSong = "4875936889260991133.krc";
        model = parseLyricData(appContext, fileNameOfSong);
        assertNotNull(model);
        assertEquals(LyricType.KRC, model.type);

        fileNameOfSong = "660078.xml";
        model = parseLyricData(appContext, fileNameOfSong);
        assertNotNull(model);
        assertEquals(LyricType.XML, model.type);

        fileNameOfSong = "6246262727282260.lrc";
        model = parseLyricData(appContext, fileNameOfSong);
        assertNotNull(model);
        assertEquals(LyricType.LRC, model.type);

        fileNameOfSong = "kj5380f846be5811ed9efdb2f16c44e48f.lrc";
        model = parseLyricData(appContext, fileNameOfSong);
        assertNotNull(model);
        assertEquals(LyricType.LRC, model.type);
    }

    private LyricModel parseLyricData(Context context, String fileName) {
        try {
            String lyricsContent = Utils.loadAsString(context, fileName);
            if (TextUtils.isEmpty(lyricsContent)) {
                LogUtils.e("parseLyricData: lyricsContent is empty");
                return null;
            }
            return LyricPitchParser.parseLyricData(lyricsContent.getBytes("UTF-8"), null, true, 0);
        } catch (Exception e) {
            LogUtils.e("parseLyricData: " + e.getMessage());
        }
        return null;
    }

    @Test
    public void testParsePitchFile() {
        String fileNameOfSong = "4875936889260991133.krc";
        String fileNameOfPitch = "4875936889260991133.pitch";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File lyricFile = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        File pitches = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfPitch);
        LyricModel model = LyricPitchParser.parseFile(lyricFile, pitches, true, 0);

        assertEquals(294, model.pitchDataList.size());
        assertEquals(241, model.pitchDataList.get(0).duration);
        assertEquals(15203, model.pitchDataList.get(0).startTime);
        assertEquals(50, model.pitchDataList.get(0).pitch, 0);
        assertEquals(2907, model.pitchDataList.get(model.pitchDataList.size() - 1).duration);
        assertEquals(180203, model.pitchDataList.get(model.pitchDataList.size() - 1).startTime);
        assertEquals(50, model.pitchDataList.get(model.pitchDataList.size() - 1).pitch, 0);
    }

    @Test
    public void testParseKRCLyricAndPitch() {
        String fileNameOfSong = "4875936889260991133.krc";
        String fileNameOfPitch = "4875936889260991133.pitch";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        File pitches = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfPitch);
        LyricModel model = LyricPitchParser.parseFile(lyrics, pitches, false, 0);
        assert model != null;
        assertEquals(11590, model.lines.get(0).getStartTime());

        assertFalse(model.lines.isEmpty());
        assertEquals("十年 (《明年今日》国语版|《隐婚男女》电影插曲|《摆渡人》电影插曲)", model.name);
        assertEquals("陈奕迅", model.singer);
        assertEquals(LyricType.KRC, model.type);
        assertEquals(182736, model.duration); //[179088,3648]
        assertEquals(15203, model.preludeEndPosition);
        assertFalse(model.pitchDataList.isEmpty());

        assertEquals(294, model.pitchDataList.size());
        assertEquals(241, model.pitchDataList.get(0).duration);
        assertEquals(15203, model.pitchDataList.get(0).startTime, 15203);
        assertEquals(50, model.pitchDataList.get(0).pitch, 0);
        assertEquals(2907, model.pitchDataList.get(model.pitchDataList.size() - 1).duration);
        assertEquals(180203, model.pitchDataList.get(model.pitchDataList.size() - 1).startTime);
        assertEquals(50, model.pitchDataList.get(model.pitchDataList.size() - 1).pitch, 0);
    }

    @Test
    public void testKrcCalculateScoreWithPitch() {
        String fileNameOfSong = "4875936889260991133.krc";
        String fileNameOfPitch = "4875936889260991133.pitch";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        File lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        File pitches = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfPitch);
        LyricModel model = LyricPitchParser.parseFile(lyrics, pitches, false, 0);
        assert model != null;
        assertEquals(11590, model.lines.get(0).getStartTime());

        assertFalse(model.lines.isEmpty());
        assertEquals("十年 (《明年今日》国语版|《隐婚男女》电影插曲|《摆渡人》电影插曲)", model.name);
        assertEquals("陈奕迅", model.singer);
        assertEquals(LyricType.KRC, model.type);

        ScoringMachine scoringMachine = new ScoringMachine(new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished line:" + line + " score:" + score + " cumulativeScore:" + cumulativeScore + " index:" + index + " numberOfLines:" + numberOfLines);
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate pitch:" + pitch + " scoreAfterNormalization:" + scoreAfterNormalization + " progress:" + progress);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        scoringMachine.prepare(model, false);

        assertEquals(100, scoringMachine.calculateScoreWithPitch(66, 15686), 0);
        assertEquals(100, scoringMachine.calculateScoreWithPitch(65, 15886), 0);
        assertEquals(100, scoringMachine.calculateScoreWithPitch(68, 15888), 0);
        assertEquals(0, scoringMachine.calculateScoreWithPitch(30, 15686), 0);
        assertEquals(0, scoringMachine.calculateScoreWithPitch(90, 15686), 0);
        assertEquals(0, scoringMachine.calculateScoreWithPitch(0, 15686), 0);
        assertEquals(0, scoringMachine.calculateScoreWithPitch(0, 15786), 0);

    }

    @Test
    public void testEnhancedLrcLyricFile() {
        enableLyricViewExLog();
        String fileNameOfSong = "kj5380f846be5811ed9efdb2f16c44e48f.lrc";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel model = LyricPitchParser.parseFile(lyrics, null, true, 0);
        Log.d(TAG, "testEnhancedLrcLyricFile: " + model);

        int lineCount = model.lines.size();
        assertEquals(8, lineCount);

        StringBuilder firstLineContent = new StringBuilder();
        for (LyricsLineModel.Tone tone : model.lines.get(0).tones) {
            firstLineContent.append(tone.word);
        }
        assertEquals("他们总是说我有时不会怎么讲话", firstLineContent.toString());
    }

    @Test
    public void testLyricFileParse() {
        enableLyricViewExLog();
        String fileNameOfSong = "non-normal-timestamp-format.lrc";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricModel model = LyricPitchParser.parseFile(lyrics, null, true, 0);
        Log.d(TAG, "testLyricFileParse: " + model);
        assertNotNull(model);


        fileNameOfSong = "6246262727282260.lrc";
        lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        model = LyricPitchParser.parseFile(lyrics, null, true, 0);
        Log.d(TAG, "testLyricFileParse: " + model);
        assertNotNull(model);

        fileNameOfSong = "872957.xml";
        lyrics = Utils.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        model = LyricPitchParser.parseFile(lyrics, null, true, 0);
        Log.d(TAG, "testLyricFileParse: " + model);
        assertNotNull(model);
    }
}
