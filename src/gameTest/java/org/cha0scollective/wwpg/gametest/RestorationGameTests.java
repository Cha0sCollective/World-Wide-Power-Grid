package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.mixin.CapacitorHistoryAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class RestorationGameTests {
    @GameTest(template="empty", timeoutTicks=60)
    public static void coldBoardSavePreservesUnsolvedCapacitorHistory(GameTestHelper h) {
        var relative=new BlockPos(1,2,1);h.setBlock(relative.below(),Blocks.STONE);
        h.setBlock(relative,ModdedBlocks.CIRCUIT_BOARD.get());var pos=h.absolutePos(relative);
        h.runAtTickTime(5,()->{
            var level=h.getLevel();var board=(CircuitBoardBlockEntity)level.getBlockEntity(pos);
            board.setSchematic(ElectronicsPersistenceGameTests.capacitorBoard());
            var saved=board.saveWithoutMetadata(level.registryAccess());
            for(var entry:saved.getCompound("Schematic").getList("Components",10)) {
                var tag=(net.minecraft.nbt.CompoundTag)entry;
                if(tag.getString("Id").equals("powergrid:capacitor"))tag.getCompound("Properties").putFloat("powergrid:charge",7.5f);
            }
            board.loadWithComponents(saved,level.registryAccess());
            var capacitor=board.getComponentsStream().filter(c->c.component==Components.CAPACITOR.get()).findFirst().orElseThrow();
            var wire=(CRSeriesWire)capacitor.wires.getFirst();
            GlobalElectricNetworks.getWorldNetworks(level).prepareForConnection(new BlockWireEndpoint(pos,0).getNode(level),new BlockWireEndpoint(pos,1).getNode(level));
            BoardComponentGameTests.near(h,((CapacitorHistoryAccessor)wire).wwpg$storedVoltage(),7.5,1e-6,"Loaded capacitor history");
            BoardComponentGameTests.near(h,wire.capacitorVoltage(),0,1e-6,"Deliberately unsolved terminal reading");
            capacitor.component.tick(capacitor); // native persistence callback before the first solve
            var persisted=capacitor.serializeNbt(level.registryAccess()).getCompound("Properties").getFloat("powergrid:charge");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("RESTORE_COLD_SAVE: measured={}, history={}, persisted={}",wire.capacitorVoltage(),((CapacitorHistoryAccessor)wire).wwpg$storedVoltage(),persisted);
            BoardComponentGameTests.near(h,persisted,7.5,1e-6,"Cold board tick overwrote stored charge with a zero terminal reading");
            board.loadWithComponents(board.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
            var restored=board.getComponentsStream().filter(c->c.component==Components.CAPACITOR.get()).findFirst().orElseThrow();
            BoardComponentGameTests.near(h,((CapacitorHistoryAccessor)restored.wires.getFirst()).wwpg$storedVoltage(),7.5,1e-6,"Second deserialize lost capacitor history");
            h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void missingWireWaitsForEntitiesThenExpires(GameTestHelper h) {
        // Keep both blocks and their hanging-wire midpoint inside this chunk.
        var source=new BlockPos(4100,64,4100);var load=source.offset(3,0,0);var level=h.getLevel();
        var chunk=new net.minecraft.world.level.ChunkPos(source);
        DelayedEntityLoads.hold(level,chunk,60);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunk(chunk.x,chunk.z);
        WorldNetworks.PartId[] id=new WorldNetworks.PartId[1];
        h.startSequence().thenIdle(5).thenExecute(()->{
            for(var pos:new BlockPos[]{source,load}) {
                level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
            level.setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        }).thenIdle(5).thenExecute(()->{
            WiringGameTests.connect(h,source,0,load,0,true);
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            var part=world.findConnectedWires(new BlockWireEndpoint(source,0)).getFirst();id[0]=part.persistentOwnerId;
            var wire=part.owner;h.assertTrue(wire!=null,"Native wire part has no owner");
            h.assertTrue(new net.minecraft.world.level.ChunkPos(wire.blockPosition()).equals(chunk),"Test wire escaped the delayed chunk");
            wire.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // retain its saved PG part but make the entity absent
            world.bounty(id[0],chunk);
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Controlled wire-check loading delay did not run");
            boolean retained=GlobalElectricNetworks.getWorldNetworks(level).getPart(id[0])!=null;
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("RESTORE_WIRE_CHECK: entitiesReady=false, retained={}",retained);
            h.assertTrue(retained,"PG expired a missing wire before the entity load completed");
        }).thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for fixture entity readiness"))
        .thenIdle(5).thenExecute(()->h.assertTrue(GlobalElectricNetworks.getWorldNetworks(level).getPart(id[0])!=null,"Ready wire did not receive its full grace period"))
        .thenIdle(10).thenExecute(()->{
            h.assertTrue(GlobalElectricNetworks.getWorldNetworks(level).getPart(id[0])==null,"Entity-ready missing wire never expired");
            level.setChunkForced(chunk.x,chunk.z,false);
            h.succeed();
        });
    }
}
