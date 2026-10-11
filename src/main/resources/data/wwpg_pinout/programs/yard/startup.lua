-- Initialise only a newly built yard. A restart preserves native switch state.
local readings = {}
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
    local single = p.comparePin(pin, 9)
    readings[pin] = {bulk=volts[pin], single=single, clock=os.clock()}
    -- Native main-thread peripheral calls can sample different committed ticks
    -- while the saved wire entities load. Accuracy is checked by the controlled
    -- acceptance circuits, not by assuming startup calls share one snapshot.
    assert(type(volts[pin]) == "number" and volts[pin] == volts[pin] and math.abs(volts[pin]) < math.huge, "Invalid bulk voltage: " .. pin)
    assert(type(single) == "number" and single == single and math.abs(single) < math.huge, "Invalid pin voltage: " .. pin)
  end
  print("WWPG stationary computer-control yard")
  assert(shell.run("wwpg", "help"))
  assert(shell.run("wwpg", "volts"), "Could not display native voltage readings")
end)
local report = assert(fs.open("yard-ready.json", "w"))
report.write(textutils.serializeJSON({ok=ok, error=err, readings=readings}))
report.close()
if not ok then printError(err) end
