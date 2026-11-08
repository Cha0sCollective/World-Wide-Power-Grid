package org.cha0scollective.wwpg.benchmark;

/** Adds a finite work-queue stage without weakening graph or healthy-tick acceptance. */
public final class LoadingGate {
    public enum Stage { CHUNKS, WORK, GRAPH, ACCEPTED, COMPLETE }
    private Stage stage = Stage.CHUNKS;
    private final int loadLimit;
    private long stageTick, lastProgressNs, highWater = -1, lastQueued = Long.MAX_VALUE;
    private int healthyTicks;
    public LoadingGate(int chunks, long now, int override) {
        loadLimit = override > 0 ? override : Math.max(600, 3 * chunks);
        lastProgressNs = now;
    }
    public boolean observe(long tick, long now, long progress, boolean chunks, boolean graph, boolean accepted) {
        return observe(tick,now,progress,chunks,graph,accepted,0);
    }
    public boolean observe(long tick, long now, long progress, boolean chunks, boolean graph, boolean accepted, long queued) {
        if (stage == Stage.COMPLETE) return true;
        if (now-lastProgressNs > 30_000_000_000L) throw new IllegalStateException(stage+" made no readiness progress for 30 seconds");
        if (progress > highWater || queued < lastQueued) lastProgressNs = now;
        highWater = Math.max(highWater,progress); lastQueued = queued;
        if (!chunks && stage != Stage.CHUNKS) throw new IllegalStateException("Chunk readiness lost during restoration");
        if (stage == Stage.CHUNKS && chunks) { stage = Stage.WORK; stageTick = tick; }
        if (queued > 0 && (stage == Stage.GRAPH || stage == Stage.ACCEPTED)) {
            stage = Stage.WORK; healthyTicks = 0;
        }
        if (stage == Stage.WORK && queued == 0) { stage = Stage.GRAPH; stageTick = tick; }
        if (stage == Stage.GRAPH && graph) { stage = Stage.ACCEPTED; stageTick = tick; lastProgressNs = now; }
        if (stage == Stage.ACCEPTED) {
            healthyTicks = graph && accepted && queued == 0 ? healthyTicks+1 : 0;
            if (healthyTicks > 0) lastProgressNs = now;
            if (healthyTicks == 100) { stage = Stage.COMPLETE; return true; }
        }
        if (stage == Stage.CHUNKS || stage == Stage.WORK) {
            if (tick > loadLimit) throw new IllegalStateException(stage+" readiness exceeded total "+loadLimit+" ticks");
        } else if (tick-stageTick > 300) throw new IllegalStateException(stage+" readiness exceeded 300 ticks");
        return false;
    }
    public Stage stage() { return stage; }
    public int loadLimit() { return loadLimit; }
    public int healthyTicks() { return healthyTicks; }
}
