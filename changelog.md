# Changelog

## 0.9.0 — 2026-10-09 — Evidence navigation and player memorials

- Removed the intermediate settings dashboard; Configure opens Cloth Config directly.
- Added current player-skin lookup for session holograms and victim portraits in a custom stone memorial with vanilla flowers. Missing skins retain a default appearance.
- Replaced white container cards with chest-style inventory slots and colored add/remove backgrounds.
- Added optional server-time labels (UTC), enabled by default in static view and replay.
- CSV deletion now prunes empty parent folders, preserving the library root and folders with other contents.
- Changed the new/blank/reset replay visible-event limit to 10; existing saved limits remain intact.
- Added statistics item/player searches, fixed player column, vertical page arrows, per-column three-state sorting, compact count ranking and category dropdown with Reset filters.
- Added per-player/material evidence pages with quantities, timestamps, worlds and coordinates; background preparation and bounded displayed rows.
- Verified 62 unit tests and all seven isolated client scenarios, including 50-player statistics navigation and Improved Transparency rendering.


## 0.8.0 — 2026-10-08 — Library organization and interface refinement

- Fixed Open CSV Folder using an asynchronous native file-manager launch.
- Added recursive CSV discovery and capture subfolders with configurable names, unique generated defaults and collision suffixes. Simple Mode remembers its active nested file across restarts.
- Replaced Smart Timeline with a numeric text input and adjacent ON/OFF switch; blank/reset default is now 2 seconds.
- Added an animated settings dashboard, smoother navigation and a reduced-motion preference.
- Rebuilt statistics as a bordered table with soft action colors, persistent Show details and Compact controls, paired green/red counts and totals.
- Fixed the ghost-block crash with Minecraft Improved Transparency by supplying OIT pipelines for both through-wall and depth-tested rendering.
- Expanded regression and in-game coverage; updated documentation and screenshots. Verification: 59 unit tests and six client scenarios passed; native CSV-folder launch confirmed in Windows Explorer.

## 0.7.0 — 2026-10-08 — CSV investigations and statistics

- Added shared import/export CSV library compatible with CoreTrace 1.4.1+26.3: selection, ordering, individual/all deletion and persistent manifests.
- Combined selected events chronologically across files; added legacy JSON migration and automatic capture CSV saving.
- Replaced Auto Save/Backup/Replace Previous Capture controls with optional Simple Mode, including a warning and archival of existing files on activation.
- Added optional event teleport navigation, configurable command placeholders and optional radius filtering at view load.
- Added container item bubbles and login/logout holograms with category toggles and chronological arrows.
- Added category and per-player statistics with item icons, player heads, quantities, event counts and totals.
- Made Smart Timeline gap configurable (default 3 seconds).
- Kept parsing, aggregation and indexing off the game thread; bounded unchanged-file CSV cache.
- Updated README, compatibility fixtures and unit/client integration coverage. Verification: 51 unit tests, six client scenarios and a final focused library/rendering run passed.

## 0.6.1 — 2026-09-28 — Separate identity for coexistence

- Changed Coreviewer's Fabric mod ID to `coreviewer` and separated its Java package, resource namespace, key names, mixin metadata and local data folder from CoreTrace.
- Added a one-time, non-destructive copy of missing configuration, events and backups from `.minecraft/coretrace/` to `.minecraft/coreviewer/`. Imported automatic capture and pagination start OFF to avoid both mods paging at once.
- Kept `/coreviewer` commands and all 0.6.0 investigation features; CoreTrace can now be installed separately.
- Verified 40 unit tests and five isolated Minecraft client scenarios; compared the release JAR with CoreTrace 0.4 and found distinct IDs, entrypoints and Java class paths.
- Added a GitHub Actions build workflow and release notes for source publication and JAR downloads.

## 0.6.0 — 2026-09-27 — Capture and playback customization

- Replaced fixed radius/delay/visible-event choices with numeric input and blank-input defaults (100 blocks, 1500 ms, 10 static actions, 20 replay actions).
- Added successful-auto-capture reset of both capture switches, with an independent chat notification toggle.
- Added optional start/completion sounds and an interactive vanilla sound picker with search, preview, stop and selection.
- Added staged replacement of the previous same-server capture on successful completion, independent notification and existing backup/Auto Save behavior.
- Added chronological static pages, V/B page shortcuts, G menu shortcut and configurable timed progress above health.
- Added rolling replay windows, configurable progress/pause timeout and Smart Timeline idle-gap skipping.
- Updated replay shortcuts: Right Shift, comma, period; added J load, K restart, L stop, H menu. Active keyboard shortcuts take priority over vanilla bindings.
- Added conservative legacy-default migration, cancellation/persistence tests and full client UI/key/capture integration coverage. Verification: 39 unit tests and five isolated Minecraft client scenarios passed.

