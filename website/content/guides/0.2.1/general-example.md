Documentation correction · October 12, 2026. These instructions retain the example from WWPG 0.2.1, with the Unix permission step and release-pinned navigation corrected. The original README inside the published SDK is unchanged.

## About the general example

This separate NeoForge project demonstrates stationary devices built with
**WWPG extension API 1.0.0**. It compiles against the SDK artifact and NeoForge;
its Java source imports no PG, CEE, or WWPG implementation classes.

This example ships with WWPG 0.2.1. Its nine blocks demonstrate the general API;
the [upstream example]({{docs}}native-example/) demonstrates native CEE panels and
PG circuit boards. The [API Workshop]({{docs}}workshop/) is a
playable save using both add-ons. The [release verification](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-verification.zip)
records the tested artifacts and completed checks.

## Build independently

First download and extract the [0.2.1 developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-developer-sdk.zip), verify its [checksum](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-checksums.txt), and extract `maven/wwpg-api-1.0.0-maven.zip` into a separate directory. That directory must directly contain `org/cha0scollective/wwpg`. The [first add-on tutorial]({{docs}}create-addon/#1-prepare-the-project-and-sdk) gives the full Windows/Unix extraction commands.

Extract `examples/wwpg-api-example-1.0.0-source.zip` from the outer SDK into its own empty directory. Enter the extracted directory containing `settings.gradle` and `gradlew`; this is the example project root. Extract the Maven archive separately. For instance:

```powershell
Expand-Archive -LiteralPath "C:/Dev/WWPG SDK/examples/wwpg-api-example-1.0.0-source.zip" -DestinationPath "C:/Dev/Example Project"
Set-Location "C:/Dev/Example Project"
.\gradlew.bat build "-PapiRepository=C:/Dev/WWPG API Maven" --no-daemon
```

```sh
unzip "/home/me/WWPG SDK/examples/wwpg-api-example-1.0.0-source.zip" -d "/home/me/Example Project"
cd "/home/me/Example Project"
chmod +x gradlew
./gradlew build "-PapiRepository=/home/me/WWPG API Maven" --no-daemon
```

Existing published source archives need the explicit `chmod` step. Future SDK archives record executable Unix wrapper permissions.



Use Java 21 and the example project's Gradle wrapper.

Download and extract the
[developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-developer-sdk.zip).
Verify its release checksum, then extract its `maven/wwpg-api-1.0.0-maven.zip`; the directory containing
`org/cha0scollective/wwpg` is the Maven repository root. Run the wrapper from
this project's directory, supplying that root's absolute path:

```text
chmod +x gradlew
./gradlew build "-PapiRepository=/absolute/path/to/wwpg-api-maven" --no-daemon
```

On Windows use `gradlew.bat` and an absolute Windows path. The output is
`build/libs/wwpg-api-example-1.0.0.jar`. The compile dependency is
`org.cha0scollective.wwpg:wwpg-api:1.0.0`; do not add the WWPG source project to
the example's build or shade API classes into the example jar.

## Install and operate

Install the example jar together with the **WWPG 0.2.1 runtime** on the server
and every client. Use the tested Minecraft 1.21.1 / NeoForge 21.1.231 environment
with Java 21, Create 6.0.10-280, CEE 1.1.3, PG 0.6.2, and Architectury 13.0.8.
Follow [WWPG's installation instructions]({{docs}}install/) for the upstream
dependencies and solver configuration. The example adds no FE or computer mod
requirement. Pinout and CC:Tweaked remain optional.

The nine example blocks appear in the Creative **Redstone Blocks** tab. They are
developer demonstration devices, including ideal supplies; crafting recipes and
production gameplay balance are deliberately not part of this sample. For an
individual block use, for example:

```text
/give @s wwpg_api_example:load
/give @s wwpg_api_example:source
```

Use an empty hand to interact:

- **Right-click:** show the current voltage, mean power, and stored capacitor
  voltage / inductor current in server chat.
- **Sneak-right-click:** change the device's enabled setting, then show its reading.
  A changed setting affects the next electrical tick.
- **Bright block:** the last accepted circuit has measurable power or stored
  capacitor voltage. This visible indicator is driven by solved results, not just
  the enabled setting. An idle supply with no load can therefore remain dark.

The north-facing red and blue contact markers identify positive and negative terminals. The
upper pair is the primary circuit; devices with a second pair use the lower pair
for transformer output or controlled-source output. Numerical IDs are **0 upper
positive**, **1 upper negative**, **2 lower positive**, and **3 lower negative**.
Use the wiring tool's selected-terminal feedback to confirm the intended contact.

## Devices and expected behavior

| Registry ID | Demonstration |
| --- | --- |
| `load` | Two independent 100-ohm elements in parallel: 50 ohms enabled. Disabling makes both effectively open. |
| `source` | Ideal 20 V DC supply enabled; 0 V when disabled. Disabling is a zero-voltage source, not removal of its connection. |
| `ac_source` | Ideal 20 V peak, 50 Hz sine wave enabled; zero amplitude when disabled. Nominal RMS is approximately 14.142 V before wire losses. |
| `capacitor` | A 0.01 F capacitor behind a 100-ohm charging resistor. Disabling adds a 100-ohm discharge path. Stored voltage is saved in its block data. |
| `inductor` | A 1 H inductor in series with 100 ohms. Its accepted current is saved. The enabled setting does not change this demonstration's electrical model. |
| `diode` | PG-native PN-junction behavior, with 1-ohm series resistance and 22 °C parameters. The enabled setting does not reverse or remove the diode. |
| `transformer` | Four-terminal ideal transformer. Enabled ratio is secondary/primary = 2; disabled ratio is 0.5. Use AC for the demonstration. |
| `voltage_control` | Four-terminal controlled voltage supply: upper-pair voltage multiplied by 2 at the lower pair enabled, or zero gain disabled. |
| `current_control` | Four-terminal controlled current supply: upper-pair voltage multiplied by 0.01 siemens at the lower pair enabled, or zero output disabled. |

The displayed main voltage is the **upper-pair instantaneous voltage**. It is not
an RMS meter or the lower-pair transformer output. Mean power sums all elements
in the device using the API's absorbed-power convention. A supply delivering
energy can therefore display negative watts. Both storage fields are printed for
every device; a model that has no capacitor/inductor retains zero for that field.

For a first visible circuit, connect both terminals of `source` to the
corresponding terminals of `load`, using a complete return path. With negligible
wire resistance expect roughly **20 V, 0.4 A, and 8 W** at the enabled load.
Sneak-right-click the load: it should go dark and its power should fall nearly to
zero. Re-enable it to restore the output. Connect the same load to a native CEE
or PG supply to check cross-mod operation.

For storage, replace the load with `capacitor`. At 20 V its voltage approaches
the supply over several seconds; the ideal charging time constant is one second.
Read the stored value, save and reopen the world, then read again. During a valid
reload, the stored value is restored before the first new solve. A connected live
supply can change that value again, so a visual reading alone does not prove
pre-solve persistence; automated acceptance checks inspect the saved history.

For the transformer, use `ac_source` on the upper winding and a load across the
lower winding. Use a meter that reports RMS to compare the two winding voltages.
Changing the ratio should change the load's voltage and brightness. Do not short
an ideal source or connect two conflicting ideal voltage outputs together.

Controlled sources need two separate circuits: an input across the upper pair
and a load across the lower pair. The current-controlled output's positive
current direction follows the API convention; reverse a load's terminal
orientation if its voltage sign matters. A controlled source supplies ideal
output power and is not a gameplay-balanced energy converter.

If the chat says **Electrical reading unavailable**, resolve the status through
WWPG diagnostics. Do not interpret an unloaded, isolated, or failed circuit as
an accepted zero-voltage circuit.

## Source structure and ownership

`ExampleMod.java` registers block capabilities and exposes `ElectricalDevice`.
`prepare()` builds immutable `CircuitDescription` records with stable element
IDs. The load intentionally places two elements on the same terminal pair to
demonstrate parallel-element identity. `commit()` accepts solved results once,
updates the visible state, and saves accepted capacitor/inductor history using
the block entity's versioned native NBT.

The example's retained fields are authoritative saved history. Creating a new
description every tick does not recharge or reset an existing element. Invalid
results do not overwrite that history. Block rendering, interaction, inventory
items, and synchronization belong to the example mod; PG substeps and mixed
electrical topology belong to WWPG.

The example's `useItemOn` lets nonempty held stacks proceed to their native item
handler by returning `SKIP_DEFAULT_BLOCK_INTERACTION`. Empty hands use
`PASS_TO_DEFAULT_BLOCK_INTERACTION`, so the readout and sneak-click control still
work. Preserve this ordering in an interactive add-on: consuming every click in
`useWithoutItem` can swallow a CEE spool or multimeter before `Item.useOn` runs.
Test real block/item dispatch as well as direct electrical fixtures.

Breaking an example block returns its block item using the supplied loot table.
The item does not carry its previous block entity's stored charge. This sample
does not promise portable charged-device behavior.

Panel attachments and circuit-board components use upstream assembly workflows.
The [upstream example]({{docs}}native-example/) documents those routes; the nine
blocks in this project demonstrate the general device API.

See [the public API contract]({{docs}}developers/) for electrical units,
identity rules, lifecycle boundaries, measurements, and compatibility policy.
Maintainer packaging checks live in the [release guide](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/39df6fa087d0bbc4601d9aab40d76105895a1e7a/docs/RELEASING.md).


[Build, install and debug]({{docs}}addon-workflow/) · [API validation and limits]({{docs}}api-limits/)
