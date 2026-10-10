# Expanded test yard

This is a hands-on world for **WWPG 0.1.0-beta.2**. It gives most supported equipment its own working mixed-mod circuit, with signs, testing tools, and a guidebook. Every built-in **Power Grid board component** and **CEE panel attachment** appears in a working example.

Download [the expanded test-yard ZIP](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.2/wwpg-0.1.0-beta.2-fixture-world-v1.zip). Extract **WWPG Expanded Test Yard/** into your Minecraft instance's `saves` folder. Open **WWPG - Expanded Test Yard** in the world list. Use the existing beta.2 mod jar and [exact dependencies and solver settings](INSTALL.md#required-versions).

You spawn at **(8, 64, 16)** in Creative mode. Take **Engineer's Goggles**, both handheld meters, the wrench, and the guidebook from the chest at **(6, 64, 16)**. Follow the white path or fly. Each station has its own supply, so changing one example generally does not shut down the others.

The yard demonstrates **85 of the 134 declared behavior groups** in live exhibits; the remaining equipment is supplied as parts or assembly references. A placed PG design table is excluded because of its [saved-design load error](STATUS.md#pg-circuit-design-table-saved-design-error).

## Where to go

| Area | Coordinates | What to try |
| --- | --- | --- |
| Factory and relay demo | Near spawn, z8 and z24 | Flip **Factory enable**. Green RUN lights; switching off delays briefly, then red OFF lights. Watch the water pump and heater beneath its basin. |
| Board gallery, B01–B25 | x32–112, z8–81 | Inspect individual components, their CEE supplies, and external PG gauges. Use the feed switch or the labeled controls. |
| Panel gallery, P1–P9 | x32–112, z94–115 | Switch lamps, press buttons, trip/reset a breaker, read gauges, and control wireless lamps. All 11 attachment types are present. |
| Transformers and variacs, T1–T7 | x32–112, z124–145 | Compare input and output voltage. Adjust ratios, turn hand cranks, or flip the redstone lever. |
| Routing board, B26 | (83,64,140) | Inspect front/back traces, connector pins, a via, and a label in a powered resistor circuit. |
| Meter banks, M1–M3 | x32,64,96 at z156 | Compare the two mods' voltage, current, power, and energy readings. The AC station reads 50 Hz. |
| Devices and storage, D1–D22 | x32–112, z176–237 | Try standalone controls, connectors, motors, capacitors, an inductor, battery charging, and an accumulator. |
| Parts cabinets | x6–10, z48 onward | Take additional supported parts and spares for your own builds. Cabinet contents are inventory, rather than working demonstrations. |

## Transformers and gauges

Wear goggles to read the numbers. A meter needle can move very little even when a circuit works.

| Station | Supply and expected output |
| --- | --- |
| T1: CEE transformer | PG 100 V supply; about **50 V** output. Change the transformer ratio to raise or lower it. |
| T2: CEE transformer | PG 100 V supply; about **200 V** output. |
| T3: PG small transformer | CEE 20 V supply; 20-turn primary and 40-turn secondary. Output is close to **40 V**, with PG's modeled loss. |
| T4: PG medium transformer | CEE 20 V supply; 40-turn primary and 20-turn secondary. Output is close to **10 V**, with modeled loss. |
| T5: PG variac | CEE 20 V supply. Turn the hand crank to change the output; hold Shift to reverse its direction. |
| T6: CEE variac | CEE 20 V supply feeding a PG load. Starts near **10 V**; its crank changes the output. |
| T7: CEE redstone variac | CEE 20 V supply feeding a PG load. The lever changes the output between low and high settings. |

**M1** uses a CEE source with PG meters. **M2** uses a PG source with CEE meters. Both supply a 10-ohm load: expect about **10 V**, **1 A**, and **10 W**, with energy totals increasing. **M3** uses a PG 50 Hz AC source and CEE frequency meter. Its 100 V setting is peak voltage; AC voltage meters report RMS.

## Panels

- **P1:** ammeter, voltmeter, and indicator. The lamp and panel indicator together draw about **0.60 A at 300 V**; the lamp branch alone draws about **0.30 A**.
- **P2:** cut-off switch. An empty-hand click changes the lamp.
- **P3:** emergency stop. Click to stop; **shift-click to reset**.
- **P4:** momentary switch. Press for a brief lamp flash.
- **P5:** 1 A circuit breaker. Change the separate overload resistor from **100000 ohms** to **50 ohms** to trip it. Restore **100000 ohms**, then reset the breaker.
- **P6/P7:** single- and three-pole energy meters. Their powered lamps consume energy; totals increase.
- **P8/P9:** analog lever and steering wheel. Move the control to change the signal sent to the nearby Create wireless receiver and lamp.

## Circuit boards

The electrical gallery has 25 boards, with one component behavior per station. Supplies are labeled by **PAD number**. The resistor above each supply limits current; leave those settings in place unless you intend to change the circuit.

| Stations | Components and useful checks |
| --- | --- |
| B01–B05 | Resistor, capacitor, inductor, potentiometer, varistor. Change voltage, watch stored voltage/current, adjust the knob, or compare the varistor's clamped voltage. |
| B06–B11 | Fuse holder, switch, button, relay, DPDT relay, redstone relay. Click native controls, inspect switched contacts, or flip the physical lever. |
| B12–B15 | Diode, NPN, PNP, VFET. Compare forward conduction and bias-controlled current. The PNP's supplies have reversed physical polarity. |
| B16–B20 | Triode, pentode, thyratron, regulator tube, barretter. Heater and bias supplies are labeled. Turning off a heater takes time to cool the tube. |
| B21–B25 | Neon bulb, light bulb, voltage gauge, current gauge, display module. Look for glow, numeric readings, and changes in the display after power pulses. |
| B26 | Connector pins, via, and label complete the set of **28 built-in component types**. This live resistor circuit draws about **10 mA at 10 V**. |

Small currents are expected here: **10 mA means 0.010 A**, not a failed circuit. Use the external current gauge or a handheld meter to see changes. [Handheld-meter instructions](INSTALL.md#handheld-meters) explain terminal probes and wire measurements.

## Storage and repairs

The PG portable battery example **charges an energy item**; its placed block does not act as a wired generator. The CEE accumulator stores power for its connected load. Open its charger switch to disconnect the supply and watch the load remain powered.

Keep a clean copy of the downloaded world before cutting wires, replacing parts, or deliberately overloading components. Signs marked **LIVE** identify connected examples. The parts cabinets contain remaining supported equipment, including assemblies that need building; placing an item in a chest does not demonstrate that equipment operating.

The world checks cover circuit readings, native controls, and reopening the exported save with native and Java backends. They do not replace the beta's full support matrix, establish large-network performance, or resolve the intermittent reload/restart failures in [current status](STATUS.md). The [world's evidence record](../release/examples/expanded-yard-v1/) lists its exact coverage and checks.
