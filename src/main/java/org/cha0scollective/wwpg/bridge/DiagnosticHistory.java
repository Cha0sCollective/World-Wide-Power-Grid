package org.cha0scollective.wwpg.bridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Bounded server-thread history; eviction never replenishes the warning budget. */
final class DiagnosticHistory<K> {
    static final int LIMIT = 128, REPORTS_PER_WINDOW = 8, WINDOW_TICKS = 200;
    private final LinkedHashMap<K, Entry<K>> entries = new LinkedHashMap<>();
    private final BiFunction<K, Integer, String> describe;
    private final Consumer<String> warning;
    private long windowStart = Long.MIN_VALUE, repeated, deferredNew, evicted, occurrences;
    private int reports;

    DiagnosticHistory(BiFunction<K, Integer, String> describe, Consumer<String> warning) {
        this.describe = describe; this.warning = warning;
    }

    void advance(long tick) {
        if (windowStart == Long.MIN_VALUE) windowStart = tick;
        else if (tick < windowStart || tick - windowStart >= WINDOW_TICKS) {
            flush(); windowStart = tick; reports = 0;
        }
    }

    void record(K key, long tick, long stamp, int step) {
        advance(tick);
        var entry = entries.get(key);
        boolean fresh = entry == null;
        if (fresh) {
            entry = new Entry<>(key, tick); entries.put(key, entry);
            if (entries.size() > LIMIT) {
                entries.remove(entries.keySet().iterator().next()); ++evicted;
            }
        }
        // A gap in this network's solve stamps means it stopped reporting this
        // failure, so a later occurrence deserves a fresh incident notice.
        boolean returned = stamp >= 0 && entry.lastStamp >= 0 && stamp - entry.lastStamp > 1;
        entry.lastStamp = stamp; entry.step = step; entry.lastTick = tick;
        ++entry.count; ++occurrences;
        if (reports < REPORTS_PER_WINDOW && (fresh || returned || entry.lastReport == Long.MIN_VALUE
                || tick - entry.lastReport >= WINDOW_TICKS)) {
            warning.accept(text(entry)); entry.lastReport = tick; ++reports;
        } else {
            ++repeated;
            if (fresh) ++deferredNew;
        }
    }

    void flush() {
        if (repeated != 0) {
            warning.accept("Electrical diagnostic summary: additionalOccurrences=" + repeated
                    + ", newlyRecorded=" + deferredNew + ", retained=" + entries.size()
                    + ", historyEvictions=" + evicted + "; inspect /wwpg errors for circuit details");
            repeated = deferredNew = 0;
        }
    }

    List<String> snapshot() {
        var result = new ArrayList<String>();
        entries.values().stream().sorted(Comparator.comparingLong((Entry<K> e) -> e.lastTick).reversed())
                .forEach(e -> result.add("firstTick=" + e.firstTick + ", lastTick=" + e.lastTick
                        + ", count=" + e.count + ": " + (e.firstDescription == null ? text(e) : e.firstDescription)));
        if (evicted != 0) result.add("Diagnostic history limited to " + LIMIT + " entries; historyEvictions="
                + evicted + ", totalOccurrences=" + occurrences);
        return List.copyOf(result);
    }

    private String text(Entry<K> entry) {
        String value = describe.apply(entry.key, entry.step);
        value = value.length() <= 2048 ? value : value.substring(0, 2048);
        // Retain the first reported evidence after repair, unload or a split
        // retires the original network. Periodic warnings can show fresh details.
        if (entry.firstDescription == null) entry.firstDescription = value;
        return value;
    }

    private static final class Entry<K> {
        final K key;
        final long firstTick;
        long lastTick, count, lastStamp = -1, lastReport = Long.MIN_VALUE;
        String firstDescription;
        int step;
        Entry(K key, long tick) { this.key = key; firstTick = lastTick = tick; }
    }
}
