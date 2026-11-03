package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.cee.CeeSubstepResult;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.compat.cio.*;
import org.cha0scollective.wwpg.extension.CeeAdapters;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Supplier;

/** Opt-in CIO artifact tests; the ordinary suite never silently installs this optional mod. */
@GameTestHolder("wwpg_cio")
@PrefixGameTestTemplate(false)
public final class CioCompatibilityGameTests {
    private static final String ROOT = "com.cio.createinteroperable.";

    @GameTest(template="empty", timeoutTicks=40)
    public static void nativePhaseCallbacksMatchAdapterExactly(GameTestHelper h) {
        for (int total : new int[]{16,32}) for (double increment : new double[]{11.25,56.25,-56.25}) {
            var nativeSource=source(37,increment,120*Math.sqrt(2),total);
            var adapted=source(37,increment,120*Math.sqrt(2),total);
            var registry=new CeeAdapters.Registry();
            register(registry,adapted);registry.freeze();
            var binding=registry.newSession().capture(adapted).orElseThrow().binding();
            double[] voltages=new double[total*2];
            for(int step=0;step<total;step++) {
                nativeSource.tick(voltages,step,total,0,1);
                binding.before(step==0?0:voltages[step-1],step,total,.05/total);
                near(h,adapted.currentSource(),nativeSource.currentSource(),0,"CIO source equation");
                double v=-nativeSource.currentSource()*nativeSource.resistance()*100/100.05;
                voltages[step]=v;
                nativeSource.afterTick(voltages,0,1,step,total);
                binding.after(v,nativeSource.currentSource()+v/.05,step,total,.05/total,true);
                sameHistory(h,nativeSource,adapted);
            }
            binding.finish(true);sameHistory(h,nativeSource,adapted);
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void rejectedSubstepsRestoreAllPhaseHistory(GameTestHelper h) {
        for(int rejected:new int[]{0,8,15}) {
            var source=source(23,56.25,170,16);
            var original=source(23,56.25,170,16);
            var registry=new CeeAdapters.Registry();register(registry,source);registry.freeze();
            var binding=registry.newSession().capture(source).orElseThrow().binding();
            for(int step=0;step<=rejected;step++) {
                binding.before(0,step,16,.05/16);
                binding.after(-40+step,2,step,16,.05/16,step!=rejected);
            }
            binding.finish(false);
            h.assertTrue(binding.failed(),"Rejected tick was accepted");sameHistory(h,original,source);
        }
        var source=source(0,1,20,16);var access=(CioPhaseSourceAccess)source;
        var checkpoint=access.wwpg$checkpoint();
        double[] original=(double[])get(source,"terminal");
        set(source,"terminal",new double[]{9});source.resistance=9;source.currentSource=20;
        set(source,"samples",8);set(source,"sumVI",88d);set(source,"v",77d);
        checkpoint.restore();
        h.assertTrue(get(source,"terminal")==original,"Rollback changed native sample-array identity");
        sameHistory(h,source(0,1,20,16),source);h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=160)
    public static void inverterPowersThreePhasesWithNativeMeasurements(GameTestHelper h) { inverter(h,false); }

    @GameTest(template="empty", timeoutTicks=240)
    public static void inverterControlsRecoverWithoutLosingPhaseHistory(GameTestHelper h) { inverter(h,true); }

    private static void inverter(GameTestHelper h,boolean controls) {
        var level=h.getLevel();var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);
        h.setBlock(a.below(),Blocks.STONE);h.setBlock(b.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,block("ElectroEnergeticsModule","CEE_INVERTER"));
        var source=h.absolutePos(a);var inverter=h.absolutePos(b);
        for(int p=0;p<3;p++){var l=new BlockPos(7,2,1+2*p);h.setBlock(l.below(),Blocks.STONE);h.setBlock(l,ModdedBlocks.CREATIVE_RESISTOR.get());}
        h.runAtTickTime(8,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(source)).setValue(300);
            var be=level.getBlockEntity(inverter);call(be,"setFrequency",double.class,50d);call(be,"setSetpoint",int.class,120);call(be,"setEnabled",boolean.class,true);
            wire(h,source,0,inverter,0,true);wire(h,source,1,inverter,1,true);
            for(int p=0;p<3;p++){var load=h.absolutePos(new BlockPos(7,2,1+2*p));((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
                wire(h,inverter,3+p,load,0,true);wire(h,inverter,6,load,1,true);}
        });
        h.runAtTickTime(85,()->verifyAfterCommit(h,inverter,50,()->{
            if(!controls){h.succeed();return;}
            call(level.getBlockEntity(inverter),"setEnabled",boolean.class,false);
        }));
        if(!controls)return;
        h.runAtTickTime(110,()->{
            var be=level.getBlockEntity(inverter);h.assertTrue(!(boolean)call(be,"isRunning"),"Disabled inverter still runs");
            near(h,(double)call(be,"phaseVolts",int.class,0),0,.001,"Disabled inverter voltage");
            call(be,"setFrequency",double.class,25d);call(be,"setReversed",boolean.class,true);call(be,"setEnabled",boolean.class,true);
        });
        h.runAtTickTime(190,()->verifyAfterCommit(h,inverter,25,h::succeed));
    }

    private static void verifyAfterCommit(GameTestHelper h,BlockPos pos,double frequency,Runnable next) {
        // CIO's per-phase accumulators can be newer than its device readouts during
        // the tick. Compare a single committed result, then report through GameTest.
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var seen=new java.util.concurrent.atomic.AtomicBoolean();
        var listener=new java.util.function.Consumer<com.george_vi.electroenergetics.events.FinishElectricSimulationEvent>() {
            @Override public void accept(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event) {
                if(event.level!=h.getLevel()||seen.get())return;
                seen.set(true);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
                try { verifyInverter(h,pos,frequency); } catch (RuntimeException|AssertionError error) { failure.set(error); }
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent.class,listener);
        h.runAfterDelay(2,()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            h.assertTrue(seen.get(),"No committed CIO result arrived");
            if(failure.get()!=null)h.fail(failure.get().getMessage());
            next.run();
        });
    }

    private static void verifyInverter(GameTestHelper h,BlockPos pos,double frequency) {
        var be=h.getLevel().getBlockEntity(pos);
        h.assertTrue((boolean)call(be,"isRunning"),"Inverter is not running");
        near(h,(double)call(be,"dcVolts"),300,.1,"DC input");near(h,(double)call(be,"runningHz"),frequency,.001,"AC frequency");
        var type=type(ROOT+"inverter.cee.InverterDevice");
        @SuppressWarnings({"rawtypes","unchecked"}) var device=DevicesSavedData.load(h.getLevel()).getDevice(pos,(Class)type);
        Object[] phases=(Object[])get(device,"sources");double[][] samples=new double[3][];
        double totalPower=0;
        for(int phase=0;phase<3;phase++){
            Object model=phases[phase];int count=16;
            h.assertTrue((int)get(model,"samples")==0,"CIO did not consume the committed phase samples");
            samples[phase]=(double[])get(model,"terminal");double peak=(double)get(model,"peak"),angle=(double)get(model,"startAngle"),step=(double)get(model,"step");
            double squares=0;
            for(int s=0;s<count;s++){double actual=samples[phase][s];double expected=peak*Math.sin(Math.toRadians(angle+step*s))*100/100.05;
                near(h,actual,expected,.04,"Loaded phase waveform");squares+=actual*actual;}
            double rms=Math.sqrt(squares/count);h.assertTrue(rms>10,"Phase is not powered");
            near(h,((double[])get(device,"phaseVolts"))[phase],rms,.001,"Native phase RMS");
            double power=(double)get(model,"sumVI")/count;
            near(h,power,squares/count/100,.06,"Real phase power");totalPower+=power;
        }
        for(int phase=0;phase<3;phase++){
            double squares=0;for(int s=0;s<16;s++){double d=samples[phase][s]-samples[(phase+1)%3][s];squares+=d*d;}
            near(h,((double[])get(device,"lineVolts"))[phase],Math.sqrt(squares/16),.001,"Native line RMS");
        }
        double dcAmps=(double)get(device,"dcAmps");near(h,dcAmps*300,totalPower/.96+15,.5,"DC draw includes native efficiency and idle draw");
        var backend=org.patryk3211.powergrid.config.CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend"));
        h.assertTrue(Bridges.get(h.getLevel()).backendCounts().getOrDefault(backend,0L)>0,"Requested backend was not exercised");
        System.out.println("CIO_ACCEPT inverter backend="+backend+" hz="+frequency+" acWatts="+totalPower+" dcWatts="+(dcAmps*300));
        System.out.println("CIO_ACCEPT diagnostic history at tick="+h.getLevel().getGameTime()+": "+Bridges.get(h.getLevel()).diagnostics());
    }

    @GameTest(template="empty", timeoutTicks=160)
    public static void powerKitsStillDeliverLoadedOutput(GameTestHelper h) {
        for(int variant=0;variant<2;variant++){
            int z=1+variant*4;var a=new BlockPos(1,2,z);var b=new BlockPos(4,2,z);var c=new BlockPos(7,2,z);
            for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
            h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,block(variant==0?"PowerGridModule":"ElectroEnergeticsModule",variant==0?"DEB_RECTIFIER_TIER1":"CEE_DEB_RECTIFIER_TIER1"));h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
            var source=h.absolutePos(a);var kit=h.absolutePos(b);var load=h.absolutePos(c);
            h.runAtTickTime(8,()->{((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(120);((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(120);
                wire(h,source,0,kit,0,true);wire(h,source,1,kit,1,true);wire(h,kit,2,load,0,true);wire(h,kit,3,load,1,true);});
            h.runAtTickTime(70,()->near(h,Math.abs(voltage(h,load)),12,.02,"Power Kit loaded output"));
        }
        h.runAtTickTime(75,h::succeed);
    }

    @GameTest(template="empty", timeoutTicks=160)
    public static void savedCouplerIsInertAcrossRebuildAndRestart(GameTestHelper h) {
        var level=h.getLevel();var source=new BlockPos(3000,64,3000);var coupler=source.east(3);var ceeSource=coupler.east(3);
        level.setChunkForced(source.getX()>>4,source.getZ()>>4,true);
        boolean setup=System.getProperty("wwpg.test.restartPhase","SETUP").equals("SETUP");
        if(setup){for(var p:new BlockPos[]{source,coupler,ceeSource})level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
            // Direct fixture placement models a coupler already saved before WWPG was installed.
            level.setBlockAndUpdate(coupler,block("BridgeModule","MINI_GRID_COUPLER").defaultBlockState());
            level.setBlockAndUpdate(ceeSource,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        } else h.assertTrue(CioCompatibility.gridCoupler(level.getBlockState(coupler).getBlock()),"Saved coupler disappeared");
        h.runAtTickTime(10,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(source)).setValue(120);
            DevicesSavedData.load(level).getDevice(ceeSource,CreativeBatteryDevice.class).voltage=90;
            if(setup){wire(h,source,0,coupler,0,true);wire(h,source,1,coupler,1,true);
                wire(h,ceeSource,1,coupler,0,false);wire(h,ceeSource,0,coupler,1,false);}
        });
        h.runAtTickTime(65,()->{
            verifyCoupler(h,source,coupler,ceeSource);
            var be=level.getBlockEntity(coupler);var saved=be.saveWithoutMetadata(level.registryAccess());be.loadWithComponents(saved,level.registryAccess());
            IElectric.getAt(level,coupler).getBehaviour(level,coupler,level.getBlockState(coupler)).rebuildCircuit(false);
        });
        h.runAtTickTime(95,()->{verifyCoupler(h,source,coupler,ceeSource);System.out.println("CIO_ACCEPT saved coupler phase="+(setup?"SETUP":"VERIFY"));h.succeed();});
    }

    private static void verifyCoupler(GameTestHelper h,BlockPos source,BlockPos coupler,BlockPos ceeSource) {
        var level=h.getLevel();var behaviour=IElectric.getAt(level,coupler).getBehaviour(level,coupler,level.getBlockState(coupler));
        h.assertTrue(!behaviour.hasInternals(),"Disabled coupler retained internal transfer components");
        near(h,Math.abs(voltage(h,source)),120,.02,"PG source remains independent");
        var results=InfrastructureSavedData.load(level).ticker.lastResults;
        near(h,Math.abs(results.getVoltageAt(ceeSource,0,1)),90,.02,"CEE source remains independent");
        h.assertTrue(Double.isNaN(results.getVoltageAt(coupler,0,1)),"Disabled CEE port published a normal reading");
        h.assertTrue(behaviour.getConnections().values().stream().mapToInt(java.util.Collection::size).sum()==2,"Saved PG wires were removed");
        for(int pin=0;pin<2;pin++)h.assertTrue(InfrastructureSavedData.load(level).getConnections(new InWorldNode(pin,coupler)).size()==1,"Saved CEE wire was removed");
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void newCouplerPlacementIsBlockedWithExplanation(GameTestHelper h) {
        var floor=new BlockPos(2,1,2);h.setBlock(floor,Blocks.STONE);var pos=h.absolutePos(floor);
        var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=new ItemStack(block("BridgeModule","MINI_GRID_COUPLER"));
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter().add(0,.5,0),Direction.UP,pos,false)));
        h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(),"Disabled coupler was placed");
        h.assertTrue(stack.getCount()==1,"Blocked placement consumed the item");h.succeed();
    }

