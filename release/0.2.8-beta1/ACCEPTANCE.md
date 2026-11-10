# 0.2.8-beta1 prerelease acceptance

This is a version-only promotion of the tested 0.2.8-rc.1 control, requested on
November 10, 2026. The original tested source is
`74c278628a73743209473a0261e743baa72fe9c9`. The beta1 tag identifies the publication
source. API 1.0.0 and the selected runtime dependency jars are unchanged.

The [artifact comparison](artifact-comparison.json) checks all 356 gameplay-jar
entries and 513 acceptance-companion entries. Only declared mod versions and the
companion's exact WWPG dependency range change. Every class, fixture, native
library and other resource remains identical. The playable worlds retain all
native save bytes; only their release inventory labels and README version text
change. The verification download retains the original archives and comparisons.

## Carried-forward acceptance

- All 542 required headless jobs have validated passing coverage across
  Windows/Linux, Native/Java and BASE/PINOUT. NeoForge 21.1.265 is the complete
  primary environment; .231 and .248 retain the catalog's compatibility lanes.
- All 12 separate real Windows client scenarios passed, including two actual
  TCP clients, controls, tools, readings, screenshots and Save and Quit.
- The extracted SDK examples and starter retain their independent build and
  gameplay proofs. The two public API jars remain byte-identical to API 1.0.0.
- Both guarded native builds passed 40,740 exact reduction comparisons across
  16 floating-point modes, 37 sparse safety checks and 100 valid cleanup cycles.

The original [headless campaign](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/42800563160)
finished with one failed environment. Its other 23 environments passed. The
[unchanged-input continuation](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/42827048801)
retained those passing groups and completed the 12 missing environment jobs.
These are assembled observations, not a new full-suite execution on beta1.
Publication validates applicable evidence without launching Minecraft.

## Preserved motor finding

The original Linux Native/BASE/.248 third cold verification failed its CEE locked
motor assertion once. All four original regression phases passed in the complete
unchanged-artifact repeat, without diagnostic logging or changed assertions. Four
cold diagnostic phases also passed on each OS. The initial cause remains unknown;
no runtime fix is claimed. The original failed process remains failed and is not
counted as passing coverage. Its logs and diagnostic/recheck assessment remain
inside the verification download.

The earlier campaign interrupted by the older-loader CC:Tweaked dependency
mismatch remains preserved separately. Historical evidence is never relabelled.

## Runtime and scope

The retained loading and wire-processing improvements default on; no experimental
JVM flags are needed. Keep PG at 16 electrical substeps. The rejected worker and
cache prototypes are excluded. The copied 14,650-node benchmark is included as
a capacity workload; this release does not certify reliable 20 TPS.

Five downloads are published through `tools/release_downloads.py`: gameplay jar,
example worlds, developer SDK, verification bundle and checksums, in that order.
The verification bundle contains the promoted manifest, original process proofs,
exact byte comparisons and captured dependency versions/hashes.
