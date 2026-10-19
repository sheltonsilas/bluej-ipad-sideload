# Core protocol and session tests — 2026-10-19T06:05:27Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `bash ../ipados-ubuntu/tools/mvn.sh -B -f spike-java/pom.xml test`

```
[INFO] Running org.bluej.ipad.core.CoreProtocolTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.418 s -- in org.bluej.ipad.core.CoreProtocolTest
[INFO] Running org.bluej.ipad.core.CoreSessionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.385 s -- in org.bluej.ipad.core.CoreSessionTest
[INFO] Running org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.110 s -- in org.bluej.ipad.spike.InMemoryCompilerTest
[INFO] Running org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.570 s -- in org.bluej.ipad.spike.InterpreterTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The core session and JSON protocol are exercised headlessly, so the Swift shell can be
built against a tested contract instead of against the interpreter internals.
