# BlueJ feature parity tracker

Status tracks the iPad application in `app/` running against `core-server/` and the
interpreter in `spike-java/`. Every claim below is backed by a test in this repository:
`spike-java/` (Java core), `app/BlueJPadKit` (Swift model), or `app/BlueJPadUITests`
(simulator, driven against the real core).

| BlueJ workflow | Status | Evidence / current behavior |
| --- | --- | --- |
| Create/open/save projects and `project.bluej` metadata | PARTIAL | Projects are folders of `.java` files under `Documents/BlueJ Projects` with a `project.json`; create, open and delete them from the browser. No `package.bluej`/`project.bluej` targets, no packages UI beyond package declarations in source. |
| Class diagram, relationships and arrows | PARTIAL | Classes are laid out by inheritance depth with `extends` arrows, compile state, selection and a context menu; taps open the editor or create an object. No manual dragging, no `uses`/association arrows. |
| Compile button and javac-style diagnostics | DONE | `javac` compiles the project in memory on the core, returning file/line/column diagnostics. Errors appear per file in the editor and in a strip above the diagram. The compiler runs on the JDK hosting the core, not on the device. |
| Uncompiled-class striping | DONE | Classes without bytecode are drawn with a diagonal stripe; erroneous classes are outlined in red. |
| Code editor, syntax highlighting and editing actions | PARTIAL | Monospaced `UITextView` with Java token colouring, save, per-file diagnostics, breakpoint highlighting and a "Toggle Breakpoint" edit-menu action. No find/replace, no refactoring, no undo history across saves. |
| Constructor dialog and object bench | PARTIAL | Constructors with parameters open a parameter sheet; objects get BlueJ-style names (`counter1`), can be removed, and are cleared on compile. The bench is session state, not persisted. |
| Method calls, parameters and return values | PARTIAL | Instance and static calls with a parameter dialog; returns are logged (`counter1.increment() finished.`, `add(2,3) returned 5.`). Void calls are reported as finished rather than "returned null". |
| Inspector and object reference navigation | PARTIAL | Instance fields in declaration order, with expandable objects and arrays. No reference graph, no computed properties. |
| Terminal and Scanner input | PARTIAL | `System.out` output is captured and shown in the terminal. No `Scanner`/stdin, so programs that read input cannot run yet. |
| Static method calls | DONE | Static methods (including `main`) are callable from the selected class, with arguments and results in the terminal. |
| Introductory language subset | PARTIAL | Covered by the interpreter conformance tests: arithmetic, comparisons, branches, `switch`, loops, recursion, instance and static fields, static initialisers, int/object arrays, string concatenation and intrinsics, doubles, `try`/`catch`. Missing: `long`, `float`, threads, synchronization, interfaces/default methods and most of the JDK. Unsupported bytecode fails explicitly. |
| Breakpoints, stepping and variable view | PARTIAL | Real debugger: breakpoints per `Class#line`, continue, step in/over/out, stop, current frame with locals, instance fields and the caller stack; debugging runs the class's `main` and pauses on the first line when no breakpoint is set. No conditional breakpoints, no watch expressions, no disassembly. |
| JUnit support | UNSUPPORTED | Not implemented; no UI claims to run tests. |
| Native iOS build of the app | PARTIAL | The SwiftUI app builds and runs in CI against the core over loopback HTTP; unsigned IPA build proofs exist for the Java shell (`v0.1.0-phase0`). The app still talks to a host core because an iPad has no JVM: the in-process bridge is not built yet. |

## What to test next

- Manual layout and association arrows on the diagram.
- A debugger that can also debug bench objects and stop on exceptions.
- Terminal input for `Scanner`, which needs a stdin channel in the protocol.
- The in-process bridge, so the app runs without a host machine.
