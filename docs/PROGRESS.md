# Progress — BlueJ for iPad feasibility work

Updated 2026-10-18. Repo: <https://github.com/sheltonsilas/bluej-ipad-sideload> (public, unofficial).

## Where this stands

Phase 0 (feasibility) is being closed with evidence rather than opinion. Three of the four
required proofs have reproducible evidence; the fourth (a native iOS build) is being
iterated in CI.

| # | Proof | Evidence | Status |
| --- | --- | --- | --- |
| A | ECJ compiles a Java source string to bytecode in process | `spike-java` `InMemoryCompilerTest` (2 tests) on Windows and in [CI run 39092304323](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39092304323) | Proven, test scope only |
| B | An interpreter executes compiled bytecode | `spike-java` `InterpreterTest` (3 tests): arithmetic, string constants, field state, explicit failure on an unsupported opcode | Proven, narrow prototype |
| C | Method invocation on an interpreted object | Constructor plus `increment(5) == 9`, `doubled() == 18`, field inspection; also `ci/ios-shell` `BlueJShell` self-test (6 checks) on Windows and Linux | Proven, narrow prototype |
| D | JavaFX/Java app built for iOS that yields an unsigned `.ipa` | GluonFX matrix in `.github/workflows/ios-spike.yml` | In progress |

## Local host work (Windows, no admin)

- Portable toolchain installed in user scope: Temurin `21.0.12.1+1` and Maven `3.9.16` under
  `ipados-ubuntu/tools/`, with `tools/mvn.sh` as a path-safe wrapper. No machine-wide install
  and no admin rights were used.
- Keep-awake: sleep and hibernate timeouts are 0 on AC and DC, the display sleeps after one
  minute while the system stays up, and a hidden PowerShell worker holds
  `SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED)` every 30 seconds. A watchdog
  relaunches the worker if it dies. The lid action was set to "do nothing" but this power
  scheme does not expose `LIDACTION` to `powercfg /query`, so that part is unverified; see
  [ISSUES.md](../ISSUES.md) entries H4 and the backup at `C:/Users/Ramya/power_settings_backup.txt`.
- Local evidence files: [docs/evidence/](evidence/).

## iOS build path

- `.github/workflows/ios-spike.yml` builds on macOS runners only (no local Xcode). It now runs
  a matrix over module (JavaFX hello vs. no-JavaFX runtime shell), GluonFX plugin version and
  host architecture, and uploads the app bundle, an unsigned `.ipa`, and the full native build
  log for every attempt.
- Gluon's GraalVM (`gluonhq/setup-graalvm@master`, `gluon-23+25.1-dev`, JDK 23) is required;
  `actions/setup-java` cannot provide it.
- Both modules declare `<target>${gluonfx.target}</target>` and `ios`/`ios-sim` profiles; the
  earlier workflow built nothing for iOS because the target never reached the plugin.
- Build obstacles so far, with the workaround applied, are recorded in [ISSUES.md](../ISSUES.md):
  missing GraalVM distribution, missing iOS target, an incomplete CAP cache in Substrate
  0.0.68/0.0.69, EPL-2.0 ECJ licensing, and the absence of any Apple signing identity.

## What is deliberately not claimed

- No signed, App Store-installable IPA. The build is unsigned and needs re-signing by whoever
  installs it.
- No BlueJ feature parity: [PARITY.md](../PARITY.md) still marks every product workflow
  unsupported or partial, because no BlueJ UI exists in this fork yet.
- No production architecture decision yet; see [DECISIONS.md](DECISIONS.md).
