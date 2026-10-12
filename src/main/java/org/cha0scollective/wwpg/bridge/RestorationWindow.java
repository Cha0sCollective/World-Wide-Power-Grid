package org.cha0scollective.wwpg.bridge;

/** A restoration burst cannot defer electrical preparation beyond twenty ticks. */
public final class RestorationWindow {
    private long first, last;
    private boolean active;
    private RestorationWindow parent = this;
    private boolean activated, ready = true;
    public RestorationWindow root() {
        var root = this; while (root.parent != root) root = root.parent;
        var current = this; while (current.parent != current) { var next = current.parent; current.parent = root; current = next; }
        return root;
    }
    public void touch(long tick) { var root = root(); if (!root.active) { root.first = tick; root.active = true; } root.last = tick; root.activated = true; }
    public boolean hold(long tick, boolean ready) {
        var root = root(); if (root != this) return root.hold(tick, ready);
        if (!active) return false;
        if (tick - first >= 20 || ready && this.ready && tick - last >= 2) { active = false; return false; }
        return true;
    }
    public boolean active() { return root().active; }
    public boolean activated() { return root().activated; }
    public void resetReady() { root().ready = true; }
    public void observeReady(boolean ready) { root().ready &= ready; }
    public void absorb(RestorationWindow other) {
        var firstRoot = root(); var secondRoot = other.root();
        if (firstRoot == secondRoot || !firstRoot.active && !secondRoot.active) return;
        if (!firstRoot.active) { firstRoot.first = secondRoot.first; firstRoot.last = secondRoot.last; firstRoot.active = true; }
        else if (secondRoot.active) { firstRoot.first = Math.min(firstRoot.first, secondRoot.first); firstRoot.last = Math.max(firstRoot.last, secondRoot.last); }
        firstRoot.ready &= secondRoot.ready; firstRoot.activated |= secondRoot.activated;
        secondRoot.parent = firstRoot;
    }
}
