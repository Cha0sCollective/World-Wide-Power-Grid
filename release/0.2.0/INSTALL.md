# Install WWPG 0.2.0


Use this fixed set on the server and every client:

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.231 |
| Java | 21 |
| Create | 6.0.10-280, full published jar |
| Create: Electro Energetics | 1.21.1-1.1.3 |
| Power Grid | Minecraft 1.21.1, 0.6.2 |
| Architectury API | NeoForge 13.0.8 |
| WWPG | 0.2.0 |

Stop the game or server, back up the world, and replace the older WWPG jar with
`wwpg-0.2.0.jar` in `mods`. Keep one WWPG jar. Install the upstream dependencies
separately and check their hashes against the included manifests.

Launch once to generate configuration, stop again, then set the existing values
in `config/powergrid-server.toml`:

```toml
solverBackend = "NATIVE"
multiTicks = 16
```

Restart and use `/wwpg` to confirm the backend that actually performed electrical
steps. Java can be selected with `solverBackend = "JAVA"`; keep 16 substeps.
CEE's separate microtick setting does not control the shared WWPG schedule.

WWPG supplies the verified PG native resources missing from the pinned PG 0.6.2
artifact. Keep PG 0.6.2 installed. The PG 0.6.1 jar used as the binary source is
not an additional runtime dependency. The supported native targets are Windows
x86-64 and Linux x86-64 with glibc 2.38 or newer. Runtime diagnostics identify a
fallback to Java.

For the optional Pinout profile, add CC:Tweaked 1.120.2 and CC;CPG: Pinout 0.2.1
to the server and every client. Base WWPG does not require them.

## Try the worlds

The four downloadable saves are:

- `wwpg-0.2.0-stationary-yard.zip`
- `wwpg-0.2.0-stationary-yard-pinout.zip`
- `wwpg-0.2.0-api-workshop.zip`
- `wwpg-0.2.0-api-workshop-pinout.zip`

The Stationary Test Yard demonstrates the supported upstream factory content.
Choose its base or Pinout edition for your installation. Extract the named save
folder into the instance's `saves` directory. Check the published checksums before extracting each save.

The separate Extension API Workshop also requires these example add-ons:

- `wwpg-api-example-1.0.0.jar`
- `wwpg-upstream-api-example-1.0.0.jar`

Install both on the server and clients before opening that world. Its Pinout
edition additionally requires the optional mods. The eleven workshop stations
are the same in both editions; computer demonstrations are in the Pinout
Stationary Yard.

Workshop spawn is `(9004,64,8995)`. The chest at `(9000,64,8997)` contains
goggles, both meters, native wire tools, assembly parts, and a guide. Put on
goggles and follow the white paths. Empty-hand right-click on an example block
prints readings; sneak-click changes its setting. Signs explain each expected
output and how to reset it.

If the PG design editor's name field or buttons are cut off, open **Options →
Video Settings → GUI Scale** and reduce the scale. GUI Scale 2 displays the
complete editor in a 1280 × 720 window. Check that the name field and Save button
are visible before editing the circuit.

When a supported extension terminal is still loading or its previous wires are
being retired, the native wire tool displays "Device is loading. Try again in a
moment." Wait for the area to finish loading and try the connection again.

Keep a clean copy of each download for repeatable experiments. Example sources
and creative components demonstrate the API and do not define balanced survival
progression. Existing factories do not need either example add-on.

## Build an add-on

Download and verify `wwpg-api-1.0.0-maven.zip`, then extract it. Use the extracted
directory as a Maven repository and declare:

```groovy
repositories { maven { url = uri('ABSOLUTE_PATH_TO_EXTRACTED_MAVEN') } }
dependencies { compileOnly 'org.cha0scollective.wwpg:wwpg-api:1.0.0' }
```

CEE property integrations additionally compile against
`org.cha0scollective.wwpg:wwpg-cee-api:1.0.0` and pinned CEE. The runtime WWPG jar
supplies both APIs; keep the compile-time API jars out of `mods` and do not shade
their classes into an add-on.

The example source ZIPs include their own Gradle wrappers and licenses. From an
extracted example directory, use Java 21 and run:

```text
./gradlew build -PapiRepository=ABSOLUTE_PATH_TO_EXTRACTED_MAVEN --no-daemon
```

On Windows, use `gradlew.bat`. Consult docs/EXTENSION_API.md for registration,
units, stable identities, state ownership, callbacks, measurements, and faults.
