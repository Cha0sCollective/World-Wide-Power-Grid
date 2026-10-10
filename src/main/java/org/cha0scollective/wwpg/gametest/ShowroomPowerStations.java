package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.gauge.*;
import org.patryk3211.powergrid.electricity.transformer.*;
import org.patryk3211.powergrid.electricity.wire.*;
import org.patryk3211.powergrid.kinetics.base.TunedBlock;

import java.util.*;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Native transformer assemblies, adjustable variacs, and both mods' meter banks. */
final class ShowroomPowerStations {
    private record Transformer(BlockPos source,BlockPos device,BlockPos load,BlockPos meter,String name,double input,double expected,boolean medium) {}
    private final List<Transformer> transformers=new ArrayList<>();
    private final List<BlockPos> meterBases=List.of(new BlockPos(32,64,156),new BlockPos(64,64,156));
    ShowroomPowerStations(GameTestHelper h,boolean restore) {
        String[] names={"CEE step-down","CEE step-up","PG small step-up","PG medium step-down","PG variac","CEE variac","CEE RS variac"};
        for(int i=0;i<names.length;i++) {
            var base=new BlockPos(32+(i%5)*16,64,124+(i/5)*16);
            var s=new Transformer(base,base.east(3),base.east(7),base.east(7).south(3),names[i],i<2?100:20,i==0?50:i==1?200:i==2?40:i==3||i==5?10:20,i==3);
            transformers.add(s);
            if(restore)continue;
            put(h,s.source,i<2?ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get():CEEBlocks.CREATIVE_BATTERY.get());
            if(i<2)put(h,s.device,CEEBlocks.TRANSFORMER.get());
            else if(i<4){put(h,s.device,ModdedBlocks.TRANSFORMER_CORE.get());if(s.medium)for(var p:new BlockPos[]{s.device.south(),s.device.above(),s.device.south().above()})put(h,p,ModdedBlocks.TRANSFORMER_CORE.get());}
            else if(i==4)h.getLevel().setBlockAndUpdate(s.device,ModdedBlocks.VARIAC.getDefaultState().setValue(TunedBlock.HORIZONTAL_FACING,Direction.SOUTH));
            else put(h,s.device,i==5?CEEBlocks.VARIAC.get():CEEBlocks.REDSTONE_VARIAC.get());
            put(h,s.load,ModdedBlocks.CREATIVE_RESISTOR.get());put(h,s.meter,ModdedBlocks.VOLTAGE_METER.get());
            if(i==4||i==5)h.getLevel().setBlockAndUpdate(s.device.above(),AllBlocks.HAND_CRANK.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
            if(i==6)h.getLevel().setBlockAndUpdate(s.device.north(),Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(BlockStateProperties.POWERED,true));
            sign(h,s.device.south(4),"T"+(i+1)+" "+s.name,"LIVE mixed circuit",s.input+" V input",i<4?"Read output gauge":"Crank/lever: vary V");
            sign(h,s.meter.south(2),"OUTPUT VOLTAGE",i<4?"About "+s.expected+" V":"Starts about 20 V","Goggles / probes","PG has modeled loss");
        }
        if(!restore){buildMeters(h);h.runAtTickTime(10,()->assemble(h));h.runAtTickTime(20,()->configure(h));}
    }
    private void assemble(GameTestHelper h) {
        for(int i:new int[]{2,3}) {
            var s=transformers.get(i);var player=h.makeMockPlayer(GameType.SURVIVAL);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(s.device.getCenter(),Direction.NORTH,s.device,false));
            h.assertTrue(((TransformerCoreBlock)h.getLevel().getBlockState(s.device).getBlock()).onWrenched(h.getLevel().getBlockState(s.device),context).consumesAction(),"Showroom PG core assembly failed");
            var top=s.medium?s.device.above():s.device;var other=s.medium?s.device.south().above():s.device;
            wind(h,top,0,1,s.medium?40:20);wind(h,other,2,3,s.medium?20:40);
        }
    }
    private void wind(GameTestHelper h,BlockPos pos,int first,int last,int turns) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=ModdedItems.WIRE.asStack(64);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.set(ModdedDataComponents.CONNECTION_DATA.get(),WireConnection.of(pos,turns,new BlockWireEndpoint(pos,first)));
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(new BlockWireEndpoint(pos,last).getExactPosition(h.getLevel()),Direction.UP,pos,false));
        h.assertTrue(IElectric.getAt(h.getLevel(),pos).onWire(h.getLevel().getBlockState(pos),context).consumesAction(),"Showroom winding failed");
    }
    private void configure(GameTestHelper h) {
        for(int i=0;i<transformers.size();i++) {
            var s=transformers.get(i);resistance(h,s.load,1000);
            scroll(h,s.meter,s.expected>20?2:1);
            if(i<2){pgSource(h,s.source,s.input);scroll(h,s.device,i==0?29:55);}
            else scroll(h,s.source,(int)s.input*1000);
            if(i==5){var be=h.getLevel().getBlockEntity(s.device);var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());tag.putFloat("Progress",.5f);be.loadWithComponents(tag,h.getLevel().registryAccess());DevicesSavedData.load(h.getLevel()).getDevice(s.device,com.george_vi.electroenergetics.content.variac.VariacDevice.class).progress=.5f;be.setChanged();}
            var primary=i==3?s.device.above():s.device;var secondary=i==3?s.device.south().above():s.device;
            wire(h,s.source,i<2?0:1,primary,0,i<2);wire(h,s.source,i<2?1:0,primary,1,i<2);
            wire(h,secondary,2,s.load,0,false);wire(h,secondary,i>=4?1:3,s.load,1,true);
            wire(h,s.load,0,s.meter,0,false);wire(h,s.load,1,s.meter,1,true);
        }
        configureMeters(h);
    }
    private void buildMeters(GameTestHelper h) {
        for(int i=0;i<2;i++) {
            var b=meterBases.get(i);put(h,b,i==0?CEEBlocks.CREATIVE_BATTERY.get():ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
            put(h,b.east(3),i==0?ModdedBlocks.ENERGY_METER.get():CEEBlocks.ENERGY_METER.get());
            put(h,b.east(9),ModdedBlocks.CREATIVE_RESISTOR.get());
            put(h,b.offset(9,0,3),i==0?ModdedBlocks.POWER_METER.get():CEEBlocks.VOLTMETER.get());
            put(h,b.offset(5,0,4),i==0?ModdedBlocks.CURRENT_METER.get():CEEBlocks.AMMETER.get());
            if(i==0)put(h,b.south(4),ModdedBlocks.VOLTAGE_METER.get());
            sign(h,b.south(7),i==0?"M1 PG GAUGES":"M2 CEE GAUGES","LIVE mixed supply","10 V / about 1 A","10 W; energy rises");
        }
        var b=new BlockPos(96,64,156);put(h,b,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());put(h,b.east(4),CEEBlocks.FREQUENCY_METER.get());put(h,b.east(8),CEEBlocks.CREATIVE_RESISTOR.get());
        sign(h,b.south(6),"M3 AC FREQUENCY","PG AC -> CEE meter","50 Hz / 100 V peak","AC voltage: RMS");
    }
    private void configureMeters(GameTestHelper h) {
        for(int i=0;i<2;i++) {
            var b=meterBases.get(i);var e=b.east(3);var load=b.east(9);var extra=b.offset(9,0,3);var amp=b.offset(5,0,4);
            resistance(h,load,10);
            if(i==0) {
                scroll(h,b.south(4),1);scroll(h,amp,2);
                scroll(h,b,10000);wire(h,b,1,e,0,false);wire(h,e,1,extra,0,true);wire(h,extra,1,amp,0,false);wire(h,amp,1,load,0,true);
                for(var p:new BlockPos[]{e,extra})wire(h,b,0,p,2,false);wire(h,b,0,load,1,false);wire(h,load,0,b.south(4),0,true);wire(h,load,1,b.south(4),1,false);
            } else {
                pgSource(h,b,10);DevicesSavedData.load(h.getLevel()).getDevice(e,com.george_vi.electroenergetics.content.energy_meter.EnergyMeterDevice.class).isClosed=true;
                wire(h,b,0,e,0,false);wire(h,b,1,e,1,true);wire(h,e,2,amp,0,false);wire(h,amp,1,load,0,true);wire(h,e,3,load,1,false);wire(h,load,0,extra,0,true);wire(h,load,1,extra,1,false);
            }
        }
        var b=new BlockPos(96,64,156);((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)h.getLevel().getBlockEntity(b)).setValue(100,50,0);
        for(var p:new BlockPos[]{b.east(4),b.east(8)}){wire(h,b,0,p,0,false);wire(h,b,1,p,1,true);}
    }
    void verify(GameTestHelper h) {
        for(int i=0;i<transformers.size();i++) {
            var s=transformers.get(i);double actual=((VoltageGaugeBlockEntity)h.getLevel().getBlockEntity(s.meter)).getValue();
            if(i==2||i==3) {
                var be=(TransformerBlockEntity)h.getLevel().getBlockEntity(s.device);h.assertTrue(be.hasPrimary()&&be.hasSecondary(),"Showroom transformer lost its windings");
                double primary=s.medium?40:20,secondary=s.medium?20:40,ratio=secondary/primary,k=be.couplingFactor(),al=be.coreAl();
                double rp=(1-k)*primary*primary*al,rs=(1-k)*secondary*secondary*al,rm=k*primary*primary*al*ModdedConfigs.server().electricity.transformerMutualInductanceMultiplier.getF();
                double reflected=(1000+rs)/(ratio*ratio),parallel=1/(1/rm+1/reflected);
                near(h,actual,s.input*parallel/(rp+parallel)*ratio*1000/(1000+rs),.5,s.name+" native transformer model");
            } else near(h,actual,s.expected,1,s.name+" output gauge");
        }
        var pg=meterBases.get(0);double loadVoltage=((VoltageGaugeBlockEntity)h.getLevel().getBlockEntity(pg.south(4))).getValue();
        near(h,loadVoltage,10,.3,"PG bank voltage including series meter losses");
        double current=((CurrentGaugeBlockEntity)h.getLevel().getBlockEntity(pg.offset(5,0,4))).getValue();
        near(h,current,loadVoltage/10,.001,"PG bank current follows Ohm's law");
        near(h,((PowerGaugeBlockEntity)h.getLevel().getBlockEntity(pg.offset(9,0,3))).getValue(),Math.abs(volts(h,pg.offset(9,0,3),0,2))*current,.01,"PG bank power follows its input V*I, including downstream meter losses");
        h.assertTrue(h.getLevel().getBlockEntity(pg.east(3)).saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("Energy")>0,"PG bank energy did not advance");
        var cee=meterBases.get(1);var vg=(com.george_vi.electroenergetics.content.gauge.ElectricGaugeBlockEntity)h.getLevel().getBlockEntity(cee.offset(9,0,3));
        near(h,vg.voltage,10,.1,"CEE bank voltage");
        near(h,((com.george_vi.electroenergetics.content.gauge.ElectricGaugeBlockEntity)h.getLevel().getBlockEntity(cee.offset(5,0,4))).voltage/.01,1,.02,"CEE bank current");
        h.assertTrue(DevicesSavedData.load(h.getLevel()).getDevice(cee.east(3),com.george_vi.electroenergetics.content.energy_meter.EnergyMeterDevice.class).totalEnergy>0,"CEE bank energy did not advance");
        // CEE estimates crossings within PG's discrete substeps; a 1% reading tolerance
        // covers the interpolation ripple without accepting a wrong-frequency source.
        near(h,h.getLevel().getBlockEntity(new BlockPos(100,64,156)).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("Frequency"),50,.5,"PG AC -> CEE frequency");
    }
    void checkInteractions(GameTestHelper h) {
        var step=transformers.get(0);h.runAtTickTime(190,()->scroll(h,step.device,55));
        h.runAtTickTime(200,()->{near(h,((VoltageGaugeBlockEntity)h.getLevel().getBlockEntity(step.meter)).getValue(),200,1,"CEE transformer adjustment");scroll(h,step.device,29);});
    }
}
