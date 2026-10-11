package org.cha0scollective.wwpg.api;

/**
 * Complete stable location of an extension device. WWPG derives this identity
 * from the containing block and provider metadata; no graph index is persistent.
 */
public record DeviceIdentity(String dimensionId, int x, int y, int z, String providerId, String slotId) {
    public DeviceIdentity {
        ApiValidation.resourceId(dimensionId, "dimensionId");
        ApiValidation.resourceId(providerId, "providerId");
        ApiValidation.key(slotId, "slotId");
    }
}
