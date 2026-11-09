package org.cha0scollective.wwpg.experimental;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedTaskWindowTest {
    @Test void finishesAnAtomicTaskThenYieldsWithoutReordering() {
        var clock = new AtomicLong();
        var window = new BoundedTaskWindow(clock::get);
        var pending = new ArrayDeque<>(List.of(1, 2, 3));
        var seen = new ArrayList<Integer>();
        var slice = window.run(10, 512, () -> {
            assertTrue(window.allowsTime());
            seen.add(pending.remove()); clock.addAndGet(7); return true;
        }, () -> fail("Queue was not empty"));
        assertEquals(List.of(1, 2), seen);
        assertEquals(3, pending.remove());
        assertEquals(new BoundedTaskWindow.Slice(2, 0, 14, 7), slice);
        assertFalse(window.allowsTime());
    }

    @Test void acceptsArrivalsDuringEmptyPollsAndRespectsTaskLimit() {
        var clock = new AtomicLong();
        var window = new BoundedTaskWindow(clock::get);
        var pending = new ArrayDeque<Integer>();
        var seen = new ArrayList<Integer>();
        var slice = window.run(100, 2, () -> {
            clock.incrementAndGet();
            if (pending.isEmpty()) return false;
            seen.add(pending.remove()); return true;
        }, () -> pending.addAll(List.of(1, 2, 3)));
        assertEquals(List.of(1, 2), seen);
        assertEquals(3, pending.remove());
        assertEquals(2, slice.completed()); assertEquals(1, slice.emptyPolls());
        assertTrue(slice.elapsedNanos() < 100);
    }

    @Test void allowanceIsOwnerScopedAndClearedAfterFailure() {
        var clock = new AtomicLong();
        var window = new BoundedTaskWindow(clock::get);
        var otherAllowed = new AtomicBoolean(true);
        var failure = new IllegalStateException("callback failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> window.run(10, 5, () -> {
            var other = new Thread(() -> otherAllowed.set(window.allowsTime()));
            other.start();
            try { other.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            assertEquals(0, window.run(100, 1, () -> fail("Nested dispatch"), () -> {}).completed());
            assertTrue(window.allowsTime());
            clock.set(10); assertFalse(window.allowsTime());
            throw failure;
        }, () -> {})));
        assertFalse(otherAllowed.get()); assertFalse(window.allowsTime());
        assertEquals(1, window.run(10, 1, () -> true, () -> {}).completed());
    }

    @Test void disabledAndEmptyWindowsDoNotCreateUnboundedWork() {
        var clock = new AtomicLong();
        var window = new BoundedTaskWindow(clock::get);
        assertEquals(0, window.run(0, 1, () -> fail("Disabled dispatch"), () -> {}).completed());
        var slice = window.run(10, 2, () -> false, clock::incrementAndGet);
        assertEquals(10, slice.emptyPolls()); assertEquals(10, slice.elapsedNanos());
        assertFalse(window.allowsTime());
    }
}
