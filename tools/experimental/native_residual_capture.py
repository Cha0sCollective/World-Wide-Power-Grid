"""Check, freeze and run a distinctly marked native residual-input capture."""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import subprocess
import sys
import zipfile

from cpu_profile import write_new
from native_stages import MARKER, WINDOWS_DLL, inspect_runtime as inspect_clean
from plain_wire_benchmark import main as benchmark
from run_native_profile_checks import windows_path, linux_path
from summarize_tree import ROOT, evidence, read, sha

KIND = 'native-residual-capture'
SLOTS = ('outer-4096', 'outer-40960', 'fit')


def parse_capture(path):
    data = path.read_bytes()
    magic, version, n, nnz, stage, ordinal, before, reserved = struct.unpack_from('<8sIIIIQII', data)
    assert magic == b'WWPGRS01' and version == 1 and n == 14925 and 0 <= nnz <= 1000000
    assert stage in (0,1) and reserved == 0 and len(data) == 76 + 28*n + 12*nnz
    offset = 40
    def take(kind, count):
        nonlocal offset
        values = struct.unpack_from('<' + str(count) + kind, data, offset)
        offset += struct.calcsize(kind)*count
        return values
    alpha, beta = take('d',2)
    assert alpha == 1 and beta == -1
    columns, rows = take('i',n+1), take('i',nnz)
    assert columns[0] == 0 and columns[-1] == nnz
    assert all(0 <= a <= b <= nnz for a,b in zip(columns, columns[1:]))
    assert all(0 <= row < n for row in rows)
    for column in range(n):
        indices = rows[columns[column]:columns[column+1]]
        assert all(a < b for a,b in zip(indices, indices[1:])), 'Noncanonical column rows'
    values, x, input_y = take('d',nnz), take('d',n), take('d',n)
    result, after = take('I',2)
    output_y = take('d',n)
    assert result == 0 and data[offset:] == b'ENDRS001'
    degrees = Counter(rows)
    histogram = Counter(degrees.get(row,0) for row in range(n))
    return dict(file=evidence(path), rows=n, nonzeros=nnz, stage=stage, outer_ordinal=ordinal,
        mxcsr_before=before, mxcsr_after=after, row_degree_histogram=dict(sorted(histogram.items())),
        maximum_row_degree=max(histogram), zero_state_entries=sum(v == 0 for v in x),
        nonfinite={name:sum(not math.isfinite(v) for v in sequence)
                   for name,sequence in (('matrix',values),('state',x),('input',input_y),('output',output_y))},
        output_bytes_sha256=hashlib.sha256(data[offset-8*n:offset]).hexdigest())


def verify_build(path):
    build = read(path)
    assert build['diagnostic'] and build['diagnostic_kind'] == KIND
    assert build['tracked_rows'] == 14925 and build['capture_slots'] == list(SLOTS)
    assert build['original_candidate_source_restored']
    for item in build['inputs'] + build['logs'] + list(build['artifacts'].values()) + [build['candidate_build']]:
        assert sha(windows_path(item['path'])) == item['sha256'], item['path']
    return build


