package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.components.RelayComponent;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** A small saved world built through the same pinned native workflows as acceptance. */
@GameTestHolder("wwpg_example")
@PrefixGameTestTemplate(false)
public final class ExampleWorldGameTests {
    private static final BlockPos PANEL = new BlockPos(12,64,8), BOARD = PANEL.east(2);
    private static final BlockPos LAMP_SOURCE = new BlockPos(18,64,8), RUN_LAMP = LAMP_SOURCE.south(3), OFF_LAMP = LAMP_SOURCE.north(3);
    @GameTest(template="empty",timeoutTicks=650) public static void buildAndVerifyMixedExampleWorld(GameTestHelper h){
        demonstrate(h, () -> {});
    }
    static void demonstrate(GameTestHelper h, Runnable additionalChecks){
        var level=h.getLevel();level.setChunkForced(0,0,true);level.setChunkForced(0,1,true);level.getChunk(0,0);level.getChunk(0,1);
        var s=new BlockPos(8,64,8);var r=s.east(2);var p=PANEL;var b=BOARD;
        var factory=new BlockPos(8,64,24);var lamp=factory.east(3);var heater=factory.east(6);var pump=factory.offset(3,0,4);var input=pump.west(4);var output=pump.east(4);
        var heaterSource=heater.south(2);
        var tools=new BlockPos(6,64,16);
        boolean verify=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        if(!verify){
        for(int x=3;x<=22;x++)for(int z=3;z<=33;z++)level.setBlockAndUpdate(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState());
        for(var pos:new BlockPos[]{s,r,p,b,b.south(3),LAMP_SOURCE,RUN_LAMP,OFF_LAMP,factory,lamp,heater,pump,input,output,heaterSource})level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(s,CEEBlocks.CREATIVE_BATTERY.getDefaultState());level.setBlockAndUpdate(r,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        level.setBlockAndUpdate(p,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.SOUTH));level.setBlockAndUpdate(b,ModdedBlocks.CIRCUIT_BOARD.getDefaultState());
        level.setBlockAndUpdate(LAMP_SOURCE,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        level.setBlockAndUpdate(RUN_LAMP.below(),Blocks.LIME_CONCRETE.defaultBlockState());level.setBlockAndUpdate(OFF_LAMP.below(),Blocks.RED_CONCRETE.defaultBlockState());
        level.setBlockAndUpdate(RUN_LAMP,CEEBlocks.BULB.getDefaultState());level.setBlockAndUpdate(OFF_LAMP,CEEBlocks.BULB.getDefaultState());
        level.setBlockAndUpdate(factory,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());level.setBlockAndUpdate(lamp,CEEBlocks.BULB.getDefaultState());level.setBlockAndUpdate(heater,CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        level.setBlockAndUpdate(heaterSource,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        level.setBlockAndUpdate(heater.above(),AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(tools,Blocks.CHEST.defaultBlockState());
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(tools);
        chest.setItem(0,org.patryk3211.powergrid.collections.ModdedItems.MULTIMETER.asStack());
        chest.setItem(1,com.george_vi.electroenergetics.CEEItems.CLAMP_METER.asStack());
        chest.setItem(2,org.patryk3211.powergrid.collections.ModdedItems.WIRE.asStack(64));
        chest.setItem(3,com.george_vi.electroenergetics.CEEItems.WIRE_SPOOL.asStack());
        chest.setItem(4,AllItems.GOGGLES.asStack());
        chest.setChanged();
        sign(h,p.south(2),"PANEL SWITCH","Right-click with","an empty hand","Factory enable");
        sign(h,b.south(2),"PG CIRCUIT BOARD","Relay powers lamps","Capacitor holds ON","~3 seconds at OFF");
        sign(h,RUN_LAMP.east(2),"GREEN: RUNNING","ON = lamp lit","OFF = brief delay","then lamp goes out");
        sign(h,OFF_LAMP.east(2),"RED: OFF","Lights after the","capacitor discharges","ON turns it off");
        level.setBlockAndUpdate(pump,CEEBlocks.ELECTRIC_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(input,AllBlocks.FLUID_TANK.getDefaultState());level.setBlockAndUpdate(output,AllBlocks.FLUID_TANK.getDefaultState());
        for(int dx=-3;dx<=3;dx++){if(dx==0)continue;level.setBlockAndUpdate(pump.offset(dx,0,0),AllBlocks.FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.DOWN,false).setValue(BlockStateProperties.UP,false).setValue(BlockStateProperties.NORTH,false).setValue(BlockStateProperties.SOUTH,false).setValue(BlockStateProperties.EAST,true).setValue(BlockStateProperties.WEST,true));}
        h.runAtTickTime(10,()->{
            ((SmartBlockEntity)level.getBlockEntity(s)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(20_000);((SmartBlockEntity)level.getBlockEntity(r)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(36);
            ((SmartBlockEntity)level.getBlockEntity(LAMP_SOURCE)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(253);
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(p);
            var control=CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get().createNew(p,CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get().mode.getNodesFor(p,ElectricalPanelSlot.THIRD_LEFT),level,ElectricalPanelSlot.THIRD_LEFT,Direction.SOUTH,level.registryAccess());control.label="Factory enable";((CutOffSwitchPanelAttachment)control).isClosed=true;
            var meter=CEEPanelAttachmentTypes.VOLTMETER.get().createNew(p,CEEPanelAttachmentTypes.VOLTMETER.get().mode.getNodesFor(p,ElectricalPanelSlot.THIRD_CENTERED),level,ElectricalPanelSlot.THIRD_CENTERED,Direction.SOUTH,level.registryAccess());meter.label="Board control voltage";
            var ammeter=CEEPanelAttachmentTypes.AMMETER.get().createNew(p,CEEPanelAttachmentTypes.AMMETER.get().mode.getNodesFor(p,ElectricalPanelSlot.THIRD_RIGHT),level,ElectricalPanelSlot.THIRD_RIGHT,Direction.SOUTH,level.registryAccess());ammeter.label="Board coil current";
            panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()]=control;panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]=meter;panel.getAttachments()[ElectricalPanelSlot.THIRD_RIGHT.ordinal()]=ammeter;panel.attachmentUpdate();
            var board=(CircuitBoardBlockEntity)level.getBlockEntity(b);board.setSchematic(ExampleControlBoard.schematic());
            h.assertTrue(board.terminalCount()==5&&board.getSchematic().findNodeBundles().stream().map(java.util.Collection::size).sorted().toList().equals(java.util.List.of(2,2,2,3,3)),"Example board must keep coil and lamp-contact traces separate");
            wire(h,s,1,r,0,true);wire(h,r,1,p,control.nodes[0].id(),false);wire(h,p,control.nodes[1].id(),p,ammeter.nodes[0].id(),true);wire(h,p,ammeter.nodes[1].id(),b,ExampleControlBoard.COIL_POSITIVE,false);wire(h,s,0,b,ExampleControlBoard.COIL_RETURN,false);
            wire(h,b,ExampleControlBoard.COIL_POSITIVE,p,meter.nodes[0].id(),true);wire(h,b,ExampleControlBoard.COIL_RETURN,p,meter.nodes[1].id(),false);
            wire(h,LAMP_SOURCE,0,b,ExampleControlBoard.LAMP_FEED,true);wire(h,LAMP_SOURCE,1,s,0,false);
            wire(h,b,ExampleControlBoard.RUN_LAMP,RUN_LAMP,0,false);wire(h,b,ExampleControlBoard.OFF_LAMP,OFF_LAMP,0,true);
            wire(h,LAMP_SOURCE,1,RUN_LAMP,1,true);wire(h,LAMP_SOURCE,1,OFF_LAMP,1,false);
            // PG's high-range scroll indices encode (index - 250) * 100 volts.
            ((SmartBlockEntity)level.getBlockEntity(factory)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(253);
            for(var pos:new BlockPos[]{lamp,pump})for(int port=0;port<2;port++)wire(h,factory,port,pos,port,true);
            // 300 V only makes this 45-ohm heater warm. A separate 600 V feed
            // reaches Create's KINDLED heat level without overvolting the lamp.
            ((SmartBlockEntity)level.getBlockEntity(heaterSource)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(256);
            for(int port=0;port<2;port++)wire(h,heaterSource,port,heater,port,true);
            var tank=level.getCapability(Capabilities.FluidHandler.BLOCK,input,Direction.EAST);h.assertTrue(tank!=null&&tank.fill(new FluidStack(Fluids.WATER,8000),IFluidHandler.FluidAction.EXECUTE)==8000,"Example water tank failed");
        });
        }else{
            h.assertTrue(level.getBlockState(s).is(CEEBlocks.CREATIVE_BATTERY.get())&&level.getBlockState(factory).is(ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()),"Example source blocks were not saved");
            var board=(CircuitBoardBlockEntity)level.getBlockEntity(b);
            var savedCapacitor=board.getComponentsStream().filter(c->c.component==Components.CAPACITOR.get()).findFirst().orElseThrow();
            h.assertTrue(savedCapacitor.serializeNbt(level.registryAccess()).getCompound("Properties").getFloat("powergrid:charge")>18,"Example restart lost the saved capacitor property before recharging");
            h.runAtTickTime(30,()->{h.assertTrue(capacitorVoltage(h)>18,"Example restart lost stored energy");assertLamps(h,true);});
        }
        h.startSequence().thenIdle(240).thenWaitUntil(()->{
            double v=new BlockWireEndpoint(b,0).getNode(level).getVoltage()-new BlockWireEndpoint(b,1).getNode(level).getVoltage();BoardComponentGameTests.near(h,v,20*120/130d,.2,"Example relay control voltage");
            assertLamps(h,true);
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(p);
            BoardComponentGameTests.near(h,((GaugePanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]).value,v,.1,"Panel reads board control voltage");
            BoardComponentGameTests.near(h,((GaugePanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_RIGHT.ordinal()]).value,.154,.015,"Panel reads board coil current");
            h.assertTrue(level.getBlockState(lamp).getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)>0,"Example mixed lamp is unpowered");
            var heaterEntity=(com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity)level.getBlockEntity(heater);
            h.assertTrue(heaterEntity.heat>=.6&&level.getBlockState(heater).getValue(com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlock.HEAT_LEVEL).isAtLeast(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED),"Example heater did not reach usable burner heat: voltage="+heaterEntity.voltage+", heat="+heaterEntity.heat);
            var tank=level.getCapability(Capabilities.FluidHandler.BLOCK,output,Direction.EAST);h.assertTrue(tank!=null&&tank.getFluidInTank(0).getAmount()>0,"Example pump did not transfer water");
            h.assertTrue(level.getBlockEntity(tools) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest&&chest.getItem(0).is(org.patryk3211.powergrid.collections.ModdedItems.MULTIMETER.get())&&chest.getItem(1).is(com.george_vi.electroenergetics.CEEItems.CLAMP_METER.get())&&chest.getItem(4).is(AllItems.GOGGLES.get()),"Example meters or goggles were not saved");
            h.assertTrue(level.getBlockEntity(p.south(2)) instanceof SignBlockEntity sign&&sign.getFrontText().getMessage(0,false).getString().equals("PANEL SWITCH"),"Example instructions were not saved");
        }).thenExecute(()->togglePanel(h)).thenIdle(10).thenExecute(()->{
            assertLamps(h,true);h.assertTrue(capacitorVoltage(h)>11,"Board capacitor did not keep the relay energized after switch-off");
        }).thenIdle(100).thenExecute(()->{
            assertLamps(h,false);h.assertTrue(capacitorVoltage(h)<10.8,"Board capacitor did not discharge");
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(p);
            BoardComponentGameTests.near(h,((GaugePanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_RIGHT.ordinal()]).value,0,.001,"Panel input current after switch-off");
            togglePanel(h);
        }).thenIdle(40).thenWaitUntil(()->{assertLamps(h,true);h.assertTrue(capacitorVoltage(h)>18,"Example did not recharge after switching on");})
          .thenExecute(()->{cleanDroppedWireItems(h);additionalChecks.run();org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("WWPG_EXAMPLE_VISIBLE_PASSED: panel on -> RUN; off -> capacitor delay -> OFF; on -> RUN");level.setDefaultSpawnPos(new BlockPos(8,64,16),180);level.getServer().getWorldData().setGameType(GameType.CREATIVE);DynamicGameTests.audit(h);level.getDataStorage().save();level.getChunkSource().save(true);}).thenSucceed();
    }
    private static boolean droppedWire(net.minecraft.world.entity.item.ItemEntity entity) {
        var item=entity.getItem().getItem();
        return item instanceof org.patryk3211.powergrid.electricity.wire.WireItem
                || item instanceof com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem
                || item instanceof com.george_vi.electroenergetics.content.wire_spool.EmptySpoolItem
                || item instanceof com.george_vi.electroenergetics.content.bundled_wire.BundledWireItem
                || entity.getItem().is(com.george_vi.electroenergetics.CEEItems.INSULATED_WIRE.get())
                || entity.getItem().is(com.george_vi.electroenergetics.CEEItems.HEAVILY_INSULATED_WIRE.get())
                || entity.getItem().is(com.george_vi.electroenergetics.CEEItems.COPPER_WIRE.get())
                || entity.getItem().is(com.george_vi.electroenergetics.CEEItems.ELECTRUM_WIRE.get())
                || entity.getItem().is(com.george_vi.electroenergetics.CEEItems.IRON_WIRE.get());
    }
    static void cleanDroppedWireItems(GameTestHelper h) {
        var bounds=new net.minecraft.world.phys.AABB(0,-64,0,130,320,242);
        var level=h.getLevel();
        var connections=level.getEntitiesOfClass(org.patryk3211.powergrid.electricity.wire.BaseWireEntity.class,bounds).size();
        var loose=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds,ExampleWorldGameTests::droppedWire);
        loose.forEach(net.minecraft.world.entity.Entity::discard);
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds,ExampleWorldGameTests::droppedWire).isEmpty(),"Example retained dropped wire items");
        h.assertTrue(level.getEntitiesOfClass(org.patryk3211.powergrid.electricity.wire.BaseWireEntity.class,bounds).size()==connections,"Example cleanup removed a connected wire entity");
        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("WWPG_EXAMPLE_CLEANUP: removed {} loose wire/spool items; {} connected PG wires/cords retained",loose.size(),connections);
    }
    private static void wire(GameTestHelper h,BlockPos a,int ta,BlockPos b,int tb,boolean pg){WiringGameTests.connect(h,a,ta,b,tb,pg);}
    private static void togglePanel(GameTestHelper h){
        var panel=(ElectricalPanelBlockEntity)h.getLevel().getBlockEntity(PANEL);
        var control=(CutOffSwitchPanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
        control.onInteract(ItemStack.EMPTY,h.makeMockPlayer(GameType.SURVIVAL),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(PANEL),Direction.SOUTH,PANEL,false));
    }
    private static double capacitorVoltage(GameTestHelper h){
        var board=(CircuitBoardBlockEntity)h.getLevel().getBlockEntity(BOARD);
        var capacitor=board.getComponentsStream().filter(c->c.component==Components.CAPACITOR.get()).findFirst().orElseThrow();
        return ((CRSeriesWire)capacitor.wires.getFirst()).capacitorVoltage();
    }
    private static void assertLamps(GameTestHelper h,boolean running){
        var level=h.getLevel();
        h.assertTrue(level.getBlockState(RUN_LAMP).getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)==(running?15:0),"RUN lamp disagrees with actual board output");
        h.assertTrue(level.getBlockState(OFF_LAMP).getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)==(running?0:15),"OFF lamp disagrees with actual board output");
        var board=(CircuitBoardBlockEntity)level.getBlockEntity(BOARD);
        var relay=board.getComponentsStream().filter(c->c.component==Components.RELAY.get()).findFirst().orElseThrow();
        h.assertTrue(!relay.destroyed&&relay.get(RelayComponent.STATE)==running,"Native PG relay state disagrees with the lamps");
    }
    private static void sign(GameTestHelper h,BlockPos at,String... lines){
        h.getLevel().setBlockAndUpdate(at,Blocks.OAK_SIGN.defaultBlockState());
        var sign=(SignBlockEntity)h.getLevel().getBlockEntity(at);
        var text=sign.getFrontText().setColor(DyeColor.BLACK);
        for(int line=0;line<4;line++)text=text.setMessage(line,Component.literal(lines[line]));
        sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.setChanged();
    }
}
