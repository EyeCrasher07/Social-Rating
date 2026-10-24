# Social Rating

Social Rating adds player reputation to your Minecraft server through ratings, reviews, and moderated reports.

## Features

- Rate ingenuity, skill, and friendliness from 1 to 5 stars.
- Add comments, edit your ratings, or withdraw them.
- Show player ratings as a score or stars above their names.
- Report reviews about yourself and view moderation decisions.
- Let server operators review reports and keep or remove reviews with an optional reason.

## Requirements

- Minecraft 26.3 with Fabric Loader 0.19.5 or later.
- [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.3.
- Java 25 or later.

Install the mod and Fabric API on both the server and clients. Ratings are specific to each server.

## Getting started

Place the required JARs in your `mods` folder. Approach a player, aim at them, and press **R**. Choose your scores, add an optional comment, and save. The default rating distance is 6 blocks.

Press **R** without a nearby target to open your own profile. The `/socialrating` command also opens a profile. Use **My ratings** to open or withdraw your reviews and **My reports** to view related reports and decisions.

Server operators use `/socialrating admin` to review reports. Server rules, editable translations, and backups are stored in `config/socialrating/`. Apply settings and translation changes with `/socialrating admin reload`.

## Build

Use JDK 25:

```sh
cd fabric/26.3
./gradlew build
```

On Windows, run `gradlew.bat build`. The JAR is written to `fabric/26.3/build/libs/`.

## License

GNU GPLv3. See [LICENSE](LICENSE).

[Modrinth](https://modrinth.com/project/social-rating) · [Server settings](docs/server-settings.md) ([Русский](docs/server-settings.ru.md))
