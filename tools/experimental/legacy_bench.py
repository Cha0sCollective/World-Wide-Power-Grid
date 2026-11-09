"""Local-only benchmark preparation, execution, analysis and vanilla-save exports.

No GitHub calls, no production-server stop commands, no overwrite or silent retries.
"""
from __future__ import annotations
import argparse,csv,hashlib,json,math,os,shutil,subprocess,sys,time,tomllib,zipfile
from pathlib import Path
from process_metrics import ChildMetrics,host

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from world_save_evidence import completed_world_save
RUNTIME_SHA='7c852948a308f22946637df1dcecee7ade680ff7f4e0906d95a1425df14212dd'
TIERS=(100,500,1000,2500,5000,10000)
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf-8'))
def write(path,value):Path(path).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def safe_directory(path):
    path=Path(path).resolve()
    if not path.is_relative_to(ROOT/'local/benchmarks'):raise ValueError('Development runs must stay under local/benchmarks')
    return path
def short_run_directory(label):
    # Keep enough headroom for full-pack datapack names under Windows MAX_PATH.
    prefix=''.join(c for c in label[:16] if c.isalnum() or c in '-_') or 'attempt'
    suffix=hashlib.sha256(label.encode('utf-8')).hexdigest()[:12]
    return safe_directory(ROOT/'local/benchmarks/runs'/(prefix+'-'+suffix))
def restore_reference(source,destination):
    """Capture and verify the bytes copied before any server can alter the save."""
    source=Path(source).resolve();destination=Path(destination)
    if destination.exists():raise ValueError('Never replace an existing world')
    files=[dict(path=p.relative_to(source).as_posix(),bytes=p.stat().st_size,sha256=sha(p))
           for p in sorted(source.rglob('*')) if p.is_file() and p.name not in ('session.lock','level.dat_old')]
    shutil.copytree(source,destination,ignore=shutil.ignore_patterns('session.lock','level.dat_old'))
    for item in files:
        if sha(destination/item['path'])!=item['sha256']:
            raise ValueError('Reference changed while copying: '+item['path'])
    return dict(schema=1,source=str(source),destination=str(destination),files=files,
                total_bytes=sum(item['bytes'] for item in files),verified_before_launch=True)
def prepare(args):
    destination=safe_directory(args.directory)
    if destination.exists():raise ValueError('Retain the previous attempt; choose a fresh directory')
    wrapper=ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')
    command=[str(wrapper),'prepareBenchmarkServer','-I','tools/benchmark/benchmark.gradle','-Pneo_version=21.1.248',f'-PbenchmarkDirectory={destination}','--offline','--no-daemon']
    if os.name=='nt':
        # Use the existing, tested batch quoting implementation.
        sys.path.insert(0,str(ROOT/'tools'))
        from run_checked import launch_command
        command=launch_command(command,ROOT)
    subprocess.run(command,cwd=ROOT,check=True)
    write(destination/'WWPG_BENCHMARK_OWNED.json',dict(schema=1,purpose='Private benchmark instance; never production',root=str(destination)))
    (destination/'eula.txt').write_text('eula=true\n',encoding='utf-8')
    (destination/'server.properties').write_text('level-name=world\nlevel-type=minecraft:flat\ngenerate-structures=false\nlevel-seed=2101248\ngamemode=creative\ndifficulty=peaceful\nserver-ip=127.0.0.1\nserver-port=25889\nonline-mode=true\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=1\n',encoding='utf-8')
    print(json.dumps(dict(prepared=str(destination),runtime_sha256=read(destination/'launch.json')['runtime']['sha256'])))
def percent(values,p):
    if not values:return None
    ordered=sorted(values);return ordered[max(0,math.ceil(len(ordered)*p)-1)]
