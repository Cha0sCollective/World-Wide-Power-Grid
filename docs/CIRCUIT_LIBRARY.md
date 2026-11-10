# Circuit library for 0.2.8-rc.1

This prerelease adds twelve circuit assemblies to release testing. They operate
real PG boards, native inputs, wires and loads. The verification download carries
the fixture inputs and traces; these are developer fixtures rather than a new
playable demonstration world. Acceptance is pending until the release record is
complete.

Use **16 electrical substeps** (`multiTicks = 16`) in PG's solver configuration.
The two 240 V heater controllers need Power Components 1.3.0 for their selectors;
the fused output needs it for its replaceable fuse. Other circuits use native PG
components. The heater tests use a real PG basin heater beneath a Create basin.

| Assembly | Operate and expect | Reset |
| --- | --- | --- |
| Three-bit full adder | Supply 15 V logic; set A, B and carry-in. S and carry-out represent their sum. Tests cover all 128 combinations. | Set inputs low. |
| LC oscillator | Supply +12 V, -12 V and ground. Observe the analog output; the preserved fixture checks sustained amplitude and a 60–66 tick period. | Disconnect and reconnect both supplies. |
| Generator/battery selector | Provide generator, battery and checker supplies. The checker selects the generator feed and charging output; loss of the checker returns the battery load to the battery. | Restore generator and checker power. |
| Converted linear regulator | Supply VIN and VREF; connect the specified external divider to ADJUST. Output follows the divided reference minus the pass-stage drop. | Restore supply/reference and remove an extra test load. |
| Eight-bit parallel register | Supply 15 V; put signed data on eight DATA lines and pulse WRITE. Q retains the last word while WRITE is low. | Write zero to all bits. |
| Direct-row 4×4 memory | Select a row with its own WRITE line and present four shared data bits. Other rows keep their words. | Write zero to each row separately. |
| Four-bit clocked shift register | Supply ±15 V; put data on DIN and raise CLK. Each rising edge advances one bit. Holding CLK high must not shift again. | Clock four zeros through it. |
| Addressed 4×4 memory | Supply ±15 V; select a row with A0/A1. Raise WRITE to store four data bits; the read selectors expose the addressed word. | Write zero to each address. |
| Fused latching output | Supply 24 V VIN and 15 V RUN; pulse TRIGGER to latch OUT. An overload blows the actual fuse. | Lower RUN. Remove the overload, replace the fuse with the fuse item, restore RUN and trigger again. |
| 240 V relay basin heater | Turn the board selector through Off, Low, Heated and Superheated. The real heater should settle at the corresponding heat level. | Select Off; allow the heater to cool. |
| Quiet 240 V basin heater | Use the same four settings. Electronic switching supplies the heater without relay clicks. The indicator neon may blink; at 16 substeps the heater's settled heat level remains steady. | Select Off; allow cooling. |
| Two-source loss alarm | Supply AUX and two monitored 12–24 V sources. Real redstone turns on if either monitored source is absent. | Restore both monitored supplies. Losing AUX is not a guaranteed alarm condition. |

The full adder is by **jolol1k3_**, the oscillator by **Dr Ender**, and the source
selector by **Ink | Soul Artist**. The regulator is the native-connector
conversion of **Dooooom20 | Ham Mafia |**'s community design. The other assemblies
are the retained WWPG circuit designs. The repository owner confirmed permission
to redistribute the community inputs on November 10, 2026. This permission does
not replace their author credits or make their designs part of the WWPG code's
MIT license.

Developer sequences, schematic SHA256 values and adapter provenance are in
`src/gameTest/resources/data/wwpg_circuits/provenance.json`. Original exploratory
failures and stock-PG checks remain historical records. They do not establish
acceptance of this prerelease. The new circuit groups cover functional operation,
power cycling and the reconstruction checks present in their original sequences;
the general lifecycle groups separately cover cold world loading and unloading.
