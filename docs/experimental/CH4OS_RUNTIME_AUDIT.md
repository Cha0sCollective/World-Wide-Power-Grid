# Ch4oS runtime audit — November 9, 2026

Read-only inspection of the local `Create-Ch4oS-Public-Prism` instance and the
WWPG experimental benchmark. This identifies candidates for later focused
comparisons; it does not measure their contribution. No gameplay benchmark,
mod installation, launcher edit or normal-runtime replacement was performed.
The preceding day-long investigation remains paused.

## What is actually different

The Public instance has 243 jar files in its mods directory. The latest log
confirms Microsoft Java 21.0.7 and loading of the principal performance mods.
Its WWPG 0.2.7 jar is byte-identical to the published baseline, SHA-256
`6c1060b17f14df765188a60da154b723705947503a813508a53f62013e625053`.
PG 0.6.2 and CEE 1.1.4 also match the pinned benchmark jars. An improvement
observed in this pack therefore does not require the experimental WWPG changes.

| Setting | Public Prism instance | Recent focused benchmark |
| --- | --- | --- |
| Java | Microsoft 21.0.7 | Temurin 21.0.12+8 |
| GC | Configured `-XX:+UseZGC -XX:+ZGenerational` | No collector override; G1 selected by a fresh JVM probe with the recorded heap flags |
| Initial / maximum heap | 1,024 / 14,000 MiB | 512 MiB / 8 GiB |
| NeoForge | 21.1.262 | 21.1.231 |
| Create | `create-1.21.1-6.0.10.jar`, different hash | `create-1.21.1-6.0.10-280.jar` |
| Architectury | 13.0.11 | 13.0.8 |
| Client render / simulation distance | 12 / 10 | Prior client loading lane: 12 / 12 |
| Client frame cap / VSync | 60 / off | Prior client loading lane: 60 / off |
| Process CPU placement | No controlled match established | Explicit `fff000` mask, logical CPUs 12–23 |

No JVM was running during the final sanitized process check, so the pack's
collector is a confirmed launcher setting rather than a live JVM observation.
The log confirms the runtime vendor/version. The older Beta and Experimental
Prism instances also configure generational ZGC, with 12,288 MiB maximum heap.
The older plain `Create-Ch4oS` instance has empty custom JVM arguments; it
should not be treated as evidence that every Ch4oS installation uses ZGC.

The minimal headless benchmark has only Architectury, Create, CEE, PG, WWPG and
its observer/helper. The pack's following performance mods are absent there.

## Installed performance mods and meaningful settings

| Component | Installed version | Relevance / local settings |
| --- | --- | --- |
| Lithium | 0.15.4+mc1.21.1 | Candidate for general server/client simulation overhead; user config has no explicit overrides. |
| ModernFix | 5.27.26+mc1.21.1 | Loading, resources and memory candidate. Explicit overrides: dynamic resources on, faster item rendering on, branding off. |
| FerriteCore | 7.0.3 | Blockstate/model/data deduplication. Config enables cache/model/quad deduplication and neighbor/property replacements; compactFastMap and the small threading detector are off. |
| Sodium | 0.8.13+mc1.21.1 | Client renderer. Configured 20 chunk-builder threads, deferred builds ALWAYS, visibility/face/fog culling, visible-only texture animation. |
| Sodium Extra | 0.9.3+mc1.21.1 | Client settings extension; presence is not a server-TPS improvement claim. |
| ImmediatelyFast | 1.6.15+1.21.1 | Client immediate rendering. Fast buffer upload/text lookup enabled; HUD batching explicitly off. |
| Smoothchunk | 4.1 | Save/unload scheduling: 300-second save delay, 20 chunk unloads per tick, protochunk saving disabled. These change scheduling/behavior and are not a transparent first test. |
| AllTheLeaks | 1.1.12 | Leak fixes/diagnostics; potential long-session stability benefit, not established as a steady-yard optimization. |
| GPU Memory Leak Fix | 1.8 | Client GPU memory concern; does not explain headless native solve time. |
| Leaky | 3.3 | Item-entity performance/detection and automatic removal thresholds. Do not copy removal behavior into a functionality-preserving test without evaluating it separately. |

