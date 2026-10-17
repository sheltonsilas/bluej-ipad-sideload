# Progress — BlueJ for iPad

Date: 2026-10-17

- Windows detected. Power settings were backed up to `C:\Users\Ramya\power_settings_backup.txt` and sleep/hibernate timeouts set to zero, monitor timeout to one minute. A hidden PowerShell keep-awake worker and watchdog run from `%TEMP%`; worker PID 8096, watchdog PID 4572 at last check. The Windows battery reported 100% and AC power.
- Lid-close action remains unresolved: this machine's `powercfg /aliases` does not expose `LIDACTION`, and attempts using the action alias / guessed GUID did not verify or configure the lid policy. Do not claim keep-awake-with-lid-closed until a valid lid query proves it.
- GitHub CLI is authenticated as `sheltonsilas` with `repo` and `workflow` scopes; `gh auth setup-git` previously succeeded. No token or device login code recorded.
- Public Repo 1 exists: <https://github.com/sheltonsilas/bluej-ipad-sideload>. It started from official upstream <https://github.com/k-pet-group/BlueJ-Greenfoot> at `bf11ce44a6dcb866921485dd43fb31411dc32937` and retains upstream licenses/copyright notices.
- Local tests on portable Temurin 21 / Maven pass: 5 tests total, zero failures, errors, or skips. Includes ECJ in-process source compile test (test dependency only; EPL-2.0) and limited interpreter/object invocation tests. The interpreter is not Java-compatible beyond its documented sample subset.
- GluonFX Maven JavaFX iOS workflow and hello app are committed locally but have not yet been pushed or run. Phase 0 remains incomplete until D has actual macOS CI evidence.
- Repo 2, releases, IPAs, and parity completion are not claimed. See [SPIKE_REPORT.md](SPIKE_REPORT.md) and [PARITY.md](../PARITY.md).
