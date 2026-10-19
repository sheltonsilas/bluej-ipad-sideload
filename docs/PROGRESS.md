# Progress — BlueJ for iPad feasibility work

Updated 2026-10-19. Repo: <https://github.com/sheltonsilas/bluej-ipad-sideload> (public, unofficial).
Latest release: [`v0.2.0-phase1`](https://github.com/sheltonsilas/bluej-ipad-sideload/releases/tag/v0.2.0-phase1);
first proof: [`v0.1.0-phase0`](https://github.com/sheltonsilas/bluej-ipad-sideload/releases/tag/v0.1.0-phase0).

## Where this stands

**Phase 0 (feasibility) is complete.** All four required proofs have reproducible evidence,
the last two of them in CI, and the resulting unsigned iOS builds are released with checksums.

| # | Proof | Evidence | Status |
| --- | --- | --- | --- |
| A | ECJ compiles a Java source string to bytecode in process | `spike-java` `InMemoryCompilerTest` (2 tests) on Windows and in [run 39205053696](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39205053696) | Proven, test scope only |
| B | An interpreter executes compiled bytecode | 11 `InterpreterTest` cases: arithmetic, strings, branches/loops/switch, recursion, fields, arrays, statics, doubles, exceptions, output, budget, explicit failure on unsupported bytecode | Proven, now the BlueJ-intro subset |
| C | Method invocation on an interpreted object | Constructor plus `increment(5) == 9`, `doubled() == 18`, field inspection; `BlueJShell` host self-test 18/18 including three protocol calls | Proven |
| D | Java app built for iOS that yields an unsigned `.ipa` | [run 39205053650](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39205053650): three green macOS jobs, arm64 iOS IPAs verified in CI by `ci/tools/verify-ipa.py` | Proven |

## Released artifacts

Both are **unsigned** and must be re-signed (e.g. with SideStore/AltStore and the user's own
Apple ID) before an iPad installs them. They are evidence that the toolchain works, not a
usable IDE.

| File | SHA-256 | Size |
| --- | --- | --- |
| `HelloApp-unsigned.ipa` | `4a74c93f1e26597266c1fdf3cdd2652f7e9952d43ebc931ca1c41086e31984c6` | 23,986,658 B |
| `BlueJShell-unsigned.ipa` | `078af92ae1df4cda8b12b9bb70c34a8f00f9af6b3497de71967ad057a85bdd21` | 15,208,010 B |

## Local host work (Windows, no admin)

- Portable toolchain in user scope: Temurin `21.0.12.1+1` and Maven `3.9.16` under
  `ipados-ubuntu/tools/`, with `tools/mvn.sh` as a path-safe wrapper. No admin rights used.
- Keep-awake: sleep and hibernate timeouts 0 on AC and DC, display sleeps after one minute
  while the system stays up, and a hidden PowerShell worker holds
  `SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED)` every 30 s with a watchdog
  that relaunches it. The lid action was set to "do nothing" but this scheme does not expose
  `LIDACTION` to `powercfg /query`; see ISSUES.md H4.

## iOS build path

- `.github/workflows/ios-spike.yml` builds on macOS runners only (no local Xcode), matrix:
  interpreter shell on `macos-15-intel`, JavaFX hello on `macos-15-intel` and on `macos-15`.
- Gluon's GraalVM (`gluonhq/setup-graalvm@master`, `gluon-23+25.1-dev`, JDK 23) is required;
  `actions/setup-java` cannot provide it.
- **GluonFX 1.0.25 / Substrate 0.0.65 is pinned**: newer Substrate versions dropped CAP-cache
  files the Gluon GraalVM needs, so native-image aborts with `Could not load CAPCache file`.
  The full attempt history is in [SPIKE_REPORT.md](SPIKE_REPORT.md).
- `gluonfx:package` runs with `<skipSigning>true</skipSigning>` and produces the real `.app`
  bundle plus `.ipa`; CI then asserts the archive is intact, has an `Info.plist` for
  `iPhoneOS`, and contains an arm64 Mach-O executable with `LC_BUILD_VERSION platform=2`.

## Phase 1: interpreter subset (first slice complete)

The architecture and ordered task list are in
[SPIKE_REPORT.md](SPIKE_REPORT.md#chosen-architecture-phase-1-input): native SwiftUI/UIKit shell,
GraalVM-native Java core, extended interpreter, interpreter-native debugger, and replacement of
the EPL-2.0 ECJ dependency before any product use.

The interpreter now runs the BlueJ-intro subset: branches, loops, `switch`, recursion, instance
and static fields, static initialisers, int/object arrays, string concatenation,
`String`/`Character`/`Math`/`Integer`/`Double` intrinsics, `System.out` capture, doubles, and
`try`/`catch` over user and runtime exceptions, with an instruction budget that stops runaway
loops. Evidence: 13 local tests ([docs/evidence/interpreter-conformance.md](evidence/interpreter-conformance.md))
and a 15-check self-test of the iOS entry point ([docs/evidence/ios-shell-host-selftest.md](evidence/ios-shell-host-selftest.md)).
`long`, `float`, threads, synchronization and most of the JDK are still unsupported and fail
explicitly.

The Java-core ↔ Swift-UI contract is now defined and tested: `org.bluej.ipad.core` exposes a
synchronous, UI-free session (class metadata, bench handles, construction, calls, field
snapshots, console capture) and a JSON protocol for the native bridge, documented in
[CORE_PROTOCOL.md](CORE_PROTOCOL.md) with 10 headless tests
([docs/evidence/core-session-protocol.md](evidence/core-session-protocol.md)). The iOS shell
runs three protocol calls in its own self-test (18 checks total).

Next: the SwiftUI shell skeleton against a recorded-protocol stub, then the interpreter debug
hook, then replacing ECJ for product compilation.

## What is deliberately not claimed

- No signed, App Store-installable IPA.
- No BlueJ feature parity: [PARITY.md](../PARITY.md) still marks every product workflow
  unsupported or partial, because no BlueJ UI exists in this fork yet.
