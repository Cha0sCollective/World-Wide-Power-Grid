"""Validate complete sampled JNI observations without inferring unseen solves."""
from collections import defaultdict
from pathlib import Path

from preparation_sections import evidence, read, rows


def validate_rows(observed, ticks, substeps):
    expected = {r['tick']: r for r in ticks if r['timing_sample'] == '1'}
    grouped = defaultdict(list)
    paired = {(r['tick'], r['step']): r for r in substeps if r['tick'] in expected}
    for row in observed: grouped[row['tick']].append(row)
    assert grouped and set(grouped) == set(expected), 'Incomplete native tick population'
    assert len(paired) == len(expected) * 16, 'Incomplete paired substeps'
    for tick, records in grouped.items():
        assert [int(r['step']) for r in records] == list(range(1, 17)), 'Missing or duplicate native invocation'
        for row in records:
            original = paired[(tick, row['step'])]
            assert row['tick_ns'] == expected[tick]['tick_ns'] == original['tick_ns']
            assert row['returned'] == 'true' and int(row['rows']) == 14925
            assert int(row['max_iterations']) > 0 and int(row['jacobian_commands']) >= 0
            indices = [int(v) for v in row['hook_indices'].split('|')]
            commands = [int(v) for v in row['hook_commands'].split('|')]
            assert len(indices) == len(commands) == int(row['iteration_calls'])
            assert indices[0] == 0 and indices == sorted(indices)
            assert all(0 <= i < int(row['max_iterations']) for i in indices)
            assert all(n >= 0 for n in commands) and int(row['residual_calls']) >= len(indices)
            assert original['native_solve_calls'] == '1'
            assert 0 < int(row['native_ns']) <= int(original['native_solve_ns'])
    return len(expected)


def validate(directory, enabled):
    directory = Path(directory).resolve()
    files = [directory / 'benchmark' / name for name in ('native-work.csv', 'native-work-end.json')]
    config = read(directory / 'invocation.json')['configuration']
    assert config.get('nativeWork', 'false') == str(enabled).lower()
    if not enabled:
        assert not any(p.exists() for p in files), 'Disabled native observer produced evidence'
        return dict(enabled=False, observation_absent=True)
    assert config['detailed'] == config['substeps'] == 'true'
    assert all(config.get(k, 'false') == 'false' for k in ('substepJfr', 'cpuJfr', 'preparationSections'))
    end = read(files[1])
    observed = rows(files[0])
    assert end['entries'] == end['exits'] == end['rows'] == len(observed) > 0
    assert end['invalid'] == 0
    steady = [r for r in observed if r['phase'] == 'STABLE']
    ticks = [r for r in rows(directory / 'benchmark/ticks.csv') if r['phase'] == 'STABLE']
    selected = validate_rows(steady, ticks, rows(directory / 'benchmark/substeps.csv'))
    assert selected >= 100
    return dict(enabled=True, sampled_steady_ticks=selected, sampled_steady_native_calls=len(steady),
        completed=end, evidence=[evidence(p) for p in files])
