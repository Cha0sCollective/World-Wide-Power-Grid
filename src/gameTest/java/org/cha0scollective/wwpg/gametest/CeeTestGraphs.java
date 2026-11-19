package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import java.lang.reflect.Method;

/** Setup-only node insertion; CEE changed addNode's binary return descriptor. */
final class CeeTestGraphs {
    private static final Method ADD=addMethod();
    private static Method addMethod(){try{return CircuitBuilder.class.getMethod("addNode",Node.class);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    static void addNodes(CircuitBuilder builder,Node... nodes){try{for(var node:nodes)ADD.invoke(builder,node);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
}
