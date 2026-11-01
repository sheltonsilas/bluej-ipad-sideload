# Core protocol and session tests — 2026-11-01T13:10:00Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `bash ../ipados-ubuntu/tools/mvn.sh -B -f spike-java/pom.xml test`

```
[INFO] Running org.bluej.ipad.core.CoreProtocolTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.387 s -- in org.bluej.ipad.core.CoreProtocolTest
[INFO] Running org.bluej.ipad.core.CoreSessionTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.808 s -- in org.bluej.ipad.core.CoreSessionTest
[INFO] Running org.bluej.ipad.core.DebugSessionTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.667 s -- in org.bluej.ipad.core.DebugSessionTest
[INFO] Running org.bluej.ipad.core.ProjectCompilerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.275 s -- in org.bluej.ipad.core.ProjectCompilerTest
[INFO] Running org.bluej.ipad.core.ProjectProtocolTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.371 s -- in org.bluej.ipad.core.ProjectProtocolTest
[INFO] Running org.bluej.ipad.core.ProjectWorkflowTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.034 s -- in org.bluej.ipad.core.ProjectWorkflowTest
[INFO] Running org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.085 s -- in org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Running org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.099 s -- in org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 64, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The core session and JSON protocol are exercised headlessly, so the Swift shell can be
built against a tested contract instead of against the interpreter internals. The suite now
covers project compilation, the bench, inspector snapshots, typed values, the debugger, and
a call that parks on `Scanner` input, including `cancel`.
