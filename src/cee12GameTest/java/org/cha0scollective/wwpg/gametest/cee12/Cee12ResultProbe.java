package org.cha0scollective.wwpg.gametest.cee12;
import com.george_vi.electroenergetics.simulation.*;
import net.minecraft.core.BlockPos;
import org.cha0scollective.wwpg.gametest.CeeTestResultProbe;
/** Same strict probe on the new upstream result-constructor descriptor. */
public final class Cee12ResultProbe extends SimulationResults {
    private final CeeTestResultProbe probe;
    public Cee12ResultProbe(CeeTestResultProbe probe){super(new double[0],1,new CircuitBuilder(java.util.Set.of()),null);this.probe=probe;}
    public double getCurrentThrough(BlockPos pos,int a,int b){return probe.getCurrentThrough(pos,a,b);}
    public double getVoltageAt(BlockPos pos,int a,int b){return probe.getVoltageAt(pos,a,b);}
    public double getVoltageAt(int a,int b){return probe.getVoltageAt(a,b);}
}
