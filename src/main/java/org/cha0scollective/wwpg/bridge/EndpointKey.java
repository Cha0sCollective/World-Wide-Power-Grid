package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.Node;

/** Dimension is supplied by the owning bridge; graph ordinals never enter this identity. */
public record EndpointKey(String owner, int terminal) implements Comparable<EndpointKey> {
    public static EndpointKey of(Node node) {
        if (node.getClass() == InWorldNode.class) {
            var p = ((InWorldNode) node).sourcePos();
            return new EndpointKey("block:" + p.getX() + "," + p.getY() + "," + p.getZ(), ((InWorldNode) node).id());
        }
        if (node.getClass() == AttachedNode.class)
            return new EndpointKey("internal:" + ((AttachedNode) node).ownerID, ((AttachedNode) node).id);
        throw new IllegalArgumentException("Unsupported endpoint " + node.getClass().getName());
    }

    @Override public int compareTo(EndpointKey other) {
        int c = owner.compareTo(other.owner);
        return c == 0 ? Integer.compare(terminal, other.terminal) : c;
    }
}
