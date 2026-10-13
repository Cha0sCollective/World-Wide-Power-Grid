package org.cha0scollective.wwpg.bridge;

import org.cha0scollective.wwpg.mixin.PgPentodeBiasAccessor;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.special.PentodeWire;
import java.lang.ref.WeakReference;
import java.util.*;

/** PG's fresh pentode limiter can need several native steps to reach its first bias. */
public final class PentodeInitialization {
    private static final Map<ElectricalNetwork,Pending> PENDING=new WeakHashMap<>();
    private PentodeInitialization() { }
    public static synchronized void added(ElectricalNetwork network,PentodeWire tube) {
        var pending=PENDING.computeIfAbsent(network,ignored->new Pending());
        if(pending.tubes.stream().noneMatch(reference->reference.get()==tube))pending.tubes.add(new WeakReference<>(tube));
    }
    private static boolean biased(PentodeWire tube) {
        double cathode=tube.getNode1().getVoltage();
        double anode=tube.getNode2().getVoltage();
        double screen=((PgPentodeBiasAccessor)tube).wwpg$screen().getVoltage();
        return Double.isFinite(cathode)&&Double.isFinite(anode)&&Double.isFinite(screen)&&anode>cathode&&screen>cathode;
    }
    public static synchronized void converged(ElectricalNetwork network) {
        var pending=PENDING.get(network);if(pending==null)return;
        // A disconnected zero-state solve must not mark a newly loaded tube's
        // later first powered bias as initialized.
        pending.tubes.removeIf(reference->{var tube=reference.get();return tube==null||tube.getNetwork()!=network||biased(tube);});
        if(pending.tubes.isEmpty())PENDING.remove(network);
    }
    public static synchronized boolean hold(ElectricalNetwork network,int warmBefore) {
        if(warmBefore<=0)return false;
        var pending=PENDING.get(network);if(pending==null)return false;
        boolean initializing=pending.tubes.stream().anyMatch(reference->{var tube=reference.get();return tube!=null&&tube.getNetwork()==network&&biased(tube);});
        return initializing&&pending.window.hold();
    }
    public static synchronized void clear() { PENDING.clear(); }
    private static final class Pending {
        final List<WeakReference<PentodeWire>> tubes=new ArrayList<>();
        final InitializationWindow window=new InitializationWindow();
    }
}
