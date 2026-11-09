package org.cha0scollective.wwpg.experimental;

import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Opt-in key arithmetic only; maps, ownership checks and sample lifetimes stay unchanged. */
public final class TerminalKeyLookup {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.terminalKeyLookup");
    private static final class Counts { long passes, frames; int largest; }
    private static final ThreadLocal<Counts> COUNTS = ThreadLocal.withInitial(Counts::new);
    private TerminalKeyLookup() { }

    public static void prepared(int frames) {
        if (!ENABLED) return;
        var counts = COUNTS.get();
        counts.passes++;
        counts.frames += frames;
        counts.largest = Math.max(counts.largest, frames);
    }

    public static void reportAndClear() {
        if (ENABLED) {
            var counts = COUNTS.get();
            WorldWidePowerGrid.LOGGER.info("WWPG experimental terminal key lookup: passes={}, frames={}, largest={}",
                    counts.passes, counts.frames, counts.largest);
        }
        COUNTS.remove();
    }
}
