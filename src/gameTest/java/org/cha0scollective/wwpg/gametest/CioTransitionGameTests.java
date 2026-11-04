package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.Arrays;

import static org.cha0scollective.wwpg.gametest.CioCompatibilityGameTests.*;

/** Kept separate so its deliberate backend fault cannot contaminate healthy-transition assertions. */
@GameTestHolder("wwpg_cio_transitions")
@PrefixGameTestTemplate(false)
public final class CioTransitionGameTests {
    @GameTest(template="empty", timeoutTicks=180)
    public static void warmupAndRealRejectionRemainDistinctAndRecover(GameTestHelper h) {
        var pos=poweredInverter(h); var level=h.getLevel();
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var completed=new java.util.concurrent.atomic.AtomicBoolean();
        var listener=new java.util.function.Consumer<FinishElectricSimulationEvent>() {
            int stage;
            History saved;
            java.util.List<String> diagnostics;
            org.patryk3211.powergrid.electricity.sim.ElectricalNetwork network;
            java.util.function.Function<Boolean,Integer> limit;
            @Override public void accept(FinishElectricSimulationEvent event) {
                if(event.level!=level)return;
                try {
                    switch(stage++) {
                        case 0 -> {
                            valid(h,pos); saved=snapshot(h,pos); diagnostics=Bridges.get(level).diagnostics();
                            network=new BlockWireEndpoint(pos,3).getNode(level).getNetwork();
                            network.warmUp(1);
                        }
                        case 1,2 -> {
                            unavailable(h,pos,event.results); held(h,pos,saved);
                            h.assertTrue(Bridges.get(level).diagnostics().equals(diagnostics),"Warm-up produced a false fault");
                            if(stage==2) network.warmUp(1);
                        }
                        case 3 -> {
                            valid(h,pos); saved=snapshot(h,pos);
                            h.assertTrue(Bridges.get(level).diagnostics().equals(diagnostics),"Warm-up recovery produced a fault");
                            // Limit the actual Newton loop, retaining the original backend binary.
                            // The changing AC source leaves a residual after one iteration.
                            limit=network.maxIterations; network.maxIterations=hooks->1;
                        }
                        case 4,5 -> {
                            unavailable(h,pos,event.results); held(h,pos,saved);
                            var errors=Bridges.get(level).diagnostics();
                            h.assertTrue(errors.stream().anyMatch(line->line.contains("PG electrical substep did not converge")),
                                    "Real backend rejection was hidden as initialization");
                            h.assertTrue(errors.stream().anyMatch(line->line.contains("CEE adapter wwpg:cio_inverter")),
                                    "Rejected CIO callback lost its fault diagnostic");
                            if(stage==6) network.maxIterations=limit;
                        }
                        case 6 -> {
                            valid(h,pos);
                            h.assertTrue(Bridges.get(level).diagnostics().stream().anyMatch(line->line.contains("did not converge")),
                                    "Recovery erased the captured failure");
                            var volts=(double[])get(device(h,pos),"phaseVolts");
                            h.assertTrue(Arrays.stream(volts).allMatch(v->Double.isFinite(v)&&v>100),"Powered CIO phases failed to recover");
                            completed.set(true);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
                        }
                    }
                } catch(RuntimeException|AssertionError error) {
                    failure.set(error);
                    if(limit!=null)network.maxIterations=limit;
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
                }
            }
        };
        h.runAtTickTime(80,()->verifyAfterCommit(h,pos,50,()->{}));
        h.runAtTickTime(85,()->net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FinishElectricSimulationEvent.class,listener));
        h.runAtTickTime(110,()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            if(failure.get()!=null)h.fail(failure.get().getMessage());
            h.assertTrue(completed.get(),"CIO transition sequence did not finish");
            h.succeed();
        });
    }

    private static void valid(GameTestHelper h,BlockPos pos) {
        h.assertTrue(ElectricalMeasurements.readVoltageBetween(h.getLevel(),pos,3,pos,6).status()==MeasurementStatus.VALID,
                "CIO phase reading did not recover");
    }
    private static void unavailable(GameTestHelper h,BlockPos pos,SimulationResults results) {
        h.assertTrue(ElectricalMeasurements.readVoltageBetween(h.getLevel(),pos,3,pos,6).status()!=MeasurementStatus.VALID,
                "Unaccepted frame published a valid API reading");
        h.assertTrue(results!=null&&Double.isNaN(results.getVoltageAt(pos,3,6)),"Unaccepted frame became a numeric CEE reading");
    }
    private record History(double angle,double[] volts,double[] amps,double[] watts) { }
    private static History snapshot(GameTestHelper h,BlockPos pos) {
        var d=device(h,pos);
        return new History((double)get(d,"angle"),((double[])get(d,"phaseVolts")).clone(),
                ((double[])get(d,"phaseAmps")).clone(),((double[])get(d,"phaseWatts")).clone());
    }
    private static void held(GameTestHelper h,BlockPos pos,Object previous) {
        var a=(History)previous;var b=snapshot(h,pos);
        h.assertTrue(a.angle==b.angle&&Arrays.equals(a.volts,b.volts)&&Arrays.equals(a.amps,b.amps)&&Arrays.equals(a.watts,b.watts),
                "Unaccepted CIO frame advanced gameplay phase/history");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object device(GameTestHelper h,BlockPos pos) {
        return DevicesSavedData.load(h.getLevel()).getDevice(pos,(Class)type("com.cio.createinteroperable.inverter.cee.InverterDevice"));
    }
}
