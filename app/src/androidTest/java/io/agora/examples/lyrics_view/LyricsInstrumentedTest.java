package io.agora.examples.lyrics_view;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.agora.examples.utils.ResourceHelper;

import io.agora.karaoke_view.v11.VoicePitchChanger;
import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.karaoke_view.v11.utils.LyricsParser;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class LyricsInstrumentedTest {

    private static final String TAG = "LyricsInstrumentedTest";

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("io.agora.examples.lyrics_view", appContext.getPackageName());
    }

    @Test
    public void parseOneAndOnlyOneLineXmlFile() {
        // specified to 810507.xml

        String fileNameOfSong = "810507.xml";
        String songArtist = "张学友";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String oneAndOnlyOneLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertEquals(true, oneAndOnlyOneLineXmlFileContent.contains(songArtist));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        // 810507.xml has 42 lines
        assertTrue(parsedLyrics.lines.size() == 42);

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
    public void parseSameTimestampForStartAndPreviousEndXmlFile() {
        // specified to 825003.xml
        // 825003.xml has 30 lines
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertEquals(true, sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new VoicePitchChanger(), new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, double score, double cumulativeScore, double perfectScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);
                mNumberOfScoringLines++;
                mLatestIndexOfScoringLines = index;
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches) {
//                Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches);
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization) {
//                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics);
        mockPlay(parsedLyrics, scoringMachine);
        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertTrue(lineCount == expectedNumberOfLines);

        // Check if `onLineFinished` working as expected
        assertTrue(mNumberOfScoringLines == lineCount);
        assertTrue(mLatestIndexOfScoringLines + 1 == lineCount);
    }

    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private long mCurrentPosition = 0;
    private int mNumberOfScoringLines = 0;
    private int mLatestIndexOfScoringLines = 0;
    private ScheduledFuture mFuture;

    private void mockPlay(final LyricsModel model, final ScoringMachine scoringMachine) {
        // 01-12 11:03:00.029 29186 29227 D LyricsInstrumentedTest_MockPlayer: duration: 242051, position: 0
        // 01-12 11:03:44.895 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.lyrics_view.v11.model.LyricsLineModel@a929307 55.4259650979513 145.58355316053687 3000.0 1 30
        // 01-12 11:03:51.835 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.lyrics_view.v11.model.LyricsLineModel@79eec34 59.921391935944555 205.50494509648144 3000.0 2 30
        // 01-12 11:04:08.953 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.lyrics_view.v11.model.LyricsLineModel@f0a3fd2 61.03482350600236 318.2838389210401 3000.0 4 30
        // 01-12 11:07:02.073 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.lyrics_view.v11.model.LyricsLineModel@34e9f0b 55.14133207074234 1815.480908114657 3000.0 29 30
        // 01-12 11:07:03.073 29186 29229 D LyricsInstrumentedTest_MockPlayer: put the pivot back in space
        // 01-12 11:07:03.093 29186 29227 D LyricsInstrumentedTest_MockPlayer: Song finished
        // 01-12 11:07:03.098 29186 29229 D LyricsInstrumentedTest_MockPlayer: quit
        // 01-12 11:07:03.199 29186 29227 D LyricsInstrumentedTest: Started at Thu Jan 12 11:03:00 GMT+08:00 2023, takes 243170 ms

        final long DURATION_OF_SONG = model.lines.get(model.lines.size() - 1).getEndTime();
        mCurrentPosition = 0;
        final String PLAYER_TAG = TAG + "_MockPlayer";
        Log.d(PLAYER_TAG, "duration: " + DURATION_OF_SONG + ", position: " + mCurrentPosition);
        if (mFuture != null) {
            mFuture.cancel(true);
        }

        CountDownLatch latch = new CountDownLatch(1);

        mFuture = mExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (mCurrentPosition >= 0 && mCurrentPosition < DURATION_OF_SONG) {
                    scoringMachine.setProgress(mCurrentPosition);
                    float pitch = (float) Math.random() * 200;
                    scoringMachine.setPitch(pitch);
                    Log.d(PLAYER_TAG, "mCurrentPosition: " + mCurrentPosition + ", pitch: " + pitch);
                } else if (mCurrentPosition >= DURATION_OF_SONG && mCurrentPosition < (DURATION_OF_SONG + 1000)) {
                    long lastPosition = mCurrentPosition;
                    scoringMachine.setProgress(0);
                    scoringMachine.setPitch(0);
                    Log.d(PLAYER_TAG, "put the pivot back in space");
                    // Put the pivot back in space
                } else if (mCurrentPosition >= (DURATION_OF_SONG + 1000)) {
                    if (mFuture != null) {
                        mFuture.cancel(true);
                    }
                    mCurrentPosition = 0;
                    latch.countDown();
                    Log.d(PLAYER_TAG, "quit");
                    return;
                }
                mCurrentPosition += 20;
            }
        }, 0, 20, TimeUnit.MILLISECONDS);

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(PLAYER_TAG, "Song finished");
    }

    @Test
    public void parseTimestampOverlapForStartAndPreviousEndXmlFile() {
        // specified to 147383.xml
        // 147383.xml has 48 lines

//      String fileNameOfSong = "147383.xml";
//      String songTitle = "光辉岁月";
//      int expectedNumberOfLines = 48;

//      String fileNameOfSong = "660078.xml";
//      String songTitle = "遇见";
//      int expectedNumberOfLines = 25;

//      String fileNameOfSong = "826125.xml";
//      String songTitle = "恋歌";
//      int expectedNumberOfLines = 26;

        String fileNameOfSong = "793566.xml";
        String songTitle = "感谢你曾来过";
        int expectedNumberOfLines = 86;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertEquals(true, sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

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
        assertTrue(lineCount == expectedNumberOfLines);
    }

}
