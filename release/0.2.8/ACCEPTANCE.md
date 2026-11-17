# 0.2.8 release qualification

Completed local qualification covers **342 headless jobs** and **12 separate
graphical scenarios**. These are local Windows and Ubuntu/WSL executions;
GitHub's focused PR checks are recorded separately at publication.

The frozen execution source is `bf0fbb87b36d73e1565fd26767847e791eb7a202`.
The gameplay jar is `wwpg-0.2.8.jar`, SHA256
`428f03c7e6b6eea3045874ecffdbdb3ce5b07e7450b7e943de09129c53f0b3ec`.
The separate GameTest companion has SHA256
`4f9894a7b78eb6b3b3783682d1d26f2468109d7418f7fa779b2b728b08b17075`.
Later publication-only documentation and inventory updates retain these exact
accepted bytes, with explicit source comparisons and original proof identities.

## Environment and coverage

The actual selected runtime is Minecraft 1.21.1, NeoForge 21.1.262, Java 21,
Create 6.0.10, PG 0.6.2, CEE 1.1.4 and Architectury 13.0.11. PINOUT uses
CC:Tweaked 1.120.2 and Pinout 0.2.1. The verification bundle records loaded
versions and hashes; embedded historical lock labels are not a claim that
21.1.266 or CC:Tweaked 1.121.0 ran in this campaign.
Some inner save text files also retain older installation versions. The outer
worlds bundle and current installation guide give the selected 0.2.8 requirements;
the exact tested world archives are unchanged.

Both operating systems, both solvers and BASE/PINOUT completed the recommended
loader's required plan. Preparation includes 137 unit tests per operating
system and independent builds of all three extracted SDK projects; their
source archives and compiled outputs match across platforms. Required cold
restart sequences, exact exported-world reopens, unavailable-state recovery,
motor restoration, upstream references and circuit checks remain included.
Older loaders are separate focused compatibility checks, not extra full suites.

After Windows finished, both completed Linux Native environments were retained
and their idle coordinator stopped at a process boundary. The two remaining
Java profiles ran in separate checkouts and CPU groups, using the same frozen
runtime and preserved Linux SDK preparation. No gameplay process was interrupted
for that scheduling change.

All twelve Native/Java graphical scenarios passed: integrated yard and Workshop,
real two-client yard and Workshop sessions, BASE Workshop and the extracted SDK
starter. Named controls/readings/synchronization assertions and successful
save-and-quit or disconnect/save/stop outcomes are retained. Selected screenshots
were inspected; screenshot counts are not a substitute for functional assertions.

## Failed attempts and uncertainty

The first Windows Native/BASE regression process passed 218 of its 219 tests.
`poweredSparsePanelOperationsObserveLiveGeometry` timed out waiting for its
first accepted voltage, reporting `NOT_READY`. Diagnostics passed in isolation,
with the original 219-test selection and at the original failed coordinates.
A difference between GameTest entity-ticking readiness and actual entity loading
is a plausible explanation, not a demonstrated cause.

After investigation, a fresh continuation retained eleven complete passing jobs
and executed the thirty-two missing jobs, including the full regression cold
sequence. Runtime, companion, assertions and deadlines were unchanged. All
required coverage passed; the original failed process is preserved as failed.
Passing repetitions do not explain or close that original readiness finding.

The continuation later stopped at a starter-launcher path check before any JVM
started. Its frozen runtime had been referenced from the other checkout. The
same artifacts were copied and rehashed inside the isolated checkout, then only
the starter and two unexecuted reference jobs ran. Forty completed jobs were
retained without rerunning or changing their original outcomes.

A tooling inventory assertion still expected 435 tests in 72 namespaces. The
merged motor regressions bring the source to 437 tests in 73 namespaces. Only
that expected inventory was corrected; the focused packaging checks passed.
Earlier preparation/orchestration failures retain their original outcomes.

The historical Linux Native/BASE/21.1.248 motor assertion remains unexplained.
Current motor restoration coverage does not establish a cause for that older
assertion. Original beta records and failed attempts remain unchanged.

## Behavior and limits

This release combines the 0.2.8 beta improvements with the merged motor
restoration correction. API 1.0.0, saves and sixteen electrical substeps remain
unchanged. PG's original generator, rotor and clutch timing is restored,
including intended self-excitation oscillation; the stabilization from 0.2.7
through beta.2 is removed.

There was no new capacity benchmark in this campaign. Reliable 20 TPS on the
14,650-node yard remains uncertified, and its beta.3 timings remain historical.
Private diagnostics and worlds are not source changes or extra release assets.
The five-download publication layout keeps the separate tests and acceptance
evidence in the verification bundle.
