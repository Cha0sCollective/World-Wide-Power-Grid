package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticHistoryTest {
    private record Failure(int circuit, String reason) { }

    @Test void twentySevenCircuitsKeepAllRejectionsWithoutPerSubstepFormatting() {
        var logs = new ArrayList<String>(); var descriptions = new AtomicInteger();
        var history = new DiagnosticHistory<Failure>((key, step) -> {
            descriptions.incrementAndGet(); return key + "; substep=" + step;
        }, logs::add);
        for (int tick = 0; tick < 200; tick++) for (int step = 0; step < 16; step++)
            for (int circuit = 0; circuit < 27; circuit++)
                history.record(new Failure(circuit,"nonconvergence"), tick, tick * 16L + step, step);
        assertEquals(8, logs.size()); assertEquals(8, descriptions.get());
        history.advance(200);
        assertEquals(9, logs.size());
        assertTrue(logs.getLast().contains("additionalOccurrences=86392"));
        var snapshot = history.snapshot(); assertEquals(27, snapshot.size());
        assertTrue(snapshot.stream().allMatch(line -> line.contains("count=3200:")));
    }

    @Test void historyEvictionCannotRestartTheWarningBudget() {
        var logs = new ArrayList<String>(); var descriptions = new AtomicInteger();
        var history = new DiagnosticHistory<Integer>((key, step) -> {
            descriptions.incrementAndGet(); return "failure=" + key;
        }, logs::add);
        for (int tick = 0; tick < 20; tick++) for (int key = 0; key < 160; key++)
            history.record(key,tick,tick,0);
        assertEquals(8, logs.size()); assertEquals(8, descriptions.get());
        history.flush(); assertEquals(9, logs.size());
        assertTrue(logs.getLast().contains("additionalOccurrences=3192"));
        assertTrue(logs.getLast().contains("newlyRecorded=3192"));
        var snapshot = history.snapshot(); assertEquals(129,snapshot.size());
        assertTrue(snapshot.getLast().contains("historyEvictions=3072"));
        assertTrue(snapshot.getLast().contains("totalOccurrences=3200"));
    }

    @Test void aGapInRejectedSolveStampsReportsTheSameFailureAgain() {
        var logs = new ArrayList<String>();
        var history = new DiagnosticHistory<String>((key, step) -> key+" step="+step,logs::add);
        history.record("circuit",1,10,0); history.record("circuit",1,11,1);
        history.record("circuit",1,14,4);
        assertEquals(2,logs.size()); assertEquals("circuit step=4",logs.getLast());
        assertTrue(history.snapshot().getFirst().contains("count=3:"));
    }

    @Test void aDifferentFailureCauseHasItsOwnNoticeAndHistory() {
        var logs = new ArrayList<String>();
        var history = new DiagnosticHistory<Failure>((key, step) -> key.reason,logs::add);
        history.record(new Failure(1,"nonconvergence"),1,10,0);
        history.record(new Failure(1,"provider failure"),1,11,1);
        assertEquals(2,logs.size()); assertEquals(2,history.snapshot().size());
    }

    @Test void periodicReportsRefreshContextAndRetainTheCompleteCount() {
        var logs = new ArrayList<String>();
        var history = new DiagnosticHistory<String>((key, step) -> key+" step="+step,logs::add);
        history.record("fault",0,1,1); history.record("fault",1,2,2);
        history.advance(200); history.record("fault",200,3,3);
        assertEquals(3,logs.size()); assertTrue(logs.get(1).contains("additionalOccurrences=1"));
        assertEquals("fault step=3",logs.getLast());
        assertTrue(history.snapshot().getFirst().contains("firstTick=0, lastTick=200, count=3:"));
        assertTrue(history.snapshot().getFirst().endsWith("fault step=1"),"First reported context was lost");
    }

    @Test void shutdownFlushesOnceAndSnapshotsAreImmutable() {
        var logs = new ArrayList<String>();
        var history = new DiagnosticHistory<String>((key, step) -> key,logs::add);
        history.record("fault",0,-1,-1); var original=history.snapshot();
        history.record("fault",1,-1,-1); history.flush(); history.flush();
        assertEquals(2,logs.size()); assertTrue(original.getFirst().contains("count=1:"));
        assertTrue(history.snapshot().getFirst().contains("count=2:"));
        assertThrows(UnsupportedOperationException.class,()->original.add("mutate"));
    }

    @Test void longDetailsStayBoundedAndQuietWindowsDoNotWarn() {
        var logs = new ArrayList<String>();
        var history = new DiagnosticHistory<String>((key, step) -> key,logs::add);
        history.advance(200); history.advance(400); assertTrue(logs.isEmpty());
        history.record("x".repeat(9000),400,-1,-1);
        assertEquals(2048,logs.getFirst().length());
        history.advance(600); assertEquals(1,logs.size());
    }
}
