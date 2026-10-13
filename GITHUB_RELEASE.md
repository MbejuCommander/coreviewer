# Coreviewer investigator 0.10.0 — Minecraft 26.3 Fabric

Coreviewer now imports compatible event sheets from `.xlsx` workbooks and recognizes CoreProtect block-click actions. Clicked blocks appear as configurable holograms in static view and replay, participate in chronological arrows, and have a dedicated Clicks category in Statistics with per-player counts and individual coordinates. Chest and ender chest clicks use block-shaped item models where the normal block renderer has no baked model.

Use **Settings → CSV / EXCEL → Manage Investigation Files → Import .xlsx** to select a workbook. Each compatible sheet becomes a selected CSV in a new import folder. Events from all selected files are ordered by recorded timestamp. The source workbook remains intact. Only `.xlsx` is supported; `.xls` is not. Simple Mode must be OFF to import.

Install `Coreviewer-Investigator-0.10.0-MC26.3.jar`, replacing the prior Coreviewer JAR. Requires Minecraft 26.3, Java 25, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3 and Cloth Config 26.3.158; Mod Menu 21.0.0 is optional. CoreTrace can remain installed. Excel reader libraries are bundled inside the Coreviewer JAR.

See README and changelog for usage, limits, build instructions and validation details. To publish on GitHub, upload the source ZIP as repository contents (or commit its extracted contents), then attach the JAR to a GitHub Release. The ZIP contains source, Gradle wrapper, documentation and tests, without local game data or old JARs. A GitHub Wiki can use README.md as its `Home` page.

Validation: 65 unit tests and one isolated Minecraft client rendering scenario passed. Live CoreProtect-server validation remains separate from simulated tests.

