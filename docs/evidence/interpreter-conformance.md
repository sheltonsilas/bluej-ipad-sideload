# Interpreter conformance — 2026-10-19T05:52:24Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `bash ../ipados-ubuntu/tools/mvn.sh -B -f spike-java/pom.xml test`

```
[INFO] Running org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.225 s -- in org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Running org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.710 s -- in org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Coverage: integer and double arithmetic, comparisons, branches, switch, loops, recursion,
static and instance fields, static initialisers, int/object arrays, string constants and
concatenation, String/Character/Math/Integer/Double intrinsics, PrintStream output,
try/catch for runtime, index and user exceptions, instruction budget, and explicit
rejection of unsupported bytecode (long).
