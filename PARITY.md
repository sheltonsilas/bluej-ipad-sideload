# BlueJ feature parity tracker

Status tracks the iPad application, not the isolated Java spike. **No application feature is complete.** The tests under `spike-java/` are runtime feasibility experiments and the `ci/` apps are build proofs; neither constitutes BlueJ UI/product parity.

| BlueJ workflow | Status | Evidence / current behavior |
| --- | --- | --- |
| Create/open/save projects and `package.bluej` metadata | UNSUPPORTED | No application UI or project persistence. |
| Class diagram editing, relationships and arrows | UNSUPPORTED | No diagram UI. |
| Compile button and javac-style diagnostics | PARTIAL | Test-only ECJ compiles a source string and reports diagnostics; ECJ license is EPL-2.0, excluded from the distributable application. No BlueJ editor/UI integration. |
| Uncompiled-class striping | UNSUPPORTED | No class diagram UI. |
| Code editor, syntax highlighting and editing actions | UNSUPPORTED | No application UI. |
| Constructor dialog and object bench | PARTIAL | Interpreter constructs objects and retains interpreted instances; not integrated with a UI or with project classes. |
| Method calls, parameters and return values | PARTIAL | Interpreter tests cover integer arguments/returns and object method calls. Limited bytecode only. |
| Inspector and object reference navigation | PARTIAL | Interpreter API exposes stored fields; no UI or arbitrary graph inspection. |
| Terminal and Scanner input | UNSUPPORTED | Not implemented. |
| Static method calls | PARTIAL | Test API covers small arithmetic and string-returning static methods. |
| Breakpoints, stepping and variable view | UNSUPPORTED | No debug interface yet; planned as an interpreter-native hook because JDI cannot attach to interpreted frames on iOS. |
| JUnit support | UNSUPPORTED | Not implemented; any future UI must clearly explain lack of support. |
| Native iOS build of a Java application | PARTIAL | Verified: `v0.1.0-phase0` ships unsigned arm64 iOS IPAs for a JavaFX hello app and for a non-JavaFX GraalVM shell hosting the interpreter (CI run 39205053650). Interactive JavaFX UI on device is not proven and is not the chosen path. |

The upstream user tutorial is linked from `https://www.bluej.org/doc/documentation.html`. Claims above remain partial/unsupported until product-level tests or recorded manual verification demonstrate the behavior.
