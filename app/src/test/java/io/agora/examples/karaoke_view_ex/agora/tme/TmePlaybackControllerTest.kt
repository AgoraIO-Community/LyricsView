package io.agora.examples.karaoke_view_ex.agora.tme

import org.junit.Assert.*
import org.junit.Test

class TmePlaybackControllerTest {
    private class Clock : TmePlaybackController.Scheduler {
        data class Task(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        val tasks = mutableListOf<Task>()
        override fun schedule(delayMs: Long, action: () -> Unit): TmePlaybackController.Cancel {
            val task = Task(now + delayMs, action); tasks += task
            return TmePlaybackController.Cancel { task.cancelled = true }
        }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val task = tasks.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                tasks.remove(task); now = task.at; task.action()
            }
            now = end
        }
    }
    private class Media : TmePlaybackController.MediaPort {
        var plays = 0; var opens = 0; var pauses = 0; var resumes = 0; var releases = 0
        var position = 0L; var playResult = 0; var openResult = 0
        var pauseResult = 0
        var positionHook: (() -> Unit)? = null
        override fun preload(sessionId: String, songId: String) = TmePlaybackController.PreloadRequest(42, "preload")
        override fun open(sessionId: String, songCode: Long): Int { opens++; return openResult }
        override fun play(): Int { plays++; return playResult }
        override fun pause(): Int { pauses++; return pauseResult }
        override fun resume(): Int { resumes++; return 0 }
        override fun position(): Long { positionHook?.invoke(); return position }
        override fun release() { releases++ }
    }
    private class Preparation : TmePlaybackController.PreparationPort<String> {
        lateinit var callback: TmePlaybackController.PreparationListener<String>
        var starts = 0; var cancellations = 0
        override fun start(songId: String, listener: TmePlaybackController.PreparationListener<String>) { starts++; callback = listener }
        override fun cancel() { cancellations++ }
    }
    private class Page : TmePlaybackController.Listener<String> {
        val inputs = mutableListOf<String>()
        lateinit var state: TmePlaybackController.State<String>
        var stateHook: ((TmePlaybackController.State<String>) -> Unit)? = null
        override fun onState(state: TmePlaybackController.State<String>) { this.state = state; stateHook?.invoke(state) }
        override fun onPosition(positionMs: Long, scoring: Boolean) { inputs += "position:$positionMs:$scoring" }
        override fun onPitch(pitch: Double) { inputs += "pitch:$pitch" }
    }
    private class Fixture {
        val clock = Clock(); val media = Media(); val preparation = Preparation(); val page = Page()
        val controller = TmePlaybackController("session", "original-id", "Song", media, preparation, clock)
        init { controller.attach(page); controller.start() }
        fun preload() { controller.onPreload("session", "preload", 42, 100, 0, 0) }
        fun opened() { controller.onPlayer("session", TmePlaybackController.PlayerEvent.OPENED) }
        fun ready() { preparation.callback.onReady("model", 3); controller.acknowledgeModel(page) }
        fun playing() { controller.onPlayer("session", TmePlaybackController.PlayerEvent.PLAYING) }
    }
    @Test fun mediaFirstWaitsForModelAcknowledgementAndOnlyPlaysOnce() {
        val f = Fixture(); f.preload(); f.opened(); f.preparation.callback.onReady("model", 3)
        assertEquals(0, f.media.plays); f.controller.acknowledgeModel(f.page)
        f.opened(); f.preload(); f.controller.acknowledgeModel(f.page)
        assertEquals(1, f.media.plays); assertEquals(1, f.media.opens)
        assertEquals(300, f.page.state.fullScore)
    }
    @Test fun dataFirstWaitsForMediaAndForeground() {
        val f = Fixture(); f.preload(); f.ready(); f.controller.detach(f.page); f.opened()
        assertEquals(0, f.media.plays); f.controller.attach(f.page)
        f.controller.acknowledgeModel(f.page); assertEquals(1, f.media.plays)
    }
    @Test fun preparationFailureWaitsForHiddenUiAndNeverReenablesScoring() {
        val f = Fixture(); f.preload(); f.opened(); f.preparation.callback.onFailure("DOWNLOAD_FAILED")
        assertEquals(0, f.media.plays); f.controller.acknowledgeUnscored(f.page)
        assertEquals(1, f.media.plays); f.preparation.callback.onReady("late", 9); f.playing()
        f.controller.onLocalPitch(220.0); assertFalse(f.page.state.scoring)
        assertFalse(f.page.inputs.any { it.startsWith("pitch") })
        assertNull(f.page.state.model)
    }
    @Test fun staleAndDuplicatePreloadAndPlayerEventsCannotStartPlayback() {
        val f = Fixture()
        f.controller.onPreload("old", "preload", 42, 100, 0, 0)
        f.controller.onPreload("session", "other", 42, 100, 0, 0)
        f.controller.onPreload("session", "preload", 99, 100, 0, 0)
        assertEquals(0, f.media.opens); f.preload(); f.ready()
        f.controller.onPlayer("old", TmePlaybackController.PlayerEvent.OPENED)
        assertEquals(0, f.media.plays); f.opened(); assertEquals(1, f.media.plays)
    }
    @Test fun actualProgressPrecedesPitchAndPauseStopsInputsUntilPlaying() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing()
        f.page.inputs.clear(); f.media.position = 345L; f.controller.onLocalPitch(220.0)
        assertEquals(listOf("position:345:true", "pitch:220.0"), f.page.inputs)
        f.controller.pause(); f.page.inputs.clear(); f.controller.onLocalPitch(220.0); f.clock.advance(100)
        assertTrue(f.page.inputs.isEmpty()); f.controller.resume(); f.controller.onLocalPitch(220.0)
        assertTrue(f.page.inputs.isEmpty()); f.playing(); f.controller.onLocalPitch(230.0)
        assertEquals("pitch:230.0", f.page.inputs.last())
    }
    @Test fun backgroundRequiresExplicitResumeAndOldDetachCannotRemoveNewPage() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing(); f.controller.detach(f.page)
        val next = Page(); f.controller.attach(next); f.controller.detach(f.page)
        assertEquals(1, f.media.pauses); assertEquals(0, f.media.resumes)
        f.controller.resume(); assertEquals(1, f.media.resumes)
    }
    @Test fun mediaFailuresAndTimeoutsNeverBecomeUnscoredPlayback() {
        val preload = Fixture(); preload.clock.advance(120_000)
        assertEquals(TmePlaybackController.Phase.FAILED, preload.page.state.phase)
        val open = Fixture(); open.media.openResult = -1; open.preload()
        assertEquals(TmePlaybackController.Phase.FAILED, open.page.state.phase)
        val timeout = Fixture(); timeout.preload(); timeout.clock.advance(30_000)
        assertEquals(TmePlaybackController.Phase.FAILED, timeout.page.state.phase)
        val play = Fixture(); play.media.playResult = -1; play.preload(); play.ready(); play.opened()
        assertEquals(TmePlaybackController.Phase.FAILED, play.page.state.phase)
        val playing = Fixture(); playing.preload(); playing.ready(); playing.opened(); playing.clock.advance(15_000)
        assertEquals(TmePlaybackController.Phase.FAILED, playing.page.state.phase)
    }
    @Test fun completionPreservesScoresAndCancelRejectsAllLateCallbacks() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing()
        f.controller.onLineFinished(81, 162, 3)
        f.controller.onPlayer("session", TmePlaybackController.PlayerEvent.COMPLETED)
        assertEquals(162, f.page.state.cumulativeScore); f.controller.resume(); assertEquals(0, f.media.resumes)
        f.controller.close(); f.preparation.callback.onReady("late", 9); f.opened(); f.clock.advance(200_000)
        assertEquals(1, f.media.plays); assertEquals(1, f.media.releases)
    }
    @Test fun preloadNotificationReentryCannotOpenTwice() {
        val f = Fixture(); var repeated = false
        f.page.stateHook = { if (it.preloadPercent == 100 && !repeated) { repeated = true; f.preload() } }
        f.preload(); assertEquals(1, f.media.opens); assertEquals(1, f.preparation.starts)
    }
    @Test fun positionPortReentryCannotDeliverStaleInputs() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing(); f.page.inputs.clear()
        f.media.positionHook = { f.controller.close() }; f.controller.onLocalPitch(220.0)
        assertTrue(f.page.inputs.isEmpty())
        val tick = Fixture(); tick.preload(); tick.ready(); tick.opened(); tick.playing(); tick.page.inputs.clear()
        tick.media.positionHook = { tick.controller.detach(tick.page) }; tick.clock.advance(20)
        assertTrue(tick.page.inputs.isEmpty())
    }
    @Test fun resumeDuringPauseNotificationDoesNotExecuteOldPause() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing()
        f.page.stateHook = { if (it.phase == TmePlaybackController.Phase.PAUSED) f.controller.resume() }
        f.controller.pause(); assertEquals(1, f.media.resumes); assertEquals(0, f.media.pauses)
    }
    @Test fun replacementPageGetsNoScoringInputUntilModelAcknowledged() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing()
        val next = Page(); f.controller.attach(next); f.controller.onLocalPitch(220.0); f.clock.advance(20)
        assertTrue(next.inputs.isEmpty()); f.controller.acknowledgeModel(next)
        f.controller.onLocalPitch(220.0); assertEquals("pitch:220.0", next.inputs.last())
    }
    @Test fun pageReplacementDuringStartingStillRequiresItsOwnAcknowledgement() {
        val f = Fixture(); val next = Page(); var replaced = false
        f.page.stateHook = { if (it.phase == TmePlaybackController.Phase.STARTING && !replaced) {
            replaced = true; f.controller.attach(next)
        } }
        f.preload(); f.ready(); f.opened(); assertEquals(0, f.media.plays)
        f.controller.acknowledgeModel(next); assertEquals(1, f.media.plays)
    }
    @Test fun detachDuringFirstStartingDoesNotResumeAnUnplayedPlayer() {
        val f = Fixture(); var detached = false
        f.page.stateHook = { if (it.phase == TmePlaybackController.Phase.STARTING && !detached) {
            detached = true; f.controller.detach(f.page)
        } }
        f.preload(); f.ready(); f.opened(); assertEquals(0, f.media.plays)
        f.controller.attach(f.page); f.controller.acknowledgeModel(f.page)
        assertEquals(1, f.media.plays); assertEquals(0, f.media.resumes)
    }
    @Test fun unscoredReplacementAcknowledgementRestartsActualPositionPolling() {
        val f = Fixture(); f.preload(); f.opened(); f.preparation.callback.onFailure("DOWNLOAD")
        f.controller.acknowledgeUnscored(f.page); f.playing()
        val next = Page(); f.controller.attach(next); f.clock.advance(20)
        f.controller.acknowledgeUnscored(next); f.media.position = 812; f.clock.advance(20)
        assertEquals(listOf("position:812:false"), next.inputs)
    }
    @Test fun failedLatePlayingCompensationBecomesMediaError() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing(); f.controller.pause()
        f.media.pauseResult = -1; f.playing()
        assertEquals(TmePlaybackController.Phase.FAILED, f.page.state.phase)
    }
    @Test fun nestedStartupSupersedesOlderPlayAttempt() {
        val f = Fixture(); var paused = false
        f.page.stateHook = {
            if (it.phase == TmePlaybackController.Phase.STARTING && !paused) {
                paused = true; f.controller.pause()
            } else if (it.phase == TmePlaybackController.Phase.WAITING_FOR_PAGE && paused && f.media.plays == 0) {
                f.controller.acknowledgeModel(f.page)
            }
        }
        f.preload(); f.ready(); f.opened(); assertEquals(1, f.media.plays)
    }
    @Test fun newerResumeAttemptSupersedesEarlierNotification() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing(); f.controller.pause()
        var pausedAgain = false
        f.page.stateHook = {
            if (it.phase == TmePlaybackController.Phase.STARTING && !pausedAgain) {
                pausedAgain = true; f.controller.pause()
            } else if (it.phase == TmePlaybackController.Phase.PAUSED && pausedAgain) f.controller.resume()
        }
        f.controller.resume(); assertEquals(1, f.media.resumes)
    }
    @Test fun realMicrophoneSilenceIsForwardedForNaturalLineCompletion() {
        val f = Fixture(); f.preload(); f.ready(); f.opened(); f.playing(); f.page.inputs.clear()
        f.media.position = 1500; f.controller.onLocalPitch(0.0)
        assertEquals(listOf("position:1500:true", "pitch:0.0"), f.page.inputs)
        f.page.inputs.clear(); f.controller.onLocalPitch(Double.NaN); f.controller.onLocalPitch(-1.0)
        assertTrue(f.page.inputs.isEmpty())
    }
}
