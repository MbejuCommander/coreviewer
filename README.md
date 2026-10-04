# Coreviewer investigator — 0.6.1

Client-only Fabric mod for **Minecraft Java 26.3**, for moderators who have permission to run CoreProtect commands. The mod reads English lookup responses displayed to the client. It does not access SQL, server files, or CoreProtect's server-side Java API.

Version 0.6.0 adds editable numeric settings, capture completion/reset controls, searchable sound previews, chronological static pages, rolling replay windows and Smart Timeline. All commands use **`/coreviewer`**.

Version 0.6.1 gives Coreviewer its own Fabric ID (`coreviewer`), Java package, resource namespace, key names and `.minecraft/coreviewer/` data directory. It can now be installed alongside the earlier CoreTrace mod, whose ID is `coretrace`. If the Coreviewer data directory does not exist yet, it copies available configuration, events and backups from `.minecraft/coretrace/` once; the original files remain untouched. Imported Auto Capture and Auto Page Advance are switched OFF so the two mods do not both schedule pagination immediately. Re-enable Coreviewer capture when ready. Later writes by either mod remain in their own folders.

## Installation

Use Java 25+, Minecraft 26.3 and Fabric Loader 0.19.5+. Put the release jar in your client's `mods` folder together with:

- Fabric API `0.161.0+26.3`
- Cloth Config API `26.3.158` for Fabric
- Mod Menu `21.0.0` (recommended)

Dependencies are not bundled. No Coreviewer server plugin is required; the server must already provide CoreProtect and grant you lookup permissions.

## Capture a lookup

1. Join your server and open `/coreviewer config` or Mod Menu > Coreviewer investigator > Configure.
2. Keep **COREVIEWER ENABLED** and **Auto Capture** ON.
3. For passive capture, keep **AUTO PAGE ADVANCE OFF** and run `/co lookup r:100 t:1h`, `/co l ...`, or `/co near` yourself. Coreviewer records supported response rows and sends no commands.
4. For automatic pagination, turn **AUTO PAGE ADVANCE ON**. Run a CoreProtect lookup manually, or open `/coreviewer` > **Capture**, edit the lookup parameters and click **Start lookup**. `/coreviewer lookup r:100 t:1h` does the same.
5. The engine reads `Page X/Y`, waits **Command Delay**, and requests `/co l <next page>`. Enter any positive whole-number delay; the default is **1500 ms**. Blank input restores 1500.
6. Use `/coreviewer status` to inspect capture/storage status. Use `/coreviewer stop` or **Stop capture** to cancel outstanding work.

Enabling the mod or joining a server never starts a lookup automatically. In passive mode the Start button explains that a manual query is required. Event Radius supplies the default lookup radius and limits the static view around the camera. Custom lookup parameters are passed to CoreProtect for validation. Only the `co/coreprotect lookup`, `l` and `near` command families can be scheduled.

Rows are parsed in batches at page boundaries or after a short quiet interval. With automatic replacement enabled, batches are staged and saved together only after successful completion; append mode saves each batch when Auto Save is ON. A query with no pagination footer expires after 30 seconds. The scheduler has one outstanding request, does not retry unanswered requests, rejects unexpected/repeated pages, and limits a session to 10,000 pages and a buffered response to 4,096 messages. Permission/no-result errors stop it. Disconnecting, changing worlds, Stop, Clear or Reload cancel pending capture. Switching Auto Page Advance OFF cancels queued commands; switching it back ON requires a new lookup.

Avoid issuing overlapping CoreProtect queries: chat responses do not carry request IDs. Coreviewer listens only to system messages during a lookup session initiated by an observed outgoing command. It ignores normal player-chat events, action-bar messages and its own feedback. Server system messages are not cryptographic proof of CoreProtect origin.

## New controls and defaults (0.6.0)

