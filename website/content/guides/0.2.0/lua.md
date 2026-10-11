Computer control is an **optional integration** with **CC;CPG: Pinout 0.2.1** and **CC:Tweaked 1.120.2**. Install both on the server and clients in addition to WWPG's required dependencies. The base WWPG jar works without them.

WWPG preserves Pinout's native peripheral API. These are Pinout methods, not a new WWPG scripting API. The release's actual Lua VM checks and downloadable Pinout yard exercise all eight methods.

## Find the peripheral and switch pins

Place the native Pinout peripheral beside a CC:Tweaked computer. Start with the separate **Stationary Test Yard — Pinout** for a wired example.

```lua title="Control the native Pinout peripheral"
local p = assert(peripheral.find("pinout"), "Pinout peripheral not attached")
p.setByte(0)       -- open all controlled pins
p.connectPin(1)    -- close pin 1
sleep(1)
p.disconnectPin(1)
p.setByte(85)      -- close pins 1, 3, 5, and 7
print(textutils.serialize(p.pinsConnected()))
```

The calls above appear in the release's tested native Lua workflows. Wire the circuit according to the yard instructions before expecting lamps to respond.

## Pin numbering and signed voltage

Controlled pins are **1–8**. Lua common pin **9** maps to PG terminal **0**. Bit 0 of `setByte` controls pin 1; its valid byte range is 0–255.

```lua title="Inspect the native layout and voltage"
print(textutils.serialize(p.pinLayout()))
local voltages = p.pinsVoltage(9)
print(voltages[1])
print(p.comparePin(1, 9))
print(p.comparePin(9, 1))
```

Reversing the reference reverses the sign. In the supplied 70 V yard loads, ON pins read slightly below 0 V relative to common because of contact resistance, and OFF pins read about −70 V. Those are readings for that demonstration, not universal circuit values. Load gauges read about 70 V when ON and 0 V when OFF.

## The eight tested methods

| Method | Tested use |
| --- | --- |
| `pinLayout()` | Inspect the native pin layout. |
| `pinsConnected()` | Read the pin connection states, including common. |
| `pinsVoltage(9)` | Read signed pin voltages relative to common. |
| `comparePin(1, 9)` | Compare two pins; reversing them reverses the sign. |
| `connectPin(1)` | Close one pin; a list such as `{1, 3, 5, 7}` is also tested. |
| `disconnectPin(1)` | Open one pin or a tested list. |
| `setByte(85)` | Set all eight controlled pins from a byte pattern. |
| `setPins({[1] = true, [2] = false})` | Set selected pin states. |

Common pin 9 is not a controlled pin. The release checks rejection of invalid controlled pins, out-of-range bytes, and invalid state values. Use CC:Tweaked's normal Lua error handling for peripheral failures.

## Try the supplied program

In the Pinout yard, type `wwpg start`, `wwpg stop`, `wwpg pattern 85`, `wwpg demo`, `wwpg volts`, or `wwpg layout`. The board relay can take about three seconds to respond as its capacitor discharges. Follow the [yard guide]({{docs}}example-worlds/) for its wiring and controls.

Sources: the [release's player program]({{source}}/src/main/resources/data/wwpg_pinout/programs/yard/wwpg.lua), [actual Lua acceptance program]({{source}}/src/main/resources/data/wwpg_pinout/programs/acceptance.lua), and [Pinout lifecycle checks]({{source}}/src/main/java/org/cha0scollective/wwpg/gametest/PinoutLuaGameTests.java). Pinout and CC:Tweaked retain ownership of their APIs and content.
