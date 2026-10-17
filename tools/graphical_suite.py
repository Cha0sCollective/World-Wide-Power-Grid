"""Serial local clients for separated-v2; collectors require actual observations."""
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from test_suites import ROOT, catalog, descriptor, read, sha, write


def require(condition, message):
    if not condition: raise ValueError(message)


def verify_file(item, base):
    path=(Path(base)/item['path']).resolve()
    if not path.is_file() and not Path(item['path']).is_absolute():path=(ROOT/item['path']).resolve()
    require(path.is_file() and sha(path)==item['sha256'] and path.stat().st_size==item['bytes'], 'Changed client input/evidence: '+str(path))
    return path


def electrical_diagnostics(texts):
    return {role:[line for line in text.splitlines() if 'WorldWidePowerGrid/' in line and '/WARN]' in line]
            for role,text in texts.items() if any('WorldWidePowerGrid/' in line and '/WARN]' in line for line in text.splitlines())}


def observed(job, texts):
    """Map required behavior to real driver assertions, not an unchecked passed flag."""
    joined='\n'.join(texts.values()); checks=set()
    if job['scenario']=='starter' and 'DOC_CLIENT_PASSED:' in joined:
        checks.update(['powered-readings','control','agreement','starter-controls','native-tools'])
    if 'CI_SMOKE_SERVER_COMPLETE' in joined and 'CI_SMOKE_CLIENT_COMPLETE' in joined:
        for name, phase in [('powered-readings','base'),('agreement','base'),('control','off')]:
            if f'CI_SMOKE_SERVER_PASSED: {phase}' in joined and all(f'CI_SMOKE_CLIENT_PASSED: {phase}' in text for role,text in texts.items() if role!='server'): checks.add(name)
        if 'CI_SMOKE_UNAVAILABLE_PASSED' in joined and 'CI_SMOKE_SERVER_PASSED: recovered' in joined: checks.add('unavailable-recovery')
        if 'CI_SMOKE_BUZZER_SIDE_PASSED' in joined and 'CI_SMOKE_CLIENT_PASSED: audio' in joined: checks.add('buzzer-side')
        if 'CI_ADDON_VISUALS_PASSED' in joined: checks.add('addon-visuals')
        if 'CI_INTEGRATED_TOOLS_PASSED' in joined: checks.add('integrated-tools')
    if 'EXPANDED_REAL_CLIENT_PASSED' in joined:
        for name, marker in {'powered-readings':'EXPANDED_CLIENT_READING:', 'agreement':'EXPANDED_CLIENT_READING:',
            'control':'EXPANDED_CLIENT_PANEL_PACKETS_PASSED','buzzer-side':'STATIONARY_CLIENT_BUZZER_AUDIO_PASSED',
            'representative-visuals':'STATIONARY_CLIENT_PLOTTER_GRAPH_PASSED','terminal-highlights':'CEE_TERMINAL_HIGHLIGHT_CLIENT_PASSED',
            'design':'STATIONARY_CLIENT_DESIGN_SCREEN_PASSED','pinout-keyboard':'STATIONARY_CLIENT_PINOUT_KEYBOARD_PASSED',
            'portal':'STATIONARY_CLIENT_NATIVE_PORTAL_PASSED'}.items():
            if marker in joined: checks.add(name)
        expected=catalog()['yard_views']
        require(set(expected)<=set(re.findall(r'EXPANDED_CLIENT_VIEW_PASSED: (\S+)',joined)), 'Missing selected yard visuals')
        require(all(f'STATIONARY_CLIENT_CRT_BEAM: {code}' in joined for code in ('S24','S25','S26')), 'CRT variant omitted')
    if 'STATIONARY_TWO_REAL_CLIENTS_PASSED' in joined:
        require(all('WWPG_REAL_CLIENT_PASSED' in texts[role] for role in ('client-a','client-b')), 'TCP client incomplete')
        checks.update(['powered-readings','control','agreement'])
        if all('STATIONARY_REAL_CLIENT_DESIGN:' in texts[role] for role in ('client-a','client-b')):checks.add('design-sync')
        if all('STATIONARY_REAL_CLIENT_LUA:' in texts[role] for role in ('client-a','client-b')):checks.add('pinout-keyboard')
    if 'API_WORKSHOP_TWO_CLIENTS_PASSED' in joined:
        checks.update(['powered-readings','control','agreement'])
        for name, phase in [('wire','wire'),('configure','configure_on'),('meter','meter'),('panel-edit','panel_edit'),('board-edit','board_edit'),('repair','repair_wire')]:
            if f'API_WORKSHOP_SERVER_PHASE_PASSED: {phase}' in joined and all(f'API_WORKSHOP_PHASE_PASSED: {phase}' in texts[role] for role in ('client-a','client-b')): checks.add(name)
    return checks


