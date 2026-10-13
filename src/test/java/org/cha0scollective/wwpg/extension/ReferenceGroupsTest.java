package org.cha0scollective.wwpg.extension;

import org.junit.jupiter.api.Test;
import java.util.IdentityHashMap;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceGroupsTest {
    @Test void matchesExistingReferencePreferenceAcrossMergesAndGroundChanges() {
        var nodes = new Object[128];var expected = new IdentityHashMap<Object,Object>();
        for(int i=0;i<nodes.length;i++){nodes[i]=new Object();expected.put(nodes[i],nodes[i]);}
        var earth=nodes[0];var groups=new ReferenceGroups();var random=new Random(102026);
        for(int i=0;i<500;i++){
            var first=nodes[random.nextInt(nodes.length)];var second=nodes[random.nextInt(nodes.length)];
            Object a=expected.get(first),b=expected.get(second),keep=b==earth?b:a,replace=keep==a?b:a;
            expected.replaceAll((node,reference)->reference==replace?keep:reference);
            groups.unite(first,second,earth);
            for(var node:nodes)assertSame(expected.get(node),groups.root(node));
        }
        // Each preparation owns a new snapshot; removing a physical ground must
        // not retain a prior tick's earth membership.
        var fresh=new ReferenceGroups();assertSame(nodes[1],fresh.root(nodes[1]));
        assertNotSame(earth,fresh.root(nodes[1]));
    }

    @Test void longMergeChainUsesHeapStorageAndKeepsUnrelatedReferencesSeparate() {
        var groups=new ReferenceGroups();var first=new Object();Object previous=first;
        for(int i=0;i<10_000;i++){var next=new Object();groups.unite(next,previous,null);previous=next;}
        assertSame(previous,groups.root(first));assertSame(previous,groups.root(first));
        var unrelated=new Object();assertSame(unrelated,groups.root(unrelated));
    }
}
