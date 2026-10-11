-- Uses the add-on's eight native methods. This is a player program, not a new API.
local args = {...}
local p = assert(peripheral.find("pinout"), "Pinout missing: check the block beside this computer")
local command = args[1] or "help"
if command == "start" then
  p.setByte(255)
  print("All eight lamps ON. Pin 3 also runs the board relay.")
elseif command == "stop" then
  p.setByte(0)
  print("All lamps OFF. The board has a short capacitor delay.")
elseif command == "pattern" then
  local byte = assert(tonumber(args[2]), "Use wwpg pattern 0-255")
  assert(byte >= 0 and byte <= 255 and byte == math.floor(byte), "Byte must be an integer from 0 to 255")
  p.setByte(byte)
  print("Pattern " .. byte .. "; bit 0 controls pin 1.")
elseif command == "pins" then
  local states = {}
  for pin = 1, 8 do states[pin] = false end
  for index = 2, #args do
    local pin = assert(tonumber(args[index]), "Use wwpg pins 1 3 5 7")
    assert(pin >= 1 and pin <= 8 and pin == math.floor(pin), "Controlled pin must be 1-8")
    states[pin] = true
  end
  p.setPins(states)
elseif command == "demo" then
  p.setByte(0)
  for pin = 1, 8 do
    p.connectPin(pin)
    print("Lamp " .. pin .. " ON")
    sleep(1)
    p.disconnectPin(pin)
  end
  p.setByte(255)
  print("Demo finished; all lamps restored ON.")
elseif command == "volts" then
  local states, volts = p.pinsConnected(), p.pinsVoltage(9)
  print("Signed volts relative to common pin 9:")
  print("ON is about 0 V; OFF is about -70 V.")
  print("Load gauges read 70 V ON, 0 V OFF.")
  for pin = 1, 8 do
    print(string.format("%d %s %+.2f V", pin, states[pin] and "ON " or "OFF", volts[pin]))
    assert(math.abs(p.comparePin(pin, 9) - volts[pin]) < 0.05, "Pin readings changed; try again")
  end
elseif command == "layout" then
  print(textutils.serialize(p.pinLayout()))
  print("Lua common 9 = PG terminal 0. Switchable pins are 1-8.")
else
  print("wwpg start / stop / demo / volts / layout")
  print("wwpg pattern 85  (odd-numbered lamps ON)")
  print("wwpg pins 1 3 5 7")
end