def summarize(directory):
    directory=Path(directory);result=read(directory/'benchmark/result.json')
    with (directory/'benchmark/ticks.csv').open(newline='',encoding='utf-8') as f:rows=[r for r in csv.DictReader(f) if r['phase']=='STABLE']
    times=[int(r['tick_ns'])/1e6 for r in rows]
    elapsed=(int(rows[-1]['wall_ns'])-int(rows[0]['wall_ns']))/1e9 if len(rows)>1 else 0
    tps=(len(rows)-1)/elapsed if elapsed else None
    aliases={'synchronize_ns':'sync_ns','result_bridge_ns':'result_ns','gameplay_commit_ns':'commit_ns'}
    # Timers record one in N ticks. Zero values on unprofiled ticks are not
    # measurements: use only marked ticks for phase costs, all ticks for TPS.
    timing=[r for r in rows if r.get('timing_sample','true') in ('true','1')]
    avg=lambda key:sum(int(r.get(key,r.get(aliases.get(key,''),0))) for r in timing)/len(timing)/1e6 if timing else None
    phases={key:avg(key) for key in ('cee_prepare_ns','synchronize_ns','result_bridge_ns','gameplay_commit_ns','pg_prepare_ns','pg_solve_ns','history_prepare_ns','history_commit_ns','cee_device_prepare_ns','cee_device_commit_ns','electrical_schedule_ns')}
    for key in rows[0] if rows else ():
        if key.endswith('_ns') and key not in ('wall_ns','tick_ns'):phases[key]=avg(key)
    # prepare includes synchronize; expose exclusive values rather than sum it twice.
    if timing:phases['cee_prepare_excluding_sync_ns']=phases['cee_prepare_ns']-phases['synchronize_ns']
    electrical=(phases['cee_prepare_ns']+phases['electrical_schedule_ns']+phases['gameplay_commit_ns']) if timing else 0
    native=sum(phases[k] for k in ('pg_prepare_ns','pg_solve_ns','cee_device_prepare_ns','cee_device_commit_ns')) if timing else 0
    bookkeeping=max(0,electrical-native)/electrical if electrical else None
    report=dict(schema=1,completed=result['completed'],passed=result['passed'],protocol_complete=result.get('protocol_complete',False),capacity_certified=False,
        mode=result['mode'],tier=result['tier'],backend=result['backend'],variant=result['variant'],audit=result.get('audit'),
        samples=len(rows),profiled_ticks=len(timing),measured_seconds=elapsed,tps=tps,mspt=dict(mean=sum(times)/len(times) if times else None,p50=percent(times,.5),p95=percent(times,.95),p99=percent(times,.99),maximum=max(times) if times else None),
        phase_mean_ms=phases,compatibility_share_upper_bound=bookkeeping,heap_peak_bytes=max((int(r['heap_bytes']) for r in rows),default=None),
        gc_ms=(int(rows[-1]['gc_ms'])-int(rows[0]['gc_ms'])) if rows else None,
        native_tick_target_met=(bool(rows and result['passed'] and tps>=19.9 and percent(times,.95)<=50) if result['backend']=='NATIVE' else None),reason=result['reason'])
    report['practical_20tps_target_met']=bool(rows and result['passed'] and tps>=19.9 and percent(times,.95)<=50)
    invocation_path=directory/'invocation.json'
    detailed=read(invocation_path).get('configuration',{}).get('detailed') if invocation_path.exists() else None
    report['detailed_timers_enabled']=None if detailed is None else str(detailed).lower()=='true'
    if report['detailed_timers_enabled'] is False:
        report['phase_mean_ms']={key:None for key in phases}
        report['compatibility_share_upper_bound']=None
    crashes=sorted((directory/'crash-reports').glob('*.txt'))
    if crashes and not result['passed']:
        excerpt=crashes[-1].read_text(encoding='utf-8',errors='replace')[:10000]
        if 'java.lang.StackOverflowError' in excerpt and 'WorldNetworks.traceTree' in excerpt:
            report['failure_origin']=dict(exception='java.lang.StackOverflowError',location='PG WorldNetworks.traceTree',
                phase=result.get('phase'),crash_report=str(crashes[-1]))
            report['reason']='Stack overflow during recursive PG wire tracing; no completed capacity result'
        elif 'ServerHangWatchdog' in excerpt:
            report['failure_origin']=dict(exception='ServerHangWatchdog',location=('EJML sparse LU factorization' if 'LuUpLooking_DSCC' in excerpt else 'See retained crash report'),crash_report=str(crashes[-1]))
            report['reason']='Normal server watchdog stopped a tick; no completed capacity result'
    process_file=directory/'process-metrics.jsonl'
    if process_file.exists():
        readings=[json.loads(line) for line in process_file.read_text(encoding='utf-8').splitlines()]
        readings=[r for r in readings if r.get('phase')=='STABLE']
        duration=readings[-1]['wall_unix']-readings[0]['wall_unix'] if len(readings)>1 else 0
        report['process']=dict(rss_peak_bytes=max((r['rss_bytes'] for r in readings),default=None),
            cpu_mean_cores=((readings[-1]['cpu_seconds']-readings[0]['cpu_seconds'])/duration if duration else None))
    write(directory/'benchmark/summary.json',report);return report
