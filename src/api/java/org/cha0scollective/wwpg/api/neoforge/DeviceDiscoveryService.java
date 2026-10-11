package org.cha0scollective.wwpg.api.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** WWPG runtime service used by ElectricalDevices; add-ons use the facade. */
public interface DeviceDiscoveryService {
    /** Queue loaded-only discovery at the next safe preparation phase. */
    boolean changed(ServerLevel level, BlockPos position);
}
