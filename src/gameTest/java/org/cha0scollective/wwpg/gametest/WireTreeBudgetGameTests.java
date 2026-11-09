package org.cha0scollective.wwpg.gametest;

import java.util.*;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.*;

@GameTestHolder("wwpg_tree_budget")
@PrefixGameTestTemplate(false)
public final class WireTreeBudgetGameTests {
    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos.below(),Blocks.STONE); h.setBlock(pos,block);
    }
    static List<BlockPos> ring(GameTestHelper h, int count) {
        h.assertTrue(WireTreeBatch.BOUNDED && WireTreeBatch.MAX_PARTS==1,"Fixture requires one bounded wire per tick");
        var positions = new ArrayList<BlockPos>();
        for (int i=0;i<count;i++) {
            var pos = new BlockPos(1+2*(i%4),2,1+2*(i/4)); positions.add(h.absolutePos(pos));
            place(h,pos,i==0?ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get():ModdedBlocks.CREATIVE_RESISTOR.get());
        }
        h.runAtTickTime(5,()->{
            DynamicGameTests.substeps(h.getLevel());
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(positions.getFirst())).setValue(10);
            for (var pos:positions.subList(1,positions.size())) ((ResistorBlockEntity)h.getLevel().getBlockEntity(pos)).setValue(1000);
            for (int terminal=0;terminal<2;terminal++) for (int i=0;i<count;i++)
                WiringGameTests.connect(h,positions.get(i),terminal,positions.get((i+1)%count),terminal,true);
        });
        return positions;
    }
    static List<TransmissionLinePart> reload(GameTestHelper h, List<BlockPos> positions) {
        var level=h.getLevel(); var world=GlobalElectricNetworks.getWorldNetworks(level);
        int alreadyPending=WireTreeBatch.pending(level);
        var unique=Collections.newSetFromMap(new IdentityHashMap<TransmissionLinePart,Boolean>());
        for (var pos:positions) for (int terminal=0;terminal<2;terminal++) {
            var wires=world.findConnectedWires(new BlockWireEndpoint(pos,terminal)); if(wires!=null)unique.addAll(wires);
        }
        unique.removeIf(part -> !(part.getEndpoint1() instanceof BlockWireEndpoint first)
                || !(part.getEndpoint2() instanceof BlockWireEndpoint second)
                || !positions.contains(first.getPos()) || !positions.contains(second.getPos()));
        var parts=new ArrayList<>(unique); parts.sort(Comparator.comparing(p->p.owner.getUUID().toString()));
        h.assertTrue(parts.size()==positions.size()*2,"Unexpected saved wire count");
        var tags=new ArrayList<CompoundTag>();
        for (var part:parts) {
            var tag=new CompoundTag(); h.assertTrue(part.owner.save(tag),"Wire save failed"); tags.add(tag);
            part.owner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
        for (int i=0;i<tags.size();i++) {
            var entity=EntityType.loadEntityRecursive(tags.get(i),level,e->e);
            h.assertTrue(entity instanceof WireEntity && ((WireEntity)entity).getWire()==parts.get(i),"Saved identity changed");
            h.assertTrue(level.addFreshEntity(entity),"Reloaded UUID did not rejoin");
        }
        h.assertTrue(WireTreeBatch.pending(level)==alreadyPending+parts.size()*2,"Reload did not queue all roots");
        return parts;
    }
    @GameTest(template="empty",batch="budget_storage",timeoutTicks=140)
    public static void queueHoldsStorageBeyondQuietDeadlineAndResumes(GameTestHelper h) {
        var positions=ring(h,16); var level=h.getLevel();
        var battery=new BlockPos(1,4,1); var terminal=battery.above(); var load=new BlockPos(7,4,1);
        var source=new BlockPos(1,4,4); var capacitor=new BlockPos(7,4,4);
        place(h,battery,ModdedBlocks.BATTERY.get()); place(h,load,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING,Direction.DOWN));
        place(h,source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); place(h,capacitor,CEEBlocks.CAPACITOR.get());
        var b=h.absolutePos(battery); var c=h.absolutePos(capacitor); var s=h.absolutePos(source);
        double[] energy={0},voltage={0}; float[] temperature={0};
        h.runAtTickTime(5,()->{
            var nativeBattery=(BatteryBlockEntity)level.getBlockEntity(b); nativeBattery.setEnergy(nativeBattery.getCapacity()*.5);
            ((ResistorBlockEntity)h.getBlockEntity(load)).setValue(1000);
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);
            DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class).capacitance=.01;
            for (int terminalId=0;terminalId<2;terminalId++) {
                WiringGameTests.connect(h,h.absolutePos(terminal),terminalId,h.absolutePos(load),terminalId,true);
                WiringGameTests.connect(h,s,terminalId,c,terminalId,true);
            }
            if (org.cha0scollective.wwpg.experimental.ScopedRestoration.ENABLED) {
                // This fixture tests affected storage. Connect both storage
                // circuits to the reloaded ring when the hold is component-scoped.
                WiringGameTests.connect(h,positions.getFirst(),0,h.absolutePos(load),0,true);
                WiringGameTests.connect(h,positions.getFirst(),0,s,0,true);
            }
        });
        h.runAtTickTime(25,()->reload(h,positions));
        for (int tick=27;tick<=52;tick++) { final int sample=tick; h.runAtTickTime(tick,()->{
            h.assertTrue(WireTreeBatch.holding(level),"Queue drained before its one-wire-per-tick budget allowed");
            h.assertTrue(RestorationCoordinator.pausedAt(level,b)&&RestorationCoordinator.pausedAt(level,c),"Pending dimension was released");
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,c,0,c,1).status()!=MeasurementStatus.VALID,"Pending circuit published accepted readings");
            var nativeBattery=(BatteryBlockEntity)level.getBlockEntity(b);
            var device=DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class);
            if(sample==27) {
                energy[0]=nativeBattery.getEnergy(); voltage[0]=device.lastVoltage; temperature[0]=device.temp;
                ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(30);
            } else {
                h.assertTrue(nativeBattery.getEnergy()==energy[0],"Held native battery consumed energy");
                h.assertTrue(device.lastVoltage==voltage[0]&&device.properties.lastVoltage==voltage[0]&&device.temp==temperature[0],"Held CEE storage or heat advanced");
            }
            int pending=WireTreeBatch.pending(level);
            WireTreeBatch.flush(level); WireTreeBatch.flush(level);
            h.assertTrue(WireTreeBatch.pending(level)==pending,"Repeated prepare bypassed the per-tick budget");
        }); }
        h.runAtTickTime(85,()->{
            h.assertTrue(WireTreeBatch.pending(level)==0,"Queue failed to drain");
            h.assertTrue(((BatteryBlockEntity)level.getBlockEntity(b)).getEnergy()<energy[0],"Native storage failed to resume");
            h.assertTrue(DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class).lastVoltage>voltage[0],"CEE storage failed to resume");
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,c,0,c,1).status()==MeasurementStatus.VALID,"Readings failed to resume");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",batch="budget_removal",timeoutTicks=100)
    public static void removedQueuedWireCannotBeResurrected(GameTestHelper h) {
        var positions=ring(h,4); var level=h.getLevel();
        WorldNetworks.PartId[] id={null}; long[] discarded={0};
        h.runAtTickTime(25,()->{
            var parts=reload(h,positions); var part=parts.getFirst(); var owner=part.owner;
            id[0]=part.persistentOwnerId; discarded[0]=WireTreeBatch.discarded();
            part.remove(); owner.remove(Entity.RemovalReason.DISCARDED);
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(WireTreeBatch.pending(level)==0,"Removal left pending roots");
            h.assertTrue(WireTreeBatch.discarded()>=discarded[0]+2,"Stale queued roots were traversed");
            h.assertTrue(GlobalElectricNetworks.getWorldNetworks(level).getPart(id[0])==null,"Removed saved part was resurrected");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
}