Other installed helpers include Clumps, Connectivity, Packet Fixer, Recipe
Essentials and Structure Essentials. Their presence alone does not imply they
speed up electrical solving. Chunk/world generation and item/entity-heavy play
are different workloads from a restored stationary yard.

The pack's actual Lithium log is important: Sable disables groups including
world chunk access, world tick scheduling, some chunk ticking and collision
optimizations. Lithium is not running with every possible optimization enabled,
even though its user configuration is empty. Do not force those groups back on
to chase a score. A minimal Lithium lane without Sable is not the exact Ch4oS
compatibility configuration.

ModernFix logs configuration of Minecraft's `max.bg.threads` to 21. Together
with Sodium's 20 mesh workers, this is another concurrency difference to
measure during loading. These settings do not move WWPG's electrical solve
onto worker threads. Blindly copying a 20-worker setting into a process pinned
to twelve logical CPUs could increase contention.

## Assessment

**There are credible untested runtime opportunities.** The current benchmark
deliberately excludes the performance-mod stack. Its results describe that
minimal environment, not the best performance achievable in an optimized pack.

Lithium is the strongest first mod candidate for general server overhead;
ModernFix and FerriteCore are strong loading/memory candidates. Sodium and
ImmediatelyFast are strong candidates for visible smoothness and rendering
readiness. In an integrated client, less render-thread overhead can also free
CPU resources for the server, but improved FPS does not by itself prove higher
electrical TPS. This is an inference to test, not a measured attribution.

Generational ZGC deserves a separate comparison, but is not the leading
explanation for the existing stable tick tail. The last 120-second candidate
sample reported 92 ms total GC collection time and a peak heap metric of
1,442,410,768 bytes (about 1.34 GiB), while p95 tick time was 57.194 ms.
That is weak evidence for GC as the dominant steady bottleneck; it does not
exclude individual pauses, safepoints, concurrent-GC CPU cost, client memory
pressure or a larger effect during loading. GC/safepoint logs are needed.
The current heap measurement also provides no reason to automatically raise
the minimal benchmark's maximum from 8 GiB to 14,000 MiB.

Lower simulation distance, newer NeoForge and a different Create jar are
additional confounders. Both client lanes cap FPS at 60, so an uncapped-versus-
capped explanation is not supported by the inspected settings. The frame cap
still does not guarantee identical GPU/render workloads, resource packs or
shaders.

## Proposed focused comparisons, not yet run

1. Keep the WWPG jar, copied yard, Java 21.0.12 runtime, heap, CPU mask and
   settings fixed. Compare G1 against `-XX:+UseZGC -XX:+ZGenerational`, recording
   GC and safepoints in dedicated diagnostic observations and clean timing
   separately. Test larger heap only as another independent variable.
2. On the same base, add the exact installed Lithium version, then ModernFix
   and FerriteCore in separate named lanes. Record actual enabled/disabled
   mixin rules and require electrical, storage, callback and reload checks.
   Compare the pack-specific ModernFix overrides separately from its defaults.
3. For client loading and frame pacing, add Sodium and ImmediatelyFast at
   matched 1080p, 12 render chunks, 12 simulation chunks and 60 FPS. Compare
   chunk-builder worker counts without assuming 20 is ideal for the CPU mask.
   Measure integrated-server tick time alongside render readiness/frame times.
4. Only afterward test the newer NeoForge/Create/Architectury set as its own
   input change. Keep their evidence distinct from the original pinned set.
   A future release still needs the applicable requested acceptance on its
   actual frozen inputs; this audit does not authorize a full-suite rerun.
5. A realistic Ch4oS lane should use a copied instance/save and its compatibility
   configuration. Keep the minimal baseline as a diagnostic lane, with the
   representative performance stack as an additional user-facing environment.

## Evidence and upstream references

[Sanitized inventory and local source hashes](ch4os-runtime-audit-20261109.json)
preserve inspected settings and selected jar identities. Full launcher configs
and logs were not copied. The local instance/configs can change after this audit.

- [Lithium](https://github.com/CaffeineMC/lithium)
- [ModernFix](https://github.com/embeddedt/ModernFix) and its
  [dynamic-resources explanation](https://github.com/embeddedt/ModernFix/wiki/Dynamic-Resources-FAQ)
- [FerriteCore](https://github.com/malte0811/FerriteCore)
- [Sodium](https://github.com/CaffeineMC/sodium)
- [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast)
