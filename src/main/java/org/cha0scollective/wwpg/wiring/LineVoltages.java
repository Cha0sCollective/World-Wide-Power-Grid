package org.cha0scollective.wwpg.wiring;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Evaluate PG's resistive interpolation for all mid-line nodes in one pass. */
public final class LineVoltages {
    private LineVoltages() {}
    public record Segment<N>(N end, double resistance) {}

    public static <N> Map<N, Double> sample(List<Segment<N>> segments, N first, N last,
                                          double firstVoltage, double lastVoltage, double totalResistance) {
        var values = new IdentityHashMap<N, Double>();
        double resistance = 0;
        for (var segment : segments) {
            resistance += segment.resistance();
            double fraction = resistance / totalResistance;
            values.putIfAbsent(segment.end(), firstVoltage * (1 - fraction) + lastVoltage * fraction);
        }
        values.put(first, firstVoltage); values.put(last, lastVoltage);
        return values;
    }
}
