package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;

/** One persistent four-terminal coupling; leakage resistances remain ordinary branches. */
public final class TransformerStamp {
    private final TransformerCoupling coupling;
    private float pgRatio;

    public TransformerStamp(ElectricalNetwork network, IElectricNode p1, IElectricNode p2,
                            IElectricNode s1, IElectricNode s2, double ceeRatio) {
        pgRatio = ratio(ceeRatio);
        // CEE imposes Vprimary = ratio * Vsecondary. PG imposes
        // ratio * Vprimary = Vsecondary, so exchange the winding roles.
        // This also represents CEE's valid zero-ratio regulator position.
        coupling = TransformerCoupling.create(pgRatio, s1, s2, p1, p2);
        network.addNode(coupling);
    }
    public void update(double ceeRatio) {
        pgRatio = ratio(ceeRatio);
        coupling.setRatio(pgRatio);
    }
    public static float ratio(double ceeRatio) {
        float converted = (float) ceeRatio;
        if (!Double.isFinite(ceeRatio) || !Float.isFinite(converted) || (ceeRatio != 0 && converted == 0))
            throw new IllegalArgumentException("Invalid transformer ratio " + ceeRatio);
        return converted;
    }
    public double primaryCurrent() { return -coupling.getStateValue(); }
    public double secondaryCurrent() { return pgRatio * coupling.getStateValue(); }
    public ElectricalNetwork network() { return coupling.getNetwork(); }
    public void remove() { coupling.remove(); }
}
