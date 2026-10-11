package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationBlock;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;

/** Uses the published duplex item handler, rather than inserting a cable into saved data. */
@GameTestHolder("wwpg_bundle")
@PrefixGameTestTemplate(false)
public final class BundledWireGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void duplexLimitsCancellationStaleSelectionAndBlockRemovalRetainNativeCosts(GameTestHelper h) {
        var first=h.absolutePos(new BlockPos(1,2,1));var second=first.east(4);
        var far=first.east((int)Math.ceil(CEEWireTypes.DUPLEX.get().getMaxLength())+1);
        var state=CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState().setValue(BundledWireTerminationBlock.FACING,Direction.UP)
                .setValue(BundledWireTerminationBlock.ROLL,false).setValue(BundledWireTerminationBlock.FLIP,false);
        for(var p:java.util.List.of(first,second,far)){h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(p,state);}
        var player=h.makeMockPlayer(GameType.SURVIVAL);var spool=CEEItems.DUPLEX_WIRE_SPOOL.asStack(2);
        var cable=new InWorldNodeConnection(new InWorldNode(0x1000,first),new InWorldNode(0x1000,second));
        h.runAtTickTime(5,()->{
            click(h,player,spool,first);
            h.assertTrue(result(h,player,far)==net.minecraft.world.InteractionResult.FAIL&&spool.getCount()==2,"Overlength duplex cable changed native length validation or cost");
            player.setShiftKeyDown(true);click(h,player,spool,second);player.setShiftKeyDown(false);
            h.assertTrue(!spool.has(CEEDataComponents.SELECTED_WIRE_TERMINATION)&&spool.getCount()==2,"Native shift cancellation retained a selection or consumed wire");
            click(h,player,spool,first);h.getLevel().setBlockAndUpdate(first,Blocks.AIR.defaultBlockState());
            h.assertTrue(result(h,player,second)==net.minecraft.world.InteractionResult.FAIL&&!spool.has(CEEDataComponents.SELECTED_WIRE_TERMINATION)&&spool.getCount()==2,"Removed first termination retained a stale selection or consumed wire");
            h.getLevel().setBlockAndUpdate(first,state);click(h,player,spool,first);
            h.getLevel().setBlockAndUpdate(first,state.setValue(BundledWireTerminationBlock.ROLL,true));
            h.assertTrue(result(h,player,second)==net.minecraft.world.InteractionResult.FAIL&&!spool.has(CEEDataComponents.SELECTED_WIRE_TERMINATION)&&spool.getCount()==2,"Changed conductor layout retained a stale selection");
            h.getLevel().setBlockAndUpdate(first,state);click(h,player,spool,first);click(h,player,spool,second);
            h.assertTrue(spool.getCount()==1&&InfrastructureSavedData.load(h.getLevel()).isConnected(cable.node1(),cable.node2()),"Valid duplex cable failed after cancelled/invalid placement");
        });
        h.runAtTickTime(25,()->{
            var bridge=org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel());
            for(int core=1;core<=2;core++)h.assertTrue(bridge.branchIdentity(new InWorldNode(0x1000|core,first),new InWorldNode(0x1000|core,second))!=null,"Native cable has no derived conductor "+core);
            h.getLevel().destroyBlock(first,true,player);
            h.assertTrue(!InfrastructureSavedData.load(h.getLevel()).isConnected(cable.node1(),cable.node2()),"Removing termination retained the parent cable");
            int drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(first).inflate(8)).stream()
                    .map(net.minecraft.world.entity.item.ItemEntity::getItem).filter(s->s.is(CEEItems.INSULATED_WIRE.get())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(drops==com.george_vi.electroenergetics.config.CEEConfigs.server().wiresPerSpool.get(),"Termination removal dropped zero or duplicate conductor costs: "+drops);
        });
        h.runAtTickTime(40,()->{
            var bridge=org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel());
            for(int core=1;core<=2;core++)h.assertTrue(bridge.branchIdentity(new InWorldNode(0x1000|core,first),new InWorldNode(0x1000|core,second))==null,"Removed cable retained a derived conductor "+core);
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    private static net.minecraft.world.InteractionResult result(GameTestHelper h,net.minecraft.world.entity.player.Player player,BlockPos pos) {
        return player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)));
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void nativeBundleNodeHelpersMatchEveryRegisteredOrientation(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,2,1));
        for(var facing:Direction.values())for(boolean roll:new boolean[]{false,true})for(boolean flip:new boolean[]{false,true}) {
            var registered=com.george_vi.electroenergetics.content.bundled_wire.BundledWireNodeConfigurator.getNodesFor(facing,roll,flip);
            for(int conductor=0;conductor<=2;conductor++) {
                var node=com.george_vi.electroenergetics.content.bundled_wire.BundledWireNodeConfigurator.getWireNodeFor(pos,roll,flip,conductor);
                h.assertTrue(registered.containsKey(node.id()),"Native bundle helper selected an unregistered ID "+node.id()+" for "+facing+" / "+roll+" / "+flip+" / "+conductor);
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeDuplexSpoolConnectsBothConductorsAndRecoversOnce(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,2,1)); var first=source.east(3);
        var second=first.east(4); var load=second.east(3);
        for(var pos:new BlockPos[]{source,first,second,load}) h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        h.getLevel().setBlockAndUpdate(load,CEEBlocks.INDICATOR_BULB.getDefaultState());
        for(var pos:new BlockPos[]{first,second}) h.getLevel().setBlockAndUpdate(pos,CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState()
                .setValue(BundledWireTerminationBlock.FACING,Direction.UP).setValue(BundledWireTerminationBlock.ROLL,false).setValue(BundledWireTerminationBlock.FLIP,false));
        var cable=new InWorldNodeConnection(new InWorldNode(0x1000,first),new InWorldNode(0x1000,second));
        var player=h.makeMockPlayer(GameType.SURVIVAL);var spool=CEEItems.DUPLEX_WIRE_SPOOL.asStack(2);
        h.runAtTickTime(5,()->{
            ShowroomTools.pgSource(h,source,10);
            WiringGameTests.connect(h,source,0,first,0x1001,true);
            WiringGameTests.connect(h,source,1,first,0x1002,false);
            WiringGameTests.connect(h,second,0x1001,load,0,false);
            WiringGameTests.connect(h,second,0x1002,load,1,true);
            click(h,player,spool,first);click(h,player,spool,second);
            var sd=InfrastructureSavedData.load(h.getLevel());
            h.assertTrue(sd.isConnected(cable.node1(),cable.node2()),"Published duplex spool accepted both clicks but created no cable");
            h.assertTrue(spool.getCount()==1&&player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get())==1,
                    "Duplex placement must consume one spool and return one empty spool");
            h.assertTrue(!spool.has(CEEDataComponents.SELECTED_WIRE_TERMINATION),"Completed duplex selection was retained");
            click(h,player,spool,first);
            h.assertTrue(spool.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(second.getCenter(),Direction.UP,second,false)))==net.minecraft.world.InteractionResult.FAIL,
                    "Duplicate duplex cable should retain native rejection feedback");
            h.assertTrue(spool.getCount()==1,"Duplicate duplex placement consumed another spool");
        });
        h.runAtTickTime(30,()->{
            BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),10,.01,
                    "Duplex conductors did not carry mixed supply and return");
            var empty=CEEItems.EMPTY_SPOOL.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,empty);
            CEEWireInteractionBehaviours.EMPTY_SPOOL.get().interactWire(new NodeConnectionPoint(cable.node1(),cable.node2(),.5f),h.getLevel(),player,empty);
            h.assertTrue(!InfrastructureSavedData.load(h.getLevel()).isConnected(cable.node1(),cable.node2())&&empty.isEmpty()
                            &&player.getInventory().countItem(CEEItems.DUPLEX_WIRE_SPOOL.get())==1,
                    "Native recovery failed to remove exactly one duplex cable and return one spool");
        });
        h.runAtTickTime(45,()->{
            BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),0,.00001,
                    "Recovered duplex left ghost power");DynamicGameTests.audit(h);h.succeed();
        });
    }
    static void click(GameTestHelper h,net.minecraft.world.entity.player.Player player,ItemStack stack,BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        h.assertTrue(stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter(),Direction.UP,pos,false))).consumesAction(),"Native duplex item rejected the termination");
    }
}
