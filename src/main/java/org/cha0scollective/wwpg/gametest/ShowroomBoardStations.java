package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.fuse.FuseState;
import org.patryk3211.powergrid.electricity.gauge.CurrentGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.patryk3211.powergrid.electricity.sim.special.NeonBulbWire;

import java.util.*;
import java.util.function.Supplier;

import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** One live, separately wired board per electrical component, with native meters. */
final class ShowroomBoardStations {
    private record Spec(String name, Supplier<? extends Component> type, int main, double[] voltage, double[] resistance,
                        String reading, String action) {}
    private static Spec spec(String name, Supplier<? extends Component> type, String reading, String action) {
        return new Spec(name,type,0,new double[]{10,0},new double[]{.1,.1},reading,action);
    }
    private static Spec configured(String name, Supplier<? extends Component> type, int main, double[] v, double[] r, String reading, String action) {
        return new Spec(name,type,main,v,r,reading,action);
    }
    private static final List<Spec> SPECS = List.of(
        spec("Resistor",Components.RESISTOR::get,"10 V / 10 mA","Change source V"),
        configured("Capacitor",Components.CAPACITOR::get,0,new double[]{10,0},new double[]{100,.1},"Charges to 10 V","Off: watch voltage"),
        configured("Inductor",Components.INDUCTOR::get,0,new double[]{10,0},new double[]{10,.1},"About 0.98 A on","Switch feed off/on"),
        configured("Potentiometer",Components.POTENTIOMETER::get,0,new double[]{10,0,0},new double[]{.1,100000,.1},"Middle pin: 5 V","Turn board knob"),
        configured("Varistor",Components.VARISTOR::get,0,new double[]{150,0},new double[]{10000,.1},"Clamps under 120 V","Try 50 V supply"),
        configured("Fuse holder",Components.FUSE_HOLDER::get,0,new double[]{.1,0},new double[]{10,.1},"10 mA / 1 A fuse","Raise V: fuse trips"),
        configured("Switch",Components.SWITCH::get,0,new double[]{10,0},new double[]{1000,.1},"ON: about 10 mA","Click the switch"),
        configured("Button",Components.BUTTON::get,0,new double[]{10,0},new double[]{1000,.1},"Brief 10 mA pulse","Press the button"),
        configured("Relay",Components.RELAY::get,0,new double[]{13,0,0,10,0},new double[]{.1,.1,1000,.1,1000},"Coil: about 0.11 A","Off: contacts swap"),
        configured("DPDT relay",Components.RELAY_DPDT::get,0,new double[]{13,0,0,10,0,0,10,0},new double[]{.1,.1,1000,.1,1000,1000,.1,1000},"2 contacts transfer","Off: probe contacts"),
        configured("Redstone relay",Components.REDSTONE_RELAY::get,0,new double[]{10,0},new double[]{1000,.1},"ON: about 10 mA","Flip nearby lever"),
        configured("Diode",Components.DIODE::get,1,new double[]{0,10},new double[]{.1,1000},"Forward: about 9 mA","Reverse source wires"),
        configured("NPN transistor",Components.BJT_NPN::get,0,new double[]{10,5,0},new double[]{1000,100000,.1},"Base controls current","PAD1 supply: try 0"),
        configured("PNP transistor",Components.BJT_PNP::get,0,new double[]{-10,-5,0},new double[]{1000,100000,.1},"Reversed supplies","PAD1 supply: try 0"),
        configured("VFET",Components.VFET::get,0,new double[]{20,0,2},new double[]{1000,.1,.1},"Gate controls current","PAD2 supply: try 0"),
        configured("Triode",Components.TRIODE::get,2,new double[]{0,0,100,6,0},new double[]{.1,.1,10000,.01,.01},"6 V heater; plate I","PAD3 heater: try 0"),
        configured("Pentode",Components.PENTODE::get,2,new double[]{0,0,100,6,0,100},new double[]{.1,.1,10000,.01,.01,1000},"Heater / screen bias","PAD3 heater: try 0"),
        configured("Thyratron",Components.THYRATRON::get,2,new double[]{0,0,100,7,0},new double[]{.1,.1,1000,.01,.01},"Heated tube strikes","Switch plate off"),
        configured("Regulator tube",Components.REGULATOR_TUBE::get,0,new double[]{100,0},new double[]{5000,.1},"Holds about 60 V","Switch feed off/on"),
        spec("Barretter tube",Components.BARRETTER_TUBE::get,"About 0.1 A","Try 10 V / 20 V"),
        configured("Neon bulb",Components.NEON_BULB::get,0,new double[]{80,0},new double[]{5000,.1},"Glows at about 45 V","Switch feed off/on"),
        configured("Light bulb",Components.LIGHT_BULB::get,0,new double[]{12,0},new double[]{.1,.1},"Board bulb glows","Off: bulb cools"),
        spec("Voltage gauge",Components.VOLTAGE_GAUGE::get,"Board gauge: 10 V","Change source V"),
        configured("Current gauge",Components.CURRENT_GAUGE::get,0,new double[]{10,0},new double[]{1000,1000},"Board gauge: 5 mA","Change source V"),
        configured("Display module",Components.DISPLAY_MODULE::get,0,new double[]{24,0,0},new double[]{.1,.1,.1},"Counts ON pulses","Switch feed off/on")
    );
    private record Station(Spec spec, BoardFixture fixture, BlockPos base, BlockPos current) {}
    private final List<Station> stations = new ArrayList<>();

