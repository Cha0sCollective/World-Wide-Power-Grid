package org.cha0scollective.wwpg.gametest;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.experimental.ScopedRestoration;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg_tree_scope")
@PrefixGameTestTemplate(false)
public final class ScopedRestorationBoundaryGameTests {
    private static void api(GameTestHelper h, BlockPos position, String kind) {
        var id=ResourceLocation.fromNamespaceAndPath("wwpg_api_example",kind);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Scope fixture requires the frozen public API example");
        h.setBlock(position.below(),Blocks.STONE);h.setBlock(position,BuiltInRegistries.BLOCK.get(id));
    }
    private static CompoundTag saved(GameTestHelper h, BlockPos absolute) {
        return h.getLevel().getBlockEntity(absolute).saveWithoutMetadata(h.getLevel().registryAccess());
    }
    @GameTest(template="empty",batch="scope_saved_graph",timeoutTicks=60)
    public static void unresolvedSavedBranchesFollowMissingOwnersAndReplacementNodes(GameTestHelper h) {
        var ring=WireTreeBudgetGameTests.ring(h,4);var level=h.getLevel();
        h.runAtTickTime(25,()->{
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            var template=WireTreeBudgetGameTests.reload(h,ring).getFirst().toNbt();
            var middle=h.absolutePos(new BlockPos(1,8,1));var branch=middle.east(2);var far=middle.east(4);
            h.assertTrue(level.getBlockEntity(middle)==null&&level.getBlockEntity(branch)==null,"Middle owners must be absent");
            var parts=new ArrayList<TransmissionLinePart>();
            for(var pair:new BlockPos[][]{{ring.getFirst(),middle},{middle,branch},{branch,ring.get(1)},{branch,far}}) {
                var tag=template.copy();tag.putUUID("Owner",UUID.randomUUID());
                tag.put("Node1",new BlockWireEndpoint(pair[0],0).serialize());
                tag.put("Node2",new BlockWireEndpoint(pair[1],0).serialize());
                var part=TransmissionLinePart.uniquePart(tag,world);parts.add(part);
                h.assertTrue(TransmissionLinePart.uniquePart(tag,world)==part&&part.owner==null&&part.getLine()==null,"Unresolved saved identity changed");
            }
            var endpoint=new BlockWireEndpoint(far,0);var old=world.globalExternalNodes.get(endpoint);
            var replacement=new OwnedFloatingNode(endpoint);var network=world.newNetwork();network.addNode(replacement);
            world.globalExternalNodes.put(endpoint,replacement);
            try {
                ScopedRestoration.prepare(List.of(world));
                h.assertTrue(ScopedRestoration.pausedAt(level,middle)&&ScopedRestoration.pausedAt(level,far),"Saved unresolved branches escaped the scope");
                h.assertTrue(ScopedRestoration.holding(world,network),"Replacement external owner escaped the saved endpoint path");
                h.assertTrue(ScopedRestoration.diagnostic(level).equals("fallback=false, unknown=[]"),"Known saved graph used a dimension fallback");
                parts.getLast().remove();ScopedRestoration.prepare(List.of(world));
                h.assertTrue(!ScopedRestoration.holding(world,network),"Removed saved branch retained stale connectivity");
            } finally {
                for(var part:parts)if(world.getPart(part.persistentOwnerId)==part)part.remove();
                world.globalExternalNodes.put(endpoint,old);network.clear();world.subnetworks.remove(network);network.cleanup();
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="scope_cross_world",timeoutTicks=60)
    public static void nativeSplitControllerPropagatesHoldAcrossWorldsInEitherOrder(GameTestHelper h) {
        var ring=WireTreeBudgetGameTests.ring(h,4);var level=h.getLevel();
        h.runAtTickTime(25,()->{
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            var remoteLevel=level.getServer().getLevel(Level.NETHER);var remote=GlobalElectricNetworks.getWorldNetworks(remoteLevel);
            WireTreeBudgetGameTests.reload(h,ring);
            var p=world.globalExternalNodes.get(new BlockWireEndpoint(ring.getFirst(),0));
            var p0=world.globalExternalNodes.get(new BlockWireEndpoint(ring.getFirst(),1));
            var remotePosition=new BlockPos(81000,80,81000);
            var s=new OwnedFloatingNode(new BlockWireEndpoint(remotePosition,0));
            var s0=new OwnedFloatingNode(new BlockWireEndpoint(remotePosition,1));
            var primary=p.getNetwork();var secondary=remote.newNetwork();secondary.addNodes(s,s0);
            var first=new SplitTransformerControllerWire(p,p0,.0075,75);
            var second=new SplitTransformerControllerWire(s,s0,.0075,75);
            first.secondary=second;second.secondary=first;primary.addWire(first);secondary.addWire(second);
            try {
                for(var worlds:List.of(List.of(world,remote),List.of(remote,world))) {
                    ScopedRestoration.prepare(worlds);
                    h.assertTrue(ScopedRestoration.holding(remote,secondary)&&ScopedRestoration.pausedAt(remoteLevel,remotePosition),"Cross-world controller released one side");
                    h.assertTrue(!WireTreeBatch.holding(remoteLevel),"Remote fixture unexpectedly had its own restoration queue");
                }
                first.secondary=null;second.secondary=null;ScopedRestoration.prepare(List.of(world,remote));
                h.assertTrue(!ScopedRestoration.holding(remote,secondary),"Removed controller link retained a remote hold");
            } finally {
                first.secondary=null;second.secondary=null;first.remove();second.remove();
                secondary.clear();remote.subnetworks.remove(secondary);secondary.cleanup();
            }
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="scope_independent_storage",timeoutTicks=280)
    public static void independentNativeCeeAndApiStorageAdvanceWhileAffectedApiStorageHolds(GameTestHelper h) {
        var ring=WireTreeBudgetGameTests.ring(h,16);var level=h.getLevel();
        var battery=new BlockPos(1,4,1);var terminal=battery.above();var load=new BlockPos(7,4,1);
        var ceeSource=new BlockPos(1,4,4);var ceeCap=new BlockPos(7,4,4);
        var apiSource=new BlockPos(1,8,1);var apiCap=new BlockPos(7,8,1);
        var heldSource=new BlockPos(1,8,4);var heldCap=new BlockPos(7,8,4);
        for(var p:List.of(battery,load,ceeSource,ceeCap))h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(battery,ModdedBlocks.BATTERY.get());h.setBlock(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING,Direction.DOWN));
        h.setBlock(ceeSource,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(ceeCap,com.george_vi.electroenergetics.CEEBlocks.CAPACITOR.get());
        api(h,apiSource,"source");api(h,apiCap,"capacitor");api(h,heldSource,"source");api(h,heldCap,"capacitor");
        var b=h.absolutePos(battery);var c=h.absolutePos(ceeCap);var a=h.absolutePos(apiCap);var held=h.absolutePos(heldCap);
        double[] before=new double[4];long[] commits={-1};Object[] models=new Object[2];
        h.runAtTickTime(5,()->{
            var nativeBattery=(org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity)level.getBlockEntity(b);nativeBattery.setEnergy(nativeBattery.getCapacity()*.5);
            ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)h.getBlockEntity(load)).setValue(1000);
            ((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)h.getBlockEntity(ceeSource)).setValue(10);
            com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level).getDevice(c,com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice.class).capacitance=.01;
            for(var pair:new BlockPos[][]{{terminal,load},{ceeSource,ceeCap},{apiSource,apiCap},{heldSource,heldCap}})
                for(int t=0;t<2;t++)WiringGameTests.connect(h,h.absolutePos(pair[0]),t,h.absolutePos(pair[1]),t,true);
            WiringGameTests.connect(h,ring.getFirst(),0,held,0,true);
        });
        for(int tick:new int[]{25,37,49,61,73,85})h.runAtTickTime(tick,()->WireTreeBudgetGameTests.reload(h,ring));
        for(int tick=27;tick<=100;tick++){final int sample=tick;h.runAtTickTime(tick,()->{
            h.assertTrue(WireTreeBatch.pending(level)>0,"Streaming fixture unexpectedly drained");
            h.assertTrue(RestorationCoordinator.pausedAt(level,held),"Affected internal API owner was released");
            for(var pos:List.of(b,c,a))h.assertTrue(!RestorationCoordinator.pausedAt(level,pos),"Independent storage was paused: "+pos);
            var device=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level).getDevice(c,com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice.class);
            double energy=((org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity)level.getBlockEntity(b)).getEnergy();
            var free=saved(h,a);var paused=saved(h,held);var circuits=org.cha0scollective.wwpg.extension.ApiCircuits.get(level);
            if(sample==27) {
                before[0]=energy;before[1]=device.lastVoltage;before[2]=free.getDouble("StoredVoltage");before[3]=paused.getDouble("StoredVoltage");
                models[0]=circuits.elementIdentity(a,"storage");models[1]=circuits.elementIdentity(held,"storage");
                ((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)h.getBlockEntity(ceeSource)).setValue(30);
            } else h.assertTrue(paused.getDouble("StoredVoltage")==before[3],"Affected API capacitor advanced its internal state");
            if(commits[0]>=0)h.assertTrue(free.getLong("CommitCount")==commits[0]+1,"Independent API gameplay did not commit exactly once");
            commits[0]=free.getLong("CommitCount");
            h.assertTrue(circuits.elementIdentity(a,"storage")==models[0]&&circuits.elementIdentity(held,"storage")==models[1],"Restoration recreated API storage models");
            var reading=ElectricalMeasurements.read(level,a);
            h.assertTrue(reading.status()==MeasurementStatus.VALID&&reading.context().substeps()==16,"Independent API storage lost complete samples");
            h.assertTrue(ElectricalMeasurements.read(level,held).status()!=MeasurementStatus.VALID,"Affected API storage published valid readings");
            if(sample==100)h.assertTrue(energy<before[0]&&device.lastVoltage>before[1]&&free.getDouble("StoredVoltage")>before[2],"Independent storage did not advance");
        });}
        h.runAtTickTime(250,()->{
            h.assertTrue(WireTreeBatch.pending(level)==0&&ElectricalMeasurements.read(level,held).status()==MeasurementStatus.VALID,"Affected API storage did not resume");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
}