def validate_observation(proof, job, inputs, base):
    require(proof.get('schema')==2 and proof.get('scenario_id')==job['id'], 'Wrong client scenario')
    require(proof.get('backend')==job['backend'] and proof.get('profile')==job['profile'] and proof.get('loader')==job['loader'], 'Wrong client environment')
    require(proof.get('source_sha')==inputs['source_sha'] and all(proof['artifact_hashes'].get(r)==inputs['artifact_hashes'][r] for r in ('runtime','api','cee_api','example','upstream_example')), 'Client input identity differs')
    require(proof.get('passed') is True, 'Failed client attempt')
    require(proof['world']['sha256']==sha(verify_file(proof['world'],base)), 'World hash differs')
    launch=read(verify_file(proof['launch'],base))
    verify_file(launch['helper'],base)
    require(bool(launch['helper_sources']), 'Helper compilation inputs missing')
    for item in launch['helper_sources']: verify_file(item,base)
    texts={role:verify_file(item,base).read_text(encoding='utf-8',errors='replace') for role,item in proof['logs'].items()}
    expected={'server','client-a','client-b'} if job['tcp'] else {'client'}
    require(set(texts)==set(proof['processes'])==expected and all(p['exit_code']==0 for p in proof['processes'].values()), 'Missing/failed client process')
    for role in expected-{'server'}:
        require('CI_SAVE_QUIT_CLICK:' in texts[role] and 'CI_SAVE_QUIT_TITLE_PASSED' in texts[role], 'Save and Quit not completed')
    if job['tcp']:
        require('All dimensions are saved' in texts['server'], 'Server did not save')
        require('GAME TESTS COMPLETE' in texts['server'] and re.search(r'All [1-9][0-9]* required tests passed',texts['server']), 'Dedicated scenario did not finish its assertions')
    else:require('All dimensions are saved' in texts['client'], 'Integrated server did not save')
    for text in texts.values():
        require('CI_LOADED_ARTIFACT: runtime '+inputs['artifact_hashes']['runtime'] in text,'Actual loaded runtime differs')
        require('NeoForge mod loading, version '+job['loader'] in text,'Actual loader differs')
        if job['world']=='workshop':
            for role in ('example','upstream_example'):
                require('CI_LOADED_ARTIFACT: '+role+' '+inputs['artifact_hashes'][role] in text,'Actual loaded addon differs')
    server_text=texts['server' if job['tcp'] else 'client']
    if job['scenario']!='starter':
        require(f'WWPG_TEST_BACKEND_CONFIG: requested={job["backend"]}, configured={job["backend"]}' in server_text,'Actual configured solver differs')
    require(not any(marker in text for text in texts.values() for marker in ('EXPANDED_REAL_CLIENT_FAILED','API_WORKSHOP_CLIENT_FAILED','CI_SCENARIO_FAILED','Exception in thread')), 'Client failure remains in log')
    require(proof.get('electrical_diagnostics',{})==electrical_diagnostics(texts),'Electrical warnings were omitted from the result summary')
    require(set(job['checks'])<=observed(job,texts), 'Missing actual client assertions: '+str(set(job['checks'])-observed(job,texts)))
    for item in proof['screenshots']:verify_file(item,base)
    require(bool(proof['screenshots']), 'Missing supporting media')


