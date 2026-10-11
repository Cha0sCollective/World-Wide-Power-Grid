**API 1.0.0 is available in WWPG 0.2.0.** Build stationary machines and instruments, add custom CEE panel properties, or connect Power Grid's native board components to mixed circuits. The mod version and API version are independent.

## Choose your integration

| Route                               | Contract                                                            | Working example                                                                       |
| ----------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| New stationary device or instrument | `org.cha0scollective.wwpg.api` and `.api.neoforge`                  | General machine addon: nine devices using immutable models and accepted results.      |
| Custom CEE property                 | Separate `org.cha0scollective.wwpg.api.cee` contract plus CEE 1.1.3 | Native addon: a storage-indicator panel attachment with checkpointed numerical state. |
| Power Grid board component          | Power Grid 0.6.2's own component registration and simulation        | Native addon: a circuit shunt assembled through PG's designer.                        |

A PG-native component already has an electrical owner. **Do not register a second WWPG circuit for it.** The CEE adapter route and PG's component route are separate upstream integrations.

## Get the released SDK

Use Java 21 and the pinned Minecraft 1.21.1 / NeoForge 21.1.231 development environment. Check the [full runtime requirements]({{docs}}install/) before testing in game.

1. Download and extract the [developer SDK bundle]({{asset:sdk}}), verifying it against [the release checksums]({{asset:checksums}}).
2. Extract its `maven/wwpg-api-1.0.0-maven.zip` into a directory such as `wwpg-api-maven`. The directory containing `org/cha0scollective/wwpg` is the repository root.
3. Point your build at its absolute path and declare the exact version:

```groovy title="build.gradle"
repositories {
    maven { url = uri('/absolute/path/to/wwpg-api-maven') }
}
dependencies {
    compileOnly 'org.cha0scollective.wwpg:wwpg-api:1.0.0'
}
```

The archive includes both coordinates, POMs, sources, and generated Javadoc. It works without GitHub credentials. Declare `1.0.0` explicitly; this distribution does not provide floating version-discovery metadata.

:::caution[Compile-time SDK, runtime mod]
The WWPG runtime supplies both API contracts. Do not put SDK, sources, or Javadoc jars in `mods`, and do not shade API classes into your addon. Declare WWPG as a required runtime mod, as the examples do. Optional integration requires isolated API-referencing classes and a runtime presence check before loading them.
:::

## Build your first integration

Inside the developer SDK bundle, extract `examples/wwpg-api-example-1.0.0-source.zip` into its own directory. The bundle's `SHA256SUMS.txt` also lists the enclosed files. The example includes its own Gradle wrapper and MIT license; no WWPG source checkout is needed.

Run from that extracted project, using the absolute path of your extracted Maven repository:

```sh title="Linux / macOS shell"
./gradlew build -PapiRepository=/absolute/path/to/wwpg-api-maven --no-daemon
```

```powershell title="Windows PowerShell"
.\gradlew.bat build -PapiRepository=C:/dev/wwpg-api-maven --no-daemon
```

The output is `build/libs/wwpg-api-example-1.0.0.jar`. Install it alongside WWPG 0.2.0 and every required mod on the server and each client. SDK jars stay in your development environment.

For a visible first circuit, obtain `wwpg_api_example:source` and `wwpg_api_example:load` from the Creative Redstone Blocks tab. Connect both matching red/blue terminal pairs with native wiring tools and a complete return path. The enabled load is two 100 Ω resistors in parallel: expect roughly **20 V, 0.4 A, and 8 W**, allowing for native wire losses. Empty-hand right-click shows server readings; sneak-right-click changes the setting. These ideal developer devices do not define balanced survival progression.

The [release-pinned source]({{source}}/examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/ExampleMod.java) is the complete working implementation. Its `ElectricalDevice` interface is:

```java title="Released ElectricalDevice contract"
public interface ElectricalDevice {
    String providerId();
    default String slotId() { return "main"; }
    List<Terminal> terminals();
    CircuitDescription prepare(TickContext context);
    void commit(DeviceResults results);
}
```

This excerpt describes the contract; use the downloadable wrapped project for the complete imports, registration, device implementation, and resources. See the [generated reference](/api/0.2.0/core/org/cha0scollective/wwpg/api/ElectricalDevice.html) for the full signatures.

## Registration and lifecycle

Register during NeoForge's `RegisterCapabilitiesEvent` using `ElectricalProviders.registerBlock` or `registerBlockEntity`. Each block has one declared electrical owner; duplicate claims fail. Registration freezes before worlds tick.

