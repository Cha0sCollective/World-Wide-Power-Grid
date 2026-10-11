package org.cha0scollective.wwpg.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.cha0scollective.wwpg.api.LocalPoint;
import org.cha0scollective.wwpg.api.Terminal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.patryk3211.powergrid.electricity.WorldNetworks;

import static org.junit.jupiter.api.Assertions.*;

class ApiTerminalIdentitiesTest {
    private static final ApiTerminalIdentities.Owner OWNER = new ApiTerminalIdentities.Owner(
            new BlockPos(1, 2, 3), "example:machine", "example:power", "main");

    private static Terminal terminal(String key, int id) {
        return new Terminal(key, id, new LocalPoint(.5, .5, .5), .125);
    }

    @Test void restartRetainsAbsentTerminalsAndRejectsRemaps() {
        var data = new ApiTerminalIdentities();
        data.remember(OWNER, List.of(terminal("positive", 0), terminal("negative", 7)));
        data.remember(OWNER, List.of(terminal("positive", 0)));
        var reopened = ApiTerminalIdentities.load(data.save(new CompoundTag(), null));
        assertEquals(Map.of(0, "positive", 7, "negative"), reopened.history(OWNER));
        assertThrows(IllegalArgumentException.class,
                () -> reopened.remember(OWNER, List.of(terminal("different", 7))));
        assertThrows(IllegalArgumentException.class,
                () -> reopened.remember(OWNER, List.of(terminal("positive", 9))));
        assertEquals(Map.of(0, "positive", 7, "negative"), reopened.history(OWNER));
    }

    @Test void newStableTerminalsCanBeAddedWithoutLosingOldHistory() {
        var data = new ApiTerminalIdentities();
        data.remember(OWNER, List.of(terminal("positive", 0)));
        data.remember(OWNER, List.of(terminal("negative", 7)));
        var reopened = ApiTerminalIdentities.load(data.save(new CompoundTag(), null));
        assertEquals(Map.of(0, "positive", 7, "negative"), reopened.history(OWNER));
    }

    @Test void OnlyPermanentRemovalAllowsTerminalIdentityReuse() {
        var data = new ApiTerminalIdentities();
        data.remember(OWNER, List.of(terminal("positive", 0)));
        assertThrows(IllegalArgumentException.class,
                () -> data.remember(OWNER, List.of(terminal("new_positive", 0))));
        data.forget(OWNER);
        data.remember(OWNER, List.of(terminal("new_positive", 0)));
        var reopened = ApiTerminalIdentities.load(data.save(new CompoundTag(), null));
        assertEquals(Map.of(0, "new_positive"), reopened.history(OWNER));
    }

    @Test void unrelatedBlockAndProviderIdentitiesDoNotShareMappings() {
        var data = new ApiTerminalIdentities();
        var other = new ApiTerminalIdentities.Owner(OWNER.position(), "example:other_machine", "example:other_power", "main");
        data.remember(OWNER, List.of(terminal("positive", 0)));
        data.remember(other, List.of(terminal("input", 0)));
        var reopened = ApiTerminalIdentities.load(data.save(new CompoundTag(), null));
        assertEquals(Map.of(0, "positive"), reopened.history(OWNER));
        assertEquals(Map.of(0, "input"), reopened.history(other));
    }

    @Test void futureDataFormatFailsClosed() {
        var tag = new CompoundTag();
        tag.putInt("Version", 3);
        assertThrows(IllegalStateException.class, () -> ApiTerminalIdentities.load(tag));
    }

