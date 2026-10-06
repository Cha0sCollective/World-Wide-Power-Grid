"""Collect explicit passing evidence before freezing the declared support matrix."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

root=Path(__file__).resolve().parents[1]
expected={
    "final-native-setup.log":114,"final-java-verify.log":114,
    "final-linux-native-setup.log":114,"final-linux-java-verify.log":114,
    "published-reference-upstream.log":2,"published-reference-compatibility.log":2,
    "published-example-setup.log":1,"published-example-verify.log":1,
    "final-multiplayer-server.log":1,
}
checks=[]
for name,count in expected.items():
    path=root/"build"/name
    output=path.read_text(errors="replace")
    match=re.search(r"All (\d+) required tests passed",output)
    if not match or int(match[1])!=count or "GAME TESTS COMPLETE" not in output or "BUILD SUCCESSFUL" not in output:
        raise SystemExit(f"Missing completed passing evidence: {name}")
    if "StackOverflowError" in output or "Encountered an unexpected exception" in output or "A fatal error has been detected" in output:
        raise SystemExit(f"Crash in accepted run: {name}")
    checks.append({"log":"build/"+name,"passed_fixtures":count,"sha256":hashlib.sha256(path.read_bytes()).hexdigest()})
for suffix in ["a","b"]:
    name=f"final-multiplayer-client-{suffix}.log";path=root/"build"/name
    output=path.read_text(errors="replace")
    if "WWPG_REAL_CLIENT_PASSED" not in output or "BUILD SUCCESSFUL" not in output or "WWPG real client failed" in output:
        raise SystemExit(f"Missing real-client validation: {name}")
    checks.append({"log":"build/"+name,"real_client_passed":True,"sha256":hashlib.sha256(path.read_bytes()).hexdigest()})
tests=failures=errors=0
for path in (root/"build/test-results/test").glob("TEST-*.xml"):
    suite=ET.parse(path).getroot();tests+=int(suite.attrib["tests"]);failures+=int(suite.attrib["failures"]);errors+=int(suite.attrib["errors"])
if tests!=4 or failures or errors:raise SystemExit("Electrical equation tests have not passed.")
report={"schema":1,"target":"0.1.0-beta.1","checked_at":datetime.now(timezone.utc).isoformat(),
        "all_release_gates_passed":True,"equation_tests":tests,"packaged_electrical_gameplay_fixtures":114,
        "checks":checks,"runtime":"Published Create 6.0.10-280, CEE 1.21.1-1.1.3 and PG 0.6.2; native v7 primary, Java regression.",
        "platforms":["Windows 11 x86-64 / Java 21","Ubuntu 24.04 x86-64 / Java 21"],
        "multiplayer":"Two real Minecraft clients over loopback TCP, native wiring/configuration packets and synchronized readings/wire rendering data.",
        "scope":"134 declared content behaviors, including all 11 base CEE panel attachments and all 28 base PG board components. Additional inventoried content remains unclaimed.",
        "remote_ci":"Workflow committed; remote jobs have not been executed in this local session.",
        "reproducibility":"Checks use isolated worlds and packaged artifacts. Final distribution hashes are recorded separately in release/distribution.json."}
(root/"release/verification.json").write_text(json.dumps(report,indent=2)+"\n",newline="\n")
print("Release gates passed; support matrix can be frozen.")
