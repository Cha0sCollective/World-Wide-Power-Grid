package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.experimental.PlainWireBatch;
import org.cha0scollective.wwpg.experimental.ScopedRestoration;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg_tree_scope")
@PrefixGameTestTemplate(false)
public final class ScopedRestorationGameTests {
    private static BlockPos independent(GameTestHelper h) {
        h.assertTrue(ScopedRestoration.ENABLED,"Scoped fixture requires the opt-in scope");
        var source=new BlockPos(1,6,1);var load=new BlockPos(7,6,1);var level=h.getLevel();
        h.setBlock(source.below(),Blocks.STONE);h.setBlock(load.below(),Blocks.STONE);
        h.setBlock(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(source);var l=h.absolutePos(load);
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(20);
            ((ResistorBlockEntity)level.getBlockEntity(l)).setValue(1000);
            for(int terminal=0;terminal<2;terminal++)WiringGameTests.connect(h,s,terminal,l,terminal,true);
        });
        return l;
    }
    @GameTest(template="empty",batch="scope_membership",timeoutTicks=60)
    public static void negativeDecisionIsRecheckedAfterNativeMergeAndFinalOwnerReplacement(GameTestHelper h) {
        var positions=WireTreeBudgetGameTests.ring(h,4);var load=independent(h);var level=h.getLevel();
        h.runAtTickTime(25,()->{
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            WireTreeBudgetGameTests.reload(h,positions);
            RestorationCoordinator.begin(level);ScopedRestoration.prepare(java.util.List.of(world));
            var healthy=world.globalExternalNodes.get(new BlockWireEndpoint(load,0)).getNetwork();
            var queued=world.globalExternalNodes.get(new BlockWireEndpoint(positions.getFirst(),0));
            var affected=queued.getNetwork();
            h.assertTrue(healthy!=affected&&!RestorationCoordinator.defer(world,healthy),"Fixture did not begin with a negative independent decision");
            healthy.merge(affected);
            h.assertTrue(queued.getNetwork()==healthy,"Native merge did not change ownership");
            h.assertTrue(RestorationCoordinator.defer(world,healthy),"Cached negative decision released newly absorbed queued work");
            var replacement=world.newNetwork();replacement.merge(healthy);
            ScopedRestoration.finishPreparation(java.util.List.of(world));
            h.assertTrue(queued.getNetwork()==replacement&&RestorationCoordinator.paused(replacement),"Final replacement owner escaped the hold");
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="scope_unknown",timeoutTicks=60)
    public static void unsupportedPhysicalEndpointFallsBackWithoutResolvingIt(GameTestHelper h) {
        var positions=WireTreeBudgetGameTests.ring(h,4);var load=independent(h);var level=h.getLevel();
        h.runAtTickTime(25,()->{
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            WireTreeBudgetGameTests.reload(h,positions);
            var unknown=new org.patryk3211.powergrid.electricity.wire.IWireEndpoint() {
                @Override public org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode getNode(net.minecraft.world.level.Level ignored) { throw new AssertionError("Scope resolved an unsupported endpoint"); }
                @Override public void joinNetwork(net.minecraft.world.level.Level ignored,org.patryk3211.powergrid.electricity.sim.ElectricalNetwork network) { throw new AssertionError("Scope initialized an endpoint"); }
                @Override public net.minecraft.world.phys.Vec3 getExactPosition(net.minecraft.world.level.Level ignored) { throw new AssertionError("Scope invoked geometry"); }
                @Override public org.patryk3211.powergrid.electricity.wire.WireEndpointType type() { throw new AssertionError("Scope requested serialization type"); }
                @Override public void read(net.minecraft.nbt.CompoundTag tag) { throw new AssertionError("Scope read serialization"); }
                @Override public void write(net.minecraft.nbt.CompoundTag tag) { throw new AssertionError("Scope wrote serialization"); }
            };
            world.globalExternalNodes.put(unknown,new org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode(unknown));
            try {
                ScopedRestoration.prepare(java.util.List.of(world));
                h.assertTrue(ScopedRestoration.pausedAt(level,load),"Unsupported physical mapping was incorrectly certified independent");
            } finally {
                world.globalExternalNodes.remove(unknown);ScopedRestoration.prepare(java.util.List.of(world));
            }
            h.assertTrue(!ScopedRestoration.pausedAt(level,load),"Ephemeral fallback survived removal of the unsupported mapping");
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="scope_streaming",timeoutTicks=280)
    public static void independentCircuitAdvancesSixteenStepsDuringRepeatedSavedArrivals(GameTestHelper h) {
        h.assertTrue(ScopedRestoration.ENABLED,"Scoped fixture requires the opt-in scope");
        var positions=WireTreeBudgetGameTests.ring(h,16);var level=h.getLevel();
        var source=new BlockPos(1,6,1);var load=new BlockPos(7,6,1);
        h.setBlock(source.below(),Blocks.STONE);h.setBlock(load.below(),Blocks.STONE);
        h.setBlock(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(source);var l=h.absolutePos(load);
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(20);
            ((ResistorBlockEntity)level.getBlockEntity(l)).setValue(1000);
            for(int terminal=0;terminal<2;terminal++)WiringGameTests.connect(h,s,terminal,l,terminal,true);
        });
        for(int tick:new int[]{25,37,49,61,73,85})h.runAtTickTime(tick,()->WireTreeBudgetGameTests.reload(h,positions));
        long[] previous={-1};
        for(int tick=27;tick<=100;tick++){final int sample=tick;h.runAtTickTime(tick,()->{
            h.assertTrue(WireTreeBatch.pending(level)>0,"Repeated saved arrivals unexpectedly drained");
            h.assertTrue(RestorationCoordinator.pausedAt(level,positions.getFirst()),"Affected ring was not held");
            h.assertTrue(!RestorationCoordinator.pausedAt(level,l),"Independent circuit was held at tick " + sample + ": " + ScopedRestoration.diagnostic(level));
            var network=GlobalElectricNetworks.getWorldNetworks(level).globalExternalNodes.get(new BlockWireEndpoint(l,0)).getNetwork();
            h.assertTrue(!RestorationCoordinator.paused(network),"Independent native network was paused");
            long steps=((PlainWireBatch.NetworkAccess)network).wwpg$batchedSteps();
            if(previous[0]>=0)h.assertTrue(steps-previous[0]==16,"Independent circuit did not advance exactly sixteen substeps");
            previous[0]=steps;
            var reading=ElectricalMeasurements.readVoltageBetween(level,l,0,l,1);
            h.assertTrue(reading.status()==MeasurementStatus.VALID&&reading.context().substeps()==16,"Independent readings became unavailable");
            if(sample==50)((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(30);
            if(sample>=55)BoardComponentGameTests.near(h,reading.voltage().orElseThrow().meanVoltageVolts(),30,.05,"Independent setting change while restoration continues");
        });}
        h.runAtTickTime(250,()->{
            h.assertTrue(WireTreeBatch.pending(level)==0,"Repeated arrivals failed to drain after stopping");
            h.assertTrue(!RestorationCoordinator.pausedAt(level,positions.getFirst()),"Restored ring remained held");
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,positions.getFirst(),0,positions.getFirst(),1).status()==MeasurementStatus.VALID,"Restored ring did not publish readings");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",batch="scope_split",timeoutTicks=280)
    public static void disconnectedCircuitResumesWhileSavedArrivalsContinue(GameTestHelper h) {
        var positions=WireTreeBudgetGameTests.ring(h,16);var load=independent(h);var level=h.getLevel();
        org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart[] bridge={null};
        h.runAtTickTime(6,()->{
            WiringGameTests.connect(h,positions.getFirst(),0,load,0,true);
            bridge[0]=GlobalElectricNetworks.getWorldNetworks(level).findConnectedWires(new BlockWireEndpoint(load,0)).stream()
                    .filter(p -> p.getEndpoint1().equals(new BlockWireEndpoint(positions.getFirst(),0))
                            || p.getEndpoint2().equals(new BlockWireEndpoint(positions.getFirst(),0))).findFirst().orElseThrow();
        });
        for(int tick:new int[]{25,37,49,61,73,85})h.runAtTickTime(tick,()->WireTreeBudgetGameTests.reload(h,positions));
        h.runAtTickTime(30,()->h.assertTrue(RestorationCoordinator.pausedAt(level,load),"Connected circuit was not held"));
        h.runAtTickTime(40,()->{
            var owner=bridge[0].owner;bridge[0].remove();owner.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        });
        long[] previous={-1};
        for(int tick=45;tick<=100;tick++){final int sample=tick;h.runAtTickTime(tick,()->{
            h.assertTrue(WireTreeBatch.pending(level)>0,"Repeated saved arrivals unexpectedly drained");
            h.assertTrue(RestorationCoordinator.pausedAt(level,positions.getFirst()),"Affected ring was released");
            h.assertTrue(!RestorationCoordinator.pausedAt(level,load),"Disconnected circuit remained held at tick " + sample + ": " + ScopedRestoration.diagnostic(level));
            var network=GlobalElectricNetworks.getWorldNetworks(level).globalExternalNodes.get(new BlockWireEndpoint(load,0)).getNetwork();
            long steps=((PlainWireBatch.NetworkAccess)network).wwpg$batchedSteps();
            if(previous[0]>=0)h.assertTrue(steps-previous[0]==16,"Disconnected circuit did not advance sixteen substeps");
            previous[0]=steps;
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,load,0,load,1).status()==MeasurementStatus.VALID,"Disconnected readings unavailable");
        });}
        h.runAtTickTime(250,()->{h.assertTrue(WireTreeBatch.pending(level)==0,"Stopped arrivals failed to drain");DynamicGameTests.audit(h);h.succeed();});
    }
}