def execute_case(job, inputs, output):
    require(os.name=='nt', 'Graphical lane uses the local Windows desktop')
    if job['scenario']=='starter': return execute_starter(job,inputs,output)
    key=job['world']+'/'+job['profile']
    require(key in inputs.get('worlds',{}), 'Bind the exact '+key+' archive in the frozen input manifest')
    world=inputs['worlds'][key]; verify_file(world,ROOT)
    directory=output/job['id'].replace('/','__')
    from reopen_stationary_yard import extract
    extract(Path(world['path']), directory/'server' if job['tcp'] else directory, not job['tcp'])
    extraction=read((directory/'server' if job['tcp'] else directory)/'extracted-yard.json')
    require(extraction['jar']['sha256']==inputs['artifacts']['runtime']['sha256'], 'World was exported for another runtime')
    selected=dict(job,save_name=extraction['source_save_name'],views=catalog()['yard_views'],
        workshop_views=[f'A{i:02}' for i in range(1,12)] if job['gallery'] else catalog()['workshop_views'])
    write(directory/'case.json',selected);write(directory/'inputs.json',inputs)
    log=directory/'prepare.log'
    command=[sys.executable,str(ROOT/'tools/run_checked.py'),'--timeout','1200','--',str(ROOT/'gradlew.bat'),
        'prepareSuiteClients','-I','tools/client_acceptance/suite.gradle','-PciInputs='+str(directory/'inputs.json'),
        '-PciCase='+str(directory/'case.json'),'-PciDirectory='+str(directory),'-PtestProfile='+job['profile'],
        '-PtestBackend='+job['backend'],'-Pneo_version='+job['loader'],'--no-daemon','--offline']
    with log.open('xb') as stream:subprocess.run(command,cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,check=True)
    launch=read(directory/'launch.json');processes={};handles=[];started=time.monotonic();failure=None
    deadline=900 if job['tcp'] else 1200  # Preserve the existing TCP / GUI launch bounds.
    try:
        for item in launch['launches']:
            if item['role'] not in ('server','client'):
                ready=['CI_TCP_READY','WWPG multiplayer loopback server ready','API_WORKSHOP_TCP_READY']
                while not any(marker in (directory/'server.log').read_text(errors='replace') for marker in ready):
                    require(processes['server'].poll() is None,'Server exited before opening listener')
                    require(time.monotonic()-started<deadline,'Listener startup deadline exceeded');time.sleep(.25)
            target=Path(item['directory']);target.mkdir(exist_ok=True)
            (target/'options.txt').write_text('soundCategory_music:0.0\npauseOnLostFocus:false\n',encoding='utf-8')
            handle=(directory/(item['role']+'.log')).open('xb');handles.append(handle)
            processes[item['role']]=subprocess.Popen(item['command'],cwd=target,env={**os.environ,**item['environment']},stdout=handle,stderr=subprocess.STDOUT)
            write(directory/(item['role']+'-started.json'),dict(pid=processes[item['role']].pid,command=item['command']))
        while any(p.poll() is None for p in processes.values()):
            require(time.monotonic()-started<deadline,'Client session deadline exceeded')
            require(all(p.poll() in (None,0) for p in processes.values()),'A client process failed');time.sleep(.25)
        require(all(p.returncode==0 for p in processes.values()),'A client process exited unsuccessfully')
    except BaseException as error:
        failure=f'{type(error).__name__}: {error}';raise
    finally:
        for process in processes.values():
            if process.poll() is None:process.terminate()
        for process in processes.values():
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        for handle in handles:handle.close()
        write(directory/'processes.json',dict(failure=failure,processes={r:dict(pid=p.pid,exit_code=p.returncode) for r,p in processes.items()}))
    collect_case(directory,directory/'verification.json')
    return dict(status='passed',exit_code=0,proof=descriptor(directory/'verification.json'),log=descriptor(log))


def collect_case(directory,output):
    """Recollect immutable observations after collector-only changes; no JVMs."""
    directory=Path(directory).resolve();job=read(directory/'case.json');inputs=read(directory/'inputs.json')
    from test_suites import plan
    expected=next((item for item in plan('graphical-gallery' if job['gallery'] else 'graphical') if item['id']==job['id']),None)
    require(expected is not None and all(job.get(k)==v for k,v in expected.items()),'Captured case differs from the selected coverage contract')
    outcome=read(directory/'processes.json');require(outcome['failure'] is None,'Failed process attempt cannot become a pass')
    world=inputs['worlds'][job['world']+'/'+job['profile']]
    logs={role:descriptor(directory/(role+'.log')) for role in outcome['processes']}
    texts={role:Path(item['path']).read_text(encoding='utf-8',errors='replace') for role,item in logs.items()}
    proof=dict(schema=2,scenario_id=job['id'],source_sha=inputs['source_sha'],backend=job['backend'],profile=job['profile'],loader=job['loader'],
        artifact_hashes={r:d['sha256'] for r,d in inputs['artifacts'].items()},world=world,passed=True,
        launch=descriptor(directory/'launch.json'),processes=outcome['processes'],logs=logs,
        electrical_diagnostics=electrical_diagnostics(texts),screenshots=[descriptor(p) for p in directory.rglob('screenshots/*.png')])
    original=directory/'verification.json'
    if original.is_file() and Path(output).resolve()!=original:proof['recollected_from']=descriptor(original)
    validate_observation(proof,job,dict(source_sha=inputs['source_sha'],artifact_hashes=proof['artifact_hashes']),directory)
    write(output,proof)
    return proof


