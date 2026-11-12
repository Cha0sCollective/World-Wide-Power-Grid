"""Audit the focused indexed-waveform trial without relabeling earlier evidence."""
import argparse
import ast
import csv
import json
from pathlib import Path
import statistics
import xml.etree.ElementTree as ET

from plain_wire_benchmark import validate_indexed_waveform
from summarize_current_profile import signature
from summarize_goal_runs import capture
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempts', nargs='+', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    frozen = ROOT / 'local/frozen/prototype-13a/inputs.json'
    inputs = read(frozen)
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in inputs['sources'])
    assert all(sha(Path(a['path'])) == a['sha256'] for a in inputs['artifacts'].values())
    previous = {s['path']: s['sha256'] for s in read(ROOT / 'local/frozen/prototype-12g/inputs.json')['sources']}
    changed = [s['path'] for s in inputs['sources'] if previous.get(s['path']) != s['sha256']]
    assert sorted(changed) == ['gradle.properties', 'src/main/java/org/cha0scollective/wwpg/extension/NativeApiReadings.java']
    proofs = [evidence(frozen), evidence(ROOT / 'local/indexed-waveform-build-01.log'),
              evidence(ROOT / 'local/indexed-waveform-evidence-01.log')]
    checks = []
    for suffix, enabled, backend, count in [('native', True, 'NATIVE', 32), ('java', True, 'JAVA', 32),
                                           ('off', False, 'NATIVE', 32), ('scope', True, 'NATIVE', 9)]:
        name = f'indexed-waveform-{suffix}-01'
        folder = ROOT / 'local/checks' / name
        result = read(folder / 'result.json')
        assert result['passed'] and result['required_tests_passed'] == count and result['exit_code'] == 0
        assert result['artifacts'] == inputs['artifacts'] and result['artifacts_unchanged']
        assert result['indexed_waveform'] == enabled and result['backend'] == backend
        assert result['scoped_restoration'] and result['cee_state_geometry']
        assert result['addons_unchanged'] and result['addon_classpath_verified']
        for item in result['addon_artifacts'].values():
            assert sha(Path(item['path'])) == item['sha256']
        observed = validate_indexed_waveform((folder / 'process.log').read_text(errors='replace'), enabled)
        assert observed == result['waveform_execution']
        if enabled and suffix != 'scope':
            assert observed['middleVisits'] > 0
        checks.append(dict(name=name, result=result))
        proofs.extend(evidence(folder / p) for p in ('invocation.json', 'result.json', 'process.log'))
        proofs.extend(evidence(p) for p in (folder / 'server').glob('packaged-*.json'))
    units = ROOT / 'local/checks/indexed-waveform-units-01/TEST-org.cha0scollective.wwpg.extension.NativeApiReadingsTest.xml'
    unit = ET.parse(units).getroot()
    assert int(unit.get('tests')) == 3 and int(unit.get('failures')) == int(unit.get('errors')) == int(unit.get('skipped')) == 0
    proofs.append(evidence(units))
    guard_log = (ROOT / 'local/indexed-waveform-evidence-01.log').read_text(encoding='utf-8-sig', errors='replace')
    assert 'Ran 4 tests' in guard_log and '\nOK' in guard_log
    runs, signatures, comparison = [], set(), []
    for name in args.attempts:
        assert Path(name).name == name
        run = capture(name)
        protocol = run['protocol']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert protocol['scoped_restoration'] and protocol['cee_state_geometry']
        assert protocol['batch'] and protocol['property_batch'] and not protocol['verify_batch']
        assert protocol['tree_batch'] and protocol['tree_prioritize_unresolved']
        assert protocol['tree_budget_ms'] == 20 and protocol['tree_max_parts'] == 32 and protocol['chunk_task_budget_ms'] == 2
        assert protocol['cpu_affinity_mask'] == 'fff000' and protocol['server_affinity_mask'] is None
        assert not any(protocol.get(k, False) for k in ('detailed', 'cpu_jfr', 'substep_jfr', 'terminal_bookkeeping', 'terminal_key_lookup'))
        match = signature(protocol); match.pop('indexed_waveform')
        signatures.add(json.dumps(match, sort_keys=True))
        folder = ROOT / 'local/benchmarks' / name
        assert read(folder / 'runner-exit.json')['exit_code'] == 0
        assert validate_indexed_waveform((folder / 'process.log').read_text(errors='replace'),
                                        protocol['indexed_waveform']) == protocol['waveform_execution']
        with (folder / 'benchmark/placement-ticks.csv').open(newline='') as stream:
            rows = [r for r in csv.DictReader(stream) if r['phase'] == 'STABLE']
        assert len(rows) == run['summary']['samples']
        comparison.append(dict(name=name, enabled=protocol['indexed_waveform'], tps=run['summary']['tps'],
            mspt=run['summary']['mspt'], ticks_over_50ms_percent=100 * sum(int(r['wall_tick_ns']) > 50_000_000 for r in rows) / len(rows),
            mean_cpu_ms=statistics.mean(int(r['cpu_tick_ns']) / 1e6 for r in rows),
            mean_allocated_bytes=statistics.mean(int(r['allocated_bytes']) for r in rows),
            compilation_ms=int(rows[-1]['compile_ms']) - int(rows[0]['compile_ms']), gc_ms=run['summary']['gc_ms']))
        for item in read(folder / 'launch.json')['runner_sources']:
            assert sha(Path(item['path'])) == item['sha256']
        runs.append(run)
    assert len(signatures) == 1 and {p['enabled'] for p in comparison} == {False, True}
    runners = ROOT / 'local/checks/indexed-waveform-audit-01/runners'
    for path in sorted(runners.glob('*.py')):
        ast.parse(path.read_text())
        proofs.append(evidence(path))
    assert len(list(runners.glob('*.py'))) == 8
    proofs.append(evidence(runners / 'plain-wire.gradle'))
    data = dict(schema=1, full_suite=False, inputs=inputs, source_hashes_verified=len(inputs['sources']),
        changed_runtime_sources=changed, focused_executions=sum(c['result']['required_tests_passed'] for c in checks),
        unit_tests=3, evidence_guard_tests=4, checks=checks, comparison=comparison, runs=runs, evidence=proofs,
        note='Finite same-jar serial traversal comparison; all samples and ownership checks retained. No full acceptance or client result is implied.')
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2) + '\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
