# v0.2.0-phase1 — interpreted runtime and core contract (unsigned)

Second feasibility milestone. The iOS builds are still **build proofs, not a BlueJ IDE**, and
they are **unsigned**: an iPad will only install them after re-signing with your own Apple ID.

## What changed since v0.1.0-phase0

- **The interpreter runs the BlueJ-intro bytecode subset.** Branches, loops, `switch`,
  recursion, instance and static fields, static initialisers, `int`/object arrays, string
  concatenation, `String`/`Character`/`Math`/`Integer`/`Double` intrinsics, `System.out`
  capture, doubles, and `try`/`catch` over user and runtime exceptions, all bounded by an
  instruction budget that stops runaway loops. `long`, `float`, threads and most of the JDK
  fail explicitly as unsupported.
- **A tested Java ↔ Swift contract.** `org.bluej.ipad.core` exposes class metadata, bench
  handles, construction, instance/static calls, field snapshots and console capture, plus a
  JSON protocol documented in [CORE_PROTOCOL.md](CORE_PROTOCOL.md).
- **Evidence:** 23 JVM tests and an 18-check self-test of the iOS entry point
  ([interpreter conformance](evidence/interpreter-conformance.md),
  [core protocol](evidence/core-session-protocol.md),
  [shell self-test](evidence/ios-shell-host-selftest.md)).

## Files

| File | What it is | SHA-256 | Size |
| --- | --- | --- | --- |
| `BlueJShell-unsigned.ipa` | GraalVM-native arm64 shell running the extended interpreter and the JSON core protocol over an embedded class, bundle id `org.bluej.ipad.shell` | `078af92ae1df4cda8b12b9bb70c34a8f00f9af6b3497de71967ad057a85bdd21` | 15,208,010 B |
| `HelloApp-unsigned.ipa` | GluonFX JavaFX hello app, arm64, bundle id `org.bluej.ipad.hello` | `4a74c93f1e26597266c1fdf3cdd2652f7e9952d43ebc931ca1c41086e31984c6` | 23,986,658 B |
| `SHA256SUMS.txt` | Checksums for both files | — | — |

Built by [CI run 39208038985](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39208038985)
(Gluon GraalVM `gluon-23+25.1-dev`, GluonFX 1.0.25 / Substrate 0.0.65, Xcode on
`macos-15`/`macos-15-intel`).

## Verified contents

Each IPA is an intact ZIP with `Payload/<app>.app` containing `Info.plist`
(`CFBundleSupportedPlatforms = [iPhoneOS]`, `MinimumOSVersion = 11.0`) and an arm64 Mach-O
executable (`LC_BUILD_VERSION`, `platform = 2` (iOS), SDK 18.5). `ci/tools/verify-ipa.py`
asserts this in CI for every build.

## Installing (requires your own signing)

1. Download the `.ipa` and check it against `SHA256SUMS.txt`.
2. Import it into SideStore/AltStore and re-sign with your Apple ID.
3. Install. Free-provisioning profiles expire after 7 days; the sideloading tool refreshes.

`BlueJShell` runs its self-test and prints one `PASS`/`FAIL` line per check plus
`SELFTEST RESULT`. `HelloApp` shows a JavaFX window. Neither is a BlueJ IDE.

## Not claimed

- No BlueJ feature parity; see [PARITY.md](https://github.com/sheltonsilas/bluej-ipad-sideload/blob/main/PARITY.md).
- No interactive JavaFX IDE on device; the chosen path is a native SwiftUI shell over this
  Java core, described in
  [docs/SPIKE_REPORT.md](https://github.com/sheltonsilas/bluej-ipad-sideload/blob/main/docs/SPIKE_REPORT.md).
- Not endorsed by the BlueJ team or King's College London. GPLv2 with Classpath Exception and
  upstream notices preserved.
