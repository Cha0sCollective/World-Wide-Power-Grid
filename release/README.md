# Release records

[examples/panel-relay-v2/](examples/panel-relay-v2/) records the visibly testable panel/board example for the existing beta.2 runtime. Its world ZIP and verification archive are additional downloads; earlier artifacts, checksums, and records are preserved.

[0.1.0-beta.2/](0.1.0-beta.2/) records the handheld-meter, example-heater, and startup-registration hotfix separately. It retains the same dependency baseline, adds explicit handheld support, and supplies new verification and distribution records, including passing runs and unresolved intermittent failures. The original beta.1 records below are preserved.

This directory records the fixed dependencies, supported behaviors, local acceptance, and hashes used to prepare **0.1.0-beta.1**. The beta is now [published on GitHub](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.1). See [current status](../docs/STATUS.md) for publication, later CI results, and known issues.

| File | Purpose |
| --- | --- |
| [artifacts.json](artifacts.json) | Published dependency coordinates, checksums, matching source references, and native binary provenance. Used by the build. |
| [content-matrix.json](content-matrix.json) | Inventory of 150 behavior groups: 134 declared/verified, 14 unverified, and two unsupported. Links declared behaviors to their tests. Bundled in the jar. |
| [verification.json](verification.json) | Local acceptance snapshot from 6 October 2026, including log hashes and test counts. |
| [distribution.json](distribution.json) | Original jar, example-world, and verification-archive hashes and runtime requirements. |

`verified` records passing acceptance for the documented behavior. It does not imply every upstream item/configuration has been checked or that later regressions are resolved. Shared lifecycle tests cover mapping behavior; the [known chunk-reload failure](../docs/STATUS.md#unresolved-chunk-reload-failure) remains relevant.

The local evidence predates GitHub publication. Statements such as “remote jobs have not been executed in this local session” and “prepared and tagged locally” describe that preparation snapshot. They are preserved as evidence, not used as the current project status. The original JSON records, release tag, published assets, and hashes remain unchanged.

The GitHub release also provides `SHA256SUMS.txt` for all four downloadable artifacts. The complete bundle includes installation instructions, support documentation, the example world, and test logs. Its embedded documentation is the original release snapshot; use the repository's current docs for later status updates.

WWPG's own code and documentation, including the WWPG portions of this beta, are now available under the [MIT License](../LICENSE), also supplied as a separate `LICENSE` download on the GitHub release. The original jar's “All Rights Reserved” metadata predates that decision. New builds include the MIT license and metadata; the original published artifacts and checksums are preserved. Bundled Power Grid native resources retain their Apache 2.0 license and attribution.
