"""Audit native timing identity, call counts and nested scopes."""
import hashlib
import json
from pathlib import Path
import zipfile

from preparation_sections import evidence, read, rows

MAGIC = 0x5757504750524F31
STAGES = ('total','residual_multiply','residual_norm','sparse_solve','factorization','triangular_solve',
          'permutation','finite','copy','delta','java_callback','jacobian','rhs')
ROOT_CHILDREN = ('residual_multiply','residual_norm','sparse_solve','finite','copy','delta','java_callback','jacobian','rhs')
MARKER = 'native/wwpg/safe/experimental-profile.json'
WINDOWS_DLL = 'native/wwpg/safe/windows/libpowergridNative7.dll'


def inspect_runtime(path, enabled):
    with zipfile.ZipFile(path) as jar:
        if MARKER not in jar.namelist():
            assert not enabled, 'Native stage observation requires an explicitly marked diagnostic runtime'
            return None
        marker = json.loads(jar.read(MARKER))
        assert marker['schema'] == 1 and marker['diagnostic'] is enabled
        assert marker['native_sha256'] == hashlib.sha256(jar.read(WINDOWS_DLL)).hexdigest()
        if enabled:
            assert marker['profile_magic'] == MAGIC and marker['profile_stages'] == list(STAGES)
        return marker


def validate_row(row):
    assert int(row['profile_magic']) == MAGIC
    frequency = int(row['profile_frequency'])
    assert frequency > 0 and int(row['profile_stages']) == len(STAGES) and int(row['profile_invalid']) == 0
    calls = {k:int(row['c_' + k + '_calls']) for k in STAGES}
    times = {k:int(row['c_' + k + '_qpc']) for k in STAGES}
    assert all(v >= 0 for v in list(calls.values()) + list(times.values()))
    assert calls['total'] == 1 and times['total'] > 0
    assert all(times[k] == 0 for k in STAGES if calls[k] == 0)
    assert calls['residual_multiply'] == calls['residual_norm'] == int(row['residual_calls'])
    assert calls['java_callback'] == int(row['iteration_calls']) + int(row['residual_calls'])
    assert calls['triangular_solve'] <= calls['sparse_solve']
    assert calls['factorization'] <= calls['sparse_solve']
    assert sum(times[k] for k in ROOT_CHILDREN) <= times['total']
    assert times['factorization'] + times['triangular_solve'] <= times['sparse_solve']
    assert times['permutation'] <= times['sparse_solve']
    assert times['total'] / frequency * 1e9 <= int(row['native_ns']) + 2000
    return calls, times


def validate(directory, enabled):
    directory = Path(directory).resolve()
    config = read(directory / 'invocation.json')['configuration']
    assert config.get('nativeStages','false') == str(enabled).lower()
    file = directory / 'benchmark/native-work.csv'
    observed = rows(file) if file.exists() else []
    if not enabled:
        assert not any('profile_magic' in r for r in observed)
        return dict(enabled=False, observation_absent=True)
    assert config['nativeWork'] == config['detailed'] == config['substeps'] == 'true'
    stable = [r for r in observed if r['phase'] == 'STABLE']
    assert stable
    for row in stable: validate_row(row)
    native = list((directory / '.pg-native/wwpg-safety-1').rglob('*.dll'))
    assert len(native) == 1
    runtime = read(directory / 'launch.json')['runtime']
    marker = inspect_runtime(runtime['path'], True)
    assert hashlib.sha256(native[0].read_bytes()).hexdigest() == marker['native_sha256']
    return dict(enabled=True, complete_steady_samples=len(stable), stages=list(STAGES),
        loaded_native=evidence(native[0]), evidence=[evidence(file)], marker=marker)
