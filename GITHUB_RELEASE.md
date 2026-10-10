# Coreviewer investigator 0.9.0 — Minecraft 26.3 Fabric

Configure now opens settings directly. Session holograms use available current player skins; player deaths use a custom memorial with the victim's portrait and vanilla flowers. Container events use colored chest-style slots, and optional UTC server-time labels are enabled by default.

Statistics gains a category dropdown, item/player searches, a fixed player column, vertical page navigation, per-column ascending/descending sorting, count selection for compact columns and Reset filters. Click a player, then a material icon, to inspect individual actions and exact recorded coordinates in bounded evidence pages.

CSV deletion removes empty parent folders without deleting other contents or the library root. The default replay window is now 10; existing saved limits remain supported.

Install `Coreviewer-Investigator-0.9.0-MC26.3.jar`, replacing the prior Coreviewer JAR. Requires Minecraft 26.3, Java 25, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3 and Cloth Config 26.3.158; Mod Menu 21.0.0 is optional. CoreTrace can remain installed. Current skins are not historical skin records; unavailable data is never fabricated.

See README and changelog for usage, build instructions and validation details.

To publish on GitHub, upload the source ZIP as repository contents (or commit its extracted contents), then attach `Coreviewer-Investigator-0.9.0-MC26.3.jar` to a GitHub Release. The ZIP contains source, Gradle wrapper, documentation and tests; it does not contain local game data or older JARs. A GitHub Wiki can use README.md as its `Home` page; update relative screenshot links if the Wiki is stored separately.

Verified: 62 unit tests and all seven local Minecraft client scenarios passed, including 50-player statistics navigation, material drilldown, current-profile lookup, memorials and Improved Transparency. Live CoreProtect-server testing is separate from simulated integration tests.
