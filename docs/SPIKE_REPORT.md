# BlueJ for iPad — Phase 0 feasibility spike

**Date:** 2026-10-19
**Status:** A/B/C/D proven. Phase 0 COMPLETE. Nothing here is a parity claim.
**Purpose:** Time-boxed investigation of the technical risks before attempting the ordered public releases.

## Required proofs and results

| Required proof | Status | Evidence |
| --- | --- | --- |
| JavaFX (and plain Java) app built for iOS with Gluon/GraalVM in CI, producing an unsigned `.ipa` | **PASS — CI** | [run 39205053650](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39205053650): three green jobs (`plain-java 1.0.25`, `javafx 1.0.25`, `javafx 1.0.25 arm64 host`). Each produced `Payload/<app>.app` with `Info.plist` (`MinimumOSVersion` 11.0, `CFBundleSupportedPlatforms` `iPhoneOS`) and an arm64 Mach-O executable (`LC_BUILD_VERSION platform=2`, SDK 18.5) and a SHA-256 checksum. `ci/tools/verify-ipa.py` asserts the bundle in CI. |
| ECJ compiles a Java source string in process | **PASS — local + CI** | Temurin 21 + Maven test `InMemoryCompilerTest`: 2 tests, 0 failures/errors/skips; ECJ 3.40.0 emits `CAFEBABE` class bytes and diagnostics for invalid source. Also green in [run 39205053696](https://github.com/sheltonsilas/bluej-ipad-sideload/actions/runs/39205053696) (Java feasibility tests). ECJ is EPL-2.0 and test-scope only, not approved for the GPL app. |
| A minimal interpreter executes bytecode | **PASS — limited prototype** | `InterpreterTest`: arithmetic (26), string constant, field state, and explicit failure on unsupported conditional bytecode. The interpreter supports only a narrow opcode subset and is not a JVM. |
| An interpreted object supports method invocation | **PASS — limited prototype** | Constructor invoked interpreted `<init>`; `increment(5)` returned 9 and `doubled()` returned 18; field state inspected. This is an interpreter API, not Java reflection or JDI. |

### Artifacts that prove D

Both files are **unsigned** and need re-signing (e.g. SideStore/AltStore with the user's own Apple ID) before an iPad will install them.

| File | SHA-256 | Size | Contents |
| --- | --- | --- | --- |
| `HelloApp-unsigned.ipa` | `57e24a0b5d5279c9550b9632cab27892616b1bf3e24909a2140ab35fb42091a0` | 23,980,645 B | GluonFX 1.0.25 JavaFX 21.0.5 hello app, arm64, `org.bluej.ipad.hello` |
| `BlueJShell-unsigned.ipa` | `683c7b097cc4fa8a9dece1990a5f4c22dc7b0743ce63190dc5d925cb886541a5` | 15,193,593 B | GraalVM-native shell (no JavaFX) that runs the `org.bluej.ipad.spike` interpreter over an embedded compiled class, arm64, `org.bluej.ipad.shell` |

The native builds are reproducible from `.github/workflows/ios-spike.yml`; the artifacts are attached to the `v0.1.0-phase0` release.

## CI attempts (in order)

| # | Attempt | Result |
| --- | --- | --- |
| 1 | GluonFX 1.0.25 + Temurin 21 via `actions/setup-java` | Failed at setup: `setup-java` has no `graalvm-community` distribution and no iOS-capable GraalVM. |
| 2 | GluonFX Maven plugin, default target | Failed: nothing was ever targeted at iOS; `gluonfx:build` built a macOS binary. |
| 3 | `target=ios` via a Maven profile, plugin 1.0.25 | `native-image` aborted: `Could not load CAPCache file ... RISCV64LibCHelperDirectives.cap`. Substrate 0.0.66+ no longer ships that file for iOS. |
| 4 | Plugin 1.0.28/0.0.68 with `-H:-UseCAPCache` | Still aborted: Substrate always appends `-H:+UseCAPCache`, and the CAP cache it generated lacked `JNIHeaderDirectivesJDK22OrLater.cap`. |
| 5 | `-H:CAPCacheDir` pointed at `.cap` files extracted from the substrate jar | Still failed: the extracted set was produced for the plugin's own JDK, not for the Gluon GraalVM 23 build. |
| 6 | Pin GluonFX **1.0.25** (Substrate 0.0.65, complete iOS CAP cache) | **Build succeeded** for the JavaFX hello app and for the plain-Java interpreter shell, both `arm64-apple-ios`. |
| 7 | Add `gluonfx:package` with `<skipSigning>true</skipSigning>` | **Produced a real `.ipa`**: signing is skipped, `Payload/*.app` contains `Info.plist`, the `Assets.car`/launch screens and the executable. |
| 8 | `ci/tools/verify-ipa.py` in CI | First run exposed two bugs in the verifier itself (header unpack width, `LC_VERSION_MIN_IPHONEOS` constant); after the fix both IPAs verify as arm64, platform iOS. |

GluonFX 1.0.25 / Substrate 0.0.65 is therefore pinned in the workflow and POMs. Newer plugins can be revisited once their iOS CAP cache is complete again.

## Technical assessment

A faithful port is a large runtime replacement, not just a JavaFX skin. The upstream BlueJ architecture depends on `javac`, a separate VM/JDI workflow, and unrestricted class loading/JIT, while iOS forbids JIT, `fork`, and unsigned dynamic code. The central dependency is the student-code execution/debugging model: the port needs a bytecode engine with BlueJ-observable semantics (object bench, constructor/method invocation, state inspection, breakpoints, stepping).

## Chosen architecture (Phase 1 input)

Decision: **a GraalVM-native iPad app that hosts an in-process bytecode runtime, with a Swift/UIKit shell around a Java core**, rather than shipping desktop BlueJ or a JVM.

1. **UI shell** — native SwiftUI/UIKit (class diagram, editor, bench), driven by a Java/Kotlin core through a thin interface; JavaFX itself does not run natively on iOS (no AWT/Swing, no Prism on a no-JIT device), and the GluonFX JavaFX path proves only that a JavaFX *build* is possible, not that an interactive BlueJ IDE is.
2. **Execution** — the `org.bluej.ipad.spike` interpreter, extended to the subset BlueJ exercises (branches, arrays, exceptions, strings, `java.lang.*` intrinsics), with instruction budgets for cancellation. No JVM/JIT dependency.
3. **Compilation** — an in-process compiler. ECJ 3.40.0 is EPL-2.0 and cannot ship in this GPLv2+CE app, so Phase 1 must either use a GPL-compatible compiler (e.g. the OpenJDK javac tree already present in the upstream repository) or obtain license review; until then the interpreter runs checked-in class files.
4. **Debugging** — a first-party interpreter debug hook (breakpoints/step/variables on the interpreter's own frames). JDI cannot attach to interpreted frames and is unsupported on iOS.
5. **Project model** — reuse BlueJ's own project/`package.bluej` classes where they do not assume a filesystem-javac-JDI triad.

## Main risks / blockers

1. Interpreter coverage is the largest schedule risk; the current proof covers ~30 opcodes, with no branches, arrays, exceptions, static init, threads, I/O, or synchronization.
2. Debugger parity: JDI-based stepping and variable views cannot be preserved; only an interpreter-native debugger is possible.
3. Compiler licensing: ECJ is EPL-2.0; must be replaced or reviewed before shipping.
4. iOS distribution: unsigned IPAs need re-signing; App Store distribution requires an Apple account and would also require the BlueJ name/logo permission question to be settled.
5. Preserve upstream GPLv2 with Classpath Exception and all copyright/third-party notices. GitHub reports upstream metadata as `NOASSERTION`, while checked-in `LICENSE.txt` states the license.
6. The upstream BlueJ UI is Swing; the native rewrite is a product-scale effort, not a port of existing frames.

## Result

**Phase 0 is COMPLETE.** A, B and C have local and CI evidence; D has three green macOS jobs producing verified unsigned arm64 iOS IPAs, released as `v0.1.0-phase0`. No BlueJ product parity is claimed; see [PARITY.md](../PARITY.md).

## Phase 1 progress

1. **Interpreter subset (done, first slice).** The interpreter now executes branches, loops,
   `switch`, recursion, instance and static fields, static initialisers, int/object arrays,
   string literals and concatenation, `String`/`Character`/`Math`/`Integer`/`Double`
   intrinsics, `System.out` capture, double arithmetic, and `try`/`catch` over user and runtime
   exceptions (including `/ by zero` and array bounds), with an instruction budget that stops
   runaway loops. Evidence: 13 local tests in `spike-java`
   ([docs/evidence/interpreter-conformance.md](evidence/interpreter-conformance.md)) and a
   15-check host/CI self-test of the iOS entry point
   ([docs/evidence/ios-shell-host-selftest.md](evidence/ios-shell-host-selftest.md)). `long`,
   `float`, threads, synchronization and most of the JDK remain explicitly unsupported.
2. **Next: the Java-core ↔ Swift-UI contract.** Define compile/class-list/bench/inspector/
   console operations and a headless harness that exercises them without a UI, so the iOS
   shell can be built against a stable boundary.
3. **Then: replace ECJ for product use.** Evaluate the bundled OpenJDK javac for in-process
   compilation under GPLv2+CE; ECJ stays test-scope until then.
4. **Then: interpreter debug hook.** Breakpoints, stepping and variable views on interpreted
   frames, with tests that do not depend on JDI.
5. **Always: keep the iOS CI green.** Every push must keep the macOS matrix producing a
   verified arm64 IPA; attach each milestone build to a release.
