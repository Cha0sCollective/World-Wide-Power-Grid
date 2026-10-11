package org.cha0scollective.wwpg.gametest;

import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Current completion-yard instructions are separate from the historical example guide. */
final class StationaryYardGuide {
    static void install(GameTestHelper h,StationaryYardStations yard) {
        var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(new BlockPos(6,64,16));
        var introduction=List.of(
                "STATIONARY TEST YARD\n\nWear the goggles in this chest. Follow the white paths or fly. Each station has its own supply. Click its feed switch with an empty hand to stop and restart it. The signs and station books explain the expected response.",
                "START: x8 z16\n\nFactory enable changes green RUN / red OFF lamps. OFF has a capacitor delay. The heater has its own feed, and the pump moves water between tanks. Switches, meters and actual board contacts control these outputs.",
                "MEASURING\n\nPG multimeter: select two terminals for voltage, or select a wire for current. CEE clamp meter: hold use on a wire. Both tools work across mods. Wear goggles for precise gauge and panel numbers. Small electronics currents are often milliamps.",
                "BOARDS: x32-112 z8-81\n\nB01-B25 cover every electrical board component. B26 at x83 z140 covers traces, vias and labels. Each sign lists readings and controls. PAD supplies are numbered. Feed switches stop the selected branch. Keep coil and contact wiring separate.",
                "PANELS: x32-112 z94-115\n\nClick cut-offs. Emergency stop: click, then shift-click to reset. Momentary switches flash briefly. Breaker: change its resistor to 50 ohms; restore 100000 ohms before resetting. Analog lever and wheel control the wireless receiver lamps.",
                "TRANSFORMERS: z124-145\n\nCEE transformers show 100 to 50 V and 100 to 200 V. Native PG windings have some loss. Use variac cranks and redstone controls to change voltage. Compare input, output and current with your meters.",
                "METER BANKS: z156\n\n10 V, about 1 A and 10 W. Energy totals rise. The AC bank shows 50 Hz. DC, AC and RMS readings differ; use each instrument's native setting.",
                "DEVICES: z176-237\n\nD1-D7: switches/buttons. D8-D15: connector families. D16-D22: motors and storage. Portable batteries charge as native items; they do not generate wired power. Open the accumulator charger to watch discharge into its load.",
                "NEW STATIONS: x152-278\n\nS01 onward extend south in rows. Use the station books in this chest for full names, coordinates, readings and reset instructions. Structural parts appear inside working assemblies. Cabinets supply building parts; stored items are not demonstrations.",
                "REPAIR\n\nSwitch OFF before rewiring. Bare CEE fuse or broken bulb: copper wire. PG fuse: remove the blown wire, then install iron wire. Held fuse: remove its broken insert and install a healthy rated fuse. Holder bypass: copper INGOT. Wrench removes held switches and indicators.",
                "DESIGN TABLE\n\nThe live table contains a saved design with traces, a via, a label and a resistor. Its adjacent board was assembled from the saved output using native component items. Open, edit, save or copy with PG's normal interface. Spare blanks and components are in the cabinets.",
                "NETHER ROUTE\n\nThe S65 portal leads to a lit mixed load and voltage gauge. Its feed controls both ends. Follow the landing's white path back. Both ends are kept loaded for the demonstration; automated tests cover independent loading and portal disruption.",
                "COMPUTER EDITION\n\nThe last two stations, if present, use native Pinout with CEE and PG supplies. Open a computer. Type wwpg start, wwpg stop, wwpg demo or wwpg volts. Pattern 85 lights odd pins. Pin 3 also controls a PG board relay and lamp; the panel reads pin 5.",
                "SAVING / RESET\n\nNormal settings and connections save with the world. Keep a clean copy before destructive experiments. Turn feeds back ON and restore labeled settings after tests. Water tanks can be emptied and refilled with buckets. No automatic cleanup runs in normal play; your wire drops remain collectible."
        );
        chest.setItem(8,book("WWPG Yard Guide",introduction));
        int slot=9;
        for(int start=0;start<yard.stations.size();start+=20) {
            var sections=new ArrayList<String>();
            for(var s:yard.stations.subList(start,Math.min(start+20,yard.stations.size())))sections.add(s.code()+" "+s.name()+"\nx"+s.origin().getX()+" y64 z"+s.origin().getZ()+"\n\n"+String.join("\n\n",s.instructions()));
            chest.setItem(slot++,book("WWPG Stations "+(start/20+1),sections));
        }
        chest.setItem(slot++,Items.COPPER_INGOT.getDefaultInstance().copyWithCount(64));
        chest.setItem(slot++,com.george_vi.electroenergetics.CEEItems.COPPER_WIRE.asStack(64));
        chest.setItem(slot++,org.patryk3211.powergrid.collections.ModdedItems.WIRE_CUTTER.asStack());
        chest.setItem(slot++,com.george_vi.electroenergetics.CEEItems.EMPTY_SPOOL.asStack(16));
        chest.setItem(slot++,Items.WATER_BUCKET.getDefaultInstance());chest.setItem(slot++,Items.BUCKET.getDefaultInstance());
        chest.setItem(slot++,Items.LIME_DYE.getDefaultInstance().copyWithCount(64));chest.setItem(slot++,AllItems.GOGGLES.asStack());chest.setChanged();
        var spares=new BlockPos(10,64,16);put(h,spares,Blocks.CHEST);var supply=(ChestBlockEntity)h.getLevel().getBlockEntity(spares);
        var parts=List.of(com.george_vi.electroenergetics.CEEBlocks.FUSE.asStack(16),com.george_vi.electroenergetics.CEEBlocks.CUT_OFF_SWITCH.asStack(16),com.george_vi.electroenergetics.CEEBlocks.INDICATOR_BULB.asStack(16),
                com.george_vi.electroenergetics.CEEItems.COPPER_WIRE_SPOOL.asStack(16),com.george_vi.electroenergetics.CEEItems.EMPTY_SPOOL.asStack(16),org.patryk3211.powergrid.collections.ModdedItems.STRING_LIGHT_CORD.asStack(64),
                org.patryk3211.powergrid.collections.ModdedItems.LIGHT_BULB.asStack(16),org.patryk3211.powergrid.collections.ModdedItems.CIRCUIT_SCHEMATIC.asStack(64),org.patryk3211.powergrid.collections.ModdedItems.INCOMPLETE_CIRCUIT.asStack(16));
        for(int n=0;n<parts.size();n++)supply.setItem(n,parts.get(n));supply.setChanged();sign(h,spares.south(),"REPAIR / ASSEMBLY","Spare native parts","See guide first","Feeds OFF to repair");
    }
    private static ItemStack book(String title,List<String> sections) {
        var pages=new ArrayList<Filterable<Component>>();
        for(var section:sections) {
            var lines=new ArrayList<String>();
            for(var paragraph:section.split("\\n",-1)) {
                if(paragraph.isEmpty()){lines.add("");continue;}
                var line=new StringBuilder();
                for(var word:paragraph.split(" ")) {
                    if(!line.isEmpty()&&line.length()+word.length()+1>19){lines.add(line.toString());line.setLength(0);}
                    if(!line.isEmpty())line.append(' ');line.append(word);
                }
                if(!line.isEmpty())lines.add(line.toString());
            }
            for(int start=0;start<lines.size();start+=13)pages.add(Filterable.passThrough(Component.literal(String.join("\n",lines.subList(start,Math.min(start+13,lines.size()))))));
        }
        if(pages.size()>100)throw new IllegalStateException("Native book exceeds 100 pages: "+title+" / "+pages.size());
        var stack=Items.WRITTEN_BOOK.getDefaultInstance();stack.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough(title),"Cha0sCollective",0,pages,true));return stack;
    }
}
