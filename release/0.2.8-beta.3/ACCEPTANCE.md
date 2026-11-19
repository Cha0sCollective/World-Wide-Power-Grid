# 0.2.8-beta.3 prerelease acceptance

The actual execution source is `1e511f59e384de3072d69303a7b2fb5c9cd9dde1`.
The accepted gameplay jar SHA256 is
`25275a3a873510a88cf29e6bf94c8eb231ae7bf462036d5a2b228a0dbd1a8081`.
The release source also includes later documentation and tooling corrections.
Its runtime/build inputs are unchanged, and publication explicitly reuses the
same accepted jar, APIs, examples and separate GameTest companion. Original
reports retain their execution source; they are not relabelled as new runs.

## Completed coverage

- All **654 unique headless jobs** passed coverage validation across Windows 11
  and Ubuntu 24.04/WSL, Native/Java and BASE/PINOUT. NeoForge **21.1.266** is the
  complete primary environment; 21.1.231, 21.1.248 and 21.1.265 use compatibility
  subsets. This is local acceptance, separate from GitHub's focused checks.
- All **12 real Windows client scenarios** passed: integrated yard and Workshop,
  actual two-client TCP sessions, BASE Workshop and the independent SDK starter.
  Named electrical, input, rendering, synchronization and clean shutdown
  assertions passed. Representative screenshots were inspected; screenshot
  counts do not establish coverage.
- The frozen primary inputs used PG 0.6.2, CEE 1.1.4, Create 6.0.10 and
  Architectury 13.0.11. The computer profile used CC:Tweaked 1.121.0 and Pinout
  0.2.1; older loaders used their separately locked compatible computer jar.
  Independent SDK examples and starter source/output hashes match across both
  operating systems. API 1.0.0 and native binaries are unchanged.
- Four supplemental CIO inverter/transition checks passed on Native and Java.
  Focused tooling checks passed after correcting stale launcher/inventory
  controls. GameTests and fixture code remain outside the gameplay jar.

Windows retained 129 complete passing jobs after an interrupted startup process,
then ran its 198 missing jobs. Linux completed all 183 primary jobs before a
planned orchestration checkpoint between JVM launches. Its remaining compatibility
work ran in isolated Native/Java checkouts on separate CPU sets. No test process
was interrupted at that checkpoint; every required job is covered exactly once.

Earlier Linux preparation interruptions, an SDK ZIP permission mismatch on a
Windows-mounted filesystem, and two first-time 21.1.248 cache-construction
timeouts remain preserved as failed/incomplete attempts. The permission mismatch
was corrected through a permission-preserving G: mount, without relaxing byte
comparison. Missing loader development files were prepared separately under the
existing 1,200-second preparation bound, then checked before fresh missing tests.
The gameplay launcher remained **600 seconds**, with the normal watchdog.

## Original 14,650-node Native benchmark

One steady control run used the unchanged original save, 16 substeps, an 8 GiB
heap, High performance power plan and the retained server-thread CPU placement
(logical 12–23, mask `fff000`). No other test JVM ran during timing. Following
120 seconds of warm-up, 2,400 ticks were measured over 120.10 seconds:

| Observation | Result |
| --- | ---: |
| Average throughput | 19.98 TPS |
| Mean server-tick work | 48.53 ms |
| p95 server-tick work | 60.65 ms |
| Maximum server-tick work | 90.48 ms |
| Electrical restoration and healthy settling | 38.18 s |

The separate JVM-to-server-start marker was 13.71 seconds. Post-tick observer
work averaged 0.14 ms and is excluded from the server-tick column. These are
separately measured stages, not a directly measured combined startup figure.

The audit retained 14,650 electrical nodes, 20,637 connections and 14,925 matrix
unknowns. The graph was identical at measurement start/end. The run captured
1,050 stored-device entries, performed its saved-state expectation checks and
12 ordinary source variations, and recorded zero rejected or independent CEE
solves. Normal saving and stopping succeeded without a watchdog event.

The average-throughput threshold was reached in this window, but **p95 exceeded
50 ms**. The combined reliable-20-TPS target therefore remains unmet. This was
one control run, with no structural churn/reload phase, matched beta.2 comparison,
Java capacity run or addon performance comparison. Detailed phase timers were
disabled, so no fresh phase-cost attribution is claimed. Benchmark tooling and
detailed timing records remain local; the correctly labelled original save is
included in the example-worlds download.

## Behavior and remaining limits

Beta.3 removes duplicate CEE restoration scans while retaining coherent rollback,
isolation and unavailable-reading safeguards. It restores PG's original generator,
rotor and clutch timing, including upstream's intended oscillation. The removed
0.2.7–beta.2 stabilization is historical behavior. Focused native-timing comparisons
used a retained reference runtime with coupling disabled; they were not a fresh
PG-only self-excitation campaign or a claim of identical transients everywhere.

The historical Linux Native/BASE/21.1.248 motor assertion did not recur in this
campaign's SETUP or three cold VERIFY phases. Its original cause remains
unexplained; these passes do not close that finding. Large Java factorization
stalls and general server-capacity limits remain separate uncertainties.

The verification download retains locked inputs, original process records, logs,
selected screenshots and explicit artifact-equivalence records. Publishing does
not launch either acceptance lane again.
