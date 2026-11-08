package org.cha0scollective.wwpg.experimental;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** FIFO of atomic native connections. No traversal state survives a drain. */
public final class RestorationQueue<N, P> {
    private record Entry<N, P>(N first, N second, P part) { }
    public record Slice(int processed, int traversed, int covered, int discarded,
                        long elapsedNanos, long longestEntryNanos) { }
    private final ArrayDeque<Entry<N, P>> entries = new ArrayDeque<>();
    public void add(N first, N second, P part) { entries.addLast(new Entry<>(first, second, part)); }
    public int size() { return entries.size(); }
    public Slice drain(int maxEntries, long budgetNanos, LongSupplier clock, Predicate<P> valid,
                       Function<N, Set<N>> trace, Predicate<P> finish) {
        if (maxEntries < 1 || budgetNanos < 1) throw new IllegalArgumentException("Positive restoration budget required");
        long start = clock.getAsLong(), longest = 0;
        int processed = 0, traversed = 0, skipped = 0, discarded = 0;
        Set<N> covered = Set.of();
        while (!entries.isEmpty() && processed < maxEntries
                && (processed == 0 || clock.getAsLong() - start < budgetNanos)) {
            var entry = entries.removeFirst();
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
        return new Slice(processed, traversed, skipped, discarded, clock.getAsLong() - start, longest);
    }
}
