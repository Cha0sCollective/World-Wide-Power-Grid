# CIO integration — unreleased development

This describes the local 0.2.6-dev.114 integration. Published WWPG 0.2.5 does not
support CIO's inverter or safely separate its redundant coupler ports.

## Inverter

The integration targets Create: Interoperable 0.1.240-experimental. Connect a DC
supply to the inverter's labelled positive and negative input terminals. Connect
the AC equipment to its labelled L1/L2/L3 and neutral output terminals, then use
the inverter's normal voltage, frequency, enable and reversal controls.

WWPG executes CIO's original waveform and measurement callbacks on its electrical
substeps. CIO retains its own DC input limits, conversion losses, load reflection,
controls, overload logic and saves. A failed solve does not advance the adapter's
phase measurements or publish an accepted device update.

Support is tied to the verified private phase-model class as well as the mod
version. A different model remains unsupported with an explicit diagnostic.
Rebuilt CIO jars with the same version label can therefore require revalidation.

## Grid couplers

WWPG already connects PG and CEE devices directly. CIO's mini grid coupler and
known legacy grid-coupler implementations are disabled while WWPG is installed.

- New coupler placement is blocked without consuming the item. Its item tooltip
  explains that PG and CEE devices can be connected directly.
- Existing couplers remain in the world and keep their physical wires. Their PG
  terminals remain passive, but their power-transfer components, electrical
  updates and transfer audio are disabled.
- Their CEE endpoints stay separate from the native PG terminals. Readings on
  the disabled CEE ports are unavailable; the goggles show the disabled message.
- Remove the obsolete coupler and wire the intended devices together directly
  when changing an existing build. WWPG does not automatically rewire the build.

## Developer checks and boundaries

The companion provides three explicitly selected namespaces. These are not added
to the ordinary BASE/PINOUT acceptance matrix and never install CIO implicitly.

| Namespace | Required fixture | Checks |
| --- | --- | --- |
| `wwpg_cio` | Verified CIO jar | Original callbacks versus adapter at 16/32 substeps, early/late rejection rollback, loaded three-phase waveforms, phase/line RMS, real power and DC draw, enable/frequency/reversal, both Power Kit families, blocked placement and saved coupler restart/rebuild |
| `wwpg_cio_upgrade` | Disposable original 0.2.5 coupler reproduction save | The old unintended CEE output stops, the PG supply stays powered, PG/CEE wires remain saved and a disabled explanation is available |
| `wwpg_cio_guard` | Deliberately changed private-model identity | The mod loads, but the unverified class receives no private-history hook or adapter |

Use the packaged gameplay jar and its matching separate test companion. Add the
exact CIO jar to the test runtime, select the namespace with `testNamespaces`,
and select Native or Java with `testBackend`. An upgrade check needs a fresh copy
of its original save; preserve every attempt. The normal 600-second server
launcher bound and Minecraft watchdog still apply.

Transient rejected adapter ticks observed during initial/enable transitions are
retained in the diagnostic history. Steady powered checks and recovery checks
must pass; do not clear those diagnostics to turn an interrupted or rejected
attempt into a pass.

This integration does not certify CIO's optional furniture, phone, voice,
ComputerCraft, climate or transport integrations. Graphical input/rendering,
performance and full release acceptance require their own applicable evidence.
Focused local results and artifact identities are recorded separately from
published release records.
