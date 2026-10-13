package org.cha0scollective.wwpg.wiring;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LineVoltagesTest {
    @Test void matchesPgPrefixEquationAndPreservesEndpointAndIdentityRules() {
        var nodes = new Object[]{new Object(),new Object(),new Object(),new Object()};
        var segments = List.of(new LineVoltages.Segment<>(nodes[1],2),new LineVoltages.Segment<>(nodes[2],3),new LineVoltages.Segment<>(nodes[3],5));
        var values = LineVoltages.sample(segments,nodes[0],nodes[3],20,-10,10);
        assertEquals(20,values.get(nodes[0])); assertEquals(14,values.get(nodes[1]));
        assertEquals(5,values.get(nodes[2])); assertEquals(-10,values.get(nodes[3]));
        assertNull(values.get(new Object()));
    }

    @Test void tenThousandSegmentsMatchIndividualNativeLookupsAfterVoltageAndResistanceChanges() {
        var first = new Object();var segments = new ArrayList<LineVoltages.Segment<Object>>();
        double total=0;
        for(int i=0;i<10_000;i++){double r=1+(i%7);total+=r;segments.add(new LineVoltages.Segment<>(new Object(),r));}
        var last=segments.getLast().end();
        for(double start:new double[]{12,-24}) {
            var values=LineVoltages.sample(segments,first,last,start,3,total);double prefix=0;
            for(var segment:segments){prefix+=segment.resistance();double a=prefix/total;
                double expected=segment.end()==last?3:start*(1-a)+3*a;
                assertEquals(expected,values.get(segment.end()));
            }
        }
        segments.set(0,new LineVoltages.Segment<>(segments.getFirst().end(),8));total+=7;
        assertEquals(12*(1-8/total)+3*(8/total),LineVoltages.sample(segments,first,last,12,3,total).get(segments.getFirst().end()));
    }
}
