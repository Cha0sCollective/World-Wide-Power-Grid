package org.cha0scollective.wwpg.api;

import java.util.Objects;

/** Persistent device and local node address; terminal list order is irrelevant. */
public record EndpointIdentity(DeviceIdentity device, String nodeKey) {
    public EndpointIdentity {
        Objects.requireNonNull(device, "device");
        ApiValidation.key(nodeKey, "nodeKey");
    }
}
