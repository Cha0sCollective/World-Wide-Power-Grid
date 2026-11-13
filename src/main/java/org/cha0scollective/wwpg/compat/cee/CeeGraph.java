package org.cha0scollective.wwpg.compat.cee;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;

/** Prepared, shallow views retain CEE's own adjacency maps. No solver graph is copied. */
public final class CeeGraph {
    public static final class NodeView {
        public final Node node;
        public final int ordinal;
        public final Int2ObjectMap<ElectricalProperties> adjacency;
        public final double groundConductance;
        public NodeView(Node node, int ordinal, Int2ObjectMap<ElectricalProperties> adjacency, double groundConductance) {
            this.node=node;this.ordinal=ordinal;this.adjacency=adjacency;this.groundConductance=groundConductance;
        }
    }
    private final List<NodeView> nodes;
    public CeeGraph(CircuitBuilder builder) {
        var result=new ArrayList<NodeView>(builder.allNodes().size());
        for(int i=0;i<builder.allNodes().size();i++) result.add(CeeCompatibility.integration().node(builder,i));
        nodes=List.copyOf(result);
    }
    public List<NodeView> allNodes(){return nodes;}
    public NodeView getNode(int ordinal){return nodes.get(ordinal);}
}
