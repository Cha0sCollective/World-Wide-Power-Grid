package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.wire.*;
import java.util.*;

@GameTestHolder("wwpg_wires") @PrefixGameTestTemplate(false)
public final class TerminalToolControlGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void terminalToolsDoNotOperateStandaloneCeeControls(GameTestHelper h) {
        var blocks=List.of(CEEBlocks.CUT_OFF_SWITCH,CEEBlocks.MOMENTARY_SWITCH,CEEBlocks.EMERGENCY_STOP_BUTTON,
                CEEBlocks.HV_SWITCH,CEEBlocks.ENERGY_METER,CEEBlocks.TRI_POLAR_ENERGY_METER);
        for(int i=0;i<blocks.size();i++) {
            var p=new BlockPos(2+(i%3)*4,2,2+(i/3)*4);
            h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,blocks.get(i).getDefaultState());
        }
        h.runAtTickTime(5,()->{
            var player=h.makeMockPlayer(GameType.SURVIVAL);
            for(int i=0;i<blocks.size();i++) {
                var pos=h.absolutePos(new BlockPos(2+(i%3)*4,2,2+(i/3)*4));
                var point=pos.getCenter().add(0,0,-.35);
                for(var tool:tools(h)) protectedClick(h,player,pos,point,tool);
                alternateHandsAndCancellation(h,player,pos,point);
                // The guard must not turn the control off for normal player operation.
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                var hit=new BlockHitResult(point,Direction.NORTH,pos,false);
                h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(ItemStack.EMPTY,h.getLevel(),player,
                        InteractionHand.MAIN_HAND,hit).consumesAction(),"Normal CEE control stopped operating: "+pos);
            }
            h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void terminalToolsDoNotOperateCeePanelControls(GameTestHelper h) {
        var relative=new BlockPos(5,2,5);h.setBlock(relative.below(),Blocks.STONE);
        h.setBlock(relative,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.NORTH));
        h.runAtTickTime(5,()->{
            var pos=h.absolutePos(relative);var be=(ElectricalPanelBlockEntity)h.getBlockEntity(relative);
            var player=h.makeMockPlayer(GameType.SURVIVAL);int attachments=0;
            for(var entry:CEERegistries.PANEL_ATTACHMENT_TYPE.entrySet()) {
                var id=entry.getKey().location();
                if(!id.getNamespace().equals("electroenergetics")||Set.of("altitude_sensor","velocity_sensor").contains(id.getPath()))continue;
                Arrays.fill(be.getAttachments(),null);be.attachmentUpdate();
                var type=entry.getValue();var slot=type.mode.possibleSlots[0];
                var attachment=PanelGameTests.insert(h,pos,type,slot);attachments++;
                var point=new Vec3(pos.getX()+slot.center.x,pos.getY()+slot.center.y,pos.getZ()+.6875);
                for(var tool:tools(h))protectedClick(h,player,pos,point,tool);
                alternateHandsAndCancellation(h,player,pos,point);
                // These controls have an unconditional normal click: prove it is retained.
                if(type==CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get()||type==CEEPanelAttachmentTypes.MINIATURE_CIRCUIT_BREAKER.get()) {
                    player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    var before=panelData(h,be);
                    var result=attachment.onInteract(ItemStack.EMPTY,player,InteractionHand.MAIN_HAND,
                            new BlockHitResult(point,Direction.NORTH,pos,false));
                    h.assertTrue(result.consumesAction()&&!before.equals(panelData(h,be)),"Normal panel control stopped operating: "+id);
                }
            }
            h.assertTrue(attachments==11,"Expected all 11 stationary panel attachment types");
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("TERMINAL_TOOL_CONTROLS_PASSED: panelAttachments={}, tools={}",attachments,tools(h).size());
            h.succeed();
        });
    }

    private static List<ItemStack> tools(GameTestHelper h) {
        var items=new ArrayList<>(List.of(ModdedItems.WIRE.asStack(64),ModdedItems.IRON_WIRE.asStack(64),
                ModdedItems.GOLDEN_WIRE.asStack(64),ModdedItems.INSULATED_COPPER_WIRE.asStack(64),
                ModdedItems.MULTIMETER.asStack(),CEEItems.WIRE_SPOOL.asStack(),CEEItems.EMPTY_SPOOL.asStack()));
        for(var entry:net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet())
            if(entry.getKey().location().getNamespace().equals("gauged_wires")&&IWire.isWire(h.getLevel(),entry.getValue()))items.add(new ItemStack(entry.getValue(),64));
        return items;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void terminalToolsDoNotOperateFuseHeldSwitch(GameTestHelper h) {
        var relative=new BlockPos(4,2,4);h.setBlock(relative.below(),Blocks.STONE);
        h.setBlock(relative,CEEBlocks.FUSE_HOLDER.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.UP));
        h.runAtTickTime(5,()->{
            var pos=h.absolutePos(relative);var player=h.makeMockPlayer(GameType.SURVIVAL);
            var point=pos.getCenter().add(.25,.5,0);
            var hit=new BlockHitResult(point,Direction.UP,pos,false);
            var item=CEEBlocks.CUT_OFF_SWITCH.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,item);
            h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction()&&item.isEmpty(),"Native held switch insertion failed");
            var before=snapshot(h,pos);
            for(var tool:tools(h)) {
                player.setItemInHand(InteractionHand.MAIN_HAND,tool.copy());
                var result=h.getLevel().getBlockState(pos).useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
                h.assertTrue(!result.consumesAction()&&before.equals(snapshot(h,pos)),"Terminal tool operated or replaced held switch: "+tool);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(ItemStack.EMPTY,h.getLevel(),player,InteractionHand.MAIN_HAND,hit).consumesAction()
                    &&!before.equals(snapshot(h,pos)),"Normal held-switch operation was blocked");
            h.succeed();
        });
    }

    private static void protectedClick(GameTestHelper h,Player player,BlockPos pos,Vec3 point,ItemStack tool) {
        protectedClick(h,player,pos,point,tool,InteractionHand.MAIN_HAND,false);
    }
    private static void alternateHandsAndCancellation(GameTestHelper h,Player player,BlockPos pos,Vec3 point) {
        for(var tool:List.of(ModdedItems.GOLDEN_WIRE.asStack(),ModdedItems.MULTIMETER.asStack()))
            for(var hand:InteractionHand.values())for(boolean shift:new boolean[]{false,true})
                protectedClick(h,player,pos,point,tool,hand,shift);
    }
    private static void protectedClick(GameTestHelper h,Player player,BlockPos pos,Vec3 point,ItemStack tool,InteractionHand hand,boolean shift) {
        player.setShiftKeyDown(shift);
        player.setItemInHand(InteractionHand.MAIN_HAND,hand==InteractionHand.MAIN_HAND?tool.copy():ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND,hand==InteractionHand.OFF_HAND?tool.copy():ItemStack.EMPTY);
        var from=point.add(0,0,-2);player.setPos(from.x,from.y-player.getEyeHeight(),from.z);
        player.setYRot(0);player.setXRot(0);player.setYHeadRot(0);player.yHeadRotO=0;player.setOldPosAndRot();
        var hit=player.pick(4,1,false);
        h.assertTrue(hit instanceof BlockHitResult b&&b.getBlockPos().equals(pos),"Control fixture aim missed "+pos);
        var blockHit=(BlockHitResult)hit;var before=snapshot(h,pos);
        var event=WireItem.useOn(player,hand,pos,blockHit.getDirection());
        if(!event.interruptsFurtherEvaluation()) {
            var result=h.getLevel().getBlockState(pos).useItemOn(player.getItemInHand(hand),h.getLevel(),player,hand,blockHit);
            h.assertTrue(!result.consumesAction(),"Terminal tool operated a CEE control: "+tool+" / "+pos);
        }
        h.assertTrue(before.equals(snapshot(h,pos)),"Terminal tool changed CEE control state: "+tool+" / "+pos);
    }

    private static String snapshot(GameTestHelper h,BlockPos pos) {
        var be=h.getLevel().getBlockEntity(pos);
        return h.getLevel().getBlockState(pos).toString()+(be==null?"":be.saveWithoutMetadata(h.getLevel().registryAccess()).toString());
    }
    private static CompoundTag panelData(GameTestHelper h,ElectricalPanelBlockEntity be){return be.saveWithoutMetadata(h.getLevel().registryAccess());}
}
