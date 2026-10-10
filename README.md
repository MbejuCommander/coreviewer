# Coreviewer investigator — 0.9.0

Client-only Fabric investigation tool for Minecraft Java **26.3**. Moderators capture English CoreProtect command responses or import CoreTrace CSVs, then inspect chronological static views, event replays and statistics. No SQL, server files or server-side CoreProtect API access is required. All mod text is in English.

## Quick start

1. Install the required Fabric dependencies listed below and put the Coreviewer JAR in your client's `mods` folder.
2. Join a server with CoreProtect command permission. Open **Mod Menu → Coreviewer investigator → Configure** and enable **COREVIEWER ENABLED** and **Auto Capture**.
3. Run `/co lookup r:100 t:1h` for passive capture. To fetch later pages automatically, enable **AUTO PAGE ADVANCE** before starting a lookup. Coreviewer saves supported events in its CSV library.
4. Open `/coreviewer static` for a paged world view, `/coreviewer replay` for chronological playback, or **Statistics** for a player and material breakdown. Select the matching CoreProtect world in static/replay.

For a previously exported investigation, copy CSV files into `<game directory>/coreviewer/csv/`, then use **Settings → CSV → Select CSV Files** to choose them. You can place CSV files inside subfolders.

## Guide contents

- [Installation](#installation) · [Settings](#settings) · [Capture](#capture)
- [CSV library and imports](#csv-library-and-imports) · [Radius and teleport](#general-radius-and-teleport)
- [Static view](#static-view) · [Replay](#replay) · [Statistics](#statistics)
- [Supported evidence and limits](#supported-evidence-and-limits) · [Files and commands](#files-and-commands)
- [Development and validation](#development-and-validation)

This README can be copied into a GitHub Wiki `Home` page. In a separate Wiki repository, relative screenshot links such as `docs/static-view.png` must be replaced with links to the main repository's `docs/` files.

## Installation

Use **JDK/Java 25**, Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**, Cloth Config **26.3.158** and optionally Mod Menu **21.0.0**. Dependencies are not bundled. Install `Coreviewer-Investigator-0.9.0-MC26.3.jar` in the client `mods` folder, replacing the previous Coreviewer JAR.

Coreviewer uses mod ID `coreviewer`, commands `/coreviewer` and its own data directory. CoreTrace (`coretrace`) can remain installed. Enable only one automatic CoreProtect pager per investigation. The server must already provide CoreProtect and grant the required command permissions.

## Settings

Configure opens Cloth Config directly; the intermediate dashboard has been removed. **General → Menu Animations** controls smooth scrolling and subtle transitions. Settings retain category tabs, search, reset and Save & Quit controls. The folder buttons launch the operating system file manager asynchronously. Historical block rendering supports Minecraft's Improved Transparency option in both through-wall and depth-tested modes.

## Capture

1. Open `/coreviewer config` or Mod Menu → Coreviewer → Configure.
2. Enable **COREVIEWER ENABLED** and **Auto Capture**.
3. With **AUTO PAGE ADVANCE OFF**, run `/co lookup`, `/co l` or `/co near` manually. Coreviewer captures the response without sending pagination commands.
4. With **AUTO PAGE ADVANCE ON**, run a lookup or use Dashboard → Capture → Start lookup. The scheduler reads `Page X/Y`, waits **Command Delay** (default **1500 ms**), then requests `/co l <next page>`.
5. Every capture saves automatically as `csv/<capture folder>/events.csv`. Set **Capture → Capture Folder Name** to choose the folder; blank generates a timestamp plus random ID. Repeated names receive `(2)`, `(3)`, etc., without overwriting existing investigations. Parsed batches are persisted during capture; cancelled/incomplete queries may therefore leave a partial CSV. A query containing no supported events creates no new file.
6. Successful automatic completion disables both capture switches by default. **Disable Capture After Completion** and **Notify Capture Reset** control this behavior and its message independently. Optional start/end sounds default OFF; the vanilla sound picker has search, preview and stop controls.

Capture starts only after an outgoing supported lookup command. It ignores player chat, action bars and its own feedback. One request can be outstanding; commands are delayed, unanswered pages are not retried, and errors, repeated pages, disconnects or a 30-second timeout stop the session. Chat does not contain reliable query IDs, so avoid overlapping lookups. Server system messages are not authenticated CoreProtect evidence.

## CSV library and imports

Open **Settings → CSV → Open Import / Export Folder**. Both imports and exports use `<game directory>/coreviewer/csv/`.

Copy CSVs exported by **CoreTrace 1.4.1+26.3** into this folder or any of its subfolders. Nested CSVs are discovered recursively and listed with their relative folder paths. Open **Select CSV Files**, click **Refresh**, and select the files to combine. Green checkboxes indicate selection. The screen includes Select all, Deselect all, up/down ordering, individual deletion, and Delete all CSVs. Deletion asks for confirmation and affects CSVs in this shared folder and its subfolders only. After deletion, empty parent folders are removed up to (but never including) the shared CSV root. A folder containing another CSV or any other file is preserved. It does not delete external CoreTrace files or archived CSVs.

Files without server metadata are associated with the current server when first selected. Import them while connected to the intended server. Coreviewer exports retain their own server metadata. File selection/order is saved in `csv-library.json`. Reordering changes the file list and tie order, but **all selected events are sorted by recorded timestamp** for static/replay: an earlier block break precedes a later item action even when its CSV is lower in the list. Repeated Coreviewer event IDs are deduplicated; overlapping queries with distinct IDs may still overlap.

**Simple Mode** defaults OFF. Enabling it merges the currently selected events into a new capture folder and moves other existing library CSVs to `coreviewer/csv-archive/before-simple-<id>/`. The warning explains that subsequent captures replace the single active CSV; selection is unavailable in this mode. The previous simple capture is replaced when the new capture first saves supported events, including a partial capture. An empty capture leaves it intact. Files copied manually into the folder while Simple Mode is ON remain unselected until the mode is disabled. Archives are never automatically deleted. `simple-csv.json` remembers the active file across restarts; older root-level `simple.csv` files remain supported.

Auto Save, Backup and Replace Previous Capture controls have been removed. Capture saving is always automatic. The old configuration properties may remain for migration, but no longer control CSV saving. Legacy `events.json` is migrated once to `migrated-events.csv` when no library manifest exists; originals remain intact. CSV is now the investigation data source, not JSON.

## General: radius and teleport

**Event Radius** accepts any positive integer; blank restores **100**. It supplies the default lookup radius and limits nearby rendering. It does not change an already executed server lookup.

**Respect Radius** defaults OFF. OFF preserves the entire selected world's timeline, including distant events. ON filters events to the radius around your position **when loading the view**, excluding outside events from pages and replay. Reload after moving the center or changing the setting. Rendering still requires loaded client chunks.

**Follow Events with Teleport** defaults OFF. When enabled, the first action of a static page or the currently reached replay action can trigger a command if outside Event Radius. It needs recorded coordinates/world and server permissions. It does not run for demo scenes, open menus, active captures, or when Respect Radius is ON. There is at most one attempt per target, spaced by at least 1500 ms or Command Delay, whichever is larger. Failed commands are not retried.

Default **Teleport Command**:

```text
/co teleport #{world} {x} {y} {z}
```

`{world}` is the exact CoreProtect world name, such as `world`, `world_nether` or `world_the_end`; `{x}`, `{y}`, `{z}` are the recorded integer coordinates. The `#` belongs to the default command template. Separate arguments with spaces. A server-specific example is `/tp {world} {x} {y} {z}` **only if that server's teleport command supports a world argument**. Vanilla `/tp @s {x} {y} {z}` works within the current dimension. Unresolved placeholders or multiline templates are rejected. A dimension change shortly after a Coreviewer teleport preserves the view; disconnects and unrelated world changes end it.

## Static view

Open `/coreviewer static` (**G**). Select a CoreProtect world and **Show selected world** to bind its records to the current client dimension. Check that the dimension matches: server world names cannot reliably be inferred from client dimension IDs. Each view is a snapshot of the selected CSVs for the current server and chosen world. Load again after adding captures or changing files.

- **V/B**: previous/next chronological page. Default **Maximum Visible Events: 10**. Page progress is centered above health, yellow for 10 seconds by default; color, duration and visibility are configurable.
- **Block View**: transparent default block models, outlines, colors, opacity and optional texture tint.
- **Kill View**: player deaths use a custom stone memorial with the victim's head and two vanilla flowers; mob deaths retain frozen display figures. Victim/killer/cause labels remain available. Missing causes stay unknown.
- **Item View**: floating item models and quantities; optional generic player-head holograms.
- **Container View**: chest-style inventory slots with green added/red removed backgrounds, item models, quantities and actor names.
- **Session View**: frozen player holograms using available current skins and green/red login/logout borders.
- **Arrows**: chronological links across visible supported event categories, per player by default. Configure color, size and animation speed. Equal or approximate timestamps do not establish a definite order and are not linked.

**Show Server Time** defaults ON in STATIC VIEW and also applies to replay. Labels show the recorded event time normalized to UTC, `~` for approximate chat times, or Unknown when unavailable. It can be switched off independently of ordinary event labels.

Category toggles also apply to replay. Through-wall mode applies to block overlays, labels, markers and arrows; inventory-slot backgrounds and vanilla item/entity models retain world occlusion. Session skins and grave portraits use available server profiles or vanilla asynchronous UUID/name lookup; failed lookups use a default skin. These are current skins, not historical skins. Orientations, item metadata and movement are not reconstructed. Missing coordinates or timestamps cannot produce a chronological world marker. Type toggles, loaded chunks and rendering radius may reduce the visible subset of a page without replacing it with later actions.

## Replay

Open `/coreviewer replay` (**H**), choose a world and **Load world**. **Play in world** starts playback; opening a menu or losing focus pauses it. Replay uses original event timestamps and never edits real terrain or entities.

| Default key | Action |
| --- | --- |
| Right Shift | Play / pause |
| Comma | Forward to next timestamp |
| Period | Backward to previous timestamp |
| J | Start / load selected world |
| K | Restart paused |
| L | Stop and hide |
| H | Replay menu |

All keys are configurable. They apply only in the world, without an open menu; active shortcuts take priority over vanilla bindings. A slider supports seeking, and equal-time actions appear together.

**Maximum Visible Events** defaults to **10** (blank/reset also use 10; existing saved limits are preserved). The rolling window removes the oldest action when a new one exceeds the limit; rewind restores the corresponding earlier window. Progress shows reached/total and window size above health, separately from static progress. It hides at completion/stop or after the configurable pause timeout (default 10 seconds). Color and visibility are configurable.

**Smart Timeline** defaults ON. The **Smart Timeline (seconds)** text field is near the top of REPLAY settings, with an adjacent **ON/OFF** button. It defaults to **2** seconds and accepts nonnegative numbers, including decimals; clearing the field restores 2. Existing saved values are preserved until edited or reset. Long idle gaps are compressed after the configured wait and completion of the preceding block animation. Timeline Speed scales this wait along with historical time. Zero skips idle gaps as soon as animations allow. Disable Smart Timeline to preserve idle time. Block Break Speed, Block Place Speed and Timeline Speed support 0.25×, 0.5×, 1×, 2× and 4×.

Replay reconstructs logged actions, not walking paths or an authoritative historical world. Approximate timestamps are marked `~`. Optional Follow Events with Teleport is the only replay navigation feature that sends a server command.

## Statistics

Open **Settings → STATISTICS → Open Statistics**, or **Statistics** on the dashboard. Statistics use all selected CSVs, independently of the view's radius and world binding.

The category dropdown contains **All**, **Blocks**, **Items**, **Containers**, **Kills**, **Sessions**. The bordered table uses soft green for additions/placements/logins and soft red for removals/breaks/logouts. **Compact** defaults ON: opposing actions for the same material and category share one column, with green additions / red removals (for example, `10 / 5`). Different categories are never merged into one unit. Turn Compact OFF for separate action columns. **Show details** hides or shows header names while preserving icons and tooltips. Both preferences are saved. Horizontal arrows browse columns while the player column stays fixed. The magnifier **Item** search filters matching material/action columns; **Player** search jumps to the first matching player row (exact names have priority). **↑ Previous / ↓ Next** page through players.

Rows default to player A–Z. Each column arrow cycles highest count first → lowest first → player A–Z. **Sort: ADDED / REMOVED / TOTAL** chooses which number to rank in compact columns. Alphabetical ties are deterministic. **Reset filters** clears searches, category and sorting; sorting is not saved when the statistics screen is closed.

Click a player head or name to inspect their categories, then click a material icon in the personal table to open individual events with quantities, UTC times, worlds and exact recorded coordinates. The evidence screen reads the same snapshot as the table, runs scans off-thread and retains only its current page (at most 20 records); missing coordinates remain unavailable. Use Back and All players to return. Heads use currently available player profiles; offline/unknown profiles use a generic head.

The **TOTAL** row sums every player in the current category/filter, not just the visible page. Block, kill and session columns count events. Item/container columns sum known quantities, keeping additions and removals separate. Unknown quantities are excluded from numeric sums and reported separately. The footer reports event count; All does not combine incompatible units into one misleading number.

## Supported evidence and limits

CoreTrace CSV import supports schema 2 block break/place, item pickup/drop, container add/remove, kills, sessions, and available ender-chest/projectile item records. Inventory, chat, command, username, sign and unsupported actions are skipped and counted. CSV parsing supports UTF-8 BOM, optional `sep=,`, quoted commas/newlines and reordered header columns. Legacy Coreviewer CSVs are supported too.

English chat supports broke/placed, killed, added/removed (container), picked up/dropped/withdrew/deposited/threw/shot, and logged in/out. Coordinates come from visible `(x…/y…/z…/world)` lines. Hover timestamps are preferred; relative chat ages are marked approximate. CSV timestamps must contain a timezone or ISO offset; unavailable/invalid times remain unknown and are excluded from timelines but retained for statistics. Missing UUIDs, causes, coordinates and quantities are never fabricated. Quantity zero internally means unknown and exports as blank.

Plain-text mob/player names can be ambiguous. Entity registry matches are treated as mobs; unknown unnamespaced names as players. This cannot prove whether a player named `zombie` is a mob. Demo data is labeled and excluded from real world timelines; do not select simulated CSVs for evidentiary statistics.

## Files and commands

```text
coreviewer/
  config.json
  config.json.bak
  simple-csv.json              # active Simple Mode file, when used
  csv-library.json
  csv/                         # shared imports, including nested folders
    <capture folder>/events.csv # automatically saved capture
  csv-archive/                 # files archived when Simple Mode is enabled
```

`/coreviewer` opens the dashboard. Additional commands: `config`, `lookup [parameters]`, `stop` (capture), `status`, `static`, `replay`, `simulate`, `save` (automatic-saving status), `reload` (CSV library), and `clear` (delete active library CSVs). `/coreviewer clear` executes immediately; the library screen offers confirmation dialogs. No command deletes external source exports or the archive.

Master OFF stops capture, command scheduling and overlays while preserving settings. Already-started disk operations may finish; registered callbacks and existing data still occupy memory.

## Development and validation

Build with JDK 25 and the included Gradle 9.6 wrapper / Fabric Loom 1.17:

```powershell
.\gradlew.bat build
.\gradlew.bat runClientGameTest
.\gradlew.bat runClient
```

For a focused client scenario, use `./gradlew runClientGameTest "-PcoreviewerTestClass=dev.coreviewer.LibraryGameTest"`. Omit that property to run all seven scenarios.

Linux/macOS: `sh gradlew build`. Install the regular JAR in `build/libs`, not `-sources.jar`. The GitHub Actions workflow builds/tests source pushes and provides an artifact. See [release notes](GITHUB_RELEASE.md).

Parsing, disk writes, statistics aggregation and index construction run off the Minecraft thread. Scene selection uses immutable indexes and a bounded background queue; only loaded chunks are considered. Model caches and figure limits bound rendering work. CSV parsing reuses up to eight unchanged files / 200,000 cached events; selected history itself is not silently truncated. Large user-selected display limits still cost rendering time.

Tests cover configuration, capture scheduling, CSV compatibility with the supplied CoreTrace 1.4.1 fixture, escaping, unknown metadata, selection persistence, chronology, Simple Mode, statistics, replay and spatial indexing. Isolated Minecraft client scenarios exercise configuration, capture, rendering, replay controls, library selection, statistics, container/session models, radius filtering and a permitted teleport. These are local simulated tests, not validation against a live CoreProtect server.

Version 0.9.0 verification: **62 unit tests and all seven isolated Minecraft client scenarios passed**. Includes regression coverage for sorting paired counts, material/player searches, bounded evidence pages, empty-folder cleanup, current-skin holograms, memorial rendering and server timestamps. Live CoreProtect-server validation remains separate from local simulated tests.

Screenshots: [direct settings](docs/direct-settings.png), [player memorial](docs/player-memorial.png), [container slot](docs/container-slot.png), [search and sorting](docs/statistics-search-sort.png), [individual evidence](docs/statistics-evidence.png), [category dropdown](docs/statistics-dropdown.png), [Smart Timeline input](docs/smart-timeline.png), [icon-only statistics](docs/statistics-icons-only.png), [CSV library](docs/csv-library.png), [statistics](docs/statistics-all.png), [player statistics](docs/statistics-player.png), [containers and sessions](docs/container-sessions.png).
