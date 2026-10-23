package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;

/** Direct backend checks independent of world startup and device fixture readiness. */
@GameTestHolder("wwpg_solver_safety")
@PrefixGameTestTemplate(false)
public final class SolverFailureGameTests {
    @BeforeBatch(batch="wwpg_solver_safety")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty",batch="wwpg_solver_safety_storage",timeoutTicks=100)
    public static void rejectedSubstepsHoldCeeAndAddonStorageAndAvailability(GameTestHelper h) {
        ExtensionApiRejectedSolveGameTests.rejectedSolve(h,false);
    }

    @BeforeBatch(batch="wwpg_solver_safety_storage")
    public static void configureStorage(ServerLevel level) { DynamicGameTests.substeps(level); }

    @BeforeBatch(batch="wwpg_solver_safety_pg_storage")
    public static void configurePgStorage(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty",batch="wwpg_solver_safety_pg_storage",timeoutTicks=100)
    public static void rejectedSolvesHoldPgBatteryFeAndFilterHistoryUntilRecovery(GameTestHelper h) {
        PgStorageFailureFixture.run(h);
    }

    static ElectricalNetwork network(GameTestHelper h) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(ModdedConfigs.server().electricity.solver.solverBackend.get());
        h.assertTrue(((ElectricalNetworkAccessor)network).wwpg$solver().type()
                == ModdedConfigs.server().electricity.solver.solverBackend.get(), "Requested backend did not load");
        network.addNode(new FloatingNode()); network.addNode(new FloatingNode());
        IMNA solver = solver(network); solver.allocate(2);
        solver.setPrecision(1e-7,1e-12,1e-6,.99);
        return network;
    }

    static IMNA solver(ElectricalNetwork network) { return ((ElectricalNetworkAccessor)network).wwpg$solver(); }
    static void matrix(IMNA s, boolean singular) {
        s.jacobianPrepareForWrite();
        s.jacobianAdd(0,0,2); s.jacobianAdd(1,0,1);
        s.jacobianAdd(0,1,singular ? 4 : 1); s.jacobianAdd(1,1,singular ? 2 : 3);
        s.finishJacobianWrite();
    }
    static void rhs(IMNA s, double scale) { s.zeroRHS(); s.rhsAdd(0,4*scale); s.rhsAdd(1,7*scale); }
    static void accepted(GameTestHelper h, IMNA s, double scale) {
        for(int i=0;i<3;i++) s.singleTick();
        h.assertTrue(s.isConverged(), "Valid matrix was not accepted");
        state(h,s,scale);
    }
    static void state(GameTestHelper h, IMNA s, double scale) {
        BoardComponentGameTests.near(h,s.stateVector().get(0,0),scale,1e-9,"First solved row");
        BoardComponentGameTests.near(h,s.stateVector().get(1,0),2*scale,1e-9,"Second solved row");
    }

    @GameTest(template="empty",batch="wwpg_solver_safety",timeoutTicks=40)
    public static void singularNonemptyMatrixIsRejectedAndValidRebuildRecovers(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); matrix(s,false); rhs(s,1); accepted(h,s,1);
            matrix(s,true); rhs(s,1);
            boolean threw=false;
            try { s.singleTick(); }
            catch(IllegalStateException error) {
                threw=true;
                if(s.type()==CSolver.SolverBackend.NATIVE)
                    h.assertTrue(error.getMessage().contains("PG native solve rejected:"),"Native factorization did not report its failure");
            }
            h.assertTrue(threw || !s.isConverged(), "Singular matrix was accepted");
            if(s.type()==CSolver.SolverBackend.NATIVE) {
                h.assertTrue(threw && !s.isConverged(),"Native error retained an accepted status");
                state(h,s,1);
                try { s.singleTick(); throw new AssertionError("Failed factors were reused"); }
                catch(IllegalStateException expected) { state(h,s,1); }
            }
            matrix(s,false); rhs(s,2); accepted(h,s,2);
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_solver_safety",timeoutTicks=40)
    public static void changingInputsKeepAllSixteenSubsteps(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); matrix(s,false); rhs(s,1); accepted(h,s,1);
            for(int i=1;i<=16;i++) { rhs(s,i); s.singleTick(); h.assertTrue(s.isConverged(),"Valid changing input rejected"); state(h,s,i); }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_solver_safety",timeoutTicks=40)
    public static void callbackExceptionClearsAcceptanceAndRestoresUpdateCounting(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); matrix(s,false); rhs(s,1); accepted(h,s,1);
            var sentinel=new IllegalArgumentException("Expected solver callback rejection");
            network.innerHooks.add(new org.patryk3211.powergrid.electricity.sim.solver.ISolverHook() {
                public java.util.Collection<org.patryk3211.powergrid.electricity.sim.node.IElectricNode> coupledNodes() { return java.util.List.of(); }
                public void startIteration(int iteration) { throw sentinel; }
            });
            rhs(s,2);
            try { s.singleTick(); throw new AssertionError("Callback exception was discarded"); }
            catch(IllegalArgumentException error) { h.assertTrue(error==sentinel,"Original callback exception was replaced"); }
            h.assertTrue(!s.isConverged() && network.countUpdates,"Exception retained stale acceptance or suppressed topology updates");
            network.innerHooks.clear(); rhs(s,2); accepted(h,s,2); h.succeed();
        } finally { network.cleanup(); }
    }
}
