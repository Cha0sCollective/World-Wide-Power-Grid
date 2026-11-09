package org.cha0scollective.wwpg.experimental;

import net.minecraft.server.MinecraftServer;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Opt-in extra queue service while saved wires or a player's tracked view are loading. */
public final class ChunkTaskPump {
    public static final int BUDGET_MILLIS = Integer.getInteger("wwpg.experimental.chunkTaskBudgetMillis", 0);
    private static final int MAX_TASKS = 512;
    static {
        if (BUDGET_MILLIS < 0 || BUDGET_MILLIS > 20)
            throw new IllegalArgumentException("Chunk task budget must be between 0 and 20 ms");
    }
    private final BoundedTaskWindow window = new BoundedTaskWindow(System::nanoTime);
    private int lastProbeTick = Integer.MIN_VALUE;
    private boolean loading, reported;
    private long windows, completed, emptyPolls, maxWindowNanos, maxPollNanos, probes, maxProbeNanos;
    private int maxCompleted;

    public boolean allowsTime() { return BUDGET_MILLIS > 0 && window.allowsTime(); }

    public void afterWait(MinecraftServer server) {
        if (BUDGET_MILLIS == 0 || reported || !server.isSameThread() || server.isStopped()) return;
        int tick = server.getTickCount();
        if (lastProbeTick == Integer.MIN_VALUE || tick - lastProbeTick >= 10) {
            long start = System.nanoTime();
            loading = needsLoading(server);
            lastProbeTick = tick;
            probes++;
            maxProbeNanos = Math.max(maxProbeNanos, System.nanoTime() - start);
        }
        if (!loading) return;
        var slice = window.run(BUDGET_MILLIS * 1_000_000L, MAX_TASKS, server::pollTask, Thread::yield);
        if (slice.elapsedNanos() == 0) return; // Nested vanilla managed waits keep the outer allowance.
        windows++;
        completed += slice.completed();
        emptyPolls += slice.emptyPolls();
        maxCompleted = Math.max(maxCompleted, slice.completed());
        maxWindowNanos = Math.max(maxWindowNanos, slice.elapsedNanos());
        maxPollNanos = Math.max(maxPollNanos, slice.longestPollNanos());
    }

    private static boolean needsLoading(MinecraftServer server) {
        for (var level : server.getAllLevels()) if (WireTreeBatch.pending(level) > 0) return true;
        if (server.getPlayerList() == null) return false;
        for (var player : server.getPlayerList().getPlayers()) {
            var map = player.serverLevel().getChunkSource().chunkMap;
            boolean[] missing = {false};
            // Observe only existing holders/futures. Do not add tickets or request chunks.
            player.getChunkTrackingView().forEach(pos -> {
                if (missing[0]) return;
                var holder = map.getVisibleChunkIfPresent(pos.toLong());
                if (holder == null || holder.getChunkToSend() == null) missing[0] = true;
            });
            if (missing[0]) return true;
        }
        return false;
    }

    public void report() {
        if (BUDGET_MILLIS == 0 || reported) return;
        reported = true;
        WorldWidePowerGrid.LOGGER.info("WWPG experimental chunk-task window: budgetMillis={}, maxTasks={}, windows={}, completed={}, emptyPolls={}, maxCompleted={}, maxWindowNanos={}, maxPollNanos={}, probes={}, maxProbeNanos={}",
                BUDGET_MILLIS, MAX_TASKS, windows, completed, emptyPolls, maxCompleted,
                maxWindowNanos, maxPollNanos, probes, maxProbeNanos);
    }
}
