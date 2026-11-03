package org.cha0scollective.wwpg.compat.cio;

import org.cha0scollective.wwpg.api.cee.CeeStateCheckpoint;
import org.cha0scollective.wwpg.api.cee.CeeSubstepResult;

/** Access only to the verified CIO model's private numerical history. */
public interface CioPhaseSourceAccess {
    CeeStateCheckpoint wwpg$checkpoint();
    void wwpg$accepted(CeeSubstepResult result);
}
