package io.agora.examples.karaoke_view_ex.agora.tme

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.karaoke_view_ex.KaraokeEvent
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.internal.constants.LyricType
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TMEToneInstrumentedTest {
    @Test fun publicApiLoadsModelAndEmitsExistingLineCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.cacheDir
        val toneFile = File.createTempFile("tme-tone", ".json", directory)
        val lyricFile = File.createTempFile("tme-lyric", ".lrc", directory)
        try {
            toneFile.writeText("""[{"st":1000,"d":500,"p":44}]""")
            lyricFile.writeText("[00:00.00]作词：某人\n[00:01.00]第一句")
            val model = requireNotNull(KaraokeView.parseTMEToneData(toneFile.absolutePath, lyricFile.absolutePath))
            assertEquals(LyricType.LRC, model.type)
            assertEquals(2, model.lines.size)
            var callbacks = 0
            instrumentation.runOnMainSync {
                val karaoke = KaraokeView()
                karaoke.setKaraokeEvent(object : KaraokeEvent {
                    override fun onDragTo(view: KaraokeView, progress: Long) {}
                    override fun onLineFinished(view: KaraokeView, line: LyricsLineModel, score: Int,
                                                cumulativeScore: Int, index: Int, lineCount: Int) {
                        assertSame(model.lines[1], line)
                        assertEquals(100, score)
                        assertEquals(100, cumulativeScore)
                        assertEquals(0, index)
                        assertEquals(1, lineCount)
                        callbacks++
                    }
                })
                karaoke.setLyricData(model, true)
                karaoke.setProgress(1100)
                karaoke.setPitch((55 * (2 - 1e-6)).toFloat(), 0f, 1100)
                karaoke.setProgress(1500)
                karaoke.setPitch(0f, 0f, 1500)
                assertEquals(1, callbacks)
            }
        } finally {
            toneFile.delete()
            lyricFile.delete()
        }
    }

    @Test fun genericLrcXmlAndKrcEntryPointsStillWork() {
        val lrc = requireNotNull(KaraokeView.parseLyricData(
            "[00:43.30]旧 LRC\n[00:44.456]下一句".toByteArray(), null))
        assertEquals(LyricType.LRC, lrc.type)
        assertEquals(43300L, lrc.lines[0].startTime)
        assertEquals(44456L, lrc.lines[1].startTime)
        assertFalse(lrc.hasPitch)
        val xml = requireNotNull(KaraokeView.parseLyricData("""
            <?xml version="1.0" encoding="utf-8"?>
            <song><general><name>旧 XML</name><singer>歌手</singer></general>
            <midi_lrc><paragraph><sentence><tone begin="1.0" end="1.5" lang="1" pitch="220" pronounce="">
            <word>词</word></tone></sentence></paragraph></midi_lrc></song>
        """.trimIndent().toByteArray(), null))
        assertEquals(LyricType.XML, xml.type)
        assertEquals(220, xml.lines[0].tones[0].pitch)
        val krc = requireNotNull(KaraokeView.parseLyricData(
            "[ar:歌手]\n[1000,500]<0,500,220>旧 KRC".toByteArray(), null))
        assertEquals(LyricType.KRC, krc.type)
        assertEquals(1000L, krc.lines[0].startTime)
    }
}
