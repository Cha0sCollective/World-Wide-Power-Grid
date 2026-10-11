package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;

/** Marks a CEE property stamp whose incremental conductance must remain exact. */
public final class PropertyCurrentSourceWire extends CurrentSourceWire {
    public PropertyCurrentSourceWire(IElectricNode first, IElectricNode second, double conductance) {
        super(first, second, conductance);
    }
}
