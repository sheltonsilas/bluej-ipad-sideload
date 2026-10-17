# BlueJ feature parity tracker

Status tracks the iPad application, not the isolated Java spike. **No application feature is complete.** The tests under `spike-java/` are runtime feasibility experiments and do not constitute BlueJ UI/product parity.

| BlueJ workflow | Status | Evidence / current behavior |
| --- | --- | --- |
| Create/open/save projects and `package.bluej` metadata | UNSUPPORTED | No application UI or project persistence. |
| Class diagram editing, relationships and arrows | UNSUPPORTED | No diagram UI. |
| Compile button and javac-style diagnostics | PARTIAL | Test-only ECJ compiles a source string; ECJ license is EPL-2.0, excluded from the distributable application. No BlueJ editor/UI integration. |
| Uncompiled-class striping | UNSUPPORTED | No class diagram UI. |
| Code editor, syntax highlighting and editing actions | UNSUPPORTED | No application UI. |
| Constructor dialog and object bench | PARTIAL | A small test interpreter can construct one test class and retain interpreted instances; not integrated with BlueJ UI or project classes. |
| Method calls, parameters and return values | PARTIAL | Verified by interpreter tests for integer arguments/return. Limited bytecode only. |
| Inspector and object reference navigation | PARTIAL | Test API exposes stored fields; no UI or arbitrary graph inspection. |
| Terminal and Scanner input | UNSUPPORTED | Not implemented. |
| Static method calls | PARTIAL | Test API covers a small arithmetic and string-returning static method. |
| Breakpoints, stepping and variable view | UNSUPPORTED | No debug interface. |
| JUnit support | UNSUPPORTED | Not implemented; any future UI must clearly explain lack of support. |
| Gluon JavaFX app built for iOS | UNSUPPORTED | Initial Maven workflow added, but no CI result yet. |

The upstream user tutorial is linked from `https://www.bluej.org/doc/documentation.html`. Claims above will remain partial/unsupported until product-level tests or recorded manual verification demonstrate the behavior.
