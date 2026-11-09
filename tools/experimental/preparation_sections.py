"""Validate sparse whole-section timing without treating it as a clean speedup."""
import csv
import hashlib
import json
from pathlib import Path

SECTIONS = ('guards_ns', 'anchors_ns', 'descriptors_ns', 'validate_ns', 'rebind_ns', 'apply_ns', 'changing_ns')


def read(path):
    return json.loads(Path(path).read_text())


def rows(path):
    with Path(path).open(newline='', encoding='utf-8-sig') as stream:
        return list(csv.DictReader(stream))


def evidence(path):
    path = Path(path).resolve()
    return dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def validate_rows(observed, ticks):
    expected = {int(row['tick']) for row in ticks if int(row['tick']) % 20 == 7 or int(row['tick']) % 200 == 1}
    assert observed and {int(row['tick']) for row in observed} == expected, 'Incomplete sampled tick population'
    keys = [(row['tick'], row['owner']) for row in observed]
    assert len(keys) == len(set(keys)), 'Duplicate preparation owner/tick'
    for row in observed:
        assert row['forced_edit'] == str(int(row['tick']) % 200 == 1).lower()
        assert int(row['updates']) in (0, 1) and row['accepted'] == row['updates']
        assert int(row['stage_mask']) == (127 if int(row['updates']) else 0)
        values = [int(row[key]) for key in SECTIONS]
        assert all(value >= 0 for value in values)
        assert sum(values) == int(row['stable_ns']) <= int(row['prepare_ns'])
    return len(expected)


def validate(directory, enabled):
    directory = Path(directory).resolve()
    files = [directory / 'benchmark' / name for name in ('preparation-sections.csv', 'preparation-sections-end.json',
             'preparation-install-prepare.json', 'preparation-install-stable.json')]
    configuration = read(directory / 'invocation.json')['configuration']
    assert configuration.get('preparationSections', 'false') == str(enabled).lower()
    if not enabled:
        assert not any(path.exists() for path in files), 'Disabled observer produced evidence'
        return dict(enabled=False, observation_absent=True)
    assert all(configuration[key] == 'false' for key in ('detailed', 'substeps', 'substepJfr', 'cpuJfr', 'rhsCounters'))
    finish = read(files[1])
    assert finish['preparations'] == finish['prepare_returns'] > 0
    assert finish['stable_starts'] == finish['stable_returns'] > 0 and finish['invalid'] == 0
    for path, method in zip(files[2:], ('prepare', 'update')):
        proof = read(path)
        assert proof['method'] == method and proof['originalInstructionsPreserved']
        assert proof['sites'] == (3 if method == 'prepare' else 19)
        assert proof['beforeSha256'] != proof['afterSha256']
    all_rows = rows(files[0])
    assert len(all_rows) == finish['rows']
    stable = [row for row in all_rows if row['phase'] == 'STABLE']
    ticks = [row for row in rows(directory / 'benchmark/ticks.csv') if row['phase'] == 'STABLE']
    assert all(row['timing_sample'] == '0' for row in ticks)
    selected = validate_rows(stable, ticks)
    assert selected >= 100
    owners = {row['owner'] for row in stable}
    assert all({row['owner'] for row in stable if int(row['tick']) == tick} == owners for tick in {int(r['tick']) for r in stable})
    assert any(int(row['updates']) == 1 for row in stable)
    return dict(enabled=True, stable_sampled_ticks=selected, sampled_world_preparations=len(stable),
                owners=len(owners), completed=finish, evidence=[evidence(path) for path in files])
