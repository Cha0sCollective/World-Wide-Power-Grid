#pragma once
/* One fault allocator per test executable; the gameplay library never uses it. */
#include <assert.h>
#include <stdlib.h>

void *__real_malloc(size_t);
void *__real_calloc(size_t,size_t);
void *__real_realloc(void *,size_t);
void __real_free(void *);
static int failing,ordinal,calls,live;
static void *owned[4096];
static int fail(void){return failing && ++calls==ordinal;}
static void track(void *p){if(!p||!failing)return;for(int i=0;i<4096;++i)if(!owned[i]){owned[i]=p;++live;return;}abort();}
void __wrap_free(void *p){for(int i=0;i<4096;++i)if(p&&owned[i]==p){owned[i]=NULL;--live;break;}__real_free(p);}
void *__wrap_malloc(size_t n){if(fail())return NULL;void *p=__real_malloc(n);track(p);return p;}
void *__wrap_calloc(size_t n,size_t s){if(fail())return NULL;void *p=__real_calloc(n,s);track(p);return p;}
void *__wrap_realloc(void *p,size_t n){if(fail())return NULL;void *q=__real_realloc(p,n);if(q){for(int i=0;i<4096;++i)if(owned[i]==p){owned[i]=NULL;--live;break;}track(q);}return q;}
static void arm(int n){calls=0;ordinal=n;failing=1;}
static void disarm(void){failing=0;}