def checks(args):
    path = ROOT / args.build
    build = verify_build(path)
    folder = path.parent / 'checks'
    (folder / 'benchmark').mkdir(parents=True, exist_ok=False)
    result = subprocess.run([str(windows_path(build['artifacts']['capture_checks.exe']['path']))], cwd=folder,
                            capture_output=True, timeout=30)
    (folder / 'process.log').write_bytes(result.stdout + result.stderr)
    assert result.returncode == 0 and b'PASS 3 capture paths preserve exact output and MXCSR' in result.stdout
    assert len(re.findall(rb'WWPG_RESIDUAL_CAPTURE .* okay=1', result.stdout)) == 3
    captures = [parse_capture(folder / 'benchmark' / ('native-residual-' + slot + '.bin')) for slot in SLOTS]
    # The importer must reject incomplete and extended recordings.
    probe = folder / 'invalid-probe.bin'
    first = (folder / 'benchmark/native-residual-outer-4096.bin').read_bytes()
    rejected = 0
    for data in (first[:-1], first + b'extra', b'BADMAGIC' + first[8:]):
        probe.write_bytes(data)
        try: parse_capture(probe)
        except (AssertionError, struct.error): rejected += 1
    assert rejected == 3
    objdump = str(PurePosixPath(build['commands'][0][0]).with_name('x86_64-w64-mingw32-objdump'))
    result = subprocess.run(['wsl','-d','Ubuntu-22.04','--',objdump,'-p',linux_path(build['artifacts']['libpowergridNativeReduction.dll']['path'])],
                            capture_output=True, timeout=60)
    (folder / 'pe.txt').write_bytes(result.stdout + result.stderr)
    assert result.returncode == 0
    message = result.stdout.decode(errors='replace')
    imports = sorted(re.findall(r'DLL Name:\s*(\S+)',message))
    exports = sorted(re.findall(r'^\s*\[\s*\d+\]\s+(\S+)\s*$',message,re.MULTILINE))
    baseline = read(ROOT / 'local/native-stages-01/native-safety-checks.json')['pe_audits']['baseline']
    assert imports == baseline['imports'] and exports == baseline['exports']
    verify_build(path)
    proof = dict(schema=1,passed=True,build=evidence(path),capture_paths_checked=3,malformed_recordings_rejected=3,
        captures=captures,imports=imports,exports=exports,
        evidence=[evidence(folder / p) for p in ('process.log','pe.txt')],
        note='Focused capture transparency and parser checks, not new gameplay acceptance.')
    write_new(path.parent / 'checks.json',proof)
    print(json.dumps(dict(passed=True,capture_paths=3,malformed_rejected=3)))


def freeze(args):
    base_path, build_path, check_path = (ROOT / p for p in (args.base_inputs,args.build,args.checks))
    base, build, checked = read(base_path), verify_build(build_path), read(check_path)
    assert checked['passed'] and checked['build']['sha256'] == sha(build_path)
    assert all(sha(ROOT / p['path']) == p['sha256'] for p in base['sources'])
    assert all(sha(Path(p['path'])) == p['sha256'] for p in base['artifacts'].values())
    original = Path(base['artifacts']['runtime']['path'])
    original_marker = inspect_clean(original,False)
    assert original_marker['native_build']['sha256'] == build['candidate_build']['sha256']
    native = windows_path(build['artifacts']['libpowergridNativeReduction.dll']['path'])
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    runtime = folder / 'wwpg-0.2.7-experimental.native-residual-capture.jar'
    marker = dict(schema=1,diagnostic=True,diagnostic_kind=KIND,native_sha256=sha(native),tracked_rows=14925,
        capture_slots=list(SLOTS),native_build=evidence(build_path),original_runtime_sha256=sha(original),
        gameplay_class_bytes_unchanged=True)
    props_name = 'native/wwpg/safe/guarded.properties'
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(runtime,'w') as new:
        props = old.read(props_name).decode()
        assert len(re.findall(r'windows\.sha256=[0-9a-f]{64}',props)) == 1
        props = re.sub(r'(windows\.sha256=)[0-9a-f]{64}', lambda match: match[1] + sha(native), props)
        for item in old.infolist():
            data = (native.read_bytes() if item.filename == WINDOWS_DLL else props.encode() if item.filename == props_name else
                    (json.dumps(marker,indent=2)+'\n').encode() if item.filename == MARKER else old.read(item))
            new.writestr(item,data)
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(runtime) as new:
        assert set(old.namelist()) == set(new.namelist())
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        assert set(changed) == {WINDOWS_DLL,props_name,MARKER}
        classes = [n for n in old.namelist() if n.endswith('.class')]
        assert all(old.read(n) == new.read(n) for n in classes)
    artifacts = dict(runtime=dict(path=str(runtime),sha256=sha(runtime)))
    for role in ('fixtures','cee'):
        target = folder / Path(base['artifacts'][role]['path']).name
        shutil.copy2(base['artifacts'][role]['path'],target)
        artifacts[role] = dict(path=str(target),sha256=sha(target))
    tool_copy = folder / Path(__file__).name
    shutil.copy2(__file__,tool_copy)
    record = dict(schema=1,diagnostic=True,diagnostic_kind=KIND,java_home=base['java_home'],sources=base['sources'],
        artifacts=artifacts,native_unchanged=False,gameplay_classes_unchanged=True,gameplay_classes_compared=len(classes),
        changed_jar_entries=changed,evidence=[evidence(p) for p in (base_path,build_path,check_path,tool_copy)])
    write_new(folder / 'inputs.json',record)
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'),runtime=artifacts['runtime'],native_sha256=sha(native))))


