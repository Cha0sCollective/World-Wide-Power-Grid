"""Audit the named focused scope fixtures and copied-save performance observations."""
import argparse
import ast
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

from plain_wire_benchmark import validate_scoped_restoration
from summarize_current_profile import signature
from summarize_goal_runs import capture
from summarize_tree import ROOT, read, sha, evidence, attempt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--enabled-checks', nargs='+', required=True)
    parser.add_argument('--disabled-checks', nargs='+', required=True)
    parser.add_argument('--client-reports', nargs='+', required=True)
    parser.add_argument('--reload-attempt', required=True)
    parser.add_argument('--steady-attempts', nargs='+', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    manifest = ROOT / args.inputs
    inputs = read(manifest)
    for item in inputs['sources']:
        assert sha(ROOT / item['path']) == item['sha256'], item['path']
    for item in inputs['artifacts'].values():
        assert sha(Path(item['path'])) == item['sha256']
    proofs, checks = [evidence(manifest)], []
    for enabled, names in ((True, args.enabled_checks), (False, args.disabled_checks)):
        for name in names:
            folder = ROOT / 'local/checks' / name
            result = read(folder / 'result.json')
            assert result['passed'] and result['artifacts_unchanged'] and result['exit_code'] == 0
            assert result['artifacts'] == inputs['artifacts'] and result['scoped_restoration'] == enabled
            assert result['tree_max_parts'] == 1 and result['tree_budget_ms'] == 20
            if result['addon_artifacts']:
                assert result['addons_unchanged'] and result['addon_classpath_verified']
                for item in result['addon_artifacts'].values(): assert sha(Path(item['path'])) == item['sha256']
            observed = validate_scoped_restoration((folder / 'process.log').read_text(errors='replace'), enabled)
            assert observed == result['scope_execution']
            checks.append(dict(name=name, result=result))
            proofs.extend(evidence(folder / n) for n in ('invocation.json', 'process.log', 'result.json'))
    clients = []
    for name in args.client_reports:
        report = read(ROOT / name)
        assert report['validation']['passed'] and report['result']['passed']
        assert report['protocol']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert not report['protocol']['inspection_only'] and not report['protocol']['delivery_jfr']
        for item in report['evidence']: assert sha(ROOT / item['path']) == item['sha256'], item['path']
        clients.append(report)
        proofs.append(evidence(ROOT / name))
    client_settings = set()
    for report in clients:
        properties = dict(report['protocol']['properties']); properties.pop('wwpg.experimental.scopedRestoration')
        client_settings.add(json.dumps(dict(properties=properties, helper=report['protocol']['helper']['sha256']), sort_keys=True))
    assert len(client_settings) == 1
    reload = attempt(args.reload_attempt)
    assert reload['protocol']['validation']['passed'] and reload['protocol']['reload']
    assert reload['protocol']['scoped_restoration'] and reload['protocol']['scope_execution']['fallbackWorlds'] == 0
    assert reload['protocol']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
    steady = [capture(name) for name in args.steady_attempts]
    steady_settings = set()
    for run in steady:
        protocol = run['protocol']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert not protocol['detailed'] and not protocol['substep_jfr'] and not protocol.get('cpu_jfr', False)
        match = signature(protocol); match.pop('scoped_restoration')
        steady_settings.add(json.dumps(match, sort_keys=True))
    assert len(steady_settings) == 1 and {r['protocol']['scoped_restoration'] for r in steady} == {False, True}
    archived = []
    for suffix in 'abcdef':
        path = ROOT / f'local/scoped-restoration-source-12{suffix}.zip'
        frozen = read(ROOT / f'local/frozen/prototype-12{suffix}/inputs.json')
        import hashlib
        with zipfile.ZipFile(path) as archive:
            for item in frozen['sources']:
                assert hashlib.sha256(archive.read(item['path'])).hexdigest() == item['sha256']
        archived.append(dict(archive=evidence(path), manifest=evidence(ROOT / f'local/frozen/prototype-12{suffix}/inputs.json')))
    failed = []
    for name in ('scoped-native-01', 'scoped-native-02', 'scoped-native-03', 'scoped-native-05', 'scoped-native-06'):
        folder = ROOT / 'local/checks' / name
        result = read(folder / 'result.json'); assert not result['passed']
        failed.append(dict(name=name, result=result, evidence=[evidence(folder / 'result.json'), evidence(folder / 'process.log')]))
    units = []
    for path in sorted((ROOT / 'local/checks/scoped-units-01').glob('*.xml')):
        suite = ET.parse(path).getroot(); assert suite.get('failures') == suite.get('errors') == '0'
        units.append(dict(name=suite.get('name'), tests=int(suite.get('tests')), evidence=evidence(path)))
    assert sum(x['tests'] for x in units) == 17
    prior = {i['path']: i['sha256'] for i in read(ROOT / 'local/frozen/prototype-12a/inputs.json')['sources']}
    unit_sources = [i for i in inputs['sources'] if Path(i['path']).stem in
        ('RestorationComponents', 'RestorationQueue', 'RestorationWindow', 'RestorationComponentsTest', 'RestorationQueueTest', 'RestorationWindowTest')]
    assert len(unit_sources) == 6 and all(prior[i['path']] == i['sha256'] for i in unit_sources)
    for name in ('plain_wire_benchmark.py', 'restoration_benchmark.py', 'client_loading_benchmark.py',
                 'focused_checks.py', 'summarize_client_loading.py', 'summarize_current_profile.py',
                 'summarize_scoped_restoration.py', 'test_scoped_evidence.py'):
        path = ROOT / 'tools/experimental' / name; ast.parse(path.read_text()); proofs.append(evidence(path))
    proofs.append(evidence(ROOT / 'local/scoped-evidence-check-01.log'))
    data = dict(schema=1, full_suite=False, inputs=args.inputs, runtime=inputs['artifacts']['runtime'],
        source_hashes_verified=len(inputs['sources']), checks=checks,
        focused_checks_passed=sum(c['result']['required_tests_passed'] for c in checks),
        unit_tests=units, unit_sources_unchanged=unit_sources, evidence_guard_tests=5,
        clients=clients, reload=reload, steady=steady, failed_attempts=failed, archived_sources=archived, evidence=proofs,
        note='Finite focused observations; no full acceptance or statistical-significance claim. Scope discovery is outside the wire queue time budget.')
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2) + '\n')
    print(json.dumps(dict(focused_checks=data['focused_checks_passed'], sources=data['source_hashes_verified'],
                         client_runs=len(clients), steady_runs=len(steady))))


if __name__ == '__main__':
    main()
