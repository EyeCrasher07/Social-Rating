# Social Rating

Social Rating adds player reputation to your Minecraft server through ratings, reviews, and moderated reports.

## Features

- Rate ingenuity, skill, and friendliness from 1 to 5 stars.
- Add comments, edit your ratings, or withdraw them.
- Show player ratings as a score or stars above their names.
- Report reviews about yourself and view moderation decisions.
- Let server operators review reports and keep or remove reviews with an optional reason.

## Requirements

- Minecraft 1.16.5–26.3 with the matching Fabric, Forge, or NeoForge build.
- [Fabric API](https://modrinth.com/mod/fabric-api) for Fabric builds.
- The Java version required by your Minecraft version. See the [compatibility table](docs/compatibility.md).

Install the mod on both the server and clients. Fabric also needs Fabric API on both sides. Ratings are specific to each server. Some Minecraft versions do not have releases of every loader.

## Getting started

Place the required JARs in your `mods` folder. Approach a player, aim at them, and press **R**. Choose your scores, add an optional comment, and save. The default rating distance is 6 blocks.

Press **R** without a nearby target to open your own profile. The `/socialrating` command also opens a profile. Use **My ratings** to open or withdraw your reviews and **My reports** to view related reports and decisions.

Server operators use `/socialrating admin` to review reports. Server rules, editable translations, and backups are stored in `config/socialrating/`. Apply settings and translation changes with `/socialrating admin reload`.

## Build

Each `<loader>/<minecraft-version>/` folder is a standalone Gradle project. For Minecraft 26.3, use JDK 25:

```sh
cd fabric/26.3
./gradlew build
```

On Windows, run `gradlew.bat build`. The result is `fabric/26.3/build/libs/social-rating-fabric-1.0.0+26.3.jar`.

To build and collect several versions, use `python3 tools/build.py`. Select a single build with `--target fabric-26.3`. See [build requirements](docs/compatibility.md#building) for older versions and JDK options. Collected JARs are placed in `dist/social-rating-1.0.0/`.

## License

GNU GPLv3. See [LICENSE](LICENSE).

[Modrinth](https://modrinth.com/project/social-rating) · [Compatibility](docs/compatibility.md) ([Русский](docs/compatibility.ru.md)) · [Server settings](docs/server-settings.md) ([Русский](docs/server-settings.ru.md))