Radius, command delay, event limits and message durations use editable text fields. Leave **Event Radius** blank for **100**, **Command Delay (ms)** for **1500**, Static **Maximum Visible Events** for **10**, and Replay **Maximum Visible Events** for **20**. Positive integers are accepted without preset lists; durations accept zero for a persistent message. Large chosen limits can increase memory/rendering work.

**CAPTURE:** **Disable Capture After Completion** defaults ON. After the final page of a successful automatic lookup has been parsed and saved, both Auto Capture and Auto Page Advance switch OFF. **Notify Capture Reset** separately controls its chat message and defaults ON. Enable the capture switches again before the next automatic lookup. Manual captures, cancelled queries, errors and timeouts do not trigger a successful-completion reset.

**Capture Sounds** defaults OFF. Choose start and completion sounds using each **Search sounds / Preview** button. The picker searches all registered vanilla sound events by name, supports result pages, plays a preview, and lets you stop it or select a sound. Preview works even when automatic capture sounds are disabled. Save the settings to retain the choice. Minecraft volume settings and resource packs still affect the audio.

**CSV:** **Replace Previous Capture** defaults ON. Automatic captures are staged until the last page succeeds. Then previously captured CoreProtect records for the same server are replaced with the new capture; simulated records and other servers remain. Errors/cancellation leave the prior dataset intact. With Auto Save ON, JSON and CSV are updated and Backup retains the previous files. With Auto Save OFF, only memory is replaced and Save is still required. **Notify Capture Replacement** controls the completion chat notification independently and defaults ON. OFF preserves append behavior.

**STATIC VIEW:** **V** selects the previous page, **B** the next page, and **G** opens `/coreviewer static`. All are configurable. The static screen also has Previous/Next page buttons. A 100-action history with page size 10 has ten pages. **Show Page Progress** displays `Actions 11–20/100`, centered above the health bar; the default color is yellow and duration is 10 seconds after opening/changing a page. Color, visibility and duration are configurable; zero seconds keeps it displayed. Pages use all positioned actions in the bound world; filters/radius/unloaded chunks can make fewer of those actions visible.

**REPLAY:** **J** performs Start / Load World, **K** restarts paused, **L** stops and hides replay, and **H** opens `/coreviewer replay`. Together with Right Shift, comma and period, these shortcuts can be reassigned and appear in the replay HUD. Start uses the current bound world (or the only available captured world); if selection is ambiguous, it opens the world selector. During a temporary demo it reloads that demo.

**Show Replay Progress** displays reached/total actions plus the size of the current rolling window, on a separate line above the health bar. It remains during playback, disappears when all actions have been reached or replay is stopped, and hides 10 seconds after pausing. Its color, visibility and pause timeout are configurable; zero disables the pause timeout. Static progress is hidden while replay is active, so the messages never overlap.

Existing settings are retained. On the first 0.6.0 load, old factory values (3000 ms, 500 static events, Space/Right/Left) migrate to the requested new defaults; other custom values remain. After migration, deliberately saving any of those old values preserves your choice.

## Supported records and evidence limits

The parser supports the standard **English** CoreProtect lookup format, including separate coordinate lines such as `^ (x-12/y64/z30/world)` and exact timestamp text in hover components.

| Visible action | Local event |
| --- | --- |
| broke / placed | BLOCK_BREAK / BLOCK_PLACE |
| killed | PLAYER_KILL or MOB_KILL |
| added / removed | ITEM_ADD / ITEM_REMOVE |
| picked up / withdrew | ITEM_ADD |
| dropped / deposited / threw / shot | ITEM_REMOVE |

