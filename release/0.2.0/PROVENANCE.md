# WWPG 0.2.0 release provenance

This record identifies the exact implementation and test inputs accepted for
0.2.0. “Frozen” means those files were fixed before acceptance: later edits
cannot be substituted for the checked bytes.

## Compiled implementation

The runtime was compiled from source
`934fe30fd5a6dfe27f8ab13bee6edb40ca1078da`. Its SHA256 is
`708d3bac7fee11f34a770f0a145ee4226a95566870db572767aafa97bae78f10`.
Both the general and CEE APIs are version **1.0.0**.

The [final verification record](verification.json) identifies the twelve
SDK/runtime/example downloads, both API interface snapshots, and the exact
extracted-SDK builds. Both independently built examples matched their frozen
jar bytes. The record also identifies the actual CI checkout, run and attempt.
The internal labels **candidate33**, **source33** and **SDK33** refer to this
accepted artifact set and its builds; they are investigation labels, not mod
versions.

Later documentation and evidence commits are separate from the source compiled
into the runtime. The CI workflow checks the requested PR source, rather than
an implicit merge checkout. A later branch check does not change the frozen
release's implementation identity.

## Graphical and multiplayer checks

The Windows checks used a disposable helper outside the runtime release.
Each launch copied and recorded its own helper jar, Java sources, Gradle
initialization script and resources. Proofs retain those hashes, logs,
screenshots, the exact exported world and all five runtime/API/example hashes.

The accepted client records are four **BASE api35** sessions and four
**PINOUT api33** sessions, covering graphical and two-client checks on both
backends. They include visible native design-name entry and Save clicks.
These labels identify the retained sessions. The older **api30** records
belong to historical source29; they do not certify the final release.
Changing the helper requires complete new client checks even when the runtime
jar is unchanged.

## Earlier failure and its correction

Failed development runs and deliberately canceled partial runs remain available.
In **CI29**, seven jobs failed a timed Showroom lamp assertion; the remaining job
was deliberately canceled. **Diagnostic31** reproduced the assertion on both
solvers at game tick 192.

The assertion observed the preceding rejected CEE frame before the next gameplay
commit. **Diagnostic32** moved the ON observation to tick 193 and retained the
OFF observation at tick 205. Both backends passed the strict full-brightness and
voltage checks, followed by darkness and zero readings, with no independent CEE
solves. The native timer, pulse behavior and electrical models were unchanged.

These controls establish the cause of that observation failure and the correction
in source33. They do not replace the complete CI, cold-restart, exported-world
and client checks required for release. The final accepted manifest passed all
of those gates.

## Worlds, API baseline and publication

The two world families were accepted by their exact ZIP hashes. Copying a file
under a public download name preserves that identity; rebuilding or repacking it
requires fresh checks. The Workshop requires its separate example gameplay mods.
API, source and Javadoc jars are developer assets.

API 1.0.0 establishes the first public interface baseline. No compatibility
comparison with an earlier public WWPG API is claimed. The
[release-check guide](../../docs/API_RELEASE_CHECKS.md) explains future comparisons
and packaging gates. The staging script prepares files; it does not upload them,
perform Git operations or freeze the API.
