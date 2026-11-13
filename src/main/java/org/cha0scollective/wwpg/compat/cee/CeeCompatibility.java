package org.cha0scollective.wwpg.compat.cee;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import java.lang.reflect.Modifier;

/** Select once from the upstream binary contract; no reflective calls in the solve loop. */
public final class CeeCompatibility {
    private CeeCompatibility() { }
    public static boolean modern() { return Modifier.isAbstract(ElectricalProperties.class.getModifiers()); }
    private static final class Holder {
        static final CeeIntegration INSTANCE=load();
        private static CeeIntegration load() {
            String name="org.cha0scollective.wwpg.compat.cee"+(modern()?"12.Cee12Integration":"11.Cee11Integration");
            try { return (CeeIntegration)Class.forName(name).getConstructor().newInstance(); }
            catch(ReflectiveOperationException error) { throw new IllegalStateException("Unsupported CEE electrical contract: "+name,error); }
        }
    }
    public static CeeIntegration integration() { return Holder.INSTANCE; }
}
