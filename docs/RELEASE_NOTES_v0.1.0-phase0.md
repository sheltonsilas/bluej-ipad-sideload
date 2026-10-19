# v0.1.0-phase0 — iOS feasibility artifacts (unsigned)

Phase 0 of the unofficial BlueJ-for-iPad feasibility spike is complete. These are **build
proofs**, not a usable IDE, and they are **unsigned**.

## What is in this release

| File | What it is | SHA-256 | Size |
| --- | --- | --- | --- |
| `HelloApp-unsigned.ipa` | GluonFX 1.0.25 + JavaFX 21.0.5 hello app, arm64, bundle id `org.bluej.ipad.hello` | `57e24a0b5d5279c9550b9632cab27892616b1bf3e24909a2140ab35fb42091a0` | 23,980,645 B |
| `BlueJShell-unsigned.ipa` | GraalVM-native shell (no JavaFX) that runs the experimental `org.bluej.ipad.spike` bytecode interpreter over an embedded compiled class, arm64, bundle id `org.bluej.ipad.shell` | `683c7b097cc4fa8a9dece1990a5f4c22dc7b0743ce63190dc5d925cb886541a5` | 15,193,593 B |
| `SHA256SUMS.txt` | Checksums for both files | — | — |

Built by [CI run 39205053650](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39205053650)
on GitHub's `macos-15`/`macos-15-intel` runners (Gluon GraalVM `gluon-23+25.1-dev`, JDK 23).

## Verified contents

Each IPA is an intact ZIP with `Payload/<app>.app` containing:

- `Info.plist` with `CFBundleSupportedPlatforms = [iPhoneOS]`, `MinimumOSVersion = 11.0`;
- an arm64 Mach-O executable (`LC_BUILD_VERSION`, `platform = 2` (iOS), SDK 18.5);
- launch screens, `Assets.car` and app icons.

`ci/tools/verify-ipa.py` asserts exactly that in CI and exits non-zero otherwise.

## Installing (requires your own signing)

These bundles carry no Apple signature, so an iPad will not install them as shipped. Use a
sideloading tool such as SideStore or AltStore with your own Apple ID to re-sign and install:

1. Download the `.ipa` and check it against `SHA256SUMS.txt`.
2. Import it into SideStore/AltStore and re-sign with your Apple ID.
3. Install. A free Apple ID provisioning profile expires after 7 days; refreshing is handled by
   the sideloading tool.

`HelloApp` shows a JavaFX window; `BlueJShell` runs the interpreter self-test and is evidence
that GraalVM-native Java hosting the interpreter works on iOS. Neither is a BlueJ IDE.

## What this release does not claim

- No BlueJ feature parity. See [PARITY.md](https://github.com/sheltonsilas/bluej-ipad-sideload/blob/main/PARITY.md): every product workflow is still unsupported or partial.
- No interactive JavaFX IDE on device. The chosen architecture is a native SwiftUI/UIKit shell
  over a GraalVM-native Java core; see
  [docs/SPIKE_REPORT.md](https://github.com/sheltonsilas/bluej-ipad-sideload/blob/main/docs/SPIKE_REPORT.md).
- Not endorsed by the BlueJ team or King's College London. GPLv2 with Classpath Exception and
  upstream notices are preserved.
