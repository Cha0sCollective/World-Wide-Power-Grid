"""Run exact numerical checks and a focused native microbenchmark; preserve all evidence."""
import argparse
import csv
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import time

from cpu_profile import write_new
from freeze_tree import ROOT, sha
from run_native_profile_checks import windows_path, linux_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', required=True)
    parser.add_argument('--toolchain', required=True)
    parser.add_argument('--baseline-checks', required=True)
    args = parser.parse_args()
    build_path, toolchain_path, baseline_path = (ROOT / p for p in (args.build,args.toolchain,args.baseline_checks))
    build, toolchain, baseline = (json.loads(p.read_text()) for p in (build_path,toolchain_path,baseline_path))
    assert baseline['passed'] and baseline['build']['sha256'] == build['baseline_build']['sha256']
    assert all(sha(windows_path(p['path'])) == p['sha256'] for p in build['artifacts'].values())
    folder = build_path.parent / 'checks'
    folder.mkdir(exist_ok=False)
    executable = windows_path(build['artifacts']['numeric_checks.exe']['path'])
    start = time.monotonic()
    result = subprocess.run([str(executable)],capture_output=True,timeout=120,cwd=folder)
    log = folder / 'numeric.log'
    log.write_bytes(result.stdout + result.stderr)
    output = log.read_text(errors='replace')
    matched = re.search(r'PASS (\d+) exact scalar/candidate comparisons',output)
    proof = dict(schema=1, passed=result.returncode == 0 and bool(matched), exit_code=result.returncode,
        exact_comparisons=int(matched[1]) if matched else 0, fp_modes=16,
        elapsed_seconds=time.monotonic()-start, build=dict(path=str(build_path),sha256=sha(build_path)),
        evidence=[dict(path=str(p),sha256=sha(p)) for p in (log,baseline_path,toolchain_path,Path(__file__))],
        scope='Focused numerical equivalence, protected boundaries and PE identity; no gameplay acceptance.')
    if not proof['passed']:
        write_new(build_path.parent / 'checks.json',proof)
        print(output)
        raise SystemExit(1)
    objdump = str(PurePosixPath(linux_path(toolchain['compiler'])).with_name('x86_64-w64-mingw32-objdump'))
    dll = build['artifacts']['libpowergridNativeReduction.dll']
    command = ['wsl','-d','Ubuntu-22.04','--',objdump,'-p',linux_path(dll['path'])]
    result = subprocess.run(command,capture_output=True,timeout=60)
    audit_path = folder / 'candidate-pe.txt'
    audit_path.write_bytes(result.stdout + result.stderr)
    message = audit_path.read_text(errors='replace')
    assert result.returncode == 0 and 'file format pei-x86-64' in message
    imports = sorted(re.findall(r'DLL Name:\s*(\S+)',message))
    exports = sorted(re.findall(r'^\s*\[\s*\d+\]\s+(\S+)\s*$',message,re.MULTILINE))
    assert imports == baseline['pe_audits']['baseline']['imports']
    assert exports == baseline['pe_audits']['baseline']['exports']
    proof['pe_audit'] = dict(imports=imports,exports=exports,command=command,
        evidence=dict(path=str(audit_path),sha256=sha(audit_path)))
    result = subprocess.run([str(executable),'--benchmark'],capture_output=True,timeout=120,cwd=folder)
    benchmark = folder / 'microbenchmark.csv'
    benchmark.write_bytes(result.stdout)
    assert result.returncode == 0 and not result.stderr
    rows = list(csv.DictReader(io.StringIO(result.stdout.decode())))
    assert len(rows) == 96
    measurements = []
    for kind in ('norm','finite'):
        for shape in range(3):
            selected = [r for r in rows if r['kind'] == kind and int(r['shape']) == shape]
            reference = [r for r in selected if r['role'] == 'reference']
            candidate = [r for r in selected if r['role'] == 'candidate']
            assert len(reference) == len(candidate) == 8
            assert all(a['checksum'] == b['checksum'] and a['calls'] == b['calls'] for a,b in zip(reference,candidate))
            means = {role: sum(float(r['elapsed_ms']) for r in selected if r['role'] == role)/
                sum(int(r['calls']) for r in selected if r['role'] == role) for role in ('reference','candidate')}
            measurements.append(dict(kind=kind,shape=shape,mean_ms_per_call=means,
                candidate_over_reference=means['candidate']/means['reference']))
    proof['microbenchmark'] = dict(evidence=dict(path=str(benchmark),sha256=sha(benchmark)), measurements=measurements,
        note='Alternating order, 14925 rows, eight blocks per shape. Shape 2 intentionally exercises subnormal fallback. Not game timing.')
    proof['artifacts_unchanged'] = all(sha(windows_path(p['path'])) == p['sha256'] for p in build['artifacts'].values())
    assert proof['artifacts_unchanged']
    write_new(build_path.parent / 'checks.json',proof)
    print(json.dumps(dict(passed=True,exact_comparisons=proof['exact_comparisons'],microbenchmark=measurements)))


if __name__ == '__main__':
    main()
