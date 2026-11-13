package org.cha0scollective.wwpg.compat.cee;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.cha0scollective.wwpg.compat.cee.CeeSavedCurrent.Convention.*;
class CeeSavedCurrentTest {
    static CompoundTag current(double value){var tag=new CompoundTag();tag.putDouble("LastCurrent",value);return tag;}
    @Test void unmarkedLegacyHistoryKeepsEnergyAndDirectionOnModernRuntime(){var read=CeeSavedCurrent.read(current(2),LEGACY,MODERN,"");assertEquals(-2,read.current());assertFalse(read.ambiguous());assertEquals(6,.5*3*read.current()*read.current());}
    @Test void markedHistoryConvertsOnceAndCanReturnToLegacy(){var tag=current(2);CeeSavedCurrent.mark(tag,LEGACY,false);var modern=CeeSavedCurrent.read(tag,UNKNOWN,MODERN,"");tag.putDouble("LastCurrent",modern.current());CeeSavedCurrent.mark(tag,MODERN,false);assertEquals(-2,CeeSavedCurrent.read(tag,LEGACY,MODERN,"").current());assertEquals(2,CeeSavedCurrent.read(tag,LEGACY,LEGACY,"").current());}
    @Test void stockModernHistoryIsNotReversed(){assertEquals(2,CeeSavedCurrent.read(current(2),MODERN,MODERN,"").current());}
    @Test void ambiguousChargedHistoryIsRetainedAndExplicitlyHeld(){var tag=current(2);var read=CeeSavedCurrent.read(tag,UNKNOWN,MODERN,"");assertEquals(2,read.current());assertTrue(read.ambiguous());CeeSavedCurrent.mark(tag,MODERN,true);assertTrue(CeeSavedCurrent.read(tag,MODERN,MODERN,"").ambiguous());}
    @Test void emptyHistoryNeedsNoChoice(){assertFalse(CeeSavedCurrent.read(current(0),UNKNOWN,MODERN,"").ambiguous());}
    @Test void explicitOverrideOnlyResolvesUnknownOrigins(){assertEquals(-2,CeeSavedCurrent.read(current(2),UNKNOWN,MODERN,"1.1").current());var tag=current(2);CeeSavedCurrent.mark(tag,MODERN,false);assertEquals(2,CeeSavedCurrent.read(tag,UNKNOWN,MODERN,"1.1").current());}
    @Test void rootOriginSurvivesReplacementOfTheLoadedModList(){var root=new CompoundTag();var fml=new CompoundTag();var mods=new ListTag();var cee=new CompoundTag();cee.putString("ModId","electroenergetics");cee.putString("ModVersion","1.21.1-1.1.4");mods.add(cee);fml.put("LoadingModList",mods);root.put("fml",fml);assertEquals(LEGACY,CeeSaveConventions.rootSource(root));CeeSaveConventions.markRoot(root,LEGACY);cee.putString("ModVersion","1.21.1-1.2.0-132");assertEquals(LEGACY,CeeSaveConventions.rootSource(root));assertEquals("1.21.1-1.2.0-132",root.getCompound("fml").getList("LoadingModList",Tag.TAG_COMPOUND).getCompound(0).getString("ModVersion"));}
    @Test void missingAndUnknownVersionsAreNotGuessed(){assertEquals(UNKNOWN,CeeSaveConventions.rootSource(new CompoundTag()));assertEquals(UNKNOWN,version("1.21.1-1.3.0"));}
}
