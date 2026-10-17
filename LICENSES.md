# Third-party licenses

| Component | Version | License | Notes |
| --- | --- | --- | --- |
| BlueJ upstream | commit `bf11ce44a6dcb866921485dd43fb31411dc32937` | GPL version 2 with Classpath Exception | Preserved `LICENSE.txt` and original copyright headers. BlueJ third-party notices are in `bluej/doc/THIRDPARTYLICENSE.txt` and its third-party license directory. |
| Eclipse ECJ (`org.eclipse.jdt:ecj`) | 3.40.0 | Eclipse Public License 2.0 (EPL-2.0) | Maven Central POM declares EPL-2.0. This is not GPL-compatible for incorporation into the GPLv2+Classpath-Exception application under the project's stated dependency policy. It is currently an isolated feasibility test dependency; do not ship or bundle it in the port until legal compatibility is established or it is replaced by a compatible compiler. SHA-256 of downloaded artifact used for local proof: `05cc22a24e7982970f63a405fc6c820bc80b806f27f3c5a6236fc475f8f7152b`. |
| ASM core and tree (`org.ow2.asm`) | 9.7 | BSD 3-Clause | Used only to decode bytecode instructions in the interpreter spike. |
| JUnit Jupiter | 5.11.4 | EPL-2.0 / Eclipse Distribution License 1.0 | Test-scope only; not bundled. Review exact transitive test tooling licenses before distribution. |
| Temurin JDK (local test toolchain only) | 21.0.12.1+1 | GPL-2.0 with Classpath Exception | Portable local JDK; not included in source repository or application distribution. |
| Maven build plugins / test transitive dependencies | pinned in `spike-java/pom.xml` where direct | Mixed; full SBOM/license audit pending | Build-only and test-only. No claim that the complete transitive graph has been audited. |

**License gate:** the user requires only GPL-compatible dependencies in deliverables. ECJ's verified EPL-2.0 license fails that requirement for application integration. The in-process compile test proves technical behavior only; it does not approve ECJ for the port. Find a compatible compiler or obtain qualified license review before proceeding with integration.
