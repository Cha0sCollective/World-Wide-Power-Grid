package org.cha0scollective.wwpg.gametest;

import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import static org.cha0scollective.wwpg.gametest.SolverFailureGameTests.*;

/** Native-specific exception/late-factorization paths; the Java run omits this namespace. */
@GameTestHolder("wwpg_native_guard")
@PrefixGameTestTemplate(false)
public final class NativeFailureGameTests {
    @BeforeBatch(batch="wwpg_native_guard")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @BeforeBatch(batch="wwpg_native_guard_storage")
    public static void configureStorage(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty",batch="wwpg_native_guard_storage",timeoutTicks=100)
    public static void failedNativeFactorizationHoldsCeeAndAddonStorageAndRecovers(GameTestHelper h) {
        ExtensionApiRejectedSolveGameTests.rejectedSolve(h,true);
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=40)
    public static void zeroRhsCannotAcceptAPreviouslyFailedFactorization(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); h.assertTrue(s.type()==CSolver.SolverBackend.NATIVE,"Native-only fixture");
            matrix(s,true); rhs(s,1);
            try { s.singleTick(); throw new AssertionError("Singular matrix was accepted"); }
            catch(IllegalStateException expected) { }
            s.zeroRHS(); s.zeroState();
            try { s.singleTick(); throw new AssertionError("Zero RHS concealed failed factorization"); }
            catch(IllegalStateException expected) { h.assertTrue(!s.isConverged(),"Latched failure was accepted"); }
            matrix(s,false); rhs(s,2); accepted(h,s,2); h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=40)
    public static void lateFactorizationFailureRestoresTheEntryState(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); h.assertTrue(s.type()==CSolver.SolverBackend.NATIVE,"Native-only fixture");
            matrix(s,false); rhs(s,1); accepted(h,s,1);
            int[] calls={0};
            var hook=new ISolverHook() {
                public Collection<IElectricNode> coupledNodes() { return List.of(); }
                public void startIteration(int i) {
                    if(++calls[0]==2) { s.jacobianAdd(0,1,3); s.jacobianAdd(1,1,-1); }
                }
            };
            network.innerHooks.add(hook); network.maxIterations=hooks -> 8; rhs(s,2);
            try { s.singleTick(); throw new AssertionError("Late singular factorization was accepted"); }
            catch(IllegalStateException error) { h.assertTrue(error.getMessage().contains("PG native solve rejected:"),"Unexpected exception"); }
            h.assertTrue(calls[0]>=2 && !s.isConverged(),"Late rejection was not exercised");
            state(h,s,1); network.innerHooks.clear(); matrix(s,false); rhs(s,2); accepted(h,s,2);
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=40)
    public static void callbackExceptionPreservesOriginalErrorStateAndRecovery(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); h.assertTrue(s.type()==CSolver.SolverBackend.NATIVE,"Native-only fixture");
            matrix(s,false); rhs(s,1); accepted(h,s,1);
            var sentinel=new IllegalArgumentException("Expected native callback fixture rejection");
            network.innerHooks.add(new ISolverHook() {
                public Collection<IElectricNode> coupledNodes() { return List.of(); }
                public void startIteration(int i) { throw sentinel; }
            });
            rhs(s,2);
            try { s.singleTick(); throw new AssertionError("Callback exception was discarded"); }
            catch(IllegalArgumentException error) { h.assertTrue(error==sentinel,"JNI replaced the original callback exception"); }
            h.assertTrue(!s.isConverged() && network.countUpdates,"Callback failure retained stale acceptance/bookkeeping");
            state(h,s,1); network.innerHooks.clear(); rhs(s,2); accepted(h,s,2);
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=40)
    public static void nonfiniteResidualOutsideTheLargestFiniteRowIsRejected(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); matrix(s,false); rhs(s,1); accepted(h,s,1);
            network.innerHooks.add(new ISolverHook() {
                public Collection<IElectricNode> coupledNodes() { return List.of(); }
                public void startIteration(int i) { }
                public void addResidual(org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder residual) {
                    residual.add(0,1); residual.add(1,Double.NaN);
                }
            });
            try { s.singleTick(); throw new AssertionError("Nonfinite residual accepted"); }
            catch(IllegalStateException expected) { h.assertTrue(expected.getMessage().contains("info=-1004"),"Wrong nonfinite rejection"); }
            h.assertTrue(!s.isConverged(),"Nonfinite residual retained acceptance"); state(h,s,1);
            network.innerHooks.clear(); rhs(s,2); accepted(h,s,2); h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=40)
    public static void nonfiniteRhsHoldsTheEntryStateAndRecovers(GameTestHelper h) {
        var network=network(h);
        try {
            var s=solver(network); matrix(s,false); rhs(s,1); accepted(h,s,1);
            s.zeroRHS(); s.rhsAdd(0,Double.POSITIVE_INFINITY); s.rhsAdd(1,0);
            try { s.singleTick(); throw new AssertionError("Nonfinite input accepted"); }
            catch(IllegalStateException expected) { h.assertTrue(expected.getMessage().contains("info=-1004"),"Wrong input rejection"); }
            h.assertTrue(!s.isConverged(),"Nonfinite input retained acceptance"); state(h,s,1);
            rhs(s,2); accepted(h,s,2); h.succeed();
        } finally { network.cleanup(); }
    }
}
