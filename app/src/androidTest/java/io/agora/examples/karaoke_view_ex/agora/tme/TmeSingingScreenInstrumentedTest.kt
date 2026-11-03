package io.agora.examples.karaoke_view_ex.agora.tme

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.examples.karaoke_view_ex.MainActivity
import io.agora.examples.karaoke_view_ex.R
import io.agora.examples.karaoke_view_ex.TmeSingingActivity
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.utils.ServiceType
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.LyricsView
import io.agora.karaoke_view_ex.internal.ScoringMachine
import io.agora.karaoke_view_ex.internal.lyric.parse.TMEToneParser
import io.agora.karaoke_view_ex.model.LyricModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses the real shared page with a deterministic media port; no audio-playback claims. */
@RunWith(AndroidJUnit4::class)
class TmeSingingScreenInstrumentedTest {
    private class Session {
        lateinit var preparation: TmePlaybackController.PreparationListener<LyricModel>
        var plays = 0
        var seekResult = 0
        val seeks = mutableListOf<Long>()
        var beforePlay: () -> Unit = {}
        val controller = TmePlaybackController("screen-test", "original-id", "测试歌曲",
            object : TmePlaybackController.MediaPort {
                override fun preload(sessionId: String, songId: String) = TmePlaybackController.PreloadRequest(42, "preload-test")
                override fun open(sessionId: String, songCode: Long) = 0
                override fun play(): Int { beforePlay(); plays++; return 0 }
                override fun pause() = 0
                override fun resume() = 0
                override fun seek(positionMs: Long): Int { seeks += positionMs; return seekResult }
                override fun position() = 1234L
                override fun release() {}
            }, object : TmePlaybackController.PreparationPort<LyricModel> {
                override fun start(songId: String, listener: TmePlaybackController.PreparationListener<LyricModel>) { preparation = listener }
                override fun cancel() {}
            }, object : TmePlaybackController.Scheduler {
                override fun schedule(delayMs: Long, action: () -> Unit) = TmePlaybackController.Cancel {}
            })
        fun opened() {
            controller.onPreload("screen-test", "preload-test", 42, 100, 0, 0)
            controller.onPlayer("screen-test", TmePlaybackController.PlayerEvent.OPENED)
        }
        fun playing() = controller.onPlayer("screen-test", TmePlaybackController.PlayerEvent.PLAYING)
    }

