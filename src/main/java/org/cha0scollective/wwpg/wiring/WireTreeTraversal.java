package org.cha0scollective.wwpg.wiring;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

/** Stack-safe form of PG's depth-first wire resolution, including its leaf rules. */
public final class WireTreeTraversal {
    private WireTreeTraversal() {}

    public interface Graph<N, E> {
        // Capture the original part collection, migrate the endpoint, then snapshot it.
        List<E> enter(N endpoint);
        boolean resolved(E edge);
        N other(E edge, N endpoint);
        boolean valid(N endpoint);
        void resolve(E edge);
    }

    public static <N, E> boolean trace(N endpoint, Set<N> visited, Graph<N, E> graph) {
        var first = enter(endpoint, visited, graph);
        if (first == null) return false;
        var stack = new ArrayDeque<Frame<N, E>>();
        stack.push(first);
        while (!stack.isEmpty()) {
            var frame = stack.peek();
            if (frame.index == frame.parts.size()) {
                boolean answer = frame.continueResolving;
                stack.pop();
                if (stack.isEmpty()) return answer;
                var parent = stack.peek();
                if (answer) {
                    graph.resolve(parent.pending);
                    parent.continueResolving = true;
                }
                parent.pending = null;
                continue;
            }
            var part = frame.parts.get(frame.index++);
            if (graph.resolved(part)) frame.continueResolving = true;
            var other = graph.other(part, frame.endpoint);
            if (frame.parts.size() == 1) {
                if (other != null && graph.valid(other)) {
                    graph.resolve(part);
                    frame.continueResolving = true;
                }
                frame.index = frame.parts.size();
            } else if (other != null) {
                var child = enter(other, visited, graph);
                if (child != null) {
                    frame.pending = part;
                    stack.push(child);
                }
            }
        }
        throw new AssertionError("Missing root result");
    }

    private static <N, E> Frame<N, E> enter(N endpoint, Set<N> visited, Graph<N, E> graph) {
        if (!visited.add(endpoint)) return null;
        var parts = graph.enter(endpoint);
        return parts == null ? null : new Frame<>(endpoint, parts);
    }

    private static final class Frame<N, E> {
        final N endpoint;
        final List<E> parts;
        int index;
        boolean continueResolving;
        E pending;
        Frame(N endpoint, List<E> parts) { this.endpoint = endpoint; this.parts = parts; }
    }
}
