package org.cha0scollective.wwpg.experimental;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Atomic native connections, with optional bounded priority. No traversal state survives a drain. */
public final class RestorationQueue<N, P> {
    private record Entry<N, P>(N first, N second, P part, long order) { }
    public record Slice(int processed, int traversed, int covered, int discarded,
                        long elapsedNanos, long longestEntryNanos, int prioritized) { }
    private final ArrayDeque<Entry<N, P>> entries = new ArrayDeque<>();
    private long nextOrder;
    private int priorityStreak;
    public void add(N first, N second, P part) { entries.addLast(new Entry<>(first, second, part, nextOrder++)); }
    public int size() { return entries.size(); }
    public Slice drain(int maxEntries, long budgetNanos, LongSupplier clock, Predicate<P> valid,
                       Function<N, Set<N>> trace, Predicate<P> finish) {
        return drain(maxEntries, budgetNanos, clock, valid, trace, finish, null);
    }
    /** The priority predicate must only inspect the saved part; it must not invoke world callbacks. */
    public Slice drain(int maxEntries, long budgetNanos, LongSupplier clock, Predicate<P> valid,
                       Function<N, Set<N>> trace, Predicate<P> finish, Predicate<P> priority) {
        if (maxEntries < 1 || budgetNanos < 1) throw new IllegalArgumentException("Positive restoration budget required");
        long start = clock.getAsLong(), longest = 0;
        int processed = 0, traversed = 0, skipped = 0, discarded = 0, prioritized = 0;
        ArrayDeque<Entry<N, P>> urgent = null, ordinary = null;
        if (priority != null) {
            urgent = new ArrayDeque<>(); ordinary = new ArrayDeque<>();
            // Snapshot priorities once per slice. Reads and rebuilding count toward
            // elapsed time; newly attached work remains queued for the next slice.
            for (var entry : entries) (priority.test(entry.part) ? urgent : ordinary).addLast(entry);
            entries.clear();
        }
        Set<N> covered = Set.of();
        try {
            while ((priority == null ? !entries.isEmpty() : !urgent.isEmpty() || !ordinary.isEmpty()) && processed < maxEntries
                    && (processed == 0 || clock.getAsLong() - start < budgetNanos)) {
                Entry<N, P> entry;
                if (priority == null) entry = entries.removeFirst();
                else if (!urgent.isEmpty() && (ordinary.isEmpty() || priorityStreak < 3)) {
                    entry = urgent.removeFirst(); prioritized++; priorityStreak = Math.min(3, priorityStreak + 1);
                } else { entry = ordinary.removeFirst(); priorityStreak = 0; }
                long entryStart = clock.getAsLong();
                if (!valid.test(entry.part)) {
                    discarded += 2;
                    covered = Set.of();
                } else {
                    if (covered.contains(entry.first)) skipped++;
                    else { covered = trace.apply(entry.first); traversed++; }
                    if (covered.contains(entry.second)) skipped++;
                    else { covered = trace.apply(entry.second); traversed++; }
                    if (finish.test(entry.part)) covered = Set.of();
                }
                longest = Math.max(longest, clock.getAsLong() - entryStart);
                processed++;
            }
        } finally {
            if (priority != null) {
                // Restore arrival order among unprocessed entries, including after
                // an exception. A later slice may observe different priorities.
                var pending = new ArrayDeque<Entry<N, P>>(urgent.size() + ordinary.size() + entries.size());
                while (!urgent.isEmpty() || !ordinary.isEmpty()) {
                    boolean first = ordinary.isEmpty() || !urgent.isEmpty() && urgent.peekFirst().order < ordinary.peekFirst().order;
                    pending.addLast((first ? urgent : ordinary).removeFirst());
                }
                pending.addAll(entries); entries.clear(); entries.addAll(pending);
            }
        }
        return new Slice(processed, traversed, skipped, discarded, clock.getAsLong() - start, longest, prioritized);
    }
}
