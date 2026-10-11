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
    if os.name == "nt" and Path(command[0]).name == "gradlew":
        command[0] = str(Path("gradlew.bat").resolve())
    if os.name == "nt" and command[0].lower().endswith(".bat"):
        command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", *command]
    jcmd = shutil.which("jcmd")
    if not jcmd and os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin" / ("jcmd.exe" if os.name == "nt" else "jcmd")
        if candidate.is_file():
            jcmd = str(candidate)
    previous = java_processes(jcmd)
    process = subprocess.Popen(command, start_new_session=os.name != "nt")
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
