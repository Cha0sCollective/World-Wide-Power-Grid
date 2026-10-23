package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.light.string.StringLightCordEntity;
import org.patryk3211.powergrid.electricity.light.string.StringLightCordRecipe;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Native cords, grounded circuits, saved designs and visible fluid transfer. */
final class StationaryYardConnections {
    private final StationaryYardStations yard;
    StationaryYardConnections(StationaryYardStations yard) { this.yard=yard; }
    void build() { junction(false); junction(true); socket(); grounds(); diode(); stringLights(); designer(); resistor(); pump(); catenary(); }

    private void close(StationaryYardStations.Station s,double voltage,boolean pg) {
        yard.source(s.source(),voltage);
        ((SwitchBlockEntity)yard.h.getLevel().getBlockEntity(s.control())).setState(true);
        wire(yard.h,s.source(),pg?0:1,s.control(),0,true);
    }
    private BlockPos socketFeed(StationaryYardStations.Station s,double voltage) {
        var socket=s.device().west(2);
        yard.block(socket,ModdedBlocks.SOCKET.get());
        close(s,voltage,false);
        wire(yard.h,s.control(),1,socket,0,false);wire(yard.h,s.source(),0,socket,1,true);
        return socket;
    }
    private void junction(boolean ceiling) {
        var h=yard.h;var block=ceiling?ModdedBlocks.CEILING_TILE_JUNCTION.get():ModdedBlocks.CORD_JUNCTION.get();
        var s=yard.station(ceiling?"Ceiling cord junction":"Cord junction",List.of(ceiling?"powergrid:ceiling_tile_junction":"powergrid:cord_junction"),
                "Cord carries 70 V","Feed: lamp ON/OFF","Unplug / reconnect","Use the PG cord and socket, then select both exposed load terminals.");
        yard.feed(s,false);yard.block(s.device(),block);yard.block(s.device().west(2),ModdedBlocks.SOCKET.get());
        var load=s.device().east(4);yard.state(load,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));yard.prepareMeter(s);
        yard.configure.add(()->{
            var socket=socketFeed(s,70);NativeInteractions.connectCord(h,socket,0,1,s.device());
            NativeInteractions.connectJunctionToSplit(h,s.device(),load);yard.blockMeter(s,load,0,1,70);
        });
        yard.check(s,()->near(h,volts(h,load,0,1),70,.1,"Native cord junction output"));
    }
    private void socket() {
        var h=yard.h;var s=yard.station("Socket and split cord",List.of("powergrid:socket"),
                "70 V lights lamp","Empty hand: unplug","Cord: reconnect","The socket's two visible terminals keep their native polarity and hidden cord endpoints.");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.SOCKET.get());
        var load=s.device().east(4);yard.state(load,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));yard.prepareMeter(s);
        yard.configure.add(()->{
            close(s,70,false);wire(h,s.control(),1,s.device(),0,false);wire(h,s.source(),0,s.device(),1,true);
            NativeInteractions.connectJunctionToSplit(h,s.device(),load);yard.blockMeter(s,load,0,1,70);
        });
        yard.check(s,()->near(h,volts(h,load,0,1),70,.1,"Native socket output"));
    }
    private void grounds() {
        var h=yard.h;var s=yard.station("Both physical grounds",List.of("electroenergetics:ground_rod","powergrid:grounding_rod"),
                "Load: 10 V / 0.1 A","Rods set reference","Change soil / rewire","Removing one rod changes voltage relative to earth; the load keeps its 10 V supply.");
        var cg=s.device().north(3);var pg=s.device().south(3);
        yard.feed(s,true);yard.block(s.device(),CEEBlocks.CREATIVE_RESISTOR.get());yard.block(cg,CEEBlocks.GROUND_ROD.get());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int depth=1;depth<=2;depth++)yard.block(pg.offset(x,-depth,z),Blocks.STONE);
        yard.block(pg,ModdedBlocks.GROUNDING_ROD.get());yard.prepareMeter(s);
        yard.configure.add(()->{
            close(s,10,true);((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(s.device())).setResistance(100);
            wire(h,s.control(),1,s.device(),0,false);wire(h,s.source(),1,s.device(),1,true);
            wire(h,s.source(),1,cg,0,true);wire(h,s.control(),1,pg,0,false);yard.blockMeter(s,s.device(),0,1,10);
        });
        yard.check(s,()->{
            near(h,volts(h,s.device(),0,1),10,.02,"Grounded load voltage");
            double resistance=h.getLevel().getBlockEntity(pg).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("Resistance");
            h.assertTrue(resistance>0,"Native grounding soil scan is inactive");
            near(h,new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pg,0).getNode(h.getLevel()).getVoltage(),10*resistance/(resistance+1),.03,"Native physical ground reference");
        });
    }
    private void diode() {
        var h=yard.h;var s=yard.station("CEE diode polarity",List.of("electroenergetics:diode"),
                "Forward: lamp ON","Load: about 68.86 V","Reverse wires: OFF","Reconnect the supply with its original polarity to reset. CEE 1.1.3 clamps its diode linearisation at 0.8 V; its loaded drop is about 1.14 V here.");
        yard.feed(s,true);yard.block(s.device(),CEEBlocks.DIODE.get());
        var load=s.device().east(4);yard.state(load,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));yard.prepareMeter(s);
        yard.configure.add(()->{
            close(s,70,true);wire(h,s.control(),1,s.device(),1,false);wire(h,s.device(),0,load,0,true);wire(h,s.source(),1,load,1,false);yard.blockMeter(s,load,0,1,70);
        });
        yard.check(s,()->{
            double g=(1e-9/.05)*Math.exp(.8/.05);double clampedCurrent=1e-9*(Math.exp(.8/.05)-1);
            double expected=1000*(70-.8+clampedCurrent/g)/(1000+1/g);
            near(h,volts(h,load,0,1),expected,expected*.001,"Native clamped diode forward voltage");
        });
    }
    private void stringLights() {
        var h=yard.h;var s=yard.station("Dyed string lights",List.of("powergrid:string_light_block"),
                "120 V: red / blue","Feed OFF: cools","Cutter returns cord","Native dye recipe, item cost, filament heat and light blocks are retained.");
        yard.feed(s,false);yard.block(s.device().west(2),ModdedBlocks.SOCKET.get());yard.block(s.device(),ModdedBlocks.CREATIVE_RESISTOR.get());yard.prepareMeter(s);
        yard.configure.add(()->{
            var socket=socketFeed(s,120);resistance(h,s.device(),1000);
            var recipe=new StringLightCordRecipe(CraftingBookCategory.MISC);
            var cord=recipe.assemble(CraftingInput.of(3,1,List.of(ModdedItems.STRING_LIGHT_CORD.asStack(),Items.RED_DYE.getDefaultInstance(),Items.BLUE_DYE.getDefaultInstance())),h.getLevel().registryAccess());cord.setCount(64);
            h.assertTrue(NativeInteractions.connectSplitCord(h,socket,0,1,s.device(),0,1,cord)>0,"String-light cord consumed no items");yard.blockMeter(s,s.device(),0,1,120);
        });
        yard.check(s,()->{
            var wires=h.getLevel().getEntitiesOfClass(StringLightCordEntity.class,new net.minecraft.world.phys.AABB(s.device()).inflate(5));
            h.assertTrue(wires.size()==1,"Native string-light cord is missing or duplicated");
            var tag=new net.minecraft.nbt.CompoundTag();wires.getFirst().saveWithoutId(tag);
            h.assertTrue(tag.getFloat("Filament")>600&&tag.getIntArray("Pattern").length==2,"String lights are cold or lost their dyes");
            boolean lit=false;for(int x=-2;x<=0;x++)for(int y=-1;y<=0;y++)lit|=ModdedBlocks.STRING_LIGHT_BLOCK.has(h.getLevel().getBlockState(s.device().offset(x,y,0)));
            h.assertTrue(lit,"String lights created no visible light blocks");
        });
    }
    private void designer() {
        var h=yard.h;var s=yard.station("Powered saved design",List.of("powergrid:circuit_design_table"),
                "Open powered table","Saved via + label","Board: 10 V / 10 mA","Edit, save, copy and assemble with native PG items. The supplied board is made from this table's saved design.");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.CIRCUIT_DESIGN_TABLE.get());yard.block(s.device().west(2),ModdedBlocks.SOCKET.get());yard.prepareMeter(s);
        var board=s.device().east(4);var boardSupply=board.north(3);yard.block(boardSupply,CEEBlocks.CREATIVE_BATTERY.get());
        yard.configure.add(()->{
            var socket=socketFeed(s,Math.sqrt(50*org.patryk3211.powergrid.config.ResistanceValues.get(ModdedBlocks.CIRCUIT_DESIGN_TABLE.get())));
            NativeInteractions.connectCord(h,socket,0,1,s.device());
            var table=(CircuitDesignTableBlockEntity)h.getLevel().getBlockEntity(s.device());table.setSchematic(BoardWorkflowGameTests.viaLabelBoard());table.getInventory().setItem(1,ModdedItems.CIRCUIT_SCHEMATIC.asStack(64));
        });
        if(!yard.restore)h.runAtTickTime(60,()->{
            var table=(CircuitDesignTableBlockEntity)h.getLevel().getBlockEntity(s.device());h.assertTrue(table.isPowered(),"Yard designer is not powered");table.writeToItem(false);
            var schematic=CircuitSchematic.fromStack(h.getLevel().registryAccess(),table.getInventory().getItem(2));
            var data=new net.minecraft.nbt.CompoundTag();data.put("Schematic",schematic.serializeNbt(h.getLevel().registryAccess()));
            var stack=ModdedItems.INCOMPLETE_CIRCUIT.asStack();stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            for(var part:schematic.components()) {
                var item=org.patryk3211.powergrid.circuits.components.ComponentRegistry.getItem(h.getLevel(),part.component);
                var assembled=org.patryk3211.powergrid.circuits.circuitboard.IncompleteCircuitItem.insert(h.getLevel(),stack,new net.minecraft.world.item.ItemStack(item));
                h.assertTrue(assembled!=null,"Native yard assembly rejected "+part.component);stack=assembled;
            }
            h.assertTrue(stack.is(ModdedBlocks.CIRCUIT_BOARD.get().asItem()),"Native yard component assembly did not complete the board");
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            h.assertTrue(stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(board.below().getCenter().add(0,.5,0),Direction.UP,board.below(),false))).consumesAction()&&stack.isEmpty(),"Saved design board placement failed");
            yard.source(boardSupply,10);wire(h,boardSupply,1,board,0,false);wire(h,boardSupply,0,board,1,true);yard.blockMeter(s,board,0,1,10);
        });
        yard.check(s,()->{
            var table=(CircuitDesignTableBlockEntity)h.getLevel().getBlockEntity(s.device());h.assertTrue(table.isPowered()&&!table.getInventory().getItem(2).isEmpty(),"Native designer lost its saved output or power");
            var parts=((org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity)h.getLevel().getBlockEntity(board)).getComponentsStream().toList();
            h.assertTrue(parts.size()==5,"Assembled design lost its native components");near(h,volts(h,board,0,1),10,.01,"Saved design board voltage");
        });
    }
    private void resistor() {
        var h=yard.h;var s=yard.station("Native power resistor",List.of("powergrid:power_resistor"),
                "70 V / 70 mA","Scroll: 1000 ohms","Feed OFF: zero amps","Change native resistance and compare V / R against your handheld readings.");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.RESISTOR.get());yard.prepareMeter(s);
        yard.configure.add(()->{resistance(h,s.device(),1000);yard.switchedPair(s,s.device(),70,false);});
        yard.check(s,()->near(h,volts(h,s.device(),0,1)/((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(s.device())).getValue(),.07,.0001,"Native power resistor current"));
    }
    private void pump() {
        var h=yard.h;var s=yard.station("PG water transfer",List.of("powergrid:electric_pump"),
                "Feed pumps water","North -> south tank","Refill north to reset","Use buckets to empty the south tank and refill the north tank. No power means no pumping.");
        yard.feed(s,false);yard.state(s.device(),ModdedBlocks.ELECTRIC_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.SOUTH));yard.prepareMeter(s);
        var terminal=s.device().above();yard.state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING,Direction.DOWN));
        var input=s.device().north(4);var output=s.device().south(4);yard.block(input,AllBlocks.FLUID_TANK.get());yard.block(output,AllBlocks.FLUID_TANK.get());
        for(int dz=-3;dz<=3;dz++)if(dz!=0)yard.state(s.device().offset(0,0,dz),AllBlocks.FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.NORTH,true).setValue(BlockStateProperties.SOUTH,true).setValue(BlockStateProperties.EAST,false).setValue(BlockStateProperties.WEST,false).setValue(BlockStateProperties.UP,false).setValue(BlockStateProperties.DOWN,false));
        yard.configure.add(()->{
            var pump=(org.patryk3211.powergrid.electricity.pump.ElectricPumpBlockEntity)h.getLevel().getBlockEntity(s.device());yard.switchedPair(s,terminal,Math.sqrt(pump.resistance()*100),false);
            var tank=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,input,Direction.SOUTH);h.assertTrue(tank!=null&&tank.fill(new FluidStack(Fluids.WATER,8000),IFluidHandler.FluidAction.EXECUTE)==8000,"Native pump input rejected water");
        });
        yard.check(s,()->{
            h.assertTrue(((org.patryk3211.powergrid.electricity.pump.ElectricPumpBlockEntity)h.getLevel().getBlockEntity(s.device())).getSpeed()>0,"PG pump has no solved power");
            var tank=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,output,Direction.NORTH);h.assertTrue(tank!=null&&tank.getFluidInTank(0).getAmount()>0,"PG pump transferred no water");
        });
    }
    private void catenary() {
        var h=yard.h;var s=yard.station("Stationary catenary",List.of("electroenergetics:catenary_holder"),
                "40 V: lamp lit","Wrench: four styles","Empty spool: recover","Copper spool reconnects the holders. Moving trains remain outside this release.");
        yard.feed(s,true);yard.prepareMeter(s);var first=s.device().above(3);var second=first.east(6);var load=s.device().south(3);
        yard.state(load,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));
        var nativeParts=new StationaryYardDistribution(yard);
        for(var bottom:List.of(first.west(2).below(2),second.east().below(2))) {
            yard.block(bottom.below(),Blocks.STONE);for(int segment=0;segment<3;segment++)nativeParts.placeItem(bottom.above(segment).below(),Direction.UP,CEEBlocks.CONCRETE_POLE.asStack(),0);
        }
        for(var holder:List.of(first,second)){yard.block(holder.below(),Blocks.STONE);nativeParts.placeItem(holder.below(),Direction.UP,CEEBlocks.CATENARY_HOLDER.asStack(),0);}
        yard.configure.add(()->{
            close(s,40,true);wire(h,s.control(),1,first,0,true);wire(h,second,0,load,0,true);wire(h,s.source(),1,load,1,false);
            var player=h.makeMockPlayer(GameType.SURVIVAL);var spool=CEEItems.COPPER_WIRE_SPOOL.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,spool);
            for(var holder:List.of(first,second))h.assertTrue(spool.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,holder).getPosition(h.getLevel()),Direction.UP,holder,false))).consumesAction(),"Native catenary spool placement failed");
            h.assertTrue(spool.isEmpty(),"Catenary spool was not consumed");yard.blockMeter(s,load,0,1,40);
        });
        yard.check(s,()->{
            near(h,volts(h,load,0,1),40,.04,"Native stationary catenary voltage");
            for(var holder:List.of(first,second))h.assertTrue(((com.george_vi.electroenergetics.content.railway_electrification.catenary.CatenaryHolderBlockEntity)h.getLevel().getBlockEntity(holder)).getAttachedTo()!=null,"Catenary is not mounted on a native pole");
        });
    }
}
