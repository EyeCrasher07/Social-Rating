# Social Rating 1.0.0 compatibility

**English** · [Русский](compatibility.ru.md)

Choose a separate JAR for your exact Minecraft version and loader. Install the mod on the server and clients; Fabric also requires Fabric API. All builds use mod version **1.0.0**.

The table lists the loader and Fabric API versions used to build each project. `—` means the loader has no release for that Minecraft version. A `beta` suffix belongs to the loader.

| Minecraft | Fabric Loader | Fabric API | Forge | NeoForge | Java |
| --- | --- | --- | --- | --- | --- |
| 1.16.5 | 0.19.5 | 0.42.0+1.16 | 36.2.42 | — | 8 |
| 1.17 | 0.19.5 | 0.36.0+1.17 | — | — | 16 |
| 1.17.1 | 0.19.5 | 0.46.1+1.17 | 37.1.1 | — | 16 |
| 1.18 | 0.19.5 | 0.44.0+1.18 | 38.0.17 | — | 17 |
| 1.18.1 | 0.19.5 | 0.46.6+1.18 | 39.1.2 | — | 17 |
| 1.18.2 | 0.19.5 | 0.77.0+1.18.2 | 40.3.12 | — | 17 |
| 1.19 | 0.19.5 | 0.58.0+1.19 | 41.1.0 | — | 17 |
| 1.19.1 | 0.19.5 | 0.58.5+1.19.1 | 42.0.9 | — | 17 |
| 1.19.2 | 0.19.5 | 0.77.0+1.19.2 | 43.5.2 | — | 17 |
| 1.19.3 | 0.19.5 | 0.76.1+1.19.3 | 44.1.23 | — | 17 |
| 1.19.4 | 0.19.5 | 0.87.2+1.19.4 | 45.4.5 | — | 17 |
| 1.20 | 0.19.5 | 0.83.0+1.20 | 46.0.14 | — | 17 |
| 1.20.1 | 0.19.5 | 0.92.12+1.20.1 | 47.4.28 | 47.1.106 | 17 |
| 1.20.2 | 0.19.5 | 0.91.6+1.20.2 | 48.1.0 | 20.2.93 | 17 |
| 1.20.3 | 0.19.5 | 0.91.1+1.20.3 | 49.0.2 | 20.3.8-beta | 17 |
| 1.20.4 | 0.19.5 | 0.97.3+1.20.4 | 49.2.13 | 20.4.253 | 17 |
| 1.20.5 | 0.19.5 | 0.97.8+1.20.5 | — | 20.5.21-beta | 21 |
| 1.20.6 | 0.19.5 | 0.100.8+1.20.6 | 50.2.14 | 20.6.144 | 21 |
| 1.21 | 0.19.5 | 0.102.0+1.21 | 51.0.33 | 21.0.167 | 21 |
| 1.21.1 | 0.19.5 | 0.116.17+1.21.1 | 52.1.20 | 21.1.262 | 21 |
| 1.21.2 | 0.19.5 | 0.106.1+1.21.2 | — | 21.2.1-beta | 21 |
| 1.21.3 | 0.19.5 | 0.114.1+1.21.3 | 53.1.16 | 21.3.100 | 21 |
| 1.21.4 | 0.19.5 | 0.119.4+1.21.4 | 54.1.22 | 21.4.161 | 21 |
| 1.21.5 | 0.19.5 | 0.128.2+1.21.5 | 55.1.18 | 21.5.101 | 21 |
| 1.21.6 | 0.19.5 | 0.128.2+1.21.6 | 56.0.9 | 21.6.20-beta | 21 |
| 1.21.7 | 0.19.5 | 0.129.0+1.21.7 | 57.0.3 | 21.7.25-beta | 21 |
| 1.21.8 | 0.19.5 | 0.136.1+1.21.8 | 58.1.26 | 21.8.57 | 21 |
| 1.21.9 | 0.19.5 | 0.134.1+1.21.9 | 59.0.5 | 21.9.16-beta | 21 |
| 1.21.10 | 0.19.5 | 0.138.4+1.21.10 | 60.1.18 | 21.10.64 | 21 |
| 1.21.11 | 0.19.5 | 0.141.6+1.21.11 | 61.2.4 | 21.11.48 | 21 |
| 26.1 | 0.19.5 | 0.155.3+26.1.2 | 62.0.9 | 26.1.0.19-beta | 25 |
| 26.1.1 | 0.19.5 | 0.155.3+26.1.2 | 63.0.2 | 26.1.1.15-beta | 25 |
| 26.1.2 | 0.19.5 | 0.155.3+26.1.2 | 64.1.5 | 26.1.2.122 | 25 |
| 26.2 | 0.19.5 | 0.161.0+26.2 | 65.1.5 | 26.2.0.93 | 25 |
| 26.3 | 0.19.5 | 0.162.0+26.3 | 66.0.12 | 26.3.0.86 | 25 |

Files are named `social-rating-<loader>-1.0.0+<minecraft>.jar`. For example: `social-rating-forge-1.0.0+1.20.1.jar`.

## Building

Run `./gradlew build` in the selected project folder, or `gradlew.bat build` on Windows. Gradle itself needs JDK 21 for Gradle 8 projects or JDK 25 for Gradle 9 projects. The wrapper version is in `gradle/wrapper/gradle-wrapper.properties`.

Compilation uses JDK 17 for Minecraft through 1.20.4, JDK 21 for 1.20.5–1.21.11, and JDK 25 for 26.x. Install these as Gradle toolchains. Older NeoForge projects also need JDK 21 for their build tools. Output bytecode targets the Java version in the table, including Java 8 for 1.16.5.

From the repository root, build one target with:

```sh
python3 tools/build.py --target fabric-26.3
```

Omit `--target` to build all available targets. `--jdk-21`, `--jdk-25`, and repeatable `--toolchain` options accept installed JDK home paths. For example:

```sh
python3 tools/build.py --target forge-1.20.1 --jdk-25 /path/to/jdk25 --toolchain /path/to/jdk17
```

The script runs the Gradle build and checks, then collects production JARs and checksums in `dist/social-rating-1.0.0/`. Add `--changed` to rebuild only targets whose sources or JAR changed; unchanged targets still receive artifact and checksum checks. `--collect-only` verifies and copies existing JARs when their contents and sources still match the successful build. Use `--list` to list available targets.
