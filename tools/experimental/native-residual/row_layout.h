#pragma once
#include <slu_ddefs.h>

typedef struct {
    int n, nnz, nonfinite;
    int *offsets, *columns, *entries;
    double *packed, *scaled;
    unsigned char *active;
    struct wwpg_tiles *tiles;
} wwpg_row_plan;

int wwpg_row_build(wwpg_row_plan *, SuperMatrix *);
void wwpg_row_refresh(wwpg_row_plan *, SuperMatrix *);
void wwpg_row_free(wwpg_row_plan *);
int wwpg_row_indexed(wwpg_row_plan *, double, SuperMatrix *, double *, double, double *);
int wwpg_row_packed(wwpg_row_plan *, double, SuperMatrix *, double *, double, double *);
int wwpg_tiles_build(wwpg_row_plan *, SuperMatrix *);
void wwpg_tiles_refresh(wwpg_row_plan *, SuperMatrix *);
void wwpg_tiles_free(wwpg_row_plan *);
int wwpg_row_tiled(wwpg_row_plan *, double, SuperMatrix *, double *, double, double *);
