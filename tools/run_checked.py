"""Bound CI processes and retain JVM thread dumps when a run stops progressing."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys


def launch_command(command, cwd=None):
    """Keep Windows batch paths and arguments inside one correctly quoted /c command."""
    command = list(command)
    if os.name != 'nt':
        return command
    cwd = Path.cwd() if cwd is None else Path(cwd)
    if Path(command[0]).name == 'gradlew':
        command[0] = str((cwd / 'gradlew.bat').resolve())
    if not command[0].lower().endswith('.bat'):
        return command
    path = Path(command[0])
    if path.is_absolute() and path.parent.resolve() == cwd.resolve():
        command[0] = path.name
    # cmd expands percent references even inside quotes; embedded quotes can
    # break an argument's quoting. Batch acceptance tasks need neither form.
    if any(any(character in value for character in ('"', '%', '\r', '\n')) for value in command):
        raise ValueError('Windows batch arguments cannot contain quotes, percent expansions or newlines')
    invocation = ' '.join('"' + value + '"' for value in command)
    prefix = subprocess.list2cmdline([os.environ.get('COMSPEC', 'cmd.exe'), '/d', '/v:off', '/s', '/c'])
    # Pass the complete command line to CreateProcess. Re-quoting it as one
    # list argument would turn the inner quotes into backslash-escaped quotes.
    return prefix + ' "' + invocation + '"'


def java_processes(jcmd):
    if not jcmd:
        return {}
    try:
        result = subprocess.run([jcmd, "-l"], capture_output=True, text=True, timeout=20)
        return {int(line.split(" ", 1)[0]): line for line in result.stdout.splitlines()
                if line.split(" ", 1)[0].isdigit() and "sun.tools.jcmd.JCmd" not in line}
    except (OSError, subprocess.TimeoutExpired):
        return {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=float, default=600)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command or args.timeout <= 0:
        parser.error("A command and a positive timeout are required")
    try:
        executable_command = launch_command(command)
    except ValueError as error:
        parser.error(str(error))
    jcmd = shutil.which("jcmd")
    if not jcmd and os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin" / ("jcmd.exe" if os.name == "nt" else "jcmd")
        if candidate.is_file():
            jcmd = str(candidate)
    previous = java_processes(jcmd)
    process = subprocess.Popen(executable_command, start_new_session=os.name != "nt")
    try:
        return process.wait(timeout=args.timeout)
    except subprocess.TimeoutExpired:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        evidence = Path("build/watchdog") / stamp
        evidence.mkdir(parents=True, exist_ok=True)
        current = java_processes(jcmd)
        observed = {pid: name for pid, name in current.items() if pid not in previous}
        (evidence / "timeout.json").write_text(json.dumps({
            "schema": 1, "command": command, "timeout_seconds": args.timeout,
            "process_pid": process.pid, "new_java_processes": observed,
            "meaning": "Timed out; this run is failed evidence, never a passing retry."
        }, indent=2) + "\n", encoding="utf-8")
        for pid in observed:
            try:
                dump = subprocess.run([jcmd, str(pid), "Thread.print", "-l"],
                                      capture_output=True, text=True, timeout=20)
                output = dump.stdout + dump.stderr
            except (OSError, subprocess.TimeoutExpired) as error:
                output = f"Thread dump failed: {error}\n"
            (evidence / f"jvm-{pid}-threads.txt").write_text(output, encoding="utf-8")
        print(f"CI process exceeded {args.timeout}s; failed-run diagnostics: {evidence}", file=sys.stderr, flush=True)
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False)
        else:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
        process.wait()
        return 124


if __name__ == "__main__":
    sys.exit(main())
