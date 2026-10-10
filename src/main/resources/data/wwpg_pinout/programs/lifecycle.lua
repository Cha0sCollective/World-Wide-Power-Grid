-- Runs unchanged in the real CC:Tweaked VM. Inspect saved pins before writing.
local function report(path, value)
  local file = assert(fs.open(path .. ".tmp", "w"))
  file.write(textutils.serializeJSON(value))
  file.close()
  if fs.exists(path) then fs.delete(path) end
  fs.move(path .. ".tmp", path)
end
local function near(actual, expected)
  assert(math.abs(actual - expected) < 0.03, tostring(actual) .. " != " .. tostring(expected))
end
local ok, err = pcall(function()
  local p = assert(peripheral.find("pinout"), "Pinout did not attach after loading")
  local state = p.pinsConnected()
  local before = 0
  for pin = 1, 8 do if state[pin] then before = before + 2^(pin - 1) end end
  -- First installation has no saved state; every later boot must retain 85.
  local boots = tonumber(fs.exists("boots.txt") and fs.open("boots.txt", "r").readAll() or "0")
  assert(before == (boots == 0 and 0 or 85), "Saved pins changed before the first Lua write")
  p.setByte(0)
  sleep(0.2)
  for pin = 1, 8 do near(p.comparePin(pin, 9), -12) end
  p.setByte(255)
  sleep(0.3)
  for pin = 1, 8 do near(p.comparePin(pin, 9), 0) end
  p.setByte(85)
  sleep(0.3)
  local voltages = p.pinsVoltage()
  local file = assert(fs.open("boots.txt", "w")); file.write(tostring(boots + 1)); file.close()
  report("lifecycle.json", {ok = true, before = before, boots = boots + 1, voltages = voltages})
  -- The harness removes/replaces the block without restarting this computer.
  local event, side = os.pullEvent("peripheral_detach")
  report("detached.json", {ok = true, side = side})
  event, side = os.pullEvent("peripheral")
  p = assert(peripheral.find("pinout"), "Replacement did not attach")
  for pin = 1, 8 do assert(not p.pinsConnected()[pin], "Replacement inherited old switch state") end
  report("attached.json", {ok = true, side = side})
  -- Keep all new switches open until the game has checked for ghost power.
  os.pullEvent("wwpg_replacement_ready")
  p.setByte(85)
  sleep(0.3)
  report("replacement.json", {ok = true, voltages = p.pinsVoltage()})
end)
if not ok then report("lifecycle-error.json", {ok = false, error = tostring(err)}); error(err) end
