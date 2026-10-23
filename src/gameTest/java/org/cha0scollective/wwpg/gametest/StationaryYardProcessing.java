package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Native in-world recipes with collected proof and repeatable player inputs. */
final class StationaryYardProcessing {
    private final StationaryYardStations yard;
    StationaryYardProcessing(StationaryYardStations yard) { this.yard=yard; }
    void build() { process(false);process(true); }
    private void process(boolean magnetic) {
        var h=yard.h;var s=yard.station(magnetic?"Magnet making":"Fan flour washing",List.of(magnetic?"powergrid:electromagnet":"powergrid:electric_fan"),
                magnetic?"Throw alloy under":"Throw flour in water",magnetic?"Alloy -> magnet":"Flour -> dough","Feed OFF stops work","The adjacent chest contains the product made during acceptance and spare input to repeat the native recipe.");
        var device=s.device().above();var input=magnetic?device.below():device.south(2);var cabinet=s.source().north(2);
        yard.feed(s,false);yard.prepareMeter(s);yard.block(cabinet,Blocks.CHEST);
        yard.state(device,(magnetic?ModdedBlocks.ELECTROMAGNET.getDefaultState():ModdedBlocks.ELECTRIC_FAN.getDefaultState().setValue(BlockStateProperties.FACING,Direction.SOUTH)));
        var terminal=magnetic?device.above():device;
        if(magnetic)yard.state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        else {
            for(int z=1;z<=4;z++){yard.block(device.south(z).below(),Blocks.STONE);yard.block(device.south(z).east(),Blocks.GLASS);yard.block(device.south(z).west(),Blocks.GLASS);}
            yard.block(device.south(4),Blocks.GLASS);yard.block(device.south(),Blocks.WATER);
        }
        var product=magnetic?ModdedItems.MAGNET:AllItems.DOUGH;var inputItem=magnetic?AllItems.ANDESITE_ALLOY:AllItems.WHEAT_FLOUR;
        var area=new AABB(input).inflate(magnetic?1:5);
        yard.configure.add(()->{
            double voltage=((ElectricBlockEntity)h.getLevel().getBlockEntity(device)).resistance()*(magnetic?3:4);yard.switchedPair(s,terminal,voltage,false);
            var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(cabinet);chest.setItem(1,inputItem.asStack(64));chest.setChanged();
            var entity=new ItemEntity(h.getLevel(),input.getX()+.5,input.getY()+.1,input.getZ()+.5,inputItem.asStack());entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);entity.setDefaultPickUpDelay();h.getLevel().addFreshEntity(entity);
        });
        if(!yard.restore)h.startSequence().thenIdle(40).thenWaitUntil(()->h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area).stream().anyMatch(e->product.isIn(e.getItem())),"Waiting for native yard recipe "+s.code()))
                .thenExecute(()->{
                    var made=h.getLevel().getEntitiesOfClass(ItemEntity.class,area).stream().filter(e->product.isIn(e.getItem())).findFirst().orElseThrow();
                    var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(cabinet);chest.setItem(0,made.getItem().copy());chest.setChanged();made.discard();
                });
        yard.check(s,()->{
            var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(cabinet);h.assertTrue(product.isIn(chest.getItem(0)),"Native recipe product is absent from the saved proof chest");
            if(magnetic)h.assertTrue(((org.patryk3211.powergrid.electricity.electromagnet.ElectromagnetBlockEntity)h.getLevel().getBlockEntity(device)).getFieldStrength()>.25,"Magnet has no solved field");
            else h.assertTrue(((org.patryk3211.powergrid.electricity.fan.ElectricFanBlockEntity)h.getLevel().getBlockEntity(device)).getSpeed()>200,"Fan has no native recipe airflow");
        });
    }
}
