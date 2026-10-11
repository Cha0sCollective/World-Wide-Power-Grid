package org.cha0scollective.wwpg.extension;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Internal identities for API-observed native CEE wires, which have no native UUID. */
public final class ApiCeeWireIdentities extends SavedData {
    private static final int FORMAT = 1;
    private final ServerLevel level;
    private final Map<InWorldNodeConnection, UUID> byConnection = new HashMap<>();
    private final Map<UUID, InWorldNodeConnection> byIdentity = new HashMap<>();

    ApiCeeWireIdentities(ServerLevel level) { this.level = level; }

    public static ApiCeeWireIdentities get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(() -> new ApiCeeWireIdentities(level),
                (tag, registries) -> load(tag, level)), "wwpg_api_cee_wire_identities");
    }

    /** Called only after observing this physical connection as owned by an API device. */
    UUID remember(InWorldNodeConnection connection) {
        var stable = stable(connection);
        var previous = byConnection.get(stable);
        return previous != null ? previous : reconnected(stable);
    }

    public Optional<UUID> id(InWorldNodeConnection connection) {
        if (connection == null || connection.node1().id() < 0 || connection.node2().id() < 0) return Optional.empty();
        return Optional.ofNullable(byConnection.get(stable(connection)));
    }

    /** Ledger-only snapshot; does not read native data, query a capability, or create a holder. */
    public Set<UUID> idsAt(BlockPos position) {
        return byConnection.entrySet().stream().filter(entry -> entry.getKey().node1().sourcePos().equals(position)
                || entry.getKey().node2().sourcePos().equals(position)).map(Map.Entry::getValue).collect(Collectors.toUnmodifiableSet());
    }

    /** Resolve an exact current physical connection; never recreate native nodes or load chunks. */
    Optional<InWorldNodeConnection> currentConnection(UUID identity) {
        var connection = byIdentity.get(Objects.requireNonNull(identity, "identity"));
        if (connection == null || level == null || InfrastructureSavedData.load(level).getConnectionData(connection) == null)
            return Optional.empty();
        return Optional.of(connection);
    }

    /** Pure ledger lookup used by serialization checks; physical presence is checked separately. */
    Optional<InWorldNodeConnection> recordedConnection(UUID identity) {
        return Optional.ofNullable(byIdentity.get(Objects.requireNonNull(identity, "identity")));
    }

    /** A fresh accepted connect creates a new identity even when the endpoint pair is reused. */
    UUID reconnected(InWorldNodeConnection connection) {
        var stable = stable(connection);
        var previous = byConnection.remove(stable);
        if (previous != null) byIdentity.remove(previous);
        UUID identity;
        do { identity = UUID.randomUUID(); } while (byIdentity.containsKey(identity));
        byConnection.put(stable, identity);
        byIdentity.put(identity, stable);
        setDirty();
        return identity;
    }

    void forget(InWorldNodeConnection connection) {
        var previous = byConnection.remove(stable(connection));
        if (previous != null) { byIdentity.remove(previous); setDirty(); }
    }

    /** Native connect hook. Empty native-only installations acquire no ledger entries. */
    public static void connected(ServerLevel level, InWorldNode first, InWorldNode second) {
        // Native reserved endpoints and construction-time null levels are outside
        // the extension terminal model; never validate or change their behavior.
        if (level == null || first == null || second == null || first.id() < 0 || second.id() < 0) return;
        // A native CEE replacement can bypass PG's geometry lookup. Freeze the
        // previous owner's tokens before publishing this freshly accepted wire.
        ApiTerminals.captureNativeReplacement(level, first.sourcePos());
        ApiTerminals.captureNativeReplacement(level, second.sourcePos());
        var connection = new InWorldNodeConnection(first, second);
        var identities = get(level);
        if (identities.id(connection).isEmpty() && !apiObserved(level, first.sourcePos()) && !apiObserved(level, second.sourcePos())) return;
        identities.reconnected(connection);
    }

    private static boolean apiObserved(ServerLevel level, BlockPos position) {
        return ApiTerminals.existing(level, position) != null || !ApiTerminalIdentities.get(level).ownersAt(position).isEmpty();
    }

    /** Native remove hook; replacing wire data in-place deliberately does not call this. */
    public static void removed(ServerLevel level, InWorldNodeConnection connection) {
        if (level == null || connection == null || connection.node1().id() < 0 || connection.node2().id() < 0) return;
        var identities = get(level);
        if (identities.id(connection).isPresent()) identities.forget(connection);
    }

    private static InWorldNodeConnection stable(InWorldNodeConnection connection) {
        Objects.requireNonNull(connection, "connection");
        return new InWorldNodeConnection(stable(connection.node1()), stable(connection.node2()));
    }

    private static InWorldNode stable(InWorldNode node) {
        if (node.id() < 0) throw new IllegalArgumentException("A saved CEE wire endpoint requires a nonnegative native terminal ID");
        return new InWorldNode(node.id(), node.sourcePos().immutable());
    }

    static ApiCeeWireIdentities load(CompoundTag tag, ServerLevel level) {
        if (tag.getInt("Version") != FORMAT)
            throw new IllegalStateException("Unknown WWPG CEE wire identity format " + tag.getInt("Version"));
        var result = new ApiCeeWireIdentities(level);
        for (var value : tag.getList("Wires", Tag.TAG_COMPOUND)) {
            var wire = (CompoundTag) value;
            if (!wire.hasUUID("UUID")) throw new IllegalStateException("Missing saved CEE wire identity");
            var identity = wire.getUUID("UUID");
            var endpoints = wire.getIntArray("Endpoints");
            if (endpoints.length != 8 || endpoints[0] < 0 || endpoints[4] < 0)
                throw new IllegalStateException("Invalid saved CEE wire endpoints");
            var first = new InWorldNode(endpoints[0], new BlockPos(endpoints[1], endpoints[2], endpoints[3]));
            var second = new InWorldNode(endpoints[4], new BlockPos(endpoints[5], endpoints[6], endpoints[7]));
            if (first.equals(second)) throw new IllegalStateException("A saved CEE wire cannot join a terminal to itself");
            var connection = new InWorldNodeConnection(first, second);
            if (result.byConnection.putIfAbsent(connection, identity) != null || result.byIdentity.putIfAbsent(identity, connection) != null)
                throw new IllegalStateException("Duplicate saved CEE wire identity or endpoint pair");
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", FORMAT);
        var wires = new ListTag();
        byConnection.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var first = entry.getKey().node1(); var second = entry.getKey().node2();
            var wire = new CompoundTag();
            wire.putUUID("UUID", entry.getValue());
            wire.putIntArray("Endpoints", new int[]{first.id(), first.sourcePos().getX(), first.sourcePos().getY(), first.sourcePos().getZ(),
                    second.id(), second.sourcePos().getX(), second.sourcePos().getY(), second.sourcePos().getZ()});
            wires.add(wire);
        });
        tag.put("Wires", wires);
        return tag;
    }
}
