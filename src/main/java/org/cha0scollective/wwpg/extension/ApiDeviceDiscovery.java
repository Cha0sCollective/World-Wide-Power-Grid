package org.cha0scollective.wwpg.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.cha0scollective.wwpg.api.neoforge.DeviceDiscoveryService;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;

/** Runtime loaded-chunk discovery and deferred notification service. */
public final class ApiDeviceDiscovery implements DeviceDiscoveryService {
    public ApiDeviceDiscovery() {}

    /** Freeze only after all mod capability registration listeners have completed. */
    public static void freeze(FMLLoadCompleteEvent event) { ElectricalProviders.freeze(); }

    @Override public boolean changed(ServerLevel level, BlockPos position) {
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) return false;
        ApiCircuits.nominate(level, position);
        return true;
    }

    /** Inspect supplied loaded section palettes; persisted plain blocks also participate. */
    public static void chunkLoaded(ServerLevel level, ChunkAccess chunk) {
        chunk.getBlockEntitiesPos().forEach(pos -> ApiCircuits.nominate(level, pos));
        var registered = ElectricalProviders.registeredBlocks();
        if (registered.isEmpty()) return;
        var sections = chunk.getSections();
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; ++index) {
            var section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> registered.contains(state.getBlock()))) continue;
            int originY = chunk.getMinBuildHeight() + index * 16;
            for (int y = 0; y < 16; ++y) for (int z = 0; z < 16; ++z) for (int x = 0; x < 16; ++x)
                if (registered.contains(section.getBlockState(x, y, z).getBlock()))
                    ApiCircuits.nominate(level, new BlockPos(originX + x, originY + y, originZ + z));
        }
    }
}
