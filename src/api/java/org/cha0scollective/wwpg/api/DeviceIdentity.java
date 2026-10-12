package org.cha0scollective.wwpg.api;

/**
 * Complete stable location of an extension device. WWPG derives this identity
 * from the containing block and provider metadata; no graph index is persistent.
 */
public record DeviceIdentity(String dimensionId, int x, int y, int z, String providerId, String slotId) {
    /**
     * Constructs DeviceIdentity with validated captured values.
     * @param dimensionId namespaced lowercase ID, at most 256 characters, matching [a-z0-9_.-]+:[a-z0-9_./-]+
     * @param x block X coordinate; any int
     * @param y block Y coordinate; any int
     * @param z block Z coordinate; any int
     * @param providerId namespaced lowercase ID with the same grammar and limit as dimensionId
     * @param slotId stable local key, 1..128 characters matching [a-z0-9_.:/-]+
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
    public DeviceIdentity {
        ApiValidation.resourceId(dimensionId, "dimensionId");
        ApiValidation.resourceId(providerId, "providerId");
        ApiValidation.key(slotId, "slotId");
    }
}
