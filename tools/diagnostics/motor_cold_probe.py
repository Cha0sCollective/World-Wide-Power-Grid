"""Diagnostic only: original mixed-motor assertions with state observation."""
from pathlib import Path
import hashlib,json,os,subprocess,sys
root=Path(__file__).resolve().parents[2];out=root/'local/motor-cold-probe';out.mkdir(parents=True,exist_ok=False)
env=dict(os.environ);env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -XX:ActiveProcessorCount=2'
records=[]
for number,phase in enumerate(['SETUP','VERIFY','VERIFY','VERIFY']):
 log=out/f'{number}-{phase}.log'
 args=[sys.executable,str(root/'tools/run_checked.py'),'--timeout','600','--',str(root/'gradlew'),'runPackagedGameTestServer',
  '-PtestArtifact='+str(root/'build/libs/wwpg-0.2.8-rc.1.jar'),'-PtestFixtureArtifact='+str(root/'build/libs/wwpg-0.2.8-rc.1-gametests.jar'),
  '-PtestNamespaces=wwpg,wwpg_stationary,wwpg_design,wwpg_distribution,wwpg_wires,wwpg_bundle,wwpg_coupled,wwpg_platform',
  '-PtestBackend=NATIVE','-PtestProfile=BASE','-Pneo_version=21.1.248','-PrestartPhase='+phase,'-PtestDirectory='+str(out/'saved-world'),
  '-x','build','-x','jar','-x','test','--no-daemon']
 with log.open('xb') as stream:result=subprocess.run(args,cwd=root,env=env,stdout=stream,stderr=subprocess.STDOUT)
 records.append(dict(phase=phase,number=number,exit_code=result.returncode,log=log.name,sha256=hashlib.sha256(log.read_bytes()).hexdigest()))
 (out/'diagnostic-results.json').write_text(json.dumps(dict(diagnostic=True,release_acceptance=False,records=records),indent=2)+'\n')
 for line in log.read_text(errors='replace').splitlines():
  if 'MOTOR_DIAGNOSTIC' in line or 'failed at' in line or 'required tests passed' in line:print(line,flush=True)
 if result.returncode:raise SystemExit(result.returncode)
