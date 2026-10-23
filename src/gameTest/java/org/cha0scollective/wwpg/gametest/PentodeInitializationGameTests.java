package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.bridge.PentodeInitialization;
import org.cha0scollective.wwpg.mixin.CapacitorHistoryAccessor;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.mixin.PgSolverWarmupAccessor;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.*;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.patryk3211.powergrid.electricity.sim.special.*;

/** Reference calls use PG's own equations and iteration limits, without a second world solve. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class PentodeInitializationGameTests {
    @BeforeBatch(batch="wwpg_loading_pentode_reference") public static void configureReference(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_pentode_bound") public static void configureBound(ServerLevel level) { DynamicGameTests.substeps(level); }
    private record Circuit(ElectricalNetwork network,CurrentSourceWire screen,CRSeriesWire capacitor) { }
    private static Circuit circuit() {
        var network=new ElectricalNetwork(false);
        network.switchBackend(CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend","NATIVE")));
        network.triodeLimAnode=network.triodeLimCathode=network.triodeLimGrid=.5;
        network.maxIterations=hooks->200;
        var cathode=new FloatingNode();var anode=new FloatingNode();var grid=new FloatingNode();var screen=new FloatingNode();
        network.addNodes(cathode,anode,grid,screen);
        network.addWire(new ElectricWire(.005,cathode,null));network.addWire(new ElectricWire(.1,grid,cathode));
        var supply=new CurrentSourceWire(anode,cathode,1./10000);supply.setCurrent(.01);network.addWire(supply);
        var screenSupply=new CurrentSourceWire(screen,cathode,1./1000);screenSupply.setCurrent(.1);network.addWire(screenSupply);
        network.addWire(new PentodeWire(8,6000,4500,48,12,1.35f,.1f,cathode,anode,grid,screen));
        var capacitor=new CRSeriesWire(1e-9,1,anode,cathode);network.addWire(capacitor);
        network.prepare(16);network.warmUp(1);
        return new Circuit(network,screenSupply,capacitor);
    }
    private static int warm(ElectricalNetwork network) { return ((PgSolverWarmupAccessor)((ElectricalNetworkAccessor)network).wwpg$solver()).wwpg$warmUpTicks(); }
    @GameTest(template="empty",batch="wwpg_loading_pentode_reference",timeoutTicks=20)
    public static void upstreamPentodeNeedsMultipleColdStepsWithoutAdvancingStorage(GameTestHelper h) {
        var circuit=circuit();var network=circuit.network;
        try {
            var history=(CapacitorHistoryAccessor)circuit.capacitor;
            double saved=history.wwpg$storedVoltage();int before=warm(network);
            network.singleTick();
            h.assertTrue(!network.isConverged()&&before==1&&warm(network)==1,"Pinned PG did not reproduce its initial pentode limiter rejection");
            h.assertTrue(history.wwpg$storedVoltage()==saved,"Native storage advanced on the initial unaccepted solve");
            h.assertTrue(PentodeInitialization.hold(network,before),"Fresh positive-biased pentode was not recognized as initializing");
            boolean converged=false;
            for(int step=0;step<16;step++) {
                before=warm(network);network.singleTick();
                if(network.isConverged()||before>0&&warm(network)==before-1)PentodeInitialization.converged(network);
                if(network.isConverged()){converged=true;break;}
                h.assertTrue(history.wwpg$storedVoltage()==saved,"Unaccepted initialization advanced native storage");
            }
            h.assertTrue(converged&&history.wwpg$storedVoltage()!=saved,"Native pentode/storage failed to resume with the original iteration budget");
            network.maxIterations=hooks->1;circuit.screen.setCurrent(.3);network.warmUp(1);network.singleTick();
            h.assertTrue(!network.isConverged()&&!PentodeInitialization.hold(network,1),"A later failure was concealed as first initialization");
            h.succeed();
        } finally { network.cleanup(); }
    }
    @GameTest(template="empty",batch="wwpg_loading_pentode_bound",timeoutTicks=20)
    public static void persistentPentodeFailureExhaustsItsInitializationAllowance(GameTestHelper h) {
        var circuit=circuit();var network=circuit.network;network.maxIterations=hooks->1;
        try {
            var history=(CapacitorHistoryAccessor)circuit.capacitor;double saved=history.wwpg$storedVoltage();
            for(int step=0;step<17;step++) {
                network.singleTick();h.assertTrue(!network.isConverged(),"Forced one-iteration solve unexpectedly converged");
                h.assertTrue(PentodeInitialization.hold(network,1)==(step<16),"Persistent failure received an unbounded initialization allowance");
                h.assertTrue(history.wwpg$storedVoltage()==saved,"Persistent rejection advanced native storage");
            }
            h.succeed();
        } finally { network.cleanup(); }
    }
}
