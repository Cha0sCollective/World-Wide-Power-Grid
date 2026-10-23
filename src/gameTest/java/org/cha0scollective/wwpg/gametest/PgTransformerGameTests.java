package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.collections.ModdedDataComponents;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.TransformerCoreBlock;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireConnection;
import org.patryk3211.powergrid.kinetics.base.TunedBlock;
import org.patryk3211.powergrid.kinetics.variac.VariacBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class PgTransformerGameTests {
    @GameTest(template="empty",timeoutTicks=100) public static void assembledSmallTransformerStepUp(GameTestHelper h){transformer(h,false,20,40);}
    @GameTest(template="empty",timeoutTicks=100) public static void assembledSmallTransformerStepDown(GameTestHelper h){transformer(h,false,40,20);}
    @GameTest(template="empty",timeoutTicks=100) public static void assembledMediumTransformerStepUp(GameTestHelper h){transformer(h,true,20,40);}
    @GameTest(template="empty",timeoutTicks=100) public static void assembledMediumTransformerStepDown(GameTestHelper h){transformer(h,true,40,20);}

    private static void transformer(GameTestHelper h,boolean medium,int primary,int secondary){
        var core=new BlockPos(3,2,3);var source=new BlockPos(1,2,1);var load=new BlockPos(6,2,3);
        for(var p:new BlockPos[]{core,core.south(),source,load})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(core,ModdedBlocks.TRANSFORMER_CORE.get());
        if(medium)for(var p:new BlockPos[]{core.south(),core.above(),core.south().above()})h.setBlock(p,ModdedBlocks.TRANSFORMER_CORE.get());
        h.setBlock(source,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(load,CEEBlocks.CREATIVE_RESISTOR.get());
        var base=h.absolutePos(core);var s=h.absolutePos(source);var l=h.absolutePos(load);var level=h.getLevel();
        var top=medium?base.above():base;var other=medium?base.south().above():base;
        h.runAtTickTime(5,()->{
            var player=h.makeMockPlayer(GameType.SURVIVAL);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(base.getCenter(),Direction.NORTH,base,false));
            h.assertTrue(((TransformerCoreBlock)level.getBlockState(base).getBlock()).onWrenched(level.getBlockState(base),context).consumesAction(),"Native transformer core assembly failed");
            h.assertTrue(level.getBlockState(base).is(medium?ModdedBlocks.TRANSFORMER_MEDIUM.get():ModdedBlocks.TRANSFORMER_SMALL.get()),"Wrong assembled transformer size");
        });
        h.runAtTickTime(10,()->{
            wind(h,top,0,1,primary);wind(h,other,2,3,secondary);
            DevicesSavedData.load(level).getDevice(s,CreativeBatteryDevice.class).voltage=20;
            DevicesSavedData.load(level).getDevice(l,ResistorDevice.class).properties=ElectricalProperties.resistor(1000);
            WiringGameTests.connect(h,s,1,top,0,false);WiringGameTests.connect(h,s,0,top,1,false);
            WiringGameTests.connect(h,other,2,l,0,false);WiringGameTests.connect(h,other,3,l,1,false);
        });
        h.runAtTickTime(40,()->{
            var be=(TransformerBlockEntity)level.getBlockEntity(base);
            h.assertTrue(be.hasPrimary()&&be.hasSecondary()&&be.getPrimary().getTurns()==primary&&be.getSecondary().getTurns()==secondary,"Wound turns were not retained");
            double ratio=(double)secondary/primary,k=be.couplingFactor(),al=be.coreAl();
            double rp=(1-k)*primary*primary*al,rs=(1-k)*secondary*secondary*al;
            double rm=k*primary*primary*al*ModdedConfigs.server().electricity.transformerMutualInductanceMultiplier.getF();
            double reflected=(1000+rs)/(ratio*ratio),parallel=1/(1/rm+1/reflected);
            double expected=20*parallel/(rp+parallel)*ratio*1000/(1000+rs);
            near(h,voltage(h,l),expected,Math.max(.08,expected*.01),"Native transformer T-model with mixed source and load");
            var nbt=be.saveWithoutMetadata(level.registryAccess());be.loadWithComponents(nbt,level.registryAccess());
            h.assertTrue(be.getPrimary().getTurns()==primary&&be.getSecondary().getTurns()==secondary,"Transformer winding NBT round trip changed turns");
            DevicesSavedData.load(level).getDevice(s,CreativeBatteryDevice.class).voltage=10;
            h.runAfterDelay(15,()->{near(h,voltage(h,l),expected/2,Math.max(.08,expected*.01),"Transformer response after configuration/reload");
                level.destroyBlock(base,false);h.runAfterDelay(15,()->{near(h,voltage(h,l),0,.001,"Removed transformer left ghost power");DynamicGameTests.audit(h);h.succeed();});});
        });
    }

    private static void wind(GameTestHelper h,BlockPos pos,int first,int last,int turns){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=ModdedItems.WIRE.asStack(64);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        // This is the data produced by PG's native winding settings packet.
        stack.set(ModdedDataComponents.CONNECTION_DATA.get(),WireConnection.of(pos,turns,new BlockWireEndpoint(pos,first)));
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(new BlockWireEndpoint(pos,last).getExactPosition(h.getLevel()),Direction.UP,pos,false));
        h.assertTrue(IElectric.getAt(h.getLevel(),pos).onWire(h.getLevel().getBlockState(pos),context).consumesAction(),"Native winding operation failed at "+pos+":"+last);
        h.assertTrue(stack.getCount()==64-turns&&!stack.has(ModdedDataComponents.CONNECTION_DATA.get()),"Winding did not consume copper or clear winding selection");
    }

    @GameTest(template="empty",timeoutTicks=150) public static void pgVariacMechanicallyAdjustsCeeLoad(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);var drive=b.above();
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.VARIAC.getDefaultState().setValue(TunedBlock.HORIZONTAL_FACING,Direction.SOUTH));h.setBlock(c,CEEBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(a);var v=h.absolutePos(b);var l=h.absolutePos(c);var level=h.getLevel();
        double[] initial={0};
        h.runAtTickTime(5,()->{
            DevicesSavedData.load(level).getDevice(s,CreativeBatteryDevice.class).voltage=20;
            DevicesSavedData.load(level).getDevice(l,ResistorDevice.class).properties=ElectricalProperties.resistor(1000);
            WiringGameTests.connect(h,s,1,v,0,false);WiringGameTests.connect(h,s,0,v,1,false);
            WiringGameTests.connect(h,v,2,l,0,false);WiringGameTests.connect(h,v,1,l,1,false);
        });
        h.runAtTickTime(30,()->{initial[0]=voltage(h,l);h.assertTrue(initial[0]>19,"Full-ratio variac output was "+initial[0]);
            h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
            h.runAfterDelay(2,()->((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(32));});
        h.runAtTickTime(55,()->{var be=(VariacBlockEntity)level.getBlockEntity(v);double ratio=be.getRatio();
            h.assertTrue(Math.abs(be.getSpeed())==32&&ratio>.2&&ratio<.8,"Native shaft did not move variac arm: "+ratio+", speed="+be.getSpeed());
            h.assertTrue(voltage(h,l)<initial[0]*.7,"Moving variac did not reduce load voltage");
            ((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(0);
            h.runAfterDelay(10,()->{double held=be.getRatio();near(h,voltage(h,l),20*held,.1,"Settled native variac ratio");
                var nbt=be.saveWithoutMetadata(level.registryAccess());be.loadWithComponents(nbt,level.registryAccess());
                h.runAfterDelay(10,()->{near(h,voltage(h,l),20*held,.1,"Stopped/reloaded variac held its setting");DynamicGameTests.audit(h);h.succeed();});});
        });
    }
    private static double voltage(GameTestHelper h,BlockPos pos){return InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(pos,0,1);}
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
