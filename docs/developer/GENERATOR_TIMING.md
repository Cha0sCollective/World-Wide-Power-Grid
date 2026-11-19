> Historical: this describes 0.2.7 through 0.2.8-beta.2. The prepared beta.3
> removes this timing model and restores stock PG behavior, including oscillation.
> See [current upstream behavior](../UPSTREAM_BEHAVIOR.md).

# Self-excited generator timing (WWPG 0.2.7)

The correction in WWPG 0.2.7 advances a recognized
PG generator's rotor response and winding excitation together with its electrical
solution. PG's previous schedule sampled field strength and rotor speed before
solving, then applied torque later. A short field time constant could turn that
delay into sustained hunting, even with sixteen electrical substeps.

Each numerical trial evaluates the native winding curve and the native sequential
clutch force law without changing committed rotor or winding history. Only an
accepted electrical solution advances the existing rotor controller. The normal
block tick still performs synchronization, saving, rendering, damage and overspeed
handling. Nearly-stopped speed filtering remains once per game tick, before drive.

Clutch control keeps a separate clock from physical integration. Its rotor-speed
sample is captured once at the start of the game tick, and its sequential force
prediction retains PG's 50 ms horizon and previous-tick speed input. Each accepted
short electrical step applies those trial forces over its actual physical dt.
The current trial's electrical torque still participates in the prediction.
This preserves the native constant-load response and near-target operating points
without holding the physical rotor or winding state for the entire game tick.

Using the short electrical dt for both calculations changed clutch droop in the
earlier dev.127 experiment, dropping the carbon-pile fixture from about 865 V to
840 V. Retaining only the 50 ms horizon while resampling moving rotor speed every
substep would still change the controller's transient response. Focused mechanics
tests therefore compare both the force sequence and full-tick response with
native integration, including unsaturated operation in either direction.

The nonlinear source equation includes copper resistance and the trial rotor's
induced voltage. The old inertia companion is omitted on this path because the
implicit rotor response already accounts for inertia. The recognized network has
one nonlinear source; its remaining equations give a linear load line during each
physical step. Two full electrical trials identify that response. A bounded search
then brackets the source equation's crossing along the load line and directs the
normal solver toward it. This avoids creeping through an unstable field build-up
after excitation is restored at full rotor speed. Initial states and backtracking
blends cannot supply that response when a variac has just changed its constraints.
A bracketed discontinuity is rejected as a search target. Every acceptance check
evaluates the complete nonlinear equation at the state actually being checked,
using PG's ordinary tolerance and iteration limit. Rejected trials remain
unavailable and cannot commit electrical torque.

The generator owns the applied nonlinear matrix stamp as well as its residual
compensation. It uses the solver's exact coefficient update inside the usual
row-exchange boundary. PG's general matrix-adjustment helper discards changes
below `1e-9`; recording those discarded values as applied would accumulate drift
and could make an inaccurate physical source equation appear converged. The
focused variac regressions independently check the actual source equation after
accepted solves, in addition to checking availability and visible recovery.

Recognition is deliberately conservative: a single native generator source per
controller, native generator-mode clutches, native winding calculations in the
same electrical network, assigned non-leaf field terminals and a healthy prepared
network with no additional nonlinear hooks. Custom rotors or force suppliers, motor-mode clutches, independently
supplied field networks and unsupported calculation graphs retain PG's native
path. This is not a universal stabilization of every possible generator circuit.

`GeneratorTimingGameTests` covers a real six-rotor assembly, a 50:10 field
transformer, load changes and stop/restart recovery, plus unchanged direct/custom
rotor execution in both directions. A separate one-substep regression removes
excitation through shaft-driven and redstone variacs while the rotor stays at full
speed, then requires re-excitation and restored output without solve failures.
`ExcitationSearchTest` covers coherent trial ownership, both polarities, positive
feedback build-up, missing roots and discontinuities. `RotorStepTest` checks the native clutch law,
force limits, weak drive, friction and invalid inputs. Run the focused
`wwpg_generator_timing` namespace on both solvers; it has its own isolated
`generator-timing` catalog group so deliberate failure tests cannot contaminate
its healthy-world diagnostic assertions.
Full headless and graphical acceptance remain separate manual requests.

The carbon-pile regression includes the three housing-corner links joining all
72 winding blocks. It asserts the actual 540-ohm/5.4-H winding before comparing
native operating points under original, heavier and lighter loads and restart.
A fixture with only one 18-coil face connected cannot establish that comparison.

Native automatic tap regulation is retained. Its discrete control can produce
smaller voltage steps in a self-excited circuit even when fixed excitation has
settled; generator timing is not a promise of ripple-free automatic regulation.
