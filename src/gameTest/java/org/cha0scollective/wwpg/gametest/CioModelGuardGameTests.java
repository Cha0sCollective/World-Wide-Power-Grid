package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.compat.cio.CioCompatibility;
import org.cha0scollective.wwpg.compat.cio.CioPhaseSourceAccess;
import org.cha0scollective.wwpg.extension.CeeAdapters;

/** Explicit negative test using a CIO fixture with a changed model class identity. */
@GameTestHolder("wwpg_cio_guard")
@PrefixGameTestTemplate(false)
public final class CioModelGuardGameTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void changedPrivateModelLoadsWithoutUnsafeAdapter(GameTestHelper h) {
        try {
            var type=Class.forName(CioCompatibility.PHASE_TYPE);
            h.assertTrue(!CioPhaseSourceAccess.class.isAssignableFrom(type),"Unverified model received private-history hooks");
            h.assertTrue(!CeeAdapters.registered(type.asSubclass(ElectricalProperties.class)),"Unverified model was accepted");
            h.succeed();
        } catch(ClassNotFoundException failure){throw new IllegalStateException("Changed CIO fixture is required",failure);}
    }
}
