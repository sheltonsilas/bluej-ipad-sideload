# BlueJ for iPad (unofficial port)

This is an **unofficial community port feasibility fork** of BlueJ, not endorsed by the BlueJ team or King's College London. The BlueJ name and logo are used pending permission and will be renamed on request. It is not currently an iPad app; this repository does not claim iOS build or feature parity.

Upstream: [k-pet-group/BlueJ-Greenfoot](https://github.com/k-pet-group/BlueJ-Greenfoot), initially pinned at commit `bf11ce44a6dcb866921485dd43fb31411dc32937`. The project remains under the upstream GPLv2 with Classpath Exception; see [LICENSE.txt](LICENSE.txt), [NOTICE](NOTICE), and upstream third-party license inventories.

- App name configuration: [app.properties](app.properties).
- Feasibility results: [docs/SPIKE_REPORT.md](docs/SPIKE_REPORT.md).
- Feature parity tracker: [PARITY.md](PARITY.md).
- Test-only interpreter prototype: [spike-java/](spike-java/).

This repository preserves upstream source and license notices. Its iPad feasibility code is experimental and incomplete. Read the parity tracker before assuming any desktop BlueJ feature works on iPad.

---

# Upstream BlueJ and Greenfoot

<img src="bluej/icons/bluej-icon-512-embossed.png" align="left" width="128">
<img src="greenfoot/resources/images/greenfoot-icon-big.jpg" align="right" width="100">

BlueJ and Greenfoot are integrated development environments (IDE) aimed at novices. They support the use of the Java programming language, as well as the frame-based Stride language.

BlueJ and Greenfoot are maintained by Michael Kölling and his research group at King's College London.

Further details and installers
---

More details about BlueJ and Greenfoot, and pre-built installers for each, are available on the main webpages:
 - [BlueJ](https://www.bluej.org/)
 - [Greenfoot](https://www.greenfoot.org/)

License
---

This repository contains the source code for BlueJ and Greenfoot, licensed under the GPLv2 with classpath exception (see [LICENSE.txt](LICENSE.txt)).

Building and running
---

BlueJ uses Gradle as its automated build tool. To build you will first need to install a Java (21) JDK. Check out the repository then execute the following command to run BlueJ:

```
./gradlew runBlueJ
```

Or to run Greenfoot:

```
./gradlew runGreenfoot
```

Development
---

To work on the project, IntelliJ IDEA should import the Gradle project automatically, although you may need to set the JDK (21) and language level (also 21).

Contributing
---

We accept pull requests for translations or bug fixes. If you plan to add a new feature or change existing behaviour we advise you to get in contact with the maintainers first.

Building Installers
---

The installers are built automatically on GitHub. If you want to build them manually, set appropriate tool paths in `tools.properties`, then run the relevant platform task:

```
./gradlew packageBlueJWindows
./gradlew packageBlueJLinux
./gradlew packageBlueJMacIntel
./gradlew packageBlueJMacAarch
./gradlew packageGreenfootWindows
./gradlew packageGreenfootLinux
./gradlew packageGreenfootMacIntel
./gradlew packageGreenfootMacAarch
```

The upstream installers are platform-specific and cannot be cross-built. Windows requires WiX 3.10 and MinGW64. On macOS, `JAVA_HOME` must point to the matching Intel or AArch JDK.