def execute_starter(job,inputs,output):
    require('starter' in inputs,'Bind the independently compiled SDK starter proof')
    build=read(verify_file(inputs['starter'],ROOT))
    starter=verify_file(build['compiled_output'],ROOT)
    require(build['passed'] is True and build['compiled_against_api_sha256']==inputs['artifacts']['api']['sha256'], 'Starter did not compile against frozen API')
    world=inputs['worlds']['workshop/BASE'];verify_file(world,ROOT)
    directory=output/job['id'].replace('/','__')
    from reopen_stationary_yard import extract
    extract(Path(world['path']),directory,True)
    name=read(directory/'extracted-yard.json')['source_save_name']
    log=directory/'client.log'
    source_records=[]
    for source in list((ROOT/'tools/documentation_acceptance/src').rglob('*.java'))+[ROOT/'tools/documentation_acceptance/init.gradle',ROOT/'tools/client_acceptance/src/org/cha0scollective/wwpg/acceptance/ClientShutdown.java',ROOT/'tools/client_acceptance/src/org/cha0scollective/wwpg/acceptance/RuntimeIdentity.java']:
        target=directory/'helper-inputs'/source.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(source.read_bytes());source_records.append(dict(descriptor(target),source_path=str(source)))
    command=[sys.executable,str(ROOT/'tools/run_checked.py'),'--timeout','600','--',str(ROOT/'gradlew.bat'),
        'runDocumentationClient','-I','tools/documentation_acceptance/init.gradle','-PdocRuntimeJar='+inputs['artifacts']['runtime']['path'],
        '-PdocStarterJar='+str(starter),'-PdocExampleJar='+inputs['artifacts']['example']['path'],'-PdocDirectory='+str(directory),
        '-PdocWorldName='+name,'-PtestBackend='+job['backend'],'-Pneo_version='+job['loader'],'-PciV2=true',
        '-PtestApiAddon=true','-PtestExampleArtifact='+inputs['artifacts']['example']['path'],
        '-PtestUpstreamExampleArtifact='+inputs['artifacts']['upstream_example']['path'],'--offline','--no-daemon']
    with log.open('xb') as stream:result=subprocess.run(command,cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT)
    require(result.returncode==0,'SDK client failed; preserve '+str(log))
    native=read(directory/'doc-client-result.json')
    require(native['runtime_sha256']==inputs['artifacts']['runtime']['sha256'] and native['starter_sha256']==sha(starter)
            and native['controls'] and native['native_tools'] and abs(native['client_meter_volts']-20)<.1,'SDK actual observations differ')
    helper=ROOT/'build/doc-acceptance/wwpg-documentation-acceptance.jar'
    import shutil
    saved=directory/'helper.jar';shutil.copyfile(helper,saved)
    require(all(sha(item['source_path'])==item['sha256'] for item in source_records),'SDK helper inputs changed during execution')
    write(directory/'launch.json',dict(helper=descriptor(saved),helper_sources=source_records,command=command))
    proof=dict(schema=2,scenario_id=job['id'],source_sha=inputs['source_sha'],backend=job['backend'],profile=job['profile'],loader=job['loader'],
        artifact_hashes={r:d['sha256'] for r,d in inputs['artifacts'].items()},world=world,passed=True,starter_sha256=sha(starter),
        launch=descriptor(directory/'launch.json'),processes={'client':dict(exit_code=0)},logs={'client':descriptor(log)},
        screenshots=[descriptor(p) for p in directory.rglob('screenshots/*.png')])
    proof['electrical_diagnostics']=electrical_diagnostics({'client':log.read_text(encoding='utf-8',errors='replace')})
    validate_observation(proof,job,dict(source_sha=inputs['source_sha'],artifact_hashes=proof['artifact_hashes']),directory)
    write(directory/'verification.json',proof)
    return dict(status='passed',exit_code=0,proof=descriptor(directory/'verification.json'),log=descriptor(log))
