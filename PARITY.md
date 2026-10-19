# BlueJ feature parity tracker

Status tracks the iPad application, not the isolated Java spike. **No application feature is complete.** The tests under `spike-java/` are runtime feasibility experiments and the `ci/` apps are build proofs; neither constitutes BlueJ UI/product parity.

| BlueJ workflow | Status | Evidence / current behavior |
| --- | --- | --- |
| Create/open/save projects and `package.bluej` metadata | UNSUPPORTED | No application UI or project persistence. |
| Class diagram editing, relationships and arrows | UNSUPPORTED | No diagram UI. |
| Compile button and javac-style diagnostics | PARTIAL | Test-only ECJ compiles a source string and reports diagnostics; ECJ license is EPL-2.0, excluded from the distributable application. No BlueJ editor/UI integration. |
| Uncompiled-class striping | UNSUPPORTED | No class diagram UI. |
| Code editor, syntax highlighting and editing actions | UNSUPPORTED | No application UI. |
| Constructor dialog and object bench | PARTIAL | The interpreter constructs objects, runs their constructors and retains interpreted instances for later calls; there is no UI or project-class integration. |
| Method calls, parameters and return values | PARTIAL | Interpreted calls cover parameters/returns, recursion, loops, branches, switch, int/object arrays, doubles, strings and `try`/`catch`; 11 interpreter tests and a 15-check device entry point. Unsupported bytecode (e.g. `long`) fails explicitly. |
| Inspector and object reference navigation | PARTIAL | The runtime exposes instance fields (including array contents via `InterpretedArray.snapshot()`); no UI, no reference graph, no computed properties. |
| Terminal and Scanner input | PARTIAL | `System.out.print`/`println` are captured into the runtime's output buffer; there is no console UI and no `Scanner`/stdin support. |
| Static method calls | PARTIAL | Static fields, static initialisers and static methods work, including static state shared across instances; no UI integration. |
| Introductory language subset (loops, arrays, strings, exceptions, doubles) | PARTIAL | Interpreter coverage is listed in [docs/evidence/interpreter-conformance.md](docs/evidence/interpreter-conformance.md): arithmetic, comparisons, branches, switch, loops, recursion, fields, static init, int/object arrays, string concatenation and intrinsics, doubles, and user/runtime exceptions. Missing: `long`, `float`, threads, synchronization, interfaces/default methods, generics beyond erasure, and most of the JDK library. |
| Breakpoints, stepping and variable view | UNSUPPORTED | No debug interface yet; planned as an interpreter-native hook because JDI cannot attach to interpreted frames on iOS. |
| JUnit support | UNSUPPORTED | Not implemented; any future UI must clearly explain lack of support. |
| Native iOS build of a Java application | PARTIAL | Verified: `v0.1.0-phase0` ships unsigned arm64 iOS IPAs for a JavaFX hello app and for a non-JavaFX GraalVM shell hosting the interpreter (CI run 39205053650). Interactive JavaFX UI on device is not proven and is not the chosen path. |

The upstream user tutorial is linked from `https://www.bluej.org/doc/documentation.html`. Claims above remain partial/unsupported until product-level tests or recorded manual verification demonstrate the behavior.
