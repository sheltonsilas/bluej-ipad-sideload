# Issues log — BlueJ for iPad feasibility spike

Running log of obstacles, the workaround actually applied, and what is still open.
Only entries with real evidence are listed; assumptions are marked as such.

## Host (Windows, no admin)

| # | Issue | Workaround applied | Status |
| --- | --- | --- | --- |
| H1 | No JDK 21, Maven, Xcode, Swift or XcodeGen on the host. | Portable Temurin 21.0.12.1+1 and Maven 3.9.16 downloaded into `ipados-ubuntu/tools/` (user scope, no installer). Xcode-dependent work runs only on GitHub macOS runners. | Resolved |
| H2 | `winget install` exits with code 16 for the Temurin package. | Bypassed entirely: portable zip download plus SHA-256 recording instead of a machine-wide install. | Resolved (workaround) |
| H3 | `mvn` is not on PATH and Git Bash mangles Windows paths for the `.cmd` launcher. | `ipados-ubuntu/tools/mvn.sh` wrapper converts paths with `cygpath` and pins `JAVA_HOME` and a local repository. | Resolved |
| H4 | Cannot verify the lid-close action: `powercfg /query SCHEME_CURRENT SUB_BUTTONS LIDACTION` does not list `LIDACTION` for the active Dell scheme, and the saved backup has no such entry either. | Set it anyway (`/setacvalueindex` and `/setdcvalueindex` both returned 0), kept sleep/hibernate timeouts at 0, and additionally hold `ES_SYSTEM_REQUIRED` through a hidden, watchdog-protected PowerShell worker so the machine stays awake even if the lid setting is not honoured. | Mitigated — unverified by design of the power scheme |
| H5 | A `mvn test` run failed once with "Error creating properties files for forking" immediately after a previous Surefire run in the same checkout. | Re-ran; it passed with 5/5 tests. Left as a transient Windows/Surefire fork-temp flake, not a code defect. | Transient |

## CI / iOS toolchain

| # | Issue | Workaround applied | Status |
| --- | --- | --- | --- |
| C1 | `actions/setup-java@v4` has no `graalvm-community` distribution, so the first iOS workflow failed before compiling. | Switched to `gluonhq/setup-graalvm@master`, the GraalVM build Gluon tests GluonFX against (installs `gluon-23+25.1-dev-2409082136`, JDK 23). | Resolved |
| C2 | The POM never passed a target to the GluonFX plugin, so `gluonfx:build` would have built for the macOS host instead of iOS. | Added `<target>${gluonfx.target}</target>` plus `desktop`/`ios`/`ios-sim` profiles. | Resolved |
| C3 | `gluonfx:build` for iOS aborts with "Could not load CAPCache file ... RISCV64LibCHelperDirectives.cap": Substrate 0.0.68 always passes `-H:+UseCAPCache` and the shipped CAP cache is incomplete. | Append `-H:-UseCAPCache` through `nativeImageArgs` so the last flag wins. | Fix pushed, verification pending |
| C4 | ECJ 3.40.0 is EPL-2.0, which is not compatible with shipping inside this GPLv2+CE application. | ECJ is test-scope only and the interpreter's main sources no longer depend on it; the iOS shell interprets a checked-in embedded class file instead. Replacing it for product use is still open. | Open |
| C5 | The iOS app cannot be signed: no Apple Developer account, certificate or provisioning profile exists, and the required one-time macOS configuration has not been done. | Build the native `.app` with `gluonfx:build` (no signing needed) and zip it into an unsigned `.ipa` by hand, which is installable only after re-signing by the user. | Open (by design) |