def run(args):
    from cpu_profile import CpuRecording, validate_options
    validate_options(args)
    directory=safe_directory(args.directory);launch=read(directory/'launch.json')
    if not (directory/'WWPG_BENCHMARK_OWNED.json').is_file():raise ValueError('Not a runner-owned benchmark instance')
    if (directory/'profile-input.json').exists() and not read(directory/'profile-input.json').get('construction_complete'):
        raise ValueError('Profile construction is incomplete; prepare a fresh complete profile')
    if (directory/'benchmark/result.json').exists():raise ValueError('Existing result; no automatic retry')
    if sha(launch['runtime']['path'])!=launch['runtime']['sha256']:raise ValueError('Runtime changed after preparation')
    expected_runtime=getattr(args,'runtime_sha256',None)
    if expected_runtime:
        if launch['runtime'].get('kind')!='local-candidate' or launch['runtime']['sha256']!=expected_runtime:raise ValueError('Explicit candidate checksum differs')
    elif launch['runtime']['sha256']!=RUNTIME_SHA:raise ValueError('Non-published runtime requires an explicit candidate checksum')
    if sha(launch['helper']['path'])!=launch['helper']['sha256']:raise ValueError('Helper changed after launch preparation')
    for item in launch.get('profile_inputs',[]):
        if sha(item['path'])!=item['sha256']:raise ValueError('Profile input changed after preparation: '+item['path'])
    for source in launch['sources']:
        if sha(ROOT/source['path'])!=source['sha256']:raise ValueError('Helper source changed; prepare a fresh launch')
    argsfile=directory/'benchmark-launch.args'
    if argsfile.exists():raise ValueError('Existing invocation; preserve it')
    for name in ('warmup','sample','churn','timeout'):
        value=getattr(args,name)
        if not math.isfinite(value) or value<0 or name in ('sample','timeout') and value==0:raise ValueError('Invalid finite duration: '+name)
    if not 1<=args.cycles<=5:raise ValueError('Reload cycles must be between one and five')
    if args.mode=='soak' and (args.backend!='NATIVE' or args.sample<3600):raise ValueError('The agreed soak requires native and at least sixty measured minutes')
    construction_watchdog=getattr(args,'construction_watchdog_seconds',None)
    loading_trace=getattr(args,'loading_trace',False)
    loading_limit=getattr(args,'loading_limit',None)
    diagnostic_watchdog=getattr(args,'diagnostic_watchdog_seconds',None)
    if loading_trace:
        if args.mode!='verify' or not args.restore:raise ValueError('Loading diagnostics require a restored, untimed verification save')
        if loading_limit is None or not 100<=loading_limit<=3600:raise ValueError('Diagnostic loading limit must be between 100 and 3600 ticks')
    elif loading_limit is not None or diagnostic_watchdog is not None:
        raise ValueError('Diagnostic limits require explicit loading diagnostics')
    if diagnostic_watchdog is not None:
        if not math.isfinite(diagnostic_watchdog) or not 60<=diagnostic_watchdog<=600:raise ValueError('Diagnostic watchdog must be between 60 and 600 seconds')
        properties=directory/'server.properties'
        lines=[line for line in properties.read_text().splitlines() if not line.startswith('max-tick-time=')]
        lines.append('max-tick-time='+str(round(diagnostic_watchdog*1000)))
        properties.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    if construction_watchdog is not None:
        if args.mode!='generate' or not math.isfinite(construction_watchdog) or construction_watchdog<60:raise ValueError('Extended watchdog applies only to finite construction, never timed trials')
        properties=directory/'server.properties'
        lines=[line for line in properties.read_text().splitlines() if not line.startswith('max-tick-time=')]
        lines.append('max-tick-time='+str(round(construction_watchdog*1000)))
        properties.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    props=dict(autorun='true',owned='true',exit='true',tier=args.tier,backend=args.backend,variant=args.variant,mode=args.mode,warmup=args.warmup,sample=args.sample,churn=args.churn,cycles=args.cycles,restore=str(bool(args.restore)).lower(),detailed=str(args.detailed).lower())
    if construction_watchdog is not None:props['construction_watchdog_seconds']=construction_watchdog
    if loading_trace:
        props.update(loading_trace='true',loading_limit=loading_limit,diagnostic_only='true')
        if diagnostic_watchdog is not None:props['diagnostic_watchdog_seconds']=diagnostic_watchdog
    if args.restore:
        source=Path(args.restore).resolve()
        if (directory/'world').exists():raise ValueError('Never replace an existing world')
        if not (source/'level.dat').is_file() or not (source/'WWPG_BENCHMARK.json').is_file():raise ValueError('Restore requires an extracted benchmark save')
        write(directory/'restore-input.json',restore_reference(source,directory/'world'))
        props['restore']='true'
        saved=read(source/'WWPG_BENCHMARK.json')
        props.update(loading_protocol=2,loading_chunk_limit=max(600,3*len(saved.get('chunks',[]))),loading_graph_limit=300,loading_acceptance_limit=300,loading_healthy_ticks=100,loading_stall_seconds=30)
    recording=('"-XX:StartFlightRecording=filename='+str((directory/'loading.jfr').as_posix())+',settings=profile,dumponexit=true"\n') if loading_trace else ''
    props['restoration_batch']=str(getattr(args,'restoration_batch',False)).lower()
    profile_every=getattr(args,'profile_every',1)
    if profile_every<1:raise ValueError('Profile sampling period must be positive')
    props['profileEvery']=profile_every
    props['rhsCounters']=str(getattr(args,'rhs_counters',True)).lower()
    props['substeps']=str(getattr(args,'substep_trace',False)).lower()
    props['substepJfr']=str(getattr(args,'substep_jfr',False)).lower()
    props['cpuJfr']=str(getattr(args,'cpu_jfr',False)).lower()
    props['cpuJfrNonSafepoints']=str(getattr(args,'cpu_jfr_non_safepoints',False)).lower()
    props['preparationSections']=str(getattr(args,'preparation_sections',False)).lower()
    props['nativeWork']=str(getattr(args,'native_work',False)).lower()
    if getattr(args,'native_work',False) and (args.mode!='control' or not args.detailed or not getattr(args,'substep_trace',False) or getattr(args,'cpu_jfr',False) or getattr(args,'substep_jfr',False) or getattr(args,'preparation_sections',False)):
        raise ValueError('Native work observation requires a detailed control without other diagnostics')
    if getattr(args,'preparation_sections',False) and (args.mode!='control' or args.detailed or getattr(args,'cpu_jfr',False) or getattr(args,'substep_jfr',False)):
        raise ValueError('Preparation sections require a control run with other diagnostics disabled')
    if getattr(args,'substep_trace',False) and not args.detailed:raise ValueError('Substep diagnosis requires sampled detailed timers')
    if getattr(args,'substep_jfr',False) and not getattr(args,'substep_trace',False):raise ValueError('Stable JFR requires explicit substep diagnosis')
    if getattr(args,'substep_trace',False) and args.mode!='control':raise ValueError('Substep diagnosis is restricted to the finite stable-only control protocol')
    argsfile.write_text(recording+'-Dwwpg.restorationBatch='+props['restoration_batch']+'\n'+''.join(f'-Dwwpg.benchmark.{k}={v}\n' for k,v in props.items())+(directory/'launch.args').read_text(encoding='utf-8'),encoding='utf-8',newline='\n')
    command=[launch['command'][0],'@'+str(argsfile)]
    env=os.environ.copy();env.update(launch['environment'])
    if launch.get('cpu_affinity_mask'):
        from cpu_affinity import observe_group
        preflight=observe_group(int(launch['cpu_affinity_mask'],16))
        write(directory/'cpu-preflight.json',preflight)
        if not preflight['quiet']:
            raise OSError('Selected CPU group is busy; preserve this attempt and select a quiet group before timing')
    write(directory/'invocation.json',dict(schema=1,command=command,configuration=props,loader=launch['neoforge'],runtime=launch['runtime'],helper=launch['helper'],profile=launch.get('profile',{'name':'CLEAN_DEVELOPMENT'}),host=host(),launch_sha256=sha(directory/'launch.json'),started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),started_unix_ms=round(time.time()*1000)))
    started=time.monotonic()
    with (directory/'process.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,cwd=directory,env=env,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,start_new_session=os.name!='nt')
        metrics=None;metrics_file=None
        try:
            process_mask = launch.get('cpu_affinity_mask')
            if process_mask:
                from cpu_affinity import pin_owned, affinity
                write(directory/'process-affinity.json', pin_owned(process, int(process_mask,16)))
            metrics=ChildMetrics(process.pid)
            metrics_file=(directory/'process-metrics.jsonl').open('w',encoding='utf-8')
            cpu_recording=CpuRecording(directory,command[0],process) if getattr(args,'cpu_jfr',False) else None
            last=None
            while process.poll() is None:
                if time.monotonic()-started>args.timeout:raise TimeoutError('Benchmark watchdog expired')
                failed_result=directory/'benchmark/result.json'
                if failed_result.exists() and time.time()-failed_result.stat().st_mtime>90:
                    try:finished=read(failed_result)
                    except json.JSONDecodeError:finished={}
                    if finished.get('finished') and not finished.get('passed'):raise TimeoutError('Failed server left mod workers alive after ninety-second shutdown grace')
                if (directory/'CANCEL').exists() or getattr(args,'cancel_file',None) and Path(args.cancel_file).exists():raise KeyboardInterrupt('Local campaign cancellation requested')
                status=directory/'benchmark/status.json';state={}
                if status.exists():
                    try:
                        state=read(status);key=(state['phase'],state['tick']//200)
                        if key!=last:print(json.dumps(state),flush=True);last=key
                    except json.JSONDecodeError:pass
                if cpu_recording is not None:
                    cpu_recording.observe(state)
                if process.poll() is None:
                    try:reading=metrics.sample()
                    except OSError:
                        if process.poll() is not None:break
                        raise
                    reading['phase']=state.get('phase','STARTUP')
                    if process_mask:
                        actual_mask = affinity(process)[0]
                        if actual_mask != int(process_mask,16):
                            raise OSError('Owned benchmark process escaped requested CPU affinity')
                        reading['cpu_affinity_mask'] = f'{actual_mask:x}'
                    metrics_file.write(json.dumps(reading)+'\n');metrics_file.flush()
                time.sleep(1)
        except (KeyboardInterrupt,TimeoutError,OSError,ValueError) as error:
            # Never target unrelated JVMs. Capture only this launched process.
            jcmd=Path(command[0]).parent/('jcmd.exe' if os.name=='nt' else 'jcmd')
            if jcmd.exists():
                try:
                    dump=subprocess.run([str(jcmd),str(process.pid),'Thread.print','-l'],capture_output=True,text=True,timeout=15)
                    (directory/'failed-thread-dump.txt').write_text(dump.stdout+dump.stderr,encoding='utf-8')
                except subprocess.TimeoutExpired:pass
            if process.stdin:
                try:process.stdin.write(b'stop\n');process.stdin.flush();process.wait(timeout=20)
                except (OSError,subprocess.TimeoutExpired):process.kill();process.wait()
            write(directory/'runner-failure.json',dict(passed=False,error=str(error),pid=process.pid,exit_code=process.returncode))
            raise
        finally:
            if metrics:metrics.close()
            if metrics_file:metrics_file.close()
            if process.poll() is not None:
                write(directory/'runner-exit.json',dict(schema=1,pid=process.pid,exit_code=process.returncode,
                    elapsed_seconds=time.monotonic()-started,ended_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())))
    result=directory/'benchmark/result.json'
    if not result.exists():
        write(directory/'runner-failure.json',dict(passed=False,error='Server exited without a benchmark result',pid=process.pid,exit_code=process.returncode))
        raise RuntimeError(f'Server exited {process.returncode} without a benchmark result; retain process.log')
    if process.returncode==0 and read(result).get('passed'):
        shutdown=completed_world_save((directory/'process.log').read_text(encoding='utf-8',errors='replace'))
        write(directory/'shutdown-save.json',dict(schema=1,exit_code=process.returncode,**shutdown))
    report=summarize(directory);print(json.dumps(report),flush=True)
    if not report['passed'] or process.returncode!=0:raise RuntimeError('Benchmark failed; evidence retained')
def export(args):
    directory=safe_directory(args.directory);report=read(directory/'benchmark/result.json');grid=read(directory/'world/WWPG_BENCHMARK.json')
    if not report['passed']:raise ValueError('Cannot export failed construction')
    exit_record=read(directory/'runner-exit.json')
    if exit_record['exit_code']!=0:raise ValueError('Export requires a successful, finished server process')
    completed_world_save((directory/'process.log').read_text(encoding='utf-8',errors='replace'))
    output=safe_directory(args.output)
    if output.exists() or output.is_relative_to(directory/'world'):raise ValueError('Use a fresh ZIP outside the source save')
    name=f'WWPG Benchmark - {grid["variant"]} - {grid["requested_tier"]} nodes'
    files={}
    for path in sorted((directory/'world').rglob('*')):
        if path.is_file() and path.name not in ('session.lock','level.dat_old'):files[name+'/'+path.relative_to(directory/'world').as_posix()]=path.read_bytes()
    if not any(p.endswith('/level.dat') for p in files):raise ValueError('Not a normal Minecraft save')
    sys.path.insert(0,str(ROOT/'tools'))
    from export_stationary_yard import renamed_level
    files[name+'/level.dat']=renamed_level(files[name+'/level.dat'],name)
    runtime_version='0.2.1'
    if (directory/'invocation.json').exists():
        invocation=read(directory/'invocation.json')
        with zipfile.ZipFile(invocation['runtime']['path']) as jar:
            runtime_version=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())['mods'][0]['version']
        files[name+'/WWPG_BENCHMARK_RUNTIME.json']=json.dumps(dict(runtime=invocation['runtime'],profile=invocation['profile'],generated_with_wwpg=runtime_version),indent=2).encode()+b'\n'
    instructions=f'''WWPG Benchmark Yard — minimum {grid['requested_tier']} nodes\n\nThis is a normal Minecraft world save. Extract {name}/ into saves/ for single-player, or use its contents as a dedicated server world.\nUse Minecraft 1.21.1, NeoForge 21.1.248, WWPG 0.2.1 and its required mods.\nThe optional benchmark helper is needed only for timed measurements, not to open the circuits.\nSpawn is near 1038,64,1038. Native wires, boards, panels and storage are saved.\nConstruction check: {grid['audit']['electrical_nodes']} nodes, {grid['audit']['edges']} edges. This is not a certified server-capacity result.\nKeep the original ZIP. Do not reuse an edited save for comparisons.\n'''
    instructions=instructions.replace('WWPG 0.2.1 and',f'WWPG {runtime_version} and')
    files[name+'/BENCHMARK_README.txt']=instructions.encode('utf-8');files['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+p+'\n' for p,data in sorted(files.items())).encode('utf-8')
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,data in sorted(files.items()):z.writestr(path,data)
    print(json.dumps(dict(world=str(output),sha256=sha(output),save_folder=name,files=len(files))))
def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='action',required=True)
    p=sub.add_parser('prepare');p.add_argument('--directory',required=True)
    r=sub.add_parser('run');r.add_argument('--directory',required=True);r.add_argument('--tier',type=int,choices=TIERS,default=100);r.add_argument('--backend',choices=('NATIVE','JAVA'),default='NATIVE');r.add_argument('--variant',choices=('MIXED','PG','CEE','EMPTY'),default='MIXED');r.add_argument('--mode',choices=('generate','measure','verify','coldcheck','soak','control','calibrate'),default='generate');r.add_argument('--warmup',type=float,default=120);r.add_argument('--sample',type=float,default=600);r.add_argument('--churn',type=float,default=180);r.add_argument('--cycles',type=int,default=5);r.add_argument('--restore');r.add_argument('--detailed',action=argparse.BooleanOptionalAction,default=True);r.add_argument('--timeout',type=float,default=1800)
    e=sub.add_parser('export');e.add_argument('--directory',required=True);e.add_argument('--output',required=True)
    a=sub.add_parser('analyze');a.add_argument('--directory',required=True)
    r.add_argument('--runtime-sha256',help='Authorize only this exact prepared local candidate hash')
    r.add_argument('--construction-watchdog-seconds',type=float,help='Recorded setup-only watchdog allowance; disallowed for measured runs')
    r.add_argument('--loading-trace',action='store_true',help='Untimed, read-only loading records and JFR; not a capacity measurement')
    r.add_argument('--loading-limit',type=int,help='Finite diagnostic loading deadline in ticks')
    r.add_argument('--diagnostic-watchdog-seconds',type=float,help='Recorded loading-diagnostic allowance; disallowed for capacity trials')
    r.add_argument('--restoration-batch',action=argparse.BooleanOptionalAction,default=False,help='Explicit recorded local restoration prototype selection')
    r.add_argument('--rhs-counters',action=argparse.BooleanOptionalAction,default=True)
    r.add_argument('--profile-every',type=int,default=1,help='Time one tick in this many; keep sampled phase costs separate from whole-run timing')
    r.add_argument('--substep-trace',action='store_true',help='Local stable-only full-loop diagnosis of each16 substeps')
    r.add_argument('--substep-jfr',action='store_true',help='Record Java CPU/allocation samples only during the stable substep diagnosis')
    args=parser.parse_args()
    if args.action=='prepare':prepare(args)
    elif args.action=='run':run(args)
    elif args.action=='export':export(args)
    else:print(json.dumps(summarize(args.directory)))
if __name__=='__main__':main()
