# What the current GameTests actually check

Source audit: **23 October 2026**, checkout
`9e7083d0c02c2bd45dd2f9e2f23027cbc9b02eac` (WWPG 0.2.2).
The declarations were relocated without removing tests in local candidate 0.2.2-dev.99.
Source links below follow that new location. This is a reference to the assertions in the current source, **not a new test
run or a claim that every entry has passed in every environment**. See
[Testing process](TESTING.md) for execution and [published release records](../release/README.md)
for historical passing evidence.

## Inventory and how to read it

| Where the declarations live | Test methods | Source files |
| --- | ---: | ---: |
| Companion test jar: `src/gameTest/java/.../gametest` | 339 | 111 |
| Independently built general and CEE/PG example addons | 13 | 3 |
| Extracted SDK acceptance helper | 4 | 1 |
| Separate short TCP graphical helper | 1 | 1 |
| **Total** | **357** | **116** |

There are **40 namespaces**. These are annotated entry points, not 357 unique
device models, 357 simultaneous tests or the number of invocations in a complete
campaign. A method can operate many devices and check many assertions. Several
focused namespaces intentionally delegate to an existing fixture. Compilation,
JUnit, Python tooling, SDK build checks and native C fault-injection tests are
outside this GameTest count.

Each entry below explains the actions and assertions, then links every annotated
method back to its source. Counts include nested fixture behavior and delegated
checks in the description, but each annotated entry point is counted once.
Method names are source identifiers, not copy-and-paste `/test` command IDs.

**PG** means Power Grid; **CEE** means Create: Electrical Engineering. A
**mixed** circuit connects their native devices/wires through WWPG. **Unavailable**
means a measurement explicitly cannot be used; it is different from a valid zero.

## Find a particular concern

| Concern | Main fixtures to look up below | What they establish |
| --- | --- | --- |
| Capacitors and inductors | `DynamicGameTests`, `BoardComponentGameTests`, `StationaryAccuracyGameTests`, `RestorationGameTests`, `ElectronicsPersistenceGameTests` | Charge/discharge equations, step frequency, saved state before first solve and resumed operation. |
| Battery function | `FactoryGameTests`, `StationaryEquipmentGameTests`, `NativeReferenceTimingGameTests`, `NativeHandheldGameTests` | Portable/stationary charge, capacity, native nonrechargeable behavior, reference energy windows and multiblock connector readings. |
| Energy held during loading/failure | `LoadingBatchGameTests`, `SolverFailureGameTests`, `NativeFailureGameTests`, `ExtensionApiRejectedSolveGameTests` | No committed storage/FE/filter advancement on unavailable or rejected frames, followed by recovery. |
| Clicking contactors after restoration | `ContactorRestorationGameTests`, `CircuitRebuildGameTests`, `ProtectionGameTests` | Held accepted contact state, unavailable coil readings, delayed rebuild and accepted reopening/reclosing. |
| Multimeter briefly reads then zeros | `NativeHandheldGameTests`, `HandheldMeterGameTests` | Persistent connector/battery/generator readings, gauge agreement and correct unavailable recovery. |
| Terminal IDs and geometry | `NativeTerminalPreparationGameTests`, `CeeTerminalGeometryGameTests`, `ExtensionApiTerminalRefreshGameTests` | Native IDs, sparse/live geometry, custom callbacks, rotation, terminal removal and stable ownership. |
| Terminal boxes and mouse selection | Separate graphical `yard-integrated` scenario | Actual rendering/rays/native input; the geometry GameTests alone do not certify this. |
| Unload/rebuild failures | `UnloadPartitionGameTests`, `CircuitRebuildGameTests`, `WireRemovalRecoveryGameTests`, `CoupledNodeRemovalGameTests` | Correct split/merge, retired owner/hooks, native wire removal and surviving circuits. |
| Batching/input/power optimizations | `MatrixOwnershipGameTests`, `ResidualAssemblyGameTests`, `PlainWirePowerGameTests`, `NativeTerminalPreparationGameTests`, `StableBranchGameTests` | Correct matrix owner, contribution order/frequency, sampling fallbacks, identities and immutable old results. |
| Addon errors and lifecycle | `ExtensionApi*`, `FaultFixtures`, `UpstreamApi*` | Powered public/native API devices, malformed/throwing callbacks, quarantine, ownership, persistence and recovery. |
| Nether and optional computers | `StationaryNetherGameTests`, `NetherLifecycleGameTests`, `Pinout*`, `YardComputerBootstrapGameTests` | Native dimension transfer, teardown/reload and actual Lua-controlled output. |
| Save/reopen | Persistence, lifecycle and world-builder entries | Saved data/identity checks across the specified cold and chunk phases; client Save and Quit is separately checked by graphical acceptance. |

## Selection and limits

- The [versioned catalog](../tools/test-suites.json) selects namespaces, actual
  backend/profile/platform/loader and restart sequences. Merely registering a
  test does not prove every environment ran it.
- Ordinary regression/API groups run on Native and Java and on BASE/PINOUT
  where applicable. `wwpg_native_guard` is **Native only**. Pinout tests require
  the optional Pinout/CC:Tweaked installation. API groups require the independent
  example addons; the catalog supplies them. Some ownership tests intentionally
  create both backend types inside one selected run.
- `SETUP` creates/saves the fixture; a new `VERIFY` process reopens it. Catalog
  groups with four phases keep three distinct cold verifications. The pending
  retirement fixture uses **SETUP → ARM → VERIFY**. Focused execution normally
  runs only the initial phase. A SETUP-only pass does not prove cold restart.
- Real unload tests wait for chunk unload/entity-readiness conditions and often
  keep saved wire entities delayed. This is stronger than an NBT round-trip;
  the description explicitly distinguishes those cases.
- API ownership's fresh-only fixture is selected at setup. Upstream references,
  example/showroom worlds and exact exported-ZIP reopens have their own process
  selection. Export/reopen launchers are additional acceptance work, not extra
  `@GameTest` methods.
- Reference and legacy real-client namespaces are special selections, not an
  implicit part of every electrical group. `wwpg_doc` and `wwpg_ci_client` live
  in separate helpers; the latter and the legacy multiplayer entries need real
  clients. The 339 runtime entries include the two legacy client entry points.
- Registry/inventory checks prove existence/accounting. Server-side control
  handlers and mock-player operations do not prove real mouse/keyboard input,
  rendered geometry, sound playback or multiplayer synchronization.
- Graphical acceptance separately checks representative visuals, terminal
  highlights, native controls, two-client agreement and clean Save and Quit/
  disconnect/server stop. Its exhaustive gallery is optional. None of those
  client sessions were run to write this reference.
- **Tiny capacitor-pin targeting remains unresolved.** Passing geometry or
  highlight checks must not be described as proof that every tiny pin can be hit.
- These fixtures do not certify 20 TPS on the 14,650-node yard, every possible
  oil-rig/night layout, arbitrary addons or all unsupported dependency versions.
  Numerical tolerances apply to the named fixtures, not every possible circuit.
- Invalid-permutation/allocation fault injection also exists in
  [the native C safety executable](../tools/native/sparse_safety_tests.c).
  That is a separate focused native check, not one of these Java GameTests or
  proof of general native-memory safety.

## Detailed reference

