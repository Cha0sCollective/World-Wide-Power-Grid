package org.cha0scollective.wwpg.gametest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import java.lang.reflect.Field;
import java.util.*;

/** GameTest-only IO gate: delay consumption of real disk results, never their data.
 * Other chunks continue loading. No timing hook is installed during ordinary play.
 */
final class DelayedEntityLoads {
    static void hold(ServerLevel level, ChunkPos chunk, int ticks) {
        try {
            var managerField = ServerLevel.class.getDeclaredField("entityManager");
            managerField.setAccessible(true);
            var manager = managerField.get(level);
            var inboxField = PersistentEntitySectionManager.class.getDeclaredField("loadingInbox");
            inboxField.setAccessible(true);
            var inbox = inboxField.get(manager);
            Gate gate;
            if (inbox instanceof Gate existing) gate = existing;
            else {
                @SuppressWarnings("unchecked") var queue = (Queue<ChunkEntities<?>>) inbox;
                gate = new Gate(level, manager, inboxField, queue);
                inboxField.set(manager, gate);
            }
            gate.until.put(chunk.toLong(), level.getGameTime() + ticks);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot install the pinned Minecraft entity-load test gate", error);
        }
    }

    /** Release a test-only gate during shutdown, after evidence is saved and before entity-manager close. */
    static void release(ServerLevel level,ChunkPos chunk) {
        try {
            var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);
            var manager=managerField.get(level);var inboxField=PersistentEntitySectionManager.class.getDeclaredField("loadingInbox");inboxField.setAccessible(true);
            if(inboxField.get(manager) instanceof Gate gate) {
                gate.until.remove(chunk.toLong());
                for(var iterator=gate.held.iterator();iterator.hasNext();) {
                    var result=iterator.next();
                    if(result.getPos().equals(chunk)) {iterator.remove();gate.delegate.offer(result);}
                }
            }
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("Cannot release the pinned entity-load test gate",error);}
    }

    /** Actual deserialized entities awaiting consumption by the server, without releasing the gate. */
    static List<net.minecraft.world.entity.Entity> heldEntities(ServerLevel level,ChunkPos chunk) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Inspect the entity inbox on the server thread");
        try {
            var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);
            var manager=managerField.get(level);var inboxField=PersistentEntitySectionManager.class.getDeclaredField("loadingInbox");inboxField.setAccessible(true);
            if(!(inboxField.get(manager) instanceof Gate gate))return List.of();
            var entities=new ArrayList<net.minecraft.world.entity.Entity>();
            // The delegate is Minecraft's concurrent IO inbox; held entries are
            // server-owned. Inspect both because IO may finish between ticks.
            for(var result:gate)if(result.getPos().equals(chunk))result.getEntities().forEach(entity->{
                if(entity instanceof net.minecraft.world.entity.Entity actual)entities.add(actual);
            });
            return List.copyOf(entities);
        }catch(ReflectiveOperationException error) {throw new IllegalStateException("Cannot inspect the pinned entity-load test gate",error);}
    }

    private static final class Gate extends AbstractQueue<ChunkEntities<?>> {
        final ServerLevel level;
        final Object manager;
        final Field field;
        final Queue<ChunkEntities<?>> delegate;
        final Deque<ChunkEntities<?>> held = new ArrayDeque<>();
        final Map<Long, Long> until = new HashMap<>();
        Gate(ServerLevel level, Object manager, Field field, Queue<ChunkEntities<?>> delegate) {
            this.level = level; this.manager = manager; this.field = field; this.delegate = delegate;
        }
        boolean blocked(ChunkEntities<?> result) {
            return until.getOrDefault(result.getPos().toLong(), Long.MIN_VALUE) > level.getGameTime();
        }
        @Override public boolean offer(ChunkEntities<?> result) { return delegate.offer(result); }
        @Override public ChunkEntities<?> poll() {
            until.values().removeIf(deadline -> deadline <= level.getGameTime());
            for (var it = held.iterator(); it.hasNext();) {
                var result = it.next();
                if (!blocked(result)) { it.remove(); return result; }
            }
            ChunkEntities<?> result;
            while ((result = delegate.poll()) != null) {
                if (!blocked(result)) return result;
                held.addLast(result);
            }
            if (until.isEmpty() && held.isEmpty()) {
                try { if (field.get(manager) == this) field.set(manager, delegate); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            }
            return null;
        }
        @Override public ChunkEntities<?> peek() { return held.isEmpty() ? delegate.peek() : held.peekFirst(); }
        @Override public int size() { return delegate.size() + held.size(); }
        @Override public Iterator<ChunkEntities<?>> iterator() {
            var snapshot = new ArrayList<>(held); snapshot.addAll(delegate); return snapshot.iterator();
        }
    }
}
