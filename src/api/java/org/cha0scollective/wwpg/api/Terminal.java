package org.cha0scollective.wwpg.api;

import java.util.Objects;

/**
 * A selectable external node. Key and nativeId are stable across saves and list
 * reordering; nativeId is the number serialized by upstream block wire endpoints.
 * Position and hitRadius use blocks, not pixels. Structural parts expose no terminals.
 */
public record Terminal(String key, int nativeId, LocalPoint position, double hitRadius) {
    public Terminal {
        ApiValidation.key(key, "terminal key");
        Objects.requireNonNull(position, "terminal position");
        if (nativeId < 0 || nativeId > WWPGApi.MAX_TERMINAL_ID)
            throw new IllegalArgumentException("terminal " + key + " nativeId must be 0.." + WWPGApi.MAX_TERMINAL_ID);
        ApiValidation.positive(hitRadius, "terminal " + key + " hitRadius");
        if (hitRadius > 2) throw new IllegalArgumentException("terminal " + key + " hitRadius must be at most 2 blocks");
    }
}
