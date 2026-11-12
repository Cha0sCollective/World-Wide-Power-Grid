package org.cha0scollective.wwpg.circuits;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Same native-PG onWire actions as the prior fixture, with no CEE branch. */
final class NativeCircuitWiring {
    static void connect(GameTestHelper h,BlockPos a,int terminalA,BlockPos b,int terminalB,boolean pgWire){
        h.assertTrue(pgWire,"Only stock PG wire is allowed");
        var level=h.getLevel();
        var player=new net.minecraft.world.entity.player.Player(level,a,0,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Stock-PG-wire-test")){
            public boolean isSpectator(){return false;}public boolean isCreative(){return true;}
        };
        player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.WIRE.asStack(64));
        for(var endpoint:new BlockWireEndpoint[]{new BlockWireEndpoint(a,terminalA),new BlockWireEndpoint(b,terminalB)}){
            var pos=endpoint.getPos();var click=endpoint.getExactPosition(level);
            h.assertTrue(click!=null,"Missing native PG terminal geometry");
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(click,Direction.UP,pos,false));
            h.assertTrue(IElectric.getAt(level,pos).onWire(level.getBlockState(pos),context).consumesAction(),"Native PG wire placement failed");
        }
    }
}
