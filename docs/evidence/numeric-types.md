# Numeric types — `long` and `float` — 2026-10-22T13:26:17Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `../../ipados-ubuntu/tools/mvn.sh test` in `spike-java/`

```
[INFO] Tests run: 7,  Failures: 0, Errors: 0 -- in org.bluej.ipad.core.CoreProtocolTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0 -- in org.bluej.ipad.core.CoreSessionTest
[INFO] Tests run: 8,  Failures: 0, Errors: 0 -- in org.bluej.ipad.core.DebugSessionTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0 -- in org.bluej.ipad.core.ProjectCompilerTest
[INFO] Tests run: 5,  Failures: 0, Errors: 0 -- in org.bluej.ipad.core.ProjectProtocolTest
[INFO] Tests run: 9,  Failures: 0, Errors: 0 -- in org.bluej.ipad.core.ProjectWorkflowTest
[INFO] Tests run: 2,  Failures: 0, Errors: 0 -- in org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Tests run: 14, Failures: 0, Errors: 0 -- in org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`core-server` over real HTTP, same day: 5/5 green, including
`carriesLongsAndFloatsOverHttp` — a `long` field set to 9 000 000 000 through `{"long":…}`,
incremented by 1, read back as `{"long":9000000001}`, and a `float` halved to
`{"float":0.75}`.

## What the new cases prove

- `InterpreterTest.computesWithLongsAndFloats`: 20! in 64 bits (2 432 902 008 176 640 000),
  `float` division and scaling, `int`→`long`→`float`→`double` widening inside one call,
  `LADD/LMUL/LCMP` and `LCONST`, long local loops, `(int)` and `(long)` casts, string
  concatenation of a `long` and a `float`, `Math.abs/round/max` overloads, `Long.parseLong`
  keeping 64 bits, and `ArithmeticException` for long division by zero.
- `InterpreterTest.longAndFloatArraysAndFieldsKeepTheirTypes`: `new long[n]` and
  `new float[n]` produce arrays whose component type and default elements are right, and
  `LALOAD/LASTORE`/`FASTORE` work; long/float fields survive construction and mutation.
- `InterpreterTest.printsLongsAndFloatsLikeJava`: `9000000000`, `1.5` and `0.75` (not
  `1.5000000238…`), because `float` stays a `float` in the value model.
- `CoreProtocolTest.carriesLongsAndFloatsAcrossTheBridge`: `{"long":…}` / `{"float":…}` in both
  directions, plus a `long[]` argument decoded with the right component type.
- `DebugSessionTest.showsLongAndFloatLocalsWithTheirTypes`: a paused frame reports `total` as
  a `long` and `rate` as a `float`, with the right values; the console then prints
  `5000000001 1.5`.
- `InterpreterTest.refusesUnsupportedBytecodeExplicitly` now uses a `synchronized` block, so the
  explicit-failure path is still covered by something that really is unsupported.

## Known representation notes

- A `long` or `double` occupies one entry on the interpreter's operand stack though the JVM
  gives it two; local variable indexes keep the JVM's numbering, so the second slot stays empty.
  `POP2`/`DUP2` handle both wide and narrow operands.
- `float` crosses the bridge as a JSON number (widened to double for transport) and is narrowed
  back to `Float`, so 32-bit precision and Java's formatting survive. Non-finite values become
  JSON `null`.
