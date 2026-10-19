# BlueJ for iPad — decisions

## 2026-10-17 — upstream snapshot and project identity

- Upstream: <https://github.com/k-pet-group/BlueJ-Greenfoot>, inspected commit `bf11ce44a6dcb866921485dd43fb31411dc32937`.
- Repo 1 created publicly at <https://github.com/sheltonsilas/bluej-ipad-sideload> using the complete upstream snapshot at that commit. Original source headers, upstream `LICENSE.txt`, and third-party notices remain intact.
- Preserve GPL version 2 with Classpath Exception. The port is unofficial and not endorsed by the BlueJ team or King's College London. Its name/logo remain pending permission and will be changed on request; README and NOTICE state this.
- Repo 2 stays gated behind the Repo 1 verify gate.

## 2026-10-17 — Phase 0 prototype scope

- ECJ 3.40.0's Maven metadata declares EPL-2.0. It is test-scope only for the compile feasibility proof, not approved for incorporation into or distribution with the GPL application. Replace it or obtain qualified license review before product integration.
- The bytecode interpreter is an experimental, explicit opcode subset. It uses ASM 9.7 (BSD-3-Clause) to decode class files. Unsupported bytecodes fail explicitly; this is not a Java VM or BlueJ parity claim.
- A/B/C have local prototype tests. D requires a macOS runner; no iOS workflow result or `.ipa` currently exists.
- No production architecture has been selected. Keep the upstream desktop UI/source intact while the native UI and runtime path are investigated; do not claim port parity until user workflows have tests.

## 2026-10-19 — Phase 0 closed; GluonFX version pin; architecture selected

- Proof D is proven: CI run 39205053650 built and packaged two arm64 iOS apps with
  GluonFX 1.0.25 and released their unsigned `.ipa` files as `v0.1.0-phase0`. Verification in
  CI asserts an intact archive, an `iPhoneOS` `Info.plist` (min OS 11.0) and an arm64 Mach-O
  executable with `LC_BUILD_VERSION platform=2`.
- **Pin GluonFX 1.0.25 / Substrate 0.0.65.** Substrate 0.0.66+ no longer ships
  `RISCV64LibCHelperDirectives.cap` for iOS (`JNIHeaderDirectivesJDK22OrLater.cap` is also
  missing), so native-image aborts on an incomplete CAP cache. Disabling the cache and
  redirecting `-H:CAPCacheDir` at extracted jars both failed; the older plugin is the
  working toolchain. Revisit only if a newer Substrate ships a complete cache again.
- **`<skipSigning>true</skipSigning>` is intentional.** No Apple identity or provisioning
  profile exists for this repository, so the `.app` is assembled but not signed and the `.ipa`
  must be re-signed by whoever installs it. This is a distribution limitation, not a build
  failure.
- **Architecture: native SwiftUI/UIKit shell over a GraalVM-native Java core that hosts the
  interpreter.** JavaFX cannot run interactively on iOS (no AWT/Swing, no Prism on a no-JIT
  device); the JavaFX build proves the toolchain, not an IDE. The interpreter gains the
  BlueJ-intro bytecode subset and an instruction budget for cancellation. Debugging must be
  interpreter-native because JDI cannot attach to interpreted frames.
- ECJ remains test-scope and EPL-2.0. Product compilation needs a GPL-compatible in-process
  compiler (the bundled OpenJDK javac is the leading candidate) before any integration.
