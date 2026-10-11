package org.cha0scollective.wwpg.examples.upstream;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.IndicatorBulbPanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Native CEE installation, terminals, dyeing, tooltip and indicator rendering.
 * Only electrical preparation is extended. Accepted capacitor history is saved
 * in this attachment's native versioned NBT, not in a second compatibility store.
 */
public final class StorageIndicatorAttachment extends IndicatorBulbPanelAttachment {
    public final StorageIndicatorProperties electrical = new StorageIndicatorProperties();
    public double measuredVoltage;

    public StorageIndicatorAttachment(PanelAttachmentType type) { super(type); }

    @Override public void preTick(BridgeCollector collector) {
        collector.bridge(nodes[0], nodes[1], electrical);
    }

    @Override public void postTick(SimulationResults results) {
        super.postTick(results);
        measuredVoltage = results.getVoltageAt(nodes[0], nodes[1]);
        // CEE owns the device's state, while its block entity mirrors that state
        // into chunk NBT. Mark that mirror dirty even when steady light strength
        // causes the native indicator to skip its usual display update.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                var blockEntity = chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
                if (blockEntity != null) blockEntity.setChanged();
            }
        }
    }

    @Override public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.read(tag, clientPacket, registries);
        electrical.savedVoltage = tag.getDouble("ApiStoredVoltage");
        electrical.acceptedSubsteps = tag.getLong("ApiAcceptedSubsteps");
        measuredVoltage = tag.getDouble("ApiMeasuredVoltage");
    }

    @Override public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        super.write(tag, clientPacket, registries);
        tag.putInt("ApiStateVersion", 1);
        tag.putDouble("ApiStoredVoltage", electrical.savedVoltage);
        tag.putLong("ApiAcceptedSubsteps", electrical.acceptedSubsteps);
        tag.putDouble("ApiMeasuredVoltage", measuredVoltage);
    }
}
