# BlueJ for iPad — Phase 0 feasibility spike

**Date:** 2026-10-17
**Status:** A/B/C locally proven at limited prototype scope; D (iOS build) awaits CI evidence.
**Purpose:** Time-boxed investigation of the technical risks before attempting the two ordered public releases.

## Required proofs and results

| Required proof | Status | Evidence |
| --- | --- | --- |
| JavaFX hello app packaged/built for iOS using Gluon/GraalVM, in CI | **PENDING CI** | A GluonFX Maven JavaFX hello project and `macos-15` workflow are committed. No successful or failed workflow run has been recorded yet, so this proof is not passed. |
| ECJ compiles a Java source string in process | **PASS — local proof** | Temurin 21 + Maven test `InMemoryCompilerTest`: 2 tests, 0 failures/errors/skips. ECJ 3.40.0 compiles source in-process and emits `CAFEBABE` class bytes; invalid source produces diagnostics. ECJ is EPL-2.0 and test-scope only, not approved for the GPL app. |
| A minimal interpreter executes bytecode | **PASS — limited prototype** | `InterpreterTest`: arithmetic (26), string constant, field state, and explicit failure on unsupported conditional bytecode. The interpreter supports only a narrow opcode subset and is not a JVM. |
| An interpreted object supports method invocation | **PASS — limited prototype** | Constructor invoked interpreted `<init>`; object method `increment(5)` returned 9 and `doubled()` returned 18; field state inspected. Test results: 3 interpreter tests, all passing. This is an interpreter API, not Java reflection. |

### Test details

The local proofs ran on a portable Temurin 21 JDK. Maven result: 5 tests, 0 failures, 0 errors, 0 skipped. These do **not** establish full Java/JVM compatibility, BlueJ integration, GraalVM native-image support, or iOS operation.

## Technical assessment

A faithful port is a large runtime replacement, not just a JavaFX skin. The upstream BlueJ architecture depends on Java/Javac and a controlled second VM/JDI workflow, while the target iOS environment imposes restrictions on JIT, subprocesses and dynamic loading. The central unproven dependency is the student-code execution/debugging model: the port needs a verified bytecode subset and a safe in-process runtime that exposes the object bench, constructor/method invocation, state inspection, breakpoints and stepping with BlueJ-observable semantics.

No production architecture is selected. An explicit interpreter is a plausible research direction, but its current proof covers only a few bytecodes. ECJ's EPL-2.0 license prevents use in the GPL application under the project's stated policy unless it is replaced or separately reviewed. The GluonFX Maven hello build is the first iOS feasibility attempt; no native build result has been observed. Do not promise identical JIT/interpreter results or BlueJ parity without a conformance suite.

## Main risks / blockers

1. Native iOS verification requires GitHub's macOS runner; local Windows cannot run Xcode.
2. GluonFX Maven job is committed but has not yet produced a CI run or `.ipa`.
3. ECJ 3.40.0 is EPL-2.0 and test-scope only; it is not approved for the GPL app.
4. Interpreter lacks most Java behavior: branches, arrays, exceptions, static initialization, I/O, threads, safe cancellation, debugger semantics, and broad reflection.
5. Preserve upstream GPLv2 with Classpath Exception and all copyright/third-party notices. GitHub reports upstream metadata as `NOASSERTION`, while checked-in `LICENSE.txt` explicitly states the license.
6. Repo 1 exists publicly at https://github.com/sheltonsilas/bluej-ipad-sideload and includes full upstream source at commit `bf11ce44a6dcb866921485dd43fb31411dc32937`. No release, IPA, checksum, or verify-gate evidence exists.

## Result

**Phase 0 remains INCOMPLETE.** A/B/C have local prototype evidence, but D requires an actual macOS CI run and successful unsigned iOS artifact validation. The proposed architecture is not validated. Repo 2 remains gated by Repo 1's verify gate. No BlueJ product parity or release is claimed.

## CI attempts

- Attempt 1: GluonFX Maven plugin `1.0.25`, JavaFX `21.0.5`, Temurin 21 on `macos-15`; workflow committed, run pending.
- Not yet attempted: GluonFX Gradle plugin, alternate Maven/plugin versions, and a minimal non-JavaFX iOS shell.
