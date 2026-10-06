package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import java.util.Map;

/** Invalid descriptions that CEE would otherwise silently discard. */
public interface RejectedConnections {
    Map<Node,String> wwpg$rejectedConnections();
}
