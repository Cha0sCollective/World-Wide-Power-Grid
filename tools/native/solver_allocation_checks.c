/* Exercise JNI allocation/error ownership without exhausting the host heap. */
#include <stdio.h>
#include <string.h>
#include "allocation_faults.h"
#include "solver.h"

static int pending,globals,buffer_calls,global_calls,fail_buffer,fail_global;
static struct { void *address; jlong capacity; } buffers[256];
static int next_buffer;
static jboolean JNICALL exception_check(JNIEnv *env){return pending;}
static jclass JNICALL find_class(JNIEnv *env,const char *name){return (jclass)1;}
static jclass JNICALL object_class(JNIEnv *env,jobject object){return (jclass)1;}
static jmethodID JNICALL method_id(JNIEnv *env,jclass c,const char *name,const char *signature){return (jmethodID)1;}
static jint JNICALL throw_new(JNIEnv *env,jclass type,const char *text){pending=1;return 0;}
static jobject JNICALL direct_buffer(JNIEnv *env,void *address,jlong capacity){
    if(++buffer_calls==fail_buffer){pending=1;return NULL;}
    assert(next_buffer<256);buffers[next_buffer].address=address;buffers[next_buffer].capacity=capacity;
    return (jobject)&buffers[next_buffer++];
}
static jobject JNICALL global_ref(JNIEnv *env,jobject object){if(++global_calls==fail_global){pending=1;return NULL;}++globals;return object;}
static void JNICALL delete_global(JNIEnv *env,jobject object){assert(globals>0);--globals;}
static void JNICALL delete_local(JNIEnv *env,jobject object){}
static void *JNICALL direct_address(JNIEnv *env,jobject buffer){return buffer;}
static const struct JNINativeInterface_ functions={
    .ExceptionCheck=exception_check,.FindClass=find_class,.GetObjectClass=object_class,.GetMethodID=method_id,
    .ThrowNew=throw_new,.NewDirectByteBuffer=direct_buffer,.NewGlobalRef=global_ref,
    .DeleteGlobalRef=delete_global,.DeleteLocalRef=delete_local,.GetDirectBufferAddress=direct_address
};
static JNIEnv env=&functions;
int verificationFunc(void){return 1;}
int powergrid_run_safely(int (*f)()){return f();}
extern jlong Java_org_patryk3211_powergrid_electricity_sim_solver_NativeMNA_allocateNativeObject(JNIEnv *,jobject,jobject,jobject,jint,jobject);
extern void Java_org_patryk3211_powergrid_electricity_sim_solver_NativeMNA_deallocateNativeObject(JNIEnv *,jobject,jlong);
int main(int argc,char **argv){
    assert(argc==3);int nth=atoi(argv[2]);rhsop_t rhs[2]={{.row=-1}};jacobianop_t jac[2]={{0}};auxbuf_t aux={0};
    if(!strcmp(argv[1],"object")){
        arm(nth);jlong handle=Java_org_patryk3211_powergrid_electricity_sim_solver_NativeMNA_allocateNativeObject(&env,(jobject)1,(jobject)rhs,(jobject)jac,2,(jobject)&aux);disarm();
        assert(!handle&&pending);Java_org_patryk3211_powergrid_electricity_sim_solver_NativeMNA_deallocateNativeObject(&env,(jobject)1,0);
    }else{
        solver_t solver;solver_init(&solver,rhs,jac,2,&aux,&env,(jobject)1);assert(!solver.m_A.m_factorInfo);
        solver_resize(&solver,2);assert(!pending&&globals==2);double *state=solver.m_state;state[0]=3;state[1]=4;
        solver.m_converged=1;aux.status=1;buffer_calls=global_calls=0;
        if(!strcmp(argv[1],"refs")){if(nth<=2)fail_buffer=nth;else fail_global=nth-2;}
        else arm(nth);
        if(!strcmp(argv[1],"growth")){jac[0].change=2;solver_process_jacobian_buffer(&solver,1);}
        else solver_resize(&solver,4);
        disarm();assert(pending&&!solver.m_converged&&!aux.status&&solver.m_A.m_factorInfo);
        assert(solver.m_size==2&&solver.m_state==state&&state[0]==3&&state[1]==4&&globals==2);
        pending=fail_buffer=fail_global=0;solver_resize(&solver,4);assert(!pending&&solver.m_size==4&&globals==2);
        assert(solver.m_state[0]==0&&solver.m_state[3]==0);solver_destroy(&solver);
    }
    assert(live==0&&globals==0);puts("PASS JNI allocation failure, preserved state/references, retry and cleanup");return 0;
}
