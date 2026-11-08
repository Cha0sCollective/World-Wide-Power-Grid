"""Read-only Windows CPU competition samples; records names and counters, never command lines."""
import ctypes
from ctypes import wintypes
import json
import threading
import time


class HostWatch:
    def __init__(self, path):
        self.path = path
        self.stop = threading.Event()
        self.thread = threading.Thread(target=self.run, name='experimental-host-sampler', daemon=True)

    @staticmethod
    def snapshot():
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
        kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        kernel.K32EnumProcesses.argtypes = [ctypes.POINTER(wintypes.DWORD), wintypes.DWORD, ctypes.POINTER(wintypes.DWORD)]
        ids = (wintypes.DWORD * 8192)()
        length = wintypes.DWORD()
        if not kernel.K32EnumProcesses(ids, ctypes.sizeof(ids), ctypes.byref(length)):
            raise ctypes.WinError(ctypes.get_last_error())
        result = {}
        for pid in ids[:length.value // ctypes.sizeof(wintypes.DWORD)]:
            handle = kernel.OpenProcess(0x1000, False, pid)
            if not handle:
                continue
            try:
                values = [wintypes.FILETIME() for _ in range(4)]
                size = wintypes.DWORD(32768)
                name = ctypes.create_unicode_buffer(size.value)
                if kernel.GetProcessTimes(handle, *[ctypes.byref(v) for v in values]) and kernel.QueryFullProcessImageNameW(handle, 0, name, ctypes.byref(size)):
                    numeric = [(v.dwHighDateTime << 32) | v.dwLowDateTime for v in values]
                    result[str(pid)] = dict(name=name.value.rsplit('\\', 1)[-1], created=numeric[0], cpu_seconds=sum(numeric[2:]) / 1e7)
            finally:
                kernel.CloseHandle(handle)
        return dict(wall_unix=time.time(), processes=result)

    def run(self):
        with self.path.open('x', encoding='utf-8') as stream:
            while True:
                try:
                    value = self.snapshot()
                except Exception as error:
                    value = dict(wall_unix=time.time(), error=str(error))
                stream.write(json.dumps(value) + '\n')
                stream.flush()
                if self.stop.wait(5):
                    break

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *unused):
        self.stop.set()
        self.thread.join(timeout=10)
        if self.thread.is_alive():
            raise RuntimeError('Host sampler did not stop')