- Hover timestamps are preferred. Without one, the displayed relative age is converted using the client's receipt time and marked **COREPROTECT_CHAT_APPROXIMATE** in `source`. Relative times are rounded by CoreProtect and are not exact forensic timestamps.
- Actor/victim UUIDs and death causes remain null when not supplied. The actor of a kill row is the killer. No player movement or full block state can be reconstructed from these rows.
- Coordinates and server world names come only from visible coordinate text. Inventory-only responses may omit them: position stays null and world is empty. Client dimension remains null; server world names are not guessed to match client dimensions.
- Mob names are recognized against the client's entity registry. CoreProtect does not explicitly distinguish a player username from an identical mob identifier in plain chat: a player named exactly `zombie`, for example, is ambiguous and will be classified as that mob. Custom entity identifiers absent from the client registry are also ambiguous. Inspect the retained raw row in `source` before relying on the classification.
- The event ID is a locally generated UUID, not a CoreProtect database ID. In append mode, re-running a query appends another observation; automatic replacement instead replaces the previous same-server capture. Cross-query deduplication is intentionally not attempted because identical same-second actions can be legitimate distinct records. Repeated pagination footers within one session stop capture.
- The raw action row is retained in `source`. Unsupported/localized/malformed rows are skipped; the storage status reports the count. Hover-only origin coordinates are not substituted for event coordinates.
- Simulation remains available for local testing and is explicitly marked `simulated: true`; do not mix demonstration records with evidence exports. Clear them before a real investigation.

## Static View (Phase 3)

Open `/coreviewer static` or **Static** in the dashboard after joining a world.

1. Select the server world reported by CoreProtect. The selector includes only positioned, non-simulated records belonging to the current server address.
2. Click **Show selected world** to bind that server world to your current client dimension. This is an explicit moderator choice; the mod cannot infer server world names from dimension IDs. Check that you are in the matching world before binding.
3. Use **Hide view**, disable **COREVIEWER ENABLED**, disconnect or change dimensions to end the binding.
4. **Preview demo** creates seven temporary visual examples near your current position toward +Z. Every label says `[DEMO]`. These examples are neither saved nor inserted into the world and replace the active view until hidden.

Rendering options are in **Mod Menu / Settings > STATIC VIEW**:

- **Block View**, **Ghost Blocks**, **Outline**: textured translucent default block models, outlined cubes, or both. Unknown/unsupported block models use an outline fallback. Separate break/place colors, alpha and optional texture tint are available. With texture tint OFF, block textures retain their native colors.
- **Render Behind Walls**: ghost blocks, location markers, labels and arrows can ignore depth. OFF uses Minecraft 26.3's reversed depth comparison. Death figures and item models retain normal world occlusion; their markers/labels provide visibility through walls. No direct OpenGL calls are used; rendering uses Minecraft's pipeline abstraction.
- **Kill View**, **Show Player Deaths**, **Show Mob Deaths**: detached display figures frozen in the default death pose. Labels include victim/entity, killer and cause; absent causes say **Unknown**. Figures do not tick, run AI, collide or enter the world entity list. At most 128 figures are built in a frame; additional records retain their location markers and labels.
- **Item View**: full-bright floating, rotating default item models, quantity and actor labels. **Player Hologram Heads** adds a generic player-head icon. Historical skins, equipment, item metadata and entity variants are not reconstructed. Unknown UUIDs use a display-only default player profile; it is never stored as evidence.
- **Enable Arrows**, **Arrow Color**, **Arrow Size**, **Arrow Speed**: arrows point from earlier to later block events. A moving arrowhead indicates direction; its speed is visual only. **Arrows Per Player** defaults ON; OFF connects visible block events across actors. Equal timestamps, approximate timestamps and identical positions are not connected. Hidden/out-of-range records are not rendered; links describe the visible subset, not a reconstructed walking path.
- **Event Labels**, **Maximum Visible Events**: labels can be hidden (death labels remain). Enter a positive whole-number page size, default **10**. Each page holds a distinct chronological slice of the selected world's positioned records. Alpha controls ghost blocks/markers/labels/arrows; figure and item textures remain opaque. Alpha zero hides the entire view.

Only already-loaded client chunks are used. The mod never forces server chunks to load. Records with missing coordinates are retained in the database but cannot be drawn. Full historical block states/orientations are not in standard lookup chat: blocks use their default state, not a claim of exact historical shape. Player/mob classification retains the chat ambiguities documented above. Overlapping actions at one location may visually overlap; they are evidence markers, not an authoritative historical terrain state.

