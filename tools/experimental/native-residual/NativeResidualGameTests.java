package org.cha0scollective.wwpg.gametest;

import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import static org.cha0scollective.wwpg.gametest.SolverFailureGameTests.solver;

/** Experimental companion only: live cache invalidation through the actual JNI API. */
@GameTestHolder("wwpg_native_guard")
@PrefixGameTestTemplate(false)
public final class NativeResidualGameTests {
    private static ElectricalNetwork network(GameTestHelper h,int rows) {
        var network=new ElectricalNetwork(false);
        network.switchBackend(CSolver.SolverBackend.NATIVE);
        for(int i=0;i<rows;i++) network.addNode(new FloatingNode());
        var s=solver(network); s.allocate(rows); s.setPrecision(1e-7,1e-12,1e-6,.99);
        network.maxIterations=hooks -> 32;
        rebuild(s,rows,4); rhs(s,rows,4);
        for(int warm=0;warm<3;warm++) s.singleTick();
        accepted(h,s,rows,1);
        return network;
    }
    private static void rebuild(IMNA s,int rows,double diagonal) {
        s.jacobianPrepareForWrite();
        for(int i=0;i<rows;i++) s.jacobianAdd(i,i,diagonal);
        s.finishJacobianWrite();
    }
    private static void rhs(IMNA s,int rows,double value) {
        s.zeroRHS(); for(int i=0;i<rows;i++) s.rhsAdd(i,value);
    }
    private static void accepted(GameTestHelper h,IMNA s,int rows,double expected) {
        h.assertTrue(s.isConverged(),"Updated native matrix failed to converge");
        for(int i=0;i<rows;i++)
            h.assertTrue(Math.abs(s.stateVector().get(i,0)-expected)<1e-10,"Stale matrix state at row "+i);
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=100)
    public static void cachedValuesRefreshAcrossSixteenSubsteps(GameTestHelper h) {
        int rows=67; var network=network(h,rows);
        try {
            var s=solver(network);
            for(int step=1;step<=16;step++) {
                for(int i=0;i<rows;i++) s.jacobianAdd(i,i,2);
                s.finishJacobianWrite(); rhs(s,rows,(4+2*step)*step); s.singleTick();
                accepted(h,s,rows,step);
            }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=100)
    public static void cachedStructureSurvivesInsertionClearAndResize(GameTestHelper h) {
        var network=network(h,67);
        try {
            var s=solver(network);
            for(int rows:new int[]{65,129,67}) {
                s.allocate(rows); rebuild(s,rows,4); rhs(s,rows,4); s.singleTick(); accepted(h,s,rows,1);
                for(int i=0;i<rows;i++) s.jacobianAdd(i,(i+1)%rows,1);
                s.finishJacobianWrite(); rhs(s,rows,10); s.singleTick(); accepted(h,s,rows,2);
                rebuild(s,rows,8); rhs(s,rows,24); s.singleTick(); accepted(h,s,rows,3);
            }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=100)
    public static void fittingCallbacksRefreshExistingMatrixValues(GameTestHelper h) {
        int rows=67; var network=network(h,rows);
        try {
            var s=solver(network); int[] calls={0};
            network.innerHooks.add(new ISolverHook() {
                public Collection<IElectricNode> coupledNodes() { return List.of(); }
                public void startIteration(int iteration) {
                    if(++calls[0]==2) for(int row=0;row<rows;row++) s.jacobianAdd(row,row,4);
                }
            });
            rhs(s,rows,8); s.singleTick(); accepted(h,s,rows,1);
            h.assertTrue(calls[0]>=3,"Fitting callback path did not run");
            network.innerHooks.clear(); rhs(s,rows,16); s.singleTick(); accepted(h,s,rows,2);
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty",batch="wwpg_native_guard",timeoutTicks=100)
    public static void fittingInsertionsRefreshPointersAndRefactor(GameTestHelper h) {
        int rows=257; var network=network(h,rows);
        try {
            var s=solver(network); int[] calls={0};
            network.innerHooks.add(new ISolverHook() {
                public Collection<IElectricNode> coupledNodes() { return List.of(); }
                public void startIteration(int iteration) {
                    if(++calls[0]==2) for(int offset=1;offset<=5;offset++)
                        for(int row=0;row<rows;row++) s.jacobianAdd(row,(row+offset)%rows,1);
                }
            });
            rhs(s,rows,8); s.singleTick(); accepted(h,s,rows,8./9.);
            h.assertTrue(calls[0]>=3,"Structural fitting callback path did not run");
            network.innerHooks.clear(); rhs(s,rows,18); s.singleTick(); accepted(h,s,rows,2);
            h.succeed();
        } finally { network.cleanup(); }
    }
}