    private fun withSession(session: Session, action: (MainActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val serviceKey = context.getString(R.string.prefs_key_service_type)
        val lyricKey = context.getString(R.string.prefs_key_lyric_type)
        val previousService = prefs.getInt(serviceKey, 0)
        val previousLyric = prefs.getInt(lyricKey, 0)
        val field = ServiceManager::class.java.getDeclaredField("tmeController").apply { isAccessible = true }
        instrumentation.runOnMainSync {
            ServiceManager.destroy(); ServiceManager.setServiceType(ServiceType.TME)
            field.set(null, session.controller)
        }
        try {
            val intent = Intent(context, TmeSingingActivity::class.java)
                .putExtra("sessionId", "screen-test").putExtra("songId", "original-id")
            ActivityScenario.launch<TmeSingingActivity>(intent).use { scenario ->
                scenario.onActivity { activity -> session.controller.start(); action(activity) }
            }
        } finally {
            instrumentation.runOnMainSync { session.controller.close(); ServiceManager.destroy() }
            prefs.edit().putInt(serviceKey, previousService).putInt(lyricKey, previousLyric).commit()
        }
    }

    private fun withModel(scored: Boolean, action: (LyricModel) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val tone = File.createTempFile("screen-tone", ".json", cache)
        val lyric = File.createTempFile("screen-lyric", ".lrc", cache)
        try {
            tone.writeText("""[{"st":2000,"d":500,"p":44}]""")
            lyric.writeText("[00:02.00]第一句")
            action(requireNotNull(if (scored) KaraokeView.parseTMEToneData(tone.path, lyric.path) else TMEToneParser.parseLyrics(lyric.path)))
        } finally { tone.delete(); lyric.delete() }
    }

    @Test fun sharedControlsAndScoreModelAreAppliedBeforeInitialPlay() = withModel(true) { model ->
        val session = Session()
        withSession(session) { activity ->
            session.beforePlay = {
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scoring_view).visibility)
                assertTrue(activity.findViewById<TextView>(R.id.callBack).text.contains("100"))
            }
            session.opened(); assertEquals(0, session.plays)
            // Controls must also be safe before a real model arrives.
            activity.findViewById<CheckBox>(R.id.enable_lyrics).performClick()
            activity.findViewById<CheckBox>(R.id.enable_lyrics).performClick()
            session.preparation.onReady(model, 1); assertEquals(1, session.plays)
            session.playing()
            for (id in listOf(R.id.play, R.id.pause, R.id.switch_to_next, R.id.skip_the_intro,
                R.id.play_original, R.id.enable_lyrics, R.id.enable_scoring)) {
                assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
            }
            activity.findViewById<View>(R.id.pause).performClick()
            assertEquals(TmePlaybackController.Phase.PAUSED, session.controller.state.phase)
            activity.findViewById<View>(R.id.skip_the_intro).performClick()
            assertEquals(listOf(1000L), session.seeks)
            assertEquals(TmePlaybackController.Phase.PAUSED, session.controller.state.phase)
            activity.findViewById<View>(R.id.pause).performClick()
            assertEquals(TmePlaybackController.Phase.STARTING, session.controller.state.phase)
            session.playing()
            activity.findViewById<CheckBox>(R.id.enable_lyrics).performClick()
            activity.findViewById<CheckBox>(R.id.enable_scoring).performClick()
            assertTrue(session.controller.state.scoring)
            activity.findViewById<View>(R.id.play).performClick()
            assertNull(ServiceManager.getTmeController("screen-test"))
            assertEquals(activity.getString(R.string.play), activity.findViewById<TextView>(R.id.play).text.toString())
            activity.findViewById<CheckBox>(R.id.enable_scoring).performClick()
            activity.findViewById<CheckBox>(R.id.enable_lyrics).performClick()
        }
    }

    @Test fun resourceStatusAndFailureUseExistingInformationAreas() {
        val session = Session()
        withSession(session) { activity ->
            session.opened()
            session.preparation.onStage(TmePlaybackController.Stage.LYRIC_DOWNLOADED)
            session.preparation.onStage(TmePlaybackController.Stage.PITCH_DOWNLOADING)
            val callback = activity.findViewById<TextView>(R.id.callBack)
            assertTrue(callback.text.contains(activity.getString(R.string.tme_resource_downloaded)))
            assertTrue(callback.text.contains(activity.getString(R.string.tme_resource_downloading)))
            session.preparation.onFailure("DOWNLOAD")
            assertEquals(1, session.plays)
            assertFalse(session.controller.state.scoring)
            assertTrue(activity.findViewById<TextView>(R.id.lyrics_description).text.contains("无计分"))
        }
    }

    @Test fun lyricsOnlyPlaybackStillSynchronizesTheSharedLyricsView() = withModel(false) { model ->
        val session = Session()
        withSession(session) { activity ->
            session.opened(); session.preparation.onUnscored(model, "MISSING_RESOURCE"); session.playing()
            assertEquals(1, session.plays)
            assertSame(model, session.controller.state.model)
            assertFalse(session.controller.state.scoring)
            assertTrue(activity.findViewById<View>(R.id.skip_the_intro).isEnabled)
        }
    }

    @Test fun rejectedWidgetSeekKeepsEarnedNativeScores() = withModel(true) { model ->
        val session = Session()
        withSession(session) { activity ->
            session.opened(); session.preparation.onReady(model, 1); session.playing()
            val karaokeField = MainActivity::class.java.getDeclaredField("mKaraokeView").apply { isAccessible = true }
            val karaoke = karaokeField.get(activity) as KaraokeView
            val scoringField = KaraokeView::class.java.getDeclaredField("mScoringMachine").apply { isAccessible = true }
            val scoring = scoringField.get(karaoke) as ScoringMachine
            scoring.mScoreForEachLine[0] = 100
            val lyricView = activity.findViewById<LyricsView>(R.id.lyrics_view)
            val seekField = LyricsView::class.java.getDeclaredField("mOnSeekActionListener").apply { isAccessible = true }
            val seek = seekField.get(lyricView) as LyricsView.OnLyricsSeekListener
            session.seekResult = -1
            seek.onProgressChanged(1000)
            assertEquals(100, scoring.mScoreForEachLine[0])
            session.seekResult = 0
            seek.onProgressChanged(1000)
            assertFalse(scoring.mScoreForEachLine.containsKey(0))
        }
    }

    @Test fun openingAndClosingSongSheetKeepsPlayingSession() {
        val session = Session()
        withSession(session) { activity ->
            session.opened(); session.preparation.onFailure("DOWNLOAD"); session.playing()
            activity.findViewById<View>(R.id.action_choose_song).performClick()
            activity.supportFragmentManager.executePendingTransactions()
            assertEquals(TmePlaybackController.Phase.PLAYING, session.controller.state.phase)
            activity.supportFragmentManager.fragments.filterIsInstance<DialogFragment>().single().dismiss()
            activity.supportFragmentManager.executePendingTransactions()
            assertSame(session.controller, ServiceManager.getTmeController("screen-test"))
        }
    }

    @Test fun emptyAndInterruptedEntriesUseSharedControlsAndSurviveRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val serviceKey = context.getString(R.string.prefs_key_service_type)
        val lyricKey = context.getString(R.string.prefs_key_lyric_type)
        val oldService = prefs.getInt(serviceKey, 0)
        val oldLyric = prefs.getInt(lyricKey, 0)
        try {
            for (interrupted in listOf(false, true)) {
                val intent = Intent(context, TmeSingingActivity::class.java)
                if (interrupted) intent.putExtra("songId", "missing-session").putExtra("songName", "恢复中的歌曲")
                ActivityScenario.launch<TmeSingingActivity>(intent).use { scenario ->
                    fun checkPage() = scenario.onActivity { activity ->
                        assertTrue(activity.findViewById<View>(R.id.play).isEnabled)
                        assertFalse(activity.findViewById<View>(R.id.pause).isEnabled)
                        assertEquals(activity.getString(if (interrupted) R.string.tme_interrupted else R.string.tme_choose_song),
                            activity.findViewById<TextView>(R.id.callBack).text.toString())
                        activity.findViewById<CheckBox>(R.id.enable_lyrics).performClick()
                        activity.findViewById<CheckBox>(R.id.enable_scoring).performClick()
                    }
                    checkPage(); scenario.recreate(); checkPage()
                }
            }
        } finally {
            instrumentation.runOnMainSync { ServiceManager.destroy() }
            prefs.edit().putInt(serviceKey, oldService).putInt(lyricKey, oldLyric).commit()
        }
    }
}
