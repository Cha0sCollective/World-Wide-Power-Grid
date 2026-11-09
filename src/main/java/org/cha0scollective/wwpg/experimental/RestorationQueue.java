package org.cha0scollective.wwpg.experimental;

import java.util.ArrayDeque;
import java.util.HashSet;
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
        return drain(maxEntries, budgetNanos, clock, valid, trace, finish, null);
    }
    /** Retain completed regions only while tracing reports no intervening graph mutation. */
    public Slice drain(int maxEntries, long budgetNanos, LongSupplier clock, Predicate<P> valid,
                       Function<N, Set<N>> trace, Predicate<P> finish, LongSupplier revision) {
        if (maxEntries < 1 || budgetNanos < 1) throw new IllegalArgumentException("Positive restoration budget required");
        long start = clock.getAsLong(), longest = 0;
        int processed = 0, traversed = 0, skipped = 0, discarded = 0;
        Set<N> covered = revision == null ? Set.of() : new HashSet<>();
        while (!entries.isEmpty() && processed < maxEntries
                && (processed == 0 || clock.getAsLong() - start < budgetNanos)) {
            var entry = entries.removeFirst();
            long entryStart = clock.getAsLong();
            if (!valid.test(entry.part)) {
                discarded += 2;
                covered = revision == null ? Set.of() : new HashSet<>();
            } else {
                if (covered.contains(entry.first)) skipped++;
                else { covered = trace(entry.first, covered, trace, revision); traversed++; }
                if (covered.contains(entry.second)) skipped++;
                else { covered = trace(entry.second, covered, trace, revision); traversed++; }
                if (finish.test(entry.part)) covered = revision == null ? Set.of() : new HashSet<>();
            }
            longest = Math.max(longest, clock.getAsLong() - entryStart);
            processed++;
        }
        return new Slice(processed, traversed, skipped, discarded, clock.getAsLong() - start, longest);
    }
    private Set<N> trace(N root, Set<N> covered, Function<N, Set<N>> trace, LongSupplier revision) {
        if (revision == null) return trace.apply(root);
        long before = revision.getAsLong();
        var fresh = trace.apply(root);
        if (before != revision.getAsLong()) covered.clear();
        covered.addAll(fresh);
        return covered;
    }
}
