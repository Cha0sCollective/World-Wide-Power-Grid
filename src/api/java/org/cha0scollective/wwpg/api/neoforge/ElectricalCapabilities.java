package org.cha0scollective.wwpg.api.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.cha0scollective.wwpg.api.ElectricalDevice;

/** Capability registered by an add-on through NeoForge's RegisterCapabilitiesEvent. */
public final class ElectricalCapabilities {
    /**
     * Stationary device at a block. Query with a null context. Providers must
     * invalidate their native block capabilities after device replacement or a
     * provider-changing configuration edit. Discovery never advances a circuit.
     */
    public static final BlockCapability<ElectricalDevice, Void> DEVICE = BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("wwpg", "electrical_device"), ElectricalDevice.class);

    private ElectricalCapabilities() { }
}
