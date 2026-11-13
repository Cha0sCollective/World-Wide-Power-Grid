package org.cha0scollective.wwpg.gametest;
import com.george_vi.electroenergetics.simulation.*;
import net.minecraft.core.BlockPos;
/** Strict query probe, packaged only in the separate test companion. */
public interface CeeTestResultProbe {
    double getCurrentThrough(BlockPos pos,int a,int b);
    double getVoltageAt(BlockPos pos,int a,int b);
    double getVoltageAt(int a,int b);
    static SimulationResults create(CeeTestResultProbe probe) {
        if(!org.cha0scollective.wwpg.compat.cee.CeeCompatibility.modern())return new Legacy(probe);
        try{return (SimulationResults)Class.forName("org.cha0scollective.wwpg.gametest.cee12.Cee12ResultProbe")
                .getConstructor(CeeTestResultProbe.class).newInstance(probe);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    final class Legacy extends SimulationResults {
        private final CeeTestResultProbe probe;
        Legacy(CeeTestResultProbe probe){super(new double[0],1,new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>(),new CircuitBuilder(java.util.Set.of()),null);this.probe=probe;}
        public double getCurrentThrough(BlockPos pos,int a,int b){return probe.getCurrentThrough(pos,a,b);}
        public double getVoltageAt(BlockPos pos,int a,int b){return probe.getVoltageAt(pos,a,b);}
        public double getVoltageAt(int a,int b){return probe.getVoltageAt(a,b);}
    }
}
