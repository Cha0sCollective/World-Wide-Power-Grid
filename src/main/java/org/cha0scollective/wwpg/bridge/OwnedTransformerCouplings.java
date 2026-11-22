package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;

/** CEE/API models deliberately use an ideal coupling; winding losses live elsewhere. */
public final class OwnedTransformerCouplings {
    private static final ThreadLocal<Boolean> CONSTRUCTING = new ThreadLocal<>();
    private OwnedTransformerCouplings() { }

    public static TransformerCoupling create(float ratio, IElectricNode p1, IElectricNode p2,
                                            IElectricNode s1, IElectricNode s2) {
        Boolean previous = CONSTRUCTING.get();
        CONSTRUCTING.set(true);
        try {
            return TransformerCoupling.create(ratio, p1, p2, s1, s2);
        } finally {
            if (previous == null) CONSTRUCTING.remove();
            else CONSTRUCTING.set(previous);
        }
    }

    /** Used only by the constructor warning hook, on the constructing thread. */
    public static boolean constructing() { return Boolean.TRUE.equals(CONSTRUCTING.get()); }
}