The binding is session-only and is invalidated on leaving/changing dimensions. New matching capture records become visible automatically. Real evidence is isolated from saved simulation fixtures; demo previews use a separate temporary scene.

## Replay (Phase 4)

1. Open `/coreviewer replay` or **Replay** in the dashboard while in the correct dimension.
2. Select the CoreProtect world, then **Load world**. This explicitly binds it to your current dimension. Only positioned records from that server/world are loaded; saved simulation fixtures are excluded.
3. **Load demo** creates seven temporary actions near you toward +Z, without writing evidence or changing world blocks/entities.
4. **Play in world** closes the panel and starts playback. The HUD shows state, UTC timeline time, percentage, speed and controls. Playback begins one second before the earliest action and finishes four seconds after the latest action, allowing the slowest block animation to finish.
5. **Previous / Next** jump to the previous/next distinct timestamp and pause. Equal-time actions appear together. Drag the timeline slider to seek; **View paused** closes the panel without resuming. **Restart** returns to the beginning paused. **Stop** releases the snapshot and hides the view.
6. Configure **Play / Pause**, **Forward** and **Backward** under Mod Menu > Configure > **REPLAY**. Defaults: **Right Shift** (play/pause), **comma** (forward), **period** (backward). Keys work only with a loaded replay, focused window and no open screen. Opening menus/chat or losing focus pauses playback; resume explicitly. Active keyboard shortcuts take priority over vanilla actions. They are ignored while typing or using menus. Coreviewer's saved configuration is authoritative at startup and when settings are saved; vanilla Controls changes are temporary until then.

**Timeline Speed**, **Block Break Speed** and **Block Place Speed** each support **0.25x, 0.5x, 1x, 2x, 4x**. Timeline Speed scales historical time. **Smart Timeline**, ON by default, skips gaps longer than 10 seconds after allowing the preceding block animation to finish. Turn it OFF to preserve all historical gaps; Next/scrubbing also skip idle periods. Seeking still uses the original historical timestamps. Block speeds control a one-second animation in timeline time, so timeline and block speed multiply. Place models grow into view; break models shrink away. Outline mode also draws an animated inner cube. Only the most recent **Maximum Visible Events** actions remain in the replay overlay (default **20**, positive whole-number input). When action 21 is reached, action 1 leaves a 20-action window; rewinding restores the corresponding earlier window. Frozen death figures and floating items appear at their timestamps. Item rotation, floating motion and arrows freeze with the timeline.

Replay reconstructs **actions**, not real movement or historical terrain. The world remains unchanged under the overlay. Actions at the same position may overlap; no walking paths, skins, exact block states or missing metadata are invented. `~` before the HUD time means the snapshot contains approximate chat timestamps: their ordering is an estimate, and these actions do not get chronological arrow links. Tied timestamps do not establish an order.

The timeline is an immutable snapshot; load again to include new capture. Radius, loaded-chunk checks, category filters, opacity, through-wall settings and visible-event cap still apply. The panel's action count covers the whole timeline, not only nearby visible markers. Starting Static View, Clear/Reload, master OFF, disconnect or changing dimensions ends replay. Replay sends no server commands. Indexed snapshots and asynchronous scene selection are active in Phase 5.

## Commands

| Command | Behavior |
| --- | --- |
| `/coreviewer` | Open dashboard |
| `/coreviewer config` | Open Cloth Config |
| `/coreviewer lookup [parameters]` | Queue a lookup with automatic paging enabled; default `r:<Event Radius> t:1h` |
| `/coreviewer stop` | Cancel capture and pending commands |
| `/coreviewer status` | Show capture and storage status |
| `/coreviewer simulate` | Append six labeled demo events |
| `/coreviewer save` | Save JSON and CSV |
| `/coreviewer reload` | Reload JSON; refuses to discard unsaved changes |
| `/coreviewer clear` | Clear local history; respects Auto Save |
| `/coreviewer static` | Choose a server world, show/hide static history, or preview temporary demo events |
| `/coreviewer replay` | Open the replay timeline and playback controls |

