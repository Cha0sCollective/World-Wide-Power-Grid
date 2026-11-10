#pragma once
#include "solver.h"

void wwpg_residual_release(solver_t *);
int wwpg_residual_apply(solver_t *,double *,double *);
