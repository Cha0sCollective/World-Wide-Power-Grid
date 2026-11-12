"""Focused exporter checks against an existing, explicitly filtered recording."""
from collections import Counter
import json
from pathlib import Path
import subprocess

from cpu_profile import instant_ns, write_new
from summarize_tree import ROOT, read, evidence, sha


def population(events, detailed=False):
    return Counter((e['type'], e['start'], tuple(f['method'] for f in e['frames']) if detailed else tuple(e['frames']))
                   for e in events)


def main():
    folder = ROOT / 'local/preparation-review-01/site-checks'
    folder.mkdir(exist_ok=False)
    old = read(ROOT / 'local/benchmarks/goal-cpu-profile-01/benchmark/cpu-jfr-steady.json')
    recording = ROOT / 'local/benchmarks/goal-cpu-profile-01/benchmark/cpu-profile.jfr'
    source = ROOT / 'tools/experimental/JfrSites.java'
    java = Path(read(ROOT / 'local/frozen/prototype-12g/inputs.json')['java_home']) / 'bin/java.exe'
    events = sorted((e for e in old['events'] if e['type'] in ('jdk.ExecutionSample', 'jdk.NativeMethodSample')
                     and e['thread'] == 'Server thread'), key=lambda e: instant_ns(e['start']))
    checks = []

    def run(name, start, end, output, expected_success):
        command = [str(java), str(source), str(recording), str(output), str(start), str(end)]
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
        log = folder / (name + '.log')
        with log.open('x', encoding='utf-8') as stream:
            stream.write(result.stdout + result.stderr)
        assert (result.returncode == 0) == expected_success
        checks.append(dict(name=name, command=command, exit_code=result.returncode, log=evidence(log)))

    start, end = instant_ns(events[0]['start']), instant_ns(events[200]['start'])
    output = folder / 'boundary.json'
    run('inclusive-start-exclusive-end', start, end, output, True)
    observed = read(output)['events']
    expected = [e for e in events if start <= instant_ns(e['start']) < end]
    assert observed and population(observed, True) == population(expected)
    assert any(instant_ns(e['start']) == start for e in observed)
    assert not any(instant_ns(e['start']) == end for e in observed)
    before = sha(output)
    run('preserve-existing-output', start, end, output, False)
    assert sha(output) == before
    invalid = folder / 'invalid.json'
    run('reject-reversed-window', end, start, invalid, False)
    assert not invalid.exists()
    full = read(ROOT / 'local/preparation-review-01/frame-sites.json')['events']
    assert population(full, True) == population(events)
    write_new(folder / 'result.json', dict(passed=True, checks=checks, checked_boundary_events=len(expected),
        original_export_population_identical=True, original_events=len(events),
        source=evidence(source), initial_source=evidence(ROOT / 'local/preparation-review-01/JfrSites-initial.java'),
        recording=evidence(recording), boundary=evidence(output)))
    print(json.dumps(dict(passed=True, checks=len(checks), original_events=len(events), boundary_events=len(expected))))


if __name__ == '__main__':
    main()
