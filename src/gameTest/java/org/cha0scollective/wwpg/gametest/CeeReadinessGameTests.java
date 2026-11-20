package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.CeeResultsAvailability;
import org.cha0scollective.wwpg.compat.cee.CeeCompatibility;
import java.util.*;
import java.util.function.Consumer;

/** Version-matched synthetic CEE graphs, also runnable with CEE's stock solver. */
@GameTestHolder("wwpg_cee_readiness")
@PrefixGameTestTemplate(false)
public final class CeeReadinessGameTests {
    @BeforeBatch(batch="wwpg_cee_readiness")
    public static void configure(ServerLevel level) {
        DynamicGameTests.substeps(level);
        try {
            var file=net.neoforged.fml.ModList.get().getModFileById("electroenergetics");
            var path=file.getFile().getFilePath();
            var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path)));
            WorldWidePowerGrid.LOGGER.info("CEE_READINESS_LOADED version={} sha256={} path={}",
                    file.getMods().getFirst().getVersion(),hash,path);
            String expected=System.getProperty("wwpg.test.expectedCeeSha256","");
            if(!expected.isEmpty()&&!expected.equals(hash))throw new IllegalStateException("Unexpected loaded CEE artifact: "+hash);
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
        CEEConfigs.server().simulationConfig.microTicks.set(16);
    }
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=80)
    public static void signedNortonCurrentAndPower(GameTestHelper h) {
        var fixture=new Fixture(h,"signed");
        fixture.source=supply(1,10);
        fixture.series=ElectricalProperties.resistor(9);fixture.load=ElectricalProperties.resistor(10);
        fixture.start();
        h.runAtTickTime(20,()->{
            fixture.check(5,-.5,.5);
            fixture.source=supply(1,-10);
        });
        h.runAtTickTime(28,()->{try{fixture.check(-5,.5,-.5);audit(h);h.succeed();}finally{fixture.close();}});
    }
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=80)
    public static void capacitorVoltageCurrentHistoryAndDischarge(GameTestHelper h) {
        var fixture=new Fixture(h,"capacitor");var capacitor=new CapacitorProperties();capacitor.capacitance=.001;
        fixture.source=supply(1,10);fixture.series=ElectricalProperties.resistor(19);fixture.load=capacitor;
        fixture.start();
        h.runAtTickTime(20,()->{
            h.assertTrue(capacitor.lastVoltage>9.9&&capacitor.lastVoltage<10.01,"Capacitor did not charge: "+capacitor.lastVoltage);
            fixture.record("charged");fixture.source=supply(1,0);
        });
        h.runAtTickTime(28,()->{try{
            h.assertTrue(Math.abs(capacitor.lastVoltage)<.01,"Capacitor did not discharge: "+capacitor.lastVoltage);
            fixture.record("discharged");audit(h);h.succeed();
        }finally{fixture.close();}});
    }
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=80)
    public static void nonlinearDiodeForwardReverseAndRecovery(GameTestHelper h) {
        var fixture=new Fixture(h,"diode");fixture.source=supply(1,10);
        fixture.series=ElectricalProperties.resistor(99);fixture.load=diode();fixture.start();
        h.runAtTickTime(20,()->{
            var r=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
            double current=r.getCurrentThrough(fixture.b,fixture.g);
            h.assertTrue(Double.isFinite(current)&&current>.08&&current<.101,"Forward diode current: "+current);
            fixture.record("forward");fixture.source=supply(1,-10);
        });
        h.runAtTickTime(28,()->{
            double current=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getCurrentThrough(fixture.b,fixture.g);
            h.assertTrue(Double.isFinite(current)&&Math.abs(current)<.001,"Reverse diode current: "+current);
            fixture.record("reverse");fixture.source=supply(1,10);
        });
        h.runAtTickTime(36,()->{try{
            double current=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getCurrentThrough(fixture.b,fixture.g);
            h.assertTrue(Double.isFinite(current)&&current>.08&&current<.101,"Recovered diode current: "+current);
            fixture.record("recovered");audit(h);h.succeed();
        }finally{fixture.close();}});
    }
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=100)
    public static void rectifierCapacitorTransientAndReverseHold(GameTestHelper h) {
        var fixture=new Fixture(h,"rectifier");var capacitor=new CapacitorProperties();capacitor.capacitance=.001;
        fixture.source=supply(100,10);fixture.series=diode();fixture.load=capacitor;
        fixture.leak=true;fixture.start();double[] charged={0};
        h.runAtTickTime(22,()->{
            charged[0]=capacitor.lastVoltage;
            h.assertTrue(charged[0]>8&&charged[0]<10,"Rectified capacitor charge: "+charged[0]);
            fixture.record("rectified");fixture.source=supply(100,-10);
        });
        h.runAtTickTime(30,()->{try{
            h.assertTrue(capacitor.lastVoltage>charged[0]*.5&&capacitor.lastVoltage<charged[0],"Reverse rectifier failed to retain then discharge stored energy: "+capacitor.lastVoltage);
            var r=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
            h.assertTrue(Math.abs(r.getCurrentThrough(fixture.a,fixture.b))<.001,"Reverse diode fed charged capacitor");
            fixture.record("reverse-hold");audit(h);h.succeed();
        }finally{fixture.close();}});
    }
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=100)
    public static void lateRejectionRestoresAllNumericalHistoryBesideHealthyCircuit(GameTestHelper h) {
        if(Boolean.getBoolean("wwpg.test.upstreamReference")){h.succeed();return;}
        var fixture=new Fixture(h,"rollback");var cap=new CapacitorProperties();cap.capacitance=.01;
        fixture.source=supply(100,10);fixture.series=diode();fixture.load=cap;fixture.start();
        var healthy=new Fixture(h,"rollback-neighbor");healthy.source=supply(1,10);
        healthy.series=ElectricalProperties.resistor(9);healthy.load=ElectricalProperties.resistor(10);healthy.start();
        var probe=new RollbackProbe(h,fixture,healthy);
        h.runAtTickTime(18,()->{fixture.source=supply(100,20);probe.start();});
        h.runAtTickTime(23,()->{probe.close();h.assertTrue(probe.count>=3,"Late rejection did not repeat");h.assertTrue(probe.failure==null,String.valueOf(probe.failure));});
        h.runAtTickTime(32,()->{try{
            var r=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
            h.assertTrue(r instanceof CeeResultsAvailability available&&available.available(fixture.a,fixture.b)&&available.available(fixture.g),"Rejected circuit did not recover");
            h.assertTrue(cap.lastVoltage>probe.lastVoltage,"Recovered capacitor did not resume advancing");h.succeed();
        }finally{probe.close();fixture.close();healthy.close();}});
    }
    private static final class RollbackProbe {
        final GameTestHelper helper;final Fixture fixture,healthy;
        final Consumer<AddToElectricGraphEvent> prepare=this::prepare;
        final Consumer<com.george_vi.electroenergetics.events.FinishElectricSimulationEvent> finish=this::finish;
        org.patryk3211.powergrid.electricity.sim.ElectricalNetwork network;
        org.patryk3211.powergrid.electricity.sim.solver.ISolverHook hook;
        Map<String,Double> saved;String failure;int count;double lastVoltage;boolean advanced;
        RollbackProbe(GameTestHelper helper,Fixture fixture,Fixture healthy){this.helper=helper;this.fixture=fixture;this.healthy=healthy;}
        void start(){NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class,prepare);NeoForge.EVENT_BUS.addListener(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent.class,finish);helper.runAtTickTime(70,this::close);}
        void close(){clear();NeoForge.EVENT_BUS.unregister(prepare);NeoForge.EVENT_BUS.unregister(finish);}
        void clear(){if(network!=null&&hook!=null)network.innerHooks.remove(hook);hook=null;network=null;}
        void prepare(AddToElectricGraphEvent event){if(event.level!=helper.getLevel())return;clear();advanced=false;saved=history(fixture);lastVoltage=((CapacitorProperties)fixture.load).lastVoltage;
            var branch=org.cha0scollective.wwpg.bridge.Bridges.get(event.level).branchIdentity(fixture.a,fixture.b);
            if(!(branch instanceof org.cha0scollective.wwpg.bridge.LinearBranch linear))throw new IllegalStateException("No prepared nonlinear branch");network=linear.network();
            hook=new org.patryk3211.powergrid.electricity.sim.solver.ISolverHook(){int step;
                public Collection<org.patryk3211.powergrid.electricity.sim.node.IElectricNode> coupledNodes(){return List.of();}
                public void startIteration(int iteration){if(iteration==0&&++step==11){advanced=((CapacitorProperties)fixture.load).lastVoltage!=lastVoltage;throw new IllegalStateException("CEE readiness deliberate late rejection");}}
            };network.innerHooks.add(hook);
        }
        void finish(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event){if(event.level!=helper.getLevel()||saved==null)return;
            if(!advanced)failure="Rejection did not follow physical history advancement";
            if(!saved.equals(history(fixture)))failure="Rejected CEE numerical history changed: "+saved+" -> "+history(fixture);
            var r=event.results;
            if(!(r instanceof CeeResultsAvailability available)||available.available(fixture.b)||!available.available(healthy.b))failure="Rejection availability crossed circuit ownership";
            double voltage=r.getVoltageAt(healthy.b,healthy.g);if(!Double.isFinite(voltage)||Math.abs(voltage-5)>.0001)failure="Healthy neighbor lost power: "+voltage;
            clear();saved=null;++count;
        }
        static Map<String,Double> history(Fixture fixture){var result=new LinkedHashMap<String,Double>();
            for(var item:Map.of("capacitor",fixture.load,"diode",fixture.series).entrySet())for(Class<?> type=item.getValue().getClass();type!=null;type=type.getSuperclass())
                for(var field:type.getDeclaredFields())if(field.getType()==double.class&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&!java.lang.reflect.Modifier.isFinal(field.getModifiers()))try{
                    field.setAccessible(true);result.put(item.getKey()+"."+field.getName(),field.getDouble(item.getValue()));
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            return result;
        }
    }
    private static void audit(GameTestHelper h){
        if(Boolean.getBoolean("wwpg.test.upstreamReference"))h.assertTrue(!(InfrastructureSavedData.load(h.getLevel()).ticker.lastResults instanceof CeeResultsAvailability),"Stock fixture used bridged results");
        else DynamicGameTests.audit(h);
    }
    private static ElectricalProperties supply(double resistance,double physicalVoltage){
        return ElectricalProperties.fromThevenin(resistance,CeeCompatibility.modern()?physicalVoltage:-physicalVoltage);
    }
    private static void connect(com.george_vi.electroenergetics.simulation.CircuitBuilder builder,Node a,Node b,ElectricalProperties properties){
        // The legacy diode's physical forward direction is opposite its callback node order.
        if(!CeeCompatibility.modern()&&properties.getClass()==DiodeProperties.class)builder.connect(b,a,properties);
        else builder.connect(a,b,properties);
    }
    private static ElectricalProperties diode() {
        try {
            return CeeCompatibility.modern()?DiodeProperties.class.getConstructor(double.class,double.class).newInstance(.025,1e-12)
                    :DiodeProperties.class.getConstructor().newInstance();
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=100)
    public static void transformerRatioAndPolarity(GameTestHelper h){DynamicGameTests.transformerRatioChanges(h);}
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=100)
    public static void inductorHistory(GameTestHelper h){DynamicGameTests.inductorAdvancesOnceIntoPgResistor(h);}
    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=100)
    public static void accumulatorHistory(GameTestHelper h){DynamicGameTests.accumulatorChargesFromPgAndPowersPgLoad(h);}

    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=100)
    public static void preferredTerminalChangeKeepsEveryCapacitorHistoryFrame(GameTestHelper h) {
        if(Boolean.getBoolean("wwpg.test.upstreamReference")){h.succeed();return;}
        var fixture=new Fixture(h,"reference-continuity");
        var capacitor=new CapacitorProperties();capacitor.capacitance=1;
        fixture.source=supply(1,10);fixture.series=ElectricalProperties.resistor(99);fixture.load=capacitor;fixture.start();
        double[] previous={0};
        h.runAtTickTime(20,()->{previous[0]=capacitor.lastVoltage;fixture.preferred=fixture.a;});
        for(int tick=21;tick<=50;tick++) {
            final int at=tick;
            h.runAtTickTime(tick,()->{
                var results=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
                h.assertTrue(results instanceof CeeResultsAvailability available&&available.available(fixture.b,fixture.g),
                        "Preferred terminal change rejected history at tick "+at);
                h.assertTrue(capacitor.lastVoltage>previous[0],"Preferred terminal change paused capacitor charge at tick "+at);
                previous[0]=capacitor.lastVoltage;
            });
        }
        h.runAtTickTime(51,()->{try{audit(h);h.succeed();}finally{fixture.close();}});
    }

    @GameTest(template="empty",batch="wwpg_cee_readiness",timeoutTicks=80)
    public static void higherPriorityReferenceStillReplacesTheNumericalAnchor(GameTestHelper h) {
        if(Boolean.getBoolean("wwpg.test.upstreamReference")){h.succeed();return;}
        var fixture=new Fixture(h,"reference-priority");
        fixture.source=supply(1,10);fixture.series=ElectricalProperties.resistor(9);fixture.load=ElectricalProperties.resistor(10);fixture.start();
        h.runAtTickTime(20,()->{fixture.check(5,-.5,.5);fixture.preferred=fixture.b;fixture.priority=200;});
        h.runAtTickTime(30,()->{try{
            var results=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
            BoardComponentGameTests.near(h,results.getVoltageAt(fixture.b),0,.0001,"Higher-priority zero potential");
            BoardComponentGameTests.near(h,results.getVoltageAt(fixture.g),5,.0001,"Displaced anchor RMS voltage");
            fixture.check(5,-.5,.5);audit(h);h.succeed();
        }finally{fixture.close();}});
    }

    private static final class Fixture {
        final GameTestHelper helper;final String name;
        final AttachedNode a,b,g,c;
        Node preferred;
        int priority=100;
        ElectricalProperties source,series,load;
        final Consumer<AddToElectricGraphEvent> listener=this::prepare;
        final Consumer<com.george_vi.electroenergetics.events.FinishElectricSimulationEvent> observer=this::observe;
        int frames;
        boolean active,leak;
        Fixture(GameTestHelper h,String name){helper=h;this.name=name;String owner="cee-readiness:"+name+":"+h.absolutePos(net.minecraft.core.BlockPos.ZERO);
            a=new AttachedNode(0,owner);b=new AttachedNode(1,owner);g=new AttachedNode(2,owner);c=new AttachedNode(3,owner);preferred=g;}
        void start(){
            // Stock CEE intentionally skips its graph event without native wire endpoints.
            if(Boolean.getBoolean("wwpg.test.upstreamReference")) {
                var seed=new net.minecraft.core.BlockPos(1,2,1);var other=new net.minecraft.core.BlockPos(4,2,1);
                for(var pos:List.of(seed,other)){
                    helper.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE);
                    helper.setBlock(pos,com.george_vi.electroenergetics.CEEBlocks.RESISTOR.get());
                }
                helper.runAtTickTime(5,()->WiringGameTests.connect(helper,helper.absolutePos(seed),0,helper.absolutePos(other),0,false));
            }
            active=true;NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class,listener);NeoForge.EVENT_BUS.addListener(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent.class,observer);helper.runAtTickTime(70,this::close);}
        void close(){active=false;NeoForge.EVENT_BUS.unregister(listener);NeoForge.EVENT_BUS.unregister(observer);}
        void prepare(AddToElectricGraphEvent event){if(!active||event.level!=helper.getLevel())return;
            connect(event.builder,a,g,source);connect(event.builder,a,b,series);connect(event.builder,b,g,load);
            if(leak){event.builder.connect(b,c,ElectricalProperties.resistor(999));event.builder.connect(c,g,ElectricalProperties.resistor(1));}
            event.builder.defaultZeroPotential(preferred,priority);}
        void check(double voltage,double sourceCurrent,double loadCurrent){
            var results=InfrastructureSavedData.load(helper.getLevel()).ticker.lastResults;
            if(results instanceof CeeResultsAvailability availability)helper.assertTrue(availability.available(a,g)&&availability.available(b),"Synthetic circuit unavailable");
            double[] bv=results.getVoltages(b),gv=results.getVoltages(g);
            helper.assertTrue(bv.length==16&&gv.length==16,"Sixteen waveform samples were not published");
            BoardComponentGameTests.near(helper,bv[15]-gv[15],voltage,.0001,"Signed voltage");
            BoardComponentGameTests.near(helper,results.getCurrentThrough(a,g),sourceCurrent,.0001,"Signed source current");
            BoardComponentGameTests.near(helper,results.getCurrentThrough(b,g),Boolean.getBoolean("wwpg.test.upstreamReference")?Math.abs(loadCurrent):loadCurrent,.0001,"Signed load current");
            BoardComponentGameTests.near(helper,results.getHeatLoss(b,g),2.5,.0001,"Native resistor power");record("signed");
        }
        void observe(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event){
            if(active&&event.level==helper.getLevel()&&event.results.getVoltages(b).length==16
                    &&(!(event.results instanceof CeeResultsAvailability availability)||availability.available(b,g))&&frames++<4)
                record("trace-"+frames,event.results);
        }
        void record(String phase){record(phase,InfrastructureSavedData.load(helper.getLevel()).ticker.lastResults);}
        void record(String phase,com.george_vi.electroenergetics.simulation.SimulationResults r){
            double[] bSamples=r.getVoltages(b),gSamples=r.getVoltages(g);if(bSamples.length!=gSamples.length)return;
            for(int i=0;i<bSamples.length;i++)bSamples[i]-=gSamples[i];
            WorldWidePowerGrid.LOGGER.info("CEE_READINESS {} {} voltage={} current={} sourceCurrent={} samples={}",name,phase,
                r.getVoltageAt(b,g),r.getCurrentThrough(b,g),r.getCurrentThrough(a,g),Arrays.toString(bSamples));}
    }
}
