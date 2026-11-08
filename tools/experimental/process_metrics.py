"""One-second measurements of the runner's own child process only."""
import ctypes
import os
import platform
import time
from pathlib import Path


def host():
    result=dict(platform=platform.platform(),machine=platform.machine(),cpu_count=os.cpu_count(),
                cpu=platform.processor(),scope='This local PC; not the production server')
    if os.name=='nt':
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,r'HARDWARE\DESCRIPTION\System\CentralProcessor\0') as key:
            result['cpu']=winreg.QueryValueEx(key,'ProcessorNameString')[0].strip()
        class Memory(ctypes.Structure):
            _fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong)]+[(name,ctypes.c_ulonglong) for name in ('total','available','total_page','available_page','total_virtual','available_virtual','extended')]
        memory=Memory();memory.length=ctypes.sizeof(memory)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory)):
            result.update(ram_bytes=memory.total,available_ram_bytes=memory.available)
    else:
        entries=dict(line.split(':',1) for line in Path('/proc/meminfo').read_text().splitlines())
        result.update(ram_bytes=int(entries['MemTotal'].split()[0])*1024,
                      available_ram_bytes=int(entries['MemAvailable'].split()[0])*1024)
    return result


class ChildMetrics:
    def __init__(self,pid):
        self.pid=pid;self.handle=None
        if os.name=='nt':
            from ctypes import wintypes
            self.kernel=ctypes.WinDLL('kernel32',use_last_error=True)
            self.kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
            self.kernel.OpenProcess.restype=wintypes.HANDLE
            self.kernel.CloseHandle.argtypes=[wintypes.HANDLE]
            self.kernel.GetProcessTimes.argtypes=[wintypes.HANDLE]+[ctypes.POINTER(wintypes.FILETIME)]*4
            class Counters(ctypes.Structure):
                _fields_=[('cb',wintypes.DWORD),('faults',wintypes.DWORD)]+[(name,ctypes.c_size_t) for name in ('peak_rss','rss','peak_paged','paged','peak_nonpaged','nonpaged','pagefile','peak_pagefile','private')]
            self.Counters=Counters
            self.kernel.K32GetProcessMemoryInfo.argtypes=[wintypes.HANDLE,ctypes.POINTER(Counters),wintypes.DWORD]
            self.handle=self.kernel.OpenProcess(0x1000|0x10,False,pid)
            if not self.handle:raise OSError(ctypes.get_last_error(),'Cannot sample owned server process')

    def sample(self):
        result=dict(wall_unix=time.time(),pid=self.pid)
        if os.name=='nt':
            from ctypes import wintypes
            values=[wintypes.FILETIME() for _ in range(4)]
            if not self.kernel.GetProcessTimes(self.handle,*[ctypes.byref(value) for value in values]):raise OSError(ctypes.get_last_error(),'GetProcessTimes failed')
            cpu=sum((value.dwHighDateTime<<32)|value.dwLowDateTime for value in values[2:])/1e7
            memory=self.Counters();memory.cb=ctypes.sizeof(memory)
            if not self.kernel.K32GetProcessMemoryInfo(self.handle,ctypes.byref(memory),memory.cb):raise OSError(ctypes.get_last_error(),'GetProcessMemoryInfo failed')
            result.update(cpu_seconds=cpu,rss_bytes=memory.rss,private_bytes=memory.private)
        else:
            stat=Path(f'/proc/{self.pid}/stat').read_text().rsplit(')',1)[1].split()
            memory=dict(line.split(':',1) for line in Path(f'/proc/{self.pid}/status').read_text().splitlines() if ':' in line)
            result.update(cpu_seconds=(int(stat[11])+int(stat[12]))/os.sysconf('SC_CLK_TCK'),
                          rss_bytes=int(memory['VmRSS'].split()[0])*1024)
        return result

    def close(self):
        if self.handle:self.kernel.CloseHandle(self.handle);self.handle=None
