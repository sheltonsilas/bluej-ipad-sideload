# iOS shell host self-test — 2026-10-18T03:03:11Z

Host: Windows (Git Bash), portable Temurin 21.0.12.1+1, portable Maven 3.9.16
Command: `bash ci/ios-shell/tools/selftest-host.sh`

```
BlueJ iPad runtime shell (feasibility spike)
vm=OpenJDK 64-Bit Server VM java=21.0.12.1 os=Windows 11/amd64
embedded class bytes=537
PASS static arithmetic(10) == 26 -> 26
PASS static greeting() == "hello iPad" -> "hello iPad"
PASS field count == 4 -> 4
PASS increment(5) == 9 -> 9
PASS doubled() == 18 -> 18
PASS field count == 9 after increment -> 9
SELFTEST RESULT: PASS (6 passed, 0 failed)
```
