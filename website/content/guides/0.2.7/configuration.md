WWPG uses Power Grid's electrical schedule. CEE prepares its models; WWPG updates the PG circuit; PG solves it; CEE uses the results for its native gameplay and displays.

## Configure before loading a world

Launch once to generate configuration, then stop the game or server. In `config/powergrid-server.toml`, set:

```toml title="config/powergrid-server.toml"
solverBackend = "NATIVE"
multiTicks = 16
```

Keep the other PG solver settings at their defaults. Restart after changing these settings. CEE's `microTicks` does not set the simulation schedule while WWPG is installed.

The 16 electrical steps divide one normal 50 ms game tick into 3.125 ms steps. This gives changing currents, AC devices and stored energy a finer calculation interval; it does not make the game run 16 times faster. Keep this setting when using the release's tested circuits.

WWPG 0.2.7 also coordinates supported PG generators' field and rotor updates with this schedule while retaining PG's once-per-tick clutch response. You can control excitation with a transformer, variac or carbon pile. See [generator troubleshooting]({{docs}}troubleshooting/#a-self-excited-generator-keeps-surging) if a self-excited circuit keeps surging.

## Check which backend loaded

Run `/wwpg` as an operator with permission level 2. Check the reported backend and simulation steps: requesting the native backend in configuration does not establish that it loaded.

The native engine requires **x86-64 with AVX2** on Windows and Linux. Linux release testing uses **Ubuntu 24.04**; other distributions remain unverified. PG's Java backend also remains available. Both engines are covered by the release's electrical checks on Windows and Linux; confirm which one actually loaded with `/wwpg`.

## Native files and runtime dependencies

WWPG supplies a **guarded build of PG's native engine** using the PG 0.6.2 interface and the same electrical settings. It rejects unsafe failed calculations and reports unavailable readings until the circuit recovers. **Install Power Grid 0.6.2 only**; no older PG mod jar is needed.

WWPG loads its verified engine from a checksum-named folder inside `.pg-native` in the game working directory. Existing administrator-installed files are preserved. The bundled engine is required for these safety checks; a load failure disables native support and reports the problem. PG and native-library licenses remain included.

See the [complete installation guide]({{docs}}install/#native-solver-requirements) for requirements and attribution. These instructions are derived from its [release-pinned source]({{source}}/docs/INSTALL.md).
