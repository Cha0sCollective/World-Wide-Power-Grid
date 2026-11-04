-- Runs on an actual CC:Tweaked computer next to the native Pinout peripheral.
local methods = 0
local ok, err = pcall(function()
  local p = assert(peripheral.find("pinout"), "Native pinout peripheral not attached")
  local function near(v, expected, message)
    assert(math.abs(v - expected) < 0.03, message .. ": " .. tostring(v))
  end
  local layout = p.pinLayout()
  methods = methods + 1
  assert(layout[1][2] == 9 and layout[2][1] == 1 and layout[5][3] == 8, "Native pin numbering changed")
  p.setByte(0)
  methods = methods + 1
  sleep(0.15)
  local states = p.pinsConnected()
  methods = methods + 1
  assert(states[9], "Common terminal must remain connected")
  local volts = p.pinsVoltage()
  methods = methods + 1
  near(volts[9], 0, "Common reference voltage")
  for pin = 1, 8 do
    assert(not states[pin], "Initial pin not open")
    near(volts[pin], -12, "Open pin signed voltage")
    near(p.comparePin(pin, 9), -12, "Independent signed pin comparison")
    near(p.comparePin(9, pin), 12, "Reversed signed comparison")
  end
  methods = methods + 1
  for pin = 1, 8 do
    p.connectPin(pin)
    sleep(0.1)
    near(p.comparePin(pin), 0, "Individual pin failed to close")
    p.disconnectPin(pin)
    sleep(0.1)
    near(p.comparePin(pin), -12, "Individual pin failed to open")
  end
  methods = methods + 2
  p.connectPin({1, 3, 5, 7})
  p.disconnectPin({1, 3, 5, 7})
  p.setPins({[1] = true, [2] = false, [3] = 1, [4] = 0, [8] = true})
  methods = methods + 1
  states = p.pinsConnected()
  assert(states[1] and not states[2] and states[3] and not states[4] and states[8], "Simultaneous pin update failed")
  for _, byte in ipairs({0, 1, 2, 4, 8, 16, 32, 64, 128, 85, 170, 165, 255}) do
    p.setByte(byte)
    sleep(0.1)
    states = p.pinsConnected()
    for pin = 1, 8 do
      local closed = math.floor(byte / 2^(pin - 1)) % 2 == 1
      assert(states[pin] == closed, "Native byte bit/pin mismatch")
      near(p.comparePin(pin), closed and 0 or -12, "Byte pattern electrical output")
    end
  end
  local function rejects(f, ...)
    assert(not pcall(f, ...), "Invalid input accepted")
  end
  rejects(p.connectPin, 9)
  rejects(p.disconnectPin, 0)
  rejects(p.setByte, -1)
  rejects(p.setByte, 256)
  rejects(p.comparePin, 10)
  rejects(p.pinsVoltage, 0)
  rejects(p.setPins, {[9] = true})
  rejects(p.setPins, {[1] = "yes"})
  p.setByte(255)
  sleep(0.15)
end)
local file = assert(fs.open("acceptance.tmp", "w"))
file.write(textutils.serializeJSON({ok = ok, error = tostring(err), methods = methods}))
file.close()
fs.move("acceptance.tmp", "acceptance.json")
if not ok then error(err) end
