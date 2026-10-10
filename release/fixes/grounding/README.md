# Grounding correction verification

This record covers an unreleased fix based on `cdf9cc2`. The published beta.2 jar, worlds, and historical acceptance records remain unchanged.

Adding, removing, or changing a ground connection could leave PG's equations using its previous conductance. Changing a preferred voltage reference had the same problem. PG 0.6.2 rejects incremental conductance updates when a wire's second terminal is null. WWPG now marks the owning network for equation rebuilding when these connections change, retaining existing nodes and branches.

The three new packaged regressions check:

- Physical ground removal, addition, and conductance changes against analytical voltages.
- Preferred-reference changes, physical-ground takeover, and restoration of the preferred reference.
- Two actual unload/reload cycles of a CEE ground rod's chunk, with the source/load chunks retained. Both unload events and chunk absence are required, and restoration has a bounded deadline. Native wire resistance is included in the expected voltage calculation.

The tests assert retained source-node, network, and branch identities. They also check that the selected PG backend solved the circuit and CEE did not start a second solver. A negative-control run uses these same fixtures with the original `WorldBridge.java`: exactly these three tests fail while the other 118 pass.

[verification.json](verification.json) records the tested jar and source hashes, Windows native/Java SETUP and VERIFY results, and negative-control readings. The existing GitHub Actions matrix runs the full suite, upstream reference comparisons, and example-world creation/restart on Windows and Linux with both backends. Local log paths in the record refer to ignored build output; CI publishes its logs and jars as workflow artifacts.

Reproduce each backend with Java 21, using separate server processes and the same directory for SETUP and VERIFY:

```powershell
.\gradlew.bat runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=SETUP -PtestDirectory=run/grounding-native --no-daemon
.\gradlew.bat runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=VERIFY -PtestDirectory=run/grounding-native --no-daemon
.\gradlew.bat runPackagedGameTestServer -PtestBackend=JAVA -PrestartPhase=SETUP -PtestDirectory=run/grounding-java --no-daemon
.\gradlew.bat runPackagedGameTestServer -PtestBackend=JAVA -PrestartPhase=VERIFY -PtestDirectory=run/grounding-java --no-daemon
```

This correction does not establish the causes of the earlier intermittent chunk-reload and board-capacitor restart failures. They remain recorded in [current status](../../../docs/STATUS.md).
