"""JFR sampling for one owned benchmark, independently of phase timers."""
import csv
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_new(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(value, indent=2) + '\n')


def validate_options(args):
    if not getattr(args, 'cpu_jfr', False):
        return
    if (args.detailed or getattr(args, 'substep_trace', False)
            or getattr(args, 'substep_jfr', False) or getattr(args, 'loading_trace', False)):
        raise ValueError('CPU-only JFR requires detailed, substep and loading diagnostics disabled')
    if args.mode != 'control' or args.warmup < 60:
        raise ValueError('CPU-only JFR requires a control run with at least 60 seconds of warmup')


class CpuRecording:
    def __init__(self, directory, java, process):
        self.directory = Path(directory).resolve()
        marker = json.loads((self.directory / 'WWPG_BENCHMARK_OWNED.json').read_text())
        if Path(marker['root']).resolve() != self.directory:
            raise ValueError('Recording must target this runner-owned benchmark')
        self.process = process
        self.java = Path(java)
        self.jcmd = self.java.parent / ('jcmd.exe' if os.name == 'nt' else 'jcmd')
        self.configuration = self.java.parent.parent / 'lib/jfr/profile.jfc'
        if not self.jcmd.is_file() or not self.configuration.is_file():
            raise ValueError('The selected JDK must provide jcmd and profile.jfc')
        self.destination = self.directory / 'benchmark/cpu-profile.jfr'
        self.metadata = self.directory / 'cpu-profile-start.json'
        if self.metadata.exists() or self.destination.exists():
            raise ValueError('Never overwrite an earlier recording attempt')
        self.started = False

    def observe(self, state):
        if self.started:
            return
        if state.get('phase') == 'STABLE':
            raise ValueError('CPU recording missed warmup; do not attach during the measured sample')
        if state.get('phase') != 'WARMUP':
            return
        if self.process.poll() is not None:
            raise ValueError('Owned process ended before recording started')
        command = [str(self.jcmd), str(self.process.pid), 'JFR.start',
                   'name=wwpg-cpu-profile', 'settings=profile',
                   'filename=' + str(self.destination), 'dumponexit=true']
        proof = dict(schema=1, command=command, pid=self.process.pid,
                     observed_state=state, requested_unix_ns=time.time_ns(),
                     configuration=dict(path=str(self.configuration), sha256=sha(self.configuration)),
                     jcmd=dict(path=str(self.jcmd), sha256=sha(self.jcmd)))
        before = time.monotonic_ns()
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            proof.update(exit_code=result.returncode, stdout=result.stdout, stderr=result.stderr)
        except subprocess.TimeoutExpired as error:
            proof.update(error='JFR.start exceeded 30 seconds')
            raise OSError(proof['error']) from error
        finally:
            proof.update(returned_unix_ns=time.time_ns(), elapsed_ns=time.monotonic_ns() - before)
            write_new(self.metadata, proof)
        if result.returncode != 0 or 'Started recording' not in result.stdout:
            raise OSError('JFR.start did not confirm a recording; retain cpu-profile-start.json')
        self.started = True


