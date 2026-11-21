package org.cha0scollective.wwpg.gametest.cee12;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.*;
import org.cha0scollective.wwpg.compat.cee12.mixin.ElectrocutionEntryAccessor;
import org.cha0scollective.wwpg.gametest.DynamicGameTests;
import org.cha0scollective.wwpg.gametest.WiringGameTests;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import java.util.*;
import java.util.function.Consumer;

/** Actual native contact and damage, not fabricated current or a direct call to hurt(). */
@GameTestHolder("wwpg_cee_electrocution")
@PrefixGameTestTemplate(false)
public final class ElectrocutionGameTests {
    private static final String BATCH="wwpg_cee_electrocution";
    @BeforeBatch(batch=BATCH) public static void setup(ServerLevel level) {
        DynamicGameTests.substeps(level);
        CEEConfigs.server().enableElectrocution.set(true);
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=110)
    public static void copperContactDamagesWithoutPersistentUnavailableReadings(GameTestHelper h) {
        var f=new Fixture(h,CEEWireTypes.COPPER.get(),23000,false);
        f.start();
        h.runAtTickTime(45,()->{
            h.assertTrue(f.mob.getHealth()<f.initialHealth,"Bare energized wire did not electrocute a susceptible mob");
            h.assertTrue(f.mob.isAlive(),"Contact test must remain alive to verify continued topology and readings");
            h.assertTrue(f.unavailableAfterSettling==0,"Standing contact repeatedly invalidated the wire readings");
            h.assertTrue(f.centers.size()==1,"The same contact changed central body identity: "+f.centers);
            f.assertHealthy();f.mob.discard();
        });
        h.runAtTickTime(60,()->{
            h.assertTrue(Double.isFinite(f.results().getVoltageAt(f.first())),"Wire reading did not recover after contact removal");
            h.assertTrue(!f.contacts().containsKey(f.mob),"Removed mob left an electrocution contact");
            f.assertHealthy();f.log("contact-settled");h.succeed();
        });
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=90)
    public static void dielectricProtectionKeepsNativeImmunity(GameTestHelper h) {
        var f=new Fixture(h,CEEWireTypes.COPPER.get(),23000,true);f.start();
        h.runAtTickTime(45,()->{h.assertTrue(f.mob.getHealth()==f.initialHealth,"Dielectric mob was damaged");
            h.assertTrue(!f.contacts().containsKey(f.mob),"Dielectric mob acquired a conductive contact");
            f.assertHealthy();f.mob.discard();f.log("dielectric");h.succeed();});
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=90)
    public static void insulationBelowItsRatingDoesNotElectrocute(GameTestHelper h) {
        double safeVoltage=Math.min(100,CEEWireTypes.STANDARD.get().maxInsulationVoltage()/2);
        h.assertTrue(safeVoltage>0,"Standard wire must have an insulation rating");
        var f=new Fixture(h,CEEWireTypes.STANDARD.get(),safeVoltage,false);f.start();
        h.runAtTickTime(45,()->{h.assertTrue(f.mob.getHealth()==f.initialHealth,"Within-rating insulated wire damaged the mob");
            h.assertTrue(!f.contacts().containsKey(f.mob),"Within-rating insulation acquired a conductive contact");
            f.assertHealthy();f.mob.discard();f.log("insulated");h.succeed();});
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=120)
    public static void rejectedContactCannotDamageAndRecoversBesideHealthyCircuit(GameTestHelper h) {
        var f=new Fixture(h,CEEWireTypes.COPPER.get(),23000,false);f.start();
        var failures=new IdentityHashMap<ElectricalNetwork,ISolverHook>();int[] rejected={0};
        Consumer<LevelTickEvent.Pre> inject=event->{
            if(event.getLevel()!=h.getLevel()||f.mob==null)return;
            var node=new BlockWireEndpoint(f.source,1).getNode(h.getLevel());
            if(node==null||node.getNetwork()==null)return;
            failures.computeIfAbsent(node.getNetwork(),network->{
                ISolverHook hook=new ISolverHook(){
                    public Collection<IElectricNode> coupledNodes(){return List.of();}
                    public void startIteration(int iteration){++rejected[0];throw new IllegalStateException("Electrocution regression deliberate rejection");}
                };network.innerHooks.add(hook);return hook;
            });
        };
        h.runAtTickTime(18,()->NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,inject));
        Runnable close=()->{NeoForge.EVENT_BUS.unregister(inject);failures.forEach((n,hook)->n.innerHooks.remove(hook));failures.clear();};
        h.runAtTickTime(45,()->{
            close.run();
            h.assertTrue(rejected[0]>0,"The negative control did not reject a solver step");
            h.assertTrue(f.mob.getHealth()==f.initialHealth,"Rejected current caused electrocution damage");
            h.assertTrue(!((CeeResultsAvailability)f.results()).available(f.first()),"Rejected wire was published as valid");
            h.assertTrue(!Double.isFinite(f.results().getVoltageAt(f.first())),"Rejected voltage became a numerical reading");
            var history=(ContactHazardHistory)f.wire();
            h.assertTrue(!Double.isFinite(history.wwpg$initializingHazard(h.getLevel().getGameTime())),"A real failure retained the accepted hazard cache");
            f.assertHealthy();
        });
        h.runAtTickTime(75,()->{
            close.run();h.assertTrue(f.mob.getHealth()<f.initialHealth,"Electrocution did not recover after a rejected solve");
            h.assertTrue(Double.isFinite(f.results().getVoltageAt(f.first())),"The healthy wire stayed unavailable after rejection ended");
            f.assertHealthy();f.mob.discard();f.log("reject-recover");h.succeed();
        });
        h.runAtTickTime(100,close);
    }

    private static final class Fixture {
        final GameTestHelper h;final WireType wireType;final double voltage;final boolean protectedMob;
        final BlockPos source,left,right,load,neighborSource,neighborLoad;
        Cow mob;float initialHealth;int unavailableAfterSettling;final Set<AttachedNode> centers=new HashSet<>();
        Fixture(GameTestHelper h,WireType type,double voltage,boolean protectedMob) {
            this.h=h;this.wireType=type;this.voltage=voltage;this.protectedMob=protectedMob;
            source=at(1,2,1);left=at(3,2,1);right=at(9,2,1);load=at(11,2,1);
            neighborSource=at(1,2,7);neighborLoad=at(5,2,7);
        }
        BlockPos at(int x,int y,int z){return h.absolutePos(new BlockPos(x,y,z));}
        InWorldNode first(){return new InWorldNode(0,left);}
        InWorldNode second(){return new InWorldNode(0,right);}
        InfrastructureSavedData data(){return InfrastructureSavedData.load(h.getLevel());}
        SimulationResults results(){return data().ticker.lastResults;}
        com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry wire(){
            return data().wireSimulationState.getAllConnections().stream().filter(e->e.getKey().equals(new InWorldNodeConnection(first(),second())))
                    .findFirst().orElseThrow().getValue();
        }
        @SuppressWarnings("unchecked") Map<Object,Object> contacts(){
            try {var f=data().wireElectrocutionModule.getClass().getDeclaredField("electrocutions");f.setAccessible(true);return (Map<Object,Object>)f.get(data().wireElectrocutionModule);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        void start(){
            for(int x=0;x<=12;x++)for(int z=0;z<=8;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(var p:List.of(source,left,right,load,neighborSource,neighborLoad))h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            h.getLevel().setBlockAndUpdate(load,CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
            h.getLevel().setBlockAndUpdate(left,CEEBlocks.CONNECTOR.getDefaultState());h.getLevel().setBlockAndUpdate(right,CEEBlocks.CONNECTOR.getDefaultState());
            h.getLevel().setBlockAndUpdate(neighborSource,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
            h.getLevel().setBlockAndUpdate(neighborLoad,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
            h.runAtTickTime(5,()->{
                DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=voltage;
                DevicesSavedData.load(h.getLevel()).getDevice(load,ResistorDevice.class).properties=ElectricalProperties.resistor(1000);
                data().connect(new InWorldNode(1,source),first(),wireType);data().connect(first(),second(),wireType);
                data().connect(second(),new InWorldNode(0,load),wireType);data().connect(new InWorldNode(0,source),new InWorldNode(1,load),wireType);
                ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(neighborSource)).setValue(10);
                ((ResistorBlockEntity)h.getLevel().getBlockEntity(neighborLoad)).setValue(1000);
                WiringGameTests.connect(h,neighborSource,0,neighborLoad,0,true);WiringGameTests.connect(h,neighborSource,1,neighborLoad,1,true);
            });
            h.runAtTickTime(20,()->{
                h.assertTrue(Double.isFinite(results().getVoltageAt(first())),"The contact circuit was not ready before the mob entered");
                var a=first().getPosition(h.getLevel());var b=second().getPosition(h.getLevel());
                var mid=QuadraticWireHelper.posAt(a,b,.5f,data().getConnectionData(new InWorldNodeConnection(first(),second())).getSag(a.distanceTo(b)));
                mob=EntityType.COW.create(h.getLevel());mob.setNoAi(true);mob.setPersistenceRequired();mob.setNoGravity(true);mob.setOnGround(true);
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);initialHealth=mob.getHealth();
                if(protectedMob)mob.addEffect(new MobEffectInstance(CEEMobEffects.DIELECTRIC,200));
                mob.setPos(mid.x,mid.y-.9,mid.z);h.getLevel().addFreshEntity(mob);
            });
            h.onEachTick(()->{
                if(mob==null||!mob.isAlive())return;mob.setOnGround(true);
                var entry=contacts().get(mob);if(entry!=null)centers.add(((ElectrocutionEntryAccessor)entry).wwpg$centralNode());
                if(h.getTick()>28&&!Double.isFinite(results().getVoltageAt(first())))unavailableAfterSettling++;
            });
        }
        void assertHealthy(){
            var a=new BlockWireEndpoint(neighborLoad,0).getNode(h.getLevel());var b=new BlockWireEndpoint(neighborLoad,1).getNode(h.getLevel());
            h.assertTrue(a!=null&&b!=null&&Math.abs(a.getVoltage()-b.getVoltage()-10)<.05,"A neighboring healthy circuit was disturbed");
        }
        void log(String label){WorldWidePowerGrid.LOGGER.info("CEE_ELECTROCUTION {} health={} initial={} unavailableAfterSettling={} centralIdentities={} wireVoltage={}",label,mob.getHealth(),initialHealth,unavailableAfterSettling,centers.size(),results().getVoltageAt(first()));}
    }
}
