# Prepared CEE 1.2 alpha: 0.2.12-dev.cee120.1

This is a review preparation, not a published release. The release notes and
five grouped downloads are being prepared for owner approval. Publication is
not authorized yet. The normal graphical acceptance gate remains unmet; any
headless-only alpha exception needs separate approval for this exact build.

Full headless acceptance passed **286/286 jobs** on tested source
`0cbbc7f7837eec738237a839d5aa16235f0e9561`:
https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/45023229639

Windows/Linux, Native/Java and BASE/ADDONS all passed. Each OS also passed
160 JVM tests and independent extracted SDK/example/starter builds. The strict
combined evidence gate passed. Original reports, logs, archive reopens and
loaded dependency hashes are retained under `local/headless-0212-1/`.
The exact player SHA256 is
`ec4980f544120e39c89002c0ee0cb936ec62194c9685177d2e40022344cc6723`;
its companion SHA256 is
`3d832f7a951ebc7e65ad9b32f12a398b4a69d29864c84a96c297697425c312d7`.

The runtime is unchanged from the tested jar. This preparation changes only
release documentation and packaging inputs. The independent CEE comparison
retains the approved test-only `cee120-residual-1e-16` reference control.
Graphical acceptance, real Sable/Power Dock client passes and separately selected
cross-version cold migration were not run. Historical alpha assets and evidence
remain unchanged.

Example worlds are the exact Windows Native exports tested and reopened in the
new campaign, with their original `.1` labels and bytes. They replace the older
historical `.5` downloads for this candidate. API 1.0.0, Maven payloads, both
example sources and compiled add-ons come from the accepted CI preparation.
