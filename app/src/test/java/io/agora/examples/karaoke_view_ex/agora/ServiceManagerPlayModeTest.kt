package io.agora.examples.karaoke_view_ex.agora

import io.agora.examples.utils.ServiceType
import io.agora.musiccontentcenter.IAgoraMusicPlayer
import io.agora.musiccontentcenter.IAgoraMusicPlayer.MusicPlayMode
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercises production service/player adaptation without creating an RTC engine. */
class ServiceManagerPlayModeTest {
    private lateinit var manager: MccManager
    private var previousManager: Any? = null
    private var previousService: ServiceType? = null
    private var previousOriginal = true
    private var returnCode = 0
    private val calls = mutableListOf<String>()
    private val modes = mutableListOf<MusicPlayMode>()

    private fun serviceField(name: String) = ServiceManager::class.java.getDeclaredField(name)
        .apply { isAccessible = true }
    private fun playerField() = MccManager::class.java.getDeclaredField("mAgoraMusicPlayer")
        .apply { isAccessible = true }

    @Before fun setUp() {
        previousManager = serviceField("mMccManager").get(null)
        previousService = ServiceManager.getServiceType()
        previousOriginal = ServiceManager.isOriginalPlay()
        manager = MccManager(null, object : MccManager.MccCallback {}, false)
        val player = Proxy.newProxyInstance(IAgoraMusicPlayer::class.java.classLoader,
            arrayOf(IAgoraMusicPlayer::class.java)) { _, method, args ->
            calls += method.name
            if (method.name == "setPlayMode") {
                modes += args!![0] as MusicPlayMode
                return@newProxyInstance returnCode
            }
            when (method.returnType) {
                Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Boolean.TYPE -> false
                else -> null
            }
        } as IAgoraMusicPlayer
        playerField().set(manager, player)
        serviceField("mMccManager").set(null, manager)
        serviceField("mIsOriginal").setBoolean(null, true)
        ServiceManager.setServiceType(ServiceType.MCC)
    }

    @After fun tearDown() {
        manager.destroy()
        serviceField("mMccManager").set(null, previousManager)
        serviceField("mServiceType").set(null, previousService)
        serviceField("mIsOriginal").setBoolean(null, previousOriginal)
    }

    @Test fun bothServicesSwitchThroughRtcEnumsWithoutManualTrackSelection() {
        for (service in listOf(ServiceType.MCC, ServiceType.TME)) {
            ServiceManager.setServiceType(service)
            ServiceManager.doPlayOriginal()
            assertFalse(ServiceManager.isOriginalPlay())
            ServiceManager.doPlayOriginal()
            assertTrue(ServiceManager.isOriginalPlay())
        }
        assertEquals(listOf(MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY,
            MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL,
            MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY,
            MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL), modes)
        assertEquals(listOf("setPlayMode", "setPlayMode", "setPlayMode", "setPlayMode"), calls)
    }

    @Test fun rejectedSwitchKeepsOriginalAndRetriesTheSameMode() {
        returnCode = -2
        ServiceManager.doPlayOriginal()
        assertTrue("Rejected accompaniment must preserve original state", ServiceManager.isOriginalPlay())
        returnCode = 0
        ServiceManager.doPlayOriginal()
        assertFalse(ServiceManager.isOriginalPlay())
        assertEquals(listOf(MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY,
            MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY), modes)
    }

    @Test fun missingPlayerPreservesMode() {
        playerField().set(manager, null)
        ServiceManager.doPlayOriginal()
        assertTrue(ServiceManager.isOriginalPlay())
        assertTrue(calls.isEmpty())
    }

    @Test fun missingManagerPreservesMode() {
        serviceField("mMccManager").set(null, null)
        ServiceManager.doPlayOriginal()
        assertTrue(ServiceManager.isOriginalPlay())
        assertTrue(calls.isEmpty())
    }

    @Test fun rejectedReplayKeepsAccompaniment() {
        serviceField("mIsOriginal").setBoolean(null, false)
        returnCode = -2
        assertFalse("Do not start a song if restoring original mode is rejected", ServiceManager.play())
        assertFalse(ServiceManager.isOriginalPlay())
        assertEquals(listOf(MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL), modes)
    }

    @Test fun replayWithoutManagerKeepsAccompaniment() {
        serviceField("mIsOriginal").setBoolean(null, false)
        serviceField("mMccManager").set(null, null)
        assertFalse(ServiceManager.play())
        assertFalse(ServiceManager.isOriginalPlay())
        assertTrue(calls.isEmpty())
    }

    @Test fun tmeStartsInOriginalBeforePlaying() {
        assertEquals(0, manager.playTme())
        assertEquals(listOf(MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL), modes)
        assertEquals(listOf("setPlayMode", "play"), calls)
    }

    @Test fun tmeRejectsPlaybackIfOriginalModeIsRejected() {
        returnCode = -2
        assertEquals(-2, manager.playTme())
        assertEquals(listOf(MusicPlayMode.MUSIC_PLAY_MODE_ORIGINAL), modes)
        assertEquals(listOf("setPlayMode"), calls)
    }

    @Test fun tmeResumePreservesTheSelectedMode() {
        ServiceManager.setServiceType(ServiceType.TME)
        ServiceManager.doPlayOriginal()
        assertEquals(0, manager.resumeTme())
        assertFalse(ServiceManager.isOriginalPlay())
        assertEquals(listOf(MusicPlayMode.MUSIC_PLAY_MODE_ACCOMPANY), modes)
        assertEquals(listOf("setPlayMode", "resume"), calls)
    }
}
