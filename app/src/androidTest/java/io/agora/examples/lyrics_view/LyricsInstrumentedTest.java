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

import io.agora.examples.utils.ResourceHelper;
import io.agora.lyrics_view.LrcLoadUtils;
import io.agora.lyrics_view.bean.LrcData;
import io.agora.lyrics_view.bean.LrcEntryData;

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

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String oneAndOnlyOneLineXmlFileContent = ResourceHelper.loadAsString(appContext, "810507.xml");
        assertEquals(true, oneAndOnlyOneLineXmlFileContent.contains("张学友"));

        File target = ResourceHelper.copyAssetsToCreateNewFile(appContext, "810507.xml");
        LrcData parsedLrc = LrcLoadUtils.parse(target);

        System.out.println(TAG + ": " + "Lines count for this lyrics " + parsedLrc.entrys.size());
        Log.d(TAG, "Lines count for this lyrics " + parsedLrc.entrys.size());

        for (LrcEntryData item : parsedLrc.entrys) {
            System.out.println(TAG + ": " + "Line summary: " + item.getStartTime() + " ~ " + item.getEndTime() + " " + item.tones.size());
        }

        // 810507.xml has 42 lines
        assertTrue(parsedLrc.entrys.size() == 42);

        // The 7th line contains '泪' '慢' '慢' '流' '慢' '慢' '收'
        LrcEntryData the7thLine = parsedLrc.entrys.get(6);
        long startOf7thLine = parsedLrc.entrys.get(6).getStartTime();
        long endOf7thLine = parsedLrc.entrys.get(6).getEndTime();
        assertTrue(endOf7thLine - startOf7thLine > 0);
        assertTrue(TextUtils.equals("泪", the7thLine.tones.get(0).word) && the7thLine.tones.get(0).pitch == 176);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(1).word) && the7thLine.tones.get(1).pitch == 0);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(2).word) && the7thLine.tones.get(2).pitch == 176);
        assertTrue(TextUtils.equals("流", the7thLine.tones.get(3).word) && the7thLine.tones.get(3).pitch == 158);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(4).word) && the7thLine.tones.get(4).pitch == 125);
        assertTrue(TextUtils.equals("慢", the7thLine.tones.get(5).word) && the7thLine.tones.get(5).pitch == 159);
        assertTrue(TextUtils.equals("收", the7thLine.tones.get(6).word) && the7thLine.tones.get(6).pitch == 150);

        // The 41th line contains '你' '何' '忍' '远' '走' '高' '飞'
        LrcEntryData the41thLine = parsedLrc.entrys.get(40);
        long startOf41thLine = parsedLrc.entrys.get(40).getStartTime();
        long endOf41thLine = parsedLrc.entrys.get(40).getEndTime();
        assertTrue(endOf41thLine - startOf41thLine > 0);
        assertTrue(TextUtils.equals("你", the41thLine.tones.get(0).word) && the41thLine.tones.get(0).pitch == 0);
        assertTrue(TextUtils.equals("何", the41thLine.tones.get(1).word) && the41thLine.tones.get(1).pitch == 0);
        assertTrue(TextUtils.equals("忍", the41thLine.tones.get(2).word) && the41thLine.tones.get(2).pitch == 0);
        assertTrue(TextUtils.equals("远", the41thLine.tones.get(3).word) && the41thLine.tones.get(3).pitch == 0);
        assertTrue(TextUtils.equals("走", the41thLine.tones.get(4).word) && the41thLine.tones.get(4).pitch == 0);
        assertTrue(TextUtils.equals("高", the41thLine.tones.get(5).word) && the41thLine.tones.get(5).pitch == 0);
        assertTrue(TextUtils.equals("飞", the41thLine.tones.get(6).word) && the41thLine.tones.get(6).pitch == 0);
    }
}
