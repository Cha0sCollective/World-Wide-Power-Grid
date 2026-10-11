"""Launch one dedicated server and two real TCP clients from frozen launch files."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('launch',type=Path)
    parser.add_argument('--timeout',type=float,default=900)
    args=parser.parse_args()
    spec=json.loads(args.launch.read_text(encoding='utf-8'))
    assert digest(spec['jar']['path'])==spec['jar']['sha256'],'Frozen jar changed'
    assert digest(spec['archive'])==spec['archive_sha256'],'Exact exported ZIP changed'
    root=args.launch.resolve().parent
    evidence=root/'multiplayer-result.json'
    assert not evidence.exists(),'Use a new extraction for each multiplayer run'
    started=time.monotonic()
    processes={}
    handles=[]
    failure=None
    success=False
    try:
        for launch in spec['launches']:
            role=launch['role']
            if role!='server':
                # Wait for the actual loopback listener, rather than guessing startup time.
                while 'WWPG multiplayer loopback server ready' not in (root/'server.log').read_text(encoding='utf-8',errors='replace'):
                    if processes['server'].poll() is not None:raise RuntimeError('Server exited before opening its listener')
                    if time.monotonic()-started>args.timeout:raise TimeoutError('Server startup timed out')
                    time.sleep(.25)
            log=root/f'{role}.log'
            assert not log.exists(),'Do not overwrite failed-run logs'
            handle=log.open('wb');handles.append(handle)
            env=os.environ.copy();env.update({k:str(v) for k,v in launch['environment'].items()})
            processes[role]=subprocess.Popen(launch['command'],cwd=launch['directory'],env=env,stdout=handle,stderr=subprocess.STDOUT,start_new_session=os.name!='nt')
            print(f'Launched {role} PID {processes[role].pid}',flush=True)
        while any(p.poll() is None for p in processes.values()):
            if time.monotonic()-started>args.timeout:raise TimeoutError('Real-client session timed out')
            for role,process in processes.items():
                if process.poll() not in (None,0):raise RuntimeError(f'{role} exited with {process.returncode}; retain its log')
            time.sleep(.25)
        texts={role:(root/f'{role}.log').read_text(encoding='utf-8',errors='replace') for role in processes}
        assert 'STATIONARY_TWO_REAL_CLIENTS_PASSED' in texts['server'],'Dedicated stationary checks did not complete'
        for role in ('client-a','client-b'):
            assert 'WWPG_REAL_CLIENT_PASSED' in texts[role],f'{role} did not complete real-client acceptance'
            assert 'WWPG real client failed' not in texts[role],f'{role} reported a failed interaction'
        assert digest(spec['jar']['path'])==spec['jar']['sha256'],'Frozen jar changed during acceptance'
        success=True
    except (Exception,KeyboardInterrupt) as error:
        failure=f'{type(error).__name__}: {error}'
        print(failure,flush=True)
        # Keep useful thread evidence before stopping only the processes we launched.
        jcmd=Path(spec['launches'][0]['command'][0]).with_name('jcmd.exe' if os.name=='nt' else 'jcmd')
        for role,process in processes.items():
            if process.poll() is None:
                try:
                    dump=subprocess.run([str(jcmd),str(process.pid),'Thread.print','-l'],capture_output=True,text=True,timeout=20)
                    (root/f'{role}-threads.txt').write_text(dump.stdout+dump.stderr,encoding='utf-8')
                except (OSError,subprocess.TimeoutExpired):pass
    finally:
        for process in processes.values():
            if process.poll() is None:process.terminate()
        for process in processes.values():
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        for handle in handles:handle.close()
        evidence.write_text(json.dumps({'schema':1,'passed':success,'failure':failure,'elapsed_seconds':round(time.monotonic()-started,2),'jar':spec['jar'],'archive_sha256':spec['archive_sha256'],'profile':spec['profile'],'backend':spec['backend'],'real_clients':['WWPG_A','WWPG_B'],'processes':{role:{'pid':p.pid,'exit_code':p.returncode} for role,p in processes.items()}},indent=2)+'\n',encoding='utf-8')
    return 0 if success else 1


if __name__=='__main__':
    raise SystemExit(main())