    ShowroomBoardStations(GameTestHelper h, boolean restore) {
        int i=0;
        for (var spec:SPECS) {
            var base=new BlockPos(32+(i%5)*16,62,8+(i/5)*16); i++;
            var f=new BoardFixture(h,spec.type.get(),p->{
                if(p.component==Components.RESISTOR.get())p.set(ResistorComponent.RESISTANCE,1000f);
                if(p.component==Components.CAPACITOR.get())p.set(CapacitorComponent.CAPACITANCE,.01f);
                if(p.component==Components.INDUCTOR.get())p.set(InductorComponent.INDUCTANCE,1f);
                if(p.component==Components.FUSE_HOLDER.get())p.set(FuseHolderComponent.MAX_CURRENT,1f);
                if(p.component==Components.REDSTONE_RELAY.get())p.set(org.patryk3211.powergrid.circuits.components.properties.Orientation.PROPERTY,org.patryk3211.powergrid.circuits.components.properties.Orientation.LEFT);
            },base,restore);
            if(restore&&spec.name.equals("Capacitor"))h.assertTrue(f.component.serializeNbt(h.getLevel().registryAccess()).getCompound("Properties").getFloat("powergrid:charge")>9.8,"Yard board capacitor lost its saved charge before recharging");
            var ammeter=base.offset(8,2,1);
            stations.add(new Station(spec,f,base,ammeter));
            if (!restore) {
                put(h,ammeter,ModdedBlocks.CURRENT_METER.get());
                put(h,base.offset(8,2,5),ModdedBlocks.VOLTAGE_METER.get());
                put(h,base.offset(9,2,3),CEEBlocks.CUT_OFF_SWITCH.get());
                sign(h,base.offset(3,2,9),"B"+String.format("%02d",i)+" "+spec.name,"LIVE PG board",spec.reading,spec.action);
                sign(h,base.offset(9,2,7),"BOARD FEED","Click empty-handed","cut-off switch","Goggles: readings");
                for(int pad:f.pads())sign(h,f.supply(pad).above(2),"PAD "+pad,"CEE supply",spec.voltage[pad]<0?"Reversed polarity":"Positive polarity","Goggles: adjust V");
                if (spec.name.equals("Redstone relay")) {
                    put(h,f.boardPosition.west(),Blocks.STONE);
                    h.getLevel().setBlockAndUpdate(f.boardPosition.west().above(),Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
                }
            }
        }
        if (!restore) {
            h.runAtTickTime(15,()->connect(h));
            h.runAtTickTime(25,()->{
                var f=stations.stream().filter(s->s.spec.name.equals("Thyratron")).findFirst().orElseThrow().fixture;
                double heater=Math.abs(f.component.wires.get(1).potentialDifference());
                h.assertTrue(heater>1,"Thyratron exhibit heater received no voltage");
                scroll(h,f.supply(3),(int)Math.round(7000*6/heater));
            });
        }
    }
    private void connect(GameTestHelper h) {
        for(var station:stations) {
            var spec=station.spec;var f=station.fixture;BlockPos previous=null;int previousReturn=0;
            f.referenceTerminal(spec.voltage[0]<0?1:0);
            for(int pad:f.pads()) {
                double desired=spec.voltage[pad];int positive=desired<0?0:1,negative=1-positive;
                scroll(h,f.supply(pad),(int)Math.round(Math.abs(desired)*1000));
                DevicesSavedData.load(h.getLevel()).getDevice(f.supply(pad),CreativeBatteryDevice.class).voltage=Math.abs(desired);
                double r=spec.resistance[pad];resistance(h,f.ballast(pad),r);f.resistance(pad,r);
                if(pad==spec.main) {
                    var control=station.base.offset(9,2,3);
                    wire(h,f.supply(pad),positive,control,0,false);wire(h,control,1,f.ballast(pad),0,true);
                    wire(h,f.ballast(pad),1,station.current,0,true);wire(h,station.current,1,f.boardPosition,f.port(pad),false);
                } else {wire(h,f.supply(pad),positive,f.ballast(pad),0,true);wire(h,f.ballast(pad),1,f.boardPosition,f.port(pad),true);}
                if(previous!=null)wire(h,previous,previousReturn,f.supply(pad),negative,false);
                previous=f.supply(pad);previousReturn=negative;
            }
            int first=spec.name.equals("Potentiometer")?1:spec.main;
            int second=spec.name.endsWith("transistor")?2:spec.name.equals("Potentiometer")?2:spec.main==0?1:0;
            var vm=station.base.offset(8,2,5);
            scroll(h,vm,Math.abs(spec.voltage[first]-spec.voltage[second])>20?2:1);
            scroll(h,station.current,spec.name.equals("Inductor")||spec.name.equals("Light bulb")?2:spec.name.contains("relay")||spec.name.equals("Relay")||spec.name.equals("Thyratron")||spec.name.equals("Barretter tube")?1:0);
            wire(h,f.boardPosition,f.port(first),vm,0,false);wire(h,f.boardPosition,f.port(second),vm,1,true);
            if(spec.name.equals("Redstone relay")) {
                var lever=f.boardPosition.west().above();
                ((net.minecraft.world.level.block.LeverBlock)Blocks.LEVER).pull(h.getLevel().getBlockState(lever),h.getLevel(),lever,h.makeMockPlayer(GameType.SURVIVAL));
            }
            if(spec.name.equals("Switch"))Components.SWITCH.get().use(f.board,f.component,h.makeMockPlayer(GameType.SURVIVAL));
            if(spec.name.equals("Fuse holder")) {
                var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.IRON_WIRE.asStack());
                Components.FUSE_HOLDER.get().use(f.board,f.component,player);
            }
        }
    }
    void verify(GameTestHelper h) {
        h.assertTrue(stations.size()==25,"Missing an electrical board exhibit");
        checkAll(h,stations.stream().<Runnable>map(station->()->{
            var f=station.fixture;var name=station.spec.name;
            f.referenceTerminal(station.spec.voltage[0]<0?1:0);
            h.assertTrue(f.board==h.getLevel().getBlockEntity(f.boardPosition),name+" board entity was replaced while constructing the yard");
            h.assertTrue(f.board.getComponentsStream().anyMatch(p->p==f.component),name+" component instance was replaced");
            h.assertTrue(!f.component.destroyed,name+" exhibit destroyed its component");
            double measured=((CurrentGaugeBlockEntity)h.getLevel().getBlockEntity(station.current)).getValue();
            near(h,Math.abs(measured),Math.abs(f.feedCurrent(station.spec.main)),.0001,name+" external current gauge");
            switch(name) {
                case "Resistor" -> near(h,Math.abs(f.padVoltage(0)-f.padVoltage(1)),10,.1,name+" terminal voltage");
                case "Switch" -> near(h,f.current(),.01,.0001,name+" conduction");
                case "Capacitor" -> near(h,((CRSeriesWire)f.component.wires.getFirst()).capacitorVoltage(),10,.1,name+" stored voltage");
                case "Inductor" -> near(h,f.current(),10/10.15,.02,name+" current");
                case "Potentiometer" -> near(h,f.padVoltage(1),5,.1,name+" midpoint");
                case "Varistor" -> h.assertTrue(f.current()>.001&&f.padVoltage(0)<120,name+" failed to clamp");
                case "Fuse holder" -> h.assertTrue(f.component.get(FuseHolderComponent.STATE)==FuseState.CLOSED&&f.current()>.009,name+" fuse is not conducting");
                case "Button" -> near(h,f.current(),0,.00001,"Released board button");
                case "Relay", "DPDT relay" -> {h.assertTrue(f.component.get(RelayComponent.STATE),name+" coil did not pull in");near(h,f.padVoltage(4),10,.1,name+" NO contact");}
                case "Redstone relay" -> h.assertTrue(f.current()>.009,"Physical lever did not power board redstone relay");
                case "Diode" -> h.assertTrue(f.feedCurrent(1)>.008&&f.feedCurrent(1)<.011,"Diode is not forward conducting: "+f.feedCurrent(1));
                case "NPN transistor", "PNP transistor" -> {double collector=Math.abs(f.feedCurrent(0)),base=Math.abs(f.feedCurrent(1));h.assertTrue(collector>.0005&&base>0,name+" has no bias current");near(h,collector/base,20,2,name+" gain");}
                case "VFET" -> h.assertTrue(f.current()>.0001,"VFET gate did not enable current");
                case "Triode", "Pentode" -> h.assertTrue(f.current()>.0001&&Math.abs(f.component.wires.get(1).potentialDifference())>5.8,name+" heater did not enable plate current");
                case "Thyratron" -> h.assertTrue(f.current()>.05&&f.component.get(NeonBulbComponent.LIT),"Thyratron did not ignite");
                case "Neon bulb", "Regulator tube" -> {h.assertTrue(((NeonBulbWire)f.component.wires.getFirst()).isLit(),name+" did not strike");near(h,Math.abs(f.padVoltage(0)-f.padVoltage(1)),name.equals("Neon bulb")?45:60,2,name+" holding voltage");}
                case "Barretter tube" -> near(h,f.current(),.1,.003,name+" regulation");
                case "Light bulb" -> h.assertTrue(((Component.FloatPair)f.component.customData).lerped(1)>.1&&f.current()>.2,"Board bulb is not glowing");
                case "Voltage gauge" -> near(h,Components.VOLTAGE_GAUGE.get().getValue(f.component),10,.1,name+" reading");
                case "Current gauge" -> near(h,Components.CURRENT_GAUGE.get().getValue(f.component),.005,.0001,name+" reading");
                case "Display module" -> h.assertTrue(f.component.get(ModularDisplayComponent.INDEX)>0,"Display did not count its power-on pulse");
                default -> throw new IllegalStateException("Unvalidated exhibit "+name);
            }
        }).toArray(Runnable[]::new));
    }
    void checkInteractions(GameTestHelper h) {
        var button=stations.stream().filter(s->s.spec.name.equals("Button")).findFirst().orElseThrow().fixture;
        h.runAtTickTime(190,()->Components.BUTTON.get().use(button.board,button.component,h.makeMockPlayer(GameType.SURVIVAL)));
        h.runAtTickTime(192,()->near(h,button.current(),.01,.0001,"Exhibit button press"));
        h.runAtTickTime(205,()->near(h,button.current(),0,.00001,"Exhibit button release"));
        var sw=stations.stream().filter(s->s.spec.name.equals("Switch")).findFirst().orElseThrow().fixture;
        h.runAtTickTime(190,()->Components.SWITCH.get().use(sw.board,sw.component,h.makeMockPlayer(GameType.SURVIVAL)));
        h.runAtTickTime(195,()->{near(h,sw.current(),0,.00001,"Exhibit switch open");Components.SWITCH.get().use(sw.board,sw.component,h.makeMockPlayer(GameType.SURVIVAL));});
    }
}
