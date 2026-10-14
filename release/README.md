# Release records

Maintainers: follow [Publishing a release](../docs/RELEASING.md) for the standard
five-download layout and short notes. Edit [presentation.json](presentation.json)
for the next release; version-specific records below preserve published history.

**[WWPG 0.2.1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.1)**
is the current stable release. To install it or choose a playable world, use the
[installation guide](../docs/INSTALL.md). This directory is for reviewing what
was shipped and which checks passed.

## Current prerelease: 0.2.2-beta.2

[WWPG 0.2.2-beta.2](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.2-beta.2)
combines the loading fixes, less repeated native and add-on bookkeeping, and the
expanded SDK. API 1.0.0 and saved-world formats stay compatible. Fresh release
acceptance covers both solvers and profiles; the 14,650-node 20 TPS target remains
open. [Verification](0.2.2-beta.2/verification.json) and
[downloads and checksums](0.2.2-beta.2/download-layout.json) identify the accepted
files and retained evidence.

## Previous alpha: 0.2.2-alpha.1

[WWPG 0.2.2-alpha.1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.2-alpha.1)
adds safer loading, less repeated electrical work and the expanded add-on SDK.
API 1.0.0 stays compatible. [Release record](0.2.2-alpha.1/README.md) explains
the carried-forward gameplay acceptance, focused SDK checks and remaining limits.
[Downloads and checksums](0.2.2-alpha.1/download-layout.json) identify the five files.

## Current stable release: 0.2.1

Adds tested NeoForge 21.1.248 support alongside 21.1.231. Newer NeoForge, Create,
CEE and Power Grid versions are permitted for player testing. Extension API
1.0.0 is unchanged. [Release record](0.2.1/README.md),
[dependencies](0.2.1/dependencies.json) and
[verification](0.2.1/verification.json) identify the accepted files and checks.

## Previous release: 0.2.0

The release retains stationary factory support and adds extension API 1.0.0,
two independently buildable example add-ons and the API Workshop worlds.

| Record | What it tells you |
| --- | --- |
| [Release summary](0.2.0/README.md) | Scope, API baseline and links to acceptance records. |
| [Downloads](0.2.0/DOWNLOADS.md) | Current five-file layout, sizes/checksums manifest and archived original files. |
| [Original distribution](0.2.0/distribution.json) | The original 29 accepted downloads, preserved inside the verification archive. |
| [Dependencies](0.2.0/dependencies.json) | Exact required and optional dependency versions. |
| [Verification](0.2.0/verification.json) | Accepted artifacts, test results and the evidence files supporting them. |
| [Completed stages](0.2.0/progress.json) | Completion of the API development and acceptance stages. |

A checksum is a fingerprint of a file's exact contents. The verification record
uses these fingerprints to identify the jars, worlds and logs that were checked.
See [API release checks](../docs/API_RELEASE_CHECKS.md) for the maintainer rules
and [current status](../docs/STATUS.md) for a readable account of what works and
what remains outside the release.

## Earlier releases and investigations

These records describe the version named in the table. They preserve the original
results, including failed attempts; they are not the current installation guide
or list of open problems.

| Record | What changed or was investigated |
| --- | --- |
| [0.1.0-beta.5](0.1.0-beta.5/README.md) | Stationary completion, optional Pinout, saved electronics, native player workflows and the complete Stationary Yard. |
| [0.1.0-beta.4](0.1.0-beta.4/README.md) | Restart and chunk-loading fixes. |
| [Restoration investigation](fixes/restoration/README.md) | Reproductions, fixes and checks leading to beta.4; investigation notes are separate from final acceptance. |
| [0.1.0-beta.3](0.1.0-beta.3/README.md) | Grounding fixes and checks on both solvers. |
| [Grounding investigation](fixes/grounding/README.md) | The defect and checks leading to beta.3. |
| [0.1.0-beta.2](0.1.0-beta.2/verification.json) | Handheld-meter, example-heater and startup-registration fixes. |
| [Expanded yard v1](examples/expanded-yard-v1/README.md) | The earlier beta.2 demonstration world and its recorded coverage. |
| [Panel and relay example v2](examples/panel-relay-v2/README.md) | The earlier beta.2 panel and board demonstration. |

[Release history](../docs/RELEASE_HISTORY.md) explains older restart and reload
reports, their later fixes and the limits of what the original logs established.

## Original 0.1.0-beta.1 records

The files at the root of this directory belong to the
[first functional beta](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.1).
Its narrower content matrix is preserved separately from later releases.

| File | Purpose |
| --- | --- |
| [artifacts.json](artifacts.json) | Pinned dependency coordinates, checksums, source references and native binary provenance. The build still uses these dependency records. |
| [content-matrix.json](content-matrix.json) | Original inventory of 150 behavior groups: 134 declared and verified, 14 unverified and two unsupported. |
| [verification.json](verification.json) | Local acceptance snapshot from 6 October 2026, including log hashes and test counts. |
| [distribution.json](distribution.json) | Original jar, example-world and verification-archive hashes and runtime requirements. |

Statements such as “prepared and tagged locally” describe the original preparation
snapshot. A recorded pass establishes what that run checked; it does not erase
the [intermittent failures recorded later](../docs/RELEASE_HISTORY.md#unresolved-chunk-reload-failure).

Published tags, assets, checksums and captured evidence remain unchanged. Download
bundles contain the documentation from their release; use the current repository
guides for later updates.

WWPG's own code and documentation, including its portions of the first beta,
are available under the [MIT License](../LICENSE). The original first beta jar's
“All Rights Reserved” metadata predates that decision. Later builds include MIT
metadata; bundled Power Grid native resources retain their Apache 2.0 license
and attribution.