| Area | Entry points |
| --- | ---: |
| [Mixed circuits, boards, controls and factories](GAMETEST_REFERENCE.md#mixed-circuits-boards-controls-and-factories) | 123 |
| [Stationary accuracy, equipment and instruments](GAMETEST_REFERENCE.md#stationary-accuracy-equipment-and-instruments) | 44 |
| [Distribution, generation and cross-dimension links](GAMETEST_REFERENCE.md#distribution-generation-and-cross-dimension-links) | 21 |
| [Native wires, tools and duplex cables](GAMETEST_REFERENCE.md#native-wires-tools-and-duplex-cables) | 12 |
| [Saved designers and nonlinear lifecycle](GAMETEST_REFERENCE.md#saved-designers-and-nonlinear-lifecycle) | 6 |
| [Loading, preparation and restored measurements](GAMETEST_REFERENCE.md#loading-preparation-and-restored-measurements) | 31 |
| [Rebuild safety, rejection and optimization regressions](GAMETEST_REFERENCE.md#rebuild-safety-rejection-and-optimization-regressions) | 56 |
| [General addon API and ownership](GAMETEST_REFERENCE.md#general-addon-api-and-ownership) | 32 |
| [Native CEE and PG addon extensions](GAMETEST_REFERENCE.md#native-cee-and-pg-addon-extensions) | 6 |
| [Playable world builders and cold reopens](GAMETEST_REFERENCE.md#playable-world-builders-and-cold-reopens) | 4 |
| [Optional Pinout and real Lua computers](GAMETEST_REFERENCE.md#optional-pinout-and-real-lua-computers) | 4 |
| [Upstream reference and environment checks](GAMETEST_REFERENCE.md#upstream-reference-and-environment-checks) | 11 |
| [Separately selected SDK and real-client helpers](GAMETEST_REFERENCE.md#separately-selected-sdk-and-real-client-helpers) | 7 |

### Mixed circuits, boards, controls and factories

123 annotated entry points.

#### AdditionalFactoryGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/AdditionalFactoryGameTests.java).

Reverses a PG current source into a CEE resistor; edits a native PG resistor; presses and releases a CEE momentary switch; changes a shaft-driven rheostat; checks signed three-pole metered energy against the elapsed electrical window.

Entry points:

- [`pgCurrentSourceDrivesAndReversesCeeResistor`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/AdditionalFactoryGameTests.java#L33)
- [`ceeDrivesConfiguredPgPowerResistor`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/AdditionalFactoryGameTests.java#L40)
- [`ceeStandaloneMomentaryPulsesPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/AdditionalFactoryGameTests.java#L46)
- [`pgRheostatShaftDividesCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/AdditionalFactoryGameTests.java#L53)
- [`ceeStandaloneThreePoleMeterReadsPgLoads`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/AdditionalFactoryGameTests.java#L59)

#### BoardComponentGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java).

Powers PG board gauges, switches, buttons, relays, potentiometer, capacitor, inductor, display and barretter from CEE. Checks numerical readings and on/off states, RC decay, released current and counted pulses; these are electrical/component checks, not screen-image checks.

Entry points:

- [`boardVoltageGaugeReadsCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L14)
- [`boardCurrentGaugeReadsCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L22)
- [`boardSwitchControlsCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L30)
- [`boardButtonReleasesCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L43)
- [`boardRedstoneRelaySwitchesCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L50)
- [`boardRelayTransfersMixedLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L57)
- [`boardDoubleRelayTransfersMixedLoads`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L58)
- [`boardPotentiometerDividesCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L78)
- [`boardCapacitorChargesAndDischargesFromCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L87)
- [`boardInductorBuildsAndReleasesCeeCurrent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L101)
- [`boardDisplayCountsCeePulses`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L107)
- [`boardBarretterRegulatesCeeCurrent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardComponentGameTests.java#L114)

#### BoardGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardGameTests.java).

Builds a nonlinear board with native traces, checks its three expected pad-pair bundles and powered input/current, then edits the component configuration and checks the resulting current range.

Entry points:

- [`ceePowersNonlinearBoardAndReconfiguration`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardGameTests.java#L24)

#### BoardWorkflowGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardWorkflowGameTests.java).

Assembles all 28 built-in PG board component types from their native items and checks the restored component identities. Separately powers the designer through a native cord, consumes a blank schematic, assembles/places a board, and checks vias, label properties and mixed-load current.

Entry points:

- [`everyBuiltinBoardComponentAssemblesFromNativeItems`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardWorkflowGameTests.java#L32)
- [`ceePoweredDesignerSavesAndPlacesViaLabelBoard`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardWorkflowGameTests.java#L72)

#### CeeAuxiliaryGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeAuxiliaryGameTests.java).

Checks both indicator channels, redstone variac control, current-transformer winding nodes, held-fuse insertion/tripping and three-phase synchroscope history. Assertions use brightness, voltage, current, energy or phase data rather than rendered images.

Entry points:

- [`pgPowersBothCeeIndicatorChannels`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeAuxiliaryGameTests.java#L33)
- [`ceeRedstoneVariacControlsMixedFactory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeAuxiliaryGameTests.java#L40)
- [`ceeCurrentTransformerMapsInternalWindingNodes`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeAuxiliaryGameTests.java#L48)
- [`ceeHeldFuseNativeInsertionTripsMixedLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeAuxiliaryGameTests.java#L55)
- [`ceeSynchroscopeReceivesMixedThreePhaseHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeAuxiliaryGameTests.java#L66)

#### CeeControlGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeControlGameTests.java).

Operates shaft/redstone potentiometers, a native HV switch and the two-part SF6 breaker. Checks divider changes, player/redstone control, native saved settings, disconnection and recovery of the mixed load.

Entry points:

- [`ceePotentiometerShaftAdjustsPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeControlGameTests.java#L31)
- [`ceeRedstonePotentiometerAdjustsPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeControlGameTests.java#L32)
- [`ceeHvSwitchConnectorAndPlayerControl`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeControlGameTests.java#L58)
- [`ceeSf6BreakerUsesBothBlocksAndRedstone`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeControlGameTests.java#L71)

#### ConfigReloadGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ConfigReloadGameTests.java).

Reloads configuration from background work while a mixed network is active. Requires the background reloads to finish and checks that the queued server-side work leaves the expected powered voltage.

Entry points:

- [`backgroundConfigReloadKeepsMixedNetworkSafe`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ConfigReloadGameTests.java#L19)

#### DimensionGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DimensionGameTests.java).

Builds circuits at identical coordinates in different dimensions. Requires separate world bridges, valid native endpoints and independent electrical results rather than shared coordinate-only ownership.

Entry points:

- [`identicalCoordinatesKeepSeparateDimensionTopology`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DimensionGameTests.java#L25)

#### DisplayGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayGameTests.java).

Installs native PG display modules, drives two CEE pulses and checks the count. Reconfigures/deserializes the module and verifies the saved count is retained; it does not check the displayed pixels.

Entry points:

- [`ceePulsesPgModularDisplayWithNativeModules`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayGameTests.java#L21)

#### DynamicGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java).

Checks transformer ratio and polarity edits, AC RMS, reactive winding current, normal/HV capacitor charge/discharge, inductor history and accumulator charge/discharge. Uses analytical or discrete-history expectations to detect duplicate or missing advancement.

Entry points:

- [`transformerRatioChanges`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L46)
- [`acRmsIntoPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L75)
- [`reactiveAcTransformerKeepsWindingCurrent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L95)
- [`capacitorChargesOnceAndDischarges`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L132)
- [`highVoltageCapacitorChargesOnceAndDischarges`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L135)
- [`inductorAdvancesOnceIntoPgResistor`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L182)
- [`accumulatorChargesFromPgAndPowersPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DynamicGameTests.java#L213)

#### ElectronicsPersistenceGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ElectronicsPersistenceGameTests.java).

Saves/reopens a powered panel and board, checking capacitor charge before first resumed solving, component UUID, assigned terminal IDs, labels, wire counts and delayed entity readiness. Then checks load readings, discharge and recharge.

Entry points:

- [`restartRetainsPanelTerminalsBoardIdentityAndCharge`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ElectronicsPersistenceGameTests.java#L34)

#### FactoryGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryGameTests.java).

Charges a native portable PG battery from CEE, checks no charge without solved voltage, saved charge and capacity; powers CEE light/heat/pump from PG; trips/repairs a CEE fuse and checks actual shutdown/recovery of the PG load.

Entry points:

- [`ceeChargesPgPortableBattery`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryGameTests.java#L25)
- [`pgPowersCeeLightHeatAndPump`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryGameTests.java#L52)
- [`ceeFuseTripsAndRepairRestoresPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryGameTests.java#L82)

#### FactoryTransportGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryTransportGameTests.java).

Pumps actual water through native CEE and PG pumps supplied by the other mod. Checks transferred fluid and that transfer stops after power removal, rather than only checking an electrical input value.

Entry points:

- [`pgPowerPumpsWaterThroughCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryTransportGameTests.java#L22)
- [`ceePowerPumpsWaterThroughPg`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FactoryTransportGameTests.java#L23)

#### GenerationGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GenerationGameTests.java).

Mechanically drives actual CEE alternators into PG loads. Checks rotation, output response to speed, shutdown, balanced three-phase relationships and phase-sequence reversal when the drive reverses.

Entry points:

- [`assembledCeeAlternatorPowersPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GenerationGameTests.java#L35)
- [`assembledThreePhaseGenerationPreservesPhaseAndDirection`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GenerationGameTests.java#L62)

#### GroundingChangeGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GroundingChangeGameTests.java).

Adds/removes physical grounds and changes preferred references without replacing unrelated branch identities. An actual ground-rod chunk unload/reload must update earth-return voltage and restore the physical reference.

Entry points:

- [`physicalGroundChangesKeepExistingTopology`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GroundingChangeGameTests.java#L36)
- [`preferredReferenceChangesAndPhysicalGroundTakeover`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GroundingChangeGameTests.java#L54)
- [`groundRodChunkUnloadReloadUpdatesExistingCircuit`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GroundingChangeGameTests.java#L73)

#### GroundingGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GroundingGameTests.java).

Connects the two native grounding systems and checks physical ground resistance, source polarity and takeover after a ground is removed. Ensures a synthetic reference does not override the real return path.

Entry points:

- [`bothNativeGroundingSystemsSharePhysicalReference`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/GroundingGameTests.java#L21)

#### HandheldMeterGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/HandheldMeterGameTests.java).

Uses actual multimeter/clamp handlers on CEE and mixed terminals/wires. Checks numerical updates, retained selected wire identity, attachment/release, and clearing readings/selections after a terminal or wire is removed.

Entry points:

- [`multimeterReadsCeeAndMixedTerminals`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/HandheldMeterGameTests.java#L35)
- [`multimeterReadsCeeWireAndClearsCutConnection`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/HandheldMeterGameTests.java#L63)
- [`clampAttachesToPgWireAndReleases`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/HandheldMeterGameTests.java#L91)

#### IsolationGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/IsolationGameTests.java).

Introduces unknown and nonfinite CEE electrical properties, checks only that circuit is isolated, and repairs it. Requires no stale downstream power/heat or corrupted wire thermal history while an unrelated healthy circuit keeps operating.

Entry points:

- [`unknownPropertyIsolatesAndRecoversWithoutStalePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/IsolationGameTests.java#L30)
- [`nonfinitePropertyIsolatesAndRecoversWithoutStalePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/IsolationGameTests.java#L31)

#### LifecycleGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LifecycleGameTests.java).

Powers CEE source/load through PG, changes source voltage without recreating stable PG objects, checks bridged voltage/current and removes the source to ensure no ghost power remains.

Entry points:

- [`ceeSourceAndLoadUsePg`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LifecycleGameTests.java#L23)

#### MechanicalGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MechanicalGameTests.java).

Checks CEE motor RMS sampling uses PG's electrical schedule, mechanical capacity and direction follow mixed supply, and motor output stops without power. Also changes a variac and checks the PG load voltage.

Entry points:

- [`ceeMotorRmsHistoryUsesPgSubsteps`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MechanicalGameTests.java#L25)
- [`mixedMotorsRespondToPowerAndPolarity`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MechanicalGameTests.java#L38)
- [`ceeVariacControlsPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MechanicalGameTests.java#L85)

#### MeterGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MeterGameTests.java).

Checks standalone CEE meters and PG voltage/current/power/energy gauges against solved mixed loads, exactly-once energy integration and disconnect behavior. Also checks frequency under the 16-substep clock and native CEE diode rectification.

Entry points:

- [`standaloneCeeMetersReadPgLoadAndDisconnect`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MeterGameTests.java#L22)
- [`pgGaugesAndEnergyMeterReadCeeSupply`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MeterGameTests.java#L54)
- [`frequencyMeterUsesPgSubstepClock`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MeterGameTests.java#L81)
- [`cee113DiodeRectifiesNativePgPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MeterGameTests.java#L98)

#### NonlinearBoardGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java).

Powers native board varistor, neon/regulator tubes, bulb, fuse, NPN/PNP transistors, VFET, triode, pentode and thyratron from CEE. Checks leakage/clamping, strike/hold/extinction, heat, repair, gain and heater/grid/gate-controlled conduction.

Entry points:

- [`boardVaristorClampsCeeSupply`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L20)
- [`boardNeonStrikesAndExtinguishesFromCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L29)
- [`boardRegulatorTubeStrikesAndExtinguishesFromCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L30)
- [`boardLightBulbHeatsAndCoolsFromCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L43)
- [`boardFuseTripsAndPlayerRepairsCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L54)
- [`boardNpnAmplifiesCeeBaseCurrent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L74)
- [`boardPnpAmplifiesCeeBaseCurrent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L75)
- [`boardVfetRespondsToCeeGateBias`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L87)
- [`boardTriodeHeaterAndGridUseCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L94)
- [`boardPentodeHeaterAndGridUseCeePower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L95)
- [`boardThyratronHeaterTriggerAndExtinctionUseCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NonlinearBoardGameTests.java#L107)

#### PanelGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java).

Installs/operates native CEE panel controls and meters in PG factories. Checks redstone-link and saved control state, stop/reset/momentary/breaker behavior, live gauges/indicators, energy/current, sparse assigned terminals and three-pole readings.

Entry points:

- [`analogPanelControlsRetainNativeInteractionAndLinkState`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java#L31)
- [`panelControlsAndMonitorsPgFactory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java#L83)
- [`panelPlayerStopAndMomentaryControl`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java#L134)
- [`panelBreakerTripsAndPlayerResets`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java#L177)
- [`panelEnergyMeterAndAmmeterReadMixedLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java#L212)
- [`panelTriPolarMeterUsesAssignedTerminals`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PanelGameTests.java#L246)

#### PersistenceGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PersistenceGameTests.java).

Checks mixed endpoints, both native wire systems and capacitor history through actual unload/reload and a separate cold-restart path. Delays saved wire entities, verifies their identities and the entity-readiness grace period, then requires restored power.

Entry points:

- [`chunkReloadRebindsMixedEndpoints`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PersistenceGameTests.java#L36)
- [`restartPreservesBothWireSystemsAndCapacitor`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PersistenceGameTests.java#L119)

#### PgFactoryGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgFactoryGameTests.java).

Powers native PG lights, heating coil/basin, configurable constant-speed motor and servo from CEE. Checks real light/heat/capacity/target angle, polarity/settings persistence and shutdown or cooling after power removal.

Entry points:

- [`ceePowersPgNativeLightFixtures`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgFactoryGameTests.java#L35)
- [`ceePowersPgNativeHeatingAndBasinState`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgFactoryGameTests.java#L53)
- [`ceePowersPgConstantSpeedMotorConfiguration`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgFactoryGameTests.java#L69)
- [`ceeControlsPgServoAngleAndSavedState`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgFactoryGameTests.java#L77)

#### PgGenerationGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgGenerationGameTests.java).

Uses CEE to excite a native PG winding/generator and checks output versus drive speed and loss of excitation. A native solar fixture checks real CEE load voltage and reduction under shade; this is not a full oil-rig/night scenario.

Entry points:

- [`ceeExcitesPgWindingAndGeneratorPowersCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgGenerationGameTests.java#L27)
- [`nativePgSolarPowersCeeAndRespondsToShade`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgGenerationGameTests.java#L58)

#### PgTransformerGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgTransformerGameTests.java).

Assembles/winds small and medium PG transformers with native copper consumption, checks step-up/down voltage including native impedance, saved turns, reconfiguration and removal. Also operates a mechanical PG variac and checks the CEE load.

Entry points:

- [`assembledSmallTransformerStepUp`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgTransformerGameTests.java#L39)
- [`assembledSmallTransformerStepDown`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgTransformerGameTests.java#L40)
- [`assembledMediumTransformerStepUp`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgTransformerGameTests.java#L41)
- [`assembledMediumTransformerStepDown`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgTransformerGameTests.java#L42)
- [`pgVariacMechanicallyAdjustsCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PgTransformerGameTests.java#L91)

#### ProtectionGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ProtectionGameTests.java).

Operates native PG HV/LV protection using CEE power: mechanical HV switch, fuse overload/survival repair, both contactor contacts and the HV breaker charge/close/trip/reset cycle. Checks actual downstream voltage and native repair costs.

Entry points:

- [`pgHvSwitchMechanicalAssemblyControlsCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ProtectionGameTests.java#L34)
- [`pgFuseTripsAndAcceptsSurvivalRepair`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ProtectionGameTests.java#L54)
- [`pgContactorUsesCeeCoilPowerAndBothContacts`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ProtectionGameTests.java#L75)
- [`pgHvBreakerChargesClosesTripsAndRecovers`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ProtectionGameTests.java#L89)

#### RegistryGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RegistryGameTests.java).

Checks the published supported registry inventory and cosmetic variants exist in the pinned dependencies. In packaged mode it also requires a jar-backed WWPG mod. Registry presence alone does not establish that every listed item operates correctly.

Entry points:

- [`releaseInventoryMatchesPublishedRegistries`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RegistryGameTests.java#L20)

#### RestorationGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RestorationGameTests.java).

Serializes a charged native board before a first solved terminal reading and verifies cold ticking cannot overwrite charge with zero. A genuinely missing wire must wait for delayed entity readiness, receive its full cleanup grace period, then expire.

Entry points:

- [`coldBoardSavePreservesUnsolvedCapacitorHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RestorationGameTests.java#L22)
- [`missingWireWaitsForEntitiesThenExpires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RestorationGameTests.java#L51)

#### StandaloneControlGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java).

Operates native CEE cutoff/double/emergency-stop switches and relays, PG LV/MV switches and momentary buttons. Checks both controlled lines, actual player or redstone input, release/reset and restored load voltage.

Entry points:

- [`ceeCutoffSwitchControlsPgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L30)
- [`ceeDoubleSwitchControlsBothPgLines`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L31)
- [`ceeEmergencyStopAndPlayerReset`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L32)
- [`pgLvSwitchControlsCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L33)
- [`pgMvSwitchControlsCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L34)
- [`pgButtonReleasesCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L35)
- [`ceeRelayCoilControlsPgFactoryLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L68)
- [`ceeRedstoneRelayUsesRealWorldInput`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StandaloneControlGameTests.java#L86)

#### StartupGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StartupGameTests.java).

Creates a controlled concurrent registration/publication sequence. Requires worker completion, no callback-count corruption and retention of the actual callback; this targets the startup race rather than general thread-safety certification.

Entry points:

- [`registrationKeepsConcurrentCallbacks`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StartupGameTests.java#L35)

#### StationaryEndpointGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEndpointGameTests.java).

Routes mixed power through PG floor/ceiling cord junctions and native connector families. Checks load voltage and loss of power after the junction/connector is removed.

Entry points:

- [`ceePowerCrossesPgCordJunction`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEndpointGameTests.java#L21)
- [`ceePowerCrossesPgCeilingCordJunction`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEndpointGameTests.java#L22)
- [`stationaryConnectorFamiliesRetainMixedTerminalWiring`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEndpointGameTests.java#L30)

#### SurvivalWiringGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SurvivalWiringGameTests.java).

Uses native survival interactions for PG wire types and CEE spool types on mixed terminals. Checks material consumption, resistance, duplicate rejection, empty-spool return, powered load and correct player cut/recovery.

Entry points:

- [`pgWireTypesRetainCostResistanceAndPlayerCut`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SurvivalWiringGameTests.java#L28)
- [`ceeSpoolTypesRetainCostAndPlayerRecoveryOnPg`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SurvivalWiringGameTests.java#L66)

#### TopologyGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/TopologyGameTests.java).

Edits a mixed branch/loop circuit, cuts/reconnects connections and replaces/removes devices. Checks the expected numerical result after each topology change rather than only counting discovered nodes.

Entry points:

- [`mixedBranchesLoopCutReconnectAndRemoval`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/TopologyGameTests.java#L23)

#### WireFailureGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireFailureGameTests.java).

Overloads a real CEE wire until it breaks, checks the PG load is electrically isolated, then repairs the connection and requires powered recovery.

Entry points:

- [`ceeWireOverloadBreaksAndRepairRejoinsPgCircuit`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireFailureGameTests.java#L19)

#### WiringGameTests

Namespace: `wwpg`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java).

Six basic native-placement combinations connect PG/CEE sources and loads using either mod's wire handler. Checks accepted wiring and the expected loaded voltage; these are powered interoperability tests, not just block placement.

Entry points:

- [`ceeToPgWithPgWire`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java#L32)
- [`pgToCeeWithPgWire`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java#L33)
- [`ceeToPgWithCeeSpool`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java#L34)
- [`pgToCeeWithCeeSpool`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java#L35)
- [`ceeToCeeWithPgWire`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java#L36)
- [`pgToPgWithCeeSpool`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WiringGameTests.java#L37)

### Stationary accuracy, equipment and instruments

44 annotated entry points.

#### DiagnosticsGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DiagnosticsGameTests.java).

Queries real PG circuits and checks reported backend/readiness and native component/panel identities. Probes unloaded coordinates and verifies the diagnostic does not load the chunk or invent a reading or compatibility circuit.

Entry points:

- [`diagnosticsReadNativePgModelsWithoutCreatingCompatibilityTopology`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DiagnosticsGameTests.java#L23)
- [`diagnosticOfUnloadedCoordinatesDoesNotLoadChunksOrInventReadings`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DiagnosticsGameTests.java#L49)
- [`diagnosticsIdentifyPanelSlotsAndStableBoardComponentPads`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DiagnosticsGameTests.java#L59)

#### FrequencyTimingGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FrequencyTimingGameTests.java).

Seeds even/odd saved frequency and synchroscope sample counts, changes source frequency/phase and checks the reported instruments update. Targets historical counter/sample-window errors rather than client rendering.

Entry points:

- [`evenSavedFrequencySampleCountUpdates`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FrequencyTimingGameTests.java#L21)
- [`oddSavedFrequencySampleCountUpdates`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FrequencyTimingGameTests.java#L23)
- [`oddSavedSynchroscopeSampleCountUpdatesAfterPhaseChange`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FrequencyTimingGameTests.java#L26)

#### FuseHolderWorkflowGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseHolderWorkflowGameTests.java).

Uses native held copper bypasses, switches, indicators and fuses; checks material consumption/return, independent slots, indicator properties, trip/repair and unchanged wire identities. The persistence fixture also reopens and performs five actual chunk cycles.

Entry points:

- [`nativeCopperBypassReplacementAndHeldSwitchOperateMixedLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseHolderWorkflowGameTests.java#L46)
- [`nativeHeldIndicatorColorBrightnessAndIndependentSlotsSurviveSerialization`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseHolderWorkflowGameTests.java#L70)
- [`nativeHeldFuseTripsReturnsBrokenItemAndReplacementReusesWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseHolderWorkflowGameTests.java#L92)
- [`allNativeHeldComponentsSurviveColdRestartAndFiveActualChunkCycles`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseHolderWorkflowGameTests.java#L110)

#### FuseVariantGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseVariantGameTests.java).

Repeatedly overloads and repairs a native fuse while retaining a PG wire and a CEE wire. Checks broken state, rating/type, item behavior, retained wire identities and powered voltage; physically removing the fuse must retire both connections.

Entry points:

- [`nativeFuseFailureAndRepeatedRepairRetainBothWireSystems`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/FuseVariantGameTests.java#L31)

#### StationaryAccuracyGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java).

Uses explicit tolerances for mixed-wire DC dividers and signed current (0.1%), transformer impedance/current, RC/RL windows (1%), AC RMS/phase and discrete reactive waveforms. Also checks accumulator energy and repeated small conductance edits against independent equations.

Entry points:

- [`ceeSourcePgLoadPgWiresMeetPointOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L43)
- [`ceeSourcePgLoadCeeWiresMeetPointOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L45)
- [`pgSourceCeeLoadPgWiresMeetPointOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L47)
- [`pgSourceCeeLoadCeeWiresMeetPointOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L49)
- [`dcSourceDividerAndSignedCurrentMeetPointOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L113)
- [`transformerReflectedImpedanceAndCurrentMeetPointOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L139)
- [`pinnedCapacitorChargeDischargeWindowsMeetOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L168)
- [`pinnedInductorChargeDischargeWindowsMeetOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L208)
- [`pinnedAcSamplesRmsAndQuarterPhaseMatchAnalyticalReference`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L248)
- [`reactiveTransformerSamplesPhaseRmsAndWindingCurrentsMatchDiscreteReference`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L279)
- [`pinnedAccumulatorChargeDeltasMatchIndependentEquationWithinOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L333)
- [`repeatedSmallPropertyConductanceChangesKeepTheMatrixConsistent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryAccuracyGameTests.java#L391)

#### StationaryConversionGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryConversionGameTests.java).

Moves actual FE through CEE conversion and PG inverter fixtures. Checks provider depletion, full receivers, powered native output, stored FE across reload and an inverter's once-per-game-tick energy debit rather than once-per-substep debit.

Entry points:

- [`nativeConverterPullsFeAndPowersPgWithConservedEnergy`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryConversionGameTests.java#L31)
- [`nativeConverterPushesFeAndHonorsFullReceiver`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryConversionGameTests.java#L36)
- [`nativeFeConvertersExchangeMixedPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryConversionGameTests.java#L123)
- [`nativeInverterAdvancesEnergyOncePerGameTick`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryConversionGameTests.java#L184)

#### StationaryCrtGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryCrtGameTests.java).

Powers native, andesite and brass CRT models. Checks heater and electron-gun current, X/Y deflection current, saved trace color, grid/heater cutoff and recovered current; headless assertions do not verify the rendered trace.

Entry points:

- [`mixedPowerControlsNativeCrt`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryCrtGameTests.java#L24)
- [`mixedPowerControlsAndesiteCrt`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryCrtGameTests.java#L26)
- [`mixedPowerControlsBrassCrt`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryCrtGameTests.java#L28)

#### StationaryEquipmentGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java).

Checks buzzer voltage and a safe public server tick, PG fan direction/airflow, electromagnet field, bell volume, acid-battery charge/discharge/save/capacity, nonrechargeable potato-battery behavior, redstone conversion and spark-gap on/off state. Actual audible playback is a graphical check.

Entry points:

- [`pgPowerSoundsCeeBuzzer`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java#L34)
- [`ceePowerRunsPgFanMagnetAndBell`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java#L58)
- [`stationaryBatteriesChargeAndDischargeWithCee`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java#L107)
- [`potatoBatteryPreservesNativeNonRechargeableBehavior`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java#L161)
- [`nativeRedstoneConverterControlsCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java#L194)
- [`mixedVoltageTriggersAndExtinguishesNativeSparkGap`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryEquipmentGameTests.java#L224)

#### StationaryInstrumentGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryInstrumentGameTests.java).

Checks thermometer current/max/reset values against real heat, plotter storage of a signed normalized waveform and carbon-pile trim/resistance/removal. Plotter drawing and instrument appearance need separate client evidence.

Entry points:

- [`pgThermometerReadsCeePoweredPgHeat`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryInstrumentGameTests.java#L33)
- [`nativePlotterRecordsMixedWaveform`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryInstrumentGameTests.java#L73)
- [`ceeControlsNativeCarbonPileResistance`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryInstrumentGameTests.java#L110)

#### StationaryInventoryGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryInventoryGameTests.java).

Compares the entire pinned stationary registry to its published accounting record and writes profile-specific inventory coverage. Optional Pinout entries are excluded when absent; a matching inventory is not a powered-function test of every entry.

Entry points:

- [`recordCompletePinnedStationaryInventory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryInventoryGameTests.java#L29)

#### StationaryLightingGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryLightingGameTests.java).

Repairs/re-breaks native bulbs without rewiring, checking repair ingredients and preserved wire IDs. Tests ceiling-solar shade/removal while retaining the base tile, and a growth lamp's actual crop effect.

Entry points:

- [`nativeBrokenBulbRepairRestoresMixedPowerWithoutRewiring`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryLightingGameTests.java#L40)
- [`nativeCeilingSolarAssemblyPowersCeeAndRespondsToShade`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryLightingGameTests.java#L107)
- [`ceePowerRunsNativeGrowthLampAndCropEffect`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryLightingGameTests.java#L150)

#### StationaryReaderGameTests

Namespace: `wwpg_stationary`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryReaderGameTests.java).

Inserts a native punch card, scans every row and independently checks all eight mixed outputs. Removes the card and requires cleared output and correct card consumption/state.

Entry points:

- [`nativePunchCardScansEveryRowAndControlsEightMixedLoads`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryReaderGameTests.java#L34)

### Distribution, generation and cross-dimension links

21 annotated entry points.

#### ElectricalScheduleGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ElectricalScheduleGameTests.java).

Runs a native split controller at one, two and sixteen substeps. Checks both halves advance the requested number of times, preserve paired ownership and polarity, and stop delivering voltage after shutdown.

Entry points:

- [`splitControllerPreservesPolarityAndStopsAtOneTwoAndSixteenSubsteps`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ElectricalScheduleGameTests.java#L18)

#### NetherLifecycleGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NetherLifecycleGameTests.java).

Saves both linked Nether transformer ends, their wire decorations and electrical history; cold-reopens them and unloads/reloads each end independently five times. Checks delayed saved entities, stable wire IDs and recovered transfer.

Entry points:

- [`linkedNetherEndsSurviveColdRestartAndFiveIndependentActualChunkCycles`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NetherLifecycleGameTests.java#L53)

#### StationaryDistributionGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryDistributionGameTests.java).

Assembles a native CEE transformer core/radiators and voltage regulator, checking turns, losses, cooling, neutral ratio and regulation. A dedicated check preserves every saved Nether-transformer history sample; dismantling must leave no ghost supply.

Entry points:

- [`nativeCeeCoreAssemblyTransformsMixedPowerAndUsesRadiators`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryDistributionGameTests.java#L38)
- [`netherTransformerRestoresEverySavedHistorySample`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryDistributionGameTests.java#L95)
- [`nativeRegulatorStartsAtNeutralAndRegulatesMixedLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryDistributionGameTests.java#L116)

#### StationaryGeneratorWorkflowGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryGeneratorWorkflowGameTests.java).

Installs native coils with actual item consumption, excites large/vertical generators and checks output changes with drive speed. Builds/splits horizontal/vertical housings while preserving directly supplied winding ownership and native remanent field behavior.

Entry points:

- [`nativeLargeGeneratorUsesInstalledCoilAndMixedExcitation`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryGeneratorWorkflowGameTests.java#L32)
- [`nativeVerticalGeneratorUsesInstalledCoilAndMixedExcitation`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryGeneratorWorkflowGameTests.java#L35)
- [`nativeHorizontalHousingJoinsAndSeparatesExcitedWindings`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryGeneratorWorkflowGameTests.java#L90)
- [`nativeVerticalHousingJoinsAndSeparatesExcitedWindings`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryGeneratorWorkflowGameTests.java#L93)

#### StationaryNetherGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryNetherGameTests.java).

Transfers mixed power through a real native portal link and a PG-only control. Removes/reconnects sources, unlinks/relinks and disrupts the portal, checking cached-current teardown and no ghost power in either dimension.

Entry points:

- [`nativePortalLinkTransfersMixedPowerAndStopsAfterSourceAndPortalRemoval`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryNetherGameTests.java#L34)
- [`pgOnlyPortalReferenceStopsAfterSourceAndPortalRemoval`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryNetherGameTests.java#L38)

#### StationaryPoleGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java).

Builds two- through five-segment native poles, places/styles/recovers catenary wire and operates a mounted breaker through native insulators. Checks mixed voltage, placement cost, physical break shutdown and redstone control.

Entry points:

- [`nativeTwoSegmentPoleCarriesMixedPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java#L35)
- [`nativeThreeSegmentPoleCarriesMixedPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java#L36)
- [`nativeFourSegmentPoleCarriesMixedPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java#L37)
- [`nativeFiveSegmentPoleCarriesMixedPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java#L38)
- [`nativeCatenarySpoolStylesAndRecoveryCarryMixedPower`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java#L40)
- [`nativePoleMountAndInsulatorControlMountedMixedBreaker`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryPoleGameTests.java#L156)

#### StationaryProcessingGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryProcessingGameTests.java).

Uses powered native equipment to transform real items: electromagnet alloy-to-magnet processing and fan flour-to-dough washing. Checks input consumption/output items and that the unpowered magnet stops processing.

Entry points:

- [`mixedSupplyMagnetizesNativeRecipeAndStopsUnpowered`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryProcessingGameTests.java#L28)
- [`mixedSupplyFanPerformsNativeWashingRecipe`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryProcessingGameTests.java#L60)

#### StationaryStringLightGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryStringLightGameTests.java).

Crafts a dyed native string cord, places and powers it, checks actual filament light output and retained construction cost, then cuts it and verifies returned materials and no ghost power.

Entry points:

- [`nativeColoredStringCordLightsMixedCircuitAndReturnsCuttingCost`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryStringLightGameTests.java#L29)

#### WireAttachmentGameTests

Namespace: `wwpg_distribution`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireAttachmentGameTests.java).

Installs/styles/removes native wire attachments on a mixed circuit. Checks saved attachment state, item cost and overlap rejection, retained voltage, wrench recovery and no remaining supply after the decorated wire is cut.

Entry points:

- [`nativeWireAttachmentsInstallRemoveAndPersist`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireAttachmentGameTests.java#L36)

### Native wires, tools and duplex cables

12 annotated entry points.

#### BundledWireGameTests

Namespace: `wwpg_bundle`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BundledWireGameTests.java).

Exercises real duplex spool interactions: length rejection, cancellation, stale selections, orientation/node IDs, duplicate placement, two derived conductors, item costs and recovery. Checks powered voltage and loss of power after removal.

Entry points:

- [`duplexLimitsCancellationStaleSelectionAndBlockRemovalRetainNativeCosts`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BundledWireGameTests.java#L22)
- [`nativeBundleNodeHelpersMatchEveryRegisteredOrientation`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BundledWireGameTests.java#L62)
- [`nativeDuplexSpoolConnectsBothConductorsAndRecoversOnce`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BundledWireGameTests.java#L74)

#### BundledWireLifecycleGameTests

Namespace: `wwpg_bundle`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BundledWireLifecycleGameTests.java).

Keeps a duplex cable and a colored LV bulb through five actual chunk unload/reload cycles with delayed wire entities and a cold restart. Checks cable/wire identities, bulb state, saved thermal properties and restored 12 V power.

Entry points:

- [`duplexAndLvBulbRetainNativeStateAcrossFiveActualCyclesAndColdRestart`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BundledWireLifecycleGameTests.java#L35)

#### CeeTerminalGeometryGameTests

Namespace: `wwpg_wires`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeTerminalGeometryGameTests.java).

Uses a powered sparse-ID CEE panel; rotates it, edits/removes attachments and queries terminal geometry. Checks live IDs, removal of stale measurements, retained panel ownership and no unrelated capacitor-history advancement. It does not fire a client targeting ray.

Entry points:

- [`poweredSparsePanelOperationsObserveLiveGeometry`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeeTerminalGeometryGameTests.java#L46)

#### NativeBulbItemsGameTests

Namespace: `wwpg_wires`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeBulbItemsGameTests.java).

Installs/removes/reinstalls native PG bulb items with dye, temperature and failure state. Checks item cost/return, serialized state, powered light, cooling, burnout and successful replacement without recreating an unchanged CEE source.

Entry points:

- [`nativePgBulbItemsRetainMixedLightAndReplacement`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeBulbItemsGameTests.java#L20)

#### NativeWireToolsGameTests

Namespace: `wwpg_wires`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeWireToolsGameTests.java).

Uses a lineman's stick with an offhand clamp/attachments and the native hanging-insulator spool on fresh PG terminals. Checks accepted interaction, current, persistent wire identity, item recovery/cost, loose-end continuation and powered resistance.

Entry points:

- [`nativeLinemansStickWithOffhandClampMeasuresPgWire`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeWireToolsGameTests.java#L23)
- [`nativeHangingInsulatorSpoolRecognizesFreshPgTerminal`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeWireToolsGameTests.java#L45)
- [`nativeLinemansStickInstallsAndReturnsOffhandAttachments`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeWireToolsGameTests.java#L90)

#### StationaryWireFamiliesGameTests

Namespace: `wwpg_wires`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryWireFamiliesGameTests.java).

Exercises every pinned stationary spool family and PG iron wire with native endpoint restrictions. Checks electrical resistance, item cost, duplicate-placement rejection, type-correct recovery and lost power after cutting.

Entry points:

- [`allStationaryWireFamiliesRetainNativeRules`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryWireFamiliesGameTests.java#L24)
- [`pgIronWireUsesCeeTerminalsWithoutChangingNativePgRestrictions`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryWireFamiliesGameTests.java#L69)

#### WireResultOwnershipGameTests

Namespace: `wwpg_wires`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireResultOwnershipGameTests.java).

Uses the actual result selector with healthy native ordering, rejected endpoints and real cut nodes. Requires the healthy path to return the original connection set and verifies filtering does not mutate native membership.

Entry points:

- [`healthyNativeOrderAndRejectedEndpointsAndCutsUseTheActualSelector`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireResultOwnershipGameTests.java#L36)

### Saved designers and nonlinear lifecycle

6 annotated entry points.

#### CoupledNodeRemovalGameTests

Namespace: `wwpg_coupled`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CoupledNodeRemovalGameTests.java).

Removes a conducting pentode with a reduced control node and checks surviving ownership/results and no independent CEE solve. A second fixture checks a nonlinear pentode assembly and stable native wire identities through five real chunk cycles and cold restart.

Entry points:

- [`poweredPentodeRemovalWithReducedControlNodeKeepsRemainingCircuitValid`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CoupledNodeRemovalGameTests.java#L33)
- [`mixedPentodeAssemblySurvivesFiveActualChunkCyclesAndColdRestart`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CoupledNodeRemovalGameTests.java#L84)

#### DesignerChunkCyclesGameTests

Namespace: `wwpg_design`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignerChunkCyclesGameTests.java).

Keeps a saved designer, inventory and two-wire native cord through five actual unload/reload cycles with delayed entity readiness. Checks that the saved design and wire identities survive and power returns.

Entry points:

- [`savedDesignerAndCordSurviveFiveActualChunkCycles`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignerChunkCyclesGameTests.java#L35)

#### DesignerRestartGameTests

Namespace: `wwpg_design`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignerRestartGameTests.java).

Reopens a saved design table after cold restart, copies/writes its schematic, assembles and places the native board, and checks the resulting via/label circuit current. This is a server-side workflow, not a GUI interaction test.

Entry points:

- [`savedDesignReopensCopiesAssemblesAndPowersAfterRestart`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignerRestartGameTests.java#L43)

#### DesignTablePersistenceGameTests

Namespace: `wwpg_design`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignTablePersistenceGameTests.java).

Checks that native inventory edits schedule a world save and that designer data can deserialize before a world is assigned without losing inventory. The latter is a cold-deserialization check, not a whole-server restart by itself.

Entry points:

- [`designerInventoryChangesScheduleAWorldSave`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignTablePersistenceGameTests.java#L18)
- [`savedDesignerLoadsBeforeWorldAssignment`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DesignTablePersistenceGameTests.java#L31)

### Loading, preparation and restored measurements

31 annotated entry points.

#### BoardStartupGameTests

Namespace: `wwpg_board_startup`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardStartupGameTests.java).

Delegates to the 28-component native assembly audit, with its own accepted 10 V mixed circuit. Its separate namespace proves that the audit does not depend on another fixture having initialized a circuit first.

Entry points:

- [`assemblyAuditHasItsOwnPoweredCircuit`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/BoardStartupGameTests.java#L15)

#### CeePreparationGameTests

Namespace: `wwpg_cee_preparation`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java).

A focused wrapper selection of existing transformer, capacitor, inductor, accumulator, grounding, topology and invalid-property fixtures. These 12 declarations deliberately rerun those fixtures; they are not 12 additional independent device models.

Entry points:

- [`transformerRatioChanges`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L17)
- [`reactiveTransformerCurrents`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L19)
- [`capacitorHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L21)
- [`highVoltageCapacitorHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L23)
- [`inductorHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L25)
- [`accumulatorHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L27)
- [`physicalGroundChanges`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L29)
- [`referenceAndGroundTakeover`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L31)
- [`groundUnloadReload`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L33)
- [`loopCutReplacementAndReconnect`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L35)
- [`unsupportedPropertyRecovery`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L37)
- [`nonfinitePropertyRecovery`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CeePreparationGameTests.java#L39)

#### HealthyResultGuardGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/HealthyResultGuardGameTests.java).

Adds an unmapped descriptor beside a healthy CEE circuit. Checks the unsupported result stays unavailable, the healthy load survives and the next frame drops the obsolete descriptor; it is not a performance benchmark.

Entry points:

- [`unmappedDescriptorsRemainUnavailableBesideHealthyCircuits`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/HealthyResultGuardGameTests.java#L31)

#### LoadingBatchGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java).

Exercises warm-up and delayed-restoration windows, indexed strong/weak/ground/port discovery, unrelated-factory independence and resumed storage. Checks unavailable light/heat/pump results, no repeated converter FE transfer and held native battery/inverter history while loading.

Entry points:

- [`convergedWarmupHoldsCeeStorageWithoutFalseSolverErrors`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L36)
- [`indexedDiscoveryPreservesWeakLinesGroundsAndPorts`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L88)
- [`restoringCircuitPreservesChargeAndLeavesOtherFactoryRunning`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L112)
- [`restoringCircuitCannotKeepLightHeatOrPumpPowered`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L163)
- [`restorationDoesNotRepeatConverterFeTransfers`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L179)
- [`unownedUnscheduledChunkCannotKeepRestorationActive`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L196)
- [`restorationPreservesNativeBatteryAndInverterHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/LoadingBatchGameTests.java#L207)

#### NativeSamplingGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeSamplingGameTests.java).

Moves an interpolated terminal between one-owner and weakly coupled two-owner circuits. Checks endpoint waveform ownership, incomplete/late-rejected frame unavailability, cleanup of temporary devices and preservation of an unrelated circuit.

Entry points:

- [`middleReadingsFollowBothNetworkOwnersAndRejectIncompleteFrames`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeSamplingGameTests.java#L49)

#### NativeTerminalPreparationGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeTerminalPreparationGameTests.java).

Compares prepared IDs against original native terminal callbacks across more than 100 block states, checks per-pass reuse and custom sparse/mutable/error callbacks, and powers small/multiblock transformers to verify remapped terminals and readings.

Entry points:

- [`everyNativeBlockStateRetainsOriginalTerminalIds`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeTerminalPreparationGameTests.java#L25)
- [`customSparseGeometryAndFailuresKeepOriginalCallbacks`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeTerminalPreparationGameTests.java#L48)
- [`nativeSmallTransformerKeepsPoweredReadings`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeTerminalPreparationGameTests.java#L71)
- [`nativeMultiblockTransformerKeepsRemappedTerminalIds`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeTerminalPreparationGameTests.java#L76)

#### PentodeInitializationGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PentodeInitializationGameTests.java).

Reproduces the pinned solver's cold pentode limiter rejection, checks native storage stays held and solving resumes within the bounded initialization allowance. Persistent and later failures must remain rejected rather than being hidden as startup.

Entry points:

- [`upstreamPentodeNeedsMultipleColdStepsWithoutAdvancingStorage`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PentodeInitializationGameTests.java#L38)
- [`persistentPentodeFailureExhaustsItsInitializationAllowance`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PentodeInitializationGameTests.java#L61)

#### PropertyPowerGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PropertyPowerGameTests.java).

Pauses a powered property wire, requires its reading/power to be unavailable and its stamp owner unchanged, then reverses and zeros the source. Checks signed recovered power and valid zero-power measurement availability.

Entry points:

- [`propertyPowerIsMaskedDuringRestorationAndResumesWithSignedSupply`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PropertyPowerGameTests.java#L30)

#### RestorationPauseEpochGameTests

Namespace: `wwpg_pause_epoch`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RestorationPauseEpochGameTests.java).

Checks pause marks stay local to a network, persist through same-tick preparation, expire next tick, propagate to coupled peers and clear on teardown. A new epoch must not resurrect an old mark.

Entry points:

- [`networkMarksExpireAcrossTicksAndTeardown`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/RestorationPauseEpochGameTests.java#L14)

#### StableBranchGameTests

Namespace: `wwpg_loading`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StableBranchGameTests.java).

Changes retained CEE source/resistor parameters, including current direction, and checks expected accepted voltage/current. Requires unchanged branch/network ownership and bit-identical values in a previously published result snapshot.

Entry points:

- [`retainedDescriptionAppliesStaticAndChangedParametersWithoutReplacingItsBranch`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StableBranchGameTests.java#L30)

### Rebuild safety, rejection and optimization regressions

56 annotated entry points.

#### CircuitRebuildGameTests

Namespace: `wwpg_unload`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CircuitRebuildGameTests.java).

Clears and reuses network owners, checking source/ground counts and retired nonlinear/leaf callbacks. Opens and recloses both contactor poles twice and checks internal/external rebuild queueing while an unrelated circuit remains healthy.

Entry points:

- [`clearedOwnerCanBeReusedWithoutStaleSourceOrGroundCounts`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CircuitRebuildGameTests.java#L28)
- [`contactorDelayedRebuildSplitsAndReclosesBothPolesTwice`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CircuitRebuildGameTests.java#L49)
- [`clearedOwnerCannotReactivateRetiredLeafSolverHooks`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CircuitRebuildGameTests.java#L93)
- [`internalAndExternalRebuildsQueueOwnersAndLeaveOtherCircuitsHealthy`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/CircuitRebuildGameTests.java#L123)

#### ContactorRestorationGameTests

Namespace: `wwpg_contactors`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ContactorRestorationGameTests.java).

Repeatedly pauses/restores powered coils and linked contactors. Checks that accepted closed state holds during unavailable readings, restored state survives serialization, an open coil cannot close on an unaccepted supply, and normal accepted release/recovery still works.

Entry points:

- [`poweredContactorStaysClosedAcrossRepeatedRestoration`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ContactorRestorationGameTests.java#L29)
- [`restoredCeePoweredCoilAndLinkedContactorHoldUntilAcceptedRelease`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ContactorRestorationGameTests.java#L87)
- [`unpoweredCoilCannotCloseUntilRestorationAcceptsNewSupply`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ContactorRestorationGameTests.java#L139)

#### DisplayPulseGameTests

Namespace: `wwpg_display_pulse`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayPulseGameTests.java).

Starts native displays from saved blank/repeated-zero/transition frames and checks real powered pulses across cyclic wrap/reset behavior. Negative controls require the checker to reject a stuck-high control and a missing rising pulse.

Entry points:

- [`savedBlankFrameCountsThroughItsResetWinding`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayPulseGameTests.java#L27)
- [`savedRepeatedZeroWrapsBeforeTheNextPulse`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayPulseGameTests.java#L30)
- [`savedTransitionFrameWrapsBeforeTheNextPulse`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayPulseGameTests.java#L33)
- [`staleHighAndMissingRisingPulseCannotPass`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/DisplayPulseGameTests.java#L48)

#### MatrixOwnershipGameTests

Namespace: `wwpg_matrix_ownership`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MatrixOwnershipGameTests.java).

Builds two solver-owned matrices, including mixed backends; changes the other circuit during a build, nests builds and throws during preparation. Checks updates reach the right owner and cleanup/backend replacement leave no stale build scope.

Entry points:

- [`anotherCircuitsIncrementalUpdateKeepsItsOwner`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MatrixOwnershipGameTests.java#L27)
- [`mixedBackendUpdatesKeepTheirOwner`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MatrixOwnershipGameTests.java#L34)
- [`nestedPreparationRestoresTheOuterBuild`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MatrixOwnershipGameTests.java#L57)
- [`failedBuildLeavesForeignUpdatesAndFollowingBuildsIntact`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MatrixOwnershipGameTests.java#L73)
- [`backendReplacementAndCleanupDoNotRetainABuild`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MatrixOwnershipGameTests.java#L94)

#### NativeFailureGameTests

Namespace: `wwpg_native_guard`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java).

NATIVE-only fault fixtures: singular factorization with held CEE/addon storage, a zero RHS after failure, late failure rollback, preservation of the original Java callback exception, NaN residual and infinite RHS. Each path must reject unsafe state and subsequently recover.

Entry points:

- [`failedNativeFactorizationHoldsCeeAndAddonStorageAndRecovers`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java#L23)
- [`zeroRhsCannotAcceptAPreviouslyFailedFactorization`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java#L28)
- [`lateFactorizationFailureRestoresTheEntryState`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java#L43)
- [`callbackExceptionPreservesOriginalErrorStateAndRecovery`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java#L65)
- [`nonfiniteResidualOutsideTheLargestFiniteRowIsRejected`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java#L85)
- [`nonfiniteRhsHoldsTheEntryStateAndRecovers`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeFailureGameTests.java#L104)

#### NativeHandheldGameTests

Namespace: `wwpg_meter_sampling`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java).

Checks persistent interpolated connector voltage, PG battery connectors, a remote terminal on a real 3x3x3 battery bank and powered commutator/gauge agreement. Fresh probing must not initialize/destroy a connector; unavailable readings must clear. Three entries deliberately reuse the earlier terminal/wire/clamp fixtures.

Entry points:

- [`meterKeepsInterpolatedConnectorVoltage`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L38)
- [`meterReadsBatteryThroughDeviceConnector`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L68)
- [`meterReadsRemoteMultiblockBatteryTerminal`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L99)
- [`readingFreshConnectorDoesNotInitializeOrDestroyIt`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L138)
- [`generatorConnectorProbesFollowPoweredCommutator`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L155)
- [`existingMixedTerminalMeter`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L212)
- [`existingCeeWireMeter`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L214)
- [`existingPgWireClamp`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeHandheldGameTests.java#L216)

#### PlainWirePowerGameTests

Namespace: `wwpg_wire_power`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlainWirePowerGameTests.java).

Checks the applied plain/line/property-wire power paths against reference values and read counts, including null/exception/custom sampling fallbacks and backend release during a callback. Powered fixtures retain all sixteen substeps, signed current, edits and unavailable/recovery behavior.

Entry points:

- [`customIndexCallbackMayReleaseBackend`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlainWirePowerGameTests.java#L50)
- [`appliedMixinUsesOneDifferentialAndPreservesFallbacks`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlainWirePowerGameTests.java#L66)
- [`appliedPropertyPowerPreservesValuesReadsAndAccumulation`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlainWirePowerGameTests.java#L117)
- [`poweredPlainAndLinePowerKeepSixteenSubstepsAndEdits`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlainWirePowerGameTests.java#L161)
- [`physicalPlainAndLinePowerAreMaskedWhileLoadingAndResume`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlainWirePowerGameTests.java#L209)

#### ResidualAssemblyGameTests

Namespace: `wwpg_rhs`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ResidualAssemblyGameTests.java).

Records cancellation-sensitive RHS addition order and changing values across sixteen substeps; verifies zero endpoint-list work when there are no eliminated leaves. Checks nonempty-leaf filtering, residual replacement and leaf restoration, plus a powered source/capacitor-history fixture.

Entry points:

- [`emptyLeafAssemblyPreservesEveryChangingContributionAndOrder`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ResidualAssemblyGameTests.java#L34)
- [`nativeLeafFilteringSurvivesEmptyNonemptyAndResidualChanges`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ResidualAssemblyGameTests.java#L61)
- [`changingSourcesAndCapacitorHistoryKeepAllSixteenSubsteps`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ResidualAssemblyGameTests.java#L94)

#### SolverFailureGameTests

Namespace: `wwpg_solver_safety`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SolverFailureGameTests.java).

On the selected backend, injects rejected substeps, a singular matrix and a callback exception. Checks held CEE/API state, native battery/FE/filter state, unavailable readings, independent healthy power, cleared acceptance and recovery; changing-input coverage preserves sixteen substeps.

Entry points:

- [`rejectedSubstepsHoldCeeAndAddonStorageAndAvailability`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SolverFailureGameTests.java#L20)
- [`rejectedSolvesHoldPgBatteryFeAndFilterHistoryUntilRecovery`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SolverFailureGameTests.java#L31)
- [`singularNonemptyMatrixIsRejectedAndValidRebuildRecovers`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SolverFailureGameTests.java#L65)
- [`changingInputsKeepAllSixteenSubsteps`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SolverFailureGameTests.java#L90)
- [`callbackExceptionClearsAcceptanceAndRestoresUpdateCounting`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/SolverFailureGameTests.java#L100)

#### UnloadPartitionGameTests

Namespace: `wwpg_unload`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/UnloadPartitionGameTests.java).

Unloads/rebuilds an internal device joining powered or powered/passive circuits. Checks queued repartitioning without premature reload, separate correct solver owners/source counts while detached and merged healthy voltage after explicit reload.

Entry points:

- [`unloadRepartitionsTwoPoweredCircuitsAndReloadMergesThem`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/UnloadPartitionGameTests.java#L28)
- [`unloadRepartitionsPoweredAndPassiveCircuitsWithoutFalseFailures`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/UnloadPartitionGameTests.java#L30)
- [`rebuildingPausedDeviceRetainsExternalOwnersUntilReload`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/UnloadPartitionGameTests.java#L33)

#### WireRemovalRecoveryGameTests

Namespace: `wwpg_wire_recovery`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java).

Six entries deliberately rerun invalid-property/inductor/transformer/resistor/phase fixtures after the removal fix. Additional checks remove real wire entities, require island split/reconnect and healthy warm-up, and ensure two genuinely rejected trials still report diagnostics and hold storage.

Entry points:

- [`unknownProperty`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L35)
- [`nonfiniteProperty`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L37)
- [`inductor`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L39)
- [`transformer`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L41)
- [`directResistor`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L43)
- [`phases`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L45)
- [`completedCircuitsStayConvergedAcrossReloads`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L48)
- [`removingRealWireEntitiesSplitsAndReconnectsPoweredBlocks`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L62)
- [`genuineRejectedSolveStillReportsAndHoldsStorage`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L96)
- [`ordinaryWarmupStillHoldsStorageWithoutErrors`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/WireRemovalRecoveryGameTests.java#L112)

### General addon API and ownership

32 annotated entry points.

#### ExtensionApiDiodeParameterGameTests

Namespace: `wwpg_api`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiDiodeParameterGameTests.java).

Changes diode saturation-current/resistance combinations while retaining same-ID diode and capacitor objects. Checks accepted update counts and diode/RC equations, then removes/replaces a plain-block provider and requires unavailable old readings and fresh replacement history.

Entry points:

- [`liveDiodeParametersRetainObjectsAndAcceptedStorageHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiDiodeParameterGameTests.java#L100)

#### ExtensionApiGameTests

Namespace: `wwpg_api`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java).

Checks eight PG/CEE/API supply-load and wire-system combinations, distinct parallel elements and stable identities. Also checks addon AC RMS and power sign, controlled sources, committed capacitor NBT and transformer voltage/current using independently built public-API devices.

Entry points:

- [`pgSupplyApiLoadPgWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L26)
- [`pgSupplyApiLoadCeeWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L27)
- [`ceeSupplyApiLoadPgWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L28)
- [`ceeSupplyApiLoadCeeWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L29)
- [`apiSupplyPgLoadPgWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L55)
- [`apiSupplyPgLoadCeeWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L56)
- [`apiSupplyCeeLoadPgWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L57)
- [`apiSupplyCeeLoadCeeWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L58)
- [`apiAcSourceRmsAndNativeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L70)
- [`apiControlledVoltageDrivesNativeCeeLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L93)
- [`apiControlledCurrentDrivesNativePgLoad`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L94)
- [`apiStorageUsesOneScheduleAndNativeNbt`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L113)
- [`apiTransformerNativeLoadAndSignedCurrent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiGameTests.java#L124)

#### ExtensionApiLifecycleGameTests

Namespace: `wwpg_api`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiLifecycleGameTests.java).

Checks addon saved storage and both wire systems across cold restart and five actual delayed chunk cycles. Exercises fresh-owner replacement while saved wire entities are delayed, read-only discovery, and retirement of exactly the old owner's wires without disturbing unrelated circuits.

Entry points:

- [`addonSavedStateAndBothWireSystemsSurviveColdRestart`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiLifecycleGameTests.java#L39)
- [`addonStateAndSavedWiresSurviveFiveActualDelayedChunkCycles`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiLifecycleGameTests.java#L92)
- [`diagnosticFreshSidecarReplacementDuringDelayedWireReloadCutsOnlySavedApiWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiLifecycleGameTests.java#L307)

#### ExtensionApiOwnershipGameTests

Namespace: `wwpg_api_ownership`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiOwnershipGameTests.java).

Checks repeated binding cannot reset accepted substep progress, clear rejection or change saved energy; actual unload/reload removes unavailable samples and restores ownership. A merge/split/source-replacement fixture checks that model samples follow their final network.

Entry points:

- [`repeatedBindingPreservesProgressRejectionAndStoredEnergy`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiOwnershipGameTests.java#L30)
- [`ownershipRecoversAfterActualUnloadAndDelayedWireEntities`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiOwnershipGameTests.java#L59)
- [`modelSamplesFollowMergeSplitAndSourceReplacement`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiOwnershipGameTests.java#L110)

#### ExtensionApiPauseGameTests

Namespace: `wwpg_api_pause`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiPauseGameTests.java).

Uses actual server tick freeze and single-step commands with charging API and custom CEE storage. Checks that frozen/read-only activity does not advance committed history, one requested tick commits once, and normal unfreeze resumes power/history.

Entry points:

- [`freezeAndOneTickStepPreserveApiAndCeeCommittedHistory`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiPauseGameTests.java#L46)

#### ExtensionApiPendingRestartGameTests

Namespace: `wwpg_api_pending_restart`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiPendingRestartGameTests.java).

Uses the isolated SETUP, ARM and VERIFY sequence to save pending owner/wire retirement before entity readiness. On a new process, checks disk-loaded ownership format, exact old PG/CEE wire identities, held entity loading, retirement completion and unrelated-wire preservation.

Entry points:

- [`sameOwnerRetirementSurvivesSavingBeforeEntityReadiness`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiPendingRestartGameTests.java#L50)

#### ExtensionApiRejectedSolveGameTests

Namespace: `wwpg_api`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiRejectedSolveGameTests.java).

Forces a nonthrowing rejected solve and checks rollback of CEE and addon committed state, unavailable meters/results, diagnostics and a still-powered unrelated circuit. Restores valid solving and requires storage and readings to recover.

Entry points:

- [`nonthrowingRejectedSolveRollsBackCeeAndApiButKeepsHealthyCircuit`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiRejectedSolveGameTests.java#L33)

#### ExtensionApiReplacementRestartGameTests

Namespace: `wwpg_api_restart`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiReplacementRestartGameTests.java).

Reopens an actual saved ownership record with delayed wire entities, replaces its owner with fresh API sidecars and checks that old wires from both systems are retired exactly. Requires fresh storage state and preservation/recovery of the unrelated/new circuits.

Entry points:

- [`coldOwnerReplacementDuringDelayedEntityLoadingRetiresOnlyOldWires`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiReplacementRestartGameTests.java#L44)

#### ExtensionApiTerminalRefreshGameTests

Namespace: `wwpg_api_ownership`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiTerminalRefreshGameTests.java).

Edits public-API terminal geometry, adds/removes IDs and repeatedly discovers unchanged providers. Checks live native geometry, no unnecessary facade/owner replacement, no history advancement by reads, and proper saved-wire retirement when a terminal disappears.

Entry points:

- [`unchangedFacadeAndLiveTerminalEditsPreservePoweredOwnership`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiTerminalRefreshGameTests.java#L88)

#### FaultFixtures

Namespace: `wwpg_api_faults`. [Source](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java).

Uses an independently built public-API addon to throw during preparation/capability/linkage/commit, return malformed descriptions and replace block entities. Checks fault isolation, quarantine until explicit repair, read-only/fresh-wrapper ownership, storage history, switched grounds and exact old-wire retirement.

Entry points:

- [`malformedAndThrowingPreparationIsolateWithoutStaleResults`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L111)
- [`capabilityAndTerminalLinkageFailuresIsolateWireLookupAndRecover`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L127)
- [`throwingCommitRequiresExplicitChangedToRecover`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L148)
- [`throwingUnavailableCommitIsQuarantinedUntilExplicitRepair`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L166)
- [`freshCapabilityWrappersRetainStorageAndReadonlyQueriesHaveNoCallbacks`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L189)
- [`switchedGroundsKeepExactReferenceAndPhysicalCurrent`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L225)
- [`loadedBlockEntityReplacementCutsOldWiresAndNativePgReplacementCanBeRewired`](../examples/extension-addon/src/main/java/org/cha0scollective/wwpg/example/FaultFixtures.java#L248)

### Native CEE and PG addon extensions

6 annotated entry points.

#### UpstreamApiGameTests

Namespace: `wwpg_api_upstream`. [Source](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamApiGameTests.java).

Uses independently built native CEE panel/storage and PG board-component addons. Injects capture/callback failures, checks rollback and unavailable tools with a healthy native source, then verifies native installation/assembly, custom committed storage and one electrical owner.

Entry points:

- [`ceeAdapterCaptureFailureIsolatesExternallyDrivenTerminalsAndRecovers`](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamApiGameTests.java#L43)
- [`ceeAdapterCallbackFailureRollsBackHistoryAndIsolatesReadings`](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamApiGameTests.java#L96)
- [`ceePanelAdapterPreservesNativeInstallationAndAcceptedStorage`](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamApiGameTests.java#L263)
- [`pgAddonComponentUsesNativeAssemblyAndKeepsOneElectricalOwner`](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamApiGameTests.java#L336)

#### UpstreamRestartGameTests

Namespace: `wwpg_api_upstream_restart`. [Source](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamRestartGameTests.java).

Cold-reopens custom CEE panel and PG board-addon fixtures and performs five real delayed chunk cycles. Checks saved settings/committed state before first solving, stable native wire identities and resumed powered operation.

Entry points:

- [`customPanelAndNativeBoardSurviveColdServerRestart`](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamRestartGameTests.java#L45)
- [`customPanelAndNativeBoardSurviveFiveActualChunkCycles`](../examples/cee-addon/src/main/java/org/cha0scollective/wwpg/examples/upstream/UpstreamRestartGameTests.java#L86)

### Playable world builders and cold reopens

4 annotated entry points.

#### ExampleWorldGameTests

Namespace: `wwpg_example`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExampleWorldGameTests.java).

Builds or reopens the earlier mixed example world and operates its relay-controlled lamps, capacitor, heater and fluid pump. Checks actual light, heat and transferred fluid, saved instructions/tools, board trace isolation and switch-off/recharge behavior.

Entry points:

- [`buildAndVerifyMixedExampleWorld`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExampleWorldGameTests.java#L43)

#### ExtensionApiWorldGameTests

Namespace: `wwpg_api_world`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiWorldGameTests.java).

Builds/reopens the playable Workshop using both independently built example addons. Verifies powered machine/storage/instrument/panel/board stations, configuration and control changes, native assembly, saved addon identities and circuit recovery; real-client visuals/input are separate.

Entry points:

- [`buildAndVerifyPublicExtensionWorkshop`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiWorldGameTests.java#L103)

#### ShowroomWorldGameTests

Namespace: `wwpg_showroom`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ShowroomWorldGameTests.java).

Builds/reopens the older expanded showroom, runs server-side interactions and verifies board/panel/power/device helpers, routing, guide storage and inventory coverage. Parts stored in cabinets are explicitly not equivalent to powered device tests.

Entry points:

- [`buildAndVerifyExpandedFixtureWorld`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ShowroomWorldGameTests.java#L40)

#### StationaryYardGameTests

Namespace: `wwpg_yard`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryYardGameTests.java).

Delegates to the full Stationary Yard builder/verifier: the combined Pinout edition includes the ordinary exhibits, powered device/board/panel checks, instructions and server-side interactions across cold phases. It is not the 14,650-node performance world or a rendered client tour.

Entry points:

- [`stationaryYardHasWorkingInstructionsAndSurvivesRestart`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/StationaryYardGameTests.java#L12)

### Optional Pinout and real Lua computers

4 annotated entry points.

#### PinoutLifecycleGameTests

Namespace: `wwpg_pinout`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PinoutLifecycleGameTests.java).

Uses actual computer/Pinout/panel/board circuits through cold restart, five real delayed chunk cycles and replacement. Checks saved native wire identities, Lua reports, independent powered loads, no ghost power and no inherited solved reading on a new owner.

Entry points:

- [`nativePinsComputerPanelAndBoardSurviveColdRestartFiveChunkCyclesAndReplacement`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PinoutLifecycleGameTests.java#L48)

#### PinoutLuaGameTests

Namespace: `wwpg_pinout`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PinoutLuaGameTests.java).

Runs actual Lua against all eight mixed loads using both CEE and PG supplies. Checks API-method coverage, independently measured pin voltages and a real computer reboot followed by a new successful Lua report.

Entry points:

- [`actualLuaControlsAllEightMixedLoads`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PinoutLuaGameTests.java#L29)
- [`actualLuaControlsAllEightMixedLoadsFromPowerGridSupply`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PinoutLuaGameTests.java#L34)

#### YardComputerBootstrapGameTests

Namespace: `wwpg_pinout`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/YardComputerBootstrapGameTests.java).

Finishes a real native computer startup, proves turnOn alone does not restart its shell, then requires one reboot and a delayed, atomically completed Lua report. Verifies delayed completion is not accepted early and pin outputs remain correct.

Entry points:

- [`completedComputerNeedsRebootAndDelayedLuaCompletion`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/YardComputerBootstrapGameTests.java#L23)

### Upstream reference and environment checks

11 annotated entry points.

#### NativeReferenceTimingGameTests

Namespace: `wwpg_reference`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java).

Compares powered fixture windows with independently captured pinned-upstream controls: bulb/heater/fuse timing within one tick, accumulator/acid-battery energy within 1%, potato-battery behavior and diode/nonlinear-board reactive windows. These are device timing/equivalence checks, not TPS measurements.

Entry points:

- [`bulbFailureAndHeaterTransitionsMatchPinnedUpstreamWithinOneTick`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L41)
- [`fuseTripAndNativeRepairMatchPinnedUpstreamWithinOneTick`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L74)
- [`accumulatorMixedChargeDischargeMatchesPinnedStorageWithinOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L107)
- [`mixedAcidBatteryChargeDischargeMatchesNativeEnergyWithinOnePercent`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L250)
- [`mixedPotatoBatteryPreservesNativeNoChargingAndDischargeWindow`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L253)
- [`ceeDiodeReactiveSwitchingMatchesPinnedNativeWindow`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L256)
- [`ceeClampedDiodeHighBiasMatchesNativeEquationAndReference`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L289)
- [`pgNonlinearBoardReactiveSwitchingMatchesPinnedNativeWindow`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/NativeReferenceTimingGameTests.java#L313)

#### PlatformCompatibilityGameTests

Namespace: `wwpg_platform`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlatformCompatibilityGameTests.java).

Checks the actual loaded NeoForge version matches the requested run and that Pinout/CC:Tweaked presence matches BASE or PINOUT. This verifies the test environment, not the electrical functionality of every dependency version.

Entry points:

- [`requestedLoaderAndProfileActuallyLoaded`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/PlatformCompatibilityGameTests.java#L14)

#### ReferenceWorldGameTests

Namespace: `wwpg_reference`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ReferenceWorldGameTests.java).

Reopens native CEE and PG reference worlds, checking saved voltage/current/capacitor/board behavior and the actual requested backend. In the upstream-control mode it requires that the WWPG bridge did not activate; the compatibility mode checks the WWPG run separately.

Entry points:

- [`nativeCeeStationaryWorldRemainsFunctional`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ReferenceWorldGameTests.java#L36)
- [`nativePgStationaryWorldRemainsFunctional`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ReferenceWorldGameTests.java#L50)

### Separately selected SDK and real-client helpers

7 annotated entry points.

#### ExtensionApiMultiplayerGameTests

Namespace: `wwpg_api_multiplayer`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiMultiplayerGameTests.java).

An opt-in legacy server entry point for two real clients operating the public API Workshop. Actual interaction assertions are delegated to the Workshop client/session helpers; this is not an ordinary headless electrical check.

Entry points:

- [`twoActualClientsOperateThePublicApiWorkshop`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/ExtensionApiMultiplayerGameTests.java#L16)

#### MultiplayerGameTests

Namespace: `wwpg_multiplayer`. [Source](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MultiplayerGameTests.java).

An opt-in legacy TCP server/command workflow with two real clients. Checks simultaneous native wire placement, native settings packets, powered readings and client-reported display/wire agreement; additional stationary interactions are delegated to its session helper.

Entry points:

- [`twoRealClients`](../src/gameTest/java/org/cha0scollective/wwpg/gametest/MultiplayerGameTests.java#L42)

#### DocAcceptance

Namespace: `wwpg_doc`. [Source](../tools/documentation_acceptance/src/org/cha0scollective/wwpg/docs/DocAcceptance.java).

Runs against the independently extracted SDK starter: operates controls and both wire systems, checks terminal geometry/native meters/clamp and unavailable recovery, deliberately mismatches model/settings then repairs them, and checks cold saved settings and actual chunk reload. Real client clicks/readouts belong to the separate SDK graphical helper.

Entry points:

- [`controlsGeometryUnavailableAndBothWireSystems`](../tools/documentation_acceptance/src/org/cha0scollective/wwpg/docs/DocAcceptance.java#L126)
- [`mismatchIsReportedAndRepairRecovers`](../tools/documentation_acceptance/src/org/cha0scollective/wwpg/docs/DocAcceptance.java#L164)
- [`savedSettingsSurviveColdRestartBeforeFirstSolve`](../tools/documentation_acceptance/src/org/cha0scollective/wwpg/docs/DocAcceptance.java#L182)
- [`actualChunkReloadKeepsSettingsAndConnectedWires`](../tools/documentation_acceptance/src/org/cha0scollective/wwpg/docs/DocAcceptance.java#L202)

#### ServerAcceptance

Namespace: `wwpg_ci_client`. [Source](../tools/client_acceptance/src/org/cha0scollective/wwpg/acceptance/ServerAcceptance.java).

A separately packaged graphical helper starts a loopback dedicated server, waits for two actual clients, delegates powered/control/agreement assertions to SmokeSession and only succeeds after both clients disconnect. It must not be counted as an ordinary headless run.

Entry points:

- [`twoClients`](../tools/client_acceptance/src/org/cha0scollective/wwpg/acceptance/ServerAcceptance.java#L17)

## Keeping this reference current

Update the relevant fixture description and entry-point list whenever a GameTest
is added, removed, delegated or changes its assertions. Classify a new namespace
in the catalog; the read-only `python tools/test_suites.py audit` catches
unclassified namespaces but does not verify these prose descriptions.

This source audit ran no electrical, headless or graphical campaign and does not
replace preserved execution evidence. Published historical manifests remain
unchanged.
