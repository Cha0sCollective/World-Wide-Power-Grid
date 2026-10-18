# WWPG 0.2.2-beta.5

[Prerelease downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.2-beta.5)
· [Verification manifest](verification.json).

Beta.5 preserves contactor state while restoration makes coil readings
unavailable, then resumes normal switching after accepted solving. Selectable
CEE terminals use Power Grid's existing highlight boxes and native labels.
New API Workshop players spawn on the platform. API 1.0.0, existing saves,
the native binary, equations and all sixteen substeps remain unchanged.

The testing lanes now separate full headless acceptance from manually requested
Windows graphical acceptance. A shared catalog classifies every GameTest
namespace. Frozen artifacts and shared preparation replace repeated builds;
distinct cold restarts, exact archive reopens, unload/recovery and shutdown
checks remain required. Publishing validates evidence without launching tests.

The accepted runtime was built at
`9ac18f4c249deb934090b992d9d8f746cbe4a177` and differs from tested dev.98
only in its declared version. The release manifest names the later frozen
documentation/tooling source separately. Jar SHA256:
`ab572c47eb966468101cadb24ce924f1d258565e863e5010989c1ac4f3fe84cb`.

Full headless coverage comprises 342 catalog entries across Windows/Linux,
Native/Java, BASE/PINOUT and NeoForge 21.1.231/21.1.248. The
[original campaign](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/39082520711)
failed when an external fixture repeated fresh-only wiring during cold
verification. The corrected
[continuation](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/39084316464)
retains actual passing groups and reruns incomplete groups. It does not
relabel the original run. The metadata-only Workshop correction passed
[24 focused exact reopens](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/39087574238),
replacing only affected export/reopen records in the assembled coverage.

Twelve Windows client scenarios cover the combined Pinout yard, both Workshop
editions, two real TCP clients and the independently built SDK starter.
Affected Workshop/starter cases use the exact corrected archives and observe
the initial player position before helper teleportation. Both SDK clients ran
fresh after a launcher argument-order correction. Every integrated session
completed Save and Quit to Title; dedicated sessions disconnected both clients
and saved/stopped the server. Failed helper/launcher attempts remain preserved.
Workshop panel-storage startup diagnostics remain visible alongside later
accepted powered-state checks; this is not a warning-free certification.

The example-worlds bundle includes the unchanged original 14,650-node benchmark
save, labelled separately from the playable yard. No performance benchmark was
run for this release; 19.9 TPS with p95≤50 ms remains uncertified. Tiny
capacitor-pin targeting remains unresolved. Private oil-rig saves, benchmark
tools and detailed performance results remain local. Public release verification
belongs in the verification download.
