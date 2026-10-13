package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.extension.ApiCircuits;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.function.Consumer;

/** Tick ownership must follow native topology without recreating add-on models. */
@GameTestHolder("wwpg_api_ownership")
@PrefixGameTestTemplate(false)
public final class ExtensionApiOwnershipGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void repeatedBindingPreservesProgressRejectionAndStoredEnergy(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,2,1));var cap=source.east(3);
        place(h,source,"source");place(h,cap,"capacitor");
        var held=new CompoundTag[1];var identity=new Object[1];
        h.startSequence().thenIdle(5).thenExecute(()->{
            WiringGameTests.connect(h,source,0,cap,0,true);
            WiringGameTests.connect(h,source,1,cap,1,false);
        }).thenIdle(25).thenExecute(()->{
            var level=h.getLevel();var network=new BlockWireEndpoint(cap,0).getNode(level).getNetwork();
            var circuits=ApiCircuits.get(level);
            h.assertTrue(ApiCircuits.step(level,network)==16,"Ownership fixture requires a complete sixteen-substep frame");
            held[0]=state(h,cap);identity[0]=circuits.elementIdentity(cap,"storage");
            h.assertTrue(held[0].getDouble("StoredVoltage")>1,"Rejection fixture requires charged storage");
            circuits.beginSolving();circuits.beginSolving();
            h.assertTrue(ApiCircuits.step(level,network)==16,"Repeated binding reset accepted substep progress");
            circuits.invalidate(network);circuits.beginSolving();circuits.finish();
            h.assertTrue(ElectricalMeasurements.read(level,cap).status()==MeasurementStatus.SOLVE_FAILED,"Repeated binding cleared network rejection");
            h.assertTrue(circuits.elementIdentity(cap,"storage")==identity[0],"Rebinding replaced capacitor ownership");
        }).thenIdle(1).thenExecute(()->{
            h.assertTrue(held[0].getDouble("StoredVoltage")==state(h,cap).getDouble("StoredVoltage"),"Rejected rebound frame committed extra stored energy");
        }).thenIdle(5).thenExecute(()->{
            var result=ElectricalMeasurements.read(h.getLevel(),cap);
            h.assertTrue(result.status()==MeasurementStatus.VALID,"Next prepared tick did not clear the previous rejection");
            h.assertTrue(state(h,cap).getDouble("StoredVoltage")>held[0].getDouble("StoredVoltage"),"Valid following ticks did not resume storage");
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=900)
    public static void ownershipRecoversAfterActualUnloadAndDelayedWireEntities(GameTestHelper h) {
        var source=new BlockPos(40000,64,40000);var capacitor=source.east(3);
        var background=h.absolutePos(new BlockPos(1,2,1));var load=background.east(3);
        var chunk=new ChunkPos(source);var saved=new CompoundTag[1];
        Consumer<ChunkEvent.Unload> capture=event->{
            if(event.getLevel()==h.getLevel() && event.getChunk().getPos().equals(chunk)) {
                var entity=((net.minecraft.world.level.chunk.LevelChunk)event.getChunk()).getBlockEntities().get(capacitor);
                if(entity!=null)saved[0]=entity.saveWithoutMetadata(h.getLevel().registryAccess());
            }
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class,capture);
        h.getLevel().setChunkForced(chunk.x,chunk.z,true);h.getLevel().getChunkAt(source);
        place(h,source,"source");place(h,capacitor,"capacitor");place(h,background,"source");place(h,load,"load");
        h.startSequence().thenWaitUntil(()->h.assertTrue(org.cha0scollective.wwpg.extension.ApiTerminals.canWire(h.getLevel(),source)
                &&org.cha0scollective.wwpg.extension.ApiTerminals.canWire(h.getLevel(),capacitor),"Waiting for initially ready API owners"))
                .thenExecute(()->{
                    for(var pair:new BlockPos[][]{{source,capacitor},{background,load}}) {
                        WiringGameTests.connect(h,pair[0],0,pair[1],0,true);
                        WiringGameTests.connect(h,pair[0],1,pair[1],1,false);
                    }
                }).thenIdle(30).thenExecute(()->{
                    powered(h,load);
                    h.assertTrue(state(h,capacitor).getDouble("StoredVoltage")>1,"Unload fixture must start with charged storage");
                    h.getLevel().getDataStorage().save();h.getLevel().getChunkSource().save(true);
                    h.getLevel().setChunkForced(chunk.x,chunk.z,false);
                }).thenIdle(330).thenWaitUntil(()->{
                    h.getLevel().getChunkSource().tick(()->true,true);
                    h.assertTrue(saved[0]!=null && h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"Waiting for actual add-on chunk unload");
                }).thenExecute(()->{
                    powered(h,load);
                    h.assertTrue(ElectricalMeasurements.read(h.getLevel(),capacitor).status()==MeasurementStatus.NOT_READY,"Unloaded API owner retained an available sample");
                    DelayedEntityLoads.hold(h.getLevel(),chunk,60);
                    h.getLevel().setChunkForced(chunk.x,chunk.z,true);h.getLevel().getChunkAt(source);
                    unchangedStorage(h,saved[0],state(h,capacitor));
                }).thenIdle(20).thenExecute(()->{
                    powered(h,load);
                    h.assertTrue(!h.getLevel().areEntitiesLoaded(chunk.toLong()),"Fixture did not delay real saved wire entities");
                    unchangedStorage(h,saved[0],state(h,capacitor));
                    h.assertTrue(ElectricalMeasurements.read(h.getLevel(),capacitor).status()==MeasurementStatus.NOT_READY,"Paused API storage exposed stale measurements");
                }).thenWaitUntil(()->h.assertTrue(h.getLevel().areEntitiesLoaded(chunk.toLong()),"Waiting for delayed saved wires"))
                .thenIdle(10).thenExecute(()->{
                    powered(h,load);
                    var result=ElectricalMeasurements.read(h.getLevel(),capacitor);
                    h.assertTrue(result.status()==MeasurementStatus.VALID,"Reloaded ownership did not receive every accepted sample: "+result);
                    h.assertTrue(state(h,capacitor).getDouble("StoredVoltage")+1e-9>=saved[0].getDouble("StoredVoltage"),"Reload lost capacitor state");
                    NeoForge.EVENT_BUS.unregister(capture);h.getLevel().setChunkForced(chunk.x,chunk.z,false);
                    DynamicGameTests.audit(h);
                }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=160)
    public static void modelSamplesFollowMergeSplitAndSourceReplacement(GameTestHelper h) {
        var a=h.absolutePos(new BlockPos(1,2,1));
        var b=h.absolutePos(new BlockPos(1,2,5));
        var la=a.east(3);var lb=b.east(3);
        for(var p:new BlockPos[]{a,b})place(h,p,"source");
        for(var p:new BlockPos[]{la,lb})place(h,p,"load");
        var identities=new Object[2];
        h.startSequence().thenIdle(5).thenExecute(()->{
            for(var pair:new BlockPos[][]{{a,la},{b,lb}}) {
                WiringGameTests.connect(h,pair[0],0,pair[1],0,true);
                WiringGameTests.connect(h,pair[0],1,pair[1],1,false);
            }
        }).thenIdle(25).thenExecute(()->{
            powered(h,la);powered(h,lb);
            stableTerminalPaths(h,la);stableTerminalPaths(h,lb);
            h.assertTrue(network(h,la)!=network(h,lb),"Independent add-on circuits unexpectedly shared ownership");
            identities[0]=ApiCircuits.get(h.getLevel()).elementIdentity(la,"load_a");
            identities[1]=ApiCircuits.get(h.getLevel()).elementIdentity(lb,"load_a");
            WiringGameTests.connect(h,la,1,lb,1,true);
        }).thenIdle(15).thenExecute(()->{
            powered(h,la);powered(h,lb);
            h.assertTrue(network(h,la)==network(h,lb),"Native merge did not migrate API ownership");
            sameModels(h,la,lb,identities);
            var shared=network(h,la);
            var endpoint=new BlockWireEndpoint(la,1);
            var behaviour=IElectric.getAt(h.getLevel(),la).getBehaviour(h.getLevel(),la,h.getLevel().getBlockState(la));
            var joining=behaviour.getConnections().get(endpoint).stream().filter(wire->wire.isConnectedTo(lb,1)).findFirst().orElseThrow();
            var part=new WorldNetworks.SimpleId(joining.getUUID());joining.kill();
            var world=GlobalElectricNetworks.getWorldNetworks(h.getLevel());
            h.assertTrue(world.getPart(part)==null && behaviour.getConnections().getOrDefault(endpoint,java.util.Set.of()).stream()
                    .noneMatch(wire->wire.isConnectedTo(lb,1)),"Native join wire was not physically removed");
            // PG permits disconnected circuits to remain in one solver. Ask
            // its native discovery mechanism to repartition that solver so
            // this fixture specifically exercises API ownership migration.
            world.scheduleIslandDiscovery(shared);
        }).thenIdle(15).thenExecute(()->{
            powered(h,la);powered(h,lb);
            h.assertTrue(network(h,la)!=network(h,lb),"Native split retained stale API ownership");
            sameModels(h,la,lb,identities);
            h.getLevel().setBlockAndUpdate(a,Blocks.AIR.defaultBlockState());
        }).thenIdle(10).thenExecute(()->{
            powered(h,lb);
            var result=ElectricalMeasurements.read(h.getLevel(),la);
            if(result.status()==MeasurementStatus.VALID)
                h.assertTrue(Math.abs(result.element("load_a").orElseThrow().meanCurrentAmps())<.0001,"Removed source left stale power on its former API network");
            place(h,a,"source");
        }).thenIdle(5).thenExecute(()->{
            WiringGameTests.connect(h,a,0,la,0,true);
            WiringGameTests.connect(h,a,1,la,1,false);
        }).thenIdle(20).thenExecute(()->{
            powered(h,la);powered(h,lb);
            stableTerminalPaths(h,la);stableTerminalPaths(h,lb);
            sameModels(h,la,lb,identities);
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    private static void place(GameTestHelper h,BlockPos pos,String kind) {
        var id=ResourceLocation.fromNamespaceAndPath("wwpg_api_example",kind);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Independent public API example is required: "+id);
        h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id).defaultBlockState());
    }
    private static ElectricalNetwork network(GameTestHelper h,BlockPos load) { return new BlockWireEndpoint(load,0).getNode(h.getLevel()).getNetwork(); }
    private static void powered(GameTestHelper h,BlockPos load) {
        var result=ElectricalMeasurements.read(h.getLevel(),load);
        h.assertTrue(result.status()==MeasurementStatus.VALID,"Incomplete API network frame: "+result.status()+" "+result.detail());
        var branch=result.element("load_a").orElseThrow();
        h.assertTrue(Math.abs(branch.meanVoltageVolts()-20)<.02 && Math.abs(branch.meanCurrentAmps()-.2)<.0003,
                "API ownership lost signed voltage/current samples: "+branch);
    }
    private static void sameModels(GameTestHelper h,BlockPos a,BlockPos b,Object[] identities) {
        h.assertTrue(identities[0]==ApiCircuits.get(h.getLevel()).elementIdentity(a,"load_a")
                && identities[1]==ApiCircuits.get(h.getLevel()).elementIdentity(b,"load_a"),"Topology reassignment recreated unchanged add-on models");
    }
    private static CompoundTag state(GameTestHelper h,BlockPos pos) {
        var entity=h.getLevel().getBlockEntity(pos);h.assertTrue(entity!=null,"Missing saved add-on entity");
        return entity.saveWithoutMetadata(h.getLevel().registryAccess());
    }
    private static void unchangedStorage(GameTestHelper h,CompoundTag expected,CompoundTag actual) {
        h.assertTrue(expected.getDouble("StoredVoltage")==actual.getDouble("StoredVoltage")
                &&expected.getLong("CommitCount")==actual.getLong("CommitCount"),"Unready restored ownership advanced saved storage or gameplay");
    }
    private static void stableTerminalPaths(GameTestHelper h,BlockPos pos) {
        var level=h.getLevel();var adapter=org.cha0scollective.wwpg.extension.ApiTerminals.existing(level,pos);
        h.assertTrue(adapter!=null,"Missing prepared terminal owner");
        var pg=org.cha0scollective.wwpg.extension.ApiTerminals.savedPgWireIdentities(level,pos);
        var cee=org.cha0scollective.wwpg.extension.ApiCeeWireIdentities.get(level).idsAt(pos);
        h.assertTrue(!pg.isEmpty()&&!cee.isEmpty(),"Terminal path fixture requires saved wires from both systems");
        int nodes=GlobalElectricNetworks.getWorldNetworks(level).globalExternalNodes.size();
        var before=state(h,pos);
        for(int pass=0;pass<5;pass++)for(int terminal=0;terminal<2;terminal++)
            h.assertTrue(adapter.preparedTerminal(terminal)==adapter.behaviour().getTerminal(terminal),"Prepared and native lookups resolved different terminal identities");
        h.assertTrue(adapter.preparedTerminal(4095)==null&&adapter.behaviour().getTerminal(4095)==null,"Unknown terminal lookup allocated geometry");
        h.assertTrue(nodes==GlobalElectricNetworks.getWorldNetworks(level).globalExternalNodes.size()&&before.equals(state(h,pos)),"Terminal resolution advanced gameplay or created duplicate nodes");
        h.assertTrue(pg.equals(org.cha0scollective.wwpg.extension.ApiTerminals.savedPgWireIdentities(level,pos))
                &&cee.equals(org.cha0scollective.wwpg.extension.ApiCeeWireIdentities.get(level).idsAt(pos)),"Prepared/native terminal queries changed saved wire ownership");
    }
}
