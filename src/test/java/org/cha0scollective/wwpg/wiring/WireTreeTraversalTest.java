/* PG's extracted traceTree method retains its upstream Apache-2.0 copyright.
 * Copyright 2025 patryk3211. License: https://www.apache.org/licenses/LICENSE-2.0
 * This is an isolated control-flow model, not a Minecraft integration test. */
package org.cha0scollective.wwpg.wiring;
import java.util.*;
import org.junit.jupiter.api.Test;

public final class WireTreeTraversalTest {
    interface IWireEndpoint { boolean isValid(Object world); }
    static final class Endpoint implements IWireEndpoint {
        final int id;boolean valid;
        Endpoint(int id,boolean valid){this.id=id;this.valid=valid;}
        public boolean isValid(Object world){return valid;}
        public boolean equals(Object other){return other instanceof Endpoint e&&id==e.id;}
        public int hashCode(){return id;}
    }
    static final class TransmissionLinePart {
        final int id;final IWireEndpoint a,b;final boolean resolvable;boolean resolved;
        TransmissionLinePart(int id,IWireEndpoint a,IWireEndpoint b,boolean resolved,boolean resolvable){this.id=id;this.a=a;this.b=b;this.resolved=resolved;this.resolvable=resolvable;}
        Object getLine(){return resolved?this:null;}
        IWireEndpoint getEndpoint1(){return a;}
        IWireEndpoint getEndpoint2(){return b;}
    }
    static final class ModdedConfigs {static boolean logsEnabled(){return false;}}
    static final class Logger {void debug(String s,Object x){} void warn(String s){}}
    static final class PowerGrid {static final Logger LOGGER=new Logger();}

    final Object world=new Object();
    final Map<IWireEndpoint,IWireEndpoint> globalExternalNodes=new HashMap<>();
    final Map<IWireEndpoint,Collection<TransmissionLinePart>> partNodeMap=new HashMap<>();
    final List<String> events=new ArrayList<>();
    final Map<IWireEndpoint,Runnable> migrations=new HashMap<>();
    void addAndMigrateNode(IWireEndpoint endpoint){events.add("visit:"+((Endpoint)endpoint).id);var change=migrations.remove(endpoint);if(change!=null)change.run();}
    boolean makeTransmissionLine(TransmissionLinePart part){events.add("resolve:"+part.id);if(!part.resolvable)return false;part.resolved=true;return true;}

    private boolean traceTree(IWireEndpoint endpoint, Set<IWireEndpoint> visited) {
        if(!visited.add(endpoint))
            return false;
        Collection<TransmissionLinePart> parts = partNodeMap.get(globalExternalNodes.get(endpoint));
        if(parts == null)
            return false;
        // Make sure endpoints are always up to date in line parts.
        // TODO: Verify that this doesn't brake a bunch of things
        addAndMigrateNode(endpoint);
        parts = List.copyOf(parts);
        boolean continueResolving = false;
        for(var part : parts) {
            if(part.getLine() != null) {
                // This branch connects to a valid line, back-trace and resolve all line parts.
                continueResolving = true;
            }
            if(parts.size() == 1) {
                if(ModdedConfigs.logsEnabled())
                    PowerGrid.LOGGER.debug("Found edge line at {}", endpoint);
                if(part.getEndpoint1().equals(endpoint)) {
                    // Check endpoint2
                    if(part.getEndpoint2().isValid(world)) {
                        // Resolve segment
                        makeTransmissionLine(part);
                        return true;
                    }
                } else if(part.getEndpoint2().equals(endpoint)) {
                    // Check endpoint1
                    if(part.getEndpoint1().isValid(world)) {
                        // Resolve segment
                        makeTransmissionLine(part);
                        return true;
                    }
                } else {
                    PowerGrid.LOGGER.warn("[1] Part was expected to have this endpoint");
                }
                break;
            }
            if(ModdedConfigs.logsEnabled())
                PowerGrid.LOGGER.debug("Continuing line trace through {}", endpoint);
            if(part.getEndpoint1().equals(endpoint)) {
                if(traceTree(part.getEndpoint2(), visited)) {
                    makeTransmissionLine(part);
                    continueResolving = true;
                }
            } else if(part.getEndpoint2().equals(endpoint)) {
                if(traceTree(part.getEndpoint1(), visited)) {
                    makeTransmissionLine(part);
                    continueResolving = true;
                }
            } else {
                PowerGrid.LOGGER.warn("[2] Part was expected to have this endpoint");
            }
        }
        return continueResolving;
    }


