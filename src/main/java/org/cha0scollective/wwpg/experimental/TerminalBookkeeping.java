package org.cha0scollective.wwpg.experimental;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Opt-in storage for private terminal frames; no owner or topology validity cache. */
public final class TerminalBookkeeping {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.terminalBookkeeping");
    private static final class Counts { long maps, passes, frames; int largest; }
    private static final ThreadLocal<Counts> COUNTS = ThreadLocal.withInitial(Counts::new);
    private TerminalBookkeeping() { }

    public static <K, V> Map<K, V> frameMap() {
        if (!ENABLED) return new HashMap<>();
        COUNTS.get().maps++;
        return new Object2ObjectOpenHashMap<>();
    }

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
            WorldWidePowerGrid.LOGGER.info("WWPG experimental terminal bookkeeping: maps={}, passes={}, frames={}, largest={}",
                    counts.maps, counts.passes, counts.frames, counts.largest);
        }
        COUNTS.remove();
    }
}