## 0.5.0 — 2026-09-27 — Phase 5

- Renamed the displayed mod to Coreviewer investigator and replaced the command root with /coreviewer.
- Retained internal mod ID, namespace and coretrace data directory for in-place compatibility with previous releases.
- Added immutable per-world chunk indexes and pre-sorted timelines, built and published with event snapshots off the client thread.
- Added asynchronous nearest-event selection with a bounded/coalescing queue, loaded-chunk snapshots, lazy refresh and cancellation.
- Added bounded block/item model caches with resource-reload and world invalidation; retained rendering/figure caps.
- Replaced replay scans/copies with binary-search boundaries and snapshot slices.
- Replaced JSON-based config copies with independent in-memory copies.
- Added sparse 100,001-event regression, reference-equivalence, loaded-chunk, cancellation/cache, snapshot and replay tests.
- Added client checks for renamed commands/dashboard, cached rendering, resource-pack reload and cache cleanup.

## 0.4.0 — 2026-09-27 — Phase 4

- Added event-based replay with immutable server/world snapshots, chronological reveal and deterministic seek/rewind.
- Added Replay screen, timeline scrubber, Play in world, Previous/Next, Restart, Stop and paused viewing.
- Added configurable play/pause and step keys, HUD, UTC time and approximate-time indication.
- Activated independent timeline, break and place speeds (0.25x–4x); block animations and item/arrow motion follow the timeline.
- Equal-time actions appear together; completed actions retain evidence markers without changing real terrain.
- Pause on screens/focus loss; stop on master OFF, world changes, disconnect, Clear/Reload or Static View selection.
- Added unit tests and isolated Minecraft UI/input/rendering coverage.
- Preserved schema 1 storage/config compatibility. Phase 5 optimization remains deferred.

## 0.3.0 — 2026-09-27 — Phase 3

- Added Static Investigation screen and explicit server-world to current-dimension session binding.
- Added translucent default block models, cube outlines, native/tinted textures, colors and opacity.
- Added depth-tested and through-wall block/marker/label/arrow rendering using Minecraft 26.3 render pipelines and reversed depth.
- Added chronological arrows with configurable color, thickness/size, moving direction indicator and per-actor grouping; equal/approximate timestamps are not connected.
- Added detached player/mob figures frozen in a death pose, killer/cause labels, floating item models and optional generic player-head icons.
- Added radius/loaded-chunk filtering, configurable nearest-event cap, figure limits and per-frame render-state data.
- Added temporary seven-event visual demo without modifying world blocks/entities or saved evidence.
- Added selection/chronology tests and an isolated world rendering integration test.
- Kept capture/storage compatibility. Replay and advanced performance work remain Phases 4 and 5.


## 0.2.0 — 2026-09-26 — Phase 2

- Added passive English CoreProtect system-chat capture scoped to outgoing lookup/l/near commands.
- Added background block, kill and item parsing, hover timestamps, relative-time provenance, nullable missing fields and raw action text.
- Added automatic pagination with configurable delays, one outstanding request, response bounds, timeout, repeated-page detection and error cancellation.
- Added Capture dashboard, lookup parameters, Start/Stop controls and lookup/stop/status client commands.
- Wired master/Auto Capture switches, passive mode, disconnect/world-change cancellation and local CSV/JSON autosave.
- Preserved Phase 1 JSON compatibility and simulated fixtures.
- Added parser/scheduler/storage tests and Fabric component capture coverage inside the client integration test.
- Documented English-only parsing, ambiguous kill names, missing coordinates, approximate timestamps and repeated-query observations.
- Static rendering and replay remain deferred to Phases 3 and 4.


## 0.1.0 — 2026-09-26 — Phase 1

- Started CORETRACE INVESTIGATOR as a client-only Minecraft 26.3 Fabric project.
- Integrated Mod Menu and Cloth Config with GENERAL, CAPTURE, CSV, STATIC VIEW and REPLAY categories.
- Added a working master enable gate and persistent configuration.
- Added typed block, kill and item event models with provenance, nullable unknown fields and six explicitly simulated fixtures.
- Added local events.json, events.csv and config.json under the game directory's coretrace folder.
- Added asynchronous serialized storage, JSON reload, CSV escaping, optional autosave and previous-version backups.
- Preserved malformed data and rejected unsupported schemas instead of silently resetting files.
- Added a local dashboard and simulate/save/reload/clear commands.
- Added unit tests and an isolated client UI integration test.
- Reserved future configuration without implementing capture, automatic pagination, static rendering or replay.
- Archived the earlier CoreView prototype separately; it is not part of the Phase 1 build.



