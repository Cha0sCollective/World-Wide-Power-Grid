"""Add sparse live reporting to the preserved teardown-only path observer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--base-observer-build', required=True)
    args = parser.parse_args()
    base_path = ROOT / args.base_observer_build
    base = json.loads(base_path.read_text())
    assert base['diagnostic_kind'] == 'native-reduction-counts' and base['original_candidate_source_restored']
    for item in base['inputs'] + base['logs'] + [base['artifact'], base['candidate_build']]:
        assert sha(Path(item['path'])) == item['sha256'], item['path']
    folder = (ROOT / 'local' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    folder.mkdir(exist_ok=False)
    shutil.copytree(base_path.parent / 'src', folder / 'src')
    header = folder / 'src/reduction.h'
    original = header.read_text()
    changes = [
        ('static _Atomic uint64_t wwpg_path_counts[4];',
         'static _Atomic uint64_t wwpg_path_counts[4];\nstatic atomic_flag wwpg_report_lock = ATOMIC_FLAG_INIT;\nstatic void wwpg_report_reduction_paths(void);'),
        ('    if(size == 14925) atomic_fetch_add_explicit(&wwpg_path_counts[path],1,memory_order_relaxed);',
         '    if(size == 14925) {\n        uint64_t previous = atomic_fetch_add_explicit(&wwpg_path_counts[path],1,memory_order_relaxed);\n        if((previous & 4095) == 4095) wwpg_report_reduction_paths();\n    }'),
        ('static void wwpg_report_reduction_paths(void) {',
         'static void wwpg_report_reduction_paths(void) {\n    while(atomic_flag_test_and_set_explicit(&wwpg_report_lock,memory_order_acquire)) {}'),
        ('    fflush(stdout);', '    fflush(stdout);\n    atomic_flag_clear_explicit(&wwpg_report_lock,memory_order_release);')]
    updated = original
    for old, new in changes:
        assert updated.count(old) == 1
        updated = updated.replace(old, new)
    restored = updated
    for old, new in reversed(changes): restored = restored.replace(new, old)
    assert restored == original
    header.write_text(updated)
    commands = [[value.replace(str(base_path.parent), str(folder)) for value in command] for command in base['commands']]
    candidate = json.loads(Path(base['candidate_build']['path']).read_text())
    working = Path(candidate['baseline_build']['path']).parent / 'build'
    env = dict(os.environ, PATH=str(Path(commands[0][0]).parent) + os.pathsep + os.environ['PATH'])
    inputs = [base_path, Path(__file__)] + sorted((folder / 'src').iterdir())
    hashes = {str(p): sha(p) for p in inputs}
    for index, command in enumerate(commands):
        with (folder / ('build-' + str(index) + '.log')).open('x') as log:
            result = subprocess.run(command, cwd=working, env=env, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode: raise SystemExit(result.returncode)
    assert all(sha(Path(p)) == digest for p, digest in hashes.items())
    dll = folder / 'libpowergridNativeReduction.dll'
    record = dict(schema=1, diagnostic=True, diagnostic_kind=base['diagnostic_kind'],
        candidate_build=base['candidate_build'], base_observer_build=dict(path=str(base_path), sha256=sha(base_path)),
        original_candidate_source_restored=True, original_observer_source_restored=True, tracked_rows=14925,
        periodic_report_per_path_calls=4096, artifact=dict(path=str(dll), sha256=sha(dll)), commands=commands,
        inputs=[dict(path=p, sha256=digest) for p, digest in hashes.items()],
        logs=[dict(path=str(p), sha256=sha(p)) for p in sorted(folder.glob('*.log'))],
        note='Cumulative counts print every 4096 calls of each path and at solver teardown. Last report can omit trailing calls; not stable-only evidence.')
    (folder / 'build.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(dict(built=str(folder / 'build.json'), native=record['artifact'])))


if __name__ == '__main__':
    main()
