package io.agora.karaoke_view_ex.tme;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import io.agora.karaoke_view_ex.internal.ScoringMachine;
import io.agora.karaoke_view_ex.internal.ai.AIAlgorithmScoreNative;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.internal.model.TMELyricModel;
import io.agora.karaoke_view_ex.internal.scoring.DefaultScoringAlgorithm;
import io.agora.karaoke_view_ex.model.LyricModel;
import static org.junit.Assert.*;

public class TMEToneScoringTest extends TMEFileTestSupport {
    private static class Listener implements ScoringMachine.OnScoringListener {
        final List<LyricsLineModel> lines = new ArrayList<>();
        final List<Integer> scores = new ArrayList<>(), totals = new ArrayList<>(), indexes = new ArrayList<>(), counts = new ArrayList<>();
        public void resetUi() {}
        public void requestRefreshUi() {}
        public void onPitchAndScoreUpdate(float pitch, double score, long progress) {}
        public void onLineFinished(LyricsLineModel line, int score, int total, int index, int count) {
            lines.add(line); scores.add(score); totals.add(total); indexes.add(index); counts.add(count);
        }
    }

    private void sing(ScoringMachine machine, int time) {
        machine.setLyricProgress(time);
        machine.setPitch(machine.getRefPitch(time), 0, time);
    }

    @Test public void callbacksOnlyForScorableLinesWithOriginalModelsAndRealNativeScores() throws Exception {
        LyricModel model = parse("[{\"st\":1000,\"d\":500,\"p\":44},{\"st\":3000,\"d\":500,\"p\":56}]",
                "[00:00.00]作词：某人\n[00:01.00]第一句\n[00:02.00]空白间隔\n[00:03.00]第二句");
        Listener listener = new Listener();
        ScoringMachine machine = new ScoringMachine(listener);
        machine.prepare(model, true);
        sing(machine, 1100);
        sing(machine, 2000);
        sing(machine, 2000);
        assertEquals(1, listener.lines.size());
        sing(machine, 3100);
        sing(machine, 3500);
        assertEquals(2, listener.lines.size());
        assertSame(model.lines.get(1), listener.lines.get(0));
        assertSame(model.lines.get(3), listener.lines.get(1));
        assertEquals(Integer.valueOf(100), listener.scores.get(0));
        assertEquals(Integer.valueOf(100), listener.scores.get(1));
        assertEquals(Integer.valueOf(200), listener.totals.get(1));
        assertEquals(Integer.valueOf(0), listener.indexes.get(0));
        assertEquals(Integer.valueOf(1), listener.indexes.get(1));
        assertEquals(Integer.valueOf(2), listener.counts.get(0));
        assertEquals(4, machine.getPitchLines().size());
        assertTrue(machine.getPitchLines().get(0).pitches.isEmpty());
        assertEquals(55 * (2 - 1e-6), machine.getPitchLines().get(1).pitches.get(0).getFrequency(), 0.00001);
    }

    @Test public void boundaryBelongsToNextNoteAndFinalLineClosesOnLateUpdate() throws Exception {
        LyricModel model = parse("[{\"st\":1000,\"d\":1000,\"p\":44},{\"st\":2000,\"d\":500,\"p\":56}]",
                "[00:01.00]第一句\n[00:02.00]第二句");
        Listener listener = new Listener();
        ScoringMachine machine = new ScoringMachine(listener);
        machine.prepare(model, true);
        assertEquals(55 * (4 - 1e-6), machine.getRefPitch(2000), 0.0001);
        sing(machine, 1100);
        sing(machine, 2000);
        assertEquals(1, listener.lines.size());
        sing(machine, 5000);
        assertEquals(2, listener.lines.size());
        assertEquals(Integer.valueOf(100), listener.scores.get(1));
        assertEquals(0, machine.getRefPitch(2500), 0);
    }

    @Test public void missingNotesAreZeroAndNotesAverageEqually() throws Exception {
        TMELyricModel model = (TMELyricModel) parse(
                "[{\"st\":1000,\"d\":200,\"p\":44},{\"st\":1200,\"d\":200,\"p\":56}]", "[00:01.00]一句");
        Listener listener = new Listener();
        ScoringMachine machine = new ScoringMachine(listener);
        machine.prepare(model, true);
        sing(machine, 1100);
        sing(machine, 1400);
        assertEquals(Integer.valueOf(50), listener.scores.get(0));
        java.util.LinkedHashMap<Long, Float> samples = new java.util.LinkedHashMap<>();
        samples.put(1200L, 100f);
        assertEquals(50, new DefaultScoringAlgorithm().getLineScore(samples, 0, model.scoringLines.get(0)));
    }

    @Test public void seekingReopensScoringLinesAndKeepsEarlierTotals() throws Exception {
        LyricModel model = parse("[{\"st\":1000,\"d\":1000,\"p\":44},{\"st\":2000,\"d\":1000,\"p\":56}]",
                "[00:01.00]第一句\n[00:02.00]第二句");
        Listener listener = new Listener();
        ScoringMachine machine = new ScoringMachine(listener);
        machine.prepare(model, true);
        sing(machine, 1100); sing(machine, 2100); sing(machine, 3000);
        machine.whenDraggingHappen(2200);
        sing(machine, 2300); sing(machine, 3000);
        assertEquals(Integer.valueOf(200), listener.totals.get(listener.totals.size() - 1));
        assertEquals(Integer.valueOf(1), listener.indexes.get(listener.indexes.size() - 1));
    }

    @Test public void legacyModelsKeepInclusivePitchBoundary() {
        LyricsLineModel.Tone tone = new LyricsLineModel.Tone();
        tone.begin = 1000; tone.end = 2000; tone.pitch = 110; tone.word = "旧格式";
        LyricModel legacy = new LyricModel(LyricType.XML);
        legacy.lines = java.util.Collections.singletonList(new LyricsLineModel(tone));
        legacy.hasPitch = true; legacy.preludeEndPosition = 1000;
        ScoringMachine machine = new ScoringMachine(new Listener());
        machine.prepare(legacy, true);
        assertEquals(110, machine.getRefPitch(2000), 0);
        assertEquals(0, machine.getRefPitch(2001), 0);
        assertFalse(legacy.copy() instanceof TMELyricModel);
        assertEquals(100, AIAlgorithmScoreNative.calculatedScore(110, 110, 15, 0), 0);
    }
}
