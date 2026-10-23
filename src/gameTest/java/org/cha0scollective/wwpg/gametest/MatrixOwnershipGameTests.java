package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder;
import java.util.ArrayList;

/** Actual mixed dense/sparse and native/Java ownership, not a modeled interceptor. */
@GameTestHolder("wwpg_matrix_ownership")
@PrefixGameTestTemplate(false)
public final class MatrixOwnershipGameTests {
    @BeforeBatch(batch="matrix_ownership")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty", batch="matrix_ownership", timeoutTicks=60)
    public static void anotherCircuitsIncrementalUpdateKeepsItsOwner(GameTestHelper h) {
        var backend=ModdedConfigs.server().electricity.solver.solverBackend.get();
        for (int[] sizes : new int[][]{{2,2},{2,8},{8,2},{8,8}}) verifyForeignEdit(h,backend,backend,sizes[0],sizes[1]);
        h.succeed();
    }

    @GameTest(template="empty", batch="matrix_ownership", timeoutTicks=60)
    public static void mixedBackendUpdatesKeepTheirOwner(GameTestHelper h) {
        var backend=ModdedConfigs.server().electricity.solver.solverBackend.get();
        var other=backend==CSolver.SolverBackend.NATIVE?CSolver.SolverBackend.JAVA:CSolver.SolverBackend.NATIVE;
        h.assertTrue(other.isSupported(),"Mixed ownership fixture requires both backends");
        verifyForeignEdit(h,backend,other,8,8);
        verifyForeignEdit(h,other,backend,8,8);
        h.succeed();
    }

    private static void verifyForeignEdit(GameTestHelper h, CSolver.SolverBackend firstBackend,
                                          CSolver.SolverBackend secondBackend, int firstSize, int secondSize) {
        try (var b=new Circuit(secondBackend,secondSize,null);
             var a=new Circuit(firstBackend,firstSize,()->b.resistor.setResistance(.5))) {
            b.settle(); near(h,b.first.getVoltage(),2,"B before parameter edit");
            a.settle(); b.solve();
            near(h,a.first.getVoltage(),2,"A received B's incremental conductance");
            near(h,b.first.getVoltage(),1.5,"B lost its incremental conductance");
            near(h,b.resistor.current(),1,"B branch current");
            a.assertOwner(h,firstBackend,firstSize); b.assertOwner(h,secondBackend,secondSize);
        }
    }

    @GameTest(template="empty", batch="matrix_ownership", timeoutTicks=60)
    public static void nestedPreparationRestoresTheOuterBuild(GameTestHelper h) {
        var backend=ModdedConfigs.server().electricity.solver.solverBackend.get();
        try (var b=new Circuit(backend,8,null); var a=new Circuit(backend,2,()->b.network.prepare(16))) {
            a.settle(); b.solve();
            near(h,a.first.getVoltage(),2,"Outer build after nested preparation");
            near(h,b.first.getVoltage(),2,"Nested build");
            // This edit occurs after both scopes have closed and must be immediate.
            b.resistor.setResistance(.5); b.solve();
            near(h,b.first.getVoltage(),1.5,"Post-build incremental update");
            a.network.setDirty(); a.settle();
            near(h,a.first.getVoltage(),2,"Outer rebuild retained a nested scope");
        }
        h.succeed();
    }

    @GameTest(template="empty", batch="matrix_ownership", timeoutTicks=60)
    public static void failedBuildLeavesForeignUpdatesAndFollowingBuildsIntact(GameTestHelper h) {
        var backend=ModdedConfigs.server().electricity.solver.solverBackend.get();
        try (var b=new Circuit(backend,8,null); var a=new Circuit(backend,2,()->{
            b.resistor.setResistance(.5);
            throw new IllegalStateException("intentional matrix build failure");
        })) {
            b.settle();
            try { a.network.prepare(16); h.fail("The intentional stamp exception did not escape"); }
            catch (IllegalStateException expected) {
                h.assertTrue(expected.getMessage().equals("intentional matrix build failure"),"Unexpected build failure");
            }
            b.solve(); near(h,b.first.getVoltage(),1.5,"Failed A build discarded B's parameter update");
            a.network.setDirty(); a.settle();
            near(h,a.first.getVoltage(),2,"A could not rebuild after exception");
            b.resistor.setResistance(1); b.solve();
            near(h,b.first.getVoltage(),2,"Exception retained an active build scope");
        }
        h.succeed();
    }

    @GameTest(template="empty", batch="matrix_ownership", timeoutTicks=60)
    public static void backendReplacementAndCleanupDoNotRetainABuild(GameTestHelper h) {
        var backend=ModdedConfigs.server().electricity.solver.solverBackend.get();
        var other=backend==CSolver.SolverBackend.NATIVE?CSolver.SolverBackend.JAVA:CSolver.SolverBackend.NATIVE;
        h.assertTrue(other.isSupported(),"Replacement fixture requires both backends");
        try (var circuit=new Circuit(backend,8,null)) {
            circuit.settle(); near(h,circuit.first.getVoltage(),2,"Initial owner");
            if(other.isSupported()) {
                circuit.network.switchBackend(other); circuit.settle();
                near(h,circuit.first.getVoltage(),2,"Replacement owner");
                circuit.resistor.setResistance(.5); circuit.solve();
                near(h,circuit.first.getVoltage(),1.5,"Replacement incremental update");
            }
        }
        try (var next=new Circuit(backend,2,null)) {
            next.settle(); near(h,next.first.getVoltage(),2,"Fresh owner after cleanup");
        }
        h.succeed();
    }

    private static void near(GameTestHelper h,double actual,double expected,String detail) {
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<1e-7,
                detail+": expected="+expected+", actual="+actual);
    }

    private static final class Circuit implements AutoCloseable {
        final ElectricalNetwork network=new ElectricalNetwork(false);
        final FloatingNode first,second;
        final ElectricWire resistor;
        Circuit(CSolver.SolverBackend backend,int size,Runnable whileStamping) {
            network.switchBackend(backend);
            var nodes=new ArrayList<FloatingNode>();
            for(int i=0;i<size;i++) { var node=new FloatingNode(); nodes.add(node); network.addNode(node); }
            first=nodes.get(size-2); second=nodes.get(size-1);
            for(var node:nodes) if(node!=first) network.addWire(new ElectricWire(1,node,null));
            resistor=whileStamping==null?new ElectricWire(1,first,second):new CallbackWire(first,second,whileStamping);
            network.addWire(resistor);
            var source=new CurrentSourceWire(first,null,0d); source.setCurrent(1); network.addWire(source);
        }
        void settle() { network.prepare(16); solve(); }
        void solve() {
            for(int i=0;i<64;i++) network.singleTick();
            if(!network.isConverged()) throw new AssertionError("Ownership fixture did not converge");
        }
        void assertOwner(GameTestHelper h,CSolver.SolverBackend backend,int size) {
            h.assertTrue(network.size()==size && first.getIndex()==size-2 && second.getIndex()==size-1,
                    "Fixture did not retain different-sized matrices and high-index terminals");
            h.assertTrue(((ElectricalNetworkAccessor)network).wwpg$solver().type()==backend,"Fixture ran wrong backend");
        }
        @Override public void close() { network.cleanup(); }
    }
    private static final class CallbackWire extends ElectricWire {
        Runnable callback;
        CallbackWire(IElectricNode first,IElectricNode second,Runnable callback) { super(1,first,second); this.callback=callback; }
        @Override public void stamp(IAdmittanceAdder add,double conductance) {
            super.stamp(add,conductance);
            if(callback!=null) { var once=callback; callback=null; once.run(); }
        }
    }
}
