package org.cha0scollective.wwpg.api.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.cha0scollective.wwpg.api.ElectricalDevice;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Startup registration of stationary device providers. Each block has one declared
 * electrical owner. Duplicate claims fail with the block and both registration IDs
 * instead of depending on NeoForge's first non-null capability provider ordering.
 * Register during RegisterCapabilitiesEvent through these helpers; the capability
 * itself remains available for ordinary read-only queries.
 */
public final class ElectricalProviders {
    private static final Map<Block, String> OWNERS = new IdentityHashMap<>();
    private static boolean frozen;

    private ElectricalProviders() {}

    /** Register one namespaced provider for explicitly listed plain or entity blocks. */
    public static synchronized void registerBlock(RegisterCapabilitiesEvent event, String providerId,
            IBlockCapabilityProvider<ElectricalDevice, Void> provider, Block... blocks) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(provider, "provider");
        var targets = declare(event, providerId, blocks);
        event.registerBlock(ElectricalCapabilities.DEVICE, (level, pos, state, blockEntity, context) ->
                checked(providerId, provider.getCapability(level, pos, state, blockEntity, context)), targets);
    }

    /**
     * Register one namespaced provider for every valid block of a native entity type.
     * A type covering several separately named electrical providers should instead
     * use registerBlock for each concrete block.
     */
    public static synchronized <BE extends BlockEntity> void registerBlockEntity(RegisterCapabilitiesEvent event,
            String providerId, BlockEntityType<BE> type, ICapabilityProvider<? super BE, Void, ElectricalDevice> provider) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        declare(event, providerId, type.getValidBlocks().toArray(Block[]::new));
        event.registerBlockEntity(ElectricalCapabilities.DEVICE, type,
                (entity, context) -> checked(providerId, provider.getCapability(entity, context)));
    }

    private static Block[] declare(RegisterCapabilitiesEvent event, String providerId, Block[] blocks) {
        if (frozen) throw new IllegalStateException("WWPG electrical provider registrations are frozen");
        Objects.requireNonNull(providerId, "providerId");
        if (ResourceLocation.tryParse(providerId) == null || providerId.indexOf(':') < 1)
            throw new IllegalArgumentException("Electrical provider requires a namespaced ID: " + providerId);
        Objects.requireNonNull(blocks, "blocks");
        var targets = blocks.clone();
        if (targets.length == 0) throw new IllegalArgumentException("Electrical provider " + providerId + " has no target blocks");
        var unique = java.util.Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        for (var block : targets) {
            Objects.requireNonNull(block, "target block");
            if (!unique.add(block)) throw new IllegalArgumentException("Duplicate target block " + name(block) + " in provider " + providerId);
            var previous = OWNERS.get(block);
            if (previous != null) {
                var owners = new String[]{ previous, providerId };
                Arrays.sort(owners);
                throw new IllegalStateException("Conflicting electrical providers " + owners[0] + " and " + owners[1] + " for " + name(block));
            }
            if (event.isBlockRegistered(ElectricalCapabilities.DEVICE, block))
                throw new IllegalStateException("Provider " + providerId + " cannot claim " + name(block)
                        + ": an undeclared electrical capability provider is already registered; use ElectricalProviders registration helpers");
        }
        for (var block : targets) OWNERS.put(block, providerId);
        return targets;
    }

    private static ElectricalDevice checked(String declaredProvider, ElectricalDevice device) {
        if (device != null && !declaredProvider.equals(device.providerId()))
            throw new IllegalStateException("Electrical provider " + declaredProvider + " returned device owned by " + device.providerId());
        return device;
    }

    /** Declared owner of a block, without querying a world or creating a device. */
    public static synchronized Optional<String> owner(Block block) { return Optional.ofNullable(OWNERS.get(block)); }

    /** Immutable declared targets for loaded-chunk discovery; no world access. */
    public static synchronized Set<Block> registeredBlocks() { return Set.copyOf(OWNERS.keySet()); }

    /**
     * Validate a returned capability against its startup declaration. A direct
     * NeoForge registration bypassing this catalog is not a supported API provider.
     */
    public static synchronized void validate(Block block, ElectricalDevice device) {
        Objects.requireNonNull(device, "device");
        var expected = OWNERS.get(block);
        if (expected == null)
            throw new IllegalStateException("Undeclared electrical provider " + device.providerId() + " for " + name(block)
                    + "; register with ElectricalProviders during RegisterCapabilitiesEvent");
        if (!expected.equals(device.providerId()))
            throw new IllegalStateException("Electrical owner mismatch for " + name(block) + ": declared " + expected + ", returned " + device.providerId());
    }

    /** Runtime lifecycle hook after startup registration; no later additions are accepted. */
    public static synchronized void freeze() { frozen = true; }

    /** Whether the startup registration catalog has been frozen. */
    public static synchronized boolean isFrozen() { return frozen; }

    private static String name(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }
}
