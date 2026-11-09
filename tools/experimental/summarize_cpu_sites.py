"""Export exact recorded CPU frame sites and audit them against a filtered profile."""
import argparse
from collections import Counter, defaultdict
from pathlib import Path
import subprocess

from cpu_profile import write_new
from summarize_profile import category
from summarize_tree import ROOT, evidence, read


def population(events, detailed=False):
    return Counter((e['type'], e['start'], tuple(f['method'] for f in e['frames']) if detailed else tuple(e['frames']))
                   for e in events)


def ranked(counter, limit):
    return [dict(site=site, samples=count) for site, count in counter.most_common(limit)]


def location(frame):
    return (frame['method'], frame['line'], frame['bci'], frame['frame_type'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    output = ROOT / args.output
    assert not output.exists()
    report_path = ROOT / args.report
    report = read(report_path)
    attempt = report['diagnostic']['name']
    assert Path(attempt).name == attempt
    directory = ROOT / 'local/benchmarks' / attempt
    recording = directory / 'benchmark/cpu-profile.jfr'
    exporter = ROOT / 'tools/experimental/JfrSites.java'
    destination = directory / 'benchmark/cpu-jfr-sites.json'
    metadata = directory / 'benchmark/cpu-jfr-sites-export.json'
    window = report['window']
    expected = [e for e in read(directory / 'benchmark/cpu-jfr-steady.json')['events']
                if e['type'] in ('jdk.ExecutionSample', 'jdk.NativeMethodSample') and e['thread'] == 'Server thread']
    java = Path(read(ROOT / args.inputs)['java_home']) / 'bin/java.exe'
    command = [str(java), '-Xmx512m', str(exporter), str(recording), str(destination),
               str(window['start_unix_ns']), str(window['end_unix_ns'])]
    proof = dict(recording=evidence(recording), exporter=evidence(exporter),
                 window=window, command=command)
    if destination.exists():
        previous = read(metadata)
        assert all(previous[key] == value for key, value in proof.items())
        assert previous['export'] == evidence(destination)
    else:
        assert not metadata.exists()
        result = subprocess.run(command, capture_output=True, text=True, timeout=60)
        assert result.returncode == 0, result.stderr
        write_new(metadata, dict(**proof, export=evidence(destination)))
    exported = read(destination)
    assert exported['start_ns'] == window['start_unix_ns'] and exported['end_ns'] == window['end_unix_ns']
    events = exported['events']
    assert population(events, True) == population(expected), 'CPU event population differs from the filtered profile'
    sections = {}
    for event_type in ('jdk.ExecutionSample', 'jdk.NativeMethodSample'):
        selected = [event for event in events if event['type'] == event_type]
        scopes, leaves, application_sites, chains = Counter(), defaultdict(Counter), defaultdict(Counter), defaultdict(Counter)
        for event in selected:
            frames = event['frames']
            scope = category([frame['method'] for frame in frames])
            scopes[scope] += 1
            if frames:
                leaves[scope][location(frames[0])] += 1
                chains[scope][tuple(location(frame) for frame in frames[:5])] += 1
            application_sites[scope].update(set(location(frame) for frame in frames
                if frame['method'].startswith('org.cha0scollective.wwpg.')))
        assert dict(scopes) == report['jfr']['cpu'][event_type]['server_scopes']
        sections[event_type] = dict(samples=len(selected), truncated=sum(e['truncated'] for e in selected),
            scopes=dict(scopes), leaf_sites={scope: ranked(counts, 25) for scope, counts in leaves.items()},
            application_sites={scope: ranked(counts, 40) for scope, counts in application_sites.items()},
            top_stacks={scope: ranked(counts, 15) for scope, counts in chains.items()})
    write_new(output, dict(schema=1, attempt=attempt, population_identical=True,
        debug_non_safepoints=report['debug_non_safepoints'], window=window, cpu=sections,
        evidence=[evidence(path) for path in (report_path, ROOT / args.inputs, recording, exporter,
                  destination, metadata, directory / 'benchmark/cpu-jfr-steady.json', Path(__file__))],
        limitations=['Recorded line and bytecode locations are sampling attribution, not exact exclusive instruction costs.',
                    'Java and native-method sample counts have different periods; never combine their percentages.']))
    print({event_type: section['samples'] for event_type, section in sections.items()})


if __name__ == '__main__':
    main()