    private static void register(CeeAdapters.Registry registry,MicroTickingElectricalProperties source){
        @SuppressWarnings({"rawtypes","unchecked"}) Class<MicroTickingElectricalProperties> type=(Class)source.getClass();
        registry.register("test:cio_phase",type,new CioInverterAdapter());
    }
    private static MicroTickingElectricalProperties source(double angle,double step,double peak,int count){
        try{var c=type(CioCompatibility.PHASE_TYPE).getDeclaredConstructor();c.setAccessible(true);var p=(MicroTickingElectricalProperties)c.newInstance();
            var start=p.getClass().getDeclaredMethod("start",double.class,double.class,double.class,int.class);start.setAccessible(true);start.invoke(p,angle,step,peak,count);return p;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Install the verified CIO fixture",e);}
    }
    private static void sameHistory(GameTestHelper h,Object a,Object b){
        for(String key:new String[]{"startAngle","step","peak","v","sumV2","sumI2","sumVI","samples"})h.assertTrue(get(a,key).equals(get(b,key)),"CIO history differs: "+key);
        h.assertTrue(Arrays.equals((double[])get(a,"terminal"),(double[])get(b,"terminal")),"CIO waveform history differs");
        var x=(MicroTickingElectricalProperties)a;var y=(MicroTickingElectricalProperties)b;
        near(h,x.resistance,y.resistance,0,"Resistance history");near(h,x.currentSource,y.currentSource,0,"Current history");
        near(h,x.voltageSource,y.voltageSource,0,"Voltage history");h.assertTrue(x.isForcedVoltageSource==y.isForcedVoltageSource,"Source family history");
    }
    private static Class<?> type(String name){try{return Class.forName(name);}catch(ClassNotFoundException e){throw new IllegalStateException("Install the verified CIO fixture",e);}}
    private static Block block(String module,String field){try{return (Block)((Supplier<?>)type(ROOT+"modules."+module).getField(field).get(null)).get();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object get(Object owner,String name){try{var f=field(owner,name);return f.get(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void set(Object owner,String name,Object value){try{field(owner,name).set(owner,value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Field field(Object owner,String name)throws NoSuchFieldException{for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object call(Object owner,String name){try{return owner.getClass().getMethod(name).invoke(owner);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object call(Object owner,String name,Class<?> arg,Object value){try{return owner.getClass().getMethod(name,arg).invoke(owner,value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static double voltage(GameTestHelper h,BlockPos p){return new BlockWireEndpoint(p,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(p,1).getNode(h.getLevel()).getVoltage();}
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb,boolean pg){WiringGameTests.connect(h,a,pa,b,pb,pg);}
    private static void near(GameTestHelper h,double actual,double expected,double tolerance,String name){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<=tolerance,name+": "+actual+" expected "+expected);}
}
