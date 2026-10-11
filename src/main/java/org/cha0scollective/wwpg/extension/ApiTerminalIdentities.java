package org.cha0scollective.wwpg.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.cha0scollective.wwpg.api.Terminal;
import org.patryk3211.powergrid.electricity.WorldNetworks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned API identities only; native beta worlds and graph ordinals are untouched. */
final class ApiTerminalIdentities extends SavedData {
    private static final int FORMAT = 2;
    private int loadedFormat;
    private final Map<Owner, Map<Integer, String>> identities = new HashMap<>();
    private final Map<Owner, Set<WorldNetworks.PartId>> pgWires = new HashMap<>();
    private final Map<Owner, Set<UUID>> ceeWires = new HashMap<>();
    private final Map<Owner, WireOwnership> pendingRemovals = new HashMap<>();

    record WireOwnership(Set<WorldNetworks.PartId> pgWires, Set<UUID> ceeWires) {
        WireOwnership { pgWires=Set.copyOf(pgWires); ceeWires=Set.copyOf(ceeWires); }
    }

    record Owner(BlockPos position, String block, String provider, String slot) {
        Owner { position = position.immutable(); }
    }

    static ApiTerminalIdentities get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(ApiTerminalIdentities::new,
                (tag, registries) -> load(tag)), "wwpg_api_terminal_identities");
    }

    Map<Integer, String> history(Owner owner) {
        return Map.copyOf(identities.getOrDefault(owner, Map.of()));
    }

    List<Owner> ownersAt(BlockPos position) {
        return identities.keySet().stream().filter(owner -> owner.position().equals(position)).toList();
    }
    List<Owner> owners() { return List.copyOf(identities.keySet()); }

    /** The actual deserialized format; absent for a newly created, unsaved store. */
    java.util.OptionalInt loadedFormat() {
        return loadedFormat == 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(loadedFormat);
    }

    Set<WorldNetworks.PartId> pgWires(Owner owner) { return pgWires.getOrDefault(owner, Set.of()); }
    Set<UUID> ceeWires(Owner owner) { return ceeWires.getOrDefault(owner, Set.of()); }

    Map<Owner, WireOwnership> pendingRemovals() { return Map.copyOf(pendingRemovals); }

    /** A physical removal is durable even when native wire entities cannot yet be retired. */
    void queueRemoval(Owner owner, Set<WorldNetworks.PartId> pg, Set<UUID> cee) {
        var old=pendingRemovals.get(owner);
        var joinedPg=new java.util.HashSet<>(pg);var joinedCee=new java.util.HashSet<>(cee);
        if(old!=null) {joinedPg.addAll(old.pgWires());joinedCee.addAll(old.ceeWires());}
        var next=new WireOwnership(joinedPg,joinedCee);
        validatePgWires(next.pgWires());
        if(!next.equals(old)) {pendingRemovals.put(owner,next);setDirty();}
    }

    void finishRemoval(Owner owner) {
        if(pendingRemovals.remove(owner)!=null)setDirty();
    }

    private static void validatePgWires(Set<WorldNetworks.PartId> wires) {
        for(var id:wires)
            if(!(id instanceof WorldNetworks.SimpleId)&&!(id instanceof WorldNetworks.ComplexId complex&&complex.sub()>=0))
                throw new IllegalArgumentException("Unsupported PG wire ownership identity");
    }

    void rememberWires(Owner owner, Set<WorldNetworks.PartId> pg, Set<UUID> cee) {
        if (!identities.containsKey(owner)) throw new IllegalArgumentException("Wire ownership requires a validated API terminal owner");
        var nextPg = Set.copyOf(pg); var nextCee = Set.copyOf(cee);
        validatePgWires(nextPg);
        if (!nextPg.equals(pgWires.getOrDefault(owner, Set.of())) || !nextCee.equals(ceeWires.getOrDefault(owner, Set.of()))) {
            pgWires.put(owner, nextPg); ceeWires.put(owner, nextCee); setDirty();
        }
    }

    /** Validate before changing this record, so a failed declaration is never partly accepted. */
    void remember(Owner owner, List<Terminal> terminals) {
        var next = new HashMap<>(identities.getOrDefault(owner, Map.of()));
        validate(next, terminals);
        for (var terminal : terminals) next.putIfAbsent(terminal.nativeId(), terminal.key());
        if (!next.equals(identities.get(owner))) {
            identities.put(owner, Map.copyOf(next));
            setDirty();
        }
    }

    static void validate(Map<Integer, String> previous, List<Terminal> terminals) {
        var byKey = new HashMap<String, Integer>();
        previous.forEach((nativeId, key) -> byKey.put(key, nativeId));
        var ids = new java.util.HashSet<Integer>();
        var keys = new java.util.HashSet<String>();
        for (var terminal : terminals) {
            if (terminal == null || !ids.add(terminal.nativeId()) || !keys.add(terminal.key()))
                throw new IllegalArgumentException("Duplicate or null API terminal identity");
            String previousKey = previous.get(terminal.nativeId());
            if (previousKey != null && !previousKey.equals(terminal.key()))
                throw new IllegalArgumentException("Native terminal " + terminal.nativeId()
                        + " changed saved key from " + previousKey + " to " + terminal.key());
            Integer previousId = byKey.get(terminal.key());
            if (previousId != null && previousId != terminal.nativeId())
                throw new IllegalArgumentException("Terminal " + terminal.key()
                        + " changed saved native identity from " + previousId + " to " + terminal.nativeId());
        }
    }

    void forget(Owner owner) {
        if (identities.remove(owner) != null) { pgWires.remove(owner); ceeWires.remove(owner); setDirty(); }
    }

    static ApiTerminalIdentities load(CompoundTag tag) {
        int version = tag.getInt("Version");
        if (version != 1 && version != FORMAT)
            throw new IllegalStateException("Unknown WWPG API terminal identity format " + tag.getInt("Version"));
        var result = new ApiTerminalIdentities();
        result.loadedFormat = version;
        for (var generic : tag.getList("Devices", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) generic;
            var position = entry.getIntArray("Position");
            if (position.length != 3) throw new IllegalStateException("Invalid saved API device position");
            var owner = new Owner(new BlockPos(position[0], position[1], position[2]), entry.getString("Block"),
                    entry.getString("Provider"), entry.getString("Slot"));
            if (owner.block().isBlank() || owner.provider().isBlank() || owner.slot().isBlank())
                throw new IllegalStateException("Invalid saved API device owner");
            var terminals = new HashMap<Integer, String>();
            var keys = new java.util.HashSet<String>();
            for (var genericTerminal : entry.getList("Terminals", Tag.TAG_COMPOUND)) {
                var terminal = (CompoundTag) genericTerminal;
                int id = terminal.getInt("NativeId");
                String key = terminal.getString("Key");
                if (id < 0 || id > 4095 || key.isBlank() || !keys.add(key) || terminals.putIfAbsent(id, key) != null)
                    throw new IllegalStateException("Invalid saved API terminal identity");
            }
            if (result.identities.putIfAbsent(owner, Map.copyOf(terminals)) != null)
                throw new IllegalStateException("Duplicate saved API device identity");
            if (version == 1) continue;
            var pg = new java.util.HashSet<WorldNetworks.PartId>();
            for (var genericWire : entry.getList("PgWires", Tag.TAG_COMPOUND)) {
                var wire = (CompoundTag) genericWire;
                if (!wire.hasUUID("UUID")) throw new IllegalStateException("Invalid saved PG wire identity");
                int sub = wire.getInt("Sub");
                if (sub < -1) throw new IllegalStateException("Invalid saved PG wire sub-index");
                WorldNetworks.PartId id = sub == -1 ? new WorldNetworks.SimpleId(wire.getUUID("UUID"))
                        : new WorldNetworks.ComplexId(wire.getUUID("UUID"), sub);
                if (!pg.add(id)) throw new IllegalStateException("Duplicate saved PG wire identity");
            }
            result.pgWires.put(owner, Set.copyOf(pg));
            var cee = new java.util.HashSet<UUID>();
            for (var genericWire : entry.getList("CeeWires", Tag.TAG_INT_ARRAY))
                if (!cee.add(net.minecraft.nbt.NbtUtils.loadUUID(genericWire))) throw new IllegalStateException("Duplicate saved CEE wire identity");
            result.ceeWires.put(owner, Set.copyOf(cee));
        }
        if(version==FORMAT)for(var generic:tag.getList("PendingRemovals",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)generic;var position=entry.getIntArray("Position");
            if(position.length!=3)throw new IllegalStateException("Invalid pending API removal position");
            var owner=new Owner(new BlockPos(position[0],position[1],position[2]),entry.getString("Block"),entry.getString("Provider"),entry.getString("Slot"));
            if(owner.block().isBlank()||owner.provider().isBlank()||owner.slot().isBlank())throw new IllegalStateException("Invalid pending API removal owner");
            var pg=new java.util.HashSet<WorldNetworks.PartId>();
            for(var genericWire:entry.getList("PgWires",Tag.TAG_COMPOUND)) {
                var wire=(CompoundTag)genericWire;
                if(!wire.hasUUID("UUID")||wire.getInt("Sub") < -1)throw new IllegalStateException("Invalid pending PG wire identity");
                WorldNetworks.PartId id=wire.getInt("Sub")==-1?new WorldNetworks.SimpleId(wire.getUUID("UUID")):new WorldNetworks.ComplexId(wire.getUUID("UUID"),wire.getInt("Sub"));
                if(!pg.add(id))throw new IllegalStateException("Duplicate pending PG wire identity");
            }
            var cee=new java.util.HashSet<UUID>();
            for(var genericWire:entry.getList("CeeWires",Tag.TAG_INT_ARRAY))
                if(!cee.add(net.minecraft.nbt.NbtUtils.loadUUID(genericWire)))throw new IllegalStateException("Duplicate pending CEE wire identity");
            if(result.pendingRemovals.putIfAbsent(owner,new WireOwnership(pg,cee))!=null)throw new IllegalStateException("Duplicate pending API removal owner");
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", FORMAT);
        var devices = new ListTag();
        identities.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> entry.getKey().position().toShortString()
                + "/" + entry.getKey().block() + "/" + entry.getKey().provider() + "/" + entry.getKey().slot())).forEach(saved -> {
            var owner = saved.getKey();
            var entry = new CompoundTag();
            entry.putIntArray("Position", new int[]{owner.position().getX(), owner.position().getY(), owner.position().getZ()});
            entry.putString("Block", owner.block());
            entry.putString("Provider", owner.provider());
            entry.putString("Slot", owner.slot());
            var terminals = new ListTag();
            saved.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(identity -> {
                var terminal = new CompoundTag();
                terminal.putInt("NativeId", identity.getKey());
                terminal.putString("Key", identity.getValue());
                terminals.add(terminal);
            });
            entry.put("Terminals", terminals);
            var pg = new ListTag();
            pgWires(owner).stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(id -> {
                var wire = new CompoundTag();
                if (id instanceof WorldNetworks.SimpleId simple) { wire.putUUID("UUID", simple.id()); wire.putInt("Sub", -1); }
                else if (id instanceof WorldNetworks.ComplexId complex) { wire.putUUID("UUID", complex.id()); wire.putInt("Sub", complex.sub()); }
                else throw new IllegalStateException("Unknown native PG wire identity type");
                pg.add(wire);
            });
            entry.put("PgWires", pg);
            var cee = new ListTag();
            ceeWires(owner).stream().sorted().forEach(id -> cee.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            entry.put("CeeWires", cee);
            devices.add(entry);
        });
        tag.put("Devices", devices);
        var pending=new ListTag();
        pendingRemovals.entrySet().stream().sorted(java.util.Comparator.comparing(entry->entry.getKey().position().toShortString()
                +"/"+entry.getKey().block()+"/"+entry.getKey().provider()+"/"+entry.getKey().slot())).forEach(saved->{
            var owner=saved.getKey();var entry=new CompoundTag();
            entry.putIntArray("Position",new int[]{owner.position().getX(),owner.position().getY(),owner.position().getZ()});
            entry.putString("Block",owner.block());entry.putString("Provider",owner.provider());entry.putString("Slot",owner.slot());
            var pg=new ListTag();
            saved.getValue().pgWires().stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(id->{
                var wire=new CompoundTag();
                if(id instanceof WorldNetworks.SimpleId simple) {wire.putUUID("UUID",simple.id());wire.putInt("Sub",-1);}
                else if(id instanceof WorldNetworks.ComplexId complex) {wire.putUUID("UUID",complex.id());wire.putInt("Sub",complex.sub());}
                else throw new IllegalStateException("Unknown pending PG wire identity type");
                pg.add(wire);
            });
            entry.put("PgWires",pg);
            var cee=new ListTag();saved.getValue().ceeWires().stream().sorted().forEach(id->cee.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            entry.put("CeeWires",cee);pending.add(entry);
        });
        tag.put("PendingRemovals",pending);
        return tag;
    }
}
