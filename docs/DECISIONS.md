# BlueJ for iPad — decisions

## 2026-10-17 — upstream snapshot and project identity

- Upstream: <https://github.com/k-pet-group/BlueJ-Greenfoot>, inspected commit `bf11ce44a6dcb866921485dd43fb31411dc32937`.
- Repo 1 created publicly at <https://github.com/sheltonsilas/bluej-ipad-sideload> using the complete upstream snapshot at that commit. Original source headers, upstream `LICENSE.txt`, and third-party notices remain intact.
- Preserve GPL version 2 with Classpath Exception. The port is unofficial and not endorsed by the BlueJ team or King's College London. Its name/logo remain pending permission and will be changed on request; README and NOTICE state this.
- Repo 2 stays gated behind the Repo 1 verify gate.

## 2026-10-17 — Phase 0 prototype scope

- ECJ 3.40.0's Maven metadata declares EPL-2.0. It is test-scope only for the compile feasibility proof, not approved for incorporation into or distribution with the GPL application. Replace it or obtain qualified license review before product integration.
- The bytecode interpreter is an experimental, explicit opcode subset. It uses ASM 9.7 (BSD-3-Clause) to decode class files. Unsupported bytecodes fail explicitly; this is not a Java VM or BlueJ parity claim.
- A/B/C have local prototype tests. D requires a macOS runner; no iOS workflow result or `.ipa` currently exists.
- No production architecture has been selected. Keep the upstream desktop UI/source intact while the native UI and runtime path are investigated; do not claim port parity until user workflows have tests.
