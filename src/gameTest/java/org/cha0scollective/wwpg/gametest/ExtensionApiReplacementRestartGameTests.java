package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.cha0scollective.wwpg.extension.ApiCeeWireIdentities;
import org.cha0scollective.wwpg.wiring.RemovedWires;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Fresh-JVM proof: replace a saved API owner before its real native wire entities load. */
@GameTestHolder("wwpg_api_restart")
@PrefixGameTestTemplate(false)
public final class ExtensionApiReplacementRestartGameTests {
    private static final BlockPos SOURCE = new BlockPos(13312,64,13312), LOAD = SOURCE.east(3), CEE_LOAD = SOURCE.east(6);
    private static final Path EXPECTED = Path.of("extension-api-replacement-before-restart.nbt");

    @GameTest(template="empty",timeoutTicks=300)
    public static void coldOwnerReplacementDuringDelayedEntityLoadingRetiresOnlyOldWires(GameTestHelper h) throws IOException {
        var level=h.getLevel();var chunk=new ChunkPos(SOURCE);
        boolean verify=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        var accepted=new AtomicBoolean();
        Consumer<ServerStoppingEvent> stopping=event->{
            if(event.getServer()!=level.getServer()||!accepted.get())return;
            var expected=new CompoundTag();expected.put("Capacitor",saved(h,LOAD));expected.put("CeeCapacitor",saved(h,CEE_LOAD));
            var ids=wireIds(h,LOAD);h.assertTrue(ids.size()==2,"Cold replacement setup lost its saved wires before shutdown");
            int i=0;for(var id:ids)expected.putUUID("Wire"+(i++),id);
            var ceeIds=ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD);
            h.assertTrue(ceeIds.size()==2,"Cold replacement setup lost its two saved CEE wire identities");
            i=0;for(var id:ceeIds)expected.putUUID("CeeWire"+(i++),id);
            try {NbtIo.writeCompressed(expected,EXPECTED);}catch(IOException error){throw new IllegalStateException(error);}
        };
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,stopping);
        if(!verify) {
            level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(SOURCE);
            for(var pos:new BlockPos[]{SOURCE,LOAD,CEE_LOAD}) {
                level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            }
            example(h,SOURCE,"source");example(h,LOAD,"capacitor");example(h,CEE_LOAD,"capacitor");
            h.startSequence().thenWaitUntil(()->h.assertTrue(ApiTerminals.canWire(level,SOURCE)&&ApiTerminals.canWire(level,LOAD)&&ApiTerminals.canWire(level,CEE_LOAD),"Waiting for cold SETUP chunk/entity readiness"))
                    .thenExecute(()-> {
                WiringGameTests.connect(h,SOURCE,0,LOAD,0,true);WiringGameTests.connect(h,SOURCE,1,LOAD,1,true);
                WiringGameTests.connect(h,SOURCE,0,CEE_LOAD,0,false);WiringGameTests.connect(h,SOURCE,1,CEE_LOAD,1,false);
            }).thenIdle(30).thenExecute(()-> {
                h.assertTrue(saved(h,LOAD).getDouble("StoredVoltage")>1,"Cold replacement setup must save charged storage");
                h.assertTrue(saved(h,CEE_LOAD).getDouble("StoredVoltage")>1,"Cold replacement setup must save CEE-wired charged storage");
                h.assertTrue(wireIds(h,LOAD).size()==2,"Cold replacement setup must save two native PG wires");
                accepted.set(true);level.getDataStorage().save();level.getChunkSource().save(true);DynamicGameTests.audit(h);
            }).thenSucceed();
            return;
        }

        var expected=NbtIo.readCompressed(EXPECTED,NbtAccounter.unlimitedHeap());
        int ownershipVersion=ApiTerminals.savedOwnershipFormat(level).orElse(-1);
        h.assertTrue(ownershipVersion==2,"Cold replacement requires actual disk-loaded ownership format 2, got "+ownershipVersion);
        var oldIds=Set.of(expected.getUUID("Wire0"),expected.getUUID("Wire1"));
        var oldCeeIds=Set.of(expected.getUUID("CeeWire0"),expected.getUUID("CeeWire1"));
        h.assertTrue(ApiTerminals.existing(level,LOAD)==null,"Cold replacement proof requires a genuinely fresh API sidecar");
        h.assertTrue(ApiTerminals.existing(level,CEE_LOAD)==null,"Cold CEE replacement proof requires a genuinely fresh API sidecar");
        DelayedEntityLoads.hold(level,chunk,100);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(SOURCE);
        h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Cold replacement did not defer disk-loaded wire entities");
        var before=saved(h,LOAD);
        for(var key:new String[]{"StoredVoltage","StoredCurrent","CommitCount","WWPGExampleStateVersion"})
            h.assertTrue(Objects.equals(expected.getCompound("Capacitor").get(key),before.get(key)),"Cold replacement changed saved "+key+" before its first solve");
        h.assertTrue(before.getDouble("StoredVoltage")>1,"Cold replacement reference was not charged");
        var beforeCee=saved(h,CEE_LOAD);
        for(var key:new String[]{"StoredVoltage","StoredCurrent","CommitCount","WWPGExampleStateVersion"})
            h.assertTrue(Objects.equals(expected.getCompound("CeeCapacitor").get(key),beforeCee.get(key)),"Cold CEE replacement changed saved "+key+" before its first solve");
        h.assertTrue(beforeCee.getDouble("StoredVoltage")>1,"Cold CEE replacement reference was not charged");
        // Deliberately do not discover terminal geometry or bind an API node.
        // Ordinary players can remove a loaded block before its first solve.
        level.setBlockAndUpdate(LOAD,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        level.setBlockAndUpdate(CEE_LOAD,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        h.assertTrue(ElectricalMeasurements.read(level,LOAD).status()==MeasurementStatus.NOT_READY,"Native replacement inherited saved API results");
        h.assertTrue(ElectricalMeasurements.read(level,CEE_LOAD).status()==MeasurementStatus.NOT_READY,"CEE-wired native replacement inherited saved API results");

        h.startSequence().thenIdle(5).thenExecute(()-> {
            h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Cold native replacement missed the partial-load window");
            ExtensionApiLifecycleGameTests.assertLoadingWireRejection(h,SOURCE);
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            world.addAndMigrateNode(new BlockWireEndpoint(LOAD,0));world.addAndMigrateNode(new BlockWireEndpoint(LOAD,1));
            world.addAndMigrateNode(new BlockWireEndpoint(CEE_LOAD,0));world.addAndMigrateNode(new BlockWireEndpoint(CEE_LOAD,1));
        }).thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for cold replacement wire readiness"))
                .thenIdle(2).thenExecute(()-> {
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            for(var id:oldIds) {
                h.assertTrue(world.getPart(new WorldNetworks.SimpleId(id))==null,"Old API wire migrated into cold native replacement: "+id);
                h.assertTrue(RemovedWires.get(level).contains(id),"Cold late wire entity lacks a removal tombstone: "+id);
            }
            h.assertTrue(ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD).stream().noneMatch(oldCeeIds::contains),"Old CEE wire UUID survived the cold native replacement");
            ((ResistorBlockEntity)level.getBlockEntity(LOAD)).setValue(100);
            ((ResistorBlockEntity)level.getBlockEntity(CEE_LOAD)).setValue(100);
            WiringGameTests.connect(h,SOURCE,0,LOAD,0,true);WiringGameTests.connect(h,SOURCE,1,LOAD,1,true);
            WiringGameTests.connect(h,SOURCE,0,CEE_LOAD,0,false);WiringGameTests.connect(h,SOURCE,1,CEE_LOAD,1,false);
        }).thenIdle(8).thenExecute(()-> {
            var reading=ElectricalMeasurements.readVoltageBetween(level,LOAD,0,LOAD,1);
            h.assertTrue(reading.status()==MeasurementStatus.VALID,"Cold native replacement failed to recover: "+reading.detail());
            double voltage=reading.voltage().orElseThrow().lastVoltageVolts();
            h.assertTrue(Math.abs(voltage-20)<.03,"Cold native replacement after fresh wiring: "+voltage);
            var newIds=wireIds(h,LOAD);
            h.assertTrue(newIds.size()==2&&newIds.stream().noneMatch(oldIds::contains),"Cold cleanup removed replacement wires or reused old identities");
            h.assertTrue(ElectricalMeasurements.read(level,LOAD).status()==MeasurementStatus.NOT_READY,"Native replacement acquired API circuit ownership");
            var ceeReading=ElectricalMeasurements.readVoltageBetween(level,CEE_LOAD,0,CEE_LOAD,1);
            h.assertTrue(ceeReading.status()==MeasurementStatus.VALID,"Cold native CEE-wired replacement failed to recover: "+ceeReading.detail());
            double ceeVoltage=ceeReading.voltage().orElseThrow().lastVoltageVolts();
            h.assertTrue(Math.abs(ceeVoltage-20)<.03,"Cold native replacement after fresh CEE wiring: "+ceeVoltage);
            var newCeeIds=ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD);
            h.assertTrue(newCeeIds.size()==2&&newCeeIds.stream().noneMatch(oldCeeIds::contains),"Cold cleanup removed new CEE wires or reused old identities");
            h.assertTrue(ElectricalMeasurements.read(level,CEE_LOAD).status()==MeasurementStatus.NOT_READY,"CEE-wired native replacement acquired API circuit ownership");
            ExtensionApiLifecycleGameTests.assertSelectedApiReplacementRejectsSecondClick(h,SOURCE,LOAD);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_COLD_REPLACEMENT: diskStateVerified=true, delayedEntities=true, oldWiresRetired=true, newWiresPowered=true, oldCeeWiresRetired=true, newCeeWiresPowered=true, nativePG=true, nativeCEE=true, ownershipVersion={}",ownershipVersion);
            for(var pos:new BlockPos[]{SOURCE,LOAD,CEE_LOAD})level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            // Rearm a new saved API circuit for the next independent cold run.
            // The oracle is replaced only after this proof and rearming succeed.
            example(h,SOURCE,"source");example(h,LOAD,"capacitor");example(h,CEE_LOAD,"capacitor");
        }).thenWaitUntil(()->h.assertTrue(ApiTerminals.canWire(level,SOURCE)&&ApiTerminals.canWire(level,LOAD)&&ApiTerminals.canWire(level,CEE_LOAD),"Waiting for next-cold-run replacement retirement and native entity readiness"))
                .thenExecute(()-> {
            WiringGameTests.connect(h,SOURCE,0,LOAD,0,true);WiringGameTests.connect(h,SOURCE,1,LOAD,1,true);
            WiringGameTests.connect(h,SOURCE,0,CEE_LOAD,0,false);WiringGameTests.connect(h,SOURCE,1,CEE_LOAD,1,false);
        }).thenIdle(30).thenExecute(()-> {
            h.assertTrue(saved(h,LOAD).getDouble("StoredVoltage")>1,"Cold replacement failed to rearm saved storage for the next fresh JVM");
            h.assertTrue(saved(h,CEE_LOAD).getDouble("StoredVoltage")>1,"Cold replacement failed to rearm CEE-wired saved storage for the next fresh JVM");
            h.assertTrue(wireIds(h,LOAD).size()==2,"Cold replacement failed to rearm both saved native wires");
            h.assertTrue(ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD).size()==2,"Cold replacement failed to rearm both saved CEE wires");
            accepted.set(true);level.getDataStorage().save();level.getChunkSource().save(true);DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    private static Set<UUID> wireIds(GameTestHelper h,BlockPos pos) {
        var world=GlobalElectricNetworks.getWorldNetworks(h.getLevel());var ids=new HashSet<UUID>();
        for(int port=0;port<2;port++) {
            var parts=world.findConnectedWires(new BlockWireEndpoint(pos,port));
            if(parts!=null)for(var part:parts) {
                h.assertTrue(part.persistentOwnerId instanceof WorldNetworks.SimpleId,"Expected a simple native PG cord identity");
                ids.add(((WorldNetworks.SimpleId)part.persistentOwnerId).id());
            }
        }
        return ids;
    }

    private static CompoundTag saved(GameTestHelper h,BlockPos pos) {
        var entity=h.getLevel().getBlockEntity(pos);h.assertTrue(entity!=null,"Saved extension block entity missing at "+pos);
        return entity.saveWithoutMetadata(h.getLevel().registryAccess());
    }

    private static void example(GameTestHelper h,BlockPos pos,String name) {
        var id=ResourceLocation.fromNamespaceAndPath("wwpg_api_example",name);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Independent API add-on missing: "+id);
        h.getLevel().setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id).defaultBlockState());
    }
}
