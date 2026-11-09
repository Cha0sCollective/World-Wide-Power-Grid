package org.cha0scollective.wwpg.experimental;

/** Ephemeral connectivity; a hold follows unions made before or after seeding. */
public final class RestorationComponents {
    private RestorationComponents() { }
    public static final class Node {
        private Node parent = this;
        private int rank;
        private boolean held;
        private Node root() {
            var root = this;
            while (root.parent != root) root = root.parent;
            var current = this;
            while (current.parent != current) {
                var next = current.parent; current.parent = root; current = next;
            }
            return root;
        }
        public boolean held() { return root().held; }
        public void hold() { root().held = true; }
    }
    public static void join(Node first, Node second) {
        if (first == null || second == null) return;
        var a = first.root(); var b = second.root();
        if (a == b) return;
        if (a.rank < b.rank) { var swap = a; a = b; b = swap; }
        b.parent = a; a.held |= b.held;
        if (a.rank == b.rank) a.rank++;
    }
}
