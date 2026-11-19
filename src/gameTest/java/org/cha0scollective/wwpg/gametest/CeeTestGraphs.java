package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import java.lang.reflect.Method;

/** Setup-only access; CEE changed its indexed-node binary return descriptors. */
final class CeeTestGraphs {
    private static final Method ADD=addMethod();
    private static final Method GET=getMethod();
    private static Method getMethod(){try{return CircuitBuilder.class.getMethod("getNode",Node.class);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    static boolean hasNode(CircuitBuilder builder,Node node){try{return GET.invoke(builder,node)!=null;}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static Method addMethod(){try{return CircuitBuilder.class.getMethod("addNode",Node.class);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    static void addNodes(CircuitBuilder builder,Node... nodes){try{for(var node:nodes)ADD.invoke(builder,node);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
}
