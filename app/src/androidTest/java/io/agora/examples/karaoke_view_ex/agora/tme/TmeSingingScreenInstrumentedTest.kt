package io.agora.examples.karaoke_view_ex.agora.tme

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.examples.karaoke_view_ex.R
import io.agora.examples.karaoke_view_ex.TmeSingingActivity
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.model.LyricModel
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No credentials or SDK session: verify safe process-restoration entry into the real screen. */
@RunWith(AndroidJUnit4::class)
class TmeSingingScreenInstrumentedTest {
    private class Session {
        lateinit var preparationListener: TmePlaybackController.PreparationListener<LyricModel>
        var plays = 0
        var beforePlay: () -> Unit = {}
        val controller = TmePlaybackController("screen-test", "original-id", "测试歌曲",
            object : TmePlaybackController.MediaPort {
                override fun preload(sessionId: String, songId: String) = TmePlaybackController.PreloadRequest(42, "preload-test")
                override fun open(sessionId: String, songCode: Long) = 0
                override fun play(): Int { beforePlay(); plays++; return 0 }
                override fun pause() = 0
                override fun resume() = 0
                override fun position() = 1234L
                override fun release() {}
            }, object : TmePlaybackController.PreparationPort<LyricModel> {
                override fun start(songId: String, listener: TmePlaybackController.PreparationListener<LyricModel>) { preparationListener = listener }
                override fun cancel() {}
            }, object : TmePlaybackController.Scheduler {
                override fun schedule(delayMs: Long, action: () -> Unit) = TmePlaybackController.Cancel {}
            })
        fun preload() = controller.onPreload("screen-test", "preload-test", 42, 100, 0, 0)
        fun opened() = controller.onPlayer("screen-test", TmePlaybackController.PlayerEvent.OPENED)
    }

    // Replace only the retained session in tests; the production owner/API stays unchanged.
    private fun withSession(session: Session, action: (TmeSingingActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val field = ServiceManager::class.java.getDeclaredField("tmeController").apply { isAccessible = true }
        val previous = field.get(null)
        instrumentation.runOnMainSync { field.set(null, session.controller) }
        try {
            val intent = Intent(instrumentation.targetContext, TmeSingingActivity::class.java)
                .putExtra("sessionId", "screen-test").putExtra("songId", "original-id")
            ActivityScenario.launch<TmeSingingActivity>(intent).use { scenario ->
                scenario.onActivity { activity -> session.controller.start(); action(activity) }
            }
        } finally {
            instrumentation.runOnMainSync { session.controller.close(); field.set(null, previous) }
        }
    }

    @Test fun scoringAreaAndFullScoreAreAppliedBeforeInitialPlay() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val tone = File.createTempFile("screen-tone", ".json", cache)
        val lyric = File.createTempFile("screen-lyric", ".lrc", cache)
        try {
            tone.writeText("""[{"st":1000,"d":500,"p":44}]""")
            lyric.writeText("[00:00.00]作词：某人\n[00:01.00]第一句")
            val model = requireNotNull(KaraokeView.parseTMEToneData(tone.path, lyric.path))
            val session = Session()
            withSession(session) { activity ->
                session.beforePlay = {
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scoring_area).visibility)
                    assertEquals(activity.getString(R.string.tme_scores, 0, 0, 100),
                        activity.findViewById<android.widget.TextView>(R.id.score_summary).text.toString())
                }
                session.preload(); session.opened()
                assertEquals(0, session.plays)
                session.preparationListener.onReady(model, 1)
                assertEquals(1, session.plays)
                session.opened(); assertEquals(1, session.plays)
                session.controller.onPlayer("screen-test", TmePlaybackController.PlayerEvent.PLAYING)
                activity.findViewById<View>(R.id.pause_resume).performClick()
                assertEquals(TmePlaybackController.Phase.PAUSED, session.controller.state.phase)
                activity.findViewById<View>(R.id.pause_resume).performClick()
                assertEquals(TmePlaybackController.Phase.STARTING, session.controller.state.phase)
            }
        } finally { tone.delete(); lyric.delete() }
    }

    @Test fun independentDownloadStatesAndHiddenScoringPrecedeUnscoredPlay() {
        val session = Session()
        withSession(session) { activity ->
            session.beforePlay = { assertEquals(View.GONE, activity.findViewById<View>(R.id.scoring_area).visibility) }
            session.preload(); session.opened()
            session.preparationListener.onStage(TmePlaybackController.Stage.LYRIC_DOWNLOADED)
            session.preparationListener.onStage(TmePlaybackController.Stage.PITCH_DOWNLOADING)
            assertTrue(activity.findViewById<android.widget.TextView>(R.id.lyric_status).text.contains(activity.getString(R.string.tme_resource_downloaded)))
            assertTrue(activity.findViewById<android.widget.TextView>(R.id.pitch_status).text.contains(activity.getString(R.string.tme_resource_downloading)))
            session.preparationListener.onFailure("DOWNLOAD")
            assertEquals(1, session.plays)
            assertFalse(session.controller.state.scoring)
            assertTrue(activity.findViewById<android.widget.TextView>(R.id.failure_reason).text.isNotEmpty())
        }
    }

    @Test fun absentSessionShowsInterruptedAndKeepsAllScoringHidden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, TmeSingingActivity::class.java)
            .putExtra("songName", "恢复中的歌曲").putExtra("songId", "original-id")
        ActivityScenario.launch<TmeSingingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.scoring_area).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.restart_song).visibility)
                assertFalse(activity.findViewById<View>(R.id.pause_resume).isEnabled)
                assertFalse(activity.findViewById<View>(R.id.toggle_track).isEnabled)
                assertEquals(activity.getString(R.string.tme_interrupted),
                    activity.findViewById<android.widget.TextView>(R.id.singing_status).text.toString())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.scoring_area).visibility)
                assertFalse(activity.findViewById<View>(R.id.pause_resume).isEnabled)
            }
        }
    }
}
