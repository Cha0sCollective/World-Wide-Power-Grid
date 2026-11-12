package org.cha0scollective.wwpg.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.*;
import com.george_vi.electroenergetics.CEEBlocks;

@GameTestHolder("wwpg_tree_restore")
@PrefixGameTestTemplate(false)
public final class WireTreeRestorationGameTests {
    @GameTest(template="empty", batch="tree_pg", timeoutTicks=120) public static void pgSavedRing(GameTestHelper h) { restore(h, false, false, false, false); }
    @GameTest(template="empty", batch="tree_cee", timeoutTicks=120) public static void ceeSavedRing(GameTestHelper h) { restore(h, true, false, false, false); }
    @GameTest(template="empty", batch="tree_reverse", timeoutTicks=120) public static void reverseLoadOrder(GameTestHelper h) { restore(h, true, true, false, false); }
    @GameTest(template="empty", batch="tree_missing", timeoutTicks=120) public static void missingSavedPartFallsBack(GameTestHelper h) { restore(h, false, true, true, false); }
    @GameTest(template="empty", batch="tree_cold_pg", timeoutTicks=120) public static void coldPgLoopWithoutResolvedEdges(GameTestHelper h) { restore(h, false, false, false, true); }
    @GameTest(template="empty", batch="tree_cold_cee", timeoutTicks=120) public static void coldCeeLoopInReverseOrder(GameTestHelper h) { restore(h, true, true, false, true); }

    private static void restore(GameTestHelper h, boolean cee, boolean reverse, boolean removePart, boolean coldParts) {
        var level = h.getLevel();
        var positions = List.of(new BlockPos(1,2,1), new BlockPos(4,2,1), new BlockPos(4,2,4), new BlockPos(1,2,4));
        for (int i=0;i<positions.size();i++) {
            var pos=positions.get(i); h.setBlock(pos.below(), Blocks.STONE);
            h.setBlock(pos, i==0 ? ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()
                    : cee ? CEEBlocks.CREATIVE_RESISTOR.get() : ModdedBlocks.CREATIVE_RESISTOR.get());
        }
        var abs=positions.stream().map(h::absolutePos).toList();
        var saved=new ArrayList<CompoundTag>(); var parts=new ArrayList<TransmissionLinePart>();
        var restored=new ArrayList<WireEntity>();
        h.runAtTickTime(5,()->{
            DynamicGameTests.substeps(level);
            ((CreativeSourceBlockEntity)level.getBlockEntity(abs.getFirst())).setValue(10);
            for(var pos:abs.subList(1,abs.size())) {
                if(cee) com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level)
                        .getDevice(pos, com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice.class)
                        .properties = com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties.resistor(1000);
                else ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)level.getBlockEntity(pos)).setValue(1000);
            }
            long before=WireTreeBatch.attached();
            for(int terminal=0;terminal<2;terminal++) for(int i=0;i<4;i++)
                WiringGameTests.connect(h,abs.get(i),terminal,abs.get((i+1)%4),terminal,true);
            h.assertTrue(before==WireTreeBatch.attached(),"Live wire placement entered restoration batching");
        });
        h.runAtTickTime(25,()->{
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            var unique=Collections.newSetFromMap(new IdentityHashMap<TransmissionLinePart,Boolean>());
            for(var pos:abs) for(int terminal=0;terminal<2;terminal++) {
                var found=world.findConnectedWires(new BlockWireEndpoint(pos,terminal));
                if(found!=null)unique.addAll(found);
            }
            parts.addAll(unique); parts.sort(Comparator.comparing(p->p.owner.getUUID().toString()));
            h.assertTrue(parts.size()==8,"Expected eight saved ring wires, got "+parts.size());
            for(var part:parts) {
                var tag=new CompoundTag(); h.assertTrue(part.owner.save(tag),"Wire did not serialize");saved.add(tag);
                part.owner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                h.assertTrue(world.getPart(part.persistentOwnerId)==part && part.owner==null,"Unload lost saved part identity");
            }
            if(coldParts) {
                var partTags=parts.stream().map(TransmissionLinePart::toNbt).toList();
                for(var part:parts)part.remove();
                parts.clear();
                for(var tag:partTags)parts.add(TransmissionLinePart.uniquePart(tag,world));
                h.assertTrue(parts.stream().allMatch(p->p.getLine()==null && p.owner==null),"Cold fixture retained resolved lines");
            }
            if(removePart)parts.getFirst().remove();
            var order=new ArrayList<Integer>();for(int i=0;i<saved.size();i++)order.add(i);
            if(reverse)Collections.reverse(order);
            long before=WireTreeBatch.attached();
            for(int index:order) {
                var entity=EntityType.loadEntityRecursive(saved.get(index),level,e->e);
                h.assertTrue(entity instanceof WireEntity,"Reload did not produce native wire");
                var wire=(WireEntity)entity;restored.add(wire);
                h.assertTrue(level.addFreshEntity(wire),"Restored entity UUID could not rejoin world");
                if(!removePart||index!=0)h.assertTrue(wire.getWire()==parts.get(index),"Reload replaced saved wire part");
            }
            if(WireTreeBatch.ENABLED) {
                h.assertTrue(WireTreeBatch.attached()-before==(removePart?7:8),"Saved wire batching did not match eligibility");
                h.assertTrue(WireTreeBatch.pending(level)>0,"Expected pending restoration roots");
            }
            // The production flush runs at the next electrical prepare boundary.
        });
        h.runAtTickTime(50,()->{
            h.assertTrue(WireTreeBatch.pending(level)==0,"Restoration roots did not drain");
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            for(var wire:restored) {
                h.assertTrue(wire.getWire() instanceof TransmissionLinePart,"Saved wire lost native part");
                var part=(TransmissionLinePart)wire.getWire();
                h.assertTrue(world.getPart(part.persistentOwnerId)==part && part.owner==wire && part.getLine()!=null,"Restored part is unresolved or has wrong owner");
            }
            var load=abs.get(2);
            double voltage=new BlockWireEndpoint(load,0).getNode(level).getVoltage()-new BlockWireEndpoint(load,1).getNode(level).getVoltage();
            h.assertTrue(Math.abs(voltage-10)<.2,"Restored circuit voltage was "+voltage);
            DynamicGameTests.audit(h); h.succeed();
        });
    }
}
