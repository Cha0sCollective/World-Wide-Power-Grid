# 0.2.8-beta.3

- Fix large-world loading stalls caused by repeatedly scanning CEE circuits during restoration. Keep rollback ownership and unavailable-reading safeguards intact.
- Restore Power Grid's original generator, rotor and clutch timing. Remove WWPG's stabilization introduced in 0.2.7; self-excited generators can again oscillate under the same conditions as stock PG, as intended upstream.
- Replace stabilization-specific checks with native-response comparisons while retaining the independent unload, circuit regrouping, measurement and native solver safety fixes.

654 headless jobs and all 12 graphical scenarios passed on the frozen beta.3 runtime. Prior beta evidence continues to describe its original runtime.
