# BlueJ for iPad — decisions

## 2026-10-17 — upstream snapshot and project identity

- Upstream: <https://github.com/k-pet-group/BlueJ-Greenfoot>, inspected commit `bf11ce44a6dcb866921485dd43fb31411dc32937`.
- Preserve the upstream GPL version 2 with Classpath Exception and every original copyright/license notice. The upstream repository is a BlueJ/Greenfoot monorepo; this first repository is a public experimental sideload feasibility fork, not a completed iPad port.
- The port is unofficial, not endorsed by the BlueJ team; name/logo remain pending permission and will be changed on request. This wording is in README.md and NOTICE.

## 2026-10-17 — Phase 0 design status

- No production architecture selected. The immediate work is a minimal compiler/interpreter spike, isolated from upstream BlueJ modules.
- ECJ 3.40.0 is confirmed in Maven Central metadata as EPL-2.0. It is test-scope only for a proof of technical behavior and is not approved for inclusion or distribution with the GPLv2+Classpath-Exception application. Replace it or obtain qualified license clearance before integration.
- The experimental interpreter is an explicit opcode subset and uses ASM 9.7 (BSD 3-Clause) to decode bytecode. Unsupported bytecodes fail with a clear exception; this is not Java compatibility or BlueJ parity.
- The user asked for public repository creation; the public repository now exists. No Release or `.ipa` is claimed. Second repo creation remains gated by the requested Repo 1 verify gate.
