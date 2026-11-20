# 0.2.11 release acceptance

The factory-light fix rejects new CEE copper links between connectors on the
same Power Grid terminal. A disposable save with an existing redundant link
reopened in a new server process and saved successfully. The wire stayed in the
save and did not affect the circuit. Regression checks covered a powered
neighboring circuit, separating the light row, and removing the redundant wire.

On a moving and rotating Sable 2.0.5 build, CEE wires to Power Grid and Power Dock
0.3.0-preview each passed 16 of 16 targeting checks and 917 render pose checks.
The CEE-to-CEE control passed the same checks. The client saved and exited.

All 342 headless entries and all 12 Windows graphical scenarios passed on
the frozen release candidate. Headless coverage spans Windows and Ubuntu,
Native and Java, and BASE and PINOUT. Graphical checks include integrated play,
two-client synchronization, clean saves, and the independently built starter.
The first Windows Java PINOUT safety attempt reached a chunk-readiness limit.
The continuation retained 136 passing entries and passed all 35 remaining
entries in fresh worlds, including all 67 tests in the affected safety group.

The verification download retains the execution records. Tests ran on source
`96e475d2ec476a90c14a2e1ed48a001ef3307e8d` with Minecraft 1.21.1, Java 21,
NeoForge 21.1.262, Power Grid 0.6.2, CEE 1.1.4, Create 6.0.10 and Architectury
13.0.12. The 14,650-node benchmark is the previously published save with updated
release labels. It carries no 20 TPS certification.
