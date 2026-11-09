package org.cha0scollective.wwpg.clientdelivery.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerChunkSender.class)
public interface PlayerChunkSenderAccess {
    @Accessor("pendingChunks") LongSet delivery$pending();
    @Accessor("memoryConnection") boolean delivery$memory();
    @Accessor("desiredChunksPerTick") float delivery$desired();
    @Accessor("batchQuota") float delivery$quota();
    @Accessor("unacknowledgedBatches") int delivery$unacknowledged();
    @Accessor("maxUnacknowledgedBatches") int delivery$maximumUnacknowledged();
}
