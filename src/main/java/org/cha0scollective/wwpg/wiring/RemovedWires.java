package org.cha0scollective.wwpg.wiring;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Deletion identities for PG wire entities whose chunk can load after their endpoint was removed. */
public final class RemovedWires extends SavedData {
    private final Set<UUID> deleted = new HashSet<>();
    public static RemovedWires get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RemovedWires::new, (tag, provider) -> {
            if (tag.getInt("Version") != 1) throw new IllegalStateException("Unknown WWPG wire deletion format");
            var data = new RemovedWires();
            for (var entry : tag.getList("Deleted", Tag.TAG_INT_ARRAY)) data.deleted.add(NbtUtils.loadUUID(entry));
            return data;
        }), "wwpg_removed_wires");
    }
    public void remember(UUID id) { if (deleted.add(id)) setDirty(); }
    public boolean contains(UUID id) { return deleted.contains(id); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Version", 1);
        var entries = new ListTag();
        deleted.stream().sorted().forEach(id -> entries.add(NbtUtils.createUUID(id)));
        tag.put("Deleted", entries); return tag;
    }
}
