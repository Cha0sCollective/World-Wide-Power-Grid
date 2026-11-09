"""Audit focused task-window checks and same-jar client comparisons."""
import argparse
import ast
import json
import statistics
import xml.etree.ElementTree as ET

from summarize_tree import ROOT, read, sha, evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client-report', action='append', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    frozen = ROOT / 'local/frozen/prototype-08a/inputs.json'
    inputs = read(frozen)
    for item in inputs['sources']:
        assert sha(ROOT / item['path']) == item['sha256'], item['path']
    for item in inputs['artifacts'].values():
        from pathlib import Path
        assert sha(Path(item['path'])) == item['sha256']
    proofs = [evidence(frozen)]
    unit_tests = 0
    for path in sorted((ROOT / 'local/checks/task-window-unit-01/test-results').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        assert all(int(suite.attrib[key]) == 0 for key in ('failures', 'errors', 'skipped'))
        unit_tests += int(suite.attrib['tests'])
        proofs.append(evidence(path))
    assert unit_tests == 19
    fixture_tests = 0
    for selection, expected in [('budget', 38), ('lifecycle', 11)]:
        for backend in ('native', 'java'):
            folder = ROOT / f'local/checks/task-window-{selection}-{backend}-01'
            result = read(folder / 'result.json')
            assert result['passed'] and result['required_tests_passed'] == expected
            assert result['artifacts'] == inputs['artifacts'] and result['chunk_task_budget_ms'] == 2
            fixture_tests += expected
            proofs.extend(evidence(folder / name) for name in ('invocation.json', 'result.json', 'process.log'))
    runs, groups, signatures = [], {0: [], 2: []}, set()
    for name in args.client_report:
        path = ROOT / name
        data = read(path)
        protocol, result = data['protocol'], data['result']
        assert protocol['validation_passed'] and data['validation']['passed'] and result['passed']
        assert not protocol['inspection_only'] and not protocol['full_suite']
        assert protocol['runtime'] == inputs['artifacts']['runtime']
        assert result['resolution'] == [1920, 1080] and result['render_distance_chunks'] == result['simulation_distance_chunks'] == 12
        for item in data['evidence']:
            assert sha(ROOT / item['path']) == item['sha256'], item['path']
        properties = dict(protocol['properties'])
        budget = int(properties.pop('wwpg.experimental.chunkTaskBudgetMillis'))
        assert budget in groups
        signatures.add(json.dumps(dict(properties=properties, helper=protocol['helper'], runtime=protocol['runtime']), sort_keys=True))
        groups[budget].append(result['restored_rendered_seconds'])
        runs.append(dict(attempt=data['attempt'], budget_ms=budget,
            restored_rendered_seconds=result['restored_rendered_seconds'], electrical_ready_seconds=result['electrical_ready_seconds'],
            first_world_frame_seconds=result['first_world_frame_seconds'], server_loading=data['server_loading'],
            worst_loading_tick_seconds=data['worst_loading_tick_seconds'], longest_loading_tick_gap_seconds=data['longest_loading_tick_gap_seconds'],
            world_frame_intervals=data['world_frame_intervals'], task_window=data['task_window'], report=evidence(path)))
    assert len(signatures) == 1 and all(groups.values()), 'Need enabled and disabled samples with matched artifacts/settings'
    summary = {str(k): dict(samples=len(v), mean_seconds=statistics.mean(v), minimum_seconds=min(v), maximum_seconds=max(v)) for k, v in groups.items()}
    reduction = 100 * (1 - statistics.mean(groups[2]) / statistics.mean(groups[0]))
    scripts = ('summarize_task_window.py', 'summarize_client_loading.py', 'client_loading_benchmark.py',
        'plain_wire_benchmark.py', 'restoration_benchmark.py', 'focused_checks.py')
    for name in scripts:
        path = ROOT / 'tools/experimental' / name
        ast.parse(path.read_text())
        proofs.append(evidence(path))
    data = dict(schema=1, full_suite=False, source_hashes_verified=len(inputs['sources']), unit_tests=unit_tests,
        focused_regression_tests=fixture_tests, active_dispatch_in_game_tests=False,
        active_dispatch_in_client_runs=True, runtime=inputs['artifacts']['runtime'], runs=runs,
        complete_loading_by_budget=summary, mean_reduction_percent=reduction, evidence=proofs,
        note='GameTestServer bypasses the ordinary wait; its fixture passes do not exercise extra dispatch. Real client runs validate active dispatch and full-yard correctness. A few stationary loads do not prove all gameplay or sustained client TPS.')
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2)+'\n')
    print(json.dumps(dict(summary=summary, mean_reduction_percent=reduction, source_hashes_verified=len(inputs['sources']))))


if __name__ == '__main__':
    main()
