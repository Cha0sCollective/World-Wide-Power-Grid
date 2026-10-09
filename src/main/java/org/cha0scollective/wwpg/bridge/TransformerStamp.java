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
        coupling = TransformerCoupling.create(pgRatio, p1, p2, s1, s2);
        network.addNode(coupling);
    }
    public void update(double ceeRatio) {
        pgRatio = ratio(ceeRatio);
        coupling.setRatio(pgRatio);
    }
    public static float ratio(double ceeRatio) {
        float converted = (float) (1 / ceeRatio);
        if (!Double.isFinite(ceeRatio) || ceeRatio == 0 || !Float.isFinite(converted) || converted == 0)
            throw new IllegalArgumentException("Invalid transformer ratio " + ceeRatio);
        return converted;
    }
    public double primaryCurrent() { return pgRatio * coupling.getStateValue(); }
    public double secondaryCurrent() { return -coupling.getStateValue(); }
    public ElectricalNetwork network() { return coupling.getNetwork(); }
    public void remove() { coupling.remove(); }
}
