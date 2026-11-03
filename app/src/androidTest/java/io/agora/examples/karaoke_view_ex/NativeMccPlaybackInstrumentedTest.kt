package io.agora.examples.karaoke_view_ex

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.examples.karaoke_view_ex.agora.RtcManager
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.utils.LyricsResourcePool
import io.agora.examples.utils.ServiceType
import io.agora.karaoke_view_ex.KaraokeView
import io.agora.karaoke_view_ex.internal.ScoringMachine
import io.agora.karaoke_view_ex.internal.constants.LyricType
import io.agora.karaoke_view_ex.model.LyricModel
import io.agora.mediaplayer.Constants.MediaPlayerState
import io.agora.musiccontentcenter.IAgoraMusicPlayer
import io.agora.rtc2.IRtcEngineEventHandler
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live acceptance: uses the configured account, real SDK and real microphone. */
@RunWith(AndroidJUnit4::class)
class NativeMccPlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val evidence get() = File(context.cacheDir, "native-mcc-verification").apply { mkdirs() }

    @Before fun requireExplicitLiveRun() {
        assumeTrue("Pass -e nativeMccLive true to access the live music service",
            InstrumentationRegistry.getArguments().getString("nativeMccLive") == "true")
    }

    @Test fun loveTransfer() = verifySong(0)
    @Test fun sayLoveYou() = verifySong(1)
    @Test fun loveStory() = verifySong(2)

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun waitFor(message: String, timeoutMs: Long = 60_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    private fun verifySong(position: Int) {
        val song = LyricsResourcePool.asMusicList()[position]
        val report = StringBuilder("song=${song.songName}; songCode=${song.songCode}\n")
        val prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        val serviceKey = context.getString(R.string.prefs_key_service_type)
        val lyricKey = context.getString(R.string.prefs_key_lyric_type)
        val originalService = if (prefs.contains(serviceKey)) prefs.getInt(serviceKey, -1) else null
        val originalLyric = if (prefs.contains(lyricKey)) prefs.getInt(lyricKey, -1) else null
        prefs.edit().putInt(serviceKey, ServiceType.MCC.type).putInt(lyricKey, LyricType.XML.ordinal).commit()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var player: IAgoraMusicPlayer
                scenario.onActivity {
                    assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
                    assertNull(field(ServiceManager, "mMccExManager"))
                    player = field(requireNotNull(field(ServiceManager, "mMccManager")), "mAgoraMusicPlayer") as IAgoraMusicPlayer
                }
                val pitchCallbacks = AtomicInteger()
                val nonzeroPitch = AtomicInteger()
                val observer = object : IRtcEngineEventHandler() {
                    override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
                        speakers?.filter { it.uid == 0 }?.forEach {
                            pitchCallbacks.incrementAndGet()
                            if (it.voicePitch > 0) nonzeroPitch.incrementAndGet()
                        }
                    }
                }
                RtcManager.getRtcEngine().addHandler(observer)
                try {
                    onView(withText("选歌")).perform(click())
                    onView(withText(song.songName)).inRoot(isDialog()).perform(click())
                    waitFor("${song.songName}: native player did not reach PLAYING; state=${player.state}") {
                        player.state == MediaPlayerState.PLAYER_STATE_PLAYING
                    }
                    report.append("player=PLAYING; streamCount=${player.streamCount}\n")
                    lateinit var model: LyricModel
                    lateinit var machine: ScoringMachine
                    scenario.onActivity { activity ->
                        model = field(activity, "mLyricsModel") as LyricModel
                        machine = field(field(activity, "mKaraokeView") as KaraokeView, "mScoringMachine") as ScoringMachine
                        assertEquals(LyricType.XML, model.type)
                        assertTrue(model.lines.isNotEmpty())
                        assertTrue("XML must contain reference pitches", model.hasPitch)
                        assertEquals(true, field(activity, "mUseInternalScoring"))
                        assertEquals(true, field(machine, "mUsingInternalScoring"))
                    }
                    report.append("lyric=${model.type}; lines=${model.lines.size}; hasPitch=${model.hasPitch}; internalScoring=true\n")
                    waitFor("Playback position did not advance", 5_000) { player.playPosition > 800 }
                    onView(withId(R.id.pause)).perform(click())
                    waitFor("Pause failed", 5_000) { player.state == MediaPlayerState.PLAYER_STATE_PAUSED }
                    val pausedAt = player.playPosition
                    SystemClock.sleep(350)
                    assertTrue(kotlin.math.abs(player.playPosition - pausedAt) <= 200)
                    onView(withId(R.id.pause)).perform(click())
                    waitFor("Resume failed", 5_000) { player.state == MediaPlayerState.PLAYER_STATE_PLAYING }
                    waitFor("Resumed position did not advance", 5_000) { player.playPosition > pausedAt + 300 }
                    report.append("pause/resume=PASS\n")
                    onView(withId(R.id.play_original)).perform(click())
                    assertFalse(ServiceManager.isOriginalPlay())
                    onView(withId(R.id.play_original)).perform(click())
                    assertTrue(ServiceManager.isOriginalPlay())
                    report.append("original/accompaniment controls=PASS (inspect SDK return codes in logcat)\n")
                    // Exercise the real touch path and verify it results in a native-player seek.
                    val draggingKey = context.getString(R.string.prefs_key_lyrics_dragging_switch)
                    if (prefs.getBoolean(draggingKey, true)) {
                        val beforeDrag = player.playPosition
                        onView(withId(R.id.lyrics_view)).perform(swipeUp())
                        waitFor("Lyric drag did not seek the native player", 5_000) {
                            player.playPosition > beforeDrag + 2_000
                        }
                        report.append("lyricDrag=PASS; before=$beforeDrag; after=${player.playPosition}\n")
                    } else {
                        report.append("lyricDrag=SKIPPED (user preference disables dragging)\n")
                    }
                    val firstSungIndex = model.lines.indexOfFirst { !it.tones.isNullOrEmpty() }
                    assertTrue(firstSungIndex >= 0)
                    val firstSung = model.lines[firstSungIndex]
                    ServiceManager.seek(firstSung.startTime)
                    waitFor("Seek did not reach first sung line", 5_000) {
                        kotlin.math.abs(player.playPosition - firstSung.startTime) < 2_000
                    }
                    waitFor("First sung line did not finish playing", 20_000) {
                        player.playPosition > firstSung.endTime + 500
                    }
                    waitFor("First sung line did not produce an internal score", 5_000) {
                        var scored = false
                        scenario.onActivity { scored = machine.mScoreForEachLine.containsKey(firstSungIndex) }
                        scored
                    }
                    scenario.onActivity { report.append("lineScores=${machine.mScoreForEachLine}\n") }
                    report.append("liveMicrophonePitchCallbacks=${pitchCallbacks.get()}; nonzeroPitchCallbacks=${nonzeroPitch.get()}\n")
                    assertTrue("No live microphone pitch callbacks", pitchCallbacks.get() > 0)
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    File(evidence, "song-$position.png").outputStream().use {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                    onView(withId(R.id.pause)).perform(click())
                    report.append("result=PASS\n")
                } finally {
                    RtcManager.getRtcEngine().removeHandler(observer)
                    report.append("finalPlayerState=${player.state}; position=${player.playPosition}\n")
                }
            }
        } catch (failure: Throwable) {
            report.append("result=FAIL; ${failure.javaClass.simpleName}: ${failure.message}\n")
            throw failure
        } finally {
            File(evidence, "song-$position.txt").writeText(report.toString())
            Log.i("NativeMccAcceptance", report.toString())
            val editor = prefs.edit()
            if (originalService == null) editor.remove(serviceKey) else editor.putInt(serviceKey, originalService)
            if (originalLyric == null) editor.remove(lyricKey) else editor.putInt(lyricKey, originalLyric)
            editor.commit()
        }
    }
}
