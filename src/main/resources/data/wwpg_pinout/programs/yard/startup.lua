-- Initialise only a newly built yard. A restart preserves native switch state.
local ok, err = pcall(function()
  local p = assert(peripheral.find("pinout"), "Pinout has not attached")
  if not fs.exists(".yard-built") then
    assert(shell.run("wwpg", "start"), "Could not start the yard demonstration")
    local marker = assert(fs.open(".yard-built", "w"))
    marker.write("Native pin states remain unchanged on later boots.\n")
    marker.close()
  end
  sleep(0.3)
  local states, volts = p.pinsConnected(), p.pinsVoltage(9)
  assert(states[9], "Native common pin is disconnected")
  for pin = 1, 8 do
    assert(math.abs(p.comparePin(pin, 9) - volts[pin]) < 0.05, "Native pin readings disagree: " .. pin)
  end
  print("WWPG stationary computer-control yard")
  assert(shell.run("wwpg", "help"))
end)
local report = assert(fs.open("yard-ready.json", "w"))
report.write(textutils.serializeJSON({ok=ok, error=err}))
report.close()
if not ok then printError(err) end
