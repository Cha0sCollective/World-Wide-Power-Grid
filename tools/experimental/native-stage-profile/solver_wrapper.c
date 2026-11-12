jobject solver_single_tick(solver_t *solver, int maxIters, jobject mnaObj, int cmdCount) {
    if (solver->m_A.m_profile != NULL) {
        /* Observe reentry as invalid evidence while preserving the original call. */
        solver->m_A.m_profile[3]++;
        return solver_single_tick_body(solver, maxIters, mnaObj, cmdCount);
    }
    memset(solver->m_profile, 0, sizeof(solver->m_profile));
    solver->m_profile[0] = WWPG_PROFILE_MAGIC;
    solver->m_profile[1] = wwpg_profile_frequency();
    solver->m_profile[2] = WWPG_STAGE_COUNT;
    solver->m_A.m_profile = solver->m_profile;
    uint64_t start = wwpg_profile_clock();
    jobject result = solver_single_tick_body(solver, maxIters, mnaObj, cmdCount);
    wwpg_profile_record(solver->m_profile, WWPG_TOTAL, start);
    solver->m_A.m_profile = NULL;
    return result;
}
