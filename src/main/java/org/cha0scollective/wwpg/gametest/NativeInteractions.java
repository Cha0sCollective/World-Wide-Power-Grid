package org.cha0scollective.wwpg.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.ISocketElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.powercord.CordItem;

import java.util.UUID;

/** Exercises upstream item handlers, including their hidden socket terminals. */
final class NativeInteractions {
    static void connectCord(GameTestHelper h, BlockPos source, int positive, int negative, BlockPos target) {
        var hit = new BlockHitResult[1];
        var player = new Player(h.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "WWPG-cord-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
            @Override public HitResult pick(double distance, float partialTick, boolean fluids) { return hit[0]; }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.CORD.asStack(64));
        for (int port : new int[] {positive, negative}) {
            hit[0] = new BlockHitResult(new BlockWireEndpoint(source, port).getExactPosition(h.getLevel()), Direction.UP, source, false);
            h.assertTrue(CordItem.useOn(player, InteractionHand.MAIN_HAND, source, Direction.UP).isTrue(), "Native split cord selection failed");
        }
        var block = h.getLevel().getBlockState(target).getBlock();
        var click = block instanceof ISocketElectric socket
                ? socket.socket(h.getLevel().getBlockState(target)).getOrigin().add(target.getX(), target.getY(), target.getZ())
                : target.getCenter();
        hit[0] = new BlockHitResult(click, Direction.SOUTH, target, false);
        h.assertTrue(CordItem.useOn(player, InteractionHand.MAIN_HAND, target, Direction.SOUTH).isTrue(), "Native cord socket connection failed");
    }

    static void connectJunctionToSplit(GameTestHelper h,BlockPos junction,BlockPos target){
        var hit=new BlockHitResult[1];
        var player=new Player(h.getLevel(),BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"WWPG-junction-test")){
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return true;}
            @Override public HitResult pick(double distance,float tick,boolean fluids){return hit[0];}
        };
        player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.CORD.asStack(64));
        hit[0]=new BlockHitResult(junction.getCenter(),Direction.SOUTH,junction,false);
        h.assertTrue(CordItem.useOn(player,InteractionHand.MAIN_HAND,junction,Direction.SOUTH).isTrue(),"Native junction selection failed");
        for(int port=0;port<2;port++){
            hit[0]=new BlockHitResult(new BlockWireEndpoint(target,port).getExactPosition(h.getLevel()),Direction.UP,target,false);
            h.assertTrue(CordItem.useOn(player,InteractionHand.MAIN_HAND,target,Direction.UP).isTrue(),"Native cord split connection failed");
        }
    }
}
