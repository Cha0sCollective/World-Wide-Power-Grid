"""Run focused safety executables and inspect the two isolated Windows DLLs."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import time

from cpu_profile import write_new
from freeze_tree import ROOT, sha


def windows_path(value):
    if value.startswith('/mnt/') and value[6:7] == '/':
        value = value[5].upper() + ':/' + value[7:]
    return Path(value)


def linux_path(value):
    value = str(value)
    if re.match(r'^[A-Za-z]:[\\/]', value):
        return '/mnt/' + value[0].lower() + '/' + value[3:].replace('\\', '/')
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', required=True)
    parser.add_argument('--toolchain', required=True)
    parser.add_argument('--distribution', default='Ubuntu-22.04')
    args = parser.parse_args()
    build_path, toolchain_path = (ROOT / v for v in (args.build, args.toolchain))
    build, toolchain = (json.loads(p.read_text()) for p in (build_path, toolchain_path))
    directory = build_path.parent / 'safety-checks'
    directory.mkdir(exist_ok=False)
    artifacts = build['artifacts']
    for item in artifacts.values():
        assert sha(windows_path(item['path'])) == item['sha256']
    objdump = str(PurePosixPath(linux_path(toolchain['compiler'])).with_name('x86_64-w64-mingw32-objdump'))
    checks = []
    for role, name in (('baseline', 'sparse_safety_tests.exe'), ('profile', 'profile_sparse_safety_tests.exe')):
        executable = windows_path(artifacts[name]['path'])
        started = datetime.now(timezone.utc).isoformat()
        begin = time.monotonic()
        result = subprocess.run([str(executable)], cwd=executable.parent, capture_output=True, timeout=120)
        output = result.stdout + result.stderr
        log = directory / (role + '.log')
        log.write_bytes(output)
        message = output.decode('utf-8', errors='replace')
        matched = re.findall(r'PASS (\d+) native safety checks plus (\d+) valid refactor/cleanup cycles', message)
        passed = result.returncode == 0 and len(matched) == 1 and int(matched[0][1]) == 100
        if role == 'profile':
            passed &= 'PASS 4 active profile counter checks' in message
        checks.append(dict(role=role, executable=artifacts[name], started_utc=started,
            elapsed_seconds=time.monotonic() - begin, exit_code=result.returncode, passed=passed,
            native_checks=int(matched[0][0]) if matched else 0, cleanup_cycles=int(matched[0][1]) if matched else 0,
            active_counter_checks=4 if role == 'profile' and passed else 0,
            output=dict(path=str(log), sha256=sha(log))))
        print(json.dumps(checks[-1]), flush=True)
    audits = {}
    for role, name in (('baseline', 'libpowergridNative7.dll'), ('profile', 'libpowergridNativeProfile.dll')):
        command = ['wsl', '-d', args.distribution, '--', objdump, '-p', linux_path(artifacts[name]['path'])]
        result = subprocess.run(command, capture_output=True, timeout=60)
        output = result.stdout + result.stderr
        log = directory / (role + '-pe.txt')
        log.write_bytes(output)
        message = output.decode('utf-8', errors='replace')
        assert result.returncode == 0 and 'file format pei-x86-64' in message
        imports = re.findall(r'DLL Name:\s*(\S+)', message)
        exports = re.findall(r'^\s*\[\s*\d+\]\s+(\S+)\s*$', message, re.MULTILINE)
        assert imports and exports
        assert not any('__wrap_' in item for item in exports)
        audits[role] = dict(artifact=artifacts[name], imports=sorted(imports), exports=sorted(exports),
            command=command, output=dict(path=str(log), sha256=sha(log)))
    assert audits['baseline']['imports'] == audits['profile']['imports']
    assert set(audits['baseline']['exports']) <= set(audits['profile']['exports'])
    getter = 'Java_org_cha0scollective_wwpg_benchmark_NativeStages_snapshot'
    assert getter in audits['profile']['exports'] and getter not in audits['baseline']['exports']
    assert all(sha(windows_path(item['path'])) == item['sha256'] for item in artifacts.values())
    proof = dict(schema=1, passed=all(item['passed'] for item in checks),
        build=dict(path=str(build_path), sha256=sha(build_path)),
        toolchain=dict(path=str(toolchain_path), sha256=sha(toolchain_path)),
        checks=checks, pe_audits=audits, artifacts_unchanged=True,
        evidence=[dict(path=str(Path(__file__)), sha256=sha(Path(__file__)))],
        scope='Standalone native safety and active-counter checks only; no gameplay or full acceptance.')
    write_new(build_path.parent / 'native-safety-checks.json', proof)
    if not proof['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
