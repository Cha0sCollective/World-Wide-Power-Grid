package org.cha0scollective.wwpg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;
import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.UUID;

/** Create skips remove() for chunk unload, so PG's static link needs detaching here. */
@Mixin(value = NetherTransformerBlockEntity.class, remap = false)
public abstract class NetherTransformerUnloadMixin extends ElectricBlockEntity {
    @Shadow @Final private static Map<UUID, ?> TRANSFORMERS;
    @Shadow private UUID id;
    @Shadow private boolean secondary;
    @Shadow private SplitTransformerControllerWire wire;

    protected NetherTransformerUnloadMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override public void onChunkUnloaded() {
        super.onChunkUnloaded();
        var value = TRANSFORMERS.get(id);
        if (value == null) return;
        var entry = (NetherTransformerEntryAccessor) value;
        // A late unload must not detach an already replaced endpoint.
        if ((secondary ? entry.wwpg$secondary() : entry.wwpg$primary()) != wire) return;
        entry.wwpg$setWire(secondary, null);
        if (entry.wwpg$isEmpty()) TRANSFORMERS.remove(id);
    }
}
