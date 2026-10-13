# WWPG 0.2.2-alpha.1

[Download the alpha](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.2-alpha.1).
The stable release remains 0.2.1. This alpha includes the loading improvements
and the expanded add-on SDK; extension API 1.0.0 and existing saves stay compatible.

The five downloads are recorded in [download-layout.json](download-layout.json).
The mod jar comes first. The SDK includes the build-your-add-on tutorial,
adjustable-load starter, workflow and troubleshooting guides, API jars, sources,
Javadoc and Maven repository. Worlds and verification records have their own ZIPs.
See [release notes](release-notes.md) and the [changelog](../../CHANGELOG.md).

## Acceptance

The release is frozen from source `3dbea810bbef75029045d95a851d50680d70aa9a`.
The accepted runtime is byte-identical to candidate 31 except for its declared
mod version. The preserved full gameplay checks cover native and Java solvers,
base and Pinout profiles, Windows clients, restarts and real two-client sessions.
The preserved full CI covers Windows and Linux on both solvers and profiles.
These are carried-forward results, not a newly run full suite.

Focused checks built the exact extracted SDK examples and starter. The starter
passed Windows/Linux server checks on both solvers and Windows graphical checks
on both solvers. API implementation comparisons, package validation, download
hashes and note limits also passed. The verification ZIP contains the locked
records, original evidence and comparison rules. Interrupted duplicate campaigns
remain recorded as interrupted.

Future development uses focused checks. Full suites, including end-of-day runs,
require an explicit user request; pushes, pull requests and releases do not
automatically launch them. See [publishing rules](../../docs/RELEASING.md).

## Remaining limits

The large-network native speed target and intermittent large Java factorization
stall remain open. Ordinary gameplay acceptance does not establish capacity
acceptance. Benchmark tools, worlds and detailed results remain local.

Published earlier releases, checksums, tags and captured failures are unchanged.
