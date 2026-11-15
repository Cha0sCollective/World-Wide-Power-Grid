package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

/** Restored consumers must not retain saved rotation beside a paused source. */
@GameTestHolder("wwpg_motor_restoration")
@PrefixGameTestTemplate(false)
public final class MotorRestorationGameTests {
    @BeforeBatch(batch="wwpg_loading_motor")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_backdriven_motor")
    public static void configureBackdrive(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty",batch="wwpg_loading_backdriven_motor",timeoutTicks=100)
    public static void restorationKeepsExternallyDrivenMotorTurning(GameTestHelper h){
        var supply=new BlockPos(1,2,1);var drive=new BlockPos(3,2,3);var motor=new BlockPos(4,2,3);
        for(var p:new BlockPos[]{supply,drive,motor})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(supply,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(motor,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("electroenergetics:white_electric_motor")).defaultBlockState().setValue(ElectricMotorBlock.FACING,Direction.WEST));
        var level=h.getLevel();var s=h.absolutePos(supply);var m=h.absolutePos(motor);int[] held={0};
        h.runAtTickTime(5,()->{
            ((com.simibubi.create.foundation.blockEntity.SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(64);
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(0);
            for(int port=0;port<2;port++)WiringGameTests.connect(h,s,port,m,port,true);
        });
        h.runAtTickTime(35,()->RestorationCoordinator.restoring(level,new ChunkPos(s)));
        for(int tick=30;tick<75;tick++)h.runAtTickTime(tick,()->{
            var be=(KineticBlockEntity)level.getBlockEntity(m);
            h.assertTrue(be!=null&&be.hasSource()&&Math.abs(be.getSpeed())==64,"Restoration stopped an externally driven shaft");
            if(RestorationCoordinator.pausedAt(level,m))held[0]++;
        });
        h.runAtTickTime(80,()->{h.assertTrue(held[0]>0,"Back-driven motor never paused electrically");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",batch="wwpg_loading_motor",timeoutTicks=240)
    public static void poweredMotorReloadKeepsAttachedFans(GameTestHelper h) {
        var source=new BlockPos(1,2,1);
        var east=new BlockPos(3,2,3);var west=new BlockPos(6,2,6);
        h.setBlock(source.below(),Blocks.STONE);h.setBlock(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("electroenergetics:white_electric_motor"));
        for(var motor:new BlockPos[]{east,west}) {
            var facing=motor.equals(east)?Direction.EAST:Direction.WEST;
            var fan=motor.relative(facing);
            h.setBlock(motor.below(),Blocks.STONE);h.setBlock(fan.below(),Blocks.STONE);
            h.setBlock(motor,block.defaultBlockState().setValue(ElectricMotorBlock.FACING,facing));
            h.setBlock(fan,AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING,facing));
        }
        var level=h.getLevel();var s=h.absolutePos(source);
        var motors=new BlockPos[]{h.absolutePos(east),h.absolutePos(west)};
        var fans=new BlockPos[]{motors[0].east(),motors[1].west()};int[] pausedTicks={0};
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(230);
            for(var motor:motors)for(int terminal=0;terminal<2;terminal++)WiringGameTests.connect(h,s,terminal,motor,terminal,true);
        });
        h.runAtTickTime(35,()->checkFans(h,fans,true));
        // A freshly loaded kinetic BE has validationCountdown=0 and requests
        // attachment. Exercise that native order on the already powered motor
        // and consumers without replacing the electrical endpoint owners.
        for(int tick:new int[]{40,95})h.runAtTickTime(tick,()->{
            try {
                var validation=KineticBlockEntity.class.getDeclaredField("validationCountdown");validation.setAccessible(true);
                for(var motor:motors)validation.setInt(level.getBlockEntity(motor),0);
                for(var fan:fans)((KineticBlockEntity)level.getBlockEntity(fan)).updateSpeed=true;
            } catch(ReflectiveOperationException e) {
                throw new IllegalStateException("Pinned Create validation layout changed",e);
            }
            RestorationCoordinator.restoring(level,new ChunkPos(s));
        });
        for(int tick=41;tick<=140;tick++)h.runAtTickTime(tick,()->{
            for(var fan:fans)h.assertTrue(level.getBlockEntity(fan) instanceof KineticBlockEntity,"Paused motor validation destroyed its attached fan");
            if(RestorationCoordinator.pausedAt(level,motors[0]))pausedTicks[0]++;
        });
        h.runAtTickTime(85,()->checkFans(h,fans,true));
        h.runAtTickTime(145,()->{
            checkFans(h,fans,true);h.assertTrue(pausedTicks[0]>0,"Fixture never paused its restored motor");
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(0);
        });
        h.runAtTickTime(180,()->{
            checkFans(h,fans,false);((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(230);
        });
        h.runAtTickTime(220,()->{checkFans(h,fans,true);DynamicGameTests.audit(h);h.succeed();});
    }

    private static void checkFans(GameTestHelper h,BlockPos[] fans,boolean powered){
        for(var pos:fans){
            h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof KineticBlockEntity,"Attached fan disappeared");
            var fan=(KineticBlockEntity)h.getLevel().getBlockEntity(pos);
            h.assertTrue(powered?Math.abs(fan.getSpeed())==32:fan.getSpeed()==0,"Fan did not follow restored supply: "+fan.getSpeed()+" / "+fan.saveWithoutMetadata(h.getLevel().registryAccess()));
            h.assertTrue(fan.calculateStressApplied()>0,"Fan supplied no real mechanical load");
        }
    }
}
