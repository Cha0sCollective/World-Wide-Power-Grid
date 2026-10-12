# Create your own adjustable load

Build a separate add-on with one controllable electrical load. You need basic Java, Java 21 and the released WWPG **0.2.1** developer SDK. You do not need WWPG's implementation source or previous NeoForge experience. This project is a creative teaching example, not a survival-balanced machine.

## 1. Prepare the project and SDK

Download the [developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-developer-sdk.zip), and check the [published checksums](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-checksums.txt). Extract the outer ZIP. Extract `maven/wwpg-api-1.0.0-maven.zip` into `wwpg-api-maven`; its root contains `org/cha0scollective/wwpg`.

The [complete starter source ZIP](https://wwpg.ch4os.live/tutorial/0.2.1/tutorial-load-0.1.0-source.zip) and [repository project](../examples/adjustable-load/README.md) contains only one load. Its addon ID is `tutorial_load`; the supplied examples use different IDs, so they can run together. The starter's own version is **0.1.0**, while its compile-time API is **1.0.0**.

For a fresh project built by copying files: create an empty `tutorial-load` directory, copy `gradlew`, `gradlew.bat` and the entire `gradle/wrapper` directory from the SDK's extracted general example, copy its `LICENSE`, then add the complete text files from the [website file listings](https://wwpg.ch4os.live/docs/0.2.1/create-addon/#complete-project-files). Do not copy the nine-device example's Java or resources. All text files are included in the website tutorial; the starter directory has the same files and wrapper. Keep its MIT license file at the project root; the build packages it into your jar.

For the ZIP route, download both the SDK and the starter source ZIP before these commands. The examples below assume they are in your Downloads directory. Use new destination directories; do not extract over another project.

```powershell
Expand-Archive -LiteralPath "$env:USERPROFILE/Downloads/wwpg-0.2.1-developer-sdk.zip" -DestinationPath "C:/Dev/WWPG SDK"
Expand-Archive -LiteralPath "C:/Dev/WWPG SDK/maven/wwpg-api-1.0.0-maven.zip" -DestinationPath "C:/Dev/WWPG API Maven"
Expand-Archive -LiteralPath "$env:USERPROFILE/Downloads/tutorial-load-0.1.0-source.zip" -DestinationPath "C:/Dev/Tutorial Load"
Set-Location "C:/Dev/Tutorial Load"
```

```sh
unzip "$HOME/Downloads/wwpg-0.2.1-developer-sdk.zip" -d "$HOME/Dev/WWPG SDK"
unzip "$HOME/Dev/WWPG SDK/maven/wwpg-api-1.0.0-maven.zip" -d "$HOME/Dev/WWPG API Maven"
unzip "$HOME/Downloads/tutorial-load-0.1.0-source.zip" -d "$HOME/Dev/Tutorial Load"
cd "$HOME/Dev/Tutorial Load"
chmod +x gradlew
```

Expect `settings.gradle` and `src` in the project root, and `org/cha0scollective/wwpg` directly in the Maven directory. Existing SDK example archives require `chmod +x gradlew`; the documentation companion and future exports also carry executable Unix metadata.

The wrapper runs the project's Gradle version. `settings.gradle` names the project; `build.gradle` declares the NeoForge plugin and compile-only API; `gradle.properties` controls Gradle's JVM. None is a Minecraft gameplay class.

## 2. Understand the names

| Name | Where it is used |
| --- | --- |
| `tutorial_load` | `@Mod`, NeoForge metadata, Gradle's `mods` entry and resource namespace |
| `org.example.tutorialload` | Java package and matching directories under `src/main/java` |
| `tutorial_load:load` | Block/item registry ID and the WWPG provider ID |
| `positive`, `negative` | Stable terminal keys; native IDs 0 and 1 |
| `load_in` | Internal node between the switch and resistor |
| `enable`, `load` | Stable switch and resistor element IDs |

To name your own add-on, change the mod ID in **Java, TOML dependency table names, Gradle's mods entry, resource directories and JSON references together**. Change the Java package with its directories and imports. Rename the jar's archive name and display strings too. Do not rename terminal/element IDs in an existing saved world unless you intentionally replace its electrical identity. Keep the general example installed during the first circuit test. Install both SDK examples for the final naming/collision check; the native example also needs its declared CEE/PG prerequisites. The resulting jar is `<archivesName>-<version>.jar`; the filenames below assume the starter is unchanged.

## 3. Follow the electrical flow

1. **Register:** `TutorialLoadMod` registers ordinary NeoForge blocks, items and block entities. On the mod bus's `RegisterCapabilitiesEvent`, it gives WWPG the `LoadDevice` capability.
2. **Describe:** `terminals()` returns immutable block-local geometry. `prepare()` captures the enabled flag and resistance in a switch-plus-resistor circuit. It has no gameplay effects and advances no electrical history.
3. **Solve:** WWPG sends that description through PG's existing electrical schedule. Your add-on does not create or run a solver.
4. **Consume:** `commit()` reads accepted measurements and changes the lamp indication. Unavailable results clear the indication and readout, while configuration remains saved.

Both contacts are near the top of the block's **north face** (negative-Z side). Native terminal **0** is positive; **1** is the return. This simple block has fixed orientation. If you add rotation later, transform the positions consistently in both terminal descriptions without changing their IDs.

An off state is still a wired device. The starter keeps its terminal list, internal node and two element IDs, changing `ElectricalModel.Switch.closed`. Returning `CircuitDescription.empty()` here would be wrong: that declares no terminals while `terminals()` still declares two. The runtime checks complete terminal records as a set, including position and hit radius.

`LoadBlock` handles empty-hand controls and lets held spools/meters reach their native item handlers. `LoadDevice` stores settings in versioned block-entity NBT. The inherited `slotId()` is `"main"`: this block has one device slot. The empty ground set adds no deliberate earth connection; this two-wire circuit measures voltage differences against the connected source, while PG supplies its native reference for a floating network. An explicit ground fixes a declared node to the common reference; do not add one merely to force a meter reading between unrelated floating networks. This resistive load has no electrical storage history; a capacitor/inductor add-on must additionally save accepted `electricalState()` values and must not overwrite them on unavailable results.

## 4. Build and install

Run from the starter directory containing `settings.gradle`:

```sh
chmod +x gradlew
./gradlew build "-PapiRepository=/absolute/path/to/wwpg-api-maven" --no-daemon
```

```powershell
.\gradlew.bat build "-PapiRepository=C:/Dev/WWPG API Maven" --no-daemon
```

Both commands use an absolute repository path; quote the complete property argument when it contains spaces. Expect `BUILD SUCCESSFUL` and `build/libs/tutorial-load-0.1.0.jar`. The project compiles against NeoForge 21.1.231; adding `"-Pneo_version=21.1.248"` selects the other tested loader for compilation.

If you do not yet have a NeoForge instance, follow the official [client setup](https://docs.neoforged.net/user/docs/client/) or [dedicated-server setup](https://docs.neoforged.net/user/docs/server/), selecting Minecraft **1.21.1** and NeoForge **21.1.248** (or the original tested 21.1.231). Start it once, stop it, and locate its game directory containing `mods` and `logs`.

Stop that separate Minecraft instance, then install **the starter jar**, **WWPG 0.2.1**, and the required mods from [Installation](INSTALL.md). Add the supplied `wwpg-api-example-1.0.0.jar` from the example-worlds bundle's `workshop-mods` directory for its 20 V source. Install matching jars on the dedicated server and every client. Keep SDK API/source/Javadoc jars out of `mods`. Remove older copies of your starter jar before restarting.

There is deliberately no `runClient` or `runServer` task in this project. Use the [build-and-install loop](ADDON_WORKFLOW.md), including debugger attachment, rather than trying commands the project does not provide.

## 5. Try a complete circuit

Create a Creative test world. Obtain **API Source** (`wwpg_api_example:source`) and **Tutorial Adjustable Load** (`tutorial_load:load`) from the Creative Redstone Blocks tab, or use `/give` with those IDs.

Place them a few blocks apart. Obtain a stack of **64 Copper Wire** (`/give @s powergrid:wire 64`) before starting; native placement checks the material available for the wire's length. With that PG wire, right-click the source's red contact (terminal 0), then the contact near the top of the load's north face at the western side (terminal 0: local x = 0.25, y = 0.85). A hanging wire appears when the second endpoint is accepted. Repeat from the source's blue contact (terminal 1) to the load's eastern contact (terminal 1: local x = 0.75, y = 0.85). Terminal hit radius is 12% of a block; the lamp texture has no painted contacts. Facing south at the north face, the western contact is on your right and the eastern contact is on your left. Aim close to those positions; reversed contacts still heat the resistor but give negative signed current/voltage. This connects source terminal **0 → load 0**, then source **1 → load 1**. Both wires are required. Leave the source enabled at 20 V. The load starts **ON / 100 Ω** and lights when it consumes power. CEE wire tools can build the same two connections instead; do not place overlapping duplicate wires for this first circuit.

| Action on the load, with an empty hand | Expected result |
| --- | --- |
| Start enabled at 100 Ω | About 20 V, 0.2 A, 4 W; lit |
| Sneak-right-click once | 200 Ω: about 0.1 A, 2 W |
| Sneak-right-click again | 400 Ω: about 0.05 A, 1 W |
| Sneak-right-click again | Returns to 100 Ω |
| Right-click the top face | Toggles OFF/ON; OFF draws approximately zero current and goes dark |

Right-click a side or the front with an empty hand to read without changing anything. After a setting change, the immediate message says **unavailable: waiting for next solve**. Wait a moment, then click the front again for the newly accepted voltage, current and power. The closed switch adds 0.01 Ω, and real wires add resistance, so expect small departures from the ideal values above. For the electrical-only comparison, `I = 20 / (R + 0.01 + total wire resistance)` and resistor power is `I²R`.

Use PG's multimeter across the terminals to check voltage and on a wire to check current. Hold CEE's clamp meter on either wire for the same current check. The native instruments must still work when held during block interaction. An open load may still have 20 V across its external terminals; an off state is not the same as an unavailable result.

## 6. Save, change and repair

Set the load to **OFF / 400 Ω**, save and stop the world, then reopen it. Check that its settings remain OFF / 400 Ω before enabling it; a fresh default must not silently hide state loss. Read settings with an empty-hand front click. For a real chunk-unload check, build away from spawn/forced chunks, such as `(24000, 64, 24000)`, set the server view and simulation distances to 5, teleport to `(0, 100, 0)`, and wait at least 20 seconds before returning. Use a breakpoint in `LoadDevice.onLoad()` to confirm a fresh load actually occurred; walking away alone does not prove an unload. Inspect saved fields at the end of `loadAdditional()` before resuming, as explained in the debugger guide. To reset the circuit, use PG wire cutters on the two wires and reconnect them; break and replace the load for default ON / 100 Ω settings. Removing and replacing the block creates a new load with default settings and new wire ownership.

An unavailable readout can mean loading, isolation or failed solving; it is not a measured zero. Inspect the [author troubleshooting guide](ADDON_TESTING.md) and the [constructor limits](API_LIMITS.md). The complete [general example](../examples/extension-addon/README.md) demonstrates storage and other models after this first device works. CEE properties and PG boards have different registration prerequisites; use the [native example](../examples/cee-addon/README.md) for those routes.
