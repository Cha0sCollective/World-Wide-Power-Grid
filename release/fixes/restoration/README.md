# Restart and chunk restoration verification

This source correction is based on beta.3 (`7fa7844`). Published beta.3 jars, worlds, checksums, and acceptance records are preserved. The development jar still carries beta.3 metadata; its hash identifies this unreleased candidate.

The investigation found three distinct restoration behaviors:

- Delaying real Minecraft entity-load results reproduces the old chunk test's 0 V reading while entities are still pending. Blocks being available does not mean saved wires are ready.
- PG's missing-wire timer can expire a queued part before entity loading completes. WWPG keeps the check queued and resets its age until readiness, then preserves PG's ten-tick cleanup grace period. A wire whose entity remains absent must still be removed.
- A newly restored PG board can have zero terminal readings while its capacitor history retains charge. PG's native capacitor persistence callback copies that reading into its saved property. WWPG copies committed capacitor history instead, retaining PG's existing electrical model and substeps. The committed history includes PG's normal leakage.

The 123-check packaged suite requires two actual chunk unload/reload cycles, retained wire identities during a 60-tick entity-load delay, and restored power within five ticks of entity readiness. Restart checks inspect the deserialized capacitor property and history before the first solve, preventing recharging from masking lost state. They then check native connections, live readings, panel metering, and discharge/recharge behavior. The total test deadlines remain bounded.

Two added fixtures check a 7.5 V cold board save and its second deserialization, and PG's missing-wire reconciliation before and after entity readiness. The latter deliberately removes a native wire entity while retaining its part, queues the normal reconciliation API during delayed loading, and requires eventual cleanup. The ordinary whole-chunk fixture alone did not reproduce premature deletion; this focused fixture exercises that boundary independently.

The GameTest IO gate delays consumption of real disk-load results, retains their data, lets other chunks proceed, and restores the original inbox after the delay. It is installed only by the fixtures, never in ordinary gameplay.

[verification.json](verification.json) records source and candidate hashes, Windows native/Java SETUP and VERIFY checks, negative controls, and diagnostic logs. GitHub's existing four-job matrix also runs these fixtures, upstream references, and both example worlds' creation/restart on Windows and Linux with both solvers. CI outcomes are recorded separately when complete.

Reproduce with Java 21 and separate processes for creation and restart:

```powershell
.\gradlew.bat runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=SETUP -PtestDirectory=run/restoration-native --no-daemon
.\gradlew.bat runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=VERIFY -PtestDirectory=run/restoration-native --no-daemon
.\gradlew.bat runPackagedGameTestServer -PtestBackend=JAVA -PrestartPhase=SETUP -PtestDirectory=run/restoration-java --no-daemon
.\gradlew.bat runPackagedGameTestServer -PtestBackend=JAVA -PrestartPhase=VERIFY -PtestDirectory=run/restoration-java --no-daemon
```

The old intermittent CI failures did not record entity readiness and capacitor history at the failing instant. The controlled delayed restart retained charge; the separate cold-save fixture proves an overwrite path, not that every historical zero reading lost charge. These corrections and readiness checks address reproducible defects without retrospectively assigning a conclusive cause to those old logs. The design-table saved-design error remains a separate issue in [current status](../../../docs/STATUS.md).