def inspect_runtime(path, enabled):
    assert not enabled
    with zipfile.ZipFile(path) as jar:
        marker = json.loads(jar.read(MARKER))
        assert marker['schema'] == 1 and marker['diagnostic'] is True and marker['diagnostic_kind'] == KIND
        assert marker['tracked_rows'] == 14925 and marker['capture_slots'] == list(SLOTS)
        assert 'profile_magic' not in marker and 'profile_stages' not in marker
        assert hashlib.sha256(jar.read(WINDOWS_DLL)).hexdigest() == marker['native_sha256']
        assert re.findall(r'windows\.sha256=([0-9a-f]{64})', jar.read('native/wwpg/safe/guarded.properties').decode()) == [marker['native_sha256']]
        return marker


def validate(directory):
    protocol, launch = read(directory / 'experiment.json'), read(directory / 'launch.json')
    assert protocol['diagnostic_kind'] == KIND and protocol['native_work'] and protocol['detailed'] and not protocol['native_stages']
    assert protocol['validation']['passed'] and protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    assert read(directory / 'runner-exit.json')['exit_code'] == 0 and read(directory / 'shutdown-save.json')['all_dimensions_saved']
    assert sha(Path(launch['runtime']['path'])) == launch['runtime']['sha256'] == protocol['runtime']['sha256']
    marker = inspect_runtime(launch['runtime']['path'],False)
    native = list((directory / '.pg-native/wwpg-safety-1').rglob('*.dll'))
    assert len(native) == 1 and sha(native[0]) == marker['native_sha256']
    for item in launch['runner_sources']: assert sha(Path(item['path'])) == item['sha256']
    captures = [parse_capture(directory / 'benchmark' / ('native-residual-' + slot + '.bin')) for slot in SLOTS]
    assert [c['stage'] for c in captures] == [0,0,1]
    assert [c['outer_ordinal'] for c in captures[:2]] == [4096,40960] and captures[2]['outer_ordinal'] >= 40960
    assert all(not any(c['nonfinite'].values()) for c in captures)
    log = (directory / 'process.log').read_text(errors='replace')
    records = re.findall(r'WWPG_RESIDUAL_CAPTURE slot=(\d+) stage=(\d+) outer=(\d+) rows=14925 nnz=(\d+) okay=(\d+)',log)
    assert len(records) == 3 and {int(r[0]) for r in records} == {0,1,2} and all(r[4] == '1' for r in records)
    proof = dict(schema=1,passed=True,diagnostic=True,diagnostic_kind=KIND,captures=captures,
        marker=marker,loaded_native=evidence(native[0]),native_work_observation=protocol['native_work_observation'],
        evidence=[evidence(directory / p) for p in ('experiment.json','launch.json','runner-exit.json','shutdown-save.json','process.log')],
        limitations=['Three selected full-sized residual calls, not an exhaustive set of mod inputs.',
            'Capture ordinals identify outer-loop/fitting calls, not guaranteed stable-phase positions.',
            'Detailed timers and native file I/O are enabled; these are not clean performance timings.'])
    write_new(directory / 'native-residual-captures.json',proof)
    return proof


def main():
    mode = sys.argv.pop(1)
    if mode == 'run':
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument('--name',required=True)
        args,_ = parser.parse_known_args()
        benchmark(runtime_inspector=inspect_runtime,diagnostic_kind=KIND)
        proof = validate(ROOT / 'local/benchmarks' / args.name)
        print(json.dumps(dict(passed=True,captures=proof['captures'])))
        return
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build',required=True)
    if mode == 'freeze':
        parser.add_argument('--name',required=True)
        parser.add_argument('--base-inputs',required=True)
        parser.add_argument('--checks',required=True)
    assert mode in ('checks','freeze')
    args = parser.parse_args()
    (checks if mode == 'checks' else freeze)(args)


if __name__ == '__main__':
    main()
