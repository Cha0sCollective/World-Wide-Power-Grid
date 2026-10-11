package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.fuse.FuseHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Exercises the four entries in CEE's native FuseHoldable.ALL map. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class FuseHolderWorkflowGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,2,1), HOLDER=new BlockPos(4,2,1), LOAD=new BlockPos(7,2,1);

    @GameTest(template="empty",timeoutTicks=160)
    public static void nativeCopperBypassReplacementAndHeldSwitchOperateMixedLoad(GameTestHelper h) {
        place(h);
        Set<WorldNetworks.PartId> wires=new HashSet<>();
        h.runAtTickTime(5,()->{
            setup(h,20,1000);install(h,h.absolutePos(HOLDER),true,bypass());wire(h);
        });
        h.runAtTickTime(25,()->{
            near(h,voltage(h),20*1000/1000.1,.03,"Native copper bypass");wires.addAll(ids(h.getLevel(),h.absolutePos(HOLDER)));
            var player=use(h,h.absolutePos(HOLDER),true,ItemStack.EMPTY);
            h.assertTrue(!tag(h).contains("FirstID")&&player.getInventory().items.stream().anyMatch(s->s.is(CEETags.FUSE_BYPASS_ITEM)),"Native bypass removal did not return its material");
            install(h,h.absolutePos(HOLDER),true,CEEBlocks.CUT_OFF_SWITCH.asStack());
        });
        h.runAtTickTime(45,()->{near(h,voltage(h),0,.001,"Held switch defaults open");use(h,h.absolutePos(HOLDER),true,ItemStack.EMPTY);});
        h.runAtTickTime(65,()->{near(h,voltage(h),20,.03,"Held switch closes");h.assertTrue(tag(h).getCompound("FirstData").getBoolean("Closed"),"Native closed state missing");roundTrip(h);});
        h.runAtTickTime(85,()->{near(h,voltage(h),20,.03,"Held switch native serialization");use(h,h.absolutePos(HOLDER),true,ItemStack.EMPTY);});
        h.runAtTickTime(110,()->{
            near(h,voltage(h),0,.001,"Held switch opens");
            var wrench=CEETags.itemFromTag(CEETags.FUSE_WRENCH).getDefaultInstance();h.assertTrue(!wrench.isEmpty(),"Native fuse wrench tag is empty");
            use(h,h.absolutePos(HOLDER),true,wrench);h.assertTrue(!tag(h).contains("FirstID"),"Native wrench did not remove held switch");
            h.assertTrue(wires.equals(ids(h.getLevel(),h.absolutePos(HOLDER))),"Changing inserts changed native wire identities");DynamicGameTests.audit(h);h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=170)
    public static void nativeHeldIndicatorColorBrightnessAndIndependentSlotsSurviveSerialization(GameTestHelper h) {
        place(h);
        h.runAtTickTime(5,()->{
            setup(h,70,1000);install(h,h.absolutePos(HOLDER),true,CEEBlocks.INDICATOR_BULB.asStack());
            install(h,h.absolutePos(HOLDER),false,CEEBlocks.CUT_OFF_SWITCH.asStack());
            use(h,h.absolutePos(HOLDER),true,Items.LIME_DYE.getDefaultInstance());
            WiringGameTests.connect(h,h.absolutePos(SOURCE),0,h.absolutePos(HOLDER),1,true);
            WiringGameTests.connect(h,h.absolutePos(SOURCE),1,h.absolutePos(HOLDER),3,false);
            WiringGameTests.connect(h,h.absolutePos(SOURCE),0,h.absolutePos(HOLDER),0,false);
            WiringGameTests.connect(h,h.absolutePos(HOLDER),2,h.absolutePos(LOAD),0,true);
            WiringGameTests.connect(h,h.absolutePos(SOURCE),1,h.absolutePos(LOAD),1,true);
        });
        h.runAtTickTime(30,()->{indicator(h,1);near(h,voltage(h),0,.001,"Other native slot remains open");use(h,h.absolutePos(HOLDER),false,ItemStack.EMPTY);});
        h.runAtTickTime(55,()->{indicator(h,1);near(h,voltage(h),70,.03,"Second slot closes independently");roundTrip(h);ShowroomTools.pgSource(h,h.absolutePos(SOURCE),35);});
        h.runAtTickTime(80,()->{indicator(h,.5);near(h,voltage(h),35,.03,"Independent slot survived native serialization");ShowroomTools.pgSource(h,h.absolutePos(SOURCE),0);});
        h.runAtTickTime(110,()->{
            indicator(h,0);use(h,h.absolutePos(HOLDER),true,CEETags.itemFromTag(CEETags.FUSE_WRENCH).getDefaultInstance());
            h.assertTrue(!tag(h).contains("FirstID")&&tag(h).getString("SecondID").equals("electroenergetics:cut_off_switch"),"Native indicator removal changed other slot");DynamicGameTests.audit(h);h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=250)
    public static void nativeHeldFuseTripsReturnsBrokenItemAndReplacementReusesWires(GameTestHelper h) {
        place(h);Set<WorldNetworks.PartId> wires=new HashSet<>();
        h.runAtTickTime(5,()->{setup(h,20,1000);install(h,h.absolutePos(HOLDER),true,fuse(1));wire(h);});
        h.runAtTickTime(35,()->{near(h,voltage(h),20,.03,"Native held fuse conducts");wires.addAll(ids(h.getLevel(),h.absolutePos(HOLDER)));ShowroomTools.resistance(h,h.absolutePos(LOAD),1);});
        h.runAtTickTime(130,()->{
            h.assertTrue(tag(h).getCompound("FirstData").getBoolean("Broken"),"Native held fuse did not trip");near(h,voltage(h),0,.001,"Held fuse isolates load");
            var player=use(h,h.absolutePos(HOLDER),true,ItemStack.EMPTY);
            h.assertTrue(player.getInventory().items.stream().anyMatch(s->CEEBlocks.BROKEN_FUSE.isIn(s)&&s.getOrDefault(CEEDataComponents.FUSE_AMPERAGE,100)==1),"Removal lost native broken item or rating");
            ShowroomTools.resistance(h,h.absolutePos(LOAD),1000);install(h,h.absolutePos(HOLDER),true,fuse(3));
        });
        h.runAtTickTime(165,()->{near(h,voltage(h),20,.03,"Native replacement restores mixed power");roundTrip(h);});
        h.runAtTickTime(195,()->{
            near(h,voltage(h),20,.03,"Replacement survives native serialization");h.assertTrue(tag(h).getCompound("FirstData").getDouble("SetAmperage")==3,"Replacement lost native rating");
            h.assertTrue(wires.equals(ids(h.getLevel(),h.absolutePos(HOLDER))),"Held fuse replacement changed PG wire identities");DynamicGameTests.audit(h);h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=3100)
    public static void allNativeHeldComponentsSurviveColdRestartAndFiveActualChunkCycles(GameTestHelper h) throws IOException {
        var level=h.getLevel();var source=new BlockPos(48000,64,48000);var a=source.east(3);var b=source.east(6);var chunk=new ChunkPos(source);
        var path=Path.of("fuse-holders-before-restart.nbt");boolean verify=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        var expected=verify?NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()):new CompoundTag();
        String[] expectedColor={verify?expected.getCompound("A").getCompound("SecondData").getString("Color"):"lime"};
        var unloads=new AtomicInteger();Consumer<ChunkEvent.Unload> listener=e->{if(e.getLevel()==level&&e.getChunk().getPos().equals(chunk))unloads.incrementAndGet();};
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class,listener);level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(source);
        if(!verify){
            for(var p:new BlockPos[]{source,a,b,source.east(9),source.east(9).south(3),source.east(9).south(6)}){
                level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(p,p.equals(source)?ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState():p.equals(a)||p.equals(b)?holder():ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
            }
        }else verifySaved(h,a,b,expected,"Cold before first solve");
        Set<WorldNetworks.PartId> owners=new HashSet<>();
        var seq=h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for native holder chunk entities")).thenIdle(5).thenExecute(()->{
            if(verify)return;
            ShowroomTools.pgSource(h,source,70);for(int i=0;i<3;i++)ShowroomTools.resistance(h,source.east(9).south(i*3),1000);
            install(h,a,true,CEEBlocks.CUT_OFF_SWITCH.asStack());use(h,a,true,ItemStack.EMPTY);install(h,a,false,CEEBlocks.INDICATOR_BULB.asStack());use(h,a,false,Items.LIME_DYE.getDefaultInstance());
            install(h,b,true,bypass());install(h,b,false,fuse(3));
            branch(h,source,a,1,3,source.east(9));branch(h,source,b,1,3,source.east(9).south(3));branch(h,source,b,0,2,source.east(9).south(6));
            WiringGameTests.connect(h,source,0,a,0,false);WiringGameTests.connect(h,source,1,a,2,true);
        }).thenIdle(35).thenExecute(()->{
            operating(h,source,a,b,expectedColor[0]);owners.addAll(ids(level,a));owners.addAll(ids(level,b));h.assertTrue(owners.size()==4,"Four PG holder wires required");
            if(verify)h.assertTrue(ownerString(owners).equals(expected.getString("WireIds")),"Cold restart changed native holder wire identities");
            // Edit an already saved holder through its actual player workflow.
            // Dynamic saved-data persistence cannot conceal a stale block entity.
            level.getChunkSource().save(true);
            h.assertTrue(!level.getChunkAt(a).isUnsaved(),"Native baseline chunk did not finish saving");
            expectedColor[0]=expectedColor[0].equals("lime")?"red":"lime";
            use(h,a,false,(expectedColor[0].equals("red")?Items.RED_DYE:Items.LIME_DYE).getDefaultInstance());
            h.assertTrue(level.getChunkAt(a).isUnsaved(),"Editing existing native holder did not schedule a chunk save");
        });
        for(int cycle=1;cycle<=5;cycle++){
            int count=cycle;
            seq.thenExecute(()->{
                capture(level,a,b,expected);
                org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("HOLDER_SAVE_STATE: cycle={}, unsaved={}, A={}, B={}",count,level.getChunkAt(a).isUnsaved(),expected.getCompound("A"),expected.getCompound("B"));
                level.getDataStorage().save();level.getChunkSource().save(true);level.setChunkForced(chunk.x,chunk.z,false);
            })
                    .thenIdle(330).thenWaitUntil(()->{level.getChunkSource().tick(()->true,true);h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&unloads.get()>=count,"Waiting for actual holder unload "+count);})
                    .thenExecute(()->{DelayedEntityLoads.hold(level,chunk,60);level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(source);verifySaved(h,a,b,expected,"Cycle "+count+" before first solve");})
                    .thenIdle(20).thenExecute(()->{h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()),"Delayed holder wire entities not exercised");h.assertTrue(owners.stream().allMatch(id->GlobalElectricNetworks.getWorldNetworks(level).getPart(id)!=null),"Native holder wires expired before entity readiness");})
                    .thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong()),"Waiting for holder wire entities")).thenIdle(30)
                    .thenExecute(()->{operating(h,source,a,b,expectedColor[0]);var current=ids(level,a);current.addAll(ids(level,b));h.assertTrue(owners.equals(current),"Holder reload duplicated or replaced wires");org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("FUSE_HOLDER_CYCLE: cycle={}, actualUnloads={}",count,unloads.get());});
        }
        seq.thenExecute(()->{
            capture(level,a,b,expected);expected.putString("WireIds",ownerString(owners));try{NbtIo.writeCompressed(expected,path);}catch(IOException e){throw new RuntimeException(e);}
            level.getDataStorage().save();level.getChunkSource().save(true);NeoForge.EVENT_BUS.unregister(listener);level.setChunkForced(chunk.x,chunk.z,false);DynamicGameTests.audit(h);
        }).thenSucceed();
    }
    private static void capture(ServerLevel level,BlockPos a,BlockPos b,CompoundTag out){
        // Native holder writes insert tags by reference. Freeze the observed
        // snapshot before another native commit can advance switch history.
        out.put("A",level.getBlockEntity(a).saveWithoutMetadata(level.registryAccess()).copy());
        out.put("B",level.getBlockEntity(b).saveWithoutMetadata(level.registryAccess()).copy());
    }
    private static void verifySaved(GameTestHelper h,BlockPos a,BlockPos b,CompoundTag expected,String phase){
        for(int i=0;i<2;i++){
            var p=i==0?a:b;var actual=h.getLevel().getBlockEntity(p).saveWithoutMetadata(h.getLevel().registryAccess());var old=expected.getCompound(i==0?"A":"B");
            for(String slot:new String[]{"First","Second"}){
                h.assertTrue(actual.getString(slot+"ID").equals(old.getString(slot+"ID")),phase+" lost native insert identity");
                if(!actual.getCompound(slot+"Data").equals(old.getCompound(slot+"Data")))
                    org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.error("HOLDER_SAVED_MISMATCH: phase={}, holder={}, slot={}, expected={}, actual={}",phase,p,slot,old.getCompound(slot+"Data"),actual.getCompound(slot+"Data"));
                h.assertTrue(actual.getCompound(slot+"Data").equals(old.getCompound(slot+"Data")),phase+" changed serialized native insert state");
            }
        }
    }
    private static void operating(GameTestHelper h,BlockPos source,BlockPos a,BlockPos b,String expectedColor){
        var sa=h.getLevel().getBlockEntity(a).saveWithoutMetadata(h.getLevel().registryAccess());var sb=h.getLevel().getBlockEntity(b).saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(sa.getCompound("FirstData").getBoolean("Closed")&&sa.getCompound("SecondData").getString("Color").equals(expectedColor)&&sa.getCompound("SecondData").getFloat("Light")>.99,"Reload lost native switch or indicator output");
        h.assertTrue(!sb.getCompound("SecondData").getBoolean("Broken")&&sb.getCompound("SecondData").getDouble("SetAmperage")==3,"Reload lost native fuse setting");
        for(int i=0;i<3;i++)near(h,ShowroomTools.volts(h,source.east(9).south(i*3),0,1),70,.03,"Reloaded native held load "+i);
    }
    private static void branch(GameTestHelper h,BlockPos source,BlockPos holder,int in,int out,BlockPos load){WiringGameTests.connect(h,source,0,holder,in,true);WiringGameTests.connect(h,holder,out,load,0,false);WiringGameTests.connect(h,source,1,load,1,true);}
    private static String ownerString(Set<WorldNetworks.PartId> ids){return ids.stream().map(Object::toString).sorted().collect(java.util.stream.Collectors.joining("\n"));}
    private static Set<WorldNetworks.PartId> ids(ServerLevel level,BlockPos p){var out=new HashSet<WorldNetworks.PartId>();for(int port=0;port<4;port++){var wires=GlobalElectricNetworks.getWorldNetworks(level).findConnectedWires(new BlockWireEndpoint(p,port));if(wires!=null)for(var w:wires)out.add(w.persistentOwnerId);}return out;}
    private static net.minecraft.world.level.block.state.BlockState holder(){return CEEBlocks.FUSE_HOLDER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP);}
    private static void place(GameTestHelper h){for(var p:new BlockPos[]{SOURCE,HOLDER,LOAD})h.setBlock(p.below(),Blocks.STONE);h.setBlock(SOURCE,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(HOLDER,holder());h.setBlock(LOAD,ModdedBlocks.CREATIVE_RESISTOR.get());}
    private static void setup(GameTestHelper h,double v,double r){ShowroomTools.pgSource(h,h.absolutePos(SOURCE),v);ShowroomTools.resistance(h,h.absolutePos(LOAD),r);}
    private static ItemStack fuse(int rating){var item=CEEBlocks.FUSE.asStack();item.set(CEEDataComponents.FUSE_AMPERAGE,rating);return item;}
    private static ItemStack bypass(){return CEETags.itemFromTag(CEETags.FUSE_BYPASS_ITEM).getDefaultInstance();}
    private static void install(GameTestHelper h,BlockPos holder,boolean first,ItemStack item){use(h,holder,first,item);h.assertTrue(item.isEmpty(),"Native insert installation did not consume its item");}
    private static net.minecraft.world.entity.player.Player use(GameTestHelper h,BlockPos holder,boolean first,ItemStack item){
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,item);var click=holder.getCenter().add(first?.25:-.25,.5,0);
        h.assertTrue(h.getLevel().getBlockState(holder).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(click,Direction.UP,holder,false)).consumesAction(),"Native held component interaction failed");return player;
    }
    private static CompoundTag tag(GameTestHelper h){return h.getBlockEntity(HOLDER).saveWithoutMetadata(h.getLevel().registryAccess());}
    private static void roundTrip(GameTestHelper h){var be=(FuseHolderBlockEntity)h.getBlockEntity(HOLDER);var saved=tag(h).copy();be.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(saved.equals(tag(h)),"Native holder NBT round trip changed contents");be.updateFuses();}
    private static void indicator(GameTestHelper h,double light){var data=tag(h).getCompound("FirstData");near(h,data.getFloat("Light"),light,.015,"Native held indicator brightness");h.assertTrue(data.getString("Color").equals("lime"),"Native held indicator lost dye");}
    private static void wire(GameTestHelper h){branch(h,h.absolutePos(SOURCE),h.absolutePos(HOLDER),1,3,h.absolutePos(LOAD));}
    private static double voltage(GameTestHelper h){return ShowroomTools.volts(h,h.absolutePos(LOAD),0,1);}
    private static void near(GameTestHelper h,double actual,double expected,double tolerance,String label){ShowroomTools.near(h,actual,expected,tolerance,label);}
}
