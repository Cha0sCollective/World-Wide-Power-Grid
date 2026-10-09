# Install WWPG 0.1.0-beta.1

Use Java 21 and install this exact NeoForge combination on the server and each client:

| Dependency | Fixed version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.231 |
| Create | 6.0.10-280 |
| Create: Electro Energetics | 1.21.1-1.1.3 |
| Power Grid | Minecraft 1.21.1, 0.6.2 |
| Architectury API | NeoForge 13.0.8 |
| WWPG | 0.1.0-beta.1 |

Put the jars in `mods`. Use Create's published full release jar for normal installation and release testing. An optional slim development configuration uses Ponder 1.0.82+mc1.21.1 and Flywheel 1.0.6 separately; these are the same pinned runtime libraries. See [the artifact lock](../release/artifacts.json) for the exact CEE and PG downloads and hashes.

Start once to create configuration, then stop. In `config/powergrid-server.toml`, set `solverBackend = "NATIVE"` and `multiTicks = 16` before reopening the world. This is the substep count used for the release's AC, motor RMS, storage and electronics checks. Keep PG's other solver settings at their defaults. CEE's `microTicks` does not control simulation with WWPG installed. Do not change the substep count while playing; restart after configuration edits.

WWPG includes the verified native v7 DLL/SO from the published PG 0.6.1 artifact because PG 0.6.2 omitted them. The JNI interface is byte-identical. WWPG installs a missing binary into `.pg-native` in the game's working directory before PG loads it. It preserves an existing administrator-installed binary. **Install only PG 0.6.2 as the running mod**; the 0.6.1 donor jar is not a dependency to put in `mods`.

Native release checks cover Windows x86-64 and Linux x86-64 on Ubuntu 24.04. The Linux binary requires glibc 2.38 or newer. PG retains its Java backend/fallback; the complete electrical suite is also checked with Java. Use `/wwpg` to see the backend actually used. `/wwpg errors` and `/wwpg at x y z` expose recent errors and terminal mappings; these commands require operator permission level 2.

Use the upstream wire/spool/cord tools on the native terminals. PG cords use socket/junction interactions and split ends; hidden cord terminals are not bare-wire attachment points. CEE duplex terminations keep their sparse terminal IDs. CEE insulators and structural poles keep their native role and do not acquire invented terminals.

The declared supported behaviors are listed in [SUPPORT.md](SUPPORT.md) and [the machine-readable matrix](../release/content-matrix.json). All 11 built-in CEE panel attachments and all 28 built-in PG board components are included. Optional sensor attachments, moving systems, Sable sublevels, specialized unclaimed devices and large-network certification are deferred.

PG's portable battery charges a native energy item; its placed block is not a voltage source that discharges into a wire. Use CEE's accumulator for reversible circuit storage. CEE 1.1.3's creative battery AC mode produces a 20 Hz waveform despite its frequency selector; WWPG preserves that pinned upstream behavior. Use a PG AC source or a driven CEE alternator for other frequencies.

The example-world ZIP contains `WWPG Example/`. Extract that directory into the client's `saves` directory, or use it as the dedicated server's level directory. Install the exact dependencies and solver settings above. Spawn is at `(8,64,16)`: the northern circuit is a CEE source, PG resistor/capacitor board and CEE control/meter panel; the southern circuit powers CEE lighting, heating and a real water pump from PG. The panel's **Factory enable** switch controls the storage circuit. Rebuild with the commands in [DEVELOPMENT.md](DEVELOPMENT.md).
