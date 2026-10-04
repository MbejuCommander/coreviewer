# Coreviewer investigator 0.6.1 — Minecraft 26.3 Fabric

Client-side investigation tools for moderators using CoreProtect lookup commands.

## Changes

- Uses the Fabric mod ID `coreviewer` so it can be installed alongside CoreTrace (`coretrace`).
- Separates the Java package, assets, key names and local data folder from CoreTrace.
- Copies existing Coreviewer/CoreTrace data once from `.minecraft/coretrace/` to `.minecraft/coreviewer/` when the new folder does not yet exist. The original data remains intact; imported automatic capture and pagination start OFF.
- Includes the 0.6.0 capture controls, sound picker, static pages, replay shortcuts, rolling event window and Smart Timeline.

## Installation

Download `Coreviewer-Investigator-0.6.1-MC26.3.jar` and place it in the **client** `mods` folder. Use Minecraft Java 26.3, Fabric Loader 0.19.5+, Java 25+, Fabric API 0.161.0+26.3 and Cloth Config API 26.3.158. Mod Menu 21.0.0 is recommended.

Remove any older Coreviewer JAR. The separate CoreTrace JAR may remain installed. If both mods are enabled, use automatic CoreProtect pagination in only one of them at a time.

## Verification

Local build: 40 unit tests and five isolated Minecraft client test scenarios passed. The Coreviewer 0.6.1 JAR and the included CoreTrace 0.4 JAR have different Fabric IDs, entrypoints and Java class paths. A live CoreProtect server test is still pending.

The source package contains the Gradle wrapper and GitHub Actions workflow. Push its contents to the repository root; the **Build Coreviewer** workflow builds the JAR on each push and pull request and makes it available under the workflow run's artifacts. For a GitHub Release, attach the client JAR from this package. GitHub provides its own source archives for tagged releases.
