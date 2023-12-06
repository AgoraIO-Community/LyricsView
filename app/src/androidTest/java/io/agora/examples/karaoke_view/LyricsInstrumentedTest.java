package io.agora.examples.karaoke_view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.agora.examples.utils.ResourceHelper;
import io.agora.karaoke_view.v11.DefaultScoringAlgorithm;
import io.agora.karaoke_view.v11.VoicePitchChanger;
import io.agora.karaoke_view.v11.ai.AINative;
import io.agora.karaoke_view.v11.internal.ScoringMachine;
import io.agora.karaoke_view.v11.logging.LogManager;
import io.agora.karaoke_view.v11.logging.Logger;
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
        assertEquals("io.agora.examples.karaoke_view", appContext.getPackageName());
    }

    @Test
    public void parseOneAndOnlyOneLineXmlFile() {
        // specified to 810507.xml

        String fileNameOfSong = "810507.xml";
        String songArtist = "张学友";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String oneAndOnlyOneLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(oneAndOnlyOneLineXmlFileContent.contains(songArtist));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

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

        long expectedStartOfVerse = (long) (13.0600 * 1000);
        long expectedDuration = (long) (113.0414 * 1000);

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String oneAndOnlyOneLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(oneAndOnlyOneLineXmlFileContent.contains(songArtist));
        assertTrue(oneAndOnlyOneLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        assertEquals(songTitle, parsedLyrics.title);
        assertEquals(songArtist, parsedLyrics.artist);
        assertEquals(expectedStartOfVerse, parsedLyrics.startOfVerse);
        assertEquals(expectedDuration, parsedLyrics.duration);
        assertEquals(expectedNumberOfLines, parsedLyrics.lines.size());

        Log.d(TAG, "Metadata for this lyrics, numberOfLines: " + parsedLyrics.lines.size() + ", title: " + parsedLyrics.title + ", artist: " + parsedLyrics.artist + ", startOfVerse: " + parsedLyrics.startOfVerse + ", duration: " + parsedLyrics.duration);
    }

    @Test
    public void unexpectedContentCheckingForLyrics() {
        LogManager.instance().addLogger(new Logger() {
            @Override
            public void onLog(int level, String tag, String message) {
                Log.e(tag, message);
            }
        });

        File target;
        LyricsModel parsedLyrics;

        String fileNameOfSong;
        String songTitle;
        String songArtist;

        // specified to
        fileNameOfSong = "237732-empty-content.xml";
        songTitle = "不是因为寂寞才想你(Empty Content)";
        songArtist = "AI";

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String lyricsContentInString = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricsParser.parse(target);

        assertEquals(null, parsedLyrics);

        // specified to
        fileNameOfSong = "237732-empty-content-2.xml";
        songTitle = "不是因为寂寞才想你(Empty Content)";
        songArtist = "AI";

        lyricsContentInString = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricsParser.parse(target);

        assertEquals(null, parsedLyrics);

        // specified to
        fileNameOfSong = "237732-invalid-content.xml";
        songTitle = "不是因为寂寞才想你(Invalid Content)";
        songArtist = "AI";

        lyricsContentInString = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricsParser.parse(target);

        assertEquals(null, parsedLyrics);

        // specified to
        fileNameOfSong = "237732-invalid-content-2.xml";
        songTitle = "不是因为寂寞才想你(Invalid Content)";
        songArtist = "AI";

        lyricsContentInString = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(lyricsContentInString.contains(songArtist));
        assertTrue(lyricsContentInString.contains(songTitle));

        target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        parsedLyrics = LyricsParser.parse(target);

        assertEquals(null, parsedLyrics);
    }

    @Test
    public void lineSeparating() {
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
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        assertTrue(parsedLyrics.startOfVerse >= 0);

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new VoicePitchChanger(), new DefaultScoringAlgorithm(),
                new ScoringMachine.OnScoringListener() {
                    @Override
                    public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                        Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);
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
                    public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress) {
                        Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress);
                    }

                    @Override
                    public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean hit, long progress) {
                        Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + hit + " " + progress);
                    }

                    @Override
                    public void requestRefreshUi() {
                        Log.d(TAG, "requestRefreshUi");
                    }
                });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics);
        mockPlay(parsedLyrics, scoringMachine, 20, true);
        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(expectedNumberOfLines, lineCount);

        // Check if `onLineFinished` working as expected
        assertEquals(lineCount, mNumberOfScoringLines);
        assertEquals(lineCount, mLatestIndexOfScoringLines + 1);
    }

    @Test
    public void testForScoring() {
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
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        mScoringMachineTestForScoring = new ScoringMachine(new VoicePitchChanger(), new DefaultScoringAlgorithm(),
                new ScoringMachine.OnScoringListener() {
                    @Override
                    public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                        mNumberOfScoringLines++;
                        mLatestIndexOfScoringLines = index;
                        Log.d(TAG, "onLineFinished hash:" + line.hashCode() + " " + score + " " + cumulativeScore + " " + perfectScore + ", mLatestIndexOfScoringLines=" + index + ", numberOfLines=" + numberOfLines + ", mNumberOfScoringLines=" + mNumberOfScoringLines);

                        assertEquals(parsedLyrics.lines.get(index).getStartTime(), line.getStartTime());
                        assertEquals(parsedLyrics.lines.get(index).getEndTime(), line.getEndTime());
                        assertTrue(mCurrentPosition - line.getEndTime() <= interval); // `onLineFinished` should immediately(such as in 50 milliseconds) come back when line finish
                        assertTrue(score >= 99); // Output
                    }

                    @Override
                    public void resetUi() {
                        Log.d(TAG, "resetUi");
                    }

                    @Override
                    public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress) {
                        Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress + " " + mScoringMachineTestForScoring);
                        mScoringMachineTestForScoring.setPitch(refPitch); // Input
                    }

                    @Override
                    public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean hit, long progress) {
                        Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + hit + " " + progress);
                        assertTrue(scoreAfterNormalization >= 0.99); // Output
                    }

                    @Override
                    public void requestRefreshUi() {
                        Log.d(TAG, "requestRefreshUi");
                    }
                });

        long startTsOfTest = System.currentTimeMillis();
        mScoringMachineTestForScoring.prepare(parsedLyrics);
        mockPlay(parsedLyrics, mScoringMachineTestForScoring, interval, false);
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
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        mNumberOfScoringLines = 0;
        mLatestIndexOfScoringLines = 0;
        ScoringMachine scoringMachine = new ScoringMachine(new VoicePitchChanger(), new DefaultScoringAlgorithm(), new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);
                mNumberOfScoringLines++;
                mLatestIndexOfScoringLines = index;
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress) {
                Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress);
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean hit, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + hit + " " + progress);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics);

        // Only first 5 lines
        for (LyricsLineModel line : parsedLyrics.lines) {
            for (LyricsLineModel.Tone tone : line.tones) {
                scoringMachine.setProgress(tone.begin + tone.getDuration() / 2);
                scoringMachine.setPitch(tone.pitch - 1);
            }

            if (mLatestIndexOfScoringLines >= 4) {
                break;
            }
        }

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);

        // Check if `onLineFinished` working as expected
        assertEquals(mNumberOfScoringLines, 5);
        assertEquals(mLatestIndexOfScoringLines + 1, 5);
    }

    private float mRefPitch = 0;

    @Test
    public void testRefPitch() {
        // specified to 825003.xml
        // 825003.xml has 30 lines
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        ScoringMachine scoringMachine = new ScoringMachine(new VoicePitchChanger(), new DefaultScoringAlgorithm(), new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress) {
                Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress);
                mRefPitch = refPitch;
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean hit, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + hit + " " + progress);
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics);

        mRefPitch = -1;
        scoringMachine.setProgress(0);
        assertEquals(mRefPitch, -1, 0d);

        mRefPitch = -1;
        scoringMachine.setProgress(28813);
        assertEquals(mRefPitch, -1, 0d);

        mRefPitch = -1;
        scoringMachine.setProgress(28814);
        assertEquals(mRefPitch, 172, 0d);

        mRefPitch = -1;
        scoringMachine.setProgress(29675); // Same with 28814
        assertEquals(mRefPitch, 172, 0d);

        mRefPitch = -1;
        scoringMachine.setProgress(185160);
        assertEquals(mRefPitch, 130, 0d);

        mRefPitch = -1;
        scoringMachine.setProgress(185161);
        assertEquals(mRefPitch, 213, 0d);

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);
    }

    private float mPitchHit = 0;
    private boolean mHit = false;

    @Test
    public void testPitchHit() {
        // specified to 825003.xml
        // 825003.xml has 30 lines
        String fileNameOfSong = "825003.xml";
        String songTitle = "净化空间";
        int expectedNumberOfLines = 30;

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        LyricsModel parsedLyrics = LyricsParser.parse(target);

        Log.d(TAG, "Line count for this lyrics(" + songTitle + ") " + parsedLyrics.lines.size());

        for (LyricsLineModel line : parsedLyrics.lines) {
            Log.d(TAG, "Line summary: " + line.getStartTime() + " ~ " + line.getEndTime() + " " + line.tones.size());
        }

        VoicePitchChanger changer = new VoicePitchChanger();
        ScoringMachine scoringMachine = new ScoringMachine(changer, new DefaultScoringAlgorithm(), new ScoringMachine.OnScoringListener() {
            @Override
            public void onLineFinished(LyricsLineModel line, int score, int cumulativeScore, int perfectScore, int index, int numberOfLines) {
                Log.d(TAG, "onLineFinished " + line + " " + score + " " + cumulativeScore + " " + perfectScore + " " + index + " " + numberOfLines);
            }

            @Override
            public void resetUi() {
                Log.d(TAG, "resetUi");
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches, long progress) {
                Log.d(TAG, "onRefPitchUpdate " + refPitch + " " + numberOfRefPitches + " " + progress);
            }

            @Override
            public void onPitchAndScoreUpdate(float pitch, double scoreAfterNormalization, boolean hit, long progress) {
                Log.d(TAG, "onPitchAndScoreUpdate " + pitch + " " + scoreAfterNormalization + " " + hit + " " + progress);
                mPitchHit = pitch;
                mHit = hit;
            }

            @Override
            public void requestRefreshUi() {
                Log.d(TAG, "requestRefreshUi");
            }
        });

        long startTsOfTest = System.currentTimeMillis();
        scoringMachine.prepare(parsedLyrics);

        mPitchHit = -1;
        scoringMachine.setProgress(0);
        scoringMachine.setPitch(0);
        assertEquals(mPitchHit, -1, 0d);
        assertFalse(mHit);

        mPitchHit = -1;
        scoringMachine.setProgress(28813);
        scoringMachine.setPitch(0);
        assertEquals(mPitchHit, -1, 0d);
        assertFalse(mHit);

        mPitchHit = -1;
        scoringMachine.setProgress(28814);
        scoringMachine.setPitch(172);
        assertEquals(mPitchHit, 172, 0d);
        assertTrue(mHit);

        mPitchHit = -1;
        scoringMachine.setProgress(29675);
        scoringMachine.setPitch(172);
        assertEquals(mPitchHit, 172, 0d);
        assertTrue(mHit);

        mPitchHit = -1;
        scoringMachine.setProgress(185160);
        scoringMachine.setPitch(130);
        assertEquals(mPitchHit, 130, 0d);
        assertTrue(mHit);

        mPitchHit = -1;
        scoringMachine.setProgress(185161);
        scoringMachine.setPitch(213);
        assertEquals(mPitchHit, 213, 0d);
        assertTrue(mHit);

        mPitchHit = -1;
        scoringMachine.setProgress(187238);
        scoringMachine.setPitch(100);
        double processedPitch = AINative.handlePitch(213, 100, scoringMachine.getMaximumRefPitch());
        assertEquals(200.0, processedPitch, 0);
        assertEquals(200.0, mPitchHit, 0.01); // 120.33999633789062
        assertTrue(mHit);

        Log.d(TAG, "Started at " + new Date(startTsOfTest) + ", taken " + (System.currentTimeMillis() - startTsOfTest) + " ms");

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);
    }

    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private long mCurrentPosition = 0;
    private int mNumberOfScoringLines = 0;
    private int mLatestIndexOfScoringLines = 0;
    private ScheduledFuture mFuture;

    private void mockPlay(final LyricsModel model, final ScoringMachine scoringMachine, int interval, boolean withPitch) {
        // 01-12 11:03:00.029 29186 29227 D LyricsInstrumentedTest_MockPlayer: duration: 242051, position: 0
        // 01-12 11:03:44.895 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.*.model.LyricsLineModel@a929307 55 145 3000 1 30
        // 01-12 11:03:51.835 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.*.model.LyricsLineModel@79eec34 60 205 3000 2 30
        // 01-12 11:04:08.953 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.*.model.LyricsLineModel@f0a3fd2 61 318 3000 4 30
        // 01-12 11:07:02.073 29186 29229 D LyricsInstrumentedTest: onLineFinished io.agora.*.model.LyricsLineModel@34e9f0b 55 1815 3000 29 30
        // 01-12 11:07:03.073 29186 29229 D LyricsInstrumentedTest_MockPlayer: put the indicator back in space
        // 01-12 11:07:03.093 29186 29227 D LyricsInstrumentedTest_MockPlayer: Song finished
        // 01-12 11:07:03.098 29186 29229 D LyricsInstrumentedTest_MockPlayer: quit
        // 01-12 11:07:03.199 29186 29227 D LyricsInstrumentedTest: Started at Thu Jan 12 11:03:00 GMT+08:00 2023, takes 243170 ms

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
                    scoringMachine.setProgress(mCurrentPosition);
                    float pitch = 0;
                    if (withPitch) {
                        pitch = (float) Math.random() * 200;
                        scoringMachine.setPitch(pitch);
                    }
                    Log.d(PLAYER_TAG, "mCurrentPosition: " + mCurrentPosition + ", pitch: " + pitch);
                } else if (mCurrentPosition >= DURATION_OF_SONG && mCurrentPosition < (DURATION_OF_SONG + 1000)) {
                    long lastPosition = mCurrentPosition;
                    scoringMachine.setProgress(mCurrentPosition);
                    scoringMachine.setPitch(0);
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
        String sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent = ResourceHelper.loadAsString(appContext, fileNameOfSong);
        assertTrue(sameTimestampForStartOfCurrentLineAndEndOfPreviousLineXmlFileContent.contains(songTitle));

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

        File lyrics = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfSong);
        File pitches = ResourceHelper.copyAssetsToCreateNewFile(appContext, fileNameOfPitch);
        LyricsModel parsedLyrics = LyricsParser.parse(lyrics, pitches);

        int lineCount = parsedLyrics.lines.size();
        assertEquals(lineCount, expectedNumberOfLines);
    }

    @Test
    public void testHandlePitchForAi() {
        assertEquals(0d, AINative.handlePitch(0, 0, 400), 0d);
        assertEquals(0d, AINative.handlePitch(1, 0, 400), 0d);
        assertEquals(1d, AINative.handlePitch(1, 1, 400), 0d);
        assertEquals(90d, AINative.handlePitch(100, 90, 400), 0d);
        assertEquals(80d, AINative.handlePitch(100, 80, 400), 0d);
        assertEquals(160d, AINative.handlePitch(200, 80, 400), 0d);
        assertEquals(320d, AINative.handlePitch(400, 80, 400), 0d);
        assertEquals(400d, AINative.handlePitch(400, 200, 400), 0d);
        assertEquals(400d, AINative.handlePitch(400, 400, 400), 0d);
        assertEquals(500d, AINative.handlePitch(400, 500, 400), 0d);
        assertEquals(300d, AINative.handlePitch(400, 600, 400), 0d);
        assertEquals(350d, AINative.handlePitch(400, 700, 400), 0d);
        assertEquals(400d, AINative.handlePitch(400, 800, 400), 0d);
    }

    @Test
    public void testCalculatedScoreForAi() {
        assertEquals(0.0f, AINative.calculatedScore(201, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(200, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(190, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(180, 100, 10, 0), 0.01f);
        assertEquals(9.763043f, AINative.calculatedScore(170, 100, 10, 0), 0.01f);
        assertEquals(22.357689f, AINative.calculatedScore(160, 100, 10, 0), 0.01f);
        assertEquals(35.765438f, AINative.calculatedScore(150, 100, 10, 0), 0.01f);
        assertEquals(50.098568f, AINative.calculatedScore(140, 100, 10, 0), 0.01f);
        assertEquals(65.494354f, AINative.calculatedScore(130, 100, 10, 0), 0.01f);
        assertEquals(82.12307f, AINative.calculatedScore(120, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AINative.calculatedScore(110, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AINative.calculatedScore(101, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AINative.calculatedScore(100, 100, 10, 0), 0.01f);
        assertEquals(100.0f, AINative.calculatedScore(99, 100, 10, 0), 0.01f);
        assertEquals(73.64238f, AINative.calculatedScore(80, 100, 10, 0), 0.01f);
        assertEquals(45.901512f, AINative.calculatedScore(70, 100, 10, 0), 0.01f);
        assertEquals(13.877029f, AINative.calculatedScore(60, 100, 10, 0), 0.01f);
        assertEquals(10.3853855f, AINative.calculatedScore(59, 100, 10, 0), 0.01f);
        assertEquals(6.834053f, AINative.calculatedScore(58, 100, 10, 0), 0.01f);
        assertEquals(3.2209551f, AINative.calculatedScore(57, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(56, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(55, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(50, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(40, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(30, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(20, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(10, 100, 10, 0), 0.01f);
        assertEquals(0.0f, AINative.calculatedScore(1, 100, 10, 0), 0.01f);
    }
}
