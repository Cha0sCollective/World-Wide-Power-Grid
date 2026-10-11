# Version compatibility

WWPG **0.2.1** allows newer NeoForge, Create, CEE and Power Grid
versions. The jar no longer requires an exact match to every tested dependency.
Minecraft remains **1.21.1**.

| Dependency | Minimum accepted by WWPG 0.2.1 | Test targets |
| --- | --- | --- |
| NeoForge | 21.1.231 | 21.1.231 and 21.1.248 |
| Create | 6.0.10 | 6.0.10-280 |
| CEE | 1.21.1-1.1.3 | 1.1.3 for Minecraft 1.21.1 |
| Power Grid | 0.6.2 | 0.6.2 for Minecraft 1.21.1 |

**A version being allowed to load does not mean it has passed WWPG's tests.**
Players can try newer upstream versions and report whether their circuits work.
For a server, check the release's dependency list and compatibility results
before changing versions. Upstream mods also have their own requirements.
Use the same mod versions on the server and every client.

The already published **WWPG 0.2.0 jar still requires NeoForge 21.1.231**.
That download is unchanged. For NeoForge 21.1.248, install
[WWPG 0.2.1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.1). The original
release records continue to describe the original tested versions.

## Checking NeoForge 21.1.248

The [compatibility workflow](../.github/workflows/neoforge-compatibility.yml)
builds the jar against 21.1.231, freezes it, and loads that same jar on 21.1.248.
It checks Windows and Linux, native and Java solvers, and base and Pinout
installations. The tests check the actual loaded version, factory and electronics
behavior, three cold restarts, real chunk unload/reload cycles, paused circuits,
and saving before wire entities finish loading. Logs, hashes and failed attempts
are retained as CI artifacts. The existing build workflow checks 21.1.231.

To test a frozen jar locally, use a fresh run directory:

```text
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -Pneo_version=21.1.248 -PtestArtifact=build/frozen/wwpg.jar -PtestProfile=BASE -PtestBackend=NATIVE -PtestNamespaces=wwpg_platform,wwpg,wwpg_stationary,wwpg_design,wwpg_distribution,wwpg_wires,wwpg_bundle,wwpg_coupled -PtestDirectory=run/neoforge-248 -x build -x jar --no-daemon
```

Repeat with `-PrestartPhase=VERIFY` three times to check cold restarts. Use
separate directories for Java and Pinout. Include `wwpg_pinout` in the namespace
list for the Pinout profile. Follow [Development](DEVELOPMENT.md) for SDK,
example-world and real-client checks.

`-Pneo_version=21.1.248` selects the loader used by development launches and the
independent example builds. `-PtestArtifact` selects the actual jar loaded by the
packaged server; its hash and runtime loader are recorded for each launch.
This avoids mistaking a freshly rebuilt jar for the frozen compatibility input.
