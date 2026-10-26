package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.bundled_wire.*;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.electricity.*;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("wwpg_bundle")
@PrefixGameTestTemplate(false)
public final class BundledWireLifecycleGameTests {
    private static final BlockPos SOURCE=new BlockPos(96000,64,48000),FIRST=SOURCE.east(3),SECOND=FIRST.east(4),LAMP=SECOND.east(3);
    private static final Path SNAPSHOT=Path.of("duplex-before-restart.nbt");
    private static final InWorldNodeConnection CABLE=new InWorldNodeConnection(new InWorldNode(0x1010,FIRST),new InWorldNode(0x1020,SECOND));

    @GameTest(template="empty",timeoutTicks=3000)
    public static void duplexAndLvBulbRetainNativeStateAcrossFiveActualCyclesAndColdRestart(GameTestHelper h)throws java.io.IOException {
        var level=h.getLevel();boolean restore=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        var expected=restore?NbtIo.readCompressed(SNAPSHOT,NbtAccounter.unlimitedHeap()):null;
        var cold=restore?disk(level,LAMP):null;
        force(level,true);
        if(!restore) {
            for(var p:new BlockPos[]{SOURCE,FIRST,SECOND,LAMP})level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(SOURCE,CEEBlocks.CREATIVE_BATTERY.getDefaultState());level.setBlockAndUpdate(LAMP,ModdedBlocks.LIGHT_FIXTURE.getDefaultState());
            level.setBlockAndUpdate(FIRST,CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState().setValue(BundledWireTerminationBlock.FACING,Direction.UP)
                    .setValue(BundledWireTerminationBlock.ROLL,true).setValue(BundledWireTerminationBlock.FLIP,false));
            level.setBlockAndUpdate(SECOND,CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState().setValue(BundledWireTerminationBlock.FACING,Direction.NORTH)
                    .setValue(BundledWireTerminationBlock.ROLL,false).setValue(BundledWireTerminationBlock.FLIP,true));
        } else {saved(h,cold,"Cold restart");h.assertTrue(cableTag(level).equals(expected.getCompound("Cable")),"Cold restart changed native cable identity, length or type before first solve");}
        var unloads=new AtomicInteger();Consumer<ChunkEvent.Unload> listener=e->{if(e.getLevel()==level&&e.getChunk().getPos().equals(new ChunkPos(SOURCE)))unloads.incrementAndGet();};
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class,listener);
        var wireIds=new HashSet<WorldNetworks.PartId>();
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(SOURCE)&&level.areEntitiesLoaded(new ChunkPos(SOURCE).toLong()),"Waiting for the durable mixed cable assembly"))
                .thenExecute(()->{
                    if(restore)return;
                    ShowroomTools.scroll(h,SOURCE,12000);
                    var player=h.makeMockPlayer(GameType.SURVIVAL);var bulb=ModdedItems.LV_LIGHT_BULB.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,bulb);player.setItemInHand(InteractionHand.OFF_HAND,Items.RED_DYE.getDefaultInstance());
                    h.assertTrue(lamp(level).replaceBulb(player,InteractionHand.MAIN_HAND,bulb)&&bulb.isEmpty(),"Native LV bulb was not installed");
                    WiringGameTests.connect(h,SOURCE,1,FIRST,0x1011,true);WiringGameTests.connect(h,SOURCE,0,FIRST,0x1012,false);
                    WiringGameTests.connect(h,SECOND,0x1021,LAMP,0,false);WiringGameTests.connect(h,SECOND,0x1022,LAMP,1,true);
                    var spool=CEEItems.DUPLEX_WIRE_SPOOL.asStack();player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                    BundledWireGameTests.click(h,player,spool,FIRST);BundledWireGameTests.click(h,player,spool,SECOND);
                    h.assertTrue(spool.isEmpty(),"Native duplex spool was not consumed");
                }).thenWaitUntil(()->powered(h)).thenExecute(()->{
                    wireIds.addAll(ids(level));h.assertTrue(wireIds.size()==2,"Expected two unchanged native PG wire owners");
                    if(restore)h.assertTrue(wireIds.stream().map(Object::toString).sorted().toList().equals(expected.getList("WireIds",8).stream().map(v->v.getAsString()).sorted().toList()),"Cold restart changed native wire identities");
                });
        for(int cycle=1;cycle<=5;cycle++) {
            int index=cycle;var before=new CompoundTag[]{null};
            sequence.thenExecute(()->{level.getDataStorage().save();level.getChunkSource().save(true);force(level,false);})
                    .thenIdle(330).thenWaitUntil(()->{
                        level.getChunkSource().tick(()->true,true);var chunk=new ChunkPos(SOURCE);
                        h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&unloads.get()>=index,"Waiting for actual cable-assembly unload "+index);
                    }).thenExecute(()->{
                        before[0]=disk(level,LAMP);
                        for(int x=(SOURCE.getX()>>4)-1;x<=(SOURCE.getX()>>4)+1;x++)for(int z=(SOURCE.getZ()>>4)-1;z<=(SOURCE.getZ()>>4)+1;z++)DelayedEntityLoads.hold(level,new ChunkPos(x,z),60);
                        force(level,true);saved(h,before[0],"Actual cycle "+index+" before first solve");
                        h.assertTrue(InfrastructureSavedData.load(level).isConnected(CABLE.node1(),CABLE.node2()),"Actual unload removed native cable");
                    }).thenIdle(20).thenExecute(()->h.assertTrue(!level.areEntitiesLoaded(new ChunkPos(SOURCE).toLong()),"Delayed native wire entities were not exercised"))
                    .thenWaitUntil(()->{h.assertTrue(level.areEntitiesLoaded(new ChunkPos(SOURCE).toLong()),"Waiting for native saved wires");powered(h);})
                    .thenExecute(()->{h.assertTrue(ids(level).equals(wireIds),"Cable cycle replaced or duplicated native PG wire identities");WorldWidePowerGrid.LOGGER.info("DUPLEX_CYCLE: cycle={} actualUnloads={} presolveBulb={} cable={}",index,unloads.get(),before[0].getString("Bulb"),cableTag(level));});
        }
        sequence.thenExecute(()->{
            var report=new CompoundTag();report.put("Cable",cableTag(level));var ids=new ListTag();wireIds.stream().map(Object::toString).sorted().forEach(s->ids.add(StringTag.valueOf(s)));report.put("WireIds",ids);
            try{NbtIo.writeCompressed(report,SNAPSHOT);}catch(java.io.IOException e){throw new IllegalStateException(e);}
            level.getDataStorage().save();level.getChunkSource().save(true);NeoForge.EVENT_BUS.unregister(listener);force(level,false);DynamicGameTests.audit(h);
        }).thenSucceed();
    }
    private static void powered(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(lamp(level).getBulbState()!=null&&lamp(level).getBulbState().getItem()==ModdedItems.LV_LIGHT_BULB.get()&&lamp(level).getBulbState().getColor()==DyeColor.RED&&lamp(level).getPowerLevel()==1,"Duplex-fed native LV bulb lost identity, dye or light");
        BoardComponentGameTests.near(h,StationaryNetherGameTests.voltage(level,LAMP),12,.02,"Duplex supply and return voltage");
    }
    private static void saved(GameTestHelper h,CompoundTag expected,String label) {
        var actual=new CompoundTag();lamp(h.getLevel()).getBulbState().write(actual);
        for(String key:new String[]{"Bulb","Temperature","Burned","Color"})h.assertTrue(Objects.equals(actual.get(key),expected.get(key)),label+" changed native bulb "+key+": "+actual.get(key)+" != "+expected.get(key));
    }
    private static AbstractLightFixtureBlockEntity lamp(ServerLevel level){return (AbstractLightFixtureBlockEntity)level.getBlockEntity(LAMP);}
    private static CompoundTag cableTag(ServerLevel level) {
        var data=InfrastructureSavedData.load(level).getConnectionData(CABLE);if(data==null)throw new GameTestAssertException("Native saved duplex cable is missing");
        var out=new CompoundTag();out.putString("Type",CEERegistries.WIRE_TYPE.getKey(data.wireType()).toString());out.putDouble("Length",data.length);return out;
    }
    private static Set<WorldNetworks.PartId> ids(ServerLevel level) {
        var out=new HashSet<WorldNetworks.PartId>();var world=GlobalElectricNetworks.getWorldNetworks(level);
        for(var pos:new BlockPos[]{SOURCE,LAMP}){var parts=world.findConnectedWires(new BlockWireEndpoint(pos,1));if(parts!=null)for(var p:parts)out.add(p.persistentOwnerId);}return out;
    }
    private static CompoundTag disk(ServerLevel level,BlockPos pos) {
        var chunk=level.getChunkSource().chunkMap.read(new ChunkPos(pos)).join().orElseThrow();
        for(var tag:chunk.getList("block_entities",10)){var be=(CompoundTag)tag;if(be.getInt("x")==pos.getX()&&be.getInt("y")==pos.getY()&&be.getInt("z")==pos.getZ())return be;}
        throw new IllegalStateException("Missing native saved lamp at "+pos);
    }
    private static void force(ServerLevel level,boolean value){StationaryNetherGameTests.force(level,SOURCE,value);}
}
