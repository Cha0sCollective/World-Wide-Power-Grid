package org.cha0scollective.wwpg.clientdelivery;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Observer bookkeeping only. Never polls, runs, reorders or submits a game task. */
public final class TaskQueueTimings {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.clientBenchmark.inspectTaskQueues");
    private static final IdentityHashMap<Object, IdentityHashMap<Runnable, Long>> ENQUEUED = new IdentityHashMap<>();
    private static final ThreadLocal<ArrayDeque<Frame>> RUNNING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ConcurrentLinkedQueue<Completed> COMPLETED = new ConcurrentLinkedQueue<>();
    public record Completed(long wall_unix_ms, String queue, String thread, String task_class,
                            long wait_nanos, long execution_nanos, long exclusive_execution_nanos) { }
    private static final class Frame {
        final String queue;
        final long started, wait;
        long nested;
        Frame(String queue, long started, long wait) { this.queue = queue; this.started = started; this.wait = wait; }
    }
    private TaskQueueTimings() { }

    private static String kind(Object owner) {
        String name = owner.getClass().getName();
        if (name.equals("net.minecraft.client.server.IntegratedServer")) return "server";
        if (name.equals("net.minecraft.server.level.ServerChunkCache$MainThreadExecutor")) return "chunk";
        return null;
    }
    public static void enqueue(Object owner, Runnable task) {
        if (!ENABLED || kind(owner) == null) return;
        synchronized (ENQUEUED) {
            ENQUEUED.computeIfAbsent(owner, ignored -> new IdentityHashMap<>()).put(task, System.nanoTime());
        }
    }
    public static void start(Object owner, Runnable task) {
        if (!ENABLED) return;
        String queue = kind(owner);
        if (queue == null) return;
        long now = System.nanoTime();
        Long queued;
        synchronized (ENQUEUED) {
            var entries = ENQUEUED.get(owner);
            queued = entries == null ? null : entries.remove(task);
        }
        RUNNING.get().push(new Frame(queue, now, queued == null ? -1 : now - queued));
    }
    public static void finish(Object owner, Runnable task) {
        if (!ENABLED || kind(owner) == null) return;
        var stack = RUNNING.get();
        var frame = stack.pop();
        long duration = System.nanoTime() - frame.started;
        if (!stack.isEmpty()) stack.peek().nested += duration;
        COMPLETED.add(new Completed(System.currentTimeMillis(), frame.queue, Thread.currentThread().getName(),
                task.getClass().getName(), frame.wait, duration, Math.max(0, duration - frame.nested)));
    }
    public static Completed pollCompleted() { return COMPLETED.poll(); }
}
