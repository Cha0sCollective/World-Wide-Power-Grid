package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEntity;

/** GameTest success discards wire entities but leaves powered blocks in place. */
@GameTestHolder("wwpg_wire_recovery")
@PrefixGameTestTemplate(false)
public final class WireRemovalRecoveryGameTests {
    @BeforeBatch(batch="completed_wire_assemblies") public static void completed(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="removed_wire_assemblies") public static void removed(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="rejected_wire_assemblies") public static void rejected(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="warmup_wire_assemblies") public static void warmup(ServerLevel level) { DynamicGameTests.substeps(level); }

    // Preserve the actual assertion sequences, then continue observing their
    // remaining blocks after vanilla success removes the physical wires.
    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=110)
    public static void unknownProperty(GameTestHelper h) { IsolationGameTests.unknownPropertyIsolatesAndRecoversWithoutStalePower(h); }
    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=110)
    public static void nonfiniteProperty(GameTestHelper h) { IsolationGameTests.nonfinitePropertyIsolatesAndRecoversWithoutStalePower(h); }
    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=100)
    public static void inductor(GameTestHelper h) { DynamicGameTests.inductorAdvancesOnceIntoPgResistor(h); }
    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=100)
    public static void transformer(GameTestHelper h) { CeeAuxiliaryGameTests.ceeCurrentTransformerMapsInternalWindingNodes(h); }
    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=100)
    public static void directResistor(GameTestHelper h) { WiringGameTests.pgToCeeWithPgWire(h); }
    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=160)
    public static void phases(GameTestHelper h) { CeeAuxiliaryGameTests.ceeSynchroscopeReceivesMixedThreePhaseHistory(h); }

    @GameTest(template="empty",batch="completed_wire_assemblies",timeoutTicks=360)
    public static void completedCircuitsStayConvergedAcrossReloads(GameTestHelper h) {
        int[] ticks={0};
        h.onEachTick(()->{
            int tick=++ticks[0];
            if(tick==120||tick==220) DynamicGameTests.substeps(h.getLevel());
            if(tick<140)return;
            for(var network:GlobalElectricNetworks.getWorldNetworks(h.getLevel()).subnetworks)
                h.assertTrue(((ElectricalNetworkAccessor)network).wwpg$sourceCount()==0||network.isConverged(),
                        "Completed/reloaded assembly retained a rejected solve: "+network.getNodes());
            if(tick==320) { DynamicGameTests.audit(h); h.succeed(); }
        });
    }

    @GameTest(template="empty",batch="removed_wire_assemblies",timeoutTicks=130)
    public static void removingRealWireEntitiesSplitsAndReconnectsPoweredBlocks(GameTestHelper h) {
        var sourceRelative=new BlockPos(1,2,1); var loadRelative=new BlockPos(4,2,1);
        h.setBlock(sourceRelative.below(),Blocks.STONE); h.setBlock(loadRelative.below(),Blocks.STONE);
        h.setBlock(sourceRelative,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(loadRelative,CEEBlocks.CREATIVE_RESISTOR.get());
        var level=h.getLevel(); var source=h.absolutePos(sourceRelative); var load=h.absolutePos(loadRelative);
        ElectricalNetwork[] original={null};
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(source)).setValue(10);
            for(int terminal=0;terminal<2;terminal++)WiringGameTests.connect(h,source,terminal,load,terminal,true);
        });
        h.runAtTickTime(25,()->{
            voltage(h,load,10); original[0]=new BlockWireEndpoint(source,0).getNode(level).getNetwork();
            h.assertTrue(new BlockWireEndpoint(load,0).getNode(level).getNetwork()==original[0],"Powered endpoints were not joined");
            var wires=level.getEntitiesOfClass(WireEntity.class,new AABB(source).inflate(6)).stream()
                    .filter(wire->wire.isConnectedTo(source,0)||wire.isConnectedTo(source,1)).toList();
            h.assertTrue(wires.size()==2,"Fixture needs two real wire entities");
            wires.forEach(wire->wire.remove(Entity.RemovalReason.DISCARDED));
        });
        h.runAtTickTime(45,()->{
            var supply=new BlockWireEndpoint(source,0).getNode(level).getNetwork();
            var passive=new BlockWireEndpoint(load,0).getNode(level).getNetwork();
            h.assertTrue(supply!=passive&&supply!=original[0]&&passive!=original[0],"Removed wires left disconnected parts in the old solver");
            h.assertTrue(supply.isConverged()&&passive.isConverged(),"Disconnected powered/passive islands did not converge");
            voltage(h,load,0);
            for(int terminal=0;terminal<2;terminal++)WiringGameTests.connect(h,source,terminal,load,terminal,true);
        });
        h.runAtTickTime(70,()->{
            voltage(h,load,10);
            h.assertTrue(new BlockWireEndpoint(source,0).getNode(level).getNetwork()==new BlockWireEndpoint(load,0).getNode(level).getNetwork(),"Replacement wires did not merge the islands");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template="empty",batch="rejected_wire_assemblies",timeoutTicks=100)
    public static void genuineRejectedSolveStillReportsAndHoldsStorage(GameTestHelper h) {
        ExtensionApiRejectedSolveGameTests.nonthrowingRejectedSolveRollsBackCeeAndApiButKeepsHealthyCircuit(h);
        h.runAtTickTime(23,()->{
            long recorded=0;
            for(var message:org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).diagnostics()) {
                if(!message.contains("PG electrical substep did not converge"))continue;
                var count=java.util.regex.Pattern.compile("count=(\\d+):").matcher(message);
                if(count.find())recorded+=Long.parseLong(count.group(1));
            }
            // One Newton trial fails in each forced frame; its finite solution
            // can converge on the next substep, while the frame stays unavailable.
            h.assertTrue(recorded==2,"Diagnostic counts differ from the two actual rejected trials: "+recorded);
        });
    }

    @GameTest(template="empty",batch="warmup_wire_assemblies",timeoutTicks=80)
    public static void ordinaryWarmupStillHoldsStorageWithoutErrors(GameTestHelper h) {
        LoadingBatchGameTests.convergedWarmupHoldsCeeStorageWithoutFalseSolverErrors(h);
    }

    private static void voltage(GameTestHelper h,BlockPos load,double expected) {
        var reading=ElectricalMeasurements.readVoltageBetween(h.getLevel(),load,0,load,1);
        h.assertTrue(reading.status()==MeasurementStatus.VALID,"Native load measurement unavailable: "+reading.status());
        BoardComponentGameTests.near(h,reading.voltage().orElseThrow().meanVoltageVolts(),expected,.02,"Native load voltage");
    }
}