    @Test void exactWireOwnersAndComplexIndexesSurviveRestartWithoutClaimingNewWires() {
        var data=new ApiTerminalIdentities();data.remember(OWNER,List.of(terminal("positive",0)));
        UUID oldCord=UUID.randomUUID(),newCord=UUID.randomUUID(),oldCee=UUID.randomUUID();
        var pg=Set.<WorldNetworks.PartId>of(new WorldNetworks.SimpleId(oldCord),new WorldNetworks.ComplexId(oldCord,0),new WorldNetworks.ComplexId(oldCord,1));
        data.rememberWires(OWNER,pg,Set.of(oldCee));
        var reopened=ApiTerminalIdentities.load(data.save(new CompoundTag(),null));
        assertEquals(2,reopened.loadedFormat().orElseThrow());
        assertEquals(pg,reopened.pgWires(OWNER));
        assertEquals(Set.of(oldCee),reopened.ceeWires(OWNER));
        assertFalse(reopened.pgWires(OWNER).contains(new WorldNetworks.SimpleId(newCord)));
        assertFalse(reopened.pgWires(OWNER).contains(new WorldNetworks.ComplexId(oldCord,2)));
        reopened.forget(OWNER);
        assertTrue(reopened.pgWires(OWNER).isEmpty());assertTrue(reopened.ceeWires(OWNER).isEmpty());
    }

    @Test void versionOneRetainsTerminalHistoryWithoutInventingWireOwnership() {
        var data=new ApiTerminalIdentities();data.remember(OWNER,List.of(terminal("positive",0)));
        assertTrue(data.loadedFormat().isEmpty());
        var legacy=data.save(new CompoundTag(),null);legacy.putInt("Version",1);
        var reopened=ApiTerminalIdentities.load(legacy);
        assertEquals(1,reopened.loadedFormat().orElseThrow());
        assertEquals(Map.of(0,"positive"),reopened.history(OWNER));
        assertTrue(reopened.pgWires(OWNER).isEmpty());assertTrue(reopened.ceeWires(OWNER).isEmpty());
        assertEquals(2,reopened.save(new CompoundTag(),null).getInt("Version"));
        assertEquals(1,reopened.loadedFormat().orElseThrow(),"Writing the current format must not pretend it was loaded from disk");
    }

    @Test void sameOwnerPendingRemovalSurvivesRestartAndKeepsNewWireIdentitiesSeparate() {
        var data=new ApiTerminalIdentities();data.remember(OWNER,List.of(terminal("positive",0)));
        var oldPg=new WorldNetworks.SimpleId(UUID.randomUUID());var oldCee=UUID.randomUUID();
        var newPg=new WorldNetworks.SimpleId(UUID.randomUUID());var newCee=UUID.randomUUID();
        data.rememberWires(OWNER,Set.of(oldPg),Set.of(oldCee));
        data.queueRemoval(OWNER,Set.of(oldPg),Set.of(oldCee));
        data.forget(OWNER); // a new incarnation may have the exact same stable declaration
        data.remember(OWNER,List.of(terminal("positive",0)));
        data.rememberWires(OWNER,Set.of(newPg),Set.of(newCee));
        var reopened=ApiTerminalIdentities.load(data.save(new CompoundTag(),null));
        assertEquals(Set.of(oldPg),reopened.pendingRemovals().get(OWNER).pgWires());
        assertEquals(Set.of(oldCee),reopened.pendingRemovals().get(OWNER).ceeWires());
        assertEquals(Set.of(newPg),reopened.pgWires(OWNER));assertEquals(Set.of(newCee),reopened.ceeWires(OWNER));
        reopened.finishRemoval(OWNER);
        assertTrue(reopened.pendingRemovals().isEmpty());
        assertEquals(Set.of(newPg),reopened.pgWires(OWNER));assertEquals(Set.of(newCee),reopened.ceeWires(OWNER));
    }

    @Test void pendingRemovalMergeAndLegacyFormatTwoRemainDeterministic() {
        var data=new ApiTerminalIdentities();
        var first=new WorldNetworks.ComplexId(UUID.randomUUID(),0);var second=new WorldNetworks.ComplexId(first.id(),1);
        var cee=UUID.randomUUID();
        data.queueRemoval(OWNER,Set.of(first),Set.of());data.queueRemoval(OWNER,Set.of(second),Set.of(cee));
        var reopened=ApiTerminalIdentities.load(data.save(new CompoundTag(),null));
        assertEquals(Set.of(first,second),reopened.pendingRemovals().get(OWNER).pgWires());
        assertEquals(Set.of(cee),reopened.pendingRemovals().get(OWNER).ceeWires());
        var legacy=data.save(new CompoundTag(),null);legacy.remove("PendingRemovals");
        assertTrue(ApiTerminalIdentities.load(legacy).pendingRemovals().isEmpty());
    }
}
