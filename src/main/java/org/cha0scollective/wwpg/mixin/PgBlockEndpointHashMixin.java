package org.cha0scollective.wwpg.mixin;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import org.cha0scollective.wwpg.experimental.TerminalKeyLookup;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockWireEndpoint.class, remap = false)
public abstract class PgBlockEndpointHashMixin {
    @Shadow private BlockPos pos;
    @Shadow private int terminal;

    /**
     * @author WWPG
     * @reason Compute PG's identical Objects.hash value without a temporary varargs array.
     */
    @Overwrite
    public int hashCode() {
        // PG reads both fields before invoking the position's hashCode. Do not
        // cache: deserialization and mutable positions may change either value.
        var position = pos;
        int id = terminal;
        if (!TerminalKeyLookup.ENABLED) return Objects.hash(position, id);
        return 31 * (31 + Objects.hashCode(position)) + id;
    }
}
