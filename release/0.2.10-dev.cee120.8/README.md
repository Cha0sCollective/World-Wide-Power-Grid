# CEE 1.2 out-of-band alpha preparation

The alpha prerelease retains the tested `0.2.10-dev.cee120.8` version and exact
player jar, SHA256
`2dfe78d0b18690753ec68dd1ed4de2791cd4925fa72789b741fff4fdfd4394d0`.
The stable release remains 0.2.10. The owner explicitly authorized
"Allow a focused-check-only alpha prerelease" for this exact build. This is not
completed full release acceptance.

The candidate has thirteen focused unit checks, two applied-coordinate checks
on each of CEE 1.2.0-132, 1.1.4 and 1.1.3, and client loading with Power Dock
0.3.0-preview and Sable 2.0.5. A negative control rejects the unchanged `.7`
coordinate behavior. In-world sublevel visuals have not been confirmed on `.8`.

Full headless acceptance belongs to the distinct `.5` player. `.8` changes player
code, so its passing focused observations do not qualify for version-only reuse.
Full graphical acceptance has not been run. The normal publication gate therefore
remains unsatisfied. The owner-approved exception permits this exact alpha only.

Keep original `.5` and `.8` artifact and evidence identities. Do not launch full
suites or change the gate merely as part of packaging. Public staging and
publication use `tools/release_downloads.py`. Its alpha exception is restricted
to this version and exact runtime hash, with locked focused proofs and mandatory
public disclosure. Other releases retain all normal acceptance gates. Bundled
worlds are unchanged historical `.5` headless exports, not saves retested on `.8`.
