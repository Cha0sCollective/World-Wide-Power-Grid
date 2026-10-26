package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Restore the yard's native /forceload settings after GameTest batch cleanup. */
public final class StationaryYardChunkTickets {
    private StationaryYardChunkTickets() {}

    public static void preserveForExport(ServerStoppingEvent event) {
        // Registered only for a packaged, dedicated yard acceptance process.
        // Vanilla GameTestRunner clears every forced chunk in its test level,
        // including the source end of the yard's playable Nether circuit.
        force(event.getServer().overworld(), new BlockPos(266, 65, 224));
        force(event.getServer().getLevel(Level.NETHER), new BlockPos(34, 65, 28));
        WorldWidePowerGrid.LOGGER.info("STATIONARY_YARD_NATIVE_TICKETS_SAVED: Overworld source and Nether load; native forced chunks restored after GameTest cleanup");
    }

    private static void force(ServerLevel level, BlockPos pos) {
        if (level == null) throw new IllegalStateException("Stationary yard is missing a native dimension");
        for (int x = (pos.getX() >> 4) - 1; x <= (pos.getX() >> 4) + 1; x++)
            for (int z = (pos.getZ() >> 4) - 1; z <= (pos.getZ() >> 4) + 1; z++)
                level.setChunkForced(x, z, true);
        // Vanilla setChunkForced(false-change) clears this dirty flag. Ensure
        // the complete native set is saved even when the last ticket existed.
        level.getDataStorage().computeIfAbsent(net.minecraft.world.level.ForcedChunksSavedData.factory(), "chunks").setDirty();
    }
}