## Configuration and local files

All mod text is in English. GENERAL contains the master switch and radius. CAPTURE contains Auto Capture, Auto Page Advance (default OFF) and delay. CSV contains Auto Save and Backup. STATIC VIEW controls also apply to replay. REPLAY contains the three speeds and configurable playback keys.

With the master switch OFF, callbacks return without capture or scheduling, queued capture generations are invalidated, and no overlays run. Settings remain available and saved. Existing memory, registered callbacks and an already-started atomic disk write may remain/finish; a loaded mod cannot literally consume zero resources. The background worker expires after one idle second.

Files live under `<game directory>/coreviewer/`, normally `.minecraft/coreviewer/`:

```text
config.json
events.json
events.csv
config.json.bak
events.json.bak
events.csv.bak
```

Phase 1 JSON schema version 1 remains compatible. JSON is authoritative; CSV is an export, not an import format. CSV uses UTF-8, quoted/escaped fields and ISO UTC timestamps. Unknown values are null in JSON and empty CSV fields. Each file uses temporary-file replacement. Reload validates JSON before regenerating CSV, recovering from an interrupted two-file update. Malformed JSON or orphan CSV is preserved and reported instead of silently erased.

**Auto Save OFF:** explicit Save is required; unsaved data does not survive exit. **Backup ON:** retains the previous saved snapshot, not an unlimited history. Clear updates files when Auto Save is ON. Data is local and includes server addresses, player names and historical actions.

## Build and verification

For GitHub, upload the source project to the repository root. The [Build Coreviewer workflow](.github/workflows/build.yml) uses Java 25, runs the Gradle build and unit tests on pushes and pull requests, and uploads the client JAR as a workflow artifact. The [release notes](GITHUB_RELEASE.md) describe the 0.6.1 download and dependencies. Attach only the regular client JAR to a GitHub Release; GitHub generates source archives from the tag.