    boolean iterative(IWireEndpoint endpoint,Set<IWireEndpoint> visited){
        return WireTreeTraversal.trace(endpoint,visited,new WireTreeTraversal.Graph<IWireEndpoint,TransmissionLinePart>() {
            public List<TransmissionLinePart> enter(IWireEndpoint node){
                var parts=partNodeMap.get(globalExternalNodes.get(node));
                if(parts==null)return null;
                addAndMigrateNode(node);
                return List.copyOf(parts);
            }
            public boolean resolved(TransmissionLinePart edge){return edge.resolved;}
            public IWireEndpoint other(TransmissionLinePart edge,IWireEndpoint node){return edge.a.equals(node)?edge.b:edge.b.equals(node)?edge.a:null;}
            public boolean valid(IWireEndpoint node){return node.isValid(world);}
            public void resolve(TransmissionLinePart edge){makeTransmissionLine(edge);}
        });
    }
    record Edge(int a,int b,boolean resolved,boolean resolvable){}
    record Fixture(boolean[] valid,List<Edge> edges,boolean missingMap,boolean mismatch,boolean migration){}
    static WireTreeTraversalTest fixture(Fixture fixture){
        var model=new WireTreeTraversalTest();var nodes=new ArrayList<Endpoint>();
        for(int i=0;i<fixture.valid.length;i++){var e=new Endpoint(i,fixture.valid[i]);nodes.add(e);model.globalExternalNodes.put(e,e);model.partNodeMap.put(e,new ArrayList<>());}
        int id=0;
        for(var edge:fixture.edges){var p=new TransmissionLinePart(id++,nodes.get(edge.a),nodes.get(edge.b),edge.resolved,edge.resolvable);model.partNodeMap.get(p.a).add(p);if(!p.a.equals(p.b))model.partNodeMap.get(p.b).add(p);}
        if(fixture.missingMap)model.partNodeMap.remove(nodes.get(nodes.size()-1));
        if(fixture.mismatch)model.partNodeMap.get(nodes.get(0)).add(new TransmissionLinePart(id++,nodes.get(1),nodes.get(2),false,true));
        if(fixture.migration){int extra=id;model.migrations.put(nodes.get(0),()->{var p=new TransmissionLinePart(extra,nodes.get(0),nodes.get(nodes.size()-1),false,true);model.partNodeMap.get(nodes.get(0)).add(p);var collection=model.partNodeMap.get(nodes.get(nodes.size()-1));if(collection!=null)collection.add(p);});}
        return model;
    }
    static void equivalent(Fixture f){equivalent(f,0);}
    static void equivalent(Fixture f,int start){
        var recursive=fixture(f);var iterative=fixture(f);var visitedR=new HashSet<IWireEndpoint>();var visitedI=new HashSet<IWireEndpoint>();
        boolean r=recursive.traceTree(new Endpoint(start,true),visitedR),i=iterative.iterative(new Endpoint(start,true),visitedI);
        if(r!=i||!recursive.events.equals(iterative.events)||!visitedR.equals(visitedI))throw new AssertionError("DFS semantics changed");
    }
    static Fixture chain(int count){
        boolean[] valid=new boolean[count];Arrays.fill(valid,true);var edges=new ArrayList<Edge>();
        for(int i=1;i<count;i++)edges.add(new Edge(i-1,i,false,true));
        return new Fixture(valid,edges,false,false,false);
    }
    @Test void preservesUpstreamResolutionAndHandlesTenThousandEndpoints(){
        var random=new Random(10_120_026);int checked=0;
        for(int test=0;test<600;test++){
            int count=3+random.nextInt(70);boolean[] valid=new boolean[count];
            for(int i=0;i<count;i++)valid[i]=random.nextInt(5)!=0;
            var edges=new ArrayList<Edge>();
            for(int i=0;i<count*3;i++)edges.add(new Edge(random.nextInt(count),random.nextInt(count),random.nextInt(5)==0,random.nextInt(7)!=0));
            equivalent(new Fixture(valid,edges,test%5==0,test%7==0,test%3==0));checked++;
        }
        for(int count:new int[]{3,10,100,500}){equivalent(chain(count),1);checked++;}
        var deep=chain(10_000);
        var fixed=fixture(deep);var visited=new HashSet<IWireEndpoint>();
        if(!fixed.iterative(new Endpoint(1,true),visited)||visited.size()!=10_000)throw new AssertionError("Iterative traversal failed");

    }
}
