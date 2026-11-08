package org.cha0scollective.wwpg.experimental;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;

/** Coverage lasts only until another traversal can mutate the graph. */
public final class RestorationRoots {
    private RestorationRoots() { }
    public record Counts(int traversed, int covered) { }
    public static <N> Counts drain(List<N> roots, Function<N, Set<N>> trace) {
        return drain(roots, trace, ignored -> false);
    }
    public static <N> Counts drain(List<N> roots, Function<N, Set<N>> trace, IntPredicate afterRoot) {
        Set<N> covered = Set.of();
        int traversed = 0, skipped = 0;
        for (int index = 0; index < roots.size(); index++) {
            var root = roots.get(index);
            if (covered.contains(root)) skipped++;
            else { covered = trace.apply(root); traversed++; }
            if (afterRoot.test(index)) covered = Set.of();
        }
        return new Counts(traversed, skipped);
    }
}
