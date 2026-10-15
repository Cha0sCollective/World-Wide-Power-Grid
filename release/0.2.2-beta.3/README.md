# WWPG 0.2.2-beta.3

[Published downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.2-beta.3)
· [Verification manifest](verification.json) · [Checksums and five-download layout](download-layout.json).

Includes the three candidate72 optimizations: per-pass native terminal identity
reuse, validated CEE description/ownership reuse, and smaller result allocation.
It also contains main's disconnected-wire and repeated-diagnostics fixes since
beta.2. API 1.0.0, native binaries, electrical settings and all 16 substeps remain
unchanged.

The release jar differs from the fully tested candidate72 only in its declared
mod version. All 548 entries were compared. Its full Windows acceptance was
retained with original sources, dates and hashes: 95 units, 140 server invocations,
16 graphical/TCP proofs, and SDK starter controls/persistence. Four fresh Linux
Native/Java × BASE/Pinout jobs passed in
[run 38762412385](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38762412385).
The two extracted SDK examples compiled against the exact frozen Maven APIs and
matched their accepted jars. No Windows full-suite rerun is claimed.

Frozen release source: `8198cb8d0884ec023d7e864a117b895737348f65`, merged through
[PR #40](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/40).
Linux ran the version-equivalent runtime at source
`41ded651055a02f48fa2a05e2eee0da0d1e1b14b`; source 6017057's original Windows
runtime and client identities remain in the reuse record.

The worlds bundle has four demonstrations plus the original mixed capacity
save: 14,650 electrical nodes, 20,637 connections, 1,050 stateful devices and 953
chunks. Only that benchmark save's display name changed; its electrical data
and stored state were preserved. See the [benchmark guide](../../docs/BENCHMARK_YARD.md).
Private benchmark tools and detailed timing results were excluded.

The 14,650-node yard has not achieved certified 19.9 TPS with p95 ≤50 ms. No new
performance benchmark or Java capacity certification is claimed by this release.
Main acceptance uses pinned NeoForge 21.1.231; starter helpers use 21.1.248.
Windows provides graphical coverage; Linux provides server coverage. Results do
not establish performance on other hardware. Earlier releases, tags, assets and
failed-attempt evidence are preserved.
