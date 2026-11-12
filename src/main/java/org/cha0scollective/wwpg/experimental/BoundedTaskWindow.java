package org.cha0scollective.wwpg.experimental;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Cooperative, owner-thread-only allowance for existing task dispatch. */
public final class BoundedTaskWindow {
    public record Slice(int completed, int emptyPolls, long elapsedNanos, long longestPollNanos) { }
    private static final Slice EMPTY = new Slice(0, 0, 0, 0);
    private final LongSupplier clock;
    private volatile Thread owner;
    private long started, budget;

    public BoundedTaskWindow(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    public boolean allowsTime() {
        return owner == Thread.currentThread() && clock.getAsLong() - started < budget;
    }

    public Slice run(long budgetNanos, int maxTasks, BooleanSupplier poll, Runnable idle) {
        if (budgetNanos <= 0 || owner != null) return EMPTY;
        if (maxTasks < 1) throw new IllegalArgumentException("Task limit must be positive");
        started = clock.getAsLong();
        budget = budgetNanos;
        owner = Thread.currentThread();
        int completed = 0, empty = 0;
        long longest = 0;
        try {
            while (completed < maxTasks && allowsTime()) {
                long before = clock.getAsLong();
                boolean ran = poll.getAsBoolean();
                longest = Math.max(longest, clock.getAsLong() - before);
                if (ran) completed++;
                else { empty++; idle.run(); }
            }
            return new Slice(completed, empty, clock.getAsLong() - started, longest);
        } finally {
            owner = null;
        }
    }
}
