package org.cha0scollective.wwpg.extension;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiCeeWireIdentitiesTest {
    private static InWorldNodeConnection wire(int nativeId) {
        return new InWorldNodeConnection(new InWorldNode(nativeId, new BlockPos(8, 64, 12)),
                new InWorldNode(2, new BlockPos(11, 65, 15)));
    }

    @Test void savedIdentityUsesStableEndpointPairAndSurvivesReloadWithoutAnOrdinal() {
        var ledger = new ApiCeeWireIdentities(null);
        var connection = wire(19);
        var identity = ledger.remember(connection);
        assertEquals(identity, ledger.remember(connection), "Scanning an unchanged wire must retain its identity");
        var saved = ledger.save(new CompoundTag(), null);
        assertEquals(1, saved.getInt("Version"));
        assertFalse(saved.getList("Wires", Tag.TAG_COMPOUND).getCompound(0).contains("Ordinal"));
        var restored = ApiCeeWireIdentities.load(saved, null);
        var reversed = new InWorldNodeConnection(connection.node2(), connection.node1());
        assertEquals(identity, restored.id(reversed).orElseThrow());
        assertEquals(connection, restored.recordedConnection(identity).orElseThrow());
        assertEquals(Set.of(identity), restored.idsAt(new BlockPos(8, 64, 12)));
        assertTrue(restored.idsAt(BlockPos.ZERO).isEmpty());
    }

    @Test void reconnectDoesNotReuseOldOwnerIdentityAndInPlaceObservationDoesNotChangeIt() {
        var ledger = new ApiCeeWireIdentities(null);
        var connection = wire(19);
        var old = ledger.remember(connection);
        var fresh = ledger.reconnected(connection);
        assertNotEquals(old, fresh);
        assertTrue(ledger.recordedConnection(old).isEmpty(), "Old-owner cleanup must not resolve the replacement wire");
        assertEquals(fresh, ledger.remember(connection), "An in-place native wire-data update keeps physical identity");
        ledger.forget(connection);
        assertTrue(ledger.id(connection).isEmpty());
        assertTrue(ledger.recordedConnection(fresh).isEmpty());
        assertNotEquals(fresh, ledger.remember(connection), "A removed and rewired endpoint pair creates a fresh identity");
    }

    @Test void lookupDoesNotCreateRecordsAndSeparateNativeIdsRemainSeparateConnections() {
        var ledger = new ApiCeeWireIdentities(null);
        assertTrue(ledger.id(wire(1)).isEmpty());
        assertEquals(0, ledger.save(new CompoundTag(), null).getList("Wires", Tag.TAG_COMPOUND).size());
        var first = ledger.remember(wire(1)); var second = ledger.remember(wire(19));
        assertNotEquals(first, second);
        assertEquals(Set.of(first, second), ledger.idsAt(new BlockPos(11, 65, 15)));
    }

    @Test void corruptedVersionsAndDuplicateIdentitiesAreRejected() {
        var invalid = new CompoundTag(); invalid.putInt("Version", 2);
        assertThrows(IllegalStateException.class, () -> ApiCeeWireIdentities.load(invalid, null));
        var ledger = new ApiCeeWireIdentities(null); ledger.remember(wire(19));
        var saved = ledger.save(new CompoundTag(), null);
        var entries = saved.getList("Wires", Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> ApiCeeWireIdentities.load(saved, null));
    }

    @Test void nativeReservedEndpointsAndConstructionTimeNullLevelsRemainUnchanged() {
        var ledger = new ApiCeeWireIdentities(null);
        var nativeReserved = wire(-1);
        assertTrue(ledger.id(nativeReserved).isEmpty());
        assertDoesNotThrow(() -> ApiCeeWireIdentities.connected(null, nativeReserved.node1(), nativeReserved.node2()));
        assertDoesNotThrow(() -> ApiCeeWireIdentities.removed(null, nativeReserved));
        assertEquals(0, ledger.save(new CompoundTag(), null).getList("Wires", Tag.TAG_COMPOUND).size());
    }

    @Test void frozenOldOwnerSnapshotCannotAcquireWiresPlacedAfterReplacement() {
        var ledger = new ApiCeeWireIdentities(null);
        var position = new BlockPos(8, 64, 12);
        var oldConnection = wire(19);
        var old = ledger.remember(oldConnection);
        var frozenOldOwner = ledger.idsAt(position);
        var replacement = ledger.reconnected(oldConnection);
        var additional = ledger.remember(wire(1));
        assertEquals(Set.of(old), frozenOldOwner, "A replacement cleanup snapshot must remain immutable");
        assertTrue(ledger.recordedConnection(old).isEmpty());
        assertEquals(Set.of(replacement, additional), ledger.idsAt(position));
        assertFalse(frozenOldOwner.contains(replacement));
        assertFalse(frozenOldOwner.contains(additional));
    }
}
