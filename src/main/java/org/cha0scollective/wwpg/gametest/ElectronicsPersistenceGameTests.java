package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.CapacitorComponent;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class ElectronicsPersistenceGameTests {
    private static final BlockPos SOURCE=new BlockPos(1536,64,1536),BALLAST=SOURCE.offset(2,0,0),PANEL=SOURCE.offset(4,0,0),BOARD=SOURCE.offset(6,0,0),LOAD=SOURCE.offset(6,0,3);
    @GameTest(template="empty",timeoutTicks=260) public static void restartRetainsPanelTerminalsBoardIdentityAndCharge(GameTestHelper h){
        var level=h.getLevel();level.setChunkForced(96,96,true);level.getChunk(96,96);
        boolean verify=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        if(!verify){
            for(var p:new BlockPos[]{SOURCE,BALLAST,PANEL,BOARD,LOAD}){level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());}
            level.setBlockAndUpdate(SOURCE,CEEBlocks.CREATIVE_BATTERY.getDefaultState());level.setBlockAndUpdate(BALLAST,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
            level.setBlockAndUpdate(PANEL,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.NORTH));
            level.setBlockAndUpdate(BOARD,ModdedBlocks.CIRCUIT_BOARD.getDefaultState());level.setBlockAndUpdate(LOAD,CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
            h.runAtTickTime(10,()->{
                ((SmartBlockEntity)level.getBlockEntity(SOURCE)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(10_000); // native millivolts
                ((ResistorBlockEntity)level.getBlockEntity(BALLAST)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(36); // native 10 ohms
                DevicesSavedData.load(level).getDevice(LOAD,ResistorDevice.class).properties=ElectricalProperties.resistor(1000);
                var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(PANEL);
                var control=CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get().createNew(PANEL,CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get().mode.getNodesFor(PANEL,ElectricalPanelSlot.THIRD_LEFT),level,ElectricalPanelSlot.THIRD_LEFT,Direction.NORTH,level.registryAccess());
                control.label="Factory enable";((CutOffSwitchPanelAttachment)control).isClosed=true;
                var meter=CEEPanelAttachmentTypes.VOLTMETER.get().createNew(PANEL,CEEPanelAttachmentTypes.VOLTMETER.get().mode.getNodesFor(PANEL,ElectricalPanelSlot.THIRD_CENTERED),level,ElectricalPanelSlot.THIRD_CENTERED,Direction.NORTH,level.registryAccess());meter.label="Stored voltage";
                panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()]=control;panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]=meter;panel.attachmentUpdate();
                ((CircuitBoardBlockEntity)level.getBlockEntity(BOARD)).setSchematic(capacitorBoard());
                wire(h,SOURCE,1,BALLAST,0,true);wire(h,BALLAST,1,PANEL,control.nodes[0].id(),false);wire(h,PANEL,control.nodes[1].id(),BOARD,0,true);wire(h,SOURCE,0,BOARD,1,false);
                wire(h,BOARD,0,LOAD,0,false);wire(h,BOARD,1,LOAD,1,true);wire(h,BOARD,0,PANEL,meter.nodes[0].id(),true);wire(h,BOARD,1,PANEL,meter.nodes[1].id(),false);
            });
        }
        h.runAtTickTime(20,()->{
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(PANEL);var board=(CircuitBoardBlockEntity)level.getBlockEntity(BOARD);
            h.assertTrue(panel!=null&&board!=null,"Persistent electronics fixture missing; run SETUP first");
            var control=(CutOffSwitchPanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
            h.assertTrue(control!=null&&control.isClosed&&"Factory enable".equals(control.label),"Restart lost panel control/label");
            if(verify)h.assertTrue(charge(h)>9.5,"Restart discarded PG board capacitor charge: "+charge(h));
            var markers=level.getDataStorage().computeIfAbsent(Marker.factory(),"wwpg_electronics_fixture");
            if(verify)h.assertTrue(markers.uuid.equals(capacitor(h).uuid.toString())&&java.util.Arrays.equals(markers.terminals,control.nodes==null?new int[0]:java.util.Arrays.stream(control.nodes).mapToInt(n->n.id()).toArray()),"Restart changed component UUID or assigned panel terminal IDs");
            else{markers.uuid=capacitor(h).uuid.toString();markers.terminals=java.util.Arrays.stream(control.nodes).mapToInt(n->n.id()).toArray();markers.setDirty();}
        });
        h.runAtTickTime(115,()->{
            BoardComponentGameTests.near(h,charge(h),10*1000/1010d,.15,"Charged PG board under mixed load");
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(PANEL);var meter=(GaugePanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()];
            BoardComponentGameTests.near(h,meter.value,voltage(h),.1,"Restored panel reads PG storage");
            ((CutOffSwitchPanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()]).isClosed=false;panel.setChanged();
            DevicesSavedData.load(level).getDevice(LOAD,ResistorDevice.class).properties=ElectricalProperties.resistor(9);
        });
        h.runAtTickTime(145,()->{h.assertTrue(charge(h)>1&&charge(h)<4,"PG storage did not discharge into the CEE load: "+charge(h));
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(PANEL);((CutOffSwitchPanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()]).isClosed=true;panel.setChanged();
            DevicesSavedData.load(level).getDevice(LOAD,ResistorDevice.class).properties=ElectricalProperties.resistor(1000);
        });
        h.runAtTickTime(240,()->{h.assertTrue(charge(h)>9.5,"Restored charging circuit failed");DynamicGameTests.audit(h);level.getDataStorage().save();level.getChunkSource().save(true);h.succeed();});
    }
    static CircuitSchematic capacitorBoard(){
        var schematic=new CircuitSchematic();schematic.setName("WWPG stored-energy example");
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(),0,5,null),0,5);schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(),13,5,null),13,5);
        var capacitor=new PlacedComponent(Components.CAPACITOR.get(),6,5,null);capacitor.set(CapacitorComponent.CAPACITANCE,.1f);schematic.placeComponent(capacitor,6,5);
        var router=new BoardFixture.Router(schematic);
        capacitor.footprint().getPads().forEach((point,pad)->{if(pad.nodeIndex()>=0)router.route(new Point(point.x()+6,point.y()+5),new Point(pad.nodeIndex()==0?1:14,6));});
        return schematic;
    }
    private static PlacedComponent capacitor(GameTestHelper h){return ((CircuitBoardBlockEntity)h.getLevel().getBlockEntity(BOARD)).getComponentsStream().filter(c->c.component==Components.CAPACITOR.get()).findFirst().orElseThrow();}
    private static double charge(GameTestHelper h){return ((CRSeriesWire)capacitor(h).wires.getFirst()).capacitorVoltage();}
    private static double voltage(GameTestHelper h){return new BlockWireEndpoint(BOARD,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(BOARD,1).getNode(h.getLevel()).getVoltage();}
    private static void wire(GameTestHelper h,BlockPos a,int ta,BlockPos b,int tb,boolean pg){WiringGameTests.connect(h,a,ta,b,tb,pg);}
    public static final class Marker extends net.minecraft.world.level.saveddata.SavedData {
        String uuid="";int[] terminals={};
        static Factory<Marker> factory(){return new Factory<>(Marker::new,(tag,registries)->{var m=new Marker();m.uuid=tag.getString("ComponentUUID");m.terminals=tag.getIntArray("PanelTerminals");return m;},null);}
        @Override public CompoundTag save(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){tag.putString("ComponentUUID",uuid);tag.putIntArray("PanelTerminals",terminals);return tag;}
    }
}