def validate_recording(directory):
    directory = Path(directory)
    proof = json.loads((directory / 'cpu-profile-start.json').read_text())
    invocation = json.loads((directory / 'invocation.json').read_text())['configuration']
    exit_record = json.loads((directory / 'runner-exit.json').read_text())
    assert proof['pid'] == exit_record['pid'] and exit_record['exit_code'] == 0
    assert proof['exit_code'] == 0 and 'Started recording' in proof['stdout']
    assert proof['observed_state']['phase'] == 'WARMUP'
    assert all(str(invocation[key]).lower() == 'false' for key in ('detailed', 'substeps', 'substepJfr', 'rhsCounters'))
    assert invocation['cpuJfr'] == 'true'
    for item in (proof['configuration'], proof['jcmd']):
        assert sha(item['path']) == item['sha256']
    with (directory / 'benchmark/placement-ticks.csv').open(newline='') as stream:
        stable = [row for row in csv.DictReader(stream) if row['phase'] == 'STABLE']
    assert stable
    # FixedPlacement timestamps are after tickServer. Allow at least 30 seconds
    # for recording startup to settle before the first measured tick starts.
    first_start_ns = int(stable[0]['wall_unix_ms']) * 1_000_000 - int(stable[0]['wall_tick_ns'])
    lead_seconds = (first_start_ns - proof['returned_unix_ns']) / 1e9
    assert lead_seconds >= 30, 'JFR startup was too close to the measured sample'
    assert not (directory / 'benchmark/substeps.csv').exists()
    assert not (directory / 'benchmark/substeps.jfr').exists()
    recording = directory / 'benchmark/cpu-profile.jfr'
    assert recording.is_file() and recording.stat().st_size > 0
    return dict(path=str(recording), sha256=sha(recording), bytes=recording.stat().st_size,
                start_evidence=dict(path=str(directory / 'cpu-profile-start.json'), sha256=sha(directory / 'cpu-profile-start.json')),
                startup_lead_seconds=lead_seconds, detailed_timers=False,
                scope='Raw recording includes warmup and shutdown; steady analysis requires explicit timestamp filtering')


def instant_ns(value):
    match = re.fullmatch(r'(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?Z', value)
    if match is None:
        raise ValueError('Expected a UTC JFR timestamp: ' + value)
    seconds = int(datetime.fromisoformat(match[1]).replace(tzinfo=timezone.utc).timestamp())
    return seconds * 1_000_000_000 + int((match[2] or '').ljust(9, '0'))


def steady_window(ticks, placement):
    ticks = [row for row in ticks if row['phase'] == 'STABLE']
    placement = [row for row in placement if row['phase'] == 'STABLE']
    if len(ticks) < 2 or [r['tick'] for r in ticks] != [r['tick'] for r in placement]:
        raise ValueError('Steady tick and placement observations must match')
    if any(row['timing_sample'] != '0' for row in ticks):
        raise ValueError('Detailed timing was enabled in the CPU-only sample')
    wall = [int(row['wall_unix_ms']) * 1_000_000 for row in placement]
    monotonic = [int(row['wall_ns']) for row in ticks]
    if any(b <= a for a, b in zip(wall, wall[1:])) or any(b <= a for a, b in zip(monotonic, monotonic[1:])):
        raise ValueError('Tick clocks must increase')
    offsets = [a - b for a, b in zip(wall, monotonic)]
    spread = max(offsets) - min(offsets)
    if spread > 20_000_000:
        raise ValueError('Wall-clock alignment changed by more than 20 ms')
    # Wall timestamps have millisecond precision and occur just after tickServer.
    # Use an inner interval: omit the first tick and both 2 ms boundary margins.
    start, end = wall[0] + 2_000_000, wall[-1] - 2_000_000
    if end <= start:
        raise ValueError('Empty steady interval')
    return dict(start_unix_ns=start, end_unix_ns=end, seconds=(end-start)/1e9,
                first_tick=int(ticks[0]['tick']), last_tick=int(ticks[-1]['tick']),
                full_sample_ticks=len(ticks), wall_monotonic_offset_spread_ns=spread,
                boundary_margin_ms=2, first_tick_excluded=True,
                scope='Inner steady-phase wall interval, including between-tick benchmark observers; not an exclusive tickServer timer')


def filter_events(events, window):
    start, end = window['start_unix_ns'], window['end_unix_ns']
    if end <= start:
        raise ValueError('Empty event interval')
    kept, excluded = [], Counter()
    for event in events:
        when, duration = instant_ns(event['start']), event['duration_ns']
        if duration < 0:
            raise ValueError('Negative JFR event duration')
        if when >= start and when < end and when + duration <= end:
            kept.append(event)
        else:
            excluded[event['type']] += 1
    return kept, dict(excluded)