`terminals()` is a read-only immutable geometry query that can run on either logical side. It must not mutate the world or advance electrical history. Keep factories and geometry queries free of client-only classes.

`prepare()` captures immutable topology and parameters on the authoritative server thread. `commit()` receives one snapshot per participating server tick after PG's substeps. Apply gameplay only when the status is `VALID`. `NOT_READY`, `ISOLATED`, and `SOLVE_FAILED` mean unavailable readings, including empty result/history maps; they are not measured zeroes or permission to reuse stale readings.

Use stable namespaced provider IDs, device slots, terminal keys and native numbers, node keys, and independent element IDs. Parallel elements need separate IDs even when their endpoints match. Preserve accepted capacitor voltage and inductor current in versioned native saved data; restore initial values only when reconstructing elements. Invalid results must not overwrite that history.

Call `ElectricalDevices.changed(ServerLevel, BlockPos)` from server-side placement, loading, and configuration hooks when the device changes. Chunk unload is temporary and preserves wires/history; permanent block replacement retires the old wire identities. Let held native wiring tools reach `Item.useOn`: the example returns `SKIP_DEFAULT_BLOCK_INTERACTION` for nonempty stacks and leaves empty-hand interactions to its readout/control.

An addon owns gameplay, rendering, networking, inventory, recipes, and persistence. WWPG owns interoperability and the shared electrical schedule. Do not run an extra solver, mutate topology during solving, or perform gameplay side effects in numerical trials. A throwing `commit()` is quarantined; repair the cause and explicitly notify `ElectricalDevices.changed` before retrying preparation.

## Extend a CEE property or PG board

The SDK's `examples/wwpg-upstream-api-example-1.0.0-source.zip` includes a separate wrapped project. Build it with the same command and Maven repository. It produces `wwpg-upstream-api-example-1.0.0.jar` and pins CEE 1.1.3, PG 0.6.2, Create, and the other development dependencies.

CEE adapters additionally declare:

```groovy title="CEE extension dependency"
compileOnly 'org.cha0scollective.wwpg:wwpg-cee-api:1.0.0'
```

Subscribe to `RegisterCeePropertyAdaptersEvent` on your **mod event bus**, registering a namespaced ID and your exact custom property class. WWPG posts this event in the serial enqueued load-complete phase. Do not replace built-in CEE models or assume a superclass adapter owns subclasses.

Static adapters capture `CeeElectricalParameters`. Dynamic adapters use accepted substep callbacks and must checkpoint every field they can mutate. Restore checkpoints without world access or additional advancement. Use native CEE source polarity; WWPG translates it to PG. Whole-tick rollback covers checkpointed CEE numerical state, not arbitrary gameplay effects or native PG state. These callbacks must remain numerical.

The PG shunt keeps PG's native component registration, model, damage, schematic identity, and assembly-item workflow. It does not gain a duplicate WWPG device model. Consult the [native example implementation]({{source}}/examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamExample.java) and [CEE reference](/api/0.2.0/cee/) for the separate adapter contract.

## Test in the workshop

The [Extension API Workshop]({{docs}}workshop/) demonstrates nine general devices, a CEE storage indicator, and a PG shunt board. Install **both example addon jars** for this save. Ordinary WWPG factories do not require either example. [Downloads and worlds](/downloads/#worlds) include the base and optional Pinout editions.

## Compatibility boundaries

`WWPGApi.version()` reports API `1.0.0`; WWPG's mod version is `0.2.0`. Compatible API additions use minor versions; breaking contract changes require a new major version and migration guidance. This is the first public API baseline, not a claim of compatibility with a previous public SDK.

Use the supported API artifacts and pinned upstream contracts. WWPG's bridge, wiring, mixin, gametest, solver internals, network objects, and JNI pointers are implementation details. API 1.0 covers stationary integration; moving contraptions, raw matrix access, replacement Lua APIs, and broad dependency ranges remain outside this release.

The optional [Lua integration]({{docs}}lua/) belongs to Pinout's upstream API and is separate from this Java SDK. The future Ch4oS player manual will teach the installed pack's gameplay edition.

[Complete release-pinned developer contract]({{source}}/docs/EXTENSION_API.md) · [Java reference](/api/0.2.0/) · [General example guide]({{source}}/examples/extension-addon/README.md) · [Native example guide]({{source}}/examples/cee-addon/README.md)
