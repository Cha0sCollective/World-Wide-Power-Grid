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
import org.cha0scollective.wwpg.extension.ApiCeeWireIdentities;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.cha0scollective.wwpg.wiring.RemovedWires;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Three separate JVMs prove removal survives a save made before native entity readiness. */
@GameTestHolder("wwpg_api_pending_restart")
@PrefixGameTestTemplate(false)
public final class ExtensionApiPendingRestartGameTests {
    private static final BlockPos SOURCE=new BlockPos(15360,64,15360),LOAD=SOURCE.east(3),CEE_LOAD=SOURCE.east(6);
    private static final Path EXPECTED=Path.of("extension-api-pending-before-restart.nbt");

    /** Registered only for this packaged singleton, before native saved-network startup. */
    public static void installBeforeStartup(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        String phase=System.getProperty("wwpg.test.restartPhase","SETUP");
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level&&level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                &&(phase.equals("ARM")||phase.equals("VERIFY"))) {
            DelayedEntityLoads.hold(level,new ChunkPos(SOURCE),1_000_000);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_PENDING_STARTUP_GATE: phase={}, installedBeforeFirstTick=true",phase);
        }
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void sameOwnerRetirementSurvivesSavingBeforeEntityReadiness(GameTestHelper h) throws IOException {
        var level=h.getLevel();var chunk=new ChunkPos(SOURCE);
        String phase=System.getProperty("wwpg.test.restartPhase","SETUP");
        h.assertTrue(Set.of("SETUP","ARM","VERIFY").contains(phase),"Unknown pending-retirement phase "+phase);
        var accepted=new AtomicBoolean();
        Consumer<ServerStoppingEvent> stopping=event->{
            if(event.getServer()!=level.getServer()||!accepted.get())return;
            if(phase.equals("ARM")) {
                h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"ARM gate expired before shutdown");
                h.assertTrue(ApiTerminals.pendingRemoval(level,LOAD)&&ApiTerminals.pendingRemoval(level,CEE_LOAD),"Pending retirements disappeared before shutdown");
                assertEmptyHistory(h,LOAD);assertEmptyHistory(h,CEE_LOAD);
                level.setChunkForced(chunk.x,chunk.z,false);
                level.getDataStorage().save();level.getChunkSource().save(true);
                org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_PENDING_RESTART_ARMED: actualInboxHeld=true, savedBeforeReady=true, replacementZeroCharge=true, nativePG=true, nativeCEE=true");
                // ServerStopping runs after the last electrical frame. Entity
                // manager close may consume IO, but cannot prepare or solve.
                DelayedEntityLoads.release(level,chunk);
            }else if(phase.equals("SETUP")) {
                var tag=new CompoundTag();tag.put("Capacitor",saved(h,LOAD));tag.put("CeeCapacitor",saved(h,CEE_LOAD));
                var pg=ApiTerminals.savedPgWireIdentities(level,LOAD);var cee=ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD);
                h.assertTrue(pg.size()==2&&cee.size()==2,"Pending-restart SETUP lost either wire system");
                int index=0;for(var id:pg) {h.assertTrue(id instanceof WorldNetworks.SimpleId,"Pending fixture expects simple PG wires");tag.putUUID("Wire"+(index++),((WorldNetworks.SimpleId)id).id());}
                index=0;for(var id:cee)tag.putUUID("CeeWire"+(index++),id);
                try {NbtIo.writeCompressed(tag,EXPECTED);}catch(IOException error){throw new IllegalStateException(error);}
                // The next process must install its IO gate before any saved
                // forced ticket can load the fixture owner's chunk.
                level.setChunkForced(chunk.x,chunk.z,false);
                level.getDataStorage().save();level.getChunkSource().save(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,stopping);
        if(phase.equals("SETUP")) {
            level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(SOURCE);
            for(var pos:new BlockPos[]{SOURCE,LOAD,CEE_LOAD}) {level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());}
            example(h,SOURCE,"source");example(h,LOAD,"capacitor");example(h,CEE_LOAD,"capacitor");
            h.startSequence().thenWaitUntil(()->h.assertTrue(ApiTerminals.canWire(level,SOURCE)&&ApiTerminals.canWire(level,LOAD)&&ApiTerminals.canWire(level,CEE_LOAD),"Waiting for SETUP readiness"))
                    .thenExecute(()->connect(h)).thenIdle(30).thenExecute(()-> {
                h.assertTrue(saved(h,LOAD).getDouble("StoredVoltage")>1&&saved(h,CEE_LOAD).getDouble("StoredVoltage")>1,"SETUP must save actual charged storage");
                accepted.set(true);level.getDataStorage().save();level.getChunkSource().save(true);DynamicGameTests.audit(h);
            }).thenSucceed();return;
        }
        var expected=NbtIo.readCompressed(EXPECTED,NbtAccounter.unlimitedHeap());
        int format=ApiTerminals.savedOwnershipFormat(level).orElse(-1);
        h.assertTrue(format==2,"Pending restart requires an actually loaded format-2 ownership file");
        var pg=Set.<WorldNetworks.PartId>of(new WorldNetworks.SimpleId(expected.getUUID("Wire0")),new WorldNetworks.SimpleId(expected.getUUID("Wire1")));
        var cee=Set.of(expected.getUUID("CeeWire0"),expected.getUUID("CeeWire1"));
        if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)!=null) {
            h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Startup consumed entity IO before the fixture gate");
            if(phase.equals("ARM")) {
                checkHistory(h,expected.getCompound("Capacitor"),saved(h,LOAD));checkHistory(h,expected.getCompound("CeeCapacitor"),saved(h,CEE_LOAD));
            }else {assertEmptyHistory(h,LOAD);assertEmptyHistory(h,CEE_LOAD);}
            assertUnreadyApiFrame(h);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_PENDING_PRE_FIXTURE: phase={}, diskHistoryUnchanged=true, apiObjects=0, resultsUnavailable=true, loadGeometryCached={}, ceeGeometryCached={}",phase,ApiTerminals.existing(level,LOAD)!=null,ApiTerminals.existing(level,CEE_LOAD)!=null);
        }
        // Native CEE wire restoration may discover read-only geometry before
        // entity readiness. The invariant is unchanged authoritative storage,
        // no extension simulation objects, and no accepted numerical results.
        assertUnreadyApiFrame(h);
        if(phase.equals("VERIFY")) {
            h.assertTrue(ApiTerminals.pendingPgWireIdentities(level,LOAD).equals(pg),"Disk lost exact pending PG removal identities");
            h.assertTrue(ApiTerminals.pendingCeeWireIdentities(level,CEE_LOAD).equals(cee),"Disk lost exact pending CEE removal identities");
        }
        DelayedEntityLoads.hold(level,chunk,phase.equals("ARM")?1_000_000:100);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(SOURCE);
        h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Pending restart did not hold real disk entity loading");
        assertUnreadyApiFrame(h);
        if(phase.equals("ARM")) {
            checkHistory(h,expected.getCompound("Capacitor"),saved(h,LOAD));checkHistory(h,expected.getCompound("CeeCapacitor"),saved(h,CEE_LOAD));
            h.assertTrue(saved(h,LOAD).getDouble("StoredVoltage")>1&&saved(h,CEE_LOAD).getDouble("StoredVoltage")>1,"ARM must replace previously charged devices");
            // The fixture does not request geometry or solve before removal;
            // native restoration may already have read-only geometry. The new
            // blocks retain the same block/provider/slot declarations.
            level.setBlockAndUpdate(LOAD,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(CEE_LOAD,Blocks.AIR.defaultBlockState());
            example(h,LOAD,"capacitor");example(h,CEE_LOAD,"capacitor");
            h.assertTrue(ApiTerminals.pendingPgWireIdentities(level,LOAD).equals(pg),"Physical removal did not queue saved PG wires");
            h.assertTrue(ApiTerminals.pendingCeeWireIdentities(level,CEE_LOAD).equals(cee),"Physical removal did not queue saved CEE wires");
            assertEmptyHistory(h,LOAD);assertEmptyHistory(h,CEE_LOAD);
            ExtensionApiLifecycleGameTests.assertLoadingWireRejection(h,LOAD);
            ExtensionApiLifecycleGameTests.assertLoadingWireRejection(h,CEE_LOAD);
            h.startSequence().thenWaitUntil(()->assertDeserializedPgWiresDeferred(h,chunk,pg)).thenIdle(10).thenExecute(()-> {
                h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"ARM must save while entities remain unavailable");
                assertEmptyHistory(h,LOAD);assertEmptyHistory(h,CEE_LOAD);
                assertUnreadyApiFrame(h);
                accepted.set(true);level.getDataStorage().save();level.getChunkSource().save(true);DynamicGameTests.audit(h);
            }).thenSucceed();return;
        }
        assertEmptyHistory(h,LOAD);assertEmptyHistory(h,CEE_LOAD);
        h.startSequence().thenWaitUntil(()->assertDeserializedPgWiresDeferred(h,chunk,pg)).thenIdle(10).thenExecute(()-> {
            h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"VERIFY must inspect replacement state before entity readiness");
            assertEmptyHistory(h,LOAD);assertEmptyHistory(h,CEE_LOAD);
            assertUnreadyApiFrame(h);
            ExtensionApiLifecycleGameTests.assertLoadingWireRejection(h,LOAD);
            ExtensionApiLifecycleGameTests.assertLoadingWireRejection(h,CEE_LOAD);
        }).thenWaitUntil(()->h.assertTrue(ApiTerminals.canWire(level,LOAD)&&ApiTerminals.canWire(level,CEE_LOAD),"Waiting for durable retirement to finish safely"))
                .thenIdle(3).thenExecute(()-> {
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            for(var id:pg) {
                h.assertTrue(world.getPart(id)==null,"Saved-before-ready PG wire reappeared after restart: "+id);
                h.assertTrue(RemovedWires.get(level).contains(((WorldNetworks.SimpleId)id).id()),"Retired PG wire lacks a durable removal tombstone");
            }
            h.assertTrue(ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD).stream().noneMatch(cee::contains),"Saved-before-ready CEE wire reappeared after restart");
            h.assertTrue(!ApiTerminals.pendingRemoval(level,LOAD)&&!ApiTerminals.pendingRemoval(level,CEE_LOAD),"Completed retirement was not cleared");
            h.assertTrue(saved(h,LOAD).getDouble("StoredVoltage")==0&&saved(h,CEE_LOAD).getDouble("StoredVoltage")==0,"Replacement inherited old charge or ghost power");
            connect(h);
        }).thenIdle(30).thenExecute(()-> {
            h.assertTrue(saved(h,LOAD).getDouble("StoredVoltage")>1&&saved(h,CEE_LOAD).getDouble("StoredVoltage")>1,"Fresh wires failed to power both replacements");
            h.assertTrue(ApiTerminals.savedPgWireIdentities(level,LOAD).size()==2&&ApiTerminals.savedPgWireIdentities(level,LOAD).stream().noneMatch(pg::contains),"Cleanup deleted new PG wires or reused retired IDs");
            h.assertTrue(ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD).size()==2&&ApiCeeWireIdentities.get(level).idsAt(CEE_LOAD).stream().noneMatch(cee::contains),"Cleanup deleted new CEE wires or reused retired IDs");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_PENDING_RESTART_PASSED: actualInboxHeld=true, savedBeforeReady=true, diskTombstonesVerified=true, nativePG=true, nativeCEE=true, replacementIsolated=true, replacementZeroCharge=true, newWiresPowered=true, ownershipVersion={}",format);
            accepted.set(true);level.getDataStorage().save();level.getChunkSource().save(true);DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    private static void connect(GameTestHelper h) {
        WiringGameTests.connect(h,SOURCE,0,LOAD,0,true);WiringGameTests.connect(h,SOURCE,1,LOAD,1,true);
        WiringGameTests.connect(h,SOURCE,0,CEE_LOAD,0,false);WiringGameTests.connect(h,SOURCE,1,CEE_LOAD,1,false);
    }
    private static void example(GameTestHelper h,BlockPos pos,String name) {
        var id=ResourceLocation.fromNamespaceAndPath("wwpg_api_example",name);h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Independent add-on missing: "+id);
        h.getLevel().setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id).defaultBlockState());
    }
    private static CompoundTag saved(GameTestHelper h,BlockPos pos) {
        var entity=h.getLevel().getBlockEntity(pos);h.assertTrue(entity!=null,"Pending fixture block entity missing at "+pos);
        return entity.saveWithoutMetadata(h.getLevel().registryAccess());
    }
    private static void assertEmptyHistory(GameTestHelper h,BlockPos pos) {
        var state=saved(h,pos);
        h.assertTrue(state.getDouble("StoredVoltage")==0&&state.getDouble("StoredCurrent")==0&&state.getLong("CommitCount")==0,"Fresh replacement inherited accepted storage/history before its first solve at "+pos+": "+state);
    }
    private static void checkHistory(GameTestHelper h,CompoundTag expected,CompoundTag actual) {
        for(var key:new String[]{"StoredVoltage","StoredCurrent","CommitCount","WWPGExampleStateVersion"})
            h.assertTrue(Objects.equals(expected.get(key),actual.get(key)),"ARM changed saved "+key+" before the first solve");
    }

    private static void assertUnreadyApiFrame(GameTestHelper h) {
        for(var pos:new BlockPos[]{SOURCE,LOAD,CEE_LOAD}) {
            h.assertTrue(org.cha0scollective.wwpg.extension.ApiCircuits.objectCount(h.getLevel(),pos)==0,"Startup created extension simulation objects before entity readiness at "+pos);
            var reading=ElectricalMeasurements.read(h.getLevel(),pos);
            h.assertTrue(reading.status()==MeasurementStatus.NOT_READY&&reading.nodes().isEmpty()&&reading.elements().isEmpty()&&reading.electricalState().isEmpty(),"Startup exposed accepted API state before readiness at "+pos);
        }
    }

    private static void assertDeserializedPgWiresDeferred(GameTestHelper h,ChunkPos chunk,Set<WorldNetworks.PartId> expected) {
        var wires=DelayedEntityLoads.heldEntities(h.getLevel(),chunk).stream()
                .filter(org.patryk3211.powergrid.electricity.wire.WireEntity.class::isInstance)
                .map(org.patryk3211.powergrid.electricity.wire.WireEntity.class::cast).toList();
        var ids=wires.stream().map(wire->(WorldNetworks.PartId)new WorldNetworks.SimpleId(wire.getUUID())).collect(java.util.stream.Collectors.toSet());
        h.assertTrue(ids.equals(expected),"Waiting for the exact saved PG wire entities in the held native IO inbox");
        h.assertTrue(wires.stream().allMatch(wire->wire.getWire()==null),"Entity deserialization bound a native PG graph before its server tick");
        h.assertTrue(!h.getLevel().areEntitiesLoaded(chunk.toLong()),"Deferred-wire proof requires an actually held native entity inbox");
    }
}
