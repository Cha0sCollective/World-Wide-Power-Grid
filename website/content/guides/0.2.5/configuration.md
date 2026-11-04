WWPG uses Power Grid's electrical schedule. CEE prepares its models; WWPG updates the PG circuit; PG solves it; CEE uses the results for its native gameplay and displays.

## Configure before loading a world

Launch once to generate configuration, then stop the game or server. In `config/powergrid-server.toml`, set:

```toml title="config/powergrid-server.toml"
solverBackend = "NATIVE"
multiTicks = 16
```

Keep the other PG solver settings at their defaults. Restart after changing these settings. CEE's `microTicks` does not set the simulation schedule while WWPG is installed.

## Check which backend loaded

Run `/wwpg` as an operator with permission level 2. Check the reported backend and simulation steps: requesting the native backend in configuration does not establish that it loaded.

The pinned release tests native operation on Windows x86-64 and Linux x86-64 with glibc 2.38 or newer. Linux release testing used Ubuntu 24.04. The Java backend remains available and receives the same functional checks on both operating systems.

## Native files and runtime dependencies

WWPG includes verified native v7 DLL/SO resources from official PG 0.6.1 because the pinned PG 0.6.2 jar omitted them. **Install Power Grid 0.6.2**, not the binary-source 0.6.1 mod jar.

WWPG installs missing binaries into `.pg-native` in the game working directory and preserves an existing administrator-installed binary.

See the [complete installation guide]({{docs}}install/#native-solver-requirements) for requirements and attribution. These instructions are derived from its [release-pinned source]({{source}}/docs/INSTALL.md).
