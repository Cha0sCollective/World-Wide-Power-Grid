"""Observe logical CPU load and pin only a benchmark process owned by this runner."""
import argparse
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def kernel():
    if os.name != 'nt':
        raise OSError('This experimental placement protocol requires Windows')
    api = ctypes.WinDLL('kernel32', use_last_error=True)
    api.GetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(ctypes.c_size_t)]
    api.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
    api.GetProcessId.argtypes = [wintypes.HANDLE]
    api.GetProcessId.restype = wintypes.DWORD
    api.GetActiveProcessorCount.argtypes = [wintypes.WORD]
    api.GetActiveProcessorCount.restype = wintypes.DWORD
    api.GetActiveProcessorGroupCount.argtypes = []
    api.GetActiveProcessorGroupCount.restype = wintypes.WORD
    return api


def owned_handle(process):
    # The Popen handle identifies the process we created, even if its PID is reused.
    handle = wintypes.HANDLE(int(process._handle))
    if kernel().GetProcessId(handle) != process.pid:
        raise OSError('Owned process handle does not match launch')
    return handle


def affinity(process):
    actual, system = ctypes.c_size_t(), ctypes.c_size_t()
    if not kernel().GetProcessAffinityMask(owned_handle(process), ctypes.byref(actual), ctypes.byref(system)):
        raise ctypes.WinError(ctypes.get_last_error())
    return actual.value, system.value


def pin_owned(process, mask):
    if process.poll() is not None:
        raise OSError('Owned benchmark process has already exited')
    before, available = affinity(process)
    if mask <= 0 or mask & ~available:
        raise ValueError('Requested CPU mask is outside the system mask')
    if not kernel().SetProcessAffinityMask(owned_handle(process), mask):
        raise ctypes.WinError(ctypes.get_last_error())
    after, _ = affinity(process)
    if after != mask:
        raise OSError('Process affinity readback mismatch')
    return dict(pid=process.pid, original_mask=f'{before:x}', requested_mask=f'{mask:x}', readback_mask=f'{after:x}',
                logical_cpus=[n for n in range(64) if mask & 1 << n], wall_unix=time.time(),
                scope='Owned child process immediately after launch; other processes unchanged')


def processor_times():
    api = kernel()
    if api.GetActiveProcessorGroupCount() != 1:
        raise OSError('Per-core observation currently requires one Windows processor group')
    count = api.GetActiveProcessorCount(0)
    class Times(ctypes.Structure):
        _fields_ = [(n, ctypes.c_longlong) for n in ('idle','kernel','user','dpc','interrupt')] + [('interrupt_count', wintypes.ULONG)]
    values = (Times * count)()
    returned = wintypes.ULONG()
    query = ctypes.WinDLL('ntdll').NtQuerySystemInformation
    query.argtypes = [wintypes.ULONG, ctypes.c_void_p, wintypes.ULONG, ctypes.POINTER(wintypes.ULONG)]
    query.restype = ctypes.c_long
    status = query(8, values, ctypes.sizeof(values), ctypes.byref(returned))
    if status != 0 or returned.value != ctypes.sizeof(values):
        raise OSError(f'Cannot read processor performance counters: {status}, bytes={returned.value}')
    return [[v.idle, v.kernel, v.user] for v in values]


def percentages(first, second):
    if len(first) != len(second):
        raise ValueError('Processor inventory changed')
    result = []
    for a,b in zip(first,second):
        idle, total = b[0]-a[0], b[1]+b[2]-a[1]-a[2]
        if total <= 0 or idle < 0 or idle > total:
            raise ValueError('Invalid processor counter delta')
        result.append(100*(1-idle/total))
    return result


def observe_group(mask, seconds=6):
    first = processor_times()
    time.sleep(seconds)
    last = processor_times()
    busy = percentages(first,last)
    selected = [i for i in range(len(busy)) if mask & 1 << i]
    if not selected or mask >> len(busy):
        raise ValueError('CPU selection is outside the observed processor inventory')
    mean = sum(busy[i] for i in selected)/len(selected)
    peak = max(busy[i] for i in selected)
    return dict(wall_unix=time.time(), observed_seconds=seconds, selected_mask=f'{mask:x}', logical_cpus=selected,
                logical_cpu_busy_percent=busy, selected_mean_percent=mean, selected_maximum_percent=peak,
                quiet=mean<=5 and peak<=25, mean_limit_percent=5, maximum_limit_percent=25,
                note='Preflight only; process affinity does not reserve cores against other applications')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--observe-seconds', type=int, default=6)
    parser.add_argument('--output', required=True)
    parser.add_argument('--self-check-mask', help='Hex mask for a disposable Python child affinity check')
    args = parser.parse_args()
    if not 1 <= args.observe_seconds <= 30:
        raise ValueError('Observation must be 1..30 seconds')
    target = Path(args.output).resolve()
    root = Path(__file__).resolve().parents[2]
    if not target.is_relative_to(root / 'local') or target.exists():
        raise ValueError('Choose a new evidence path under local/')
    before = processor_times()
    time.sleep(args.observe_seconds)
    after = processor_times()
    result = dict(observed_seconds=args.observe_seconds, logical_cpu_busy_percent=percentages(before,after),
                  before=before, after=after, wall_unix=time.time())
    if args.self_check_mask:
        mask = int(args.self_check_mask,16)
        child = subprocess.Popen([sys.executable,'-c','import time; time.sleep(1)'], creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            original = affinity(child)[0]
            for invalid in (0,1<<64):
                try:
                    pin_owned(child,invalid)
                    raise AssertionError('Invalid CPU mask was accepted')
                except ValueError:
                    assert affinity(child)[0] == original
            result['owned_child_check'] = pin_owned(child, mask)
            assert affinity(child)[0] == mask
            result['owned_child_check']['invalid_masks_rejected_without_change'] = True
        finally:
            child.wait(timeout=5)
        result['owned_child_check']['exit_code'] = child.returncode
    target.parent.mkdir(parents=True,exist_ok=True)
    with target.open('x',encoding='utf-8') as stream:
        stream.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
