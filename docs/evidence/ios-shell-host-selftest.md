# iOS shell host self-test — 2026-10-19T05:52:08Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `bash ci/ios-shell/tools/selftest-host.sh`

```
BlueJ iPad runtime shell (feasibility spike)
vm=OpenJDK 64-Bit Server VM java=21.0.12.1 os=Windows 11/amd64
embedded class bytes=2014
PASS static arithmetic(10) == 26 -> 26
PASS static greeting() == "hello iPad" -> "hello iPad"
PASS sumTo(6) == 30 (loop, branch, modulo) -> 30
PASS fibonacci(10) == 55 (while loop, locals) -> 55
PASS average(2, 3) == 2.5 (double arithmetic) -> 2.5
PASS describe("Ada", 36) (string concatenation) -> "Ada is 36 years old"
PASS initials("Ada", "Lovelace") == "AL" (char concatenation) -> "AL"
PASS total({2, 3, 4}) == 9 (int array) -> 9
PASS safeDivide(1, 0) == -1 (caught ArithmeticException) -> -1
PASS field count == 4 -> 4
PASS increment(5) == 9 -> 9
PASS doubled() == 18 -> 18
PASS field count == 9 after increment -> 9
PASS static created() == 1 (static initialiser and field) -> 1
PASS uncaught exception is reported -> "java.lang.ArrayIndexOutOfBoundsException"
SELFTEST RESULT: PASS (15 passed, 0 failed)
```
