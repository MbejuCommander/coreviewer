# Coreviewer Investigator 0.15.3 — Minecraft 26.3 Fabric

Single-block PDF donuts now show their vanilla block texture at 70% opacity inside the circular center.

Fixes the blue rectangle that could cover the report builder after player skins loaded. The on-screen saved-file notice now clears after 30 seconds. A donut for one block type shows that block's vanilla texture inside its hole; mixed and non-block donuts stay plain.

This update preserves player-name underscores in PDF reports and resolves current Mojang skins by name before using a recorded UUID. Skin faces are prefetched in the background when Create Report opens and appear beside the selectable player names. The PDF donut now shows outside labels with pointers, percentages inside readable slices, and a narrower right-hand key; the top-12-plus-Others rule remains.

PDF reports can now separate selected actions and materials, source files and XLSX worksheets. The new Breakdown menu starts with every option OFF. Workbook and sheet names are recorded for new imports, and older imports use their folder names.

Detail pages show per-player bars with a dotted mean line, total, active-player mean, median and maximum. Their donuts omit zero-value players and group everyone after the top 12 as Others, with a full ranking on subsequent pages. Known item and container quantities are counted; unknown amounts remain separate. Empty player-summary categories are hidden.

The Statistics TP tour now groups chronological stops by player. A searchable selector beside the player's head previews any player's first stop; Next sends the teleport. Players can move on opening; U toggles movement lock and clickable controls. Back/Next use the Static View previous/next keys by default, and can be configured independently. Escape exits.

A positive Statistics Radius shows original counts alongside retained TP stops with an ender pearl explanation. Drag horizontal or vertical scrollbars to browse large tables. Enter or an outside click clears text focus; arrow keys move through the table.

Investigation Files now filters filenames while typing. Both Investigation Files and Import Coretracer support mouse-wheel paging and draggable vertical scrollbars.

Statistics now offers **Create Report**. Select players, event materials and chart types (player bars, stacked categories, donut and timeline). A PDF summary is saved under `coreviewer/reports/` from the selected CSV/Excel investigation files. Each selected player appears, including zero-count rows. Current skin faces are included when available, with fallback avatars.

Install the 0.15.3 JAR with Minecraft 26.3, Java 25, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3, and Cloth Config 26.3.158. Mod Menu 21.0.0 is optional. Replace the older Coreviewer JAR. See README for detailed usage and limits.
