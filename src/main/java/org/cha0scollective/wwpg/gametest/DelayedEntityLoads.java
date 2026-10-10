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
