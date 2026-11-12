package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import java.util.function.Consumer;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;

@GameTestHolder("wwpg_audit_cee")
@PrefixGameTestTemplate(false)
public final class AuditCeeStateGameTests {
    private static final class Unsupported extends ElectricalProperties { Unsupported(){super(100,0,0);} }
    @GameTest(template="empty",timeoutTicks=180)
    public static void allWindingsRestoreTogether(GameTestHelper h) { isolation(h,0,false); }
    @GameTest(template="empty",timeoutTicks=180)
    public static void partialIsolationHoldsSharedRotor(GameTestHelper h) { isolation(h,1,false); }
    @GameTest(template="empty",timeoutTicks=180)
    public static void nonfinitePropertyHoldsSharedRotor(GameTestHelper h) { isolation(h,0,true); }
    @GameTest(template="empty",timeoutTicks=180)
    public static void lateRejectionRestoresSharedRotor(GameTestHelper h) { isolation(h,-2,false); }
    @GameTest(template="empty",timeoutTicks=180)
    public static void restorationHoldRestoresSharedRotor(GameTestHelper h) { isolation(h,-1,false); }

    private static void isolation(GameTestHelper h,int port,boolean nonfinite) {
        DynamicGameTests.substeps(h.getLevel());GenerationGameTests.assemble(h,true);
        var pos=h.absolutePos(new BlockPos(2,2,2));var level=h.getLevel();
        var healthySource=new BlockPos(8,2,1);var healthyLoad=new BlockPos(8,2,4);
        h.setBlock(healthySource,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(healthyLoad,ModdedBlocks.CREATIVE_RESISTOR.get());
        for(var p:new BlockPos[]{new BlockPos(2,2,5),new BlockPos(4,2,5),new BlockPos(6,2,5)})h.setBlock(p,ModdedBlocks.CREATIVE_RESISTOR.get());
        var probe=new Probe(h,pos,port,nonfinite);
        h.runAtTickTime(5,()->{
            GenerationGameTests.speed(h,128);
            ((CreativeSourceBlockEntity)h.getBlockEntity(healthySource)).setValue(10);
            ((ResistorBlockEntity)h.getBlockEntity(healthyLoad)).setValue(10);
            for(int terminal=0;terminal<2;++terminal)WiringGameTests.connect(h,h.absolutePos(healthySource),terminal,h.absolutePos(healthyLoad),terminal,true);
            for(int phase=0;phase<3;++phase){
                var load=h.absolutePos(new BlockPos(2+phase*2,2,5));((ResistorBlockEntity)level.getBlockEntity(load)).setValue(1000);
                WiringGameTests.connect(h,pos,phase+1,load,0,true);WiringGameTests.connect(h,pos,0,load,1,true);
            }
        });
        h.runAtTickTime(70,()->{
            var device=DevicesSavedData.load(level).getDevice(pos,ThreePhaseAlternatorBrushesDevice.class);
            h.assertTrue(Math.abs(device.virtualRotor.actualOmega)>0,"Alternator did not warm up");
            NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class,probe.prepare);
            NeoForge.EVENT_BUS.addListener(FinishElectricSimulationEvent.class,probe.finish);
        });
        h.runAtTickTime(78,()->{
            probe.close();
            h.assertTrue(probe.count>=3,"Rejected fixture did not observe repeated ticks");
            h.assertTrue(probe.failure==null,probe.failure==null?"":probe.failure);
            BoardComponentGameTests.near(h,new BlockWireEndpoint(h.absolutePos(healthyLoad),0).getNode(level).getVoltage()
                    -new BlockWireEndpoint(h.absolutePos(healthyLoad),1).getNode(level).getVoltage(),10,.01,"Unrelated powered circuit during rejection");
        });
        h.runAtTickTime(110,()->{
            var results=InfrastructureSavedData.load(level).ticker.lastResults;
            h.assertTrue(results instanceof BridgeSimulationResults r&&r.available(new InWorldNode(0,pos)),"Repaired winding remained unavailable");
            h.assertTrue(results.getVoltageAt(pos,1,0)>100,"Repaired winding did not resume powered readings");
            h.assertTrue(org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.readVoltageBetween(level,pos,2,pos,0).status()
                    ==org.cha0scollective.wwpg.api.MeasurementStatus.VALID,"Repaired winding retained unavailable public readings");
            h.succeed();
        });
    }
    private static final class Probe {
        final GameTestHelper helper; final BlockPos pos; final int port; final boolean nonfinite;
        double angle,omega,energy;int work,count;String failure;boolean prepared;
        ElectricalNetwork hookedNetwork;ISolverHook hook;
        final Consumer<AddToElectricGraphEvent> prepare=this::prepare;
        final Consumer<FinishElectricSimulationEvent> finish=this::finish;
        Probe(GameTestHelper h,BlockPos pos,int port,boolean nonfinite){helper=h;this.pos=pos;this.port=port;this.nonfinite=nonfinite;}
        void prepare(AddToElectricGraphEvent event) {
            if(event.level!=helper.getLevel())return;
            var d=DevicesSavedData.load(event.level).getDevice(pos,ThreePhaseAlternatorBrushesDevice.class);
            angle=d.virtualRotor.angle;omega=d.virtualRotor.actualOmega;energy=d.storedEnergy.get();work=d.workCounter.get();
            prepared=true;
            if(port<0) {
                var network=new BlockWireEndpoint(pos,1).getNode(event.level).getNetwork();
                if(port==-1)try {
                    var pause=org.cha0scollective.wwpg.bridge.RestorationCoordinator.class.getDeclaredMethod("markPaused",ElectricalNetwork.class);
                    pause.setAccessible(true);pause.invoke(null,network);
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                else {
                    hookedNetwork=network;
                    hook=new ISolverHook(){int step;
                        public java.util.Collection<IElectricNode> coupledNodes(){return java.util.List.of();}
                        public void startIteration(int iteration){if(iteration==0&&++step==11)throw new IllegalStateException("Audit deliberate late solve rejection");}
                        public void addResidual(org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder residual){}
                    };
                    network.innerHooks.add(hook);
                }
                return;
            }
            event.builder.connect(new InWorldNode(port,pos),new AttachedNode(987654,"audit:"+pos),nonfinite?ElectricalProperties.resistor(Double.NaN):new Unsupported());
        }
        void finish(FinishElectricSimulationEvent event) {
            if(event.level!=helper.getLevel()||!prepared)return;
            prepared=false;
            clearHook();
            var d=DevicesSavedData.load(event.level).getDevice(pos,ThreePhaseAlternatorBrushesDevice.class);
            if(Double.compare(angle,d.virtualRotor.angle)!=0||Double.compare(omega,d.virtualRotor.actualOmega)!=0
                    ||Double.compare(energy,d.storedEnergy.get())!=0||work!=d.workCounter.get())
                failure="Rejected winding changed shared state: angle "+angle+" -> "+d.virtualRotor.angle+", work "+work+" -> "+d.workCounter.get();
            var r=InfrastructureSavedData.load(event.level).ticker.lastResults;
            // The event precedes lastResults publication; its own results carry this frame.
            if(failure==null&&(!(event.results instanceof BridgeSimulationResults current)||current.available(new InWorldNode(2,pos))))
                failure="Partially rejected shared rotor published an available sibling phase";
            var status=org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.readVoltageBetween(event.level,pos,2,pos,0).status();
            if(status==org.cha0scollective.wwpg.api.MeasurementStatus.VALID)failure="Held winding published valid public measurements";
            if(port==-1&&status!=org.cha0scollective.wwpg.api.MeasurementStatus.NOT_READY)failure="Restoration hold was reported as "+status;
            ++count;
        }
        void clearHook(){if(hookedNetwork!=null){hookedNetwork.innerHooks.remove(hook);hookedNetwork=null;}}
        void close(){clearHook();NeoForge.EVENT_BUS.unregister(prepare);NeoForge.EVENT_BUS.unregister(finish);}
    }
}
