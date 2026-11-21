# Updated CEE 1.2 alpha

[Alpha prerelease](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.11-dev.cee120.3).
The [approved notes](release-notes.md) describe the electrocution,
contact-reading and transformer-reference repairs on the WWPG 0.2.11 baseline.

This alpha uses the exact `0.2.11-dev.cee120.3` player built from runtime source
`ac59a3880ca5d165950f326668e5d4cb7dc8b871`, SHA256
`8536f2efcb5b2af88b0b6757c08653ec9b7bb940e15442b5405bf80c550147d4`.
The paired GameTest companion SHA256 is
`3882a9aeec583375f2fbf707d5bc65e1933aba888d3fc6509be0c86c90b7e4ae`.
The release tag additionally contains publication tooling and documentation;
no gameplay jar was rebuilt for packaging.

The owner explicitly approved the notes and a focused-check-only exception for
this exact alpha on 21 November 2026. Each solver passed four transformer and
four electrocution regressions, plus 32 surrounding checks: 80 focused server
GameTests total. All 158 JVM checks and 66 original tooling checks passed.
Separate focused publication-tool checks validate this exception and the upload
process without starting gameplay.

Full headless and graphical acceptance have not been completed for this exact
build. No new client session was run for publication. The exception does not
apply to another version or artifact. Historical `.5`, `.8` and stable release
evidence retains its actual tested bytes and scope.

Example worlds are unchanged `.5` headless exports, not worlds retested on `.3`.
Their original inner version labels and hashes remain intact. API 1.0.0 and
example artifacts are retained from the `.8` SDK, with runtime/API class-byte
comparisons; this is not new independent SDK acceptance. Downloads contain no
upstream dependency jars. Use copies of worlds when testing.

Publishing uses `tools/release_downloads.py` and
`release_alpha_0211_exception.py`, which reparse the locked original process
logs, runtime audits, unit XML and approved disclosures. The final verification
manifest, exception, download layout and publication receipt are retained here.
