"""Combine retained Java jobs with a causally corrected Native continuation.

This is acceptance assembled from named real jobs, not a claim that the old
failed workflow succeeded or that retained processes ran again.
"""
import argparse
import json
from pathlib import Path
import re


def validate(ci, manifest, root, source, hashes, gate, version=None):
    gate.require(ci.get('schema') == 1 and ci.get('kind') == 'retained_ci_cohort'
                 and ci.get('source_sha') == source and ci.get('status') == 'completed'
                 and ci.get('conclusion') == 'success', 'CI cohort is incomplete')
    gate.require(set(ci.get('components', {})) == {'JAVA', 'NATIVE'}, 'CI cohort must cover both backends')
    gate.require(not manifest.get('retained_windows_acceptance'), 'CI cohort cannot omit Windows jobs')
    import collect_api_ci_evidence as collector
    retained_run, continued_runs, selected, origins = None, set(), [], []
    for backend in ('JAVA', 'NATIVE'):
        component = gate.read(gate.locked_file(ci['components'][backend], root))
        gate.require(component.get('kind') != 'retained_ci_cohort'
                     and re.fullmatch(r'[0-9a-f]{40}', component.get('source_sha','')), 'Invalid component source')
        inputs = component.get('inputs', {})
        run, jobs, artifacts = [gate.read(gate.locked_file(inputs[k], root)) for k in ('run','jobs','artifacts')]
        rows = collector.metadata(run, jobs, artifacts, backend=backend)
        gate.require(run['head_sha'] == component['source_sha'] and run['id'] == component['run_id']
                     and run['run_attempt'] == component['run_attempt'] and run['conclusion'] == component['conclusion'],
                     'CI component does not retain the actual workflow outcome')
        gate.require({j['id'] for j,_,_ in rows} == {j['id'] for j in component['jobs']}, 'CI component job identities differ')
        gate.validate_ci_acceptance(manifest, root, component, component['source_sha'], hashes, version, selection=backend)
        for job in component['jobs']:
            proof = gate.read(gate.locked_file(job['proof'], root))
            raw_job, artifact, _ = next(row for row in rows if row[0]['id'] == job['id'])
            gate.require(proof.get('github_job_id') == raw_job['id'] and proof.get('github_artifact_id') == artifact['id'],
                         'CI proof does not bind its actual uploaded artifact')
            if backend == 'NATIVE':
                import ci_reference_continuation
                gate.require(proof.get('retained_prefix'), 'Native cohort must preserve the actual successful prefix')
                origin = ci_reference_continuation.validate(proof['retained_prefix'], root)
                continued_runs.add(origin['run_id'])
                origins.append(origin)
            selected.append(job)
        if backend == 'JAVA': retained_run = component['run_id']
    gate.require(continued_runs == {retained_run}, 'Native continuation and retained Java jobs have different origins')
    gate.require(selected == ci.get('jobs'), 'Cohort summary changed or omitted actual component jobs')
    gate.require({(j['platform'],j['backend'],j['profile']) for j in selected} == gate.COMBINATIONS
                 and len(selected) == len(gate.COMBINATIONS), 'Cohort did not complete all eight combinations')
    gate.require(ci.get('scope') == 'retained_java_and_native_reference_continuation', 'Missing honest reuse scope')
    return origins


if __name__ == '__main__':
    import package_api_release as gate
    import collect_api_ci_evidence as collector
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java', type=Path, required=True); p.add_argument('--native', type=Path, required=True)
    p.add_argument('--source', required=True); p.add_argument('--output', type=Path, required=True)
    a=p.parse_args(); root=Path(__file__).resolve().parents[1]
    java, native = collector.read(a.java), collector.read(a.native)
    result=dict(schema=1, kind='retained_ci_cohort', source_sha=a.source, status='completed', conclusion='success',
                scope='retained_java_and_native_reference_continuation', url=native['url'],
                components={'JAVA':collector.descriptor(a.java,root),'NATIVE':collector.descriptor(a.native,root)},
                jobs=java['jobs']+native['jobs'])
    hashes=collector.read(root/java['jobs'][0]['proof']['path'])['artifact_hashes']
    validate(result, dict(stationary_yard_policy='combined-pinout-v1', required_regression_checks={'BASE':204,'PINOUT':208}),
             root, a.source, hashes, gate)
    collector.write(a.output,result)