Set `JAVA_HOME` to JDK 25:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runClientGameTest
```

Linux/macOS: use `sh gradlew`. Gradle wrapper 9.6.0 and Fabric Loom 1.17 are included/configured. Release artifacts are in `build/libs`; install the regular jar, not the sources jar.

Unit tests exercise storage, configuration, all six event types, hover timestamps, unknown fields, passive mode, delays, final/repeated pages, timeouts, error responses and master/Auto Capture gates. The isolated Minecraft client test opens the UI and Cloth Config, toggles the master switch, saves/reloads data, and injects CoreProtect-shaped system-message components through Fabric's real receive event to verify capture and hover metadata. Phase 3 also creates an isolated flat world and exercises ghost/outline rendering, death and item models, a wall occlusion comparison, world selection and master OFF. These are simulated integration tests, **not a live CoreProtect server compatibility test**.

Source folders: `replay/` (deterministic timeline, input and HUD), `view/` (session binding, selection, rendering), `capture/` (state machine, immutable chat snapshots, parser), `config/`, `model/`, `storage/`, `ui/`; entrypoint `CoreTraceClient` and background `InvestigationService`. Test code is excluded from the release jar. `examples/` contains Phase 1 simulated fixtures. The previous prototype is preserved under `archive/` and excluded from this build.

Version 0.6.1 passed **40 unit tests and all five isolated Minecraft client scenarios**. The migration test verifies that copied CoreTrace files become independent and are not overwritten by a later copy. The client scenario verifies keyboard shortcuts, static pages, rolling replay windows, blank numeric defaults, sound search/preview/selection, and two completed captures with automatic reset and replacement. Screenshots: [static page](docs/custom-static-page.png), [replay window](docs/custom-replay-window.png), [sound picker](docs/sound-picker.png), [completion messages](docs/capture-completion.png). Phase 5 verifies the command tree/dashboard, selection/model cache reuse, resource-pack reload and cache cleanup. Phase 4 adds snapshot isolation, tied timestamps, stepping, seeking, independent speeds, pause/end/restart and master OFF checks. Its client test loads a demo through the UI, uses keyboard controls, checks rendering and verifies that saved evidence is unchanged. Phase 5 screenshots: [dashboard](docs/coreviewer-dashboard.png), [scene after resource reload](docs/coreviewer-reloaded-scene.png). Replay screenshots: [controls](docs/replay-controls.png), [first action](docs/replay-first-action.png), [completed actions](docs/replay-completed.png). Static screenshots: [static demo](docs/static-view.png), [through walls ON](docs/through-wall.png), [through walls OFF](docs/depth-tested.png). These are simulated tests, not a live CoreProtect server compatibility test.

## Phase 5: performance and compatibility

- Event lists, per-server/world/simulation timelines and chunk buckets are built on the serialized storage worker and published together as an immutable snapshot. Parsing and disk writes stay off the client thread.
- Rendering selects the chronological page or replay window first, then filters its actions by radius, event-type toggles and loaded chunks. Off-screen/unloaded records do not get replaced with actions from a different page. The spatial index also retains a bounded-nearest selection path for reference/diagnostics. No server chunk tickets or forced loading are used.
- Dense-history selection runs on a separate lazy daemon worker. The client snapshots chunk availability only; Minecraft world objects never cross to the worker. The queue keeps at most one pending selection, replacing obsolete queued requests. Results are reused; refresh requests are issued about every 200 ms, or sooner after scope, replay, filter or significant camera changes. Render-time checks immediately exclude unloaded/out-of-radius records. New nearby results appear after asynchronous selection finishes; dense histories can take longer.
- Hide, master OFF, world changes and disconnect release caches and cancel pending selection. Idle selection/storage workers expire after one second. Index memory remains proportional to stored history; saved history is not silently truncated.
- Block geometry and default item render states use separate 256-entry LRU caches. Resource-model reloads invalidate both; world changes clear them. Existing death figures remain limited to 128, with the user-selected page/window size controlling event markers. Rendering remains on Minecraft's rendering thread and is bounded by these display limits.
- Replay takes a pre-sorted timeline from its immutable index snapshot. Binary-search bounds and list slices replace repeated full-history scans/copies for seeking and visible actions. New capture never changes an already-loaded replay.
- Configuration copies no longer serialize/parse JSON during rendering. JSON persistence and schema 1 remain unchanged.

The sparse-history regression uses **100,001 events** and verifies that its local query examines **one event in one chunk**. Dense areas still cost work on the selection worker; this is an algorithmic check, not an FPS benchmark or a guarantee for arbitrary hardware.

**Upgrade and coexistence:** Replace Coreviewer 0.5.0/0.6.0 with this JAR; do not keep multiple Coreviewer versions installed. CoreTrace can remain installed because it has a different Fabric ID, package, resource namespace, key names, command root and data folder. Coreviewer uses `/coreviewer`; CoreTrace keeps its own commands. If both mods listen to the same CoreProtect lookup, both may record it; keep only one automatic pager enabled for a given investigation. The initial data copy is a snapshot, not ongoing synchronization. Existing backups are copied without deleting the originals. Live-server compatibility remains a separate validation step.

References: [CoreProtect commands](https://docs.coreprotect.net/commands/), [CoreProtect lookup implementation](https://github.com/PlayPro/CoreProtect/blob/master/src/main/java/net/coreprotect/command/lookup/StandardLookupThread.java), [CoreProtect chat formatting](https://github.com/PlayPro/CoreProtect/blob/master/src/main/java/net/coreprotect/utility/ChatUtils.java), [CoreProtect API](https://docs.coreprotect.net/api/).


