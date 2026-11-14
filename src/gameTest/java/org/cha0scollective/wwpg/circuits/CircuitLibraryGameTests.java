package org.cha0scollective.wwpg.circuits;

import java.util.*;
import net.minecraft.gametest.framework.*;
import org.cha0scollective.wwpg.gametest.DynamicGameTests;

/** Selected assemblies keep independent GameTests, inputs and traces. */
public final class CircuitLibraryGameTests {
    public static Collection<TestFunction> cases(String expected){
        String selected=expected;
        var requested=List.of(System.getProperty("wwpg.test.circuit", "").split(","));
        // NeoForge invokes all generators before filtering structure namespaces.
        if(!requested.contains(expected)) return List.of();
        String namespace="wwpg_circuit_"+selected.replace('-','_');
        return List.of(new TestFunction("circuit-library",namespace+"."+selected,namespace+":empty",12000,0,true,h->{
            h.assertTrue(org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.overheating.get(),"Overheating must remain enabled");
            AdvancedLibrary.identity();
            h.runAtTickTime(12,()->SourceLossChecks.save("actual-backend-"+selected+".json",SourceLossChecks.backend(h)));
            System.out.println("WWPG_CIRCUIT_BEGIN case="+selected+" substeps=16");
            switch(selected){
                case "adder3"->FunctionalBaselines.adder3(h);
                case "oscillator"->FunctionalBaselines.oscillator(h);
                case "source-switch"->FunctionalBaselines.sourceSwitch(h);
                case "regulator"->FunctionalBaselines.regulator(h);
                case "register8"->FunctionalBaselines.memoryBank(h,1,8);
                case "memory-bank"->FunctionalBaselines.memoryBank(h,4,4);
                case "shift4"->AdvancedLibrary.shift(h);
                case "ram4x4"->AdvancedLibrary.ram(h);
                case "fused-latch"->FusedOutputChecks.power(h);
                case "relay-heater"->RelayHeaterChecks.operate(h,"verify");
                case "quiet-heater"->{new QuietSoundObserver();QuietHeaterChecks.operate(h,"verify");}
                case "source-loss"->SourceLossChecks.operate(h);
                default->throw new IllegalArgumentException("Unknown circuit: "+selected);
            }
        }));
    }
}
