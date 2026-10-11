package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Real disk/chunk acceptance of independently compiled add-on state and native wires. */
@GameTestHolder("wwpg_api")
@PrefixGameTestTemplate(false)
public final class ExtensionApiLifecycleGameTests {
    private static final BlockPos SOURCE=new BlockPos(8208,64,8208),CAP=SOURCE.east(3),COIL=SOURCE.east(6);
    private static final Path EXPECTED=Path.of("extension-api-before-restart.nbt");

    @GameTest(template="empty",timeoutTicks=260)
    public static void addonSavedStateAndBothWireSystemsSurviveColdRestart(GameTestHelper h) throws IOException {
        var level=h.getLevel();var chunk=new ChunkPos(SOURCE);
        var accepted=new AtomicBoolean();
        Consumer<ServerStoppingEvent> stopping=event->{
            if(event.getServer()!=level.getServer()||!accepted.get())return;
            var expected=new CompoundTag();expected.put("Capacitor",saved(h,CAP));expected.put("Inductor",saved(h,COIL));
            expected.putDouble("StoredVoltage",saved(h,CAP).getDouble("StoredVoltage"));
            try { NbtIo.writeCompressed(expected,EXPECTED); } catch(IOException e){throw new IllegalStateException(e);}
        };
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,stopping);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(SOURCE);
        boolean verify=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        if(!verify) {
            for(var pos:new BlockPos[]{SOURCE,CAP,COIL}) {
                level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            }
            example(h,SOURCE,"source");example(h,CAP,"capacitor");example(h,COIL,"inductor");
        } else {
            var stored=NbtIo.readCompressed(EXPECTED,NbtAccounter.unlimitedHeap());
            checkHistory(h,stored.getCompound("Capacitor"),saved(h,CAP),"Cold capacitor before first solve");
            checkHistory(h,stored.getCompound("Inductor"),saved(h,COIL),"Cold inductor before first solve");
            h.assertTrue(stored.getDouble("StoredVoltage")>1,"Cold restart reference must contain charged storage");
        }
        h.startSequence().thenIdle(5).thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for saved API wire entities"))
                .thenExecute(()-> {
                    if(verify)return;
                    WiringGameTests.connect(h,SOURCE,0,CAP,0,true);WiringGameTests.connect(h,SOURCE,1,CAP,1,true);
                    WiringGameTests.connect(h,SOURCE,0,COIL,0,false);WiringGameTests.connect(h,SOURCE,1,COIL,1,false);
                }).thenIdle(35).thenExecute(()-> {
                    var cap=ElectricalMeasurements.read(level,CAP);var coil=ElectricalMeasurements.read(level,COIL);
                    h.assertTrue(cap.status()==MeasurementStatus.VALID&&coil.status()==MeasurementStatus.VALID,"Saved API circuits did not recover valid power");
                    double v=saved(h,CAP).getDouble("StoredVoltage");
                    h.assertTrue(v>1&&v<=20.001,"Capacitor did not charge after native wire restore: "+v);
                    near(h,saved(h,COIL).getDouble("StoredCurrent"),.2,.0003,"Saved coil restored its native mixed circuit");
                    var before=saved(h,CAP);
                    int nodesBefore=GlobalElectricNetworks.getWorldNetworks(level).globalExternalNodes.size();
                    for(int i=0;i<20;i++) {
                        ElectricalMeasurements.read(level,CAP);
                        ElectricalMeasurements.readTerminal(level,CAP,0);
                        ElectricalMeasurements.readVoltageBetween(level,CAP,0,CAP,1);
                        ElectricalMeasurements.readTerminal(level,CAP,4095);
                    }
                    h.assertTrue(before.equals(saved(h,CAP)),"Read-only queries advanced or changed saved add-on state");
                    h.assertTrue(nodesBefore==GlobalElectricNetworks.getWorldNetworks(level).globalExternalNodes.size(),"Read-only queries created electrical nodes");
                    accepted.set(true);
                    level.getDataStorage().save();level.getChunkSource().save(true);DynamicGameTests.audit(h);
                }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=3000)
    public static void addonStateAndSavedWiresSurviveFiveActualDelayedChunkCycles(GameTestHelper h) {
        // Stay outside every concurrently forced fixture's propagated ticket
        // radius. A non-ticking level39 ticket still retains a chunk holder.
        var level=h.getLevel();var source=new BlockPos(10240,64,10240);var cap=source.east(3);var coil=source.east(6);
        var chunk=new ChunkPos(source);var unloads=new AtomicInteger();
        var expected=new CompoundTag[2];
        Consumer<ChunkEvent.Unload> listener=event->{
            if(event.getLevel()==level&&event.getChunk().getPos().equals(chunk)) {
                var loaded=(net.minecraft.world.level.chunk.LevelChunk)event.getChunk();
                expected[0]=loaded.getBlockEntities().get(cap).saveWithoutMetadata(level.registryAccess());
                expected[1]=loaded.getBlockEntities().get(coil).saveWithoutMetadata(level.registryAccess());
                unloads.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class,listener);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(source);
        for(var pos:new BlockPos[]{source,cap,coil}) {
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        }
        example(h,source,"source");example(h,cap,"capacitor");example(h,coil,"inductor");
        var ids=new HashSet<String>();
        var sequence=h.startSequence().thenIdle(5).thenExecute(()-> {
            WiringGameTests.connect(h,source,0,cap,0,true);WiringGameTests.connect(h,source,1,cap,1,true);
            WiringGameTests.connect(h,source,0,coil,0,false);WiringGameTests.connect(h,source,1,coil,1,false);
        }).thenIdle(30).thenExecute(()-> {
            h.assertTrue(saved(h,cap).getDouble("StoredVoltage")>1,"Chunk cycle requires charged storage");
            ids.addAll(wireIds(h,source));h.assertTrue(ids.size()==2,"Expected two PG wire identities before unload");
        });
        for(int cycle=1;cycle<=5;cycle++) {
            int expectedUnloads=cycle;
            var waitTicks=new AtomicInteger();
            h.startSequence().thenWaitUntil(()->h.assertTrue(waitTicks.get()>=90,"Waiting for API unload deadline"))
                    .thenFail(()->new GameTestAssertException("API chunk failed to unload after 420 ticks: "+level.getChunkSource().getChunkDebugData(chunk)));
            sequence.thenExecute(()-> {
                expected[0]=saved(h,cap);expected[1]=saved(h,coil);
                level.getDataStorage().save();level.getChunkSource().save(true);level.setChunkForced(chunk.x,chunk.z,false);
            }).thenIdle(330).thenWaitUntil(()-> {
                level.getChunkSource().tick(()->true,true);
                boolean unloaded=level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&unloads.get()>=expectedUnloads;
                int elapsed=waitTicks.incrementAndGet();
                String diagnostic=level.getChunkSource().getChunkDebugData(chunk)+", ticking="+level.getChunkSource().isPositionTicking(chunk.toLong())
                        +", entitiesLoaded="+level.areEntitiesLoaded(chunk.toLong())+", actualUnloads="+unloads.get();
                if(!unloaded&&(elapsed==1||elapsed%50==0))org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_CHUNK_WAIT: cycle={}, waitTicks={}, {}",expectedUnloads,elapsed,diagnostic);
                h.assertTrue(unloaded,"Waiting for actual API chunk unload "+expectedUnloads+": "+diagnostic);
            }).thenExecute(()-> {
                h.assertTrue(ElectricalMeasurements.read(level,cap).status()==MeasurementStatus.NOT_READY,"Unloaded add-on returned stale power");
                DelayedEntityLoads.hold(level,chunk,60);level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(source);
                checkHistory(h,expected[0],saved(h,cap),"Reloaded capacitor before first solve");
                checkHistory(h,expected[1],saved(h,coil),"Reloaded inductor before first solve");
            }).thenIdle(20).thenExecute(()-> {
                h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"API cycle did not exercise delayed native wire entity loading");
                checkHistory(h,expected[0],saved(h,cap),"Capacitor while wire entities unavailable");
            }).thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for API wire readiness"))
                    .thenIdle(5).thenExecute(()-> {
                h.assertTrue(ElectricalMeasurements.read(level,cap).status()==MeasurementStatus.VALID,"API capacitor did not resume after actual reload");
                near(h,saved(h,coil).getDouble("StoredCurrent"),.2,.0003,"Reloaded inductor");
                h.assertTrue(ids.equals(wireIds(h,source)),"API reload duplicated or changed native wire identities");
                org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_CHUNK_CYCLE: cycle={}, actualUnloads={}, preSolveStateVerified=true",expectedUnloads,unloads.get());
            });
        }
        sequence.thenExecute(()-> {NeoForge.EVENT_BUS.unregister(listener);level.setChunkForced(chunk.x,chunk.z,false);DynamicGameTests.audit(h);}).thenSucceed();
    }
    private static Set<String> wireIds(GameTestHelper h,BlockPos source) {
        var world=GlobalElectricNetworks.getWorldNetworks(h.getLevel());var ids=new HashSet<String>();
        for(int port=0;port<2;port++)for(var part:world.findConnectedWires(new BlockWireEndpoint(source,port)))ids.add(part.persistentOwnerId.toString());
        return ids;
    }

