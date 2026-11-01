# Interpreter conformance — 2026-11-01T13:10:00Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `bash ../ipados-ubuntu/tools/mvn.sh -B -f spike-java/pom.xml test`

```
[INFO] Running org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.085 s -- in org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Running org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.099 s -- in org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 64, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Coverage: integer, `long`, `float` and `double` arithmetic, comparisons, branches, `switch`,
loops, recursion, static and instance fields, static initialisers, arrays of every primitive,
string constants and concatenation, `String`/`Character`/`Math`/`Integer`/`Long`/`Float`/
`Double` intrinsics, `PrintStream` output, `Scanner` over a supplied standard input,
interfaces with default methods, polymorphic interface arrays, `instanceof`, `try`/`catch`
for runtime, index and user exceptions, the instruction budget, and explicit rejection of
unsupported bytecode (`synchronized`).

`Scanner` cases prove the Java-visible behaviour that matters: `next()` skips leading
whitespace, `nextLine()` returns the rest of the line including its leading space,
`nextInt()` leaves the terminator for a following `nextLine()`, a literal
`new Scanner("7 8")` works with no terminal, `hasNextInt()` looks ahead without consuming,
and an empty input throws `java.util.NoSuchElementException`.
