"""Run a frozen API Workshop server and two real TCP Minecraft clients."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def verify_inputs(spec):
    records = {'runtime': spec['jar'], **spec['artifacts']}
    assert set(records) == {'runtime', 'api', 'cee_api', 'example', 'upstream_example'}
    for role, record in records.items():
        assert sha(record['path']) == record['sha256'], f'Frozen {role} changed'
    assert sha(spec['archive']) == spec['archive_sha256'], 'Exact Workshop ZIP changed'
    return {role: record['sha256'] for role, record in records.items()}


def run(launch_path, timeout):
    spec = json.loads(launch_path.read_text(encoding='utf-8'))
    hashes = verify_inputs(spec)
    root = launch_path.resolve().parent
    evidence = root / 'api-multiplayer-result.json'
    assert not evidence.exists(), 'Use a new extraction; retain previous evidence'
    assert [item['role'] for item in spec['launches']] == ['server', 'client-a', 'client-b']
    processes, handles = {}, []
    started, failure, passed = time.monotonic(), None, False
    try:
        for launch in spec['launches']:
            role = launch['role']
            if role != 'server':
                server_log = root / 'server.log'
                while 'API_WORKSHOP_TCP_READY' not in server_log.read_text(encoding='utf-8', errors='replace'):
                    if processes['server'].poll() is not None:
                        raise RuntimeError('Server exited before opening its listener')
                    if time.monotonic() - started > timeout:
                        raise TimeoutError('API Workshop server startup timed out')
                    time.sleep(.25)
            log = root / f'{role}.log'
            assert not log.exists(), 'Keep failed logs and use a fresh run directory'
            handle = log.open('wb')
            handles.append(handle)
            environment = os.environ.copy()
            environment.update({key: str(value) for key, value in launch['environment'].items()})
            processes[role] = subprocess.Popen(launch['command'], cwd=launch['directory'],
                env=environment, stdout=handle, stderr=subprocess.STDOUT,
                start_new_session=os.name != 'nt')
            print(f'Launched {role} PID {processes[role].pid}', flush=True)
        while any(process.poll() is None for process in processes.values()):
            if time.monotonic() - started > timeout:
                raise TimeoutError('API Workshop two-client session timed out')
            for role, process in processes.items():
                if process.poll() not in (None, 0):
                    raise RuntimeError(f'{role} exited with {process.returncode}; retain its log')
            time.sleep(.25)
        texts = {role: (root / f'{role}.log').read_text(encoding='utf-8', errors='replace')
                 for role in processes}
        assert 'API_WORKSHOP_TWO_CLIENTS_PASSED' in texts['server'], 'Server acceptance incomplete'
        for role in ('client-a', 'client-b'):
            assert 'API_WORKSHOP_CLIENT_PASSED' in texts[role], f'{role} acceptance incomplete'
        for text in texts.values():
            assert 'API_WORKSHOP_CLIENT_FAILED' not in text and 'API_WORKSHOP_SERVER_FAILED' not in text
        assert verify_inputs(spec) == hashes
        passed = True
    except (Exception, KeyboardInterrupt) as error:
        failure = f'{type(error).__name__}: {error}'
        print(failure, flush=True)
        # Capture hangs before stopping only the Java processes started by this run.
        jcmd = Path(spec['launches'][0]['command'][0]).with_name('jcmd.exe' if os.name == 'nt' else 'jcmd')
        for role, process in processes.items():
            if process.poll() is None:
                try:
                    dump = subprocess.run([str(jcmd), str(process.pid), 'Thread.print', '-l'],
                        capture_output=True, text=True, timeout=20)
                    (root / f'{role}-threads.txt').write_text(dump.stdout + dump.stderr, encoding='utf-8')
                except (OSError, subprocess.TimeoutExpired):
                    pass
    finally:
        for process in processes.values():
            if process.poll() is None:
                process.terminate()
        for process in processes.values():
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for handle in handles:
            handle.close()
        report = dict(schema=1, completed=passed, passed=passed, failure=failure,
            elapsed_seconds=round(time.monotonic() - started, 2), source_sha=spec['source_sha'],
            artifact_hashes=hashes, world_sha256=spec['archive_sha256'],
            profile=spec['profile'], backend=spec['backend'], real_clients=['WWPG_A', 'WWPG_B'],
            processes={role: dict(pid=process.pid, exit_code=process.returncode)
                       for role, process in processes.items()})
        evidence.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    return 0 if passed else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('launch', type=Path)
    parser.add_argument('--timeout', type=float, default=1200)
    args = parser.parse_args()
    raise SystemExit(run(args.launch, args.timeout))
