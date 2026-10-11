package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CEEPanelAttachmentTypes;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.GaugePanelAttachment;
import com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Optional classes occur only in a runner loaded after both optional mods exist. */
final class StationaryYardPinout {
    static void build(StationaryYardStations yard) { Runner.station(yard,false);Runner.station(yard,true); }
    private static final class Runner {
        static void station(StationaryYardStations yard,boolean pg) {
            var h=yard.h;var level=h.getLevel();
            var s=yard.station(pg?"PG computer control":"CEE computer control",List.of("pinout:pinout"),
                    "Computer: wwpg start","wwpg stop / demo","8 lamps + board","wwpg volts shows signed pin-to-common readings. Lua common 9 is PG terminal 0; pins 1-8 keep their numbers.");
            var pins=s.device();var pc=pins.west();var panel=pins.south(10);var board=pins.offset(12,0,10);var ballast=board.north(2);
            var lampSupply=board.west(3);
            var running=board.east(3);var stopped=board.east(3).south(3);
            yard.feed(s,pg);yard.block(pins,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pinout:pinout")));
            yard.block(pc,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:computer_normal")));
            yard.state(panel,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.SOUTH));
            yard.block(board,ModdedBlocks.CIRCUIT_BOARD.get());yard.block(ballast,ModdedBlocks.CREATIVE_RESISTOR.get());
            yard.block(lampSupply,CEEBlocks.CREATIVE_BATTERY.get());
            yard.block(running,ModdedBlocks.CEILING_TILE_LAMP.get());yard.state(stopped,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));yard.prepareMeter(s);
            for(int pin=1;pin<=8;pin++)yard.state(load(pins,pin),CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));
            if(!yard.restore) {
                sign(h,pc.south(),"LUA COMMANDS","wwpg start / stop","wwpg demo / volts","wwpg pattern 85");
                sign(h,running.south(),"BOARD RUN LAMP","Pin 3 drives relay","PG lamp on = RUN","Other lamp = OFF");
                sign(h,panel.south(2),"PANEL VOLTMETER","Pin 5 load voltage","70 V ON / 0 V OFF","Goggles: number");
                for(int pin=1;pin<=8;pin++)sign(h,load(pins,pin).above(),"PIN "+pin,"70 V when ON","About 70 mA","Lua controls lamp");
            } else {
                var tag=level.getBlockEntity(pins).saveWithoutMetadata(level.registryAccess());
                for(int pin=1;pin<=8;pin++)h.assertTrue(tag.getBoolean("Pin"+pin),"Yard native pin state lost before Lua or solving: "+pin);
                h.assertTrue(level.getBlockEntity(pc).saveWithoutMetadata(level.registryAccess()).getInt("ComputerId")>=0,"Yard computer lost its native identity");
            }
            var mount=new dan200.computercraft.api.filesystem.WritableMount[1];
            var started=new boolean[]{false};
            h.startSequence().thenWaitUntil(()->{
                for(int x=s.origin().getX()>>4;x<=running.getX()>>4;x++)for(int z=s.origin().getZ()>>4;z<=stopped.getZ()>>4;z++) {
                    var chunk=new net.minecraft.world.level.ChunkPos(x,z);
                    h.assertTrue(level.areEntitiesLoaded(chunk.toLong())&&level.isPositionEntityTicking(chunk.getMiddleBlockPosition(64)),"Waiting for yard computer block/entity readiness "+chunk);
                }
            }).thenIdle(2).thenExecute(()->{
                if(!yard.restore) {
                    yard.source(s.source(),70);((SwitchBlockEntity)level.getBlockEntity(s.control())).setState(true);
                    wire(h,s.source(),pg?0:1,s.control(),0,true);wire(h,s.control(),1,pins,0,false);
                    for(int pin=1;pin<=8;pin++) { wire(h,pins,pin,load(pins,pin),0,pin%2==0);wire(h,s.source(),pg?1:0,load(pins,pin),1,pin%2!=0); }
                    ((CircuitBoardBlockEntity)level.getBlockEntity(board)).setSchematic(ExampleControlBoard.schematic());resistance(h,ballast,500);
                    wire(h,pins,3,ballast,0,true);wire(h,ballast,1,board,ExampleControlBoard.COIL_POSITIVE,false);wire(h,s.source(),pg?1:0,board,ExampleControlBoard.COIL_RETURN,true);
                    yard.source(lampSupply,120);wire(h,lampSupply,1,board,ExampleControlBoard.LAMP_FEED,true);wire(h,lampSupply,0,board,ExampleControlBoard.COIL_RETURN,false);
                    wire(h,board,ExampleControlBoard.RUN_LAMP,running,0,false);wire(h,board,ExampleControlBoard.OFF_LAMP,stopped,0,true);
                    wire(h,board,ExampleControlBoard.COIL_RETURN,running,1,true);wire(h,board,ExampleControlBoard.COIL_RETURN,stopped,1,false);
                    var player=h.makeMockPlayer(GameType.SURVIVAL);var bulb=ModdedItems.LIGHT_BULB.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,bulb);
                    h.assertTrue(((org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity)level.getBlockEntity(running)).replaceBulb(player,InteractionHand.MAIN_HAND,bulb)&&bulb.isEmpty(),"Pinout board load rejected native bulb");
                    var type=CEEPanelAttachmentTypes.VOLTMETER.get();var gauge=type.createNew(panel,type.mode.getNodesFor(panel,ElectricalPanelSlot.THIRD_CENTERED),level,ElectricalPanelSlot.THIRD_CENTERED,Direction.SOUTH,level.registryAccess());gauge.label="Pin 5 load voltage";
                    var be=(ElectricalPanelBlockEntity)level.getBlockEntity(panel);be.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]=gauge;be.attachmentUpdate();
                    wire(h,load(pins,5),0,panel,gauge.nodes[0].id(),false);wire(h,load(pins,5),1,panel,gauge.nodes[1].id(),true);yard.blockMeter(s,load(pins,1),0,1,70);
                }
                var computer=((dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity)level.getBlockEntity(pc)).createServerComputer();mount[0]=computer.createRootMount();
                if(!yard.restore)for(var program:List.of("wwpg.lua","startup.lua")) {
                    try(var source=StationaryYardPinout.class.getResourceAsStream("/data/wwpg_pinout/programs/yard/"+program)) {
                        if(source==null)throw new java.io.IOException("Missing playable Lua "+program);
                        try(var channel=mount[0].openFile(program,Set.of(StandardOpenOption.WRITE,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING))) {
                            var bytes=ByteBuffer.wrap(source.readAllBytes());while(bytes.hasRemaining())channel.write(bytes);
                        }
                    }catch(java.io.IOException error){throw new IllegalStateException(error);}
                }
                try{if(mount[0].exists("yard-ready.json"))mount[0].delete("yard-ready.json");}catch(java.io.IOException error){throw new IllegalStateException(error);}
                computer.turnOn();started[0]=true;
            });
            yard.check(s,()->{
                h.assertTrue(started[0],"Playable Lua computer has not started");
                try {
                    h.assertTrue(mount[0].exists("yard-ready.json"),"Playable Lua startup has not finished");
                    try(var channel=mount[0].openForRead("yard-ready.json")) {
                        var bytes=ByteBuffer.allocate((int)mount[0].getSize("yard-ready.json"));while(bytes.hasRemaining()&&channel.read(bytes)!=-1){}
                        var report=com.google.gson.JsonParser.parseString(new String(bytes.array(),StandardCharsets.UTF_8)).getAsJsonObject();h.assertTrue(report.get("ok").getAsBoolean(),"Playable native Lua program failed: "+report);
                    }
                }catch(java.io.IOException error){throw new IllegalStateException(error);}
                double common=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pins,0).getNode(level).getVoltage()
                        -new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(s.source(),pg?1:0).getNode(level).getVoltage();
                double contact=org.patryk3211.powergrid.config.ResistanceValues.get(ModdedBlocks.PUNCH_CARD_READER.get());
                double coilCurrent=volts(h,ballast,0,1)/500;
                for(int pin=1;pin<=8;pin++) {
                    double expected=(common-contact*(pin==3?coilCurrent:0))/(1+contact/1000);
                    near(h,volts(h,load(pins,pin),0,1),expected,expected*.001,"Playable native pin "+pin+" with native contact resistance");
                }
                var gauge=(GaugePanelAttachment)((ElectricalPanelBlockEntity)level.getBlockEntity(panel)).getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()];near(h,gauge.value,70,.15,"Pinout panel output");
                h.assertTrue(((org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity)level.getBlockEntity(running)).getPowerLevel()>0,"Computer-controlled PG board lamp is dark: coil="+volts(h,board,0,1)+", lamp="+volts(h,running,0,1));
            });
        }
        private static BlockPos load(BlockPos pins,int pin) { return pins.offset(5+((pin-1)%2)*4,0,((pin-1)/2)*2); }
    }
}