    @GameTest(template="empty",timeoutTicks=900)
    public static void diagnosticFreshSidecarReplacementDuringDelayedWireReloadCutsOnlySavedApiWires(GameTestHelper h) {
        var level=h.getLevel();var source=new BlockPos(12000,64,12000);var load=source.east(3);var chunk=new ChunkPos(source);
        var unloads=new AtomicInteger();
        Consumer<ChunkEvent.Unload> listener=event->{if(event.getLevel()==level&&event.getChunk().getPos().equals(chunk))unloads.incrementAndGet();};
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class,listener);
        level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(source);
        for(var pos:new BlockPos[]{source,load}) {
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        }
        example(h,source,"source");example(h,load,"capacitor");
        var oldIds=new java.util.HashSet<org.patryk3211.powergrid.electricity.WorldNetworks.PartId>();
        h.startSequence().thenIdle(5).thenExecute(()-> {
            WiringGameTests.connect(h,source,0,load,0,true);WiringGameTests.connect(h,source,1,load,1,true);
        }).thenIdle(30).thenExecute(()-> {
            h.assertTrue(saved(h,load).getDouble("StoredVoltage")>1,"Partial replacement needs real charged saved storage");
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            for(int port=0;port<2;port++)for(var part:world.findConnectedWires(new BlockWireEndpoint(load,port)))oldIds.add(part.persistentOwnerId);
            h.assertTrue(oldIds.size()==2,"Partial replacement needs two saved native wires");
            level.getDataStorage().save();level.getChunkSource().save(true);level.setChunkForced(chunk.x,chunk.z,false);
        }).thenIdle(330).thenWaitUntil(()-> {
            level.getChunkSource().tick(()->true,true);
            h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&unloads.get()==1,"Waiting for actual partial-replacement unload: "+level.getChunkSource().getChunkDebugData(chunk));
        }).thenExecute(()-> {
            // Reproduce cold startup's fresh sidecar cache without replacing the
            // shared world simulator or affecting another acceptance fixture.
            forgetTerminalGeometry(level,load);
            DelayedEntityLoads.hold(level,chunk,100);level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(source);
            h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Partial replacement did not delay real saved wire entities");
            h.assertTrue(saved(h,load).getDouble("StoredVoltage")>1,"Partial replacement lost storage before its first solve");
            h.assertTrue(org.cha0scollective.wwpg.extension.ApiTerminals.get(level,load)!=null,"Fresh API terminal geometry was not discovered");
            new BlockWireEndpoint(load,0).getNode(level);
            level.setBlockAndUpdate(load,org.patryk3211.powergrid.collections.ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
            h.assertTrue(ElectricalMeasurements.read(level,load).status()==MeasurementStatus.NOT_READY,"Native replacement inherited API results before the first solve");
        }).thenIdle(5).thenExecute(()-> {
            h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Native replacement missed the partial-load window");
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            world.addAndMigrateNode(new BlockWireEndpoint(load,0));world.addAndMigrateNode(new BlockWireEndpoint(load,1));
        }).thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for partial-replacement entity readiness"))
                .thenIdle(2).thenExecute(()-> {
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            for(var id:oldIds) {
                h.assertTrue(world.getPart(id)==null,"Old saved API wire migrated into native replacement: "+id);
                h.assertTrue(id instanceof org.patryk3211.powergrid.electricity.WorldNetworks.SimpleId simple
                        &&org.cha0scollective.wwpg.wiring.RemovedWires.get(level).contains(simple.id()),"Late saved wire entity lacks a deletion identity: "+id);
            }
            ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            WiringGameTests.connect(h,source,0,load,0,true);WiringGameTests.connect(h,source,1,load,1,true);
        }).thenIdle(8).thenExecute(()-> {
            var voltage=ElectricalMeasurements.readVoltageBetween(level,load,0,load,1);
            h.assertTrue(voltage.status()==MeasurementStatus.VALID,"Fresh native replacement did not resume valid power: "+voltage.detail());
            near(h,voltage.voltage().orElseThrow().lastVoltageVolts(),20,.03,"Native replacement after fresh wiring");
            h.assertTrue(wireIds(h,source).size()==2,"Native replacement lost fresh incoming wires");
            h.assertTrue(ElectricalMeasurements.read(level,load).status()==MeasurementStatus.NOT_READY,"Native replacement acquired duplicate API models");
            NeoForge.EVENT_BUS.unregister(listener);level.setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(load,Blocks.AIR.defaultBlockState());
            level.setChunkForced(chunk.x,chunk.z,false);DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    @SuppressWarnings("unchecked")
    private static void forgetTerminalGeometry(net.minecraft.server.level.ServerLevel level,BlockPos pos) {
        try {
            var field=org.cha0scollective.wwpg.extension.ApiTerminals.class.getDeclaredField("HOLDERS");field.setAccessible(true);
            var worlds=(java.util.Map<net.minecraft.world.level.Level,java.util.Map<BlockPos,?>>)field.get(null);
            var holders=worlds.get(level);if(holders!=null)holders.remove(pos);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Cannot reproduce a scoped cold terminal discovery",error);}
    }
    private static void example(GameTestHelper h,BlockPos pos,String name) {
        var id=ResourceLocation.fromNamespaceAndPath("wwpg_api_example",name);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Independent API add-on missing: "+id);
        h.getLevel().setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id).defaultBlockState());
    }
    private static CompoundTag saved(GameTestHelper h,BlockPos pos) {
        var entity=h.getLevel().getBlockEntity(pos);h.assertTrue(entity!=null,"Saved add-on block entity missing at "+pos);
        return entity.saveWithoutMetadata(h.getLevel().registryAccess());
    }
    private static void checkHistory(GameTestHelper h,CompoundTag expected,CompoundTag actual,String message) {
        for(var key:new String[]{"StoredVoltage","StoredCurrent","CommitCount","WWPGExampleStateVersion"})
            h.assertTrue(java.util.Objects.equals(expected.get(key),actual.get(key)),message+" changed "+key+": "+expected.get(key)+" -> "+actual.get(key));
    }
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String message) {
        h.assertTrue(Double.isFinite(value)&&Math.abs(value-expected)<=tolerance,message+": "+value+" expected "+expected);
    }
}
