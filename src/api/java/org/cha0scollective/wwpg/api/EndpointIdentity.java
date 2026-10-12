package org.cha0scollective.wwpg.api;

import java.util.Objects;

/** Persistent device and local node address; terminal list order is irrelevant. */
public record EndpointIdentity(DeviceIdentity device, String nodeKey) {
    /**
     * Constructs EndpointIdentity with validated captured values.
     * @param device non-null complete stable device address
     * @param nodeKey stable local key, 1..128 characters matching [a-z0-9_.:/-]+
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
    public EndpointIdentity {
        Objects.requireNonNull(device, "device");
        ApiValidation.key(nodeKey, "nodeKey");
    }
}
